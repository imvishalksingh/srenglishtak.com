package com.readiq.backend.reader;

import com.readiq.backend.api.ApiException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.LongSupplier;

/** Server RAM only. This is a rendering cache, never an authorization or delivery API. */
final class PreparedReaderPages implements AutoCloseable {
  static final long MAX_BYTES = 8L * 1024 * 1024;
  private static final long LIFE = Duration.ofMinutes(5).toNanos();

  record Key(String book, String location, int page, UUID user, UUID session) {}

  record Page(byte[] image, int pageCount) {}

  interface Rendering {
    Page render(Map<String, Object> book, int page, UUID user, UUID session, boolean background);
  }

  private record Saved(byte[] image, int count, long created) {}

  private record Warm(Map<String, Object> book, Key key, long epoch) {}

  private final Rendering rendering;
  private final LongSupplier clock;
  private final long waitMillis;
  private final boolean readAhead;
  private final Object guard = new Object();
  private final LinkedHashMap<Key, Saved> images = new LinkedHashMap<>(16, .75f, true);
  private final ConcurrentHashMap<Key, CompletableFuture<Page>> flights = new ConcurrentHashMap<>();
  private final AtomicReference<Warm> next = new AtomicReference<>();
  private final AtomicBoolean working = new AtomicBoolean();
  private final AtomicLong epoch = new AtomicLong();
  private final ExecutorService worker =
      Executors.newSingleThreadExecutor(
          task -> {
            var thread = new Thread(task, "reader-prepare-next-page");
            thread.setDaemon(true);
            return thread;
          });
  private volatile boolean closed;
  private long used;

  PreparedReaderPages(Rendering rendering, LongSupplier clock, Duration wait, boolean readAhead) {
    this.rendering = rendering;
    this.clock = clock;
    this.waitMillis = Math.max(1, wait.toMillis());
    this.readAhead = readAhead;
  }

  private static Key key(Map<String, Object> book, int page, UUID user, UUID session) {
    return new Key(
        Objects.toString(book.get("id"), ""),
        Objects.toString(book.get("file_url"), ""),
        page,
        user,
        session);
  }

  byte[] render(Map<String, Object> book, int page, UUID user, UUID session) {
    if (closed) throw busy();
    var key = key(book, page, user, session);
    var result = prepare(book, key, false, epoch.get());
    if (readAhead && page < result.pageCount()) {
      // Only one adjacent page for the most recent authorized request, never a whole-book job.
      next.set(new Warm(new HashMap<>(book), key(book, page + 1, user, session), epoch.get()));
      start();
    }
    return result.image();
  }

  private Page prepare(Map<String, Object> book, Key key, boolean background, long expectedEpoch) {
    if (closed || expectedEpoch != epoch.get()) throw busy();
    synchronized (guard) {
      expireLocked();
      var found = images.get(key);
      if (found != null) return new Page(found.image().clone(), found.count());
    }
    var mine = new CompletableFuture<Page>();
    var existing = flights.putIfAbsent(key, mine);
    if (existing != null) {
      if (background) throw busy(); // Optional preparation never waits behind another job.
      try {
        var value = existing.get(waitMillis, TimeUnit.MILLISECONDS);
        if (expectedEpoch != epoch.get() || closed) throw busy();
        return new Page(value.image().clone(), value.pageCount());
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw busy();
      } catch (TimeoutException ex) {
        throw busy();
      } catch (ExecutionException ex) {
        if (ex.getCause() instanceof RuntimeException runtime) throw runtime;
        throw busy();
      }
    }
    try {
      var value = rendering.render(book, key.page(), key.user(), key.session(), background);
      synchronized (guard) {
        if (expectedEpoch != epoch.get() || closed) throw busy();
        expireLocked();
        if (value.image().length <= MAX_BYTES) {
          var oldest = images.values().iterator();
          while (used + value.image().length > MAX_BYTES && oldest.hasNext()) {
            var old = oldest.next();
            used -= old.image().length;
            Arrays.fill(old.image(), (byte) 0);
            oldest.remove();
          }
          var saved = new Saved(value.image().clone(), value.pageCount(), clock.getAsLong());
          var prior = images.put(key, saved);
          if (prior != null) {
            used -= prior.image().length;
            Arrays.fill(prior.image(), (byte) 0);
          }
          used += saved.image().length;
        }
      }
      // Cache owns a copy. Eviction must never overwrite a response being written to the socket.
      mine.complete(value);
      return value;
    } catch (RuntimeException | Error failure) {
      flights.remove(key, mine);
      mine.completeExceptionally(failure);
      throw failure;
    } finally {
      flights.remove(key, mine);
    }
  }

  private void start() {
    if (closed || !working.compareAndSet(false, true)) return;
    try {
      worker.execute(
          () -> {
            try {
              Warm request;
              while (!closed && (request = next.getAndSet(null)) != null) {
                try {
                  prepare(request.book(), request.key(), true, request.epoch());
                } catch (RuntimeException ignored) {
                  /* Optional work cannot fail a reader request. */
                }
              }
            } finally {
              working.set(false);
              if (!closed && next.get() != null) start();
            }
          });
    } catch (RejectedExecutionException ignored) {
      working.set(false);
    }
  }

  void drop(String book, String location) {
    synchronized (guard) {
      var iterator = images.entrySet().iterator();
      while (iterator.hasNext()) {
        var entry = iterator.next();
        if (entry.getKey().book().equals(book) && entry.getKey().location().equals(location)) {
          used -= entry.getValue().image().length;
          Arrays.fill(entry.getValue().image(), (byte) 0);
          iterator.remove();
        }
      }
    }
  }

  void invalidate() {
    synchronized (guard) {
      epoch.incrementAndGet();
      next.set(null);
      images.values().forEach(entry -> Arrays.fill(entry.image(), (byte) 0));
      images.clear();
      used = 0;
    }
  }

  long epoch() {
    return epoch.get();
  }

  void expire() {
    synchronized (guard) {
      expireLocked();
    }
  }

  private void expireLocked() {
    long now = clock.getAsLong();
    var iterator = images.values().iterator();
    while (iterator.hasNext()) {
      var old = iterator.next();
      if (now - old.created() >= LIFE) {
        used -= old.image().length;
        Arrays.fill(old.image(), (byte) 0);
        iterator.remove();
      }
    }
  }

  private static ApiException busy() {
    return new ApiException(503, "Reader is busy. Please try again shortly");
  }

  @Override
  public void close() {
    closed = true;
    invalidate();
    worker.shutdownNow();
  }
}
