package com.learnai.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity @Table(name="ai_usage") @Getter @Setter @NoArgsConstructor
public class AiUsage {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @ManyToOne(optional=false) private User user;
  private LocalDate usageDate;
  private int requests;
  private long inputTokens;
  private long outputTokens;
  private long totalTokens;
}
