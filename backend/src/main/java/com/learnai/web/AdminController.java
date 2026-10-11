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
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {
  private final UserRepository users;
  private final CourseRepository courses;
  private final QuizRepository quizzes;
  private final AiUsageRepository usage;
  private final XpEventRepository xp;
  private final BadgeRepository badges;
  private final XpRuleRepository xpRules;
  private final AiSettingsService aiSettings;
  private final AiGateway aiGateway;
  private final VideoService videos;

  @GetMapping("/stats")
  public Map<String, Object> stats() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("students", users.countByRole(Role.STUDENT));
    m.put("instructors", users.countByRole(Role.INSTRUCTOR));
    m.put("courses", courses.count());
    m.put("quizzes", quizzes.count());
    m.put("xpEvents", xp.count());
    m.put("aiRequestsToday", usage.requestsOn(java.time.LocalDate.now()));
    m.put("aiTotalTokens", usage.totalTokens());
    return m;
  }

  @GetMapping("/users")
  public List<Map<String, Object>> users() {
    return users.findAll().stream().map(u -> {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", u.getId()); m.put("name", u.getName()); m.put("email", u.getEmail());
      m.put("role", u.getRole()); m.put("enabled", u.isEnabled());
      return m;
    }).toList();
  }

  public record EnabledRequest(boolean enabled) {}
  @PutMapping("/users/{id}/enabled")
  public void setEnabled(@AuthenticationPrincipal User admin, @PathVariable Long id, @RequestBody EnabledRequest r) {
    User u = users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
    if (u.getId().equals(admin.getId())) throw ApiException.badRequest("You cannot disable yourself");
    u.setEnabled(r.enabled());
    users.save(u);
  }

  @GetMapping("/ai-usage")
  public List<Map<String, Object>> aiUsage(@RequestParam(defaultValue = "7") int days) {
    return usage.findByUsageDateGreaterThanEqualOrderByUsageDateDesc(java.time.LocalDate.now().minusDays(days)).stream().map(a -> {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("user", a.getUser().getName()); m.put("email", a.getUser().getEmail()); m.put("date", a.getUsageDate().toString());
      m.put("requests", a.getRequests()); m.put("inputTokens", a.getInputTokens());
      m.put("outputTokens", a.getOutputTokens()); m.put("totalTokens", a.getTotalTokens());
      return m;
    }).toList();
  }

  @GetMapping("/xp-events")
  public List<Map<String, Object>> xpEvents() {
    return xp.findTop50ByOrderByIdDesc().stream().map(e -> {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", e.getId()); m.put("student", e.getStudent().getName()); m.put("type", e.getEventType());
      m.put("xp", e.getXp()); m.put("createdAt", e.getCreatedAt().toString());
      return m;
    }).toList();
  }

  /** Remove suspicious / fraudulent XP. */
  @DeleteMapping("/xp-events/{id}") public void deleteXp(@PathVariable Long id) { xp.deleteById(id); }

  @GetMapping("/badges")
  public List<BadgeAdminDto> badges() {
    return badges.findAll().stream().map(this::badgeDto).toList();
  }

  @PostMapping("/badges")
  public BadgeAdminDto addBadge(@RequestBody BadgeForm f) {
    Badge n = new Badge();
    apply(n, f, null);
    return badgeDto(badges.save(n));
  }

  @PutMapping("/badges/{id}")
  public BadgeAdminDto updateBadge(@PathVariable Long id, @RequestBody BadgeForm f) {
    Badge n = badges.findById(id).orElseThrow(() -> ApiException.notFound("Badge"));
    apply(n, f, id);
    return badgeDto(badges.save(n));
  }

  /** Deleting a badge also removes it from students who earned it. */
  @DeleteMapping("/badges/{id}")
  public void deleteBadge(@PathVariable Long id) {
    if (!badges.existsById(id)) throw ApiException.notFound("Badge");
    badges.deleteById(id);
  }

  private void apply(Badge n, BadgeForm f, Long selfId) {
    String code = f.code() == null ? "" : f.code().trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_");
    if (code.length() < 2) throw ApiException.badRequest("Enter a badge code (letters and numbers, e.g. JAVA_STAR)");
    if (f.name() == null || f.name().isBlank()) throw ApiException.badRequest("Enter a badge name");
    badges.findByCode(code).ifPresent(x -> { if (selfId == null || !x.getId().equals(selfId)) throw ApiException.badRequest("A badge with code " + code + " already exists"); });
    if (f.minXp() != null && f.minXp() < 0) throw ApiException.badRequest("XP must be 0 or more");
    n.setCode(code);
    n.setName(f.name().trim());
    n.setDescription(f.description() == null ? "" : f.description().trim());
    n.setMinXp(f.minXp());
  }

  private BadgeAdminDto badgeDto(Badge b) { return new BadgeAdminDto(b.getId(), b.getCode(), b.getName(), b.getDescription(), b.getMinXp()); }

  /** Leaderboard rules: how much XP each activity is worth. */
  @GetMapping("/xp-rules")
  public List<XpRuleDto> xpRules() { return xpRules.findAll().stream().map(r -> new XpRuleDto(r.getCode(), r.getXp(), r.getDescription())).toList(); }

  @PutMapping("/xp-rules/{code}")
  public XpRuleDto setXpRule(@PathVariable String code, @RequestBody XpRuleDto body) {
    XpRule r = xpRules.findByCode(code).orElseThrow(() -> ApiException.notFound("Rule"));
    if (body.xp() < 0 || body.xp() > 10000) throw ApiException.badRequest("XP must be between 0 and 10000");
    r.setXp(body.xp());
    xpRules.save(r);
    return new XpRuleDto(r.getCode(), r.getXp(), r.getDescription());
  }

  @GetMapping("/ai-settings") public AiSettingsService.View aiSettings() { return aiSettings.view(); }

  @PutMapping("/ai-settings")
  public AiSettingsService.View saveAiSettings(@RequestBody AiSettingsService.Request r) {
    aiSettings.save(r);
    return aiSettings.view();
  }

  /** Sends one tiny request to the configured provider so the admin can verify the key right away. */
  @PostMapping("/ai-settings/test")
  public Map<String, Object> testAi(@AuthenticationPrincipal User admin) {
    try {
      String reply = aiGateway.ask(admin, "You are a connectivity test.", List.of(), "Reply with the single word OK.");
      return Map.of("ok", true, "message", "Connected. The model replied: " + Mappers.clip(reply.trim(), 80));
    } catch (ApiException e) {
      return Map.of("ok", false, "message", e.getMessage());
    }
  }

  /** Lists the chat models the saved key can use, so the admin can pick one that exists. */
  @GetMapping("/ai-settings/models")
  public Map<String, Object> aiModels() {
    try {
      return Map.of("ok", true, "models", aiSettings.listModels());
    } catch (Exception e) {
      return Map.of("ok", false, "message", "Could not load models. Save a valid API key first. (" + Mappers.clip(String.valueOf(e.getMessage()), 120) + ")");
    }
  }

  public record KeyForm(String apiKey) {}

  @GetMapping("/video-settings")
  public Map<String, Object> videoSettings() { return Map.of("configured", videos.enabled(), "source", videos.source()); }

  @PutMapping("/video-settings")
  public Map<String, Object> saveVideoKey(@RequestBody KeyForm f) {
    aiSettings.putSecret("yt.apiKey", f.apiKey());
    return videoSettings();
  }

  @PostMapping("/video-settings/test")
  public Map<String, Object> testVideo() {
    try {
      Optional<String> id = videos.search("Java tutorial for beginners");
      return Map.of("ok", id.isPresent(), "message", id.isPresent() ? "Connected - YouTube search works (test video " + id.get() + ")." : "Connected, but no results were returned.");
    } catch (ApiException e) {
      return Map.of("ok", false, "message", e.getMessage());
    }
  }

  /** Looks up a video for every lesson that has none (about 100 YouTube quota units each). */
  @PostMapping("/video-settings/fill-all")
  public Map<String, Object> fillVideos() { return videos.fillAll(); }
}
