package com.learnai.service;

import com.learnai.common.*;
import com.learnai.dto.Dtos.*;
import com.learnai.model.*;
import com.learnai.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LeaderboardService {
  private final XpEventRepository xp;
  private final CourseRepository courses;
  private final EnrollmentRepository enrollments;
  private final Access access;

  public LeaderboardDto global(User me) {
    if (me.getRole() == Role.INSTRUCTOR) throw ApiException.forbidden("Instructors view course leaderboards");
    return build(xp.globalBoard(), me);
  }

  public LeaderboardDto course(User me, Long courseId) {
    Course c = courses.findById(courseId).orElseThrow(() -> ApiException.notFound("Course"));
    if (me.getRole() == Role.STUDENT && !enrollments.existsByStudentAndCourse(me, c)) throw ApiException.forbidden("Not enrolled");
    if (me.getRole() == Role.INSTRUCTOR) access.requireOwnerOrAdmin(me, c);
    return build(xp.courseBoard(courseId), me);
  }

  public int globalRank(User me) {
    List<Object[]> rows = xp.globalBoard();
    for (int i = 0; i < rows.size(); i++) if (((Number) rows.get(i)[0]).longValue() == me.getId()) return i + 1;
    return 0;
  }

  private LeaderboardDto build(List<Object[]> rows, User me) {
    Map<Long, Long> done = new HashMap<>();
    xp.completedCourses().forEach(r -> done.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue()));
    List<RankedRow> out = new ArrayList<>();
    RankedRow mine = null;
    for (int i = 0; i < rows.size(); i++) {
      Object[] r = rows.get(i);
      long sid = ((Number) r[0]).longValue();
      RankedRow row = new RankedRow(i + 1, sid, (String) r[1], ((Number) r[2]).longValue(), done.getOrDefault(sid, 0L));
      if (i < 50) out.add(row);
      if (row.studentId().equals(me.getId())) mine = row;
    }
    if (mine == null && me.getRole() == Role.STUDENT) mine = new RankedRow(0, me.getId(), me.getName(), 0, 0);
    return new LeaderboardDto(out, mine);
  }
}
