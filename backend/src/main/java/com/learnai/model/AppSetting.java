package com.learnai.model;
import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="app_settings") @Getter @Setter @NoArgsConstructor
public class AppSetting {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  private String skey;
  private String svalue;
}
