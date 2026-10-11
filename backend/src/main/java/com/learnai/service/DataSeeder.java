package com.learnai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnai.model.*;
import com.learnai.repo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Idempotent seeding on every start: demo users, badges, XP rules, a sample Java course (9 lessons with
 * published quizzes and flashcards, so the app works even without an AI key) and 18 coding problems by topic.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {
  private final UserRepository users;
  private final BadgeRepository badges;
  private final XpRuleRepository rules;
  private final CourseRepository courses;
  private final ModuleRepository modules;
  private final LessonRepository lessons;
  private final QuizRepository quizzes;
  private final QuestionRepository questions;
  private final FlashcardRepository flashcards;
  private final CodingProblemRepository problems;
  private final TestCaseRepository tests;
  private final PasswordEncoder encoder;
  private final ObjectMapper mapper;
  @Value("${app.seed.admin-email}") private String adminEmail;
  @Value("${app.seed.admin-password}") private String adminPassword;
  @Value("${app.seed.reset-demo-passwords}") private boolean resetDemoPasswords;

  @Override
  public void run(String... args) throws Exception {
    seedBadges();
    seedRules();
    demoUser("Admin", adminEmail, adminPassword, Role.ADMIN);
    User instructor = demoUser("Ivy Instructor", "instructor@learnai.com", "Instructor@123", Role.INSTRUCTOR);
    demoUser("Sam Student", "student@learnai.com", "Student@123", Role.STUDENT);
    seedCurriculum(instructor);
    seedProblems(instructor);
  }

  private User demoUser(String name, String email, String password, Role role) {
    User u = users.findByEmail(email).orElse(null);
    if (u == null) {
      u = new User();
      u.setName(name);
      u.setEmail(email);
      u.setRole(role);
      u.setPasswordHash(encoder.encode(password));
      return users.save(u);
    }
    if (resetDemoPasswords) { // keeps the documented demo logins working; set SEED_RESET_DEMO_PASSWORDS=false in production
      u.setPasswordHash(encoder.encode(password));
      u.setEnabled(true);
      users.save(u);
    }
    return u;
  }

  private void seedBadges() {
    badge("FIRST_LESSON", "Getting Started", "Completed your first lesson");
    badge("FAST_LEARNER", "Fast Learner", "Completed 10 lessons");
    badge("COURSE_MASTER", "Course Master", "Completed a whole course");
    badge("CONSISTENT_LEARNER", "Consistent Learner", "7-day learning streak");
  }
  private void badge(String code, String name, String desc) {
    if (badges.findByCode(code).isPresent()) return;
    Badge b = new Badge(); b.setCode(code); b.setName(name); b.setDescription(desc); badges.save(b);
  }

  private void seedRules() {
    rule("LESSON_COMPLETED", 50, "Completing a lesson");
    rule("QUIZ_PASSED", 100, "Passing a quiz (first time)");
    rule("COURSE_COMPLETED", 500, "Completing a whole course");
    rule("PROBLEM_SOLVED", 100, "Solving a coding problem (first time)");
    rule("STREAK_BONUS", 100, "7-day learning streak bonus");
  }
  private void rule(String code, int xp, String desc) {
    if (rules.findByCode(code).isPresent()) return;
    XpRule r = new XpRule(); r.setCode(code); r.setXp(xp); r.setDescription(desc); rules.save(r);
  }

  private void seedCurriculum(User instructor) throws Exception {
    seedCourse(mapper.readTree(new ClassPathResource("seed/curriculum.json").getInputStream()), instructor);
    JsonNode more = mapper.readTree(new ClassPathResource("seed/curriculum_more.json").getInputStream());
    for (JsonNode c : more.get("courses")) seedCourse(c, instructor);
  }

  private void seedCourse(JsonNode root, User instructor) {
    String courseTitle = root.get("course").get("title").asText();
    Course course = courses.findFirstByTitle(courseTitle).orElse(null);
    if (course == null) {
      course = new Course();
      course.setTitle(courseTitle);
      course.setDescription(root.get("course").get("description").asText());
      course.setInstructor(instructor);
      courses.save(course);
    }
    for (JsonNode m : root.get("modules")) {
      String mTitle = m.get("title").asText();
      CourseModule mod = modules.findFirstByCourseAndTitle(course, mTitle).orElse(null);
      if (mod == null) {
        mod = new CourseModule();
        mod.setCourse(course);
        mod.setTitle(mTitle);
        mod.setPosition(m.get("position").asInt());
        modules.save(mod);
      }
      int pos = 0;
      for (JsonNode l : m.get("lessons")) {
        pos++;
        String lTitle = l.get("title").asText();
        Lesson lesson = lessons.findFirstByModuleAndTitle(mod, lTitle).orElse(null);
        if (lesson == null) {
          lesson = new Lesson();
          lesson.setModule(mod);
          lesson.setTitle(lTitle);
          lesson.setPosition(pos);
          lesson.setDescription(lTitle + " in Java");
        }
        if (blank(lesson.getContent())) lesson.setContent(l.get("content").asText());
        if (blank(lesson.getVideoUrl()) && l.hasNonNull("video")) lesson.setVideoUrl(l.get("video").asText());
        lessons.save(lesson);
        seedFlashcards(lesson, l.get("cards"));
        seedQuiz(lesson, l.get("quiz"), instructor);
      }
    }
  }

  private void seedFlashcards(Lesson lesson, JsonNode cards) {
    if (!flashcards.findByLesson(lesson).isEmpty()) return;
    for (JsonNode c : cards) {
      Flashcard f = new Flashcard();
      f.setLesson(lesson);
      f.setQuestion(c.get(0).asText());
      f.setAnswer(c.get(1).asText());
      flashcards.save(f);
    }
  }

  private void seedQuiz(Lesson lesson, JsonNode items, User instructor) {
    java.util.List<Quiz> existing = quizzes.findByLesson(lesson);
    Quiz q;
    if (existing.isEmpty()) {
      q = new Quiz();
      q.setLesson(lesson);
      q.setTitle("Quiz: " + lesson.getTitle());
      q.setStatus(QuizStatus.PUBLISHED);
      q.setAiGenerated(false);
      q.setCreatedBy(instructor);
      quizzes.save(q);
    } else {
      q = existing.get(0);
      // repair: a quiz row without questions (left by an interrupted earlier start) gets its questions now
      if (q.isAiGenerated() || !questions.findByQuiz(q).isEmpty()) return;
    }
    for (JsonNode it : items) {
      Question qu = new Question();
      qu.setQuiz(q);
      qu.setQuestionText(it.get("q").asText());
      qu.setOptionA(it.get("o").get(0).asText());
      qu.setOptionB(it.get("o").get(1).asText());
      qu.setOptionC(it.get("o").get(2).asText());
      qu.setOptionD(it.get("o").get(3).asText());
      qu.setCorrect(it.get("c").asText());
      qu.setExplanation(it.get("e").asText());
      questions.save(qu);
    }
  }

  private void seedProblems(User instructor) throws Exception {
    JsonNode root = mapper.readTree(new ClassPathResource("seed/problems.json").getInputStream());
    String defaultStarter = root.get("defaultStarter").asText();
    for (JsonNode n : root.get("problems")) {
      String title = n.get("title").asText();
      CodingProblem p = problems.findFirstByTitle(title).orElse(null);
      if (p != null) { // enrich problems created by an earlier version
        if (blank(p.getTopic())) { p.setTopic(n.get("topic").asText()); p.setDifficulty(n.get("difficulty").asText()); problems.save(p); }
        continue;
      }
      p = new CodingProblem();
      p.setTitle(title);
      p.setTopic(n.get("topic").asText());
      p.setDifficulty(n.get("difficulty").asText());
      p.setDescription(n.get("description").asText());
      p.setStarterCode(n.hasNonNull("starter") ? n.get("starter").asText() : defaultStarter);
      p.setCreatedBy(instructor);
      problems.save(p);
      for (JsonNode t : n.get("tests")) {
        TestCase tc = new TestCase();
        tc.setProblem(p);
        tc.setInputData(t.get(0).asText());
        tc.setExpectedOutput(t.get(1).asText());
        tests.save(tc);
      }
    }
  }

  private static boolean blank(String s) { return s == null || s.isBlank(); }
}
