package com.learnai.repo;
import com.learnai.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.*;

public interface QuizRepository extends JpaRepository<Quiz, Long> {
  List<Quiz> findByLesson(Lesson lesson);
  List<Quiz> findByLessonAndStatus(Lesson lesson, QuizStatus status);
  List<Quiz> findByLessonModuleCourseId(Long courseId);
  List<Quiz> findByLessonModuleCourseIdAndStatus(Long courseId, QuizStatus status);
}
