package com.readiq.backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.readiq.backend.api.ApiException;
import com.readiq.backend.service.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class BookCoverServiceTest {
  static byte[] png(int width, int height) throws IOException {
    var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    var canvas = image.createGraphics(); canvas.setColor(Color.RED); canvas.fillRect(0,0,width,height); canvas.dispose();
    var output = new ByteArrayOutputStream(); ImageIO.write(image,"png",output); image.flush(); return output.toByteArray();
  }

  @Test void returnsOnlyReencodedPixelsAndStripsAppendedDocumentData() throws Exception {
    var storage = mock(StorageService.class);
    var bytes = new ByteArrayOutputStream(); bytes.write(png(16,32));
    bytes.write("%PDF-1.4 PRIVATE-DOCUMENT-DATA".getBytes(StandardCharsets.US_ASCII));
    when(storage.downloadCover("cover")).thenAnswer(call -> new ByteArrayInputStream(bytes.toByteArray()));
    byte[] result = new BookCoverService(storage).image("cover");
    assertEquals((byte)0xff,result[0]); assertEquals((byte)0xd8,result[1]);
    assertFalse(new String(result,StandardCharsets.ISO_8859_1).contains("PRIVATE-DOCUMENT-DATA"));
    var decoded = ImageIO.read(new ByteArrayInputStream(result));
    assertEquals(16,decoded.getWidth()); assertEquals(32,decoded.getHeight()); decoded.flush();
  }

  @Test void rejectsPdfHtmlEmptyAndOversizedInput() throws Exception {
    for (byte[] input : java.util.List.of("%PDF-1.4 SECRET".getBytes(),"<html>SECRET</html>".getBytes(),new byte[0],new byte[8*1024*1024+1])) {
      var storage = mock(StorageService.class);
      when(storage.downloadCover("cover")).thenReturn(new ByteArrayInputStream(input));
      var failure = assertThrows(ApiException.class,() -> new BookCoverService(storage).image("cover"));
      assertEquals(400,failure.status()); assertFalse(failure.getMessage().contains("SECRET"));
    }
  }

  @Test void boundsDimensionsAndDownsamplesLargeValidCovers() throws Exception {
    var storage = mock(StorageService.class);
    when(storage.downloadCover("wide")).thenReturn(new ByteArrayInputStream(png(8193,1)));
    assertThrows(ApiException.class,() -> new BookCoverService(storage).image("wide"));
    when(storage.downloadCover("large")).thenReturn(new ByteArrayInputStream(png(2000,1000)));
    var image = ImageIO.read(new ByteArrayInputStream(new BookCoverService(storage).image("large")));
    assertTrue(image.getWidth() <= 1600); assertTrue(image.getHeight() <= 1600); image.flush();
  }

  @Test void missingCoverDoesNotAccessStorageAndFailedReadsCanRecover() throws Exception {
    var storage = mock(StorageService.class);
    var covers = new BookCoverService(storage);
    assertEquals(404,assertThrows(ApiException.class,() -> covers.image("")).status());
    verifyNoInteractions(storage);
    when(storage.downloadCover("cover")).thenThrow(new ApiException(502,"Storage unavailable"));
    assertEquals(502,assertThrows(ApiException.class,() -> covers.image("cover")).status());
    doReturn(new ByteArrayInputStream(png(1,1))).when(storage).downloadCover("cover");
    assertTrue(covers.image("cover").length > 0);
  }

  @Test void limitsConcurrentCoverProcessing() throws Exception {
    var started = new CountDownLatch(2); var release = new CountDownLatch(1);
    byte[] data = png(1,1);
    var storage = mock(StorageService.class);
    when(storage.downloadCover("cover")).thenAnswer(call -> new ByteArrayInputStream(data) {
      @Override public byte[] readNBytes(int count) {
        started.countDown();
        try { if (!release.await(3,TimeUnit.SECONDS)) throw new IllegalStateException(); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(); }
        return data;
      }
    });
    var covers = new BookCoverService(storage);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = executor.submit(() -> covers.image("cover")); var second = executor.submit(() -> covers.image("cover"));
      try {
        assertTrue(started.await(3,TimeUnit.SECONDS));
        assertEquals(503,assertThrows(ApiException.class,() -> covers.image("cover")).status());
      } finally { release.countDown(); }
      assertTrue(first.get(3,TimeUnit.SECONDS).length > 0); assertTrue(second.get(3,TimeUnit.SECONDS).length > 0);
    }
  }
}
