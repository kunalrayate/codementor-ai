package com.learnai.repo;
import com.learnai.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.*;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {
  List<DocumentChunk> findByDocument(LearningDocument document);
  @Query("select c from DocumentChunk c left join c.document d left join d.course co where d.uploadedBy.id = :uid or co.id = :cid")
  List<DocumentChunk> visible(@Param("uid") Long userId, @Param("cid") Long courseId);
  List<DocumentChunk> findByDocumentOrderByIdAsc(LearningDocument document);
}
