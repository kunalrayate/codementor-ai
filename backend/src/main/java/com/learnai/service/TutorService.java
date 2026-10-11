package com.learnai.service;

import com.learnai.common.*;
import com.learnai.dto.Dtos.*;
import com.learnai.model.*;
import com.learnai.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TutorService {
  private final AiGateway ai;
  private final AiGuardrailService guard;
  private final AiConversationRepository convs;
  private final AiMessageRepository msgs;
  private final LessonRepository lessons;
  private final RagService rag;
  private final UsageService usage;
  private final Access access;
  private final OfflineStudyService offline;
  @Value("${app.ai.history-size}") private int historySize;

  public ChatResponse chat(User u, ChatRequest r) {
    guard.check(r.message());

    AiConversation c;
    if (r.conversationId() == null) {
      c = new AiConversation();
      c.setUser(u);
      c.setTitle(Mappers.clip(r.message(), 60));
      convs.save(c);
    } else {
      c = convs.findById(r.conversationId()).filter(x -> x.getUser().getId().equals(u.getId()))
          .orElseThrow(() -> ApiException.notFound("Conversation"));
    }

    // only the most recent N messages are sent to the LLM (token control)
    List<AiMessage> recent = new ArrayList<>(msgs.findByConversationOrderByIdDesc(c, PageRequest.of(0, historySize)));
    Collections.reverse(recent);
    List<Message> history = recent.stream().map(m ->
        "user".equals(m.getRole()) ? (Message) new UserMessage(m.getContent()) : new AssistantMessage(m.getContent())).toList();

    String system = Prompts.tutor(u.getLevel());
    Long courseId = null;
    if (r.lessonId() != null) {
      Lesson l = lessons.findById(r.lessonId()).orElseThrow(() -> ApiException.notFound("Lesson"));
      access.requireEnrolledOrStaff(u, l.getModule().getCourse());
      courseId = l.getModule().getCourse().getId();
      system += "\n\nThe student is currently studying the lesson \"" + l.getTitle() + "\":\n" + Mappers.clip(l.getContent(), 4000);
    }
    if (r.useMaterials()) {
      List<String> hits = rag.search(u, r.message(), courseId, 3);
      if (!hits.isEmpty())
        system += "\n\nRelevant excerpts from uploaded course materials (prefer them, and mention when you rely on them):\n- " + String.join("\n- ", hits);
    }

    String reply;
    try {
      reply = ai.ask(u, system, history, r.message());
    } catch (ApiException e) {
      if (e.getStatus() != org.springframework.http.HttpStatus.BAD_GATEWAY) throw e; // rate/quota messages are passed on
      reply = offline.tutorAnswer(u, r.message(), r.lessonId(), e.getMessage());
    }
    save(c, "user", r.message());
    save(c, "assistant", reply);
    return new ChatResponse(c.getId(), reply, usage.status(u).remaining());
  }

  public List<ConversationDto> conversations(User u) {
    return convs.findByUserOrderByCreatedAtDesc(u).stream().map(c -> new ConversationDto(c.getId(), c.getTitle(), c.getCreatedAt())).toList();
  }

  public List<MessageDto> messages(User u, Long conversationId) {
    AiConversation c = convs.findById(conversationId).filter(x -> x.getUser().getId().equals(u.getId()))
        .orElseThrow(() -> ApiException.notFound("Conversation"));
    return msgs.findByConversationOrderByIdAsc(c).stream().map(m -> new MessageDto(m.getRole(), clean(m.getContent()))).toList();
  }

  private void save(AiConversation c, String role, String content) {
    AiMessage m = new AiMessage();
    m.setConversation(c);
    m.setRole(role);
    m.setContent(content);
    msgs.save(m);
  }

  public void deleteConversation(User u, Long id) {
    AiConversation c = convs.findById(id).filter(x -> x.getUser().getId().equals(u.getId())).orElseThrow(() -> ApiException.notFound("Conversation"));
    convs.delete(c);
  }

  public void deleteAllConversations(User u) { convs.deleteAll(convs.findByUserOrderByCreatedAtDesc(u)); }

  /** Old stored answers may contain a raw provider error; show a short sentence instead. */
  private static String clean(String text) {
    return text == null ? "" : text.replaceAll("(?s)\\(The AI provider returned an error:.*?\\]\\s*\\)", "(the AI service was busy)");
  }
}
