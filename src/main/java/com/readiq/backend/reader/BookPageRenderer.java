package com.readiq.backend.reader;

import com.readiq.backend.api.ApiException;
import com.readiq.backend.service.StorageService;
import jakarta.annotation.PreDestroy;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.LongSupplier;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Server RAM only. Every caller must authorize before rendering; original bytes never leave here. */
@Service
public class BookPageRenderer {
  private static final int MAX_BYTES = 50 * 1024 * 1024;
  private static final long PAGE_MEMORY = 16L * 1024 * 1024;
  private static final long IDLE_NANOS = java.time.Duration.ofMinutes(10).toNanos();
  private static final long LIFE_NANOS = java.time.Duration.ofMinutes(30).toNanos();
  private final StorageService storage;
  private final LongSupplier clock;
  // PDFBox documents are not thread-safe. Keep bounded rendering on small production instances.
  private final Semaphore capacity = new Semaphore(1);
  private CachedBook cached;

  private record Source(String id, String location) {}
  private static final class CachedBook {
    final Source source;
    final byte[] original;
    final PDDocument pdf;
    final long created;
    long used, pageBytes;
    final LinkedHashMap<Integer, BufferedImage> pages = new LinkedHashMap<>(4, .75f, true);
    CachedBook(Source source, byte[] original, PDDocument pdf, long now) {
      this.source = source; this.original = original; this.pdf = pdf; created = used = now;
    }
    void clear() {
      pages.values().forEach(BufferedImage::flush);
      pages.clear(); pageBytes = 0;
      try { pdf.close(); } catch (IOException ignored) { /* Releasing all retained buffers still proceeds. */ }
      finally { Arrays.fill(original, (byte) 0); }
    }
  }

  @Autowired
  public BookPageRenderer(StorageService storage) { this(storage, System::nanoTime); }
  BookPageRenderer(StorageService storage, LongSupplier clock) { this.storage = storage; this.clock = clock; }

  private CachedBook open(Map<String, Object> book, boolean verifiedStorage) throws IOException {
    if (!"pdf".equals(book.get("format_type")))
      throw new ApiException(422, "This book needs a protected PDF edition. Contact support");
    String url = Objects.toString(book.get("file_url"), "");
    if (!storage.privateFile(url)) {
      clear();
      throw new ApiException(503, "This book must be moved to private storage before reading");
    }
    // Even a cache hit must fail closed if storage privacy is no longer configured.
    try { if (!verifiedStorage) storage.requirePrivateBucket(); }
    catch (RuntimeException failure) { clear(); throw failure; }
    long now = clock.getAsLong();
    var source = new Source(Objects.toString(book.get("id"), ""), url);
    if (cached != null && (!cached.source.equals(source) || expired(cached, now))) clear();
    if (cached != null) { cached.used = now; return cached; }
    byte[] bytes;
    try (var input = storage.download(url)) { bytes = input.readNBytes(MAX_BYTES + 1); }
    PDDocument pdf = null;
    try {
      if (bytes.length > MAX_BYTES) throw new ApiException(413, "Book exceeds the reader limit");
      // PDFBox's byte-array loader and its default stream cache are memory-only.
      pdf = Loader.loadPDF(bytes);
      if (pdf.getNumberOfPages() < 1 || pdf.getNumberOfPages() > 2000 || pdf.isEncrypted())
        throw new ApiException(422, "Book format is not supported by the protected reader");
      cached = new CachedBook(source, bytes, pdf, now);
      return cached;
    } catch (IOException | RuntimeException failure) {
      try { if (pdf != null) pdf.close(); }
      finally { Arrays.fill(bytes, (byte) 0); }
      throw failure;
    }
  }

  int pageCount(ReaderBookAccess.AuthorizedBook book) { return count(book.metadata(),true); }
  public int pageCount(Map<String, Object> book) { return count(book,false); }
  private int count(Map<String, Object> book, boolean verifiedStorage) {
    acquire();
    try { return open(book,verifiedStorage).pdf.getNumberOfPages(); }
    catch (IOException ex) { clear(); throw new ApiException(422, "Book PDF could not be opened"); }
    finally { capacity.release(); }
  }

