package com.learnai.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity @Table(name="code_submissions") @Getter @Setter @NoArgsConstructor
public class CodeSubmission {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @ManyToOne(optional=false) private User student;
  @ManyToOne private CodingProblem problem;
  private String sourceCode;
  private String status;
  private int passedCount;
  private int totalCount;
  private LocalDateTime createdAt = LocalDateTime.now();
}
