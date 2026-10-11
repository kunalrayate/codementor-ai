package com.learnai.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity @Table(name="modules") @Getter @Setter @NoArgsConstructor
public class CourseModule {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  private String title;
  private int position;
  @ManyToOne(optional=false) private Course course;
}
