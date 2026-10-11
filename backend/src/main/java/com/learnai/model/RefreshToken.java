package com.learnai.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity @Table(name="refresh_tokens") @Getter @Setter @NoArgsConstructor
public class RefreshToken {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  private String token;
  @ManyToOne(optional=false) private User user;
  private LocalDateTime expiresAt;
}
