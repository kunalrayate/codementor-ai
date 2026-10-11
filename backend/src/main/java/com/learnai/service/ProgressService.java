package com.learnai.service;

import com.learnai.common.*;
import com.learnai.dto.Dtos.*;
import com.learnai.model.*;
import com.learnai.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProgressService {
  private final LessonProgressRepository progress;
  private final LessonRepository lessons;
  private final XpService xp;
  private final Access access;

  @Transactional
  public LessonProgressDto update(User u, Long lessonId, ProgressRequest r) {
    if (u.getRole() != Role.STUDENT) throw ApiException.forbidden("Only students track progress");
    Lesson l = lessons.findById(lessonId).orElseThrow(() -> ApiException.notFound("Lesson"));
    Course c = l.getModule().getCourse();
    access.requireEnrolledOrStaff(u, c);

    LessonProgress p = progress.findByStudentAndLesson(u, l).orElseGet(() -> {
      LessonProgress n = new LessonProgress();
      n.setStudent(u);
      n.setLesson(l);
      return n;
    });
    p.setPercentWatched(Math.max(p.getPercentWatched(), Math.max(0, Math.min(100, r.percentWatched()))));
    p.setPositionSeconds(Math.max(0, r.positionSeconds()));
    p.setLastWatchedAt(LocalDateTime.now());
    boolean newlyDone = !p.isCompleted() && p.getPercentWatched() >= 90;
    if (newlyDone) p.setCompleted(true);
    progress.save(p);

    int awarded = 0;
    if (newlyDone) {
      awarded += xp.award(u, "LESSON_COMPLETED", xp.rule("LESSON_COMPLETED", 50), l.getId(), c.getId(), true);
      long done = progress.countByStudentAndCompletedTrue(u);
      if (done >= 1) xp.badge(u, "FIRST_LESSON");
      if (done >= 10) xp.badge(u, "FAST_LEARNER");
      long inCourse = progress.countByStudentAndCompletedTrueAndLessonModuleCourseId(u, c.getId());
      if (inCourse >= lessons.countByModuleCourseId(c.getId())) {
        awarded += xp.award(u, "COURSE_COMPLETED", xp.rule("COURSE_COMPLETED", 500), c.getId(), c.getId(), true);
        xp.badge(u, "COURSE_MASTER");
      }
    }
    return new LessonProgressDto(l.getId(), p.getPercentWatched(), p.getPositionSeconds(), p.isCompleted(), awarded);
  }

  public CourseProgress forCourse(User u, Long courseId) {
    List<LessonProgressDto> list = progress.findByStudentAndLessonModuleCourseId(u, courseId).stream()
        .map(p -> new LessonProgressDto(p.getLesson().getId(), p.getPercentWatched(), p.getPositionSeconds(), p.isCompleted(), 0))
        .toList();
    int total = (int) lessons.countByModuleCourseId(courseId);
    int done = (int) list.stream().filter(LessonProgressDto::completed).count();
    return new CourseProgress(done, total, total == 0 ? 0 : done * 100 / total, list);
  }

  public LessonProgressDto forLesson(User u, Long lessonId) {
    Lesson l = lessons.findById(lessonId).orElseThrow(() -> ApiException.notFound("Lesson"));
    return progress.findByStudentAndLesson(u, l)
        .map(p -> new LessonProgressDto(lessonId, p.getPercentWatched(), p.getPositionSeconds(), p.isCompleted(), 0))
        .orElse(new LessonProgressDto(lessonId, 0, 0, false, 0));
  }
}
