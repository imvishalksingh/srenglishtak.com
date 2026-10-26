package com.readiq.backend;

import static com.readiq.backend.api.Input.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.readiq.backend.api.ApiException;
import com.readiq.backend.auth.SupabaseClient;
import com.readiq.backend.payment.*;
import com.readiq.backend.security.*;
import com.readiq.backend.service.StorageService;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.*;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.*;

/** Real PostgreSQL + full Spring Security/MVC stack; Supabase is mocked at its network boundary. */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "READIQ_TEST_DB_URL", matches = ".+")
class BackendIntegrationTest {
  static final UUID ADMIN = UUID.fromString("10000000-0000-0000-0000-000000000001"),
      USER = UUID.fromString("10000000-0000-0000-0000-000000000002"),
      OTHER = UUID.fromString("10000000-0000-0000-0000-000000000003");
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired RequestMappingHandlerMapping mappings;
  @MockitoBean SupabaseClient supabase;
  @MockitoBean GooglePlayClient google;
  @MockitoBean com.readiq.backend.reader.PlayIntegrityClient integrityGoogle;
  @MockitoBean RazorpayClient razorpay;
  @MockitoSpyBean StorageService storage;
  @Autowired PurchaseService purchases;
  @Autowired DatabaseRateLimitStore limitStore;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry p) {
    p.add("spring.datasource.url", () -> System.getenv("READIQ_TEST_DB_URL"));
    p.add(
        "spring.datasource.username",
        () -> System.getenv().getOrDefault("READIQ_TEST_DB_USER", "readiq_test"));
    p.add(
        "spring.datasource.password",
        () -> System.getenv().getOrDefault("READIQ_TEST_DB_PASSWORD", ""));
    p.add("spring.datasource.hikari.maximum-pool-size", () -> 3);
    p.add("supabase.url", () -> "http://127.0.0.1:9");
    p.add("supabase.key", () -> "test-anon");
    p.add("supabase.service-role-key", () -> "test-service");
    p.add("payments.mode", () -> "test");
    p.add("payments.google-credentials-file", () -> "unused-mocked-test-credentials.json");
    p.add(
        "payments.token-encryption-key",
        () -> java.util.Base64.getEncoder().encodeToString(new byte[32]));
    p.add("payments.razorpay-key-id", () -> "rzp_test_fixture");
    p.add("payments.razorpay-key-secret", () -> "test-secret");
    p.add("payments.razorpay-webhook-secret", () -> "webhook-secret");
    p.add("reader.cloud-project-number", () -> 123);
    p.add("reader.certificate-sha256", () -> "ab".repeat(32));
    p.add("springdoc.api-docs.enabled", () -> true);
    p.add("springdoc.swagger-ui.enabled", () -> true);
    p.add("cors.allowed-origins", () -> "https://admin.example.test");
  }

  @BeforeAll
  void schema() throws Exception {
    var target =
        java.net.URI.create(System.getenv("READIQ_TEST_DB_URL").substring("jdbc:".length()));
    if (!Set.of("127.0.0.1", "localhost").contains(target.getHost())
        || !"/readiq_test".equals(target.getPath()))
      throw new IllegalStateException(
          "Integration tests require a disposable localhost database named readiq_test");
    jdbc.execute(
        "create schema if not exists auth; create table if not exists auth.users(id uuid primary"
            + " key)");
    jdbc.execute(
        "DO $$ BEGIN IF NOT EXISTS(SELECT FROM pg_roles WHERE rolname='anon') THEN CREATE ROLE"
            + " anon; END IF; IF NOT EXISTS(SELECT FROM pg_roles WHERE rolname='authenticated')"
            + " THEN CREATE ROLE authenticated; END IF; END $$");
    jdbc.execute(Files.readString(Path.of("db/001_schema.sql")));
    jdbc.execute(Files.readString(Path.of("db/002_security_and_contract.sql")));
    jdbc.execute(
        "create schema if not exists storage;create table if not exists storage.buckets(id text"
            + " primary key,name text,public boolean);create table if not exists storage.objects(id"
            + " uuid primary key,bucket_id text)");
    jdbc.execute(
        "alter table storage.objects enable row level security;grant usage on schema storage to"
            + " authenticated;grant select on storage.objects to authenticated");
    jdbc.execute(
        "DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM pg_policies WHERE schemaname='storage' AND"
            + " policyname='test_old_broad_policy') THEN CREATE POLICY test_old_broad_policy ON"
            + " storage.objects FOR SELECT TO authenticated USING(true); END IF; END $$");
    jdbc.execute(Files.readString(Path.of("db/003_purchases_and_limits.sql")));
    jdbc.execute(Files.readString(Path.of("db/004_protected_reader.sql")));
  }

  @BeforeEach
  void data() {
    doNothing().when(storage).requirePrivateBucket();
    doNothing().when(storage).requirePrivateFile(anyString());
    jdbc.execute("truncate api_rate_limits,revoked_access_tokens,purchase_revocations");
    jdbc.execute(
        "truncate auth.users,grammar_units,cbse_categories,daily_tips,vocabulary,challenges"
            + " cascade");
    for (UUID id : List.of(ADMIN, USER, OTHER)) {
      jdbc.update("insert into auth.users(id) values(?)", id);
      jdbc.update(
          "insert into profiles(id,email,full_name,role) values(?,?,?,?)",
          id,
          id + "@example.test",
          "Reader " + id.toString().substring(35),
          id.equals(ADMIN) ? "admin" : "user");
    }
    when(supabase.auth(eq("GET"), eq("/user"), isNull(), anyString()))
        .thenAnswer(
            call -> {
              String token = call.getArgument(3);
              UUID id =
                  switch (token) {
                    case "admin" -> ADMIN;
                    case "user" -> USER;
                    case "other" -> OTHER;
                    default -> throw new ApiException(401, "Invalid token");
                  };
              // A forged metadata role must never grant admin.
              return map(
                  "id",
                  id.toString(),
                  "email",
                  id + "@example.test",
                  "user_metadata",
                  map("role", "admin"));
            });
  }

  private MockHttpServletRequestBuilder request(MockHttpServletRequestBuilder b, String token) {
    if (token != null) b.header("Authorization", "Bearer " + token);
    return b.with(
        r -> {
          r.setRemoteAddr("192.0.2." + Math.floorMod(TestInfoHolder.counter++, 200));
          return r;
        });
  }

  private static class TestInfoHolder {
    static int counter = 1;
  }

  private JsonNode response(MvcResult r) throws Exception {
    return json.readTree(r.getResponse().getContentAsString());
  }

  private JsonNode postJson(String path, String token, Object body, int status) throws Exception {
    return response(
        mvc.perform(
                request(post(path), token)
                    .contentType("application/json")
                    .content(json.writeValueAsString(body)))
            .andExpect(status().is(status))
            .andReturn());
  }

  private JsonNode book(boolean visible) throws Exception {
    return postJson(
        "/api/books",
        "admin",
        map("title", "Test book", "format_type", "pdf", "is_visible", visible),
        201);
  }

  private Map<String, Object> question(boolean challenge, int correct) {
    return map(
        "question_text",
        "Choose the correct word",
        "options",
        challenge
            ? List.of("one", "two", "three", "four")
            : List.of(
                map("label", "A", "text", "one"),
                map("label", "B", "text", "two"),
                map("label", "C", "text", "three"),
                map("label", "D", "text", "four")),
        "correct_option_index",
        correct);
  }

  private JsonNode quiz() throws Exception {
    return postJson(
        "/api/quizzes",
        "admin",
        map(
            "title",
            "Quiz",
            "type",
            "general",
            "duration_minutes",
            5,
            "questions",
            List.of(question(false, 1))),
        201);
  }

  private JsonNode challenge() throws Exception {
    return postJson(
        "/api/challenges",
        "admin",
        map("name", "Challenge", "duration_minutes", 5, "questions", List.of(question(true, 1))),
        201);
  }

  @Test
  void publicCatalogAndAdminBoundary() throws Exception {
    mvc.perform(get("/api/books")).andExpect(status().isOk()).andExpect(content().json("[]"));
    mvc.perform(get("/api/admin/stats")).andExpect(status().isUnauthorized());
    postJson("/api/books", "user", map("title", "forbidden"), 403);
    var hidden = book(false);
    mvc.perform(get("/api/books/" + hidden.get("id").asText())).andExpect(status().isNotFound());
    mvc.perform(request(get("/api/books/" + hidden.get("id").asText()), "admin"))
        .andExpect(status().isOk());
    mvc.perform(request(get("/api/admin/stats"), "bad")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/books/not-a-uuid")).andExpect(status().isBadRequest());
  }

  @Test
  void profileCannotEscalateRole() throws Exception {
    mvc.perform(
            request(patch("/api/user/profile"), "user")
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        map(
                            "full_name",
                            "Updated",
                            "role",
                            "admin",
                            "id",
                            OTHER.toString(),
                            "email",
                            "takeover@example.test"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("user"))
        .andExpect(jsonPath("$.id").value(USER.toString()));
    postJson("/api/books", "user", map("title", "still forbidden"), 403);
  }

  @Test
  void annotationsAndSessionsStayOwnedAndFlat() throws Exception {
    String book = book(true).get("id").asText();
    var mark =
        postJson(
            "/api/user/bookmarks",
            "user",
            map("book_id", book, "page", 4, "user_id", OTHER.toString()),
            201);
    assertEquals(USER.toString(), mark.get("user_id").asText());
    assertEquals(book, mark.get("book_id").asText());
    assertFalse(mark.has("book"));
    mvc.perform(request(delete("/api/user/bookmarks/" + mark.get("id").asText()), "other"))
        .andExpect(status().isNotFound());
    mvc.perform(request(get("/api/user/bookmarks"), "other")).andExpect(content().json("[]"));
    var note =
        postJson(
            "/api/user/notes",
            "user",
            map("book_id", book, "note_text", "My note", "page", 4),
            201);
    mvc.perform(request(delete("/api/user/notes/" + note.get("id").asText()), "other"))
        .andExpect(status().isNotFound());
    var session =
        postJson(
            "/api/user/reading-sessions",
            "user",
            map("book_id", book, "start_page", 4, "user_id", OTHER.toString()),
            201);
    assertEquals(4, session.get("end_page").asInt());
    assertEquals(book, session.get("book_id").asText());
    mvc.perform(
            request(patch("/api/user/reading-sessions/" + session.get("id").asText()), "other")
                .contentType("application/json")
                .content("{\"minutes_spent\":5}"))
        .andExpect(status().isNotFound());
    mvc.perform(
            request(patch("/api/user/reading-sessions/" + session.get("id").asText()), "user")
                .contentType("application/json")
                .content("{\"minutes_spent\":5,\"end_page\":9}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.minutes_spent").value(5));
    postJson(
        "/api/user/progress",
        "user",
        map("book_id", book, "page", 9, "total_minutes_read", 5),
        200);
    mvc.perform(request(get("/api/user/progress"), "user"))
        .andExpect(jsonPath("$[0].books.title").value("Test book"));
  }

  @Test
  void quizAnswersArePrivateAndScoringIsAuthoritative() throws Exception {
    var quiz = quiz();
    String id = quiz.get("id").asText(), q = quiz.get("questions").get(0).get("id").asText();
    mvc.perform(get("/api/quizzes/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.questions[0].correct_option_index").doesNotExist());
    var scored =
        postJson(
            "/api/quizzes/" + id + "/submit",
            "user",
            map(
                "score",
                999,
                "answers",
                List.of(map("question_id", q, "selected_option_index", 0))),
            201);
    assertEquals(0, scored.get("score").asInt());
    assertEquals(1, scored.get("total").asInt());
    mvc.perform(request(get("/api/quizzes"), "user"))
        .andExpect(jsonPath("$[0].has_submitted").value(true));
    postJson(
        "/api/quizzes/" + id + "/submit",
        "user",
        map(
            "answers",
            List.of(map("question_id", UUID.randomUUID().toString(), "selected_option_index", 1))),
        400);
    assertEquals(1, jdbc.queryForObject("select count(*) from quiz_attempts", Integer.class));
  }

  @Test
  void invalidQuizCreationRollsBack() throws Exception {
    var invalid = question(false, 5);
    postJson(
        "/api/quizzes",
        "admin",
        map("title", "Will roll back", "questions", List.of(invalid)),
        400);
    assertEquals(0, jdbc.queryForObject("select count(*) from quizzes", Integer.class));
  }

  @Test
  void challengeScoringTimingAndDuplicateProtection() throws Exception {
    var c = challenge();
    String id = c.get("id").asText(), q = c.get("challenge_questions").get(0).get("id").asText();
    mvc.perform(request(get("/api/challenges"), "user"))
        .andExpect(jsonPath("$[0].challenge_questions").isEmpty())
        .andExpect(jsonPath("$[0].question_count").value(1));
    postJson("/api/challenges/" + id + "/submit", "user", map("answers", List.of()), 404);
    var start = postJson("/api/challenges/" + id + "/start", "user", Map.of(), 200);
    assertTrue(start.has("expires_at"));
    assertTrue(start.get("challenge").get("challenge_questions").get(0).has("question_text"));
    assertFalse(
        start.get("challenge").get("challenge_questions").get(0).has("correct_option_index"));
    postJson(
        "/api/challenges/" + id + "/submit",
        "user",
        map("score", 99999, "answers", List.of()),
        400);
    var result =
        postJson(
            "/api/challenges/" + id + "/submit",
            "user",
            map(
                "time_taken_ms",
                0,
                "answers",
                List.of(map("question_id", q, "selected_option_index", 1))),
            201);
    assertEquals(10, result.get("score").asInt());
    postJson("/api/challenges/" + id + "/submit", "user", map("answers", List.of()), 409);
    mvc.perform(get("/api/challenges/" + id + "/leaderboard"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].profiles.full_name").exists())
        .andExpect(jsonPath("$[0].profiles.email").doesNotExist());
    postJson("/api/challenges/" + id + "/start", "other", Map.of(), 200);
    jdbc.update(
        "update challenge_attempt_sessions set expires_at=now()-interval '1 minute' where"
            + " user_id=?",
        OTHER);
    postJson("/api/challenges/" + id + "/submit", "other", map("answers", List.of()), 410);
  }

  @Test
  void grammarBulkIsAtomicAndProgressIsPerUser() throws Exception {
    var unit = postJson("/api/grammar/units", "admin", map("title", "Unit", "unit_order", 1), 201);
    String id = unit.get("id").asText();
    postJson(
        "/api/grammar/units/" + id + "/lessons/bulk",
        "admin",
        map("lessons", List.of(map("title", "First", "lesson_order", 1), map("lesson_order", 2))),
        400);
    assertEquals(0, jdbc.queryForObject("select count(*) from grammar_lessons", Integer.class));
    var lesson =
        postJson(
            "/api/grammar/units/" + id + "/lessons",
            "admin",
            map(
                "title",
                "Lesson",
                "lesson_order",
                1,
                "content_type",
                "text",
                "content_data",
                "Learn"),
            201);
    assertEquals(id, lesson.get("unit_id").asText());
    postJson(
        "/api/grammar/lessons/" + lesson.get("id").asText() + "/complete", "user", Map.of(), 200);
    mvc.perform(request(get("/api/grammar/progress"), "other")).andExpect(content().json("[]"));
  }

  @Test
  void cbseVocabularyTipsAndInterestContracts() throws Exception {
    var category =
        postJson("/api/cbse/categories", "admin", map("name", "Class 12", "order_index", 1), 201);
    var material =
        postJson(
            "/api/cbse/categories/" + category.get("id").asText() + "/materials",
            "admin",
            map(
                "title",
                "Paper",
                "file_url",
                "https://example.test/paper.pdf",
                "material_type",
                "pyq"),
            201);
    assertEquals(category.get("id"), material.get("category_id"));
    postJson(
        "/api/vocabulary/bulk",
        "admin",
        map("items", List.of(map("word", "Read", "meaning", "Study text"))),
        201);
    postJson("/api/tips", "admin", map("title", "Tip", "content", "Read daily"), 201);
    var interest =
        postJson("/api/interests", "user", map("moduleTitle", "Speaking", "isExcited", true), 200);
    assertEquals(1, interest.get("excitedCount").asInt());
    assertTrue(interest.get("isExcited").asBoolean());
    mvc.perform(request(get("/api/interests/Speaking"), "other"))
        .andExpect(jsonPath("$.isExcited").value(false));
    postJson("/api/interests", "user", map("moduleTitle", "Speaking", "isExcited", "false"), 400);
  }

  @Test
  void dashboardAndPersonalSummariesHaveClientFields() throws Exception {
    String book = book(true).get("id").asText();
    postJson(
        "/api/user/progress", "user", map("book_id", book, "page", 10, "is_completed", true), 200);
    mvc.perform(request(get("/api/admin/stats"), "admin"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_books").value(1))
        .andExpect(jsonPath("$.completed_books").value(1));
    mvc.perform(request(get("/api/admin/users/" + USER + "/progress"), "admin"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.book_progress[0].books.title").value("Test book"))
        .andExpect(jsonPath("$.quiz_attempts").isArray())
        .andExpect(jsonPath("$.reading_sessions").isArray());
    mvc.perform(request(get("/api/user/achievements"), "user"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.completed_books").value(1))
        .andExpect(jsonPath("$.achievements[0].code").value("book_finisher"));
    mvc.perform(request(get("/api/user/recommendations"), "user")).andExpect(status().isOk());
  }

  @Test
  void googleAndRefreshNormalizeRawSupabaseSessions() throws Exception {
    var raw =
        map(
            "access_token",
            "test-access",
            "refresh_token",
            "test-refresh",
            "expires_in",
            3600,
            "user",
            map("id", USER.toString(), "email", USER + "@example.test"));
    when(supabase.auth(eq("POST"), eq("/token?grant_type=id_token"), anyMap(), isNull()))
        .thenReturn(raw);
    var result = postJson("/api/auth/google", null, map("id_token", "verified-by-supabase"), 200);
    assertTrue(
        result.get("session").get("expires_at").asLong() > Instant.now().getEpochSecond() + 3500);
    assertEquals("user", result.get("user").get("role").asText());
  }

  @Test
  void corsAndUploadRejection() throws Exception {
    mvc.perform(
            options("/api/books")
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isForbidden());
    var file = new MockMultipartFile("file", "fake.pdf", "application/pdf", "not a pdf".getBytes());
    mvc.perform(multipart("/api/upload").file(file).header("Authorization", "Bearer user"))
        .andExpect(status().isForbidden());
    mvc.perform(multipart("/api/upload").file(file).header("Authorization", "Bearer admin"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void oversizedJsonIsRejectedBeforeAuthentication() throws Exception {
    mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content("{\"password\":\"" + "x".repeat(1024 * 1024) + "\"}"))
        .andExpect(status().isPayloadTooLarge());
  }

  @Test
  void databaseApiRolesCannotReadAnswerKeys() {
    jdbc.execute(
        (ConnectionCallback<Void>)
            connection -> {
              try (var statement = connection.createStatement()) {
                statement.execute("set role authenticated");
                assertThrows(
                    SQLException.class, () -> statement.executeQuery("select * from questions"));
                statement.execute("reset role");
              }
              return null;
            });
  }

  @Test
  void everyExistingNodeEndpointHasASpringHandler() throws Exception {
    Set<String> actual = new HashSet<>();
    mappings
        .getHandlerMethods()
        .forEach(
            (mapping, method) -> {
              for (String path : mapping.getPatternValues())
                for (var verb : mapping.getMethodsCondition().getMethods())
                  actual.add(verb.name() + " " + path.replaceAll("\\{[^}]+}", "{}"));
            });
    for (String route : Files.readAllLines(Path.of("src/test/resources/node-contract-routes.txt")))
      if (!route.isBlank())
        assertTrue(actual.contains(route.replaceAll(":[^/]+", "{}")), "Missing route: " + route);
  }

  @Test
  void phoneBookPayloadCreatesFreeAndPaidBooksWithBlankCoverAndNullableProduct() throws Exception {
    var body = map("title", "Mobile upload", "author", "", "description", "", "category", "Classic Literature",
        "format_type", "pdf", "file_url", "http://127.0.0.1:9/storage/v1/object/authenticated/book-files/mobile.pdf",
        "cover_url", "", "is_visible", true, "price_paise", 0, "currency", "INR", "android_product_id", null);
    var free = postJson("/api/books", "admin", body, 201);
    assertEquals(0, free.path("price_paise").asInt());
    assertTrue(free.path("android_product_id").isNull());
    body.put("title", "Mobile paid upload");
    body.put("price_paise", 29900);
    body.put("android_product_id", "book.mobile_upload_01");
    var paid = postJson("/api/books", "admin", body, 201);
    assertEquals("book.mobile_upload_01", paid.path("android_product_id").asText());
    assertEquals(29900, paid.path("price_paise").asInt());
  }

  @Test
  void duplicateBookProductsGiveAConflictAndEditingTheSameMappingIsAllowed() throws Exception {
    String first = paidBook().path("id").asText();
    var body = map("title", "Another paid book", "format_type", "pdf", "price_paise", 100,
        "android_product_id", "readiq.book.fixture",
        "file_url", "http://127.0.0.1:9/storage/v1/object/authenticated/book-files/another.pdf");
    var conflict = postJson("/api/books", "admin", body, 409);
    assertEquals(ApiException.bookProductAlreadyAssigned().getMessage(), conflict.path("error").asText());
    body.put("android_product_id", "  book.another_01  ");
    var another = postJson("/api/books", "admin", body, 201);
    assertEquals("book.another_01", another.path("android_product_id").asText());
    mvc.perform(request(patch("/api/books/" + another.path("id").asText()), "admin")
        .contentType("application/json").content(json.writeValueAsString(map("android_product_id", "readiq.book.fixture"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value(ApiException.bookProductAlreadyAssigned().getMessage()));
    mvc.perform(request(patch("/api/books/" + first), "admin")
        .contentType("application/json").content(json.writeValueAsString(map("android_product_id", "readiq.book.fixture"))))
        .andExpect(status().isOk());
    assertEquals(2, jdbc.queryForObject("select count(*) from books", Integer.class));
  }

  private JsonNode paidBook() throws Exception {
    return postJson(
        "/api/books",
        "admin",
        map(
            "title",
            "Paid book",
            "format_type",
            "pdf",
            "is_visible",
            true,
            "price_paise",
            9900,
            "android_product_id",
            "readiq.book.fixture",
            "file_url",
            "http://127.0.0.1:9/storage/v1/object/authenticated/book-files/test.pdf"),
        201);
  }

  private JsonNode googleReceipt(UUID user) {
    return json.valueToTree(
        map(
            "purchaseStateContext",
            map("purchaseState", "PURCHASED"),
            "obfuscatedExternalAccountId",
            PurchaseService.account(user),
            "testPurchaseContext",
            map("fopType", "TEST"),
            "orderId",
            "GPA.fixture",
            "acknowledgementState",
            "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
            "productLineItem",
            List.of(
                map(
                    "productId",
                    "readiq.book.fixture",
                    "productOfferDetails",
                    map("quantity", 1, "refundableQuantity", 1)))));
  }

  @Test
  void paidBooksRequireServerVerifiedAccountBoundOwnership() throws Exception {
    var book = paidBook();
    String id = book.get("id").asText();
    String token = "google-test-purchase-token";
    mvc.perform(get("/api/books")).andExpect(jsonPath("$[0].file_url").isEmpty());
    mvc.perform(request(get("/api/books/" + id + "/stream"), "user"))
        .andExpect(status().isGone());
    mvc.perform(request(get("/api/books/" + id + "/access"), "other"))
        .andExpect(jsonPath("$.has_access").value(false));
    when(google.purchase(token)).thenReturn(googleReceipt(USER));
    postJson(
        "/api/payments/google/verify", "other", map("book_id", id, "purchase_token", token), 403);
    postJson(
        "/api/payments/google/verify", "user", map("book_id", id, "purchase_token", token), 200);
    postJson(
        "/api/payments/google/verify", "user", map("book_id", id, "purchase_token", token), 200);
    assertEquals(1, jdbc.queryForObject("select count(*) from book_purchases", Integer.class));
    mvc.perform(request(get("/api/books/" + id + "/access"), "user"))
        .andExpect(jsonPath("$.has_access").value(true));
    String stored = jdbc.queryForObject("select encrypted_token from book_purchases", String.class);
    assertFalse(stored.contains(token));
    jdbc.update("update book_purchases set checked_at=now()-interval '10 minutes'");
    when(google.purchase(token))
        .thenThrow(new ApiException(503, "Provider temporarily unavailable"));
    mvc.perform(request(get("/api/books/" + id + "/access"), "user"))
        .andExpect(status().isServiceUnavailable());
    doReturn(googleReceipt(USER)).when(google).purchase(token);
    mvc.perform(request(get("/api/books/" + id + "/access"), "user"))
        .andExpect(jsonPath("$.has_access").value(true));
    jdbc.update("update books set is_visible=false where id=?", UUID.fromString(id));
    mvc.perform(request(get("/api/books/" + id + "/access"), "user"))
        .andExpect(jsonPath("$.has_access").value(true));
    mvc.perform(request(get("/api/user/purchased-books"), "user"))
        .andExpect(jsonPath("$[0].id").value(id))
        .andExpect(jsonPath("$[0].file_url").isEmpty());
    jdbc.update("update books set is_visible=true where id=?", UUID.fromString(id));

    var refunded = googleReceipt(USER).deepCopy();
    ((tools.jackson.databind.node.ObjectNode) refunded.path("purchaseStateContext"))
        .put("purchaseState", "CANCELLED");
    when(google.purchase(token)).thenReturn(refunded);
    jdbc.update("update book_purchases set checked_at=now()-interval '10 minutes'");
    mvc.perform(request(get("/api/books/" + id + "/access"), "user"))
        .andExpect(jsonPath("$.has_access").value(false));
    when(google.purchase(token)).thenReturn(googleReceipt(USER));
    postJson(
        "/api/payments/google/verify", "user", map("book_id", id, "purchase_token", token), 422);
  }

  @Test
  void googlePendingWrongProductAndLiveReceiptsNeverUnlockTestBooks() throws Exception {
    String id = paidBook().get("id").asText();
    String token = "google-invalid-token";
    var result = (tools.jackson.databind.node.ObjectNode) googleReceipt(USER);
    ((tools.jackson.databind.node.ObjectNode) result.path("purchaseStateContext"))
        .put("purchaseState", "PENDING");
    when(google.purchase(token)).thenReturn(result);
    postJson(
        "/api/payments/google/verify", "user", map("book_id", id, "purchase_token", token), 422);
    result = (tools.jackson.databind.node.ObjectNode) googleReceipt(USER);
    result.remove("testPurchaseContext");
    when(google.purchase(token)).thenReturn(result);
    postJson(
        "/api/payments/google/verify", "user", map("book_id", id, "purchase_token", token), 422);
    assertEquals(0, jdbc.queryForObject("select count(*) from book_purchases", Integer.class));
  }

  @Test
  void razorpayUsesServerAmountAndIdempotencyAndRejectsReplayAfterRefund() throws Exception {
    String id = paidBook().get("id").asText();
    UUID key = UUID.randomUUID();
    when(razorpay.createOrder(any(), eq(9900)))
        .thenReturn(
            json.valueToTree(map("id", "order_fixture", "amount", 9900, "currency", "INR")));
    var checkout =
        postJson(
            "/api/payments/razorpay/orders",
            "user",
            map("book_id", id, "request_key", key, "amount", 1),
            201);
    String local = checkout.get("id").asText();
    assertEquals(9900, checkout.get("amount").asInt());
    postJson("/api/payments/razorpay/orders", "user", map("book_id", id, "request_key", key), 201);
    verify(razorpay, times(1)).createOrder(any(), eq(9900));
    when(razorpay.payment("pay_fixture"))
        .thenReturn(
            json.valueToTree(
                map(
                    "id",
                    "pay_fixture",
                    "order_id",
                    "order_fixture",
                    "status",
                    "captured",
                    "captured",
                    true,
                    "amount",
                    9900,
                    "currency",
                    "INR",
                    "amount_refunded",
                    0)));
    when(razorpay.order("order_fixture"))
        .thenReturn(
            json.valueToTree(
                map(
                    "receipt",
                    local,
                    "amount",
                    9900,
                    "currency",
                    "INR",
                    "status",
                    "paid",
                    "amount_paid",
                    9900)));
    postJson(
        "/api/payments/razorpay/verify",
        "user",
        map("order_id", local, "payment_id", "pay_fixture", "signature", "bad"),
        400);
    String sig = hmac("order_fixture|pay_fixture", "test-secret");
    postJson(
        "/api/payments/razorpay/verify",
        "other",
        map("order_id", local, "payment_id", "pay_fixture", "signature", sig),
        404);
    postJson(
        "/api/payments/razorpay/verify",
        "user",
        map("order_id", local, "payment_id", "pay_fixture", "signature", sig),
        200);
    byte[] event =
        json.writeValueAsBytes(
            map(
                "event",
                "refund.processed",
                "payload",
                map("refund", map("entity", map("payment_id", "pay_fixture")))));
    mvc.perform(
            post("/api/payments/razorpay/webhook")
                .contentType("application/json")
                .header("X-Razorpay-Signature", "bad")
                .content(event))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/payments/razorpay/webhook")
                .contentType("application/json")
                .header(
                    "X-Razorpay-Signature",
                    hmac(
                        new String(event, java.nio.charset.StandardCharsets.UTF_8),
                        "webhook-secret"))
                .content(event))
        .andExpect(status().isOk());
    postJson(
        "/api/payments/razorpay/verify",
        "user",
        map("order_id", local, "payment_id", "pay_fixture", "signature", sig),
        422);
    mvc.perform(request(get("/api/books/" + id + "/access"), "user"))
        .andExpect(jsonPath("$.has_access").value(false));
  }

  private String hmac(String value, String secret) throws Exception {
    var mac = javax.crypto.Mac.getInstance("HmacSHA256");
    mac.init(
        new javax.crypto.spec.SecretKeySpec(
            secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
    return java.util.HexFormat.of()
        .formatHex(mac.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  }

  private record ReaderTestSession(String book, java.security.KeyPair key, JsonNode session) {}

  private String readerSign(java.security.KeyPair key, String message) throws Exception {
    var signer = java.security.Signature.getInstance("SHA256withECDSA");
    signer.initSign(key.getPrivate());
    signer.update(message.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(signer.sign());
  }

  private ReaderTestSession openReader(String book, String account) throws Exception {
    var generator = java.security.KeyPairGenerator.getInstance("EC");
    generator.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
    var key = generator.generateKeyPair();
    var challenge = postJson("/api/books/" + book + "/reader/challenge", account,
        map("public_key", Base64.getEncoder().encodeToString(key.getPublic().getEncoded())), 200);
    String hash = challenge.path("request_hash").asText();
    var verdict = json.valueToTree(map(
        "requestDetails", map("requestPackageName", "com.srenglishtak.app", "requestHash", hash,
            "timestampMillis", Instant.now().toEpochMilli()),
        "appIntegrity", map("appRecognitionVerdict", "PLAY_RECOGNIZED", "packageName", "com.srenglishtak.app",
            "versionCode", "10", "certificateSha256Digest", List.of(Base64.getUrlEncoder().withoutPadding()
                .encodeToString(java.util.HexFormat.of().parseHex("ab".repeat(32))))),
        "accountDetails", map("appLicensingVerdict", "LICENSED"),
        "deviceIntegrity", map("deviceRecognitionVerdict", List.of("MEETS_DEVICE_INTEGRITY")),
        "environmentDetails", map("appAccessRiskVerdict", map("appsDetected", List.of("KNOWN_INSTALLED")))));
    String token = "opaque-fixture-" + UUID.randomUUID();
    when(integrityGoogle.decode(token)).thenReturn(verdict);
    var body = map("challenge_id", challenge.path("challenge_id").asText(), "integrity_token", token,
        "signature", readerSign(key, "activate\n" + challenge.path("challenge_id").asText() + "\n" + hash));
    var session = postJson("/api/books/" + book + "/reader/sessions", account, body, 200);
    // Reusing a valid, consumed challenge never opens a second reader session.
    postJson("/api/books/" + book + "/reader/sessions", account, body, 403);
    return new ReaderTestSession(book, key, session);
  }

  private Map<String, Object> readerProof(ReaderTestSession r, String action, int page, long timestamp) throws Exception {
    UUID nonce = UUID.randomUUID();
    String proof = com.readiq.backend.reader.ReaderCrypto.proof(action, UUID.fromString(r.book()),
        UUID.fromString(r.session().path("session_id").asText()), page, timestamp, nonce);
    return map("timestamp", timestamp, "nonce", nonce, "signature", readerSign(r.key(), proof));
  }

  private String pagePath(ReaderTestSession r, int page) {
    return "/api/books/" + r.book() + "/reader/sessions/" + r.session().path("session_id").asText() + "/pages/" + page;
  }

  private void readerPdfFixture() throws Exception {
    byte[] bytes;
    try (var pdf = new org.apache.pdfbox.pdmodel.PDDocument(); var out = new java.io.ByteArrayOutputStream()) {
      pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage());
      pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage());
      pdf.save(out);
      bytes = out.toByteArray();
    }
    doAnswer(call -> new java.io.ByteArrayInputStream(bytes)).when(storage).download(anyString());
  }

  @Test
  void protectedReaderRejectsCopiedUrlsKeysProofsAndOriginalDownloadsEvenForBuyers() throws Exception {
    readerPdfFixture();
    String id = paidBook().path("id").asText();
    for (String account : List.of("user", "other", "admin")) {
      mvc.perform(request(get("/api/books/" + id + "/stream").header("Range", "bytes=0-100"), account))
          .andExpect(status().isGone());
    }
    mvc.perform(get("/api/books/" + id + "/stream")).andExpect(status().isUnauthorized());
    var gen = java.security.KeyPairGenerator.getInstance("EC");
    gen.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
    String key = Base64.getEncoder().encodeToString(gen.generateKeyPair().getPublic().getEncoded());
    postJson("/api/books/" + id + "/reader/challenge", "user", map("public_key", key), 402);
    when(google.purchase("reader-purchase-token")).thenReturn(googleReceipt(USER));
    postJson("/api/payments/google/verify", "user", map("book_id", id, "purchase_token", "reader-purchase-token"), 200);
    // The raw route stays closed after a valid purchase too.
    mvc.perform(request(get("/api/books/" + id + "/stream"), "user")).andExpect(status().isGone());
    var r = openReader(id, "user");
    assertEquals(2, r.session().path("page_count").asInt());
    var proof = readerProof(r, "page", 1, Instant.now().getEpochSecond());
    mvc.perform(request(get(pagePath(r, 1)), "user")).andExpect(status().isMethodNotAllowed());
    postJson(pagePath(r, 1), null, proof, 401);
    postJson(pagePath(r, 1), "other", proof, 402);
    var copied = new LinkedHashMap<>(proof);
    copied.put("signature", "not-a-device-signature");
    postJson(pagePath(r, 1), "user", copied, 403);
    // Changing the page number invalidates a copied valid signature.
    postJson(pagePath(r, 2), "user", proof, 403);
    var result = mvc.perform(request(post(pagePath(r, 1)), "user").contentType("application/json")
        .content(json.writeValueAsString(proof))).andExpect(status().isOk())
        .andExpect(content().contentType("image/jpeg"))
        .andExpect(header().string("Cache-Control", "private, no-store, max-age=0")).andReturn();
    byte[] image = result.getResponse().getContentAsByteArray();
    assertEquals((byte)0xff, image[0]);
    assertNotNull(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(image)));
    postJson(pagePath(r, 1), "user", proof, 403); // exact replay
    postJson(pagePath(r, 1), "user", readerProof(r, "page", 1, Instant.now().minusSeconds(60).getEpochSecond()), 403);
    jdbc.update("update book_purchases set status='revoked' where user_id=?", USER);
    postJson(pagePath(r, 1), "user", readerProof(r, "page", 1, Instant.now().getEpochSecond()), 402);
  }

  @Test
  void freeBooksAlsoUsePrivateWatermarkedPagesAndSessionsAreAccountBoundAndRevocable() throws Exception {
    readerPdfFixture();
    String id = book(true).path("id").asText();
    jdbc.update("update books set file_url=? where id=?",
        "http://127.0.0.1:9/storage/v1/object/authenticated/book-files/free.pdf", UUID.fromString(id));
    var first = openReader(id, "user");
    var proof = readerProof(first, "page", 1, Instant.now().getEpochSecond());
    postJson(pagePath(first, 1), "other", proof, 403);
    var next = openReader(id, "user");
    postJson(pagePath(first, 1), "user", proof, 403); // account's previous session revoked
    var userPage = mvc.perform(request(post(pagePath(next, 1)), "user").contentType("application/json")
        .content(json.writeValueAsString(readerProof(next, "page", 1, Instant.now().getEpochSecond()))))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    var other = openReader(id, "other");
    var otherPage = mvc.perform(request(post(pagePath(other, 1)), "other").contentType("application/json")
        .content(json.writeValueAsString(readerProof(other, "page", 1, Instant.now().getEpochSecond()))))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    assertFalse(Arrays.equals(userPage, otherPage), "Server watermark must be bound to the reader");
    String close = "/api/books/" + id + "/reader/sessions/" + next.session().path("session_id").asText() + "/close";
    postJson(close, "user", readerProof(next, "close", 0, Instant.now().getEpochSecond()), 200);
    postJson(pagePath(next, 1), "user", readerProof(next, "page", 1, Instant.now().getEpochSecond()), 403);
    jdbc.update("update reader_sessions set expires_at=now()-interval '1 second'");
    postJson(pagePath(other, 1), "other", readerProof(other, "page", 1, Instant.now().getEpochSecond()), 403);
    for (String table : List.of("reader_challenges", "reader_sessions", "reader_request_nonces")) {
      assertFalse(jdbc.queryForObject("select has_table_privilege('authenticated',?,'SELECT')", Boolean.class, table));
      assertFalse(jdbc.queryForObject("select has_table_privilege('anon',?,'SELECT')", Boolean.class, table));
    }
  }

  @Test
  void bookCatalogCannotPublishPublicOriginalsOrEpubsAndUploadsCannotBypassPrivacy() throws Exception {
    postJson("/api/books", "admin", map("title", "Public free PDF", "format_type", "pdf", "file_url",
        "http://127.0.0.1:9/storage/v1/object/public/books/leak.pdf"), 400);
    postJson("/api/books", "admin", map("title", "Raw EPUB", "format_type", "epub"), 400);
    var file = new MockMultipartFile("file", "book.pdf", "application/pdf", "%PDF-1.7\nfixture".getBytes());
    mvc.perform(multipart("/api/upload").file(file).param("purpose", "public")
        .header("Authorization", "Bearer admin")).andExpect(status().isBadRequest());
  }

  @Test
  void failedIntegrityConsumesItsChallengeAndCannotBeReplayedToSpendGoogleQuota() throws Exception {
    String id = book(true).path("id").asText();
    var generator = java.security.KeyPairGenerator.getInstance("EC");
    generator.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
    var key = generator.generateKeyPair();
    var challenge = postJson("/api/books/" + id + "/reader/challenge", "user",
        map("public_key", Base64.getEncoder().encodeToString(key.getPublic().getEncoded())), 200);
    when(integrityGoogle.decode("invalid-attestation")).thenThrow(new ApiException(403, "Invalid attestation"));
    var body = map("challenge_id", challenge.path("challenge_id").asText(), "integrity_token", "invalid-attestation",
        "signature", readerSign(key, "activate\n" + challenge.path("challenge_id").asText() + "\n" + challenge.path("request_hash").asText()));
    postJson("/api/books/" + id + "/reader/sessions", "user", body, 403);
    postJson("/api/books/" + id + "/reader/sessions", "user", body, 403);
    verify(integrityGoogle, times(1)).decode("invalid-attestation");
    assertEquals(0, jdbc.queryForObject("select count(*) from reader_sessions", Integer.class));
  }

  @Test
  void rateLimitsAreSharedAcrossFilterInstancesAndAtomic() throws Exception {
    var pool = java.util.concurrent.Executors.newFixedThreadPool(8);
    try {
      var work = new ArrayList<java.util.concurrent.Callable<Boolean>>();
      for (int i = 0; i < 40; i++)
        work.add(() -> limitStore.consume("concurrent-fixture", 10, 900000));
      int allowed = 0;
      for (var f : pool.invokeAll(work)) if (f.get()) allowed++;
      assertEquals(10, allowed);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void typedDtosPreservePatchNullsAndRejectScalarCoercion() throws Exception {
    String id = book(true).get("id").asText();
    mvc.perform(
            request(patch("/api/books/" + id), "admin")
                .contentType("application/json")
                .content("{\"author\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.author").isEmpty())
        .andExpect(jsonPath("$.title").value("Test book"));
    postJson("/api/books", "admin", map("title", "Bad price", "price_paise", 1.5), 400);
    postJson("/api/books", "admin", map("title", "Bad flag", "is_visible", "true"), 400);
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.components.schemas.BookRequest").exists());
  }

  @Test
  void roleChangesTakeEffectEvenWhenIdentityIsCached() throws Exception {
    mvc.perform(request(get("/api/admin/stats"), "admin")).andExpect(status().isOk());
    jdbc.update("update profiles set role='user' where id=?", ADMIN);
    mvc.perform(request(get("/api/admin/stats"), "admin")).andExpect(status().isForbidden());
  }

  @Test
  void privateStoragePolicyOverridesOldBroadClientPolicies() {
    jdbc.execute("truncate storage.objects");
    jdbc.update(
        "insert into storage.objects(id,bucket_id) values(?,?),(?,?)",
        UUID.randomUUID(),
        "book-files",
        UUID.randomUUID(),
        "books");
    jdbc.execute(
        (ConnectionCallback<Void>)
            connection -> {
              try (var statement = connection.createStatement()) {
                statement.execute("set role authenticated");
                try (var result =
                    statement.executeQuery(
                        "select count(*) from storage.objects where bucket_id='book-files'")) {
                  result.next();
                  assertEquals(0, result.getInt(1));
                }
                try (var result =
                    statement.executeQuery(
                        "select count(*) from storage.objects where bucket_id='books'")) {
                  result.next();
                  assertEquals(1, result.getInt(1));
                }
                statement.execute("reset role");
              }
              return null;
            });
  }

  @Test
  void logoutRevokesEvenTheLocallyCachedIdentity() throws Exception {
    mvc.perform(request(get("/api/user/profile"), "user")).andExpect(status().isOk());
    postJson("/api/auth/logout", "user", Map.of(), 200);
    mvc.perform(request(get("/api/user/profile"), "user")).andExpect(status().isUnauthorized());
    assertEquals(
        1, jdbc.queryForObject("select count(*) from revoked_access_tokens", Integer.class));
  }

  @Test
  void paidBooksCannotUsePublicFilesAndPaymentRecordsStayServerOnly() throws Exception {
    postJson(
        "/api/books",
        "admin",
        map(
            "title",
            "Invalid paid book",
            "price_paise",
            9900,
            "file_url",
            "https://example.test/public.pdf"),
        400);
    mvc.perform(request(get("/api/admin/purchases"), "user")).andExpect(status().isForbidden());
    jdbc.execute(
        (ConnectionCallback<Void>)
            connection -> {
              try (var statement = connection.createStatement()) {
                statement.execute("set role authenticated");
                assertThrows(
                    SQLException.class,
                    () -> statement.executeQuery("select * from book_purchases"));
                statement.execute("reset role");
              }
              return null;
            });
  }

  @Test
  void webhookRejectsWrongContentTypeAndMissingSignature() throws Exception {
    mvc.perform(post("/api/payments/razorpay/webhook").contentType("text/plain").content("payload"))
        .andExpect(status().isUnsupportedMediaType());
    mvc.perform(
            post("/api/payments/razorpay/webhook").contentType("application/json").content("{}"))
        .andExpect(status().isBadRequest());
  }
}
