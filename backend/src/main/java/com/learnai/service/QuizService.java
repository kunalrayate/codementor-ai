package com.learnai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnai.common.*;
import com.learnai.dto.Dtos.*;
import com.learnai.model.*;
import com.learnai.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class QuizService {
  private final QuizRepository quizzes;
  private final QuestionRepository questions;
  private final QuizResultRepository results;
  private final LessonRepository lessons;
  private final AiGateway ai;
  private final XpService xp;
  private final Access access;
  private final FlashcardRepository flashcardRepo;
  private final QuizAttemptRepository attempts;
  private final ObjectMapper mapper;
  private final CourseRepository courseRepo;
  private final EnrollmentRepository enrollmentRepo;
  @Value("${app.quiz.questions-per-attempt:5}") private int perAttempt;
  @Value("${app.quiz.pass-percent}") private int passPercent;

  public record Item(String question, List<String> options, String correct, String explanation) {}

  /** Generates a DRAFT quiz with AI. An existing AI quiz for the lesson is reused unless force=true (cost saving). */
  public QuizDto generate(User u, QuizGenRequest r) {
    Lesson l = lessons.findById(r.lessonId()).orElseThrow(() -> ApiException.notFound("Lesson"));
    access.requireOwnerOrAdmin(u, l.getModule().getCourse());
    if (!r.force()) {
      Optional<Quiz> existing = quizzes.findByLesson(l).stream()
          .filter(q -> q.isAiGenerated() && q.getStatus() != QuizStatus.ARCHIVED).findFirst();
      if (existing.isPresent()) return toDto(existing.get(), true, true);
    }
    int n = r.numQuestions() == null ? 5 : Math.max(1, Math.min(10, r.numQuestions()));
    String prompt = """
        Create %d multiple-choice questions from the lesson below.
        Return ONLY a JSON array (no prose, no markdown). Each element must be:
        {"question": "...", "options": ["...", "...", "...", "..."], "correct": "A|B|C|D", "explanation": "..."}
        Rules: exactly 4 options without letter prefixes, exactly one correct answer, test understanding not trivia.

        LESSON TITLE: %s
        LESSON CONTENT:
        %s
        """.formatted(n, l.getTitle(), Mappers.clip(l.getContent() != null ? l.getContent() : l.getDescription(), 6000));
    String raw = ai.ask(u, Prompts.GENERATOR, List.of(), prompt);
    List<Item> items = JsonUtil.parseArray(raw, new TypeReference<List<Item>>() {});
    Quiz q = new Quiz();
    q.setLesson(l);
    q.setTitle("Quiz: " + l.getTitle());
    q.setAiGenerated(true);
    q.setStatus(QuizStatus.DRAFT);
    q.setCreatedBy(u);
    quizzes.save(q);
    int saved = 0;
    for (Item it : items) {
      if (it.options() == null || it.options().size() != 4 || it.correct() == null || !it.correct().matches("[A-Da-d]")) continue;
      save(q, new QuestionEdit(it.question(), it.options(), it.correct(), it.explanation()), new Question());
      saved++;
    }
    if (saved == 0) throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "AI produced no valid questions");
    return toDto(q, true, true);
  }

  @Transactional
  public QuizDto createManual(User u, Long lessonId, String title, List<QuestionEdit> qs) {
    Lesson l = lessons.findById(lessonId).orElseThrow(() -> ApiException.notFound("Lesson"));
    access.requireOwnerOrAdmin(u, l.getModule().getCourse());
    Quiz q = new Quiz();
    q.setLesson(l);
    q.setTitle(title == null || title.isBlank() ? "Quiz: " + l.getTitle() : title);
    q.setCreatedBy(u);
    quizzes.save(q);
    for (QuestionEdit e : qs) save(q, e, new Question());
    return toDto(q, true, true);
  }

  public List<QuizDto> forLesson(User u, Long lessonId) {
    Lesson l = lessons.findById(lessonId).orElseThrow(() -> ApiException.notFound("Lesson"));
    access.requireEnrolledOrStaff(u, l.getModule().getCourse());
    List<Quiz> list = u.getRole() == Role.STUDENT ? quizzes.findByLessonAndStatus(l, QuizStatus.PUBLISHED) : quizzes.findByLesson(l);
    return list.stream().map(q -> toDto(q, false, false, u)).toList();
  }

  public QuizDto get(User u, Long id) {
    Quiz q = quiz(id);
    boolean staff = u.getRole() != Role.STUDENT;
    if (!staff && q.getStatus() != QuizStatus.PUBLISHED) throw ApiException.notFound("Quiz");
    access.requireEnrolledOrStaff(u, q.getLesson().getModule().getCourse());
    return staff ? toDto(q, true, true) : toDto(q, false, false, u);   // students get the quiz status; questions come from /start
  }

  @Transactional
  public QuizDto setStatus(User u, Long id, QuizStatus status) {
    Quiz q = quiz(id);
    access.requireOwnerOrAdmin(u, q.getLesson().getModule().getCourse());
    q.setStatus(status);
    return toDto(quizzes.save(q), true, true);
  }

  @Transactional
  public QuestionDto editQuestion(User u, Long id, QuestionEdit e) {
    Question qu = questions.findById(id).orElseThrow(() -> ApiException.notFound("Question"));
    access.requireOwnerOrAdmin(u, qu.getQuiz().getLesson().getModule().getCourse());
    return dto(save(qu.getQuiz(), e, qu), true);
  }

  @Transactional
  public void deleteQuestion(User u, Long id) {
    Question qu = questions.findById(id).orElseThrow(() -> ApiException.notFound("Question"));
    access.requireOwnerOrAdmin(u, qu.getQuiz().getLesson().getModule().getCourse());
    questions.delete(qu);
  }

  /** A served question: text, options in the order shown, the correct option text and an explanation. */
  public record Snap(String t, List<String> o, String c, String e) {}

  /**
   * Starts a new attempt. Every attempt draws a fresh random set from the lesson's question bank - the quiz's own
   * questions plus questions built from the lesson's flashcards - and shuffles the answer options.
   */
  @Transactional
  public AttemptDto start(User u, Long quizId) {
    Quiz qz = quiz(quizId);
    if (qz.getStatus() != QuizStatus.PUBLISHED) throw ApiException.notFound("Quiz");
    access.requireEnrolledOrStaff(u, qz.getLesson().getModule().getCourse());
    Integer limit = limit(qz);
    if (u.getRole() == Role.STUDENT && limit != null) {
      int used = results.findByStudentAndQuizOrderByIdAsc(u, qz).size();
      if (used >= limit) throw ApiException.forbidden("This quiz is locked: you have already completed it (attempt " + used + "/" + limit + ").");
      Optional<QuizAttempt> open = attempts.findFirstByStudentAndQuizAndSubmittedFalseOrderByIdDesc(u, qz);
      if (open.isPresent()) { // resume the unfinished attempt - a refresh must not re-roll the questions
        List<Snap> same = fromJson(open.get().getSnapshot());
        List<AttemptQuestion> again = new ArrayList<>();
        for (int i = 0; i < same.size(); i++) again.add(new AttemptQuestion(i, same.get(i).t(), same.get(i).o()));
        return new AttemptDto(open.get().getId(), qz.getTitle(), qz.getLesson().getId(), again);
      }
    }
    List<Snap> bank = bank(qz);
    if (bank.isEmpty()) throw ApiException.badRequest("This quiz has no questions yet");
    Collections.shuffle(bank);
    List<Snap> served = new ArrayList<>();
    for (Snap s : bank.subList(0, Math.min(Math.max(perAttempt, 1), bank.size()))) {
      List<String> opts = new ArrayList<>(s.o());
      Collections.shuffle(opts);
      served.add(new Snap(s.t(), opts, s.c(), s.e()));
    }
    QuizAttempt a = new QuizAttempt();
    a.setStudent(u);
    a.setQuiz(qz);
    a.setSnapshot(toJson(served));
    attempts.save(a);
    List<AttemptQuestion> out = new ArrayList<>();
    for (int i = 0; i < served.size(); i++) out.add(new AttemptQuestion(i, served.get(i).t(), served.get(i).o()));
    return new AttemptDto(a.getId(), qz.getTitle(), qz.getLesson().getId(), out);
  }

  @Transactional
  public QuizResultDto submit(User u, Long quizId, SubmitRequest r) {
    Quiz qz = quiz(quizId);
    if (qz.getStatus() != QuizStatus.PUBLISHED) throw ApiException.notFound("Quiz");
    Course c = qz.getLesson().getModule().getCourse();
    access.requireEnrolledOrStaff(u, c);
    if (r.attemptId() == null) throw ApiException.badRequest("This page is out of date. Press Ctrl+F5 to reload it. If you still see this message, start the frontend from the same (latest) app folder as the backend - run start-all.bat.");
    QuizAttempt a = attempts.findById(r.attemptId())
        .filter(x -> x.getStudent().getId().equals(u.getId()) && x.getQuiz().getId().equals(quizId))
        .orElseThrow(() -> ApiException.notFound("Quiz attempt"));
    if (a.isSubmitted()) throw ApiException.badRequest("This attempt was already submitted. Start a new attempt.");
    Integer limit = limit(qz);
    if (limit != null) {
      int used = results.findByStudentAndQuizOrderByIdAsc(u, qz).size();
      if (used >= limit) throw ApiException.forbidden("This quiz is locked: attempt " + used + "/" + limit + " was already used.");
    }
    List<Snap> served = fromJson(a.getSnapshot());
    Map<Integer, String> answers = r.answers() == null ? Map.of() : r.answers();
    int score = 0;
    List<QuestionResult> details = new ArrayList<>();
    for (int i = 0; i < served.size(); i++) {
      Snap s = served.get(i);
      String yours = answers.get(i);
      boolean ok = yours != null && yours.trim().equals(s.c().trim());
      if (ok) score++;
      details.add(new QuestionResult((long) i, ok, yours, s.c(), s.e()));
    }
    int percent = Math.round(score * 100f / Math.max(1, served.size()));
    boolean passed = percent >= passPercent;
    a.setSubmitted(true);
    attempts.save(a);
    QuizResult res = new QuizResult();
    res.setStudent(u);
    res.setQuiz(qz);
    res.setScore(score);
    res.setTotal(served.size());
    res.setPercent(percent);
    res.setPassed(passed);
    results.save(res);
    int awarded = passed ? xp.award(u, "QUIZ_PASSED", xp.rule("QUIZ_PASSED", 100), qz.getId(), c.getId(), true) : 0;
    return new QuizResultDto(score, served.size(), percent, passed, awarded, details);
  }

  private List<Snap> bank(Quiz qz) {
    List<Snap> out = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (Question x : questions.findByQuiz(qz)) {
      String correct = switch (x.getCorrect() == null ? "A" : x.getCorrect().toUpperCase()) {
        case "B" -> x.getOptionB(); case "C" -> x.getOptionC(); case "D" -> x.getOptionD(); default -> x.getOptionA(); };
      if (seen.add(x.getQuestionText())) out.add(new Snap(x.getQuestionText(), List.of(x.getOptionA(), x.getOptionB(), x.getOptionC(), x.getOptionD()), correct, x.getExplanation()));
    }
    // extra questions built from the lesson's flashcards; wrong options come from the other cards of the same topic
    Lesson l = qz.getLesson();
    List<Flashcard> own = flashcardRepo.findByLesson(l);
    List<Flashcard> pool = new ArrayList<>();
    for (Lesson ls : lessons.findByModuleOrderByPositionAsc(l.getModule())) pool.addAll(flashcardRepo.findByLesson(ls));
    if (pool.size() < 8) {
      pool.clear();
      for (Lesson ls : lessons.findByModuleCourseId(l.getModule().getCourse().getId())) pool.addAll(flashcardRepo.findByLesson(ls));
    }
    for (Flashcard f : own) {
      List<String> wrongAnswers = new ArrayList<>();
      List<String> wrongQuestions = new ArrayList<>();
      for (Flashcard o : pool) {
        if (o.getId().equals(f.getId())) continue;
        if (!o.getAnswer().equalsIgnoreCase(f.getAnswer()) && !wrongAnswers.contains(o.getAnswer())) wrongAnswers.add(o.getAnswer());
        if (!o.getQuestion().equalsIgnoreCase(f.getQuestion()) && !wrongQuestions.contains(o.getQuestion())) wrongQuestions.add(o.getQuestion());
      }
      Collections.shuffle(wrongAnswers);
      Collections.shuffle(wrongQuestions);
      String why = "From the lesson \"" + l.getTitle() + "\": " + Mappers.clip(f.getAnswer(), 160);
      if (wrongAnswers.size() >= 3 && seen.add(f.getQuestion()))
        out.add(new Snap(f.getQuestion(), options(f.getAnswer(), wrongAnswers), f.getAnswer(), why));
      String reverse = "Which of these is described by: \"" + Mappers.clip(f.getAnswer(), 140) + "\"?";
      if (wrongQuestions.size() >= 3 && seen.add(reverse))
        out.add(new Snap(reverse, options(f.getQuestion(), wrongQuestions), f.getQuestion(), why));
    }
    return out;
  }

  private static List<String> options(String correct, List<String> wrong) {
    List<String> o = new ArrayList<>(wrong.subList(0, 3));
    o.add(correct);
    return o;
  }

  private String toJson(List<Snap> list) {
    try { return mapper.writeValueAsString(list); } catch (Exception e) { throw new IllegalStateException(e); }
  }

  private List<Snap> fromJson(String json) {
    try { return mapper.readValue(json, new TypeReference<List<Snap>>() {}); } catch (Exception e) { throw new IllegalStateException(e); }
  }

  private Question save(Quiz q, QuestionEdit e, Question target) {
    if (e.options() == null || e.options().size() != 4) throw ApiException.badRequest("Exactly 4 options required");
    if (e.correct() == null || !e.correct().matches("[A-Da-d]")) throw ApiException.badRequest("correct must be A-D");
    target.setQuiz(q);
    target.setQuestionText(e.text());
    target.setOptionA(e.options().get(0));
    target.setOptionB(e.options().get(1));
    target.setOptionC(e.options().get(2));
    target.setOptionD(e.options().get(3));
    target.setCorrect(e.correct().toUpperCase());
    target.setExplanation(e.explanation());
    return questions.save(target);
  }

  private Quiz quiz(Long id) { return quizzes.findById(id).orElseThrow(() -> ApiException.notFound("Quiz")); }

  private QuestionDto dto(Question q, boolean reveal) {
    return new QuestionDto(q.getId(), q.getQuestionText(), List.of(q.getOptionA(), q.getOptionB(), q.getOptionC(), q.getOptionD()),
        reveal ? q.getCorrect() : null, reveal ? q.getExplanation() : null);
  }

  private QuizDto toDto(Quiz q, boolean withQuestions, boolean reveal) { return toDto(q, withQuestions, reveal, null); }

  /** viewer = the student looking at the quiz (attempt counters are filled in for students only). */
  private QuizDto toDto(Quiz q, boolean withQuestions, boolean reveal, User viewer) {
    List<QuestionDto> qs = withQuestions ? questions.findByQuiz(q).stream().map(x -> dto(x, reveal)).toList() : List.of();
    Integer max = limit(q);
    int used = 0;
    Integer last = null;
    if (viewer != null && viewer.getRole() == Role.STUDENT) {
      List<QuizResult> rs = results.findByStudentAndQuizOrderByIdAsc(viewer, q);
      used = rs.size();
      if (!rs.isEmpty()) last = rs.get(rs.size() - 1).getPercent();
    }
    return new QuizDto(q.getId(), q.getTitle(), q.getStatus(), q.isAiGenerated(), q.getLesson().getId(), qs, max, used, max != null && used >= max, last);
  }

  /** null = unlimited; otherwise the number of attempts a student may complete. */
  private static Integer limit(Quiz q) { return q.getMaxAttempts() != null && q.getMaxAttempts() > 0 ? q.getMaxAttempts() : null; }

  private static Integer normalize(Integer max) {
    if (max == null || max <= 0) return null;
    if (max > 10) throw ApiException.badRequest("The maximum is 10 attempts");
    return max;
  }

  // ---------- attempt policy (instructor / admin) ----------
  @Transactional
  public QuizDto setMaxAttempts(User u, Long id, Integer max) {
    Quiz q = quiz(id);
    access.requireOwnerOrAdmin(u, q.getLesson().getModule().getCourse());
    q.setMaxAttempts(normalize(max));
    return toDto(quizzes.save(q), false, true);
  }

  @Transactional
  public int setMaxAttemptsForCourse(User u, Long courseId, Integer max) {
    Course c = courseRepo.findById(courseId).orElseThrow(() -> ApiException.notFound("Course"));
    access.requireOwnerOrAdmin(u, c);
    int n = 0;
    for (Quiz q : quizzes.findByLessonModuleCourseId(courseId)) { q.setMaxAttempts(normalize(max)); quizzes.save(q); n++; }
    return n;
  }

  /** Who completed the quiz, with scores (newest first). */
  public List<Map<String, Object>> studentResults(User u, Long id) {
    Quiz q = quiz(id);
    access.requireOwnerOrAdmin(u, q.getLesson().getModule().getCourse());
    List<Map<String, Object>> out = new ArrayList<>();
    for (QuizResult r : results.findByQuizOrderByIdDesc(q)) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("studentId", r.getStudent().getId());
      m.put("student", r.getStudent().getName());
      m.put("score", r.getScore());
      m.put("total", r.getTotal());
      m.put("percent", r.getPercent());
      m.put("passed", r.isPassed());
      m.put("date", r.getCreatedAt().toString());
      out.add(m);
    }
    return out;
  }

  /** Lets one student take the quiz again (removes that student's attempts for this quiz). */
  @Transactional
  public void allowRetake(User u, Long quizId, Long studentId) {
    Quiz q = quiz(quizId);
    access.requireOwnerOrAdmin(u, q.getLesson().getModule().getCourse());
    results.deleteByQuizAndStudentId(q, studentId);
    attempts.deleteByQuizAndStudentId(q, studentId);
  }

  /** Every published quiz of the student's courses with Status / Attempt / Access. */
  public List<Map<String, Object>> myQuizzes(User u) {
    List<Map<String, Object>> out = new ArrayList<>();
    if (u.getRole() != Role.STUDENT) return out;
    for (Enrollment e : enrollmentRepo.findByStudent(u)) {
      Course c = e.getCourse();
      List<Quiz> list = new ArrayList<>(quizzes.findByLessonModuleCourseIdAndStatus(c.getId(), QuizStatus.PUBLISHED));
      list.sort(Comparator.comparingInt((Quiz x) -> x.getLesson().getModule().getPosition()).thenComparingInt(x -> x.getLesson().getPosition()));
      for (Quiz q : list) {
        QuizDto d = toDto(q, false, false, u);
        boolean inProgress = d.attemptsUsed() == 0 && attempts.findFirstByStudentAndQuizAndSubmittedFalseOrderByIdDesc(u, q).isPresent();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("quizId", q.getId());
        m.put("title", q.getTitle());
        m.put("lessonId", q.getLesson().getId());
        m.put("topic", q.getLesson().getTitle());
        m.put("module", q.getLesson().getModule().getTitle());
        m.put("courseId", c.getId());
        m.put("course", c.getTitle());
        m.put("maxAttempts", d.maxAttempts());
        m.put("attemptsUsed", d.attemptsUsed());
        m.put("locked", d.locked());
        m.put("lastPercent", d.lastPercent());
        m.put("status", d.attemptsUsed() > 0 ? "Completed" : inProgress ? "In progress" : "Not started");
        m.put("access", d.locked() ? "Locked" : "Open");
        out.add(m);
      }
    }
    return out;
  }
}
