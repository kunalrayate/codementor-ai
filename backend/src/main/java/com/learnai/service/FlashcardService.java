package com.learnai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.learnai.common.*;
import com.learnai.dto.Dtos.FlashcardDto;
import com.learnai.model.*;
import com.learnai.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FlashcardService {
  private final FlashcardRepository cards;
  private final LessonRepository lessons;
  private final AiGateway ai;
  private final Access access;
  private final OfflineStudyService offline;

  public record Item(String question, String answer) {}

  /** Cards are generated once per lesson and cached in MySQL. Without AI, simple fill-in-the-blank cards are returned. */
  public List<FlashcardDto> forLesson(User u, Long lessonId) {
    Lesson l = lessons.findById(lessonId).orElseThrow(() -> ApiException.notFound("Lesson"));
    access.requireEnrolledOrStaff(u, l.getModule().getCourse());
    List<Flashcard> existing = cards.findByLesson(l);
    if (existing.isEmpty()) {
      try {
        String prompt = """
            Create 8 flashcards from the lesson below.
            Return ONLY a JSON array: [{"question": "...", "answer": "..."}]. Answers must be 1-2 sentences.

            LESSON TITLE: %s
            LESSON CONTENT:
            %s
            """.formatted(l.getTitle(), Mappers.clip(l.getContent() != null ? l.getContent() : l.getDescription(), 6000));
        String raw = ai.ask(u, Prompts.GENERATOR, List.of(), prompt);
        List<Item> items = JsonUtil.parseArray(raw, new TypeReference<List<Item>>() {});
        for (Item it : items) {
          if (it.question() == null || it.answer() == null) continue;
          Flashcard f = new Flashcard();
          f.setLesson(l);
          f.setQuestion(it.question());
          f.setAnswer(it.answer());
          cards.save(f);
        }
        existing = cards.findByLesson(l);
      } catch (ApiException e) {
        if (e.getStatus() != HttpStatus.BAD_GATEWAY) throw e;
        return offline.flashcards(l);
      }
    }
    return existing.stream().map(f -> new FlashcardDto(f.getId(), f.getQuestion(), f.getAnswer())).toList();
  }
}
