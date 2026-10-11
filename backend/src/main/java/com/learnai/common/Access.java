package com.learnai.common;

import com.learnai.model.*;
import com.learnai.repo.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class Access {
  private final EnrollmentRepository enrollments;

  public void requireEnrolledOrStaff(User u, Course c) {
    if (u.getRole() == Role.STUDENT && !enrollments.existsByStudentAndCourse(u, c))
      throw ApiException.forbidden("Enroll in this course first");
  }
  public boolean isOwnerOrAdmin(User u, Course c) {
    return u.getRole() == Role.ADMIN || (u.getRole() == Role.INSTRUCTOR && c.getInstructor().getId().equals(u.getId()));
  }
  public void requireOwnerOrAdmin(User u, Course c) {
    if (!isOwnerOrAdmin(u, c)) throw ApiException.forbidden("You do not own this course");
  }
}
