package com.learnai.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity @Table(name="ai_conversations") @Getter @Setter @NoArgsConstructor
public class AiConversation {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @ManyToOne(optional=false) private User user;
  private String title;
  private LocalDateTime createdAt = LocalDateTime.now();
}
