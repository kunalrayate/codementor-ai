package com.learnai.web;

import com.learnai.common.*;
import com.learnai.dto.Dtos.*;
import com.learnai.model.*;
import com.learnai.repo.*;
import com.learnai.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class QuizController {
  private final QuizService quizzes;
  public record ManualQuiz(String title, List<QuestionEdit> questions) {}
  public record AttemptsRequest(Integer maxAttempts) {}

  @GetMapping("/lessons/{id}/quizzes") public List<QuizDto> forLesson(@AuthenticationPrincipal User u, @PathVariable Long id) { return quizzes.forLesson(u, id); }

  @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
  @PostMapping("/lessons/{id}/quizzes") public QuizDto create(@AuthenticationPrincipal User u, @PathVariable Long id, @RequestBody ManualQuiz r) { return quizzes.createManual(u, id, r.title(), r.questions()); }

  @GetMapping("/quizzes/{id}") public QuizDto get(@AuthenticationPrincipal User u, @PathVariable Long id) { return quizzes.get(u, id); }

  @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
  @PatchMapping("/quizzes/{id}/status") public QuizDto status(@AuthenticationPrincipal User u, @PathVariable Long id, @RequestBody StatusRequest r) { return quizzes.setStatus(u, id, r.status()); }

  @PreAuthorize("hasRole('STUDENT')")
  @PostMapping("/quizzes/{id}/start") public AttemptDto start(@AuthenticationPrincipal User u, @PathVariable Long id) { return quizzes.start(u, id); }

  @PreAuthorize("hasRole('STUDENT')")
  @PostMapping("/quizzes/{id}/submit") public QuizResultDto submit(@AuthenticationPrincipal User u, @PathVariable Long id, @RequestBody SubmitRequest r) { return quizzes.submit(u, id, r); }

  @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
  @PutMapping("/questions/{id}") public QuestionDto edit(@AuthenticationPrincipal User u, @PathVariable Long id, @RequestBody QuestionEdit r) { return quizzes.editQuestion(u, id, r); }

  @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
  @DeleteMapping("/questions/{id}") public void delete(@AuthenticationPrincipal User u, @PathVariable Long id) { quizzes.deleteQuestion(u, id); }

  /** Attempts allowed per student: null/0 = unlimited, 1 = single attempt per topic. */
  @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
  @PatchMapping("/quizzes/{id}/attempts") public QuizDto attempts(@AuthenticationPrincipal User u, @PathVariable Long id, @RequestBody AttemptsRequest r) { return quizzes.setMaxAttempts(u, id, r.maxAttempts()); }

  @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
  @PutMapping("/courses/{id}/quizzes/attempts") public Map<String, Object> courseAttempts(@AuthenticationPrincipal User u, @PathVariable Long id, @RequestBody AttemptsRequest r) { return Map.of("updated", quizzes.setMaxAttemptsForCourse(u, id, r.maxAttempts())); }

  @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
  @GetMapping("/quizzes/{id}/results") public List<Map<String, Object>> results(@AuthenticationPrincipal User u, @PathVariable Long id) { return quizzes.studentResults(u, id); }

  @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
  @DeleteMapping("/quizzes/{id}/attempts/{studentId}") public void allowRetake(@AuthenticationPrincipal User u, @PathVariable Long id, @PathVariable Long studentId) { quizzes.allowRetake(u, id, studentId); }

  @PreAuthorize("hasRole('STUDENT')")
  @GetMapping("/me/quizzes") public List<Map<String, Object>> myQuizzes(@AuthenticationPrincipal User u) { return quizzes.myQuizzes(u); }
}
