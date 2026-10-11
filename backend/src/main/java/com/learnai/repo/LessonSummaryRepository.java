package com.learnai.repo;
import com.learnai.model.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface LessonSummaryRepository extends JpaRepository<LessonSummary, Long> {
  Optional<LessonSummary> findByLessonIdAndKindAndLevel(Long lessonId, String kind, String level);
}
