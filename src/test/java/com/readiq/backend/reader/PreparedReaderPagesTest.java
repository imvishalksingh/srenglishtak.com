package com.readiq.backend.reader;

import static org.junit.jupiter.api.Assertions.*;

import com.readiq.backend.api.ApiException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class PreparedReaderPagesTest {
  private static final UUID USER = UUID.randomUUID(), SESSION = UUID.randomUUID();

  private static Map<String, Object> book(String id) {
    return Map.of("id", id, "file_url", "private/" + id);
  }

  private static PreparedReaderPages.Page image(int value, int count) {
    return new PreparedReaderPages.Page(
        new byte[] {(byte) 255, (byte) 216, (byte) 255, (byte) value}, count);
  }

  @Test
  void returningToPagesReusesPersonalizedJpegsAndNeverOverwritesResponseBytes() {
    var calls = new AtomicInteger();
    var now = new AtomicLong();
    try (var pages =
        new PreparedReaderPages(
            (b, p, u, s, bg) -> image(calls.incrementAndGet(), 3),
            now::get,
            Duration.ofSeconds(2),
            false)) {
      byte[] original = pages.render(book("A"), 1, USER, SESSION);
      pages.render(book("A"), 2, USER, SESSION);
      pages.render(book("A"), 3, USER, SESSION);
      assertArrayEquals(original, pages.render(book("A"), 1, USER, SESSION));
      assertEquals(3, calls.get());
      var caller = pages.render(book("A"), 1, USER, SESSION);
      caller[3] = 90;
      assertEquals(1, pages.render(book("A"), 1, USER, SESSION)[3]);
      now.addAndGet(Duration.ofMinutes(5).toNanos());
      pages.expire();
      pages.render(book("A"), 1, USER, SESSION);
      assertEquals(4, calls.get());
      assertEquals(1, original[3]);
    }
  }

  @Test
  void accountSessionEditionAndBookAreAllPartOfTheCacheKey() {
    var calls = new AtomicInteger();
    try (var pages =
        new PreparedReaderPages(
            (b, p, u, s, bg) -> image(calls.incrementAndGet(), 1),
            System::nanoTime,
            Duration.ofSeconds(2),
            false)) {
      pages.render(book("A"), 1, USER, SESSION);
      pages.render(book("A"), 1, UUID.randomUUID(), SESSION);
      pages.render(book("A"), 1, USER, UUID.randomUUID());
      pages.render(book("B"), 1, USER, SESSION);
      pages.render(Map.of("id", "A", "file_url", "private/replacement"), 1, USER, SESSION);
      assertEquals(5, calls.get());
    }
  }

  @Test
  void nextPageIsPreparedOnlyOnTheServerAndForegroundJoinsTheSameWork() throws Exception {
    var foreground = new AtomicInteger();
    var background = new AtomicInteger();
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var pages =
            new PreparedReaderPages(
                (b, p, u, s, bg) -> {
                  if (bg) {
                    background.incrementAndGet();
                    entered.countDown();
                    await(release);
                  } else foreground.incrementAndGet();
                  return image(p, 2);
                },
                System::nanoTime,
                Duration.ofSeconds(3),
                true);
        var threads = Executors.newVirtualThreadPerTaskExecutor()) {
      assertEquals(1, pages.render(book("A"), 1, USER, SESSION)[3]);
      assertTrue(entered.await(2, TimeUnit.SECONDS));
      var waiting = threads.submit(() -> pages.render(book("A"), 2, USER, SESSION));
      release.countDown();
      assertEquals(2, waiting.get(3, TimeUnit.SECONDS)[3]);
      assertEquals(1, foreground.get());
      assertEquals(1, background.get());
    } finally {
      release.countDown();
    }
  }

  @Test
  void failedOptionalPreparationCannotFailTheDeliveredPageOrPreventAnExplicitPageRead()
      throws Exception {
    var tried = new CountDownLatch(1);
    var foreground = new AtomicInteger();
    try (var pages =
        new PreparedReaderPages(
            (b, p, u, s, bg) -> {
              if (bg) {
                tried.countDown();
                throw new ApiException(503, "busy");
              }
              foreground.incrementAndGet();
              return image(p, 2);
            },
            System::nanoTime,
            Duration.ofSeconds(2),
            true)) {
      assertEquals(1, pages.render(book("A"), 1, USER, SESSION)[3]);
      assertTrue(tried.await(2, TimeUnit.SECONDS));
      // A raced foreground waiter can observe the failed flight, but can safely retry with a new
      // signed request.
      try {
        pages.render(book("A"), 2, USER, SESSION);
      } catch (ApiException ex) {
        assertEquals(503, ex.status());
      }
      assertEquals(2, pages.render(book("A"), 2, USER, SESSION)[3]);
      assertEquals(2, foreground.get());
    }
  }

  @Test
  void queuedPreparationKeepsOnlyTheMostRecentAdjacentPage() throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var latest = new CountDownLatch(1);
    var warmed = new CopyOnWriteArrayList<Integer>();
    try (var pages =
        new PreparedReaderPages(
            (b, p, u, s, bg) -> {
              if (bg) {
                warmed.add(p);
                if (p == 2) {
                  entered.countDown();
                  await(release);
                }
                if (p == 9) latest.countDown();
              }
              return image(p, 10);
            },
            System::nanoTime,
            Duration.ofSeconds(2),
            true)) {
      pages.render(book("A"), 1, USER, SESSION);
      assertTrue(entered.await(2, TimeUnit.SECONDS));
      pages.render(book("A"), 4, USER, SESSION);
      pages.render(book("A"), 6, USER, SESSION);
      pages.render(book("A"), 8, USER, SESSION);
      release.countDown();
      assertTrue(latest.await(2, TimeUnit.SECONDS));
      assertEquals(List.of(2, 9), warmed);
    } finally {
      release.countDown();
    }
  }

  @Test
  void invalidationPreventsInFlightPreparedDataFromBeingDelivered() throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var pages =
            new PreparedReaderPages(
                (b, p, u, s, bg) -> {
                  if (bg) {
                    entered.countDown();
                    await(release);
                  }
                  return image(p, 2);
                },
                System::nanoTime,
                Duration.ofSeconds(2),
                true);
        var threads = Executors.newVirtualThreadPerTaskExecutor()) {
      pages.render(book("A"), 1, USER, SESSION);
      assertTrue(entered.await(2, TimeUnit.SECONDS));
      pages.invalidate();
      release.countDown();
      // Either join fails closed or a fresh foreground render succeeds; old epoch cannot be cached.
      try {
        assertEquals(2, pages.render(book("A"), 2, USER, SESSION)[3]);
      } catch (ApiException ex) {
        assertEquals(503, ex.status());
      }
    } finally {
      release.countDown();
    }
  }

  @Test
  void compressedCacheHasOneGlobalBudgetAndEvictionDoesNotCorruptInFlightResponses() {
    var calls = new AtomicInteger();
    try (var pages =
        new PreparedReaderPages(
            (b, p, u, s, bg) -> {
              calls.incrementAndGet();
              byte[] data = new byte[3 * 1024 * 1024];
              data[0] = 9;
              return new PreparedReaderPages.Page(data, 3);
            },
            System::nanoTime,
            Duration.ofSeconds(2),
            false)) {
      byte[] response = pages.render(book("A"), 1, USER, SESSION);
      pages.render(book("A"), 2, USER, SESSION);
      pages.render(book("A"), 3, USER, SESSION);
      pages.render(book("A"), 1, USER, SESSION);
      assertEquals(4, calls.get());
      assertEquals(9, response[0]);
    }
  }

  @Test
  void replacementOrEvictionOfOneSourceDoesNotDropAnotherBooksPreparedPages() {
    var calls = new AtomicInteger();
    try (var pages =
        new PreparedReaderPages(
            (b, p, u, s, bg) -> image(calls.incrementAndGet(), 1),
            System::nanoTime,
            Duration.ofSeconds(2),
            false)) {
      pages.render(book("A"), 1, USER, SESSION);
      pages.render(book("B"), 1, USER, SESSION);
      pages.drop("A", "private/A");
      pages.render(book("B"), 1, USER, SESSION);
      pages.render(book("A"), 1, USER, SESSION);
      assertEquals(3, calls.get());
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout");
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(ex);
    }
  }
}
