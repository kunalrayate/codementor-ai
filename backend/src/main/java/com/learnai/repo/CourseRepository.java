package com.learnai.repo;
import com.learnai.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.*;

public interface CourseRepository extends JpaRepository<Course, Long> {
  List<Course> findByInstructor(User instructor);
  Optional<Course> findFirstByTitle(String title);
}
