package com.learnai.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity @Table(name="quiz_results") @Getter @Setter @NoArgsConstructor
public class QuizResult {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @ManyToOne(optional=false) private User student;
  @ManyToOne(optional=false) private Quiz quiz;
  private int score;
  private int total;
  private int percent;
  private boolean passed;
  private LocalDateTime createdAt = LocalDateTime.now();
}
