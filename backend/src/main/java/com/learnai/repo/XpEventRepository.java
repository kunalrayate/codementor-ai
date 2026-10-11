package com.learnai.repo;
import com.learnai.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.*;

public interface XpEventRepository extends JpaRepository<XpEvent, Long> {
  boolean existsByStudentAndEventTypeAndRefId(User student, String type, Long refId);
  boolean existsByStudentAndEventType(User student, String type);
  List<XpEvent> findTop50ByOrderByIdDesc();
  @Query("select coalesce(sum(x.xp),0L) from XpEvent x where x.student = :s") long totalXp(@Param("s") User s);
  @Query("select x.student.id, x.student.name, sum(x.xp) from XpEvent x group by x.student.id, x.student.name order by sum(x.xp) desc")
  List<Object[]> globalBoard();
  @Query("select x.student.id, x.student.name, sum(x.xp) from XpEvent x where x.courseId = :c group by x.student.id, x.student.name order by sum(x.xp) desc")
  List<Object[]> courseBoard(@Param("c") Long courseId);
  @Query("select coalesce(sum(x.xp),0L) from XpEvent x where x.student = :s and x.courseId = :c")
  long courseXp(@Param("s") User s, @Param("c") Long courseId);
  @Query(value = "select distinct date(created_at) from xp_events where student_id = :id and created_at >= :since", nativeQuery = true)
  List<Object> activeDays(@Param("id") Long id, @Param("since") LocalDateTime since);
  List<XpEvent> findTop10ByStudentOrderByIdDesc(User student);
  @Query("select x.student.id, count(x) from XpEvent x where x.eventType = 'COURSE_COMPLETED' group by x.student.id")
  List<Object[]> completedCourses();
}
