package com.learnai.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity @Table(name="user_badges") @Getter @Setter @NoArgsConstructor
public class UserBadge {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @ManyToOne(optional=false) private User user;
  @ManyToOne(optional=false) private Badge badge;
  private LocalDateTime awardedAt = LocalDateTime.now();
}
