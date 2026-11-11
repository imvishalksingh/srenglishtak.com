package com.readiq.backend.api;

import static com.readiq.backend.dto.DtoMapper.body;

import com.readiq.backend.auth.ApiPrincipal;
import com.readiq.backend.dto.*;
import com.readiq.backend.service.*;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class LearningController {
  private final LearningService learning;
  private final ReportingService reports;

  public LearningController(LearningService learning, ReportingService reports) {
    this.learning = learning;
    this.reports = reports;
  }

  private UUID id(Authentication a) {
    return ApiPrincipal.from(a).id();
  }

  private ResponseEntity<?> created(Object value) {
    return ResponseEntity.status(201).body(value);
  }

  @GetMapping("/user/progress")
  public Object progress(Authentication a) {
    return learning.progress(id(a));
  }

  @PostMapping("/user/progress")
  public Object updateProgress(Authentication a, @RequestBody ProgressRequest request) {
    var b = body(request);
    return learning.updateProgress(id(a), b);
  }

  @GetMapping("/user/reading-sessions")
  public Object sessions(
      Authentication a, @RequestParam(value = "book_id", required = false) UUID book) {
    return learning.ownedList("reading_sessions", id(a), book);
  }

  @PostMapping("/user/reading-sessions")
  public Object startSession(Authentication a, @RequestBody SessionRequest request) {
    var b = body(request);
    return created(learning.startSession(id(a), b));
  }

  @PatchMapping("/user/reading-sessions/{session}")
  public Object finishSession(
      Authentication a, @PathVariable UUID session, @RequestBody FinishSessionRequest request) {
    var b = body(request);
    return learning.finishSession(id(a), session, b);
  }

  @GetMapping("/user/bookmarks")
  public Object bookmarks(
      Authentication a, @RequestParam(value = "book_id", required = false) UUID book) {
    return learning.ownedList("bookmarks", id(a), book);
  }

  @PostMapping("/user/bookmarks")
  public Object createBookmark(Authentication a, @RequestBody BookmarkRequest request) {
    var b = body(request);
    return created(learning.createAnnotation("bookmarks", id(a), b));
  }

  @DeleteMapping("/user/bookmarks/{bookmark}")
  public ResponseEntity<?> deleteBookmark(Authentication a, @PathVariable UUID bookmark) {
    learning.deleteAnnotation("bookmarks", id(a), bookmark);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/user/notes")
  public Object notes(
      Authentication a, @RequestParam(value = "book_id", required = false) UUID book) {
    return learning.ownedList("notes", id(a), book);
  }

  @PostMapping("/user/notes")
  public Object createNote(Authentication a, @RequestBody NoteRequest request) {
    var b = body(request);
    return created(learning.createAnnotation("notes", id(a), b));
  }

  @DeleteMapping("/user/notes/{note}")
  public ResponseEntity<?> deleteNote(Authentication a, @PathVariable UUID note) {
    learning.deleteAnnotation("notes", id(a), note);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/user/profile")
  public Object profile(Authentication a) {
    return learning.profile(id(a));
  }

  @RequestMapping(
      value = "/user/profile",
      method = {RequestMethod.PUT, RequestMethod.PATCH})
  public Object updateProfile(Authentication a, @RequestBody ProfileRequest request) {
    var b = body(request);
    return learning.updateProfile(id(a), b);
  }

  @PostMapping("/user/onboarding")
  public Object completeOnboarding(Authentication a,
      @jakarta.validation.Valid @RequestBody OnboardingRequest request) {
    return learning.completeOnboarding(id(a), mapOnboarding(request));
  }

  private Map<String,Object> mapOnboarding(OnboardingRequest request) {
    return Map.of("full_name", request.full_name(), "phone_number", request.phone_number(),
        "class", request.studentClass(), "learning_goal", request.learning_goal());
  }

  @GetMapping("/user/insights")
  public Object insights(Authentication a) {
    return reports.insights(id(a));
  }

  @GetMapping("/user/weekly-goal")
  public Object weeklyGoal(Authentication a) {
    return reports.weeklyGoal(id(a));
  }

  @GetMapping("/user/achievements")
  public Object achievements(Authentication a) {
    return reports.achievements(id(a));
  }

  @GetMapping("/user/recommendations")
  public Object recommendations(Authentication a) {
    return reports.recommendations(id(a));
  }

  @GetMapping("/grammar/overview")
  public Object grammarOverview(Authentication a) {
    return learning.grammarOverview(id(a));
  }

  @GetMapping("/grammar/progress")
  public Object grammarProgress(Authentication a) {
    return learning.grammarProgress(id(a));
  }

  @PostMapping("/grammar/lessons/{lesson}/complete")
  public Object completeLesson(Authentication a, @PathVariable UUID lesson) {
    return learning.completeLesson(id(a), lesson);
  }

  @GetMapping("/interests/{title}")
  public Object interest(Authentication a, @PathVariable String title) {
    return learning.interest(id(a), title);
  }

  @PostMapping("/interests")
  public Object updateInterest(Authentication a, @RequestBody InterestRequest request) {
    var b = body(request);
    return learning.updateInterest(id(a), b);
  }
}
