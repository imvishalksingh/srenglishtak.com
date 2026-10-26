package com.readiq.backend.reader;

import com.readiq.backend.api.ApiException;
import com.readiq.backend.service.StorageService;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.*;
import org.springframework.stereotype.Service;

/** Originals stay on the server; only one watermarked raster page is returned per request. */
@Service
public class BookPageRenderer {
  private final StorageService storage;
  private final Semaphore capacity = new Semaphore(1);
  private static final int MAX_BYTES = 50 * 1024 * 1024;

  public BookPageRenderer(StorageService storage) { this.storage = storage; }

  private PDDocument open(Map<String, Object> book) throws IOException {
    if (!"pdf".equals(book.get("format_type")))
      throw new ApiException(422, "This book needs a protected PDF edition. Contact support");
    String url = java.util.Objects.toString(book.get("file_url"), "");
    if (!storage.privateFile(url))
      throw new ApiException(503, "This book must be moved to private storage before reading");
    storage.requirePrivateBucket();
    byte[] bytes;
    try (var input = storage.download(url)) {
      bytes = input.readNBytes(MAX_BYTES + 1);
    }
    if (bytes.length > MAX_BYTES) throw new ApiException(413, "Book exceeds the reader limit");
    var pdf = Loader.loadPDF(bytes);
    if (pdf.getNumberOfPages() < 1 || pdf.getNumberOfPages() > 2000 || pdf.isEncrypted()) {
      pdf.close();
      throw new ApiException(422, "Book format is not supported by the protected reader");
    }
    return pdf;
  }

  public int pageCount(Map<String, Object> book) {
    acquire();
    try (var pdf = open(book)) {
      return pdf.getNumberOfPages();
    } catch (IOException ex) {
      throw new ApiException(422, "Book PDF could not be opened");
    } finally { capacity.release(); }
  }

  public byte[] render(Map<String, Object> book, int page, UUID user, UUID session) {
    acquire();
    try (var pdf = open(book)) {
      if (page < 1 || page > pdf.getNumberOfPages()) throw ApiException.missing();
      var box = pdf.getPage(page - 1).getCropBox();
      float width = box.getWidth(), height = box.getHeight();
      if (!Float.isFinite(width) || !Float.isFinite(height) || width < 10 || height < 10
          || width > 14400 || height > 14400)
        throw new ApiException(422, "Unsupported PDF page dimensions");
      float scale = Math.min(2.0f, 1600f / Math.max(width, height));
      var renderer = new PDFRenderer(pdf);
      renderer.setSubsamplingAllowed(true);
      BufferedImage pageImage = renderer.renderImage(page - 1, scale, ImageType.RGB);
      // Put the ownership footer outside the document; never cover the book's bottom lines.
      int footer = Math.max(12, pageImage.getWidth() / 44) * 3;
      BufferedImage image = new BufferedImage(pageImage.getWidth(), pageImage.getHeight() + footer, BufferedImage.TYPE_INT_RGB);
      var canvas = image.createGraphics();
      try {
        canvas.setColor(Color.WHITE);
        canvas.fillRect(0, 0, image.getWidth(), image.getHeight());
        canvas.drawImage(pageImage, 0, 0, null);
      } finally { canvas.dispose(); pageImage.flush(); }
      watermark(image, user, session, page);
      try (var output = new ByteArrayOutputStream()) {
        if (!ImageIO.write(image, "jpeg", output)) throw new IOException();
        return output.toByteArray();
      } finally { image.flush(); }
    } catch (IOException ex) {
      throw new ApiException(422, "Book page could not be rendered");
    } finally { capacity.release(); }
  }

  private void acquire() {
    if (!capacity.tryAcquire()) throw new ApiException(503, "Reader is busy. Please try again shortly");
  }

  private void watermark(BufferedImage image, UUID user, UUID session, int page) {
    var g = image.createGraphics();
    try {
      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      int size = Math.max(12, image.getWidth() / 44);
      g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size));
      String owner = "SR English Tak | " + user;
      String trace = session.toString().substring(0, 8) + " | p" + page + " | "
          + Instant.now().toString().substring(0, 16);
      AffineTransform original = g.getTransform();
      g.setComposite(AlphaComposite.SrcOver.derive(0.19f));
      g.setColor(new Color(50, 55, 100));
      g.rotate(-Math.PI / 7, image.getWidth() / 2.0, image.getHeight() / 2.0);
      for (int y = -image.getHeight(); y < image.getHeight() * 2; y += size * 7) {
        g.drawString(owner, 16, y);
        g.drawString(trace, 16, y + size + 4);
      }
      g.setTransform(original);
      g.setComposite(AlphaComposite.SrcOver);
      g.setColor(new Color(255, 255, 255, 220));
      g.fillRect(0, image.getHeight() - size * 3, image.getWidth(), size * 3);
      g.setColor(new Color(45, 45, 65));
      g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(10, size - 3)));
      g.drawString(owner, 8, image.getHeight() - size - 8);
      g.drawString(trace, 8, image.getHeight() - 7);
    } finally { g.dispose(); }
  }
}
