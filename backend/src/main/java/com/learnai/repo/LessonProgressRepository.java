package com.learnai.repo;
import com.learnai.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.*;

public interface LessonProgressRepository extends JpaRepository<LessonProgress, Long> {
  Optional<LessonProgress> findByStudentAndLesson(User student, Lesson lesson);
  List<LessonProgress> findByStudentAndLessonModuleCourseId(User student, Long courseId);
  long countByStudentAndCompletedTrueAndLessonModuleCourseId(User student, Long courseId);
  long countByStudentAndCompletedTrue(User student);
}
