package com.readiq.backend.reader;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.readiq.backend.api.ApiException;
import com.readiq.backend.service.StorageService;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.junit.jupiter.api.*;

class BookPageRendererTest {
  private StorageService storage;
  private MeasuredRenderer renderer;
  private static class MeasuredRenderer extends BookPageRenderer {
    int rasters;
    MeasuredRenderer(StorageService storage, java.util.function.LongSupplier clock) { super(storage,clock); }
    @Override BufferedImage rasterize(PDDocument pdf,int page) throws IOException {
      rasters++;return super.rasterize(pdf,page);
    }
  }
  private final AtomicLong clock = new AtomicLong();
  private Map<String,Object> book;
  private static final UUID USER = UUID.fromString("10000000-0000-0000-0000-000000000002");
  private static final UUID SESSION = UUID.fromString("20000000-0000-0000-0000-000000000002");
  private byte[] pdf;

  @BeforeEach void prepare() throws Exception {
    storage = mock(StorageService.class);
    when(storage.privateFile(anyString())).thenReturn(true);
    pdf = pdf();
    when(storage.download(anyString())).thenAnswer(call -> new ByteArrayInputStream(pdf));
    renderer = new MeasuredRenderer(storage,clock::get);
    book = new HashMap<>(Map.of("id",UUID.randomUUID(),"format_type","pdf","file_url","https://fixture/storage/v1/object/authenticated/book-files/one.pdf"));
  }
  @AfterEach void close() { renderer.close(); }
  private static byte[] pdf() throws Exception {
    try (var document = new PDDocument(); var bytes = new ByteArrayOutputStream()) {
      for (int index = 1; index <= 3; index++) {
        var page = new PDPage(); document.addPage(page);
        try (var content = new PDPageContentStream(document,page)) {
          content.beginText(); content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),18);
          content.newLineAtOffset(50,700); content.showText("Protected reading page "+index); content.endText();
        }
      }
      document.save(bytes); return bytes.toByteArray();
    }
  }
  private byte[] page(int number) { return renderer.render(book,number,USER,SESSION); }

  @Test void downloadsAndParsesOnceForCountAndMultiplePages() throws Exception {
    assertEquals(3,renderer.pageCount(book));
    page(1); page(2); page(1);
    verify(storage,times(1)).download(anyString());
    verify(storage,times(4)).requirePrivateBucket();
    assertEquals(2,renderer.rasters);
  }
  @Test void cachedRasterNeverContainsAnotherReadersWatermark() throws Exception {
    byte[] first=page(1);
    byte[] second=renderer.render(book,1,UUID.randomUUID(),UUID.randomUUID());
    assertFalse(Arrays.equals(first,second));
    var before=ImageIO.read(new ByteArrayInputStream(first));
    var after=ImageIO.read(new ByteArrayInputStream(page(1)));
    assertArrayEquals(before.getRGB(0,0,1224,900,null,0,1224),after.getRGB(0,0,1224,900,null,0,1224));
    before.flush(); after.flush();
    assertEquals(1,renderer.rasters);
  }
  @Test void storagePrivacyIsCheckedEvenOnWarmCacheAndFailureEvictsIt() {
    page(1);
    doThrow(new ApiException(503,"Private storage required")).when(storage).requirePrivateBucket();
    assertEquals(503,assertThrows(ApiException.class,() -> page(1)).status());
    doNothing().when(storage).requirePrivateBucket(); page(1);
    verify(storage,times(2)).download(anyString());
  }
  @Test void replacementFileOrBookCannotReuseStalePages() {
    page(1);
    book.put("file_url","https://fixture/storage/v1/object/authenticated/book-files/two.pdf"); page(1);
    book.put("id",UUID.randomUUID()); page(1);
    verify(storage,times(3)).download(anyString());
  }
  @Test void idleExpiryAndHardLifetimeReleaseCachedDocument() {
    page(1); clock.addAndGet(Duration.ofMinutes(10).toNanos()); renderer.expireCache(); page(1);
    for (int i=0; i<6; i++) { clock.addAndGet(Duration.ofMinutes(5).toNanos()); page(1); }
    verify(storage,times(3)).download(anyString());
  }
  @Test void preparedJpegSurvivesRasterEvictionWithoutRepeatingPdfRendering() throws Exception {
    page(1); page(2); page(3); page(1);
    verify(storage,times(1)).download(anyString());
    assertEquals(3,renderer.rasters);
  }
  @Test void invalidPageOrPrivateUrlNeverLeaksOriginalBytes() {
    assertEquals(404,assertThrows(ApiException.class,() -> page(0)).status());
    assertEquals(404,assertThrows(ApiException.class,() -> page(4)).status());
    when(storage.privateFile(anyString())).thenReturn(false);
    assertEquals(503,assertThrows(ApiException.class,() -> page(1)).status());
  }
  @Test void malformedPdfIsNotCachedAndRetryCanRecover() {
    doAnswer(call -> new ByteArrayInputStream("%PDF-invalid".getBytes())).when(storage).download(anyString());
    assertEquals(422,assertThrows(ApiException.class,() -> page(1)).status());
    doAnswer(call -> new ByteArrayInputStream(pdf)).when(storage).download(anyString());
    assertTrue(page(1).length>3);
    verify(storage,times(2)).download(anyString());
  }
  @Test void watermarkIsFaintSparseAndFooterDoesNotCoverContent() throws Exception {
    byte[] sample=page(1);
    java.nio.file.Files.write(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"),"readiq-reader-watermark-review.jpg"),sample);
    var image = ImageIO.read(new ByteArrayInputStream(sample));
    assertEquals(1224,image.getWidth()); assertTrue(image.getHeight()>1584);
    int dark=0,changed=0,readable=0;
    // Exclude the book's own first text line. Sparse watermark should affect few pixels.
    for (int y=300;y<1580;y++) for (int x=0;x<image.getWidth();x++) {
      int rgb=image.getRGB(x,y), r=(rgb>>16)&255,g=(rgb>>8)&255,b=rgb&255;
      if (Math.min(r,Math.min(g,b))<150) dark++;
      if (Math.min(r,Math.min(g,b))<250) changed++;
    }
    for (int y=1584;y<image.getHeight();y++) for (int x=0;x<image.getWidth();x++)
      if ((image.getRGB(x,y)&255)<180) readable++;
    assertEquals(0,dark); assertTrue(changed>100); assertTrue(changed<image.getWidth()*1280*.035);
    assertTrue(readable>100); image.flush();
  }
  @Test void switchingBetweenBooksRetainsTheirParsedDocumentsAndWarmPages() throws Exception {
    var second=new HashMap<>(book); second.put("id",UUID.randomUUID()); second.put("file_url","https://fixture/storage/v1/object/authenticated/book-files/second.pdf");
    page(1); renderer.render(second,1,USER,SESSION); page(1);
    verify(storage,times(2)).download(anyString());
    assertEquals(2,renderer.rasters);
  }
  @Test void documentCountAndGlobalPageBudgetEvictLeastRecentlyUsedEntries() throws Exception {
    for(int i=0;i<5;i++) {
      var other=new HashMap<>(book);other.put("id","book-"+i);other.put("file_url","https://fixture/storage/v1/object/authenticated/book-files/"+i+".pdf");
      renderer.render(other,1,USER,SESSION);
    }
    var last=new HashMap<>(book);last.put("id","book-4");last.put("file_url","https://fixture/storage/v1/object/authenticated/book-files/4.pdf");
    renderer.render(last,1,USER,SESSION); // Remains warm.
    var first=new HashMap<>(book);first.put("id","book-0");first.put("file_url","https://fixture/storage/v1/object/authenticated/book-files/0.pdf");
    renderer.render(first,1,USER,SESSION); // Parsed source was evicted.
    verify(storage,times(6)).download(anyString());
    assertEquals(6,renderer.rasters);
    // Personalized JPEGs survive neutral-raster turnover; the source LRU still bounds documents.
    var retained=new HashMap<>(book);retained.put("id","book-3");retained.put("file_url","https://fixture/storage/v1/object/authenticated/book-files/3.pdf");
    renderer.render(retained,1,USER,SESSION);
    verify(storage,times(6)).download(anyString());
    assertEquals(6,renderer.rasters); // Personalized JPEG survived base-raster eviction.
  }
  @Test void losslessNeutralRastersSurviveLongerBooksAndCanBePersonalizedForNewSessions() {
    page(1);page(2);page(3);
    renderer.render(book,1,UUID.randomUUID(),UUID.randomUUID());
    assertEquals(3,renderer.rasters); // No PDF re-render after a new account/session requests page 1.
  }
  @Test void productionPreparationUsesOneRealPdfRenderPerPageAndReusesPersonalizedResponses() throws Exception {
    var warmed=new CountDownLatch(1);var rasters=new java.util.concurrent.atomic.AtomicInteger();
    var live=new BookPageRenderer(storage,System::nanoTime,Duration.ofSeconds(5),true) {
      @Override BufferedImage rasterize(PDDocument document,int page) throws IOException {
        rasters.incrementAndGet();var image=super.rasterize(document,page);
        if(page==3)warmed.countDown();return image;
      }
    };
    try {
      long started=System.nanoTime();byte[] first=live.render(book,2,USER,SESSION);
      long firstMs=(System.nanoTime()-started)/1_000_000;
      assertTrue(warmed.await(5,TimeUnit.SECONDS));
      started=System.nanoTime();byte[] next=live.render(book,3,USER,SESSION);
      long nextMs=(System.nanoTime()-started)/1_000_000;
      started=System.nanoTime();byte[] previous=live.render(book,2,USER,SESSION);
      long previousMs=(System.nanoTime()-started)/1_000_000;
      assertArrayEquals(first,previous);assertNotNull(ImageIO.read(new ByteArrayInputStream(next)));
      assertEquals(2,rasters.get());verify(storage,times(1)).download(anyString());
      System.out.println("LOCAL_READER_PROBE first_ms="+firstMs+" next_ms="+nextMs+" previous_ms="+previousMs);
    } finally {live.close();}
  }
  @Test void limitsConcurrentWorkAndCleanupDoesNotInterruptActiveRendering() throws Exception {
    var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
    doAnswer(call -> { entered.countDown(); assertTrue(release.await(5,TimeUnit.SECONDS)); return new ByteArrayInputStream(pdf); }).when(storage).download(anyString());
    try (var workers=Executors.newVirtualThreadPerTaskExecutor()) {
      var job=workers.submit(() -> page(1));
      try {
        assertTrue(entered.await(5,TimeUnit.SECONDS));
        assertEquals(503,assertThrows(ApiException.class,() -> page(1)).status());
        renderer.expireCache();
      } finally { release.countDown(); }
      assertTrue(job.get(5,TimeUnit.SECONDS).length>3);
    }
    assertTrue(page(1).length>3);
  }
}
