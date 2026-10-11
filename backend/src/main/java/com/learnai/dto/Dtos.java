package com.learnai.dto;

import com.learnai.model.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class Dtos {
  private Dtos() {}
  // auth
  public record RegisterRequest(@NotBlank String name, @Email @NotBlank String email, @Size(min = 6) String password, Role role) {}
  public record LoginRequest(@NotBlank String email, @NotBlank String password) {}
  public record RefreshRequest(@NotBlank String refreshToken) {}
  public record UserInfo(Long id, String name, String email, Role role, Level level) {}
  public record AuthResponse(String accessToken, String refreshToken, UserInfo user) {}
  // courses
  public record CourseDto(Long id, String title, String description, String instructor, Long instructorId) {}
  public record LessonDto(Long id, String title, String description, String videoUrl, String content, int position, Long moduleId, Long courseId) {}
  public record ModuleDto(Long id, String title, int position, List<LessonDto> lessons) {}
  public record CourseDetail(CourseDto course, List<ModuleDto> modules, boolean enrolled, boolean canEdit) {}
  public record CourseRequest(@NotBlank String title, String description) {}
  public record ModuleRequest(@NotBlank String title, Integer position) {}
  public record LessonRequest(@NotBlank String title, String description, String videoUrl, String content, Integer position) {}
  // progress
  public record ProgressRequest(int percentWatched, int positionSeconds) {}
  public record LessonProgressDto(Long lessonId, int percentWatched, int positionSeconds, boolean completed, int xpAwarded) {}
  public record CourseProgress(int completed, int total, int percent, List<LessonProgressDto> lessons) {}
  // AI
  public record ChatRequest(Long conversationId, @NotBlank String message, Long lessonId, boolean useMaterials) {}
  public record ChatResponse(Long conversationId, String reply, int remainingRequests) {}
  public record MessageDto(String role, String content) {}
  public record ConversationDto(Long id, String title, LocalDateTime createdAt) {}
  public record FlashcardDto(Long id, String question, String answer) {}
  public record LessonIdRequest(@NotNull Long lessonId) {}
  public record QuizGenRequest(@NotNull Long lessonId, Integer numQuestions, boolean force) {}
  public record SummaryRequest(Long lessonId, Long documentId, String type) {}
  public record TextResponse(String text) {}
  public record UsageStatus(int limit, int used, int remaining) {}
  // quizzes
  public record QuestionDto(Long id, String text, List<String> options, String correct, String explanation) {}
  public record QuizDto(Long id, String title, QuizStatus status, boolean aiGenerated, Long lessonId, List<QuestionDto> questions, Integer maxAttempts, int attemptsUsed, boolean locked, Integer lastPercent) {}
  public record StatusRequest(QuizStatus status) {}
  public record QuestionEdit(String text, List<String> options, String correct, String explanation) {}
  public record SubmitRequest(Long attemptId, Map<Integer, String> answers) {}
  public record AttemptQuestion(int index, String text, List<String> options) {}
  public record AttemptDto(Long attemptId, String title, Long lessonId, List<AttemptQuestion> questions) {}
  public record QuestionResult(Long questionId, boolean correct, String yourAnswer, String correctAnswer, String explanation) {}
  public record QuizResultDto(int score, int total, int percent, boolean passed, int xpAwarded, List<QuestionResult> details) {}
  // leaderboard / me
  public record RankedRow(int rank, Long studentId, String name, long xp, long courses) {}
  public record LeaderboardDto(List<RankedRow> rows, RankedRow me) {}
  public record BadgeDto(String code, String name, String description) {}
  public record MeDto(UserInfo user, long totalXp, int globalRank, List<BadgeDto> badges, int streakDays, UsageStatus aiUsage) {}
  public record LevelRequest(Level level) {}
  // coding
  public record CodeRunRequest(@NotBlank String code, String input) {}
  public record CodeSubmitRequest(@NotBlank String code) {}
  public record TestCaseDto(String input, String expected) {}
  public record ProblemRequest(@NotBlank String title, @NotBlank String description, String starterCode, String topic, String difficulty, List<TestCaseDto> tests) {}
  public record ProblemDto(Long id, String title, String description, String starterCode, String topic, String difficulty, boolean solved, String sampleInput, String sampleOutput) {}
  public record TestOutcome(int index, boolean passed, String input, String expected, String actual) {}
  public record SubmissionResult(String status, int passed, int total, String compileOutput, List<TestOutcome> outcomes, List<String> hints, int xpAwarded) {}
  public record RunResult(boolean ok, String output, boolean timedOut) {}
  public record RunResponse(boolean ok, String output, boolean timedOut, List<String> hints) {}
  public record HintRequest(String code, String error) {}
  public record PlanStep(String text, String link) {}
  public record StudyPlanDto(String level, String headline, String stats, List<String> strengths, List<String> focus, List<PlanStep> steps) {}
  public record XpRuleDto(String code, int xp, String description) {}
  public record BadgeAdminDto(Long id, String code, String name, String description, Integer minXp) {}
  public record BadgeForm(String code, String name, String description, Integer minXp) {}
  // documents
  public record DocumentDto(Long id, String title, Long courseId, boolean hasFile, boolean mine) {}
}
