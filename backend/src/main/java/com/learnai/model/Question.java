package com.learnai.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity @Table(name="questions") @Getter @Setter @NoArgsConstructor
public class Question {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @ManyToOne(optional=false) private Quiz quiz;
  private String questionText;
  @Column(name = "option_a") private String optionA;
  @Column(name = "option_b") private String optionB;
  @Column(name = "option_c") private String optionC;
  @Column(name = "option_d") private String optionD;
  private String correct;
  private String explanation;
}
