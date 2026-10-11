package com.learnai.common;

import com.learnai.dto.Dtos.*;
import com.learnai.model.*;

public final class Mappers {
  private Mappers() {}
  public static UserInfo info(User u) { return new UserInfo(u.getId(), u.getName(), u.getEmail(), u.getRole(), u.getLevel()); }
  public static CourseDto course(Course c) { return new CourseDto(c.getId(), c.getTitle(), c.getDescription(), c.getInstructor().getName(), c.getInstructor().getId()); }
  public static LessonDto lesson(Lesson l, boolean full) {
    return new LessonDto(l.getId(), l.getTitle(), l.getDescription(), full ? l.getVideoUrl() : null, full ? l.getContent() : null,
        l.getPosition(), l.getModule().getId(), l.getModule().getCourse().getId());
  }
  public static String clip(String s, int max) { return s == null ? "" : (s.length() <= max ? s : s.substring(0, max)); }
}
