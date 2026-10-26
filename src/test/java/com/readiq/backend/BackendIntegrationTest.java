package com.readiq.backend;
import com.fasterxml.jackson.databind.*;
import com.readiq.backend.api.ApiException;
import com.readiq.backend.auth.SupabaseClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.*;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import static com.readiq.backend.api.Input.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real PostgreSQL + full Spring Security/MVC stack; Supabase is mocked at its network boundary. */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named="READIQ_TEST_DB_URL",matches=".+")
class BackendIntegrationTest {
    static final UUID ADMIN=UUID.fromString("10000000-0000-0000-0000-000000000001"),
        USER=UUID.fromString("10000000-0000-0000-0000-000000000002"),OTHER=UUID.fromString("10000000-0000-0000-0000-000000000003");
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;
    @Autowired RequestMappingHandlerMapping mappings;
    @MockitoBean SupabaseClient supabase;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){
        p.add("spring.datasource.url",()->System.getenv("READIQ_TEST_DB_URL"));
        p.add("spring.datasource.username",()->System.getenv().getOrDefault("READIQ_TEST_DB_USER","readiq_test"));
        p.add("spring.datasource.password",()->System.getenv().getOrDefault("READIQ_TEST_DB_PASSWORD",""));
        p.add("spring.datasource.hikari.maximum-pool-size",()->3);
        p.add("supabase.url",()->"http://127.0.0.1:9");
        p.add("supabase.key",()->"test-anon");p.add("supabase.service-role-key",()->"test-service");
        p.add("cors.allowed-origins",()->"https://admin.example.test");
    }
    @BeforeAll void schema()throws Exception{
        var target=java.net.URI.create(System.getenv("READIQ_TEST_DB_URL").substring("jdbc:".length()));
        if(!Set.of("127.0.0.1","localhost").contains(target.getHost())||!"/readiq_test".equals(target.getPath()))
            throw new IllegalStateException("Integration tests require a disposable localhost database named readiq_test");
        jdbc.execute("create schema if not exists auth; create table if not exists auth.users(id uuid primary key)");
        jdbc.execute("DO $$ BEGIN IF NOT EXISTS(SELECT FROM pg_roles WHERE rolname='anon') THEN CREATE ROLE anon; END IF; "+
            "IF NOT EXISTS(SELECT FROM pg_roles WHERE rolname='authenticated') THEN CREATE ROLE authenticated; END IF; END $$");
        jdbc.execute(Files.readString(Path.of("db/001_schema.sql")));
        jdbc.execute(Files.readString(Path.of("db/002_security_and_contract.sql")));
    }
    @BeforeEach void data(){
        jdbc.execute("truncate auth.users,grammar_units,cbse_categories,daily_tips,vocabulary,challenges cascade");
        for(UUID id:List.of(ADMIN,USER,OTHER)){
            jdbc.update("insert into auth.users(id) values(?)",id);
            jdbc.update("insert into profiles(id,email,full_name,role) values(?,?,?,?)",id,id+"@example.test","Reader "+id.toString().substring(35),
                id.equals(ADMIN)?"admin":"user");
        }
        when(supabase.auth(eq("GET"),eq("/user"),isNull(),anyString())).thenAnswer(call->{
            String token=call.getArgument(3);UUID id=switch(token){case "admin"->ADMIN;case "user"->USER;case "other"->OTHER;default->throw new ApiException(401,"Invalid token");};
            // A forged metadata role must never grant admin.
            return map("id",id.toString(),"email",id+"@example.test","user_metadata",map("role","admin"));
        });
    }
    private MockHttpServletRequestBuilder request(MockHttpServletRequestBuilder b,String token){
        if(token!=null)b.header("Authorization","Bearer "+token);
        return b.with(r->{r.setRemoteAddr("192.0.2."+Math.floorMod(TestInfoHolder.counter++,200));return r;});
    }
    private static class TestInfoHolder{static int counter=1;}
    private JsonNode response(MvcResult r)throws Exception{return json.readTree(r.getResponse().getContentAsString());}
    private JsonNode postJson(String path,String token,Object body,int status)throws Exception{
        return response(mvc.perform(request(post(path),token).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().is(status)).andReturn());
    }
    private JsonNode book(boolean visible)throws Exception{
        return postJson("/api/books","admin",map("title","Test book","format_type","pdf","is_visible",visible),201);
    }
    private Map<String,Object>question(boolean challenge,int correct){
        return map("question_text","Choose the correct word","options",challenge?List.of("one","two","three","four"):
            List.of(map("label","A","text","one"),map("label","B","text","two"),map("label","C","text","three"),map("label","D","text","four")),
            "correct_option_index",correct);
    }
    private JsonNode quiz()throws Exception{
        return postJson("/api/quizzes","admin",map("title","Quiz","type","general","duration_minutes",5,"questions",List.of(question(false,1))),201);
    }
    private JsonNode challenge()throws Exception{
        return postJson("/api/challenges","admin",map("name","Challenge","duration_minutes",5,"questions",List.of(question(true,1))),201);
    }
    @Test void publicCatalogAndAdminBoundary()throws Exception{
        mvc.perform(get("/api/books")).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/admin/stats")).andExpect(status().isUnauthorized());
        postJson("/api/books","user",map("title","forbidden"),403);
        var hidden=book(false);
        mvc.perform(get("/api/books/"+hidden.get("id").asText())).andExpect(status().isNotFound());
        mvc.perform(request(get("/api/books/"+hidden.get("id").asText()),"admin")).andExpect(status().isOk());
        mvc.perform(request(get("/api/admin/stats"),"bad")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/books/not-a-uuid")).andExpect(status().isBadRequest());
    }
    @Test void profileCannotEscalateRole()throws Exception{
        mvc.perform(request(patch("/api/user/profile"),"user").contentType("application/json")
            .content(json.writeValueAsString(map("full_name","Updated","role","admin","id",OTHER.toString(),"email","takeover@example.test"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("user")).andExpect(jsonPath("$.id").value(USER.toString()));
        postJson("/api/books","user",map("title","still forbidden"),403);
    }
    @Test void annotationsAndSessionsStayOwnedAndFlat()throws Exception{
        String book=book(true).get("id").asText();
        var mark=postJson("/api/user/bookmarks","user",map("book_id",book,"page",4,"user_id",OTHER.toString()),201);
        assertEquals(USER.toString(),mark.get("user_id").asText());assertEquals(book,mark.get("book_id").asText());assertFalse(mark.has("book"));
        mvc.perform(request(delete("/api/user/bookmarks/"+mark.get("id").asText()),"other")).andExpect(status().isNotFound());
        mvc.perform(request(get("/api/user/bookmarks"),"other")).andExpect(content().json("[]"));
        var note=postJson("/api/user/notes","user",map("book_id",book,"note_text","My note","page",4),201);
        mvc.perform(request(delete("/api/user/notes/"+note.get("id").asText()),"other")).andExpect(status().isNotFound());
        var session=postJson("/api/user/reading-sessions","user",map("book_id",book,"start_page",4,"user_id",OTHER.toString()),201);
        assertEquals(4,session.get("end_page").asInt());assertEquals(book,session.get("book_id").asText());
        mvc.perform(request(patch("/api/user/reading-sessions/"+session.get("id").asText()),"other").contentType("application/json").content("{\"minutes_spent\":5}"))
            .andExpect(status().isNotFound());
        mvc.perform(request(patch("/api/user/reading-sessions/"+session.get("id").asText()),"user").contentType("application/json").content("{\"minutes_spent\":5,\"end_page\":9}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.minutes_spent").value(5));
        postJson("/api/user/progress","user",map("book_id",book,"page",9,"total_minutes_read",5),200);
        mvc.perform(request(get("/api/user/progress"),"user")).andExpect(jsonPath("$[0].books.title").value("Test book"));
    }
    @Test void quizAnswersArePrivateAndScoringIsAuthoritative()throws Exception{
        var quiz=quiz();String id=quiz.get("id").asText(),q=quiz.get("questions").get(0).get("id").asText();
        mvc.perform(get("/api/quizzes/"+id)).andExpect(status().isOk()).andExpect(jsonPath("$.questions[0].correct_option_index").doesNotExist());
        var scored=postJson("/api/quizzes/"+id+"/submit","user",map("score",999,"answers",List.of(map("question_id",q,"selected_option_index",0))),201);
        assertEquals(0,scored.get("score").asInt());assertEquals(1,scored.get("total").asInt());
        mvc.perform(request(get("/api/quizzes"),"user")).andExpect(jsonPath("$[0].has_submitted").value(true));
        postJson("/api/quizzes/"+id+"/submit","user",map("answers",List.of(map("question_id",UUID.randomUUID().toString(),"selected_option_index",1))),400);
        assertEquals(1,jdbc.queryForObject("select count(*) from quiz_attempts",Integer.class));
    }
    @Test void invalidQuizCreationRollsBack()throws Exception{
        var invalid=question(false,5);
        postJson("/api/quizzes","admin",map("title","Will roll back","questions",List.of(invalid)),400);
        assertEquals(0,jdbc.queryForObject("select count(*) from quizzes",Integer.class));
    }
    @Test void challengeScoringTimingAndDuplicateProtection()throws Exception{
        var c=challenge();String id=c.get("id").asText(),q=c.get("challenge_questions").get(0).get("id").asText();
        mvc.perform(request(get("/api/challenges"),"user")).andExpect(jsonPath("$[0].challenge_questions").isEmpty()).andExpect(jsonPath("$[0].question_count").value(1));
        postJson("/api/challenges/"+id+"/submit","user",map("answers",List.of()),404);
        var start=postJson("/api/challenges/"+id+"/start","user",Map.of(),200);assertTrue(start.has("expires_at"));
        assertTrue(start.get("challenge").get("challenge_questions").get(0).has("question_text"));
        assertFalse(start.get("challenge").get("challenge_questions").get(0).has("correct_option_index"));
        postJson("/api/challenges/"+id+"/submit","user",map("score",99999,"answers",List.of()),400);
        var result=postJson("/api/challenges/"+id+"/submit","user",map("time_taken_ms",0,"answers",List.of(map("question_id",q,"selected_option_index",1))),201);
        assertEquals(10,result.get("score").asInt());
        postJson("/api/challenges/"+id+"/submit","user",map("answers",List.of()),409);
        mvc.perform(get("/api/challenges/"+id+"/leaderboard")).andExpect(status().isOk()).andExpect(jsonPath("$[0].profiles.full_name").exists())
            .andExpect(jsonPath("$[0].profiles.email").doesNotExist());
        postJson("/api/challenges/"+id+"/start","other",Map.of(),200);
        jdbc.update("update challenge_attempt_sessions set expires_at=now()-interval '1 minute' where user_id=?",OTHER);
        postJson("/api/challenges/"+id+"/submit","other",map("answers",List.of()),410);
    }
    @Test void grammarBulkIsAtomicAndProgressIsPerUser()throws Exception{
        var unit=postJson("/api/grammar/units","admin",map("title","Unit","unit_order",1),201);String id=unit.get("id").asText();
        postJson("/api/grammar/units/"+id+"/lessons/bulk","admin",map("lessons",List.of(map("title","First","lesson_order",1),map("lesson_order",2))),400);
        assertEquals(0,jdbc.queryForObject("select count(*) from grammar_lessons",Integer.class));
        var lesson=postJson("/api/grammar/units/"+id+"/lessons","admin",map("title","Lesson","lesson_order",1,"content_type","text","content_data","Learn"),201);
        assertEquals(id,lesson.get("unit_id").asText());
        postJson("/api/grammar/lessons/"+lesson.get("id").asText()+"/complete","user",Map.of(),200);
        mvc.perform(request(get("/api/grammar/progress"),"other")).andExpect(content().json("[]"));
    }
    @Test void cbseVocabularyTipsAndInterestContracts()throws Exception{
        var category=postJson("/api/cbse/categories","admin",map("name","Class 12","order_index",1),201);
        var material=postJson("/api/cbse/categories/"+category.get("id").asText()+"/materials","admin",map("title","Paper","file_url","https://example.test/paper.pdf","material_type","pyq"),201);
        assertEquals(category.get("id"),material.get("category_id"));
        postJson("/api/vocabulary/bulk","admin",map("items",List.of(map("word","Read","meaning","Study text"))),201);
        postJson("/api/tips","admin",map("title","Tip","content","Read daily"),201);
        var interest=postJson("/api/interests","user",map("moduleTitle","Speaking","isExcited",true),200);
        assertEquals(1,interest.get("excitedCount").asInt());assertTrue(interest.get("isExcited").asBoolean());
        mvc.perform(request(get("/api/interests/Speaking"),"other")).andExpect(jsonPath("$.isExcited").value(false));
        postJson("/api/interests","user",map("moduleTitle","Speaking","isExcited","false"),400);
    }
    @Test void dashboardAndPersonalSummariesHaveClientFields()throws Exception{
        String book=book(true).get("id").asText();postJson("/api/user/progress","user",map("book_id",book,"page",10,"is_completed",true),200);
        mvc.perform(request(get("/api/admin/stats"),"admin")).andExpect(status().isOk()).andExpect(jsonPath("$.total_books").value(1)).andExpect(jsonPath("$.completed_books").value(1));
        mvc.perform(request(get("/api/admin/users/"+USER+"/progress"),"admin")).andExpect(status().isOk()).andExpect(jsonPath("$.book_progress[0].books.title").value("Test book"))
            .andExpect(jsonPath("$.quiz_attempts").isArray()).andExpect(jsonPath("$.reading_sessions").isArray());
        mvc.perform(request(get("/api/user/achievements"),"user")).andExpect(status().isOk()).andExpect(jsonPath("$.summary.completed_books").value(1)).andExpect(jsonPath("$.achievements[0].code").value("book_finisher"));
        mvc.perform(request(get("/api/user/recommendations"),"user")).andExpect(status().isOk());
    }
    @Test void googleAndRefreshNormalizeRawSupabaseSessions()throws Exception{
        var raw=map("access_token","test-access","refresh_token","test-refresh","expires_in",3600,"user",map("id",USER.toString(),"email",USER+"@example.test"));
        when(supabase.auth(eq("POST"),eq("/token?grant_type=id_token"),anyMap(),isNull())).thenReturn(raw);
        var result=postJson("/api/auth/google",null,map("id_token","verified-by-supabase"),200);
        assertTrue(result.get("session").get("expires_at").asLong()>Instant.now().getEpochSecond()+3500);
        assertEquals("user",result.get("user").get("role").asText());
    }
    @Test void corsAndUploadRejection()throws Exception{
        mvc.perform(options("/api/books").header("Origin","https://evil.example").header("Access-Control-Request-Method","GET")).andExpect(status().isForbidden());
        var file=new MockMultipartFile("file","fake.pdf","application/pdf","not a pdf".getBytes());
        mvc.perform(multipart("/api/upload").file(file).header("Authorization","Bearer user")).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/upload").file(file).header("Authorization","Bearer admin")).andExpect(status().isBadRequest());
    }
    @Test void oversizedJsonIsRejectedBeforeAuthentication()throws Exception{
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"password\":\""+"x".repeat(1024*1024)+"\"}"))
            .andExpect(status().isPayloadTooLarge());
    }
    @Test void databaseApiRolesCannotReadAnswerKeys() {
        jdbc.execute((ConnectionCallback<Void>)connection->{
            try(var statement=connection.createStatement()){
                statement.execute("set role authenticated");
                assertThrows(SQLException.class,()->statement.executeQuery("select * from questions"));
                statement.execute("reset role");
            }return null;
        });
    }
    @Test void everyExistingNodeEndpointHasASpringHandler()throws Exception{
        Set<String>actual=new HashSet<>();
        mappings.getHandlerMethods().forEach((mapping,method)->{
            for(String path:mapping.getPatternValues())for(var verb:mapping.getMethodsCondition().getMethods())
                actual.add(verb.name()+" "+path.replaceAll("\\{[^}]+}","{}"));
        });
        for(String route:Files.readAllLines(Path.of("src/test/resources/node-contract-routes.txt")))
            if(!route.isBlank())assertTrue(actual.contains(route.replaceAll(":[^/]+","{}")),"Missing route: "+route);
    }
}
