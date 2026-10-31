package com.readiq.backend.service;

import com.readiq.backend.api.ApiException;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.concurrent.Semaphore;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.stereotype.Service;

/** Only decoded cover pixels leave this service. Original bytes, metadata and documents never do. */
@Service
public class BookCoverService {
  private final StorageService storage;
  private final Semaphore capacity = new Semaphore(2);
  public BookCoverService(StorageService storage) { this.storage = storage; }

  public byte[] image(String location) {
    if (location == null || location.isBlank()) throw ApiException.missing();
    if (!capacity.tryAcquire()) throw new ApiException(503, "Cover image temporarily unavailable");
    try (var input = storage.downloadCover(location)) {
      final int limit = 8 * 1024 * 1024;
      byte[] data;
      try { data = input.readNBytes(limit + 1); }
      catch (IOException failure) { throw new ApiException(503, "Cover image temporarily unavailable"); }
      if (data.length == 0 || data.length > limit) throw ApiException.bad("Invalid cover image");
      try (var pixels = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
        var readers = ImageIO.getImageReaders(pixels);
        if (!readers.hasNext()) throw ApiException.bad("Invalid cover image");
        var reader = readers.next();
        BufferedImage decoded = null, clean = null;
        try {
          reader.setInput(pixels, true, true);
          int width = reader.getWidth(0), height = reader.getHeight(0);
          if (width < 1 || height < 1 || width > 8192 || height > 8192 || (long) width * height > 36_000_000)
            throw ApiException.bad("Invalid cover image dimensions");
          var options = reader.getDefaultReadParam();
          int sample = Math.max(1, (int) Math.ceil(Math.max(width, height) / 1600.0));
          options.setSourceSubsampling(sample, sample, 0, 0);
          decoded = reader.read(0, options);
          clean = new BufferedImage(decoded.getWidth(), decoded.getHeight(), BufferedImage.TYPE_INT_RGB);
          var canvas = clean.createGraphics();
          try { canvas.setColor(Color.WHITE); canvas.fillRect(0, 0, clean.getWidth(), clean.getHeight()); canvas.drawImage(decoded, 0, 0, null); }
          finally { canvas.dispose(); }
          try (var output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(clean, "jpeg", output)) throw ApiException.bad("Invalid cover image");
            return output.toByteArray();
          }
        } finally {
          reader.dispose();
          if (decoded != null) decoded.flush();
          if (clean != null) clean.flush();
        }
      }
    } catch (ApiException failure) { throw failure; }
    catch (IOException | RuntimeException failure) { throw ApiException.bad("Cover image could not be loaded"); }
    finally { capacity.release(); }
  }
}
