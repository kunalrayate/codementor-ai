package com.learnai.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity @Table(name="coding_problems") @Getter @Setter @NoArgsConstructor
public class CodingProblem {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  private String title;
  private String description;
  private String starterCode;
  private String topic;
  private String difficulty;
  @ManyToOne private User createdBy;
}
