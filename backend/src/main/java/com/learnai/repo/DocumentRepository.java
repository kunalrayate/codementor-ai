package com.learnai.repo;
import com.learnai.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.*;

public interface DocumentRepository extends JpaRepository<LearningDocument, Long> {
  List<LearningDocument> findAllByOrderByIdDesc();
}
