package com.learnai.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity @Table(name="test_cases") @Getter @Setter @NoArgsConstructor
public class TestCase {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @ManyToOne(optional=false) private CodingProblem problem;
  private String inputData;
  private String expectedOutput;
}
