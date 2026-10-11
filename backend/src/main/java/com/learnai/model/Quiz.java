package com.learnai.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity @Table(name="quizzes") @Getter @Setter @NoArgsConstructor
public class Quiz {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @ManyToOne(optional=false) private Lesson lesson;
  private String title;
  @Enumerated(EnumType.STRING) private QuizStatus status = QuizStatus.DRAFT;
  private boolean aiGenerated;
  private Integer maxAttempts;
  @ManyToOne private User createdBy;
  private LocalDateTime createdAt = LocalDateTime.now();
}
