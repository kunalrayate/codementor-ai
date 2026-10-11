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
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {
  private final TutorService tutor;
  private final FlashcardService flashcards;
  private final QuizService quizzes;
  private final SummaryService summaries;
  private final AiSettingsService settings;

  @GetMapping("/status")
  public Map<String, Object> status() {
    AiSettingsService.Cfg c = settings.effective();
    return Map.of("configured", settings.configured(), "provider", c.provider(), "source", c.source());
  }

  @PostMapping("/chat") public ChatResponse chat(@AuthenticationPrincipal User u, @Valid @RequestBody ChatRequest r) { return tutor.chat(u, r); }
  @GetMapping("/conversations") public List<ConversationDto> conversations(@AuthenticationPrincipal User u) { return tutor.conversations(u); }
  @GetMapping("/conversations/{id}") public List<MessageDto> messages(@AuthenticationPrincipal User u, @PathVariable Long id) { return tutor.messages(u, id); }

  @DeleteMapping("/conversations/{id}") public void deleteConversation(@AuthenticationPrincipal User u, @PathVariable Long id) { tutor.deleteConversation(u, id); }
  @DeleteMapping("/conversations") public void deleteAllConversations(@AuthenticationPrincipal User u) { tutor.deleteAllConversations(u); }

  @PostMapping("/flashcards/generate") public List<FlashcardDto> flashcards(@AuthenticationPrincipal User u, @Valid @RequestBody LessonIdRequest r) { return flashcards.forLesson(u, r.lessonId()); }

  @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
  @PostMapping("/quiz/generate") public QuizDto generateQuiz(@AuthenticationPrincipal User u, @Valid @RequestBody QuizGenRequest r) { return quizzes.generate(u, r); }

  @PostMapping("/summary") public TextResponse summary(@AuthenticationPrincipal User u, @RequestBody SummaryRequest r) { return new TextResponse(summaries.summarize(u, r)); }
}
