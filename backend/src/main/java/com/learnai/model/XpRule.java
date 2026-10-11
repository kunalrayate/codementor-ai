package com.learnai.model;
import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="xp_rules") @Getter @Setter @NoArgsConstructor
public class XpRule {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  private String code;
  private int xp;
  private String description;
}
