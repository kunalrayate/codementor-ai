package com.learnai.web;

import com.learnai.common.*;
import com.learnai.dto.Dtos.*;
import com.learnai.model.*;
import com.learnai.repo.*;
import com.learnai.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CourseController {
  private final CourseService courses;
  private final ProgressService progress;
  private final LeaderboardService leaderboard;
  private final InstructorService instructor;
  private final VideoService videos;

  @GetMapping("/courses") public List<CourseDto> list() { return courses.list(); }
  @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
  @PutMapping("/courses/{id}") public CourseDto updateCourse(@AuthenticationPrincipal User u, @PathVariable Long id, @Valid @RequestBody CourseRequest r) { return courses.updateCourse(u, id, r); }

  @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
  @DeleteMapping("/courses/{id}") public void deleteCourse(@AuthenticationPrincipal User u, @PathVariable Long id) { courses.deleteCourse(u, id); }

  @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
  @PutMapping("/modules/{id}") public ModuleDto updateModule(@AuthenticationPrincipal User u, @PathVariable Long id, @Valid @RequestBody ModuleRequest r) { return courses.updateModule(u, id, r); }

  @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
  @DeleteMapping("/modules/{id}") public void deleteModule(@AuthenticationPrincipal User u, @PathVariable Long id) { courses.deleteModule(u, id); }

  @GetMapping("/courses/{id}") public CourseDetail detail(@AuthenticationPrincipal User u, @PathVariable Long id) { return courses.detail(u, id); }

  @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
  @PostMapping("/courses") public CourseDto create(@AuthenticationPrincipal User u, @Valid @RequestBody CourseRequest r) { return courses.create(u, r); }

  @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
  @PostMapping("/courses/{id}/modules") public ModuleDto addModule(@AuthenticationPrincipal User u, @PathVariable Long id, @Valid @RequestBody ModuleRequest r) { return courses.addModule(u, id, r); }

  @PreAuthorize("hasRole('STUDENT')")
  @PostMapping("/courses/{id}/enroll") public void enroll(@AuthenticationPrincipal User u, @PathVariable Long id) { courses.enroll(u, id); }

  @PreAuthorize("hasRole('STUDENT')")
  @GetMapping("/courses/{id}/progress") public CourseProgress courseProgress(@AuthenticationPrincipal User u, @PathVariable Long id) { return progress.forCourse(u, id); }

  @GetMapping("/courses/{id}/leaderboard") public LeaderboardDto courseBoard(@AuthenticationPrincipal User u, @PathVariable Long id) { return leaderboard.course(u, id); }

  @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
  @GetMapping("/courses/{id}/analytics") public List<Map<String, Object>> analytics(@AuthenticationPrincipal User u, @PathVariable Long id) { return instructor.analytics(u, id); }

  @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
  @PostMapping("/modules/{id}/lessons") public LessonDto addLesson(@AuthenticationPrincipal User u, @PathVariable Long id, @Valid @RequestBody LessonRequest r) { return courses.addLesson(u, id, r); }

  /** Finds (once) and saves a YouTube video for a lesson that has none, so the player can start immediately. */
  @PostMapping("/lessons/{id}/video/resolve")
  public Map<String, Object> resolveVideo(@AuthenticationPrincipal User u, @PathVariable Long id) {
    courses.getLesson(u, id);                                   // access check
    Map<String, Object> out = new HashMap<>();
    if (!videos.enabled()) { out.put("message", "A video for this lesson is being prepared. Please try again later."); return out; }
    try {
      Optional<String> url = videos.resolve(courses.lesson(id));
      if (url.isPresent()) out.put("url", url.get()); else out.put("message", "No suitable video was found for this lesson.");
    } catch (ApiException e) {
      out.put("message", u.getRole() == Role.ADMIN ? e.getMessage() : "A video for this lesson is being prepared. Please try again later.");
    }
    return out;
  }

  @GetMapping("/lessons/{id}") public LessonDto lesson(@AuthenticationPrincipal User u, @PathVariable Long id) { return courses.getLesson(u, id); }

  @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
  @PutMapping("/lessons/{id}") public LessonDto update(@AuthenticationPrincipal User u, @PathVariable Long id, @Valid @RequestBody LessonRequest r) { return courses.updateLesson(u, id, r); }

  @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
  @DeleteMapping("/lessons/{id}") public void delete(@AuthenticationPrincipal User u, @PathVariable Long id) { courses.deleteLesson(u, id); }

  @PreAuthorize("hasRole('STUDENT')")
  @PutMapping("/lessons/{id}/progress") public LessonProgressDto saveProgress(@AuthenticationPrincipal User u, @PathVariable Long id, @RequestBody ProgressRequest r) { return progress.update(u, id, r); }

  @PreAuthorize("hasRole('STUDENT')")
  @GetMapping("/lessons/{id}/progress") public LessonProgressDto getProgress(@AuthenticationPrincipal User u, @PathVariable Long id) { return progress.forLesson(u, id); }
}
