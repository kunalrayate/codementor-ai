package com.learnai.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** One quiz attempt: the exact questions (and shuffled options) that were served, so grading cannot be tampered with. */
@Entity @Table(name="quiz_attempts") @Getter @Setter @NoArgsConstructor
public class QuizAttempt {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @ManyToOne(optional=false) private User student;
  @ManyToOne(optional=false) private Quiz quiz;
  private String snapshot;
  private boolean submitted;
  private LocalDateTime createdAt = LocalDateTime.now();
}
