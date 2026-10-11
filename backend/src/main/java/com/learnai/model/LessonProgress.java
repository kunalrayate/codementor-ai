package com.learnai.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity @Table(name="lesson_progress") @Getter @Setter @NoArgsConstructor
public class LessonProgress {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @ManyToOne(optional=false) private User student;
  @ManyToOne(optional=false) private Lesson lesson;
  private int percentWatched;
  private int positionSeconds;
  private boolean completed;
  private LocalDateTime lastWatchedAt;
}
