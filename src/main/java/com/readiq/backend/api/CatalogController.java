package com.readiq.backend.api;
import com.readiq.backend.auth.ApiPrincipal;
import com.readiq.backend.data.Database;
import com.readiq.backend.service.CatalogService;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static com.readiq.backend.api.Input.*;
@RestController
@RequestMapping("/api")
public class CatalogController {
    private final CatalogService catalog;private final Database db;
    public CatalogController(CatalogService catalog,Database db){this.catalog=catalog;this.db=db;}
    private boolean admin(Authentication a){return a!=null&&a.getPrincipal() instanceof ApiPrincipal p&&p.admin();}
    private ResponseEntity<?>created(Object body){return ResponseEntity.status(201).body(body);}
    @GetMapping("/books")public Object books(){return catalog.books(false);}
    @GetMapping("/books/admin/all")@PreAuthorize("hasRole('ADMIN')")public Object adminBooks(){return catalog.books(true);}
    @GetMapping("/books/{id}")public Object book(@PathVariable UUID id,Authentication a){return catalog.book(id,admin(a));}
    @PostMapping("/books")@PreAuthorize("hasRole('ADMIN')")public Object createBook(@RequestBody Map<String,Object>b,Authentication a){
        return created(catalog.save("books",null,b,map("uploaded_by",ApiPrincipal.from(a).id())));
    }
    @PatchMapping("/books/{id}")@PreAuthorize("hasRole('ADMIN')")public Object updateBook(@PathVariable UUID id,@RequestBody Map<String,Object>b){return catalog.save("books",id,b,null);}
    @DeleteMapping("/books/{id}")@PreAuthorize("hasRole('ADMIN')")public ResponseEntity<?>deleteBook(@PathVariable UUID id){catalog.delete("books",id);return ResponseEntity.noContent().build();}
    @GetMapping("/grammar/units")public Object units(){return catalog.list("grammar_units",null,null);}
    @GetMapping("/grammar/units/{id}")public Object unit(@PathVariable UUID id){return db.byId("grammar_units",id);}
    @PostMapping("/grammar/units")@PreAuthorize("hasRole('ADMIN')")public Object createUnit(@RequestBody Map<String,Object>b){return created(catalog.save("grammar_units",null,b,null));}
    @DeleteMapping("/grammar/units/{id}")@PreAuthorize("hasRole('ADMIN')")public Object deleteUnit(@PathVariable UUID id){catalog.delete("grammar_units",id);return map("message","Unit deleted");}
    @GetMapping("/grammar/units/{unitId}/lessons")public Object lessons(@PathVariable UUID unitId){db.byId("grammar_units",unitId);return catalog.list("grammar_lessons","unit_id",unitId);}
    @GetMapping("/grammar/lessons/{id}")public Object lesson(@PathVariable UUID id){return db.byId("grammar_lessons",id);}
    @PostMapping("/grammar/units/{unitId}/lessons")@PreAuthorize("hasRole('ADMIN')")public Object createLesson(@PathVariable UUID unitId,@RequestBody Map<String,Object>b){
        db.byId("grammar_units",unitId);return created(catalog.save("grammar_lessons",null,b,map("unit_id",unitId)));
    }
    @PostMapping("/grammar/units/{unitId}/lessons/bulk")@PreAuthorize("hasRole('ADMIN')")public Object bulkLessons(@PathVariable UUID unitId,@RequestBody Map<String,Object>b){
        db.byId("grammar_units",unitId);return created(catalog.bulk("grammar_lessons",objects(b.get("lessons"),"lessons",500),map("unit_id",unitId)));
    }
    @PatchMapping("/grammar/lessons/{id}")@PreAuthorize("hasRole('ADMIN')")public Object updateLesson(@PathVariable UUID id,@RequestBody Map<String,Object>b){return catalog.save("grammar_lessons",id,b,null);}
    @DeleteMapping("/grammar/lessons/{id}")@PreAuthorize("hasRole('ADMIN')")public Object deleteLesson(@PathVariable UUID id){catalog.delete("grammar_lessons",id);return map("message","Lesson deleted");}
    @GetMapping("/cbse/categories")public Object categories(){return catalog.list("cbse_categories",null,null);}
    @GetMapping("/cbse/categories/{id}")public Object category(@PathVariable UUID id){return db.byId("cbse_categories",id);}
    @PostMapping("/cbse/categories")@PreAuthorize("hasRole('ADMIN')")public Object createCategory(@RequestBody Map<String,Object>b){return created(catalog.save("cbse_categories",null,b,null));}
    @DeleteMapping("/cbse/categories/{id}")@PreAuthorize("hasRole('ADMIN')")public Object deleteCategory(@PathVariable UUID id){catalog.delete("cbse_categories",id);return map("message","Category deleted");}
    @GetMapping("/cbse/categories/{categoryId}/materials")public Object materials(@PathVariable UUID categoryId){return catalog.list("cbse_materials","category_id",categoryId);}
    @GetMapping("/cbse/materials/{id}")public Object material(@PathVariable UUID id){return db.byId("cbse_materials",id);}
    @PostMapping("/cbse/categories/{categoryId}/materials")@PreAuthorize("hasRole('ADMIN')")public Object createMaterial(@PathVariable UUID categoryId,@RequestBody Map<String,Object>b){
        db.byId("cbse_categories",categoryId);return created(catalog.save("cbse_materials",null,b,map("category_id",categoryId)));
    }
    @DeleteMapping("/cbse/materials/{id}")@PreAuthorize("hasRole('ADMIN')")public Object deleteMaterial(@PathVariable UUID id){catalog.delete("cbse_materials",id);return map("message","Material deleted");}
    @GetMapping("/vocabulary")public Object vocabulary(){return catalog.list("vocabulary",null,null);}
    @GetMapping("/vocabulary/{id}")public Object word(@PathVariable UUID id){return db.byId("vocabulary",id);}
    @PostMapping("/vocabulary")@PreAuthorize("hasRole('ADMIN')")public Object createWord(@RequestBody Map<String,Object>b){return created(catalog.save("vocabulary",null,b,null));}
    @PostMapping("/vocabulary/bulk")@PreAuthorize("hasRole('ADMIN')")public Object bulkVocabulary(@RequestBody Map<String,Object>b){return created(catalog.bulk("vocabulary",objects(b.get("items"),"items",500),null));}
    @DeleteMapping("/vocabulary/{id}")@PreAuthorize("hasRole('ADMIN')")public ResponseEntity<?>deleteWord(@PathVariable UUID id){catalog.delete("vocabulary",id);return ResponseEntity.noContent().build();}
    @GetMapping("/tips")public Object tips(){return catalog.list("daily_tips",null,null);}
    @GetMapping("/tips/{id}")public Object tip(@PathVariable UUID id){return db.byId("daily_tips",id);}
    @PostMapping("/tips")@PreAuthorize("hasRole('ADMIN')")public Object createTip(@RequestBody Map<String,Object>b){return created(catalog.save("daily_tips",null,b,null));}
    @DeleteMapping("/tips/{id}")@PreAuthorize("hasRole('ADMIN')")public ResponseEntity<?>deleteTip(@PathVariable UUID id){catalog.delete("daily_tips",id);return ResponseEntity.noContent().build();}
}
