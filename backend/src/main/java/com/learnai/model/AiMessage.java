package com.learnai.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity @Table(name="ai_messages") @Getter @Setter @NoArgsConstructor
public class AiMessage {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @ManyToOne(optional=false) private AiConversation conversation;
  private String role;
  private String content;
  private LocalDateTime createdAt = LocalDateTime.now();
}
