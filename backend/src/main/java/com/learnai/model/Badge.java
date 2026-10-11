package com.learnai.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity @Table(name="badges") @Getter @Setter @NoArgsConstructor
public class Badge {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  private String code;
  private String name;
  private String description;
  private Integer minXp;
}
