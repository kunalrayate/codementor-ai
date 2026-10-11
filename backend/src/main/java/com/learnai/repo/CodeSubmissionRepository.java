package com.learnai.repo;
import com.learnai.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.*;

public interface CodeSubmissionRepository extends JpaRepository<CodeSubmission, Long> {
  List<CodeSubmission> findByStudentOrderByIdDesc(User student);
  @Query("select distinct s.problem.id from CodeSubmission s where s.student = :s and s.status = 'PASSED' and s.problem is not null")
  List<Long> solvedIds(@Param("s") User student);
  @Query("select s.problem.topic, count(s), sum(case when s.status = 'PASSED' then 1 else 0 end) from CodeSubmission s where s.student = :s and s.problem is not null group by s.problem.topic")
  List<Object[]> topicStats(@Param("s") User student);
}
