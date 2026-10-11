package com.learnai.service;

import com.learnai.common.*;
import com.learnai.dto.Dtos.*;
import com.learnai.model.*;
import com.learnai.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseService {
  private final CourseRepository courses;
  private final ModuleRepository modules;
  private final LessonRepository lessons;
  private final EnrollmentRepository enrollments;
  private final Access access;

  public List<CourseDto> list() { return courses.findAll().stream().map(Mappers::course).toList(); }

  public List<CourseDto> mine(User u) {
    if (u.getRole() == Role.STUDENT) return enrollments.findByStudent(u).stream().map(e -> Mappers.course(e.getCourse())).toList();
    if (u.getRole() == Role.INSTRUCTOR) return courses.findByInstructor(u).stream().map(Mappers::course).toList();
    return list();
  }

  @Transactional
  public CourseDto create(User u, CourseRequest r) {
    Course c = new Course();
    c.setTitle(r.title());
    c.setDescription(r.description());
    c.setInstructor(u);
    return Mappers.course(courses.save(c));
  }

  public CourseDetail detail(User u, Long id) {
    Course c = course(id);
    boolean enrolled = u.getRole() != Role.STUDENT || enrollments.existsByStudentAndCourse(u, c);
    List<ModuleDto> mods = modules.findByCourseOrderByPositionAsc(c).stream()
        .map(m -> new ModuleDto(m.getId(), m.getTitle(), m.getPosition(),
            lessons.findByModuleOrderByPositionAsc(m).stream().map(l -> Mappers.lesson(l, false)).toList()))
        .toList();
    return new CourseDetail(Mappers.course(c), mods, enrolled, access.isOwnerOrAdmin(u, c));
  }

  @Transactional
  public ModuleDto addModule(User u, Long courseId, ModuleRequest r) {
    Course c = course(courseId);
    access.requireOwnerOrAdmin(u, c);
    CourseModule m = new CourseModule();
    m.setTitle(r.title());
    m.setCourse(c);
    m.setPosition(r.position() != null ? r.position() : modules.findByCourseOrderByPositionAsc(c).size() + 1);
    modules.save(m);
    return new ModuleDto(m.getId(), m.getTitle(), m.getPosition(), List.of());
  }

  @Transactional
  public LessonDto addLesson(User u, Long moduleId, LessonRequest r) {
    CourseModule m = modules.findById(moduleId).orElseThrow(() -> ApiException.notFound("Module"));
    access.requireOwnerOrAdmin(u, m.getCourse());
    Lesson l = new Lesson();
    l.setModule(m);
    l.setPosition(r.position() != null ? r.position() : lessons.findByModuleOrderByPositionAsc(m).size() + 1);
    apply(l, r);
    return Mappers.lesson(lessons.save(l), true);
  }

  @Transactional
  public LessonDto updateLesson(User u, Long id, LessonRequest r) {
    Lesson l = lesson(id);
    access.requireOwnerOrAdmin(u, l.getModule().getCourse());
    apply(l, r);
    if (r.position() != null) l.setPosition(r.position());
    return Mappers.lesson(lessons.save(l), true);
  }

  @Transactional
  public void deleteLesson(User u, Long id) {
    Lesson l = lesson(id);
    access.requireOwnerOrAdmin(u, l.getModule().getCourse());
    lessons.delete(l);
  }

  public LessonDto getLesson(User u, Long id) {
    Lesson l = lesson(id);
    access.requireEnrolledOrStaff(u, l.getModule().getCourse());
    return Mappers.lesson(l, true);
  }

  @Transactional
  public void enroll(User u, Long courseId) {
    if (u.getRole() != Role.STUDENT) throw ApiException.forbidden("Only students can enroll");
    Course c = course(courseId);
    if (enrollments.existsByStudentAndCourse(u, c)) return;
    Enrollment e = new Enrollment();
    e.setStudent(u);
    e.setCourse(c);
    enrollments.save(e);
  }

  private void apply(Lesson l, LessonRequest r) {
    l.setTitle(r.title());
    l.setDescription(r.description());
    String url = r.videoUrl() == null ? "" : r.videoUrl().trim();
    if (!url.isEmpty() && !url.matches("(?i)^https?://\\S+$")) throw ApiException.badRequest("The video link must start with http:// or https://");
    l.setVideoUrl(url.isEmpty() ? null : url);
    l.setContent(r.content());
  }
  public Course course(Long id) { return courses.findById(id).orElseThrow(() -> ApiException.notFound("Course")); }
  public Lesson lesson(Long id) { return lessons.findById(id).orElseThrow(() -> ApiException.notFound("Lesson")); }

  @Transactional
  public CourseDto updateCourse(User u, Long id, CourseRequest r) {
    Course c = course(id);
    access.requireOwnerOrAdmin(u, c);
    c.setTitle(r.title());
    c.setDescription(r.description());
    return Mappers.course(courses.save(c));
  }

  @Transactional
  public void deleteCourse(User u, Long id) {
    Course c = course(id);
    access.requireOwnerOrAdmin(u, c);
    courses.delete(c);
  }

  @Transactional
  public ModuleDto updateModule(User u, Long id, ModuleRequest r) {
    CourseModule m = modules.findById(id).orElseThrow(() -> ApiException.notFound("Module"));
    access.requireOwnerOrAdmin(u, m.getCourse());
    m.setTitle(r.title());
    if (r.position() != null) m.setPosition(r.position());
    modules.save(m);
    return new ModuleDto(m.getId(), m.getTitle(), m.getPosition(), List.of());
  }

  @Transactional
  public void deleteModule(User u, Long id) {
    CourseModule m = modules.findById(id).orElseThrow(() -> ApiException.notFound("Module"));
    access.requireOwnerOrAdmin(u, m.getCourse());
    modules.delete(m);
  }
}
