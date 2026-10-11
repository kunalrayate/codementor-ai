package com.learnai.web;

import com.learnai.common.*;
import com.learnai.dto.Dtos.*;
import com.learnai.model.*;
import com.learnai.repo.*;
import com.learnai.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {
  private final XpEventRepository xp;
  private final UserBadgeRepository userBadges;
  private final BadgeRepository badges;
  private final UserRepository users;
  private final XpService xpService;
  private final LeaderboardService leaderboard;
  private final UsageService usage;
  private final CourseService courses;
  private final ProgressService progress;
  private final RecommendationService recommendations;

  @GetMapping
  public MeDto me(@AuthenticationPrincipal User u) {
    List<BadgeDto> mine = userBadges.findByUser(u).stream()
        .map(b -> new BadgeDto(b.getBadge().getCode(), b.getBadge().getName(), b.getBadge().getDescription())).toList();
    boolean student = u.getRole() == Role.STUDENT;
    return new MeDto(Mappers.info(u), student ? xp.totalXp(u) : 0, student ? leaderboard.globalRank(u) : 0,
        mine, student ? xpService.streak(u) : 0, usage.status(u));
  }

  /** All badges with an "earned" flag (earned and locked badges are both shown on the dashboard). */
  @GetMapping("/badges")
  public List<Map<String, Object>> allBadges(@AuthenticationPrincipal User u) {
    Set<String> earned = new HashSet<>();
    userBadges.findByUser(u).forEach(b -> earned.add(b.getBadge().getCode()));
    return badges.findAll().stream().map(b -> {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("code", b.getCode()); m.put("name", b.getName()); m.put("description", b.getDescription());
      m.put("earned", earned.contains(b.getCode()));
      return m;
    }).toList();
  }

  @PutMapping("/level")
  public UserInfo level(@AuthenticationPrincipal User u, @RequestBody LevelRequest r) {
    if (r.level() == null) throw ApiException.badRequest("level required");
    u.setLevel(r.level());
    return Mappers.info(users.save(u));
  }

  @GetMapping("/courses") public List<CourseDto> myCourses(@AuthenticationPrincipal User u) { return courses.mine(u); }

  /** Learning progress for every course the student is enrolled in. */
  @GetMapping("/progress")
  public List<Map<String, Object>> myProgress(@AuthenticationPrincipal User u) {
    List<Map<String, Object>> out = new ArrayList<>();
    if (u.getRole() != Role.STUDENT) return out;
    for (CourseDto c : courses.mine(u)) {
      CourseProgress p = progress.forCourse(u, c.id());
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("courseId", c.id()); m.put("title", c.title());
      m.put("completed", p.completed()); m.put("total", p.total()); m.put("percent", p.percent());
      out.add(m);
    }
    return out;
  }

  @GetMapping("/performance") public Map<String, Object> performance(@AuthenticationPrincipal User u) { return recommendations.performance(u); }

  @GetMapping("/xp-history")
  public List<Map<String, Object>> xpHistory(@AuthenticationPrincipal User u) {
    return xp.findTop10ByStudentOrderByIdDesc(u).stream().map(e -> {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("type", e.getEventType()); m.put("xp", e.getXp()); m.put("createdAt", e.getCreatedAt().toString());
      return m;
    }).toList();
  }

  @GetMapping("/recommendations") public StudyPlanDto plan(@AuthenticationPrincipal User u) { return recommendations.plan(u); }
  @GetMapping("/recommendations/ai") public TextResponse coach(@AuthenticationPrincipal User u) { return new TextResponse(recommendations.aiCoach(u)); }
  @GetMapping("/ai-usage") public UsageStatus aiUsage(@AuthenticationPrincipal User u) { return usage.status(u); }
}