  byte[] render(ReaderBookAccess.AuthorizedBook book, int page, UUID user, UUID session) {
    return render(book.metadata(),page,user,session,true);
  }
  public byte[] render(Map<String, Object> book, int page, UUID user, UUID session) {
    return render(book,page,user,session,false);
  }
  private byte[] render(Map<String, Object> book, int page, UUID user, UUID session, boolean verifiedStorage) {
    acquire();
    try {
      var document = open(book,verifiedStorage);
      if (page < 1 || page > document.pdf.getNumberOfPages()) throw ApiException.missing();
      BufferedImage base = document.pages.get(page);
      if (base == null) {
        base = rasterize(document.pdf, page);
        long bytes = memory(base);
        var iterator = document.pages.values().iterator();
        while (document.pageBytes + bytes > PAGE_MEMORY && iterator.hasNext()) {
          var old = iterator.next(); document.pageBytes -= memory(old); old.flush(); iterator.remove();
        }
        document.pages.put(page, base); document.pageBytes += bytes;
      }
      int footer = footerSize(base.getWidth()) * 3;
      var image = new BufferedImage(base.getWidth(), base.getHeight() + footer, BufferedImage.TYPE_INT_RGB);
      try {
        var canvas = image.createGraphics();
        try {
          canvas.setColor(Color.WHITE); canvas.fillRect(0, 0, image.getWidth(), image.getHeight());
          canvas.drawImage(base, 0, 0, null);
        } finally { canvas.dispose(); }
        // Never modify or return the shared base image. Each authorized response is personalized.
        watermark(image, base.getHeight(), user, session, page);
        try (var output = new ByteArrayOutputStream()) {
          var writers = ImageIO.getImageWritersByFormatName("jpeg");
          if (!writers.hasNext()) throw new IOException();
          var writer = writers.next();
          try (var pixels = new MemoryCacheImageOutputStream(output)) {
            writer.setOutput(pixels); writer.write(image); pixels.flush();
            return output.toByteArray();
          } finally { writer.dispose(); }
        }
      } finally { image.flush(); }
    } catch (IOException ex) { clear(); throw new ApiException(422, "Book page could not be rendered"); }
    finally { capacity.release(); }
  }

  BufferedImage rasterize(PDDocument pdf, int page) throws IOException {
    var box = pdf.getPage(page - 1).getCropBox();
    float width = box.getWidth(), height = box.getHeight();
    if (!Float.isFinite(width) || !Float.isFinite(height) || width < 10 || height < 10
        || width > 14400 || height > 14400)
      throw new ApiException(422, "Unsupported PDF page dimensions");
    float scale = Math.min(2.0f, 1600f / Math.max(width, height));
    var renderer = new PDFRenderer(pdf);
    renderer.setSubsamplingAllowed(true);
    return renderer.renderImage(page - 1, scale, ImageType.RGB);
  }

  private static long memory(BufferedImage image) { return (long) image.getWidth() * image.getHeight() * 4; }
  private static boolean expired(CachedBook book, long now) {
    return now - book.used >= IDLE_NANOS || now - book.created >= LIFE_NANOS;
  }
  private void acquire() {
    if (!capacity.tryAcquire()) throw new ApiException(503, "Reader is busy. Please try again shortly");
  }
  private void clear() { if (cached != null) { cached.clear(); cached = null; } }

  @Scheduled(fixedDelay = 30000, initialDelay = 30000)
  public void expireCache() {
    if (!capacity.tryAcquire()) return;
    try { if (cached != null && expired(cached, clock.getAsLong())) clear(); }
    finally { capacity.release(); }
  }

  @PreDestroy
  public void close() {
    capacity.acquireUninterruptibly();
    try { clear(); } finally { capacity.release(); }
  }

  private static int footerSize(int width) { return Math.max(10, width / 60); }
  private void watermark(BufferedImage image, int contentHeight, UUID user, UUID session, int page) {
    var g = image.createGraphics();
    try {
      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      int size = Math.max(12, image.getWidth() / 44);
      String owner = "SR English Tak | " + user;
      String trace = session.toString().substring(0, 8) + " | p" + page + " | "
          + Instant.now().toString().substring(0, 16);
      g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, size));
      int measured = g.getFontMetrics().stringWidth(owner);
      if (measured > image.getWidth() * .9)
        g.setFont(g.getFont().deriveFont(Math.max(10f, size * image.getWidth() * .9f / measured)));
      AffineTransform original = g.getTransform();
      g.setComposite(AlphaComposite.SrcOver.derive(.08f));
      g.setColor(new Color(50, 55, 100));
      // Two faint lines instead of a repeated overlay across the entire document.
      for (int index = 0; index < 2; index++) {
        int y = (int) (contentHeight * (index == 0 ? .34 : .68));
        String label = index == 0 ? owner : trace;
        g.setTransform(original); g.rotate(-Math.PI / 10, image.getWidth() / 2.0, y);
        g.drawString(label, Math.max(8, (image.getWidth() - g.getFontMetrics().stringWidth(label)) / 2), y);
      }
      g.setTransform(original); g.setComposite(AlphaComposite.SrcOver);
      g.setColor(new Color(70, 70, 85));
      g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, footerSize(image.getWidth())));
      // Footer remains outside document content and retains full ownership/session traceability.
      g.drawString(owner, 8, image.getHeight() - footerSize(image.getWidth()) - 8);
      g.drawString(trace, 8, image.getHeight() - 7);
    } finally { g.dispose(); }
  }
}
