package com.learnai.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity @Table(name="flashcards") @Getter @Setter @NoArgsConstructor
public class Flashcard {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @ManyToOne(optional=false) private Lesson lesson;
  private String question;
  private String answer;
}
