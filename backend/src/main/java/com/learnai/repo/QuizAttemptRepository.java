package com.learnai.repo;
import com.learnai.model.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {
  Optional<QuizAttempt> findFirstByStudentAndQuizAndSubmittedFalseOrderByIdDesc(User student, Quiz quiz);
  void deleteByQuizAndStudentId(Quiz quiz, Long studentId);
}
