package com.readiq.backend.reader;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.readiq.backend.api.ApiException;
import com.readiq.backend.auth.ApiPrincipal;
import com.readiq.backend.data.Database;
import com.readiq.backend.payment.*;
import java.util.*;
import org.junit.jupiter.api.*;

class ReaderBookAccessTest {
  static final class Rows extends Database {
    int reads; Map<String,Object> row;
    Rows() { super(null,null,null); }
    @Override public Map<String,Object> one(String sql,Object... args) {
      reads++; assertTrue(sql.contains("storage.buckets")); assertTrue(sql.contains("reader_sessions"));
      return new HashMap<>(row);
    }
  }
  Rows db; PurchaseService purchases; ReaderBookAccess access;
  final UUID user=UUID.randomUUID(),book=UUID.randomUUID(),session=UUID.randomUUID();
  @BeforeEach void prepare() {
    db=new Rows(); db.row=new HashMap<>(Map.of("price_paise",1000,"is_visible",true,"reader_owned",true,
      "reader_private",true,"reader_stale",false,"reader_public_key","device-key"));
    purchases=mock(PurchaseService.class); var settings=new PaymentSettings();settings.setMode("live");
    access=new ReaderBookAccess(db,settings,purchases);
  }
  @Test void freshPaidAccessAndStorageAndSessionUseOneQuery() {
    assertEquals("device-key",access.load(new ApiPrincipal(user,false),book,session).publicKey());
    assertEquals(1,db.reads);verifyNoInteractions(purchases);
  }
  @Test void revokedPurchaseIsNeverServedFromCachedEntitlements() {
    access.load(new ApiPrincipal(user,false),book,session);db.row.put("reader_owned",false);
    assertEquals(402,assertThrows(ApiException.class,()->access.load(new ApiPrincipal(user,false),book,session)).status());
    assertEquals(2,db.reads);
  }
  @Test void staleReceiptsKeepExistingServerRecheckAndReadFreshStateAfterIt() {
    db.row.put("reader_stale",true);
    doAnswer(call->{db.row.put("reader_stale",false);return null;}).when(purchases).requireAccess(user,book,false);
    access.load(new ApiPrincipal(user,false),book,session);
    verify(purchases).requireAccess(user,book,false);assertEquals(2,db.reads);
  }
  @Test void missingPrivateBucketAndExpiredSessionFailClosed() {
    db.row.put("reader_private",false);
    assertEquals(503,assertThrows(ApiException.class,()->access.load(new ApiPrincipal(user,false),book,session)).status());
    db.row.put("reader_private",true);db.row.remove("reader_public_key");
    assertEquals(403,assertThrows(ApiException.class,()->access.load(new ApiPrincipal(user,false),book,session)).status());
  }
  @Test void hiddenFreeBooksAreNotPublishedAndNullableOldPriceRemainsFree() {
    db.row.put("price_paise",null);db.row.put("reader_owned",false);
    access.load(new ApiPrincipal(user,false),book,null);
    db.row.put("is_visible",false);
    assertEquals(404,assertThrows(ApiException.class,()->access.load(new ApiPrincipal(user,false),book,null)).status());
    access.load(new ApiPrincipal(user,true),book,null);
  }
}
