package com.learnai.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity @Table(name="lessons") @Getter @Setter @NoArgsConstructor
public class Lesson {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  private String title;
  private String description;
  private String videoUrl;
  private String content;
  private int position;
  @ManyToOne(optional=false) private CourseModule module;
}
