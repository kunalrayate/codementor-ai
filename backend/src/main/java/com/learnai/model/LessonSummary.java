package com.learnai.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="summaries") @Getter @Setter @NoArgsConstructor
public class LessonSummary {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  private Long lessonId;
  private String kind;
  private String level;
  private String body;
  private LocalDateTime createdAt = LocalDateTime.now();
}
