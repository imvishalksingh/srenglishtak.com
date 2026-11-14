package com.readiq.backend.api;

import com.readiq.backend.dto.SpokenLessonRequest;
import com.readiq.backend.service.SpokenEnglishService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class SpokenEnglishController {
  private final SpokenEnglishService spoken;
  public SpokenEnglishController(SpokenEnglishService spoken) { this.spoken=spoken; }

  private ResponseEntity<?> read(Object value) {
    return ResponseEntity.ok().header("Cache-Control","private, no-store").body(value);
  }

  @GetMapping("/spoken-english/lessons")
  public ResponseEntity<?> lessons(@RequestParam(required=false) String category) {
    return read(spoken.lessons(category,false));
  }

  @GetMapping("/spoken-english/lessons/{id}")
  public ResponseEntity<?> lesson(@PathVariable UUID id) { return read(spoken.lesson(id,false)); }

  @GetMapping("/admin/spoken-english/lessons")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<?> adminLessons() { return read(spoken.lessons(null,true)); }

  @GetMapping("/admin/spoken-english/lessons/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<?> adminLesson(@PathVariable UUID id) { return read(spoken.lesson(id,true)); }

  @PostMapping("/admin/spoken-english/lessons")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<?> create(@Valid @RequestBody SpokenLessonRequest request) {
    return ResponseEntity.status(201).body(spoken.save(null,request));
  }

  @PutMapping("/admin/spoken-english/lessons/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public Object update(@PathVariable UUID id,@Valid @RequestBody SpokenLessonRequest request) {
    return spoken.save(id,request);
  }

  @DeleteMapping("/admin/spoken-english/lessons/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<?> delete(@PathVariable UUID id) {
    spoken.delete(id);
    return ResponseEntity.noContent().build();
  }
}
