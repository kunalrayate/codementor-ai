package com.learnai.repo;
import com.learnai.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.*;

public interface ModuleRepository extends JpaRepository<CourseModule, Long> {
  List<CourseModule> findByCourseOrderByPositionAsc(Course course);
  Optional<CourseModule> findFirstByCourseAndTitle(Course course, String title);
}
