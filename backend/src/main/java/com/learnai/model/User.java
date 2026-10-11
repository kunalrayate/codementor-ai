package com.learnai.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity @Table(name="users") @Getter @Setter @NoArgsConstructor
public class User {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  private String name;
  private String email;
  private String passwordHash;
  @Enumerated(EnumType.STRING) private Role role;
  @Enumerated(EnumType.STRING) private Level level = Level.BEGINNER;
  private boolean enabled = true;
  private LocalDateTime createdAt = LocalDateTime.now();
}
