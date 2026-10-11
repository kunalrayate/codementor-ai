package com.learnai.repo;
import com.learnai.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.*;

public interface QuizResultRepository extends JpaRepository<QuizResult, Long> {
  List<QuizResult> findByStudent(User student);
  @Query("select q.lesson.module.title, avg(r.percent) from QuizResult r join r.quiz q where r.student = :s group by q.lesson.module.title")
  List<Object[]> averageByModule(@Param("s") User student);
  @Query("select avg(r.percent) from QuizResult r where r.student = :s and r.quiz.lesson.module.course.id = :c")
  Double averageForCourse(@Param("s") User student, @Param("c") Long courseId);
  @Query("select q.lesson.id, q.lesson.title, avg(r.percent) from QuizResult r join r.quiz q where r.student = :s group by q.lesson.id, q.lesson.title")
  List<Object[]> averageByLesson(@Param("s") User student);
  List<QuizResult> findByStudentAndQuizOrderByIdAsc(User student, Quiz quiz);
  List<QuizResult> findByQuizOrderByIdDesc(Quiz quiz);
  void deleteByQuizAndStudentId(Quiz quiz, Long studentId);
}
