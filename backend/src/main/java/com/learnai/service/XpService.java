package com.learnai.service;

import com.learnai.model.*;
import com.learnai.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

/** XP event ledger + badge engine. XP amounts come from the admin-editable table xp_rules. */
@Service
@RequiredArgsConstructor
public class XpService {
  private final XpEventRepository events;
  private final BadgeRepository badges;
  private final UserBadgeRepository userBadges;
  private final XpRuleRepository rules;

  /** Current XP value of a rule (admin can change it); falls back to a default if the rule row is missing. */
  public int rule(String code, int fallback) {
    return rules.findByCode(code).map(XpRule::getXp).orElse(fallback);
  }

  /** @param once when true, the same (type, refId) pair only pays out once per student */
  @Transactional
  public int award(User student, String type, int xp, Long refId, Long courseId, boolean once) {
    if (student.getRole() != Role.STUDENT) return 0;
    if (once && refId != null && events.existsByStudentAndEventTypeAndRefId(student, type, refId)) return 0;
    XpEvent e = new XpEvent();
    e.setStudent(student);
    e.setEventType(type);
    e.setXp(xp);
    e.setRefId(refId);
    e.setCourseId(courseId);
    events.save(e);
    long total = events.totalXp(student);
    for (Badge bd : badges.findByMinXpNotNull()) if (bd.getMinXp() != null && total >= bd.getMinXp()) badge(student, bd.getCode());
    if (!type.equals("STREAK_BONUS") && streak(student) >= 7 && !events.existsByStudentAndEventType(student, "STREAK_BONUS")) {
      award(student, "STREAK_BONUS", rule("STREAK_BONUS", 100), null, null, false);
      badge(student, "CONSISTENT_LEARNER");
    }
    return xp;
  }

  @Transactional
  public void badge(User u, String code) {
    badges.findByCode(code).ifPresent(b -> {
      if (!userBadges.existsByUserAndBadge(u, b)) {
        UserBadge ub = new UserBadge();
        ub.setUser(u);
        ub.setBadge(b);
        userBadges.save(ub);
      }
    });
  }

  /** Consecutive active days ending today (or yesterday). */
  public int streak(User u) {
    Set<LocalDate> days = events.activeDays(u.getId(), LocalDate.now().minusDays(30).atStartOfDay()).stream()
        .map(o -> LocalDate.parse(o.toString())).collect(Collectors.toSet());
    LocalDate d = days.contains(LocalDate.now()) ? LocalDate.now() : LocalDate.now().minusDays(1);
    int n = 0;
    while (days.contains(d)) { n++; d = d.minusDays(1); }
    return n;
  }
}
