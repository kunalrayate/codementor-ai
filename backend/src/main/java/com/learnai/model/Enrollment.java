package com.learnai.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity @Table(name="enrollments") @Getter @Setter @NoArgsConstructor
public class Enrollment {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @ManyToOne(optional=false) private User student;
  @ManyToOne(optional=false) private Course course;
  private LocalDateTime enrolledAt = LocalDateTime.now();
}
