package com.learnai.repo;
import com.learnai.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.*;

public interface LessonRepository extends JpaRepository<Lesson, Long> {
  List<Lesson> findByModuleOrderByPositionAsc(CourseModule module);
  List<Lesson> findByModuleCourseId(Long courseId);
  long countByModuleCourseId(Long courseId);
  Optional<Lesson> findFirstByModuleAndTitle(CourseModule module, String title);
}
