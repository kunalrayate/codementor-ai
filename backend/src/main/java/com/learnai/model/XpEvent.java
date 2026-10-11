package com.learnai.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity @Table(name="xp_events") @Getter @Setter @NoArgsConstructor
public class XpEvent {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @ManyToOne(optional=false) private User student;
  private String eventType;
  private int xp;
  private Long refId;
  private Long courseId;
  private LocalDateTime createdAt = LocalDateTime.now();
}
