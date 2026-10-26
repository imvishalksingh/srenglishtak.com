package com.readiq.backend.api;

import static com.readiq.backend.api.Input.*;
import static com.readiq.backend.dto.DtoMapper.body;

import com.readiq.backend.auth.ApiPrincipal;
import com.readiq.backend.dto.*;
import com.readiq.backend.service.AssessmentService;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AssessmentController {
  private final AssessmentService service;

  public AssessmentController(AssessmentService service) {
    this.service = service;
  }

  private ApiPrincipal p(Authentication a) {
    return a != null && a.getPrincipal() instanceof ApiPrincipal p ? p : null;
  }

  private boolean admin(Authentication a) {
    return p(a) != null && p(a).admin();
  }

  private ResponseEntity<?> created(Object o) {
    return ResponseEntity.status(201).body(o);
  }

  @GetMapping("/quizzes")
  public Object quizzes(Authentication a) {
    return service.quizzes(p(a).id(), admin(a), null);
  }

  @GetMapping("/quizzes/book/{bookId}")
  public Object quizzesByBook(@PathVariable UUID bookId, Authentication a) {
    return service.quizzes(p(a) == null ? null : p(a).id(), admin(a), bookId);
  }

  @GetMapping("/quizzes/{id}")
  public Object quiz(@PathVariable UUID id, Authentication a) {
    return service.quiz(id, admin(a));
  }

  @GetMapping("/quizzes/{id}/questions")
  public Object questions(@PathVariable UUID id, Authentication a) {
    return service.quiz(id, admin(a)).get("questions");
  }

  @PostMapping("/quizzes")
  @PreAuthorize("hasRole('ADMIN')")
  public Object createQuiz(@RequestBody QuizRequest request) {
    var b = body(request);
    return created(service.saveQuiz(null, b));
  }

  @PatchMapping("/quizzes/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public Object updateQuiz(@PathVariable UUID id, @RequestBody QuizRequest request) {
    var b = body(request);
    return service.saveQuiz(id, b);
  }

  @DeleteMapping("/quizzes/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public Object deleteQuiz(@PathVariable UUID id) {
    service.deleteQuiz(id);
    return map("message", "Quiz deleted successfully");
  }

  @PostMapping("/quizzes/{id}/questions")
  @PreAuthorize("hasRole('ADMIN')")
  public Object addQuestion(@PathVariable UUID id, @RequestBody QuestionRequest request) {
    var b = body(request);
    return created(service.addQuestion(id, b));
  }

  @PostMapping("/quizzes/{id}/submit")
  public Object submitQuiz(
      @PathVariable UUID id, @RequestBody AnswersRequest request, Authentication a) {
    var b = body(request);
    return created(service.submitQuiz(p(a).id(), id, b));
  }

  @GetMapping("/challenges")
  public Object challenges(Authentication a) {
    return service.challenges(p(a).id(), admin(a));
  }

  @GetMapping("/challenges/{id}")
  public Object challenge(@PathVariable UUID id, Authentication a) {
    return service.challenge(id, admin(a));
  }

  @PostMapping("/challenges")
  @PreAuthorize("hasRole('ADMIN')")
  public Object createChallenge(@RequestBody ChallengeRequest request) {
    var b = body(request);
    return created(service.saveChallenge(null, b));
  }

  @PatchMapping("/challenges/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public Object updateChallenge(@PathVariable UUID id, @RequestBody ChallengeRequest request) {
    var b = body(request);
    return service.saveChallenge(id, b);
  }

  @DeleteMapping("/challenges/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public Object deleteChallenge(@PathVariable UUID id) {
    service.deleteChallenge(id);
    return map("message", "Challenge deleted successfully");
  }

  @PostMapping("/challenges/{id}/start")
  public Object start(@PathVariable UUID id, Authentication a) {
    return service.start(p(a).id(), id);
  }

  @PostMapping("/challenges/{id}/submit")
  public Object submitChallenge(
      @PathVariable UUID id, @RequestBody AnswersRequest request, Authentication a) {
    var b = body(request);
    return created(service.submitChallenge(p(a).id(), id, b));
  }

  @GetMapping("/challenges/{id}/leaderboard")
  public Object leaderboard(@PathVariable UUID id) {
    return service.leaderboard(id);
  }
}
