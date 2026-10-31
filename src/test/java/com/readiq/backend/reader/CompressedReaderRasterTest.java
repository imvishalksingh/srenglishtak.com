package com.readiq.backend.reader;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.Test;

class CompressedReaderRasterTest {
  @Test
  void compressionIsLosslessAndDoesNotModifyTheSourceImage() throws Exception {
    var image = new BufferedImage(640, 900, BufferedImage.TYPE_INT_RGB);
    var random = new Random(231);
    for (int y = 0; y < 900; y++)
      for (int x = 0; x < 640; x++) image.setRGB(x, y, random.nextInt());
    int[] original = image.getRGB(0, 0, 640, 900, null, 0, 640);
    var packed = CompressedReaderRaster.pack(image);
    var restored = packed.unpack();
    assertArrayEquals(original, restored.getRGB(0, 0, 640, 900, null, 0, 640));
    assertArrayEquals(original, image.getRGB(0, 0, 640, 900, null, 0, 640));
    image.flush();
    restored.flush();
    packed.clear();
    assertThrows(IOException.class, packed::unpack);
  }

  @Test
  void textStyleBlankPixelsCompressWithinTheSameGlobalCacheBudget() throws Exception {
    var image = new BufferedImage(1224, 1584, BufferedImage.TYPE_INT_RGB);
    var packed = CompressedReaderRaster.pack(image);
    assertTrue(packed.bytes() < 1224 * 1584 * 4 / 10);
    image.flush();
    packed.clear();
  }

  @Test
  void oversizedOrNonRgbBuffersAreRejectedBeforeCreatingACompressedAllocation() {
    var wide = new BufferedImage(1603, 1, BufferedImage.TYPE_INT_RGB);
    var alpha = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
    assertThrows(IOException.class, () -> CompressedReaderRaster.pack(wide));
    assertThrows(IOException.class, () -> CompressedReaderRaster.pack(alpha));
    wide.flush();
    alpha.flush();
  }
}
