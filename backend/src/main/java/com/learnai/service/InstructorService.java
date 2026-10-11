package com.learnai.service;

import com.learnai.common.*;
import com.learnai.model.*;
import com.learnai.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class InstructorService {
  private final CourseRepository courses;
  private final EnrollmentRepository enrollments;
  private final LessonRepository lessons;
  private final LessonProgressRepository progress;
  private final QuizResultRepository results;
  private final XpEventRepository xp;
  private final Access access;

  public List<Map<String, Object>> analytics(User u, Long courseId) {
    Course c = courses.findById(courseId).orElseThrow(() -> ApiException.notFound("Course"));
    access.requireOwnerOrAdmin(u, c);
    long total = lessons.countByModuleCourseId(courseId);
    List<Map<String, Object>> out = new ArrayList<>();
    for (Enrollment e : enrollments.findByCourse(c)) {
      User s = e.getStudent();
      long done = progress.countByStudentAndCompletedTrueAndLessonModuleCourseId(s, courseId);
      Double avg = results.averageForCourse(s, courseId);
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("studentId", s.getId());
      row.put("name", s.getName());
      row.put("xp", xp.courseXp(s, courseId));
      row.put("completedLessons", done);
      row.put("totalLessons", total);
      row.put("completionPercent", total == 0 ? 0 : done * 100 / total);
      row.put("avgQuizPercent", avg == null ? null : Math.round(avg));
      out.add(row);
    }
    return out;
  }
}
