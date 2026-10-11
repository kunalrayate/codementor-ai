package com.learnai.service;

import com.learnai.dto.Dtos.PlanStep;
import com.learnai.dto.Dtos.StudyPlanDto;
import com.learnai.model.*;
import com.learnai.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The study plan is computed from the student's own data (quiz results, coding results, course progress) and adapted to
 * the chosen explanation level - it never depends on an AI provider. AI coaching tips are an optional extra.
 */
@Service
@RequiredArgsConstructor
public class RecommendationService {
  private final QuizResultRepository results;
  private final CodeSubmissionRepository submissions;
  private final CodingProblemRepository problems;
  private final EnrollmentRepository enrollments;
  private final LessonRepository lessons;
  private final LessonProgressRepository progress;
  private final AiGateway ai;

  /** Plain SQL statistics (no AI): quiz average per module and coding results per topic. */
  public Map<String, Object> performance(User u) {
    List<Map<String, Object>> modules = new ArrayList<>();
    for (Object[] r : results.averageByModule(u)) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("name", r[0]);
      m.put("percent", Math.round(((Number) r[1]).doubleValue()));
      modules.add(m);
    }
    List<Map<String, Object>> code = new ArrayList<>();
    for (Object[] r : submissions.topicStats(u)) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("topic", r[0] == null ? "General" : r[0]);
      m.put("attempts", ((Number) r[1]).longValue());
      m.put("passed", r[2] == null ? 0L : ((Number) r[2]).longValue());
      code.add(m);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("modules", modules);
    out.put("codeTopics", code);
    return out;
  }

  public StudyPlanDto plan(User u) {
    Level level = u.getLevel() == null ? Level.BEGINNER : u.getLevel();
    List<String> strengths = new ArrayList<>();
    List<String> focus = new ArrayList<>();
    List<PlanStep> steps = new ArrayList<>();

    List<Object[]> byLesson = results.averageByLesson(u);
    long weakId = -1, weakPct = 101;
    String weakTitle = null;
    double sum = 0;
    for (Object[] r : byLesson) {
      long pct = Math.round(((Number) r[2]).doubleValue());
      String title = String.valueOf(r[1]);
      sum += pct;
      if (pct >= 80) strengths.add("Quiz '" + title + "': " + pct + "%");
      else if (pct < 60) {
        focus.add("Quiz '" + title + "': " + pct + "% - review the lesson and retake it");
        if (pct < weakPct) { weakPct = pct; weakId = ((Number) r[0]).longValue(); weakTitle = title; }
      }
    }
    Set<Long> solved = new HashSet<>(submissions.solvedIds(u));
    boolean anyCode = false;
    for (Object[] r : submissions.topicStats(u)) {
      anyCode = true;
      String topic = r[0] == null ? "General" : r[0].toString();
      long attempts = ((Number) r[1]).longValue();
      long passed = r[2] == null ? 0 : ((Number) r[2]).longValue();
      if (passed > 0) strengths.add("Coding - " + topic + ": " + passed + " passing submission(s)");
      else if (attempts > 0) focus.add("Coding - " + topic + ": " + attempts + " attempt(s), none passing yet");
    }

    // the next unfinished lesson in the student's courses
    Lesson next = null;
    String nextCourse = "";
    for (Enrollment e : enrollments.findByStudent(u)) {
      Long cid = e.getCourse().getId();
      Set<Long> done = progress.findByStudentAndLessonModuleCourseId(u, cid).stream().filter(LessonProgress::isCompleted)
          .map(p -> p.getLesson().getId()).collect(Collectors.toSet());
      List<Lesson> ls = new ArrayList<>(lessons.findByModuleCourseId(cid));
      ls.sort(Comparator.comparingInt((Lesson l) -> l.getModule().getPosition()).thenComparingInt(Lesson::getPosition));
      for (Lesson l : ls) if (!done.contains(l.getId())) { next = l; nextCourse = e.getCourse().getTitle(); break; }
      if (next != null) break;
    }

    if (weakTitle != null) {
      steps.add(new PlanStep("Re-read '" + weakTitle + "', then retake its quiz (now " + weakPct + "%).", "/lessons/" + weakId));
      steps.add(new PlanStep("Review the flashcards for '" + weakTitle + "' to lock in the key ideas.", "/lessons/" + weakId));
    }
    if (next != null) steps.add(new PlanStep("Continue your course: '" + next.getTitle() + "' (" + nextCourse + ").", "/lessons/" + next.getId()));
    String want = level == Level.BEGINNER ? "Easy" : level == Level.INTERMEDIATE ? "Medium" : "Hard";
    List<CodingProblem> open = problems.findAll().stream().filter(p -> !solved.contains(p.getId())).toList();
    Optional<CodingProblem> pick = open.stream().filter(p -> want.equalsIgnoreCase(p.getDifficulty())).findFirst();
    if (pick.isEmpty()) pick = open.stream().findFirst();
    if (pick.isPresent()) {
      CodingProblem p = pick.get();
      steps.add(new PlanStep("Practise coding: '" + p.getTitle() + "' (" + p.getTopic() + ", " + p.getDifficulty() + ").", "/code"));
    }
    if (steps.isEmpty()) steps.add(new PlanStep("Great work! Explore another course or ask your instructor for harder problems.", "/courses"));

    boolean noData = byLesson.isEmpty() && !anyCode;
    String headline = noData ? "Let's get started - complete a lesson, take a quiz or solve a coding problem to personalise this plan."
        : switch (level) {
            case BEGINNER -> "Small steps win. Here is an easy, friendly plan for you.";
            case INTERMEDIATE -> "You are building momentum. Here is what to focus on next.";
            case ADVANCED -> "Time to push further. Here is a focused plan.";
          };
    String stats = "Quiz average: " + (byLesson.isEmpty() ? "-" : Math.round(sum / byLesson.size()) + "%")
        + "   |   Lessons completed: " + progress.countByStudentAndCompletedTrue(u)
        + "   |   Coding problems solved: " + solved.size();
    return new StudyPlanDto(level.name(), headline, stats, strengths, focus, steps);
  }

  /** Optional: a few motivating tips written by the AI (can fail when the provider is busy). */
  public String aiCoach(User u) {
    StudyPlanDto p = plan(u);
    return ai.ask(u, Prompts.RECOMMENDER, List.of(), "Student explanation level: " + p.level() + "\n" + p.stats()
        + "\nStrengths: " + p.strengths() + "\nNeeds work: " + p.focus() + "\nWrite 3 short, motivating coaching tips.");
  }
}
