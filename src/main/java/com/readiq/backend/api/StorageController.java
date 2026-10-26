package com.readiq.backend.api;
import com.readiq.backend.auth.ApiPrincipal;
import com.readiq.backend.service.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import java.util.*;
@RestController
public class StorageController {
    private final StorageService storage;private final CatalogService catalog;
    public StorageController(StorageService storage,CatalogService catalog){this.storage=storage;this.catalog=catalog;}
    @PostMapping("/api/upload")@PreAuthorize("hasRole('ADMIN')")
    public Object upload(@RequestParam("file")MultipartFile file){return Map.of("url",storage.upload(file));}
    @GetMapping("/api/books/{id}/stream")
    public ResponseEntity<StreamingResponseBody>stream(@PathVariable UUID id,Authentication a){
        var b=catalog.book(id,a!=null&&a.getPrincipal() instanceof ApiPrincipal p&&p.admin());
        if(b.get("file_url")==null)throw ApiException.missing();
        String type="epub".equals(b.get("format_type"))?"application/epub+zip":"application/pdf";
        var input=storage.download(b.get("file_url").toString());
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type)).header("Content-Disposition","inline; filename=\"book_"+id+("application/pdf".equals(type)?".pdf":".epub")+"\"")
            .body(output->{try(input){input.transferTo(output);}});
    }
    @GetMapping({"/","/health"})public Object health(){return Map.of("status","ok","service","ReadIQ Spring API");}
}
