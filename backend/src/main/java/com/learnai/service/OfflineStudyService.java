package com.learnai.service;

import com.learnai.dto.Dtos.FlashcardDto;
import com.learnai.model.*;
import com.learnai.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.Pattern;

/** Non-AI study helpers used when no AI provider is configured, so students are never left with an error. */
@Service
@RequiredArgsConstructor
public class OfflineStudyService {
  private final LessonRepository lessons;
  private final FlashcardRepository cards;
  private final RagService rag;
  private final AiSettingsService settings;

  public String tutorAnswer(User u, String question, Long lessonId, String reason) {
    reason = friendlyReason(reason);
    Set<String> terms = RagService.terms(question);
    record Scored(Lesson lesson, double score) {}
    List<Lesson> all = lessons.findAll();
    List<Scored> scored = new ArrayList<>();
    for (Lesson l : all) {
      String text = l.getTitle() + " " + l.getTitle() + " " + (l.getContent() == null ? "" : l.getContent());
      double s = RagService.keyword(text, terms);
      if (s > 0 && lessonId != null && lessonId.equals(l.getId())) s *= 1.5;
      scored.add(new Scored(l, s));
    }
    List<Scored> top = scored.stream().filter(x -> x.score() > 0)
        .sorted(Comparator.comparingDouble(Scored::score).reversed()).limit(2).toList();

    StringBuilder sb = new StringBuilder(settings.configured()
        ? "The AI provider could not answer right now (" + reason + ")\nShowing the course notes instead:\n\n"
        : "Offline tutor mode (" + reason + ")\nThe full AI Tutor is off until an admin adds an API key in Admin > AI Settings. Until then I can quote the course material:\n\n");
    if (top.isEmpty()) {
      sb.append("I could not find that in the course lessons. Try a keyword from a lesson title, for example: ");
      sb.append(String.join(", ", all.stream().map(Lesson::getTitle).distinct().limit(8).toList())).append(".");
    } else {
      for (Scored s : top) {
        sb.append("Lesson: ").append(s.lesson().getTitle()).append("\n").append(adapt(s.lesson().getContent(), u.getLevel())).append("\n\n");
      }
      List<Flashcard> fc = cards.findByLesson(top.get(0).lesson());
      if (!fc.isEmpty()) {
        sb.append("Quick revision:\n");
        fc.stream().limit(3).forEach(f -> sb.append("- ").append(f.getQuestion()).append(" -> ").append(f.getAnswer()).append("\n"));
      }
    }
    try {
      List<String> hits = rag.search(u, question, null, 2);
      if (!hits.isEmpty()) sb.append("\nFrom your uploaded materials:\n- ").append(String.join("\n- ", hits));
    } catch (Exception ignored) { }
    return sb.toString();
  }

  /** Fill-in-the-blank flashcards built from the lesson text (not saved, so real AI cards can replace them later). */
  public List<FlashcardDto> flashcards(Lesson l) {
    String text = l.getContent() == null ? "" : l.getContent().replaceAll("\\s+", " ").trim();
    List<FlashcardDto> out = new ArrayList<>();
    int n = 0;
    for (String s : text.split("(?<=[.!?])\\s+")) {
      if (s.length() < 30) continue;
      String word = longestWord(s);
      if (word == null) continue;
      n++;
      out.add(new FlashcardDto((long) -n, "Fill in the blank (" + l.getTitle() + "): " + s.replaceFirst(Pattern.quote(word), "_____"), word + " - " + s));
      if (n >= 6) break;
    }
    if (out.isEmpty()) out.add(new FlashcardDto(-1L, "What is " + l.getTitle() + "?", text.isEmpty() ? "No lesson content yet." : (text.length() > 200 ? text.substring(0, 200) : text)));
    return out;
  }

  private static String longestWord(String sentence) {
    String best = null;
    for (String w : sentence.split("[^A-Za-z]+")) if (w.length() >= 5 && (best == null || w.length() > best.length())) best = w;
    return best;
  }

  /** BEGINNER sees only the key idea, other levels see the full lesson text. */
  private static String adapt(String content, Level level) {
    if (content == null) return "";
    if (level != Level.BEGINNER) return content;
    String[] s = content.split("(?<=[.!?])\\s+");
    return String.join(" ", Arrays.copyOfRange(s, 0, Math.min(2, s.length))) + "\n(Beginner view: the key idea. Choose INTERMEDIATE on your dashboard for the full lesson.)";
  }

  private static String friendlyReason(String r) {
    if (r == null || r.isBlank()) return "the AI is unavailable";
    String l = r.toLowerCase();
    if (l.contains("not configured")) return "AI is not configured";
    if (l.contains("busy") || l.contains("503") || l.contains("high demand") || l.contains("unavailable")) return "the AI service is busy";
    if (l.contains("quota") || l.contains("429") || l.contains("rate limit")) return "the AI quota is used up";
    if (l.contains("in time") || l.contains("timed out")) return "the AI was too slow";
    if (r.contains("{") || r.length() > 110) return "the AI service had a problem";
    return r;
  }
}
