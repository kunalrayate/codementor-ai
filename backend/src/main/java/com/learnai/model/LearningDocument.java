package com.learnai.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity @Table(name="documents") @Getter @Setter @NoArgsConstructor
public class LearningDocument {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  private String title;
  private String filePath;
  @ManyToOne private Course course;
  @ManyToOne(optional=false) private User uploadedBy;
  private LocalDateTime createdAt = LocalDateTime.now();
}
