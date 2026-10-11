package com.learnai.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity @Table(name="document_chunks") @Getter @Setter @NoArgsConstructor
public class DocumentChunk {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @ManyToOne(optional=false) private LearningDocument document;
  private String content;
  private String embedding;
}
