package com.readiq.backend.reader;

import java.awt.image.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.*;

/** Lossless neutral pixels in server RAM. No watermark, quality loss, file or delivery URL. */
final class CompressedReaderRaster {
  private final int width, height;
  private final byte[] pixels;

  private CompressedReaderRaster(int width, int height, byte[] pixels) {
    this.width = width;
    this.height = height;
    this.pixels = pixels;
  }

  static CompressedReaderRaster pack(BufferedImage image) throws IOException {
    int width = image.getWidth(), height = image.getHeight();
    if (width < 1
        || height < 1
        || width > 1602
        || height > 1602
        || image.getType() != BufferedImage.TYPE_INT_RGB)
      throw new IOException("Unsupported reader raster");
    byte[] raw = new byte[width * height * 4];
    var deflater = new Deflater(Deflater.BEST_SPEED);
    try (var output = new ByteArrayOutputStream()) {
      var source = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
      ByteBuffer.wrap(raw).asIntBuffer().put(source, 0, width * height);
      try (var stream = new DeflaterOutputStream(output, deflater)) {
        stream.write(raw);
      }
      return new CompressedReaderRaster(width, height, output.toByteArray());
    } finally {
      Arrays.fill(raw, (byte) 0);
      deflater.end();
    }
  }

  BufferedImage unpack() throws IOException {
    byte[] raw;
    try (var input = new InflaterInputStream(new ByteArrayInputStream(pixels))) {
      raw = input.readNBytes(width * height * 4 + 1);
    }
    try {
      if (raw.length != width * height * 4) throw new IOException("Invalid reader raster");
      var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
      ByteBuffer.wrap(raw)
          .asIntBuffer()
          .get(((DataBufferInt) image.getRaster().getDataBuffer()).getData());
      return image;
    } finally {
      Arrays.fill(raw, (byte) 0);
    }
  }

  int bytes() {
    return pixels.length;
  }

  void clear() {
    Arrays.fill(pixels, (byte) 0);
  }
}
