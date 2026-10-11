package com.learnai.service;

import com.learnai.common.*;
import com.learnai.dto.Dtos.SummaryRequest;
import com.learnai.model.*;
import com.learnai.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SummaryService {
  private final AiGateway ai;
  private final LessonRepository lessons;
  private final RagService rag;
  private final Access access;
  private final LessonSummaryRepository cache;

  /** Lesson summaries are generated once per (lesson, type, level) and cached, so repeated clicks cost nothing. */
  public String summarize(User u, SummaryRequest r) {
    String kind = r.type() == null ? "SHORT" : r.type().toUpperCase();
    if (!List.of("SHORT", "DETAILED", "EXAM").contains(kind)) kind = "SHORT";
    String level = (u.getLevel() == null ? Level.BEGINNER : u.getLevel()).name();
    String text;
    Long lessonId = r.lessonId();
    if (lessonId != null) {
      Lesson l = lessons.findById(lessonId).orElseThrow(() -> ApiException.notFound("Lesson"));
      access.requireEnrolledOrStaff(u, l.getModule().getCourse());
      var hit = cache.findByLessonIdAndKindAndLevel(lessonId, kind, level);
      if (hit.isPresent()) return hit.get().getBody();
      text = l.getTitle() + ". " + (l.getContent() != null ? l.getContent() : l.getDescription());
    } else if (r.documentId() != null) {
      text = rag.documentText(u, r.documentId(), 8000);
    } else throw ApiException.badRequest("lessonId or documentId is required");

    String style = switch (kind) {
      case "DETAILED" -> "a detailed summary organised with headings";
      case "EXAM" -> "an exam-oriented summary: key concepts, important definitions, and 3 likely exam questions";
      default -> "a short summary (max 5 sentences) followed by a list of key concepts";
    };
    try {
      String out = ai.ask(u, Prompts.SUMMARIZER, List.of(),
          "Write " + style + " for a " + level + " learner of the following material:\n\n" + Mappers.clip(text, 8000));
      if (lessonId != null) {
        LessonSummary s = new LessonSummary();
        s.setLessonId(lessonId); s.setKind(kind); s.setLevel(level); s.setBody(out);
        try { cache.save(s); } catch (Exception ignored) { /* a parallel request saved it first */ }
      }
      return out;
    } catch (ApiException e) {
      if (e.getStatus() != HttpStatus.BAD_GATEWAY) throw e;
      return fallback(text, e.getMessage());
    }
  }

  /** Non-AI fallback so the button still does something useful. Never cached. */
  private String fallback(String text, String reason) {
    String[] sentences = text.replaceAll("\\s+", " ").split("(?<=[.!?])\\s+");
    boolean notConfigured = reason != null && reason.toLowerCase().contains("not configured");
    StringBuilder sb = new StringBuilder(notConfigured
        ? "ℹ AI is not configured, so this is a basic summary made from the lesson text.\n"
        : "⚠ The AI is busy right now, so this is a basic summary made from the lesson text. Try again in a minute for the full AI version.\n");
    Arrays.stream(sentences).limit(4).forEach(s -> sb.append("- ").append(s).append("\n"));
    return sb.toString();
  }
}
