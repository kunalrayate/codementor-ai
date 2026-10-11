package com.learnai.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity @Table(name="courses") @Getter @Setter @NoArgsConstructor
public class Course {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  private String title;
  private String description;
  @ManyToOne(optional=false) private User instructor;
  private LocalDateTime createdAt = LocalDateTime.now();
}
