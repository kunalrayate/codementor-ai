package com.learnai.service;

import com.learnai.common.*;
import com.learnai.dto.Dtos.*;
import com.learnai.model.*;
import com.learnai.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CodeService {
  private static final int MAX_CODE = 10_000;
  private final CodingProblemRepository problems;
  private final TestCaseRepository tests;
  private final CodeSubmissionRepository submissions;
  private final SandboxService sandbox;
  private final AiGateway ai;
  private final XpService xp;

  public List<ProblemDto> list(User u, String topic) {
    Set<Long> solved = u.getRole() == Role.STUDENT ? new HashSet<>(submissions.solvedIds(u)) : Set.of();
    return problems.findAll().stream()
        .filter(p -> topic == null || topic.isBlank() || topic.equalsIgnoreCase(p.getTopic()))
        .sorted(Comparator.comparing((CodingProblem p) -> p.getTopic() == null ? "" : p.getTopic()).thenComparing(CodingProblem::getId))
        .map(p -> dto(p, solved.contains(p.getId()))).toList();
  }

  public List<String> topics() {
    return problems.findAll().stream().map(CodingProblem::getTopic).filter(t -> t != null && !t.isBlank()).distinct().sorted().toList();
  }

  public Map<String, Object> info() { return Map.of("mode", sandbox.mode(), "configured", sandbox.configuredMode()); }

  public ProblemDto get(Long id) { return dto(problem(id), false); }

  @Transactional
  public ProblemDto create(User u, ProblemRequest r) {
    if (r.tests() == null || r.tests().isEmpty()) throw ApiException.badRequest("At least one test case is required");
    CodingProblem p = new CodingProblem();
    p.setTitle(r.title());
    p.setDescription(r.description());
    p.setStarterCode(r.starterCode());
    p.setTopic(r.topic() == null || r.topic().isBlank() ? "General" : r.topic().trim());
    p.setDifficulty(r.difficulty() == null || r.difficulty().isBlank() ? "Easy" : r.difficulty().trim());
    p.setCreatedBy(u);
    problems.save(p);
    for (TestCaseDto t : r.tests()) {
      TestCase tc = new TestCase();
      tc.setProblem(p);
      tc.setInputData(t.input());
      tc.setExpectedOutput(t.expected());
      tests.save(tc);
    }
    return dto(p, false);
  }

  /** Free-form run with custom stdin (no test cases, no XP). Errors come back with plain-English explanations. */
  public RunResponse run(CodeRunRequest r) {
    validate(r.code());
    Path dir = null;
    try {
      dir = sandbox.prepare(r.code());
      RunResult c = sandbox.compile(dir);
      if (!c.ok()) {
        String out = CodeDiagnostics.clean(c.output());
        return new RunResponse(false, "Compilation failed:\n" + out, c.timedOut(), dockerHint(out, c.timedOut()));
      }
      RunResult x = sandbox.run(dir, r.input());
      String out = CodeDiagnostics.clean(x.output());
      return new RunResponse(x.ok(), out, x.timedOut(), x.ok() ? List.of() : dockerHint(out, x.timedOut()));
    } catch (IOException e) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not prepare the sandbox folder: " + e.getMessage());
    } finally { sandbox.cleanup(dir); }
  }

  public SubmissionResult submit(User u, Long problemId, String code) {
    validate(code);
    CodingProblem p = problem(problemId);
    List<TestCase> tcs = tests.findByProblem(p);
    String status, compileOut = null;
    int passed = 0;
    List<TestOutcome> outs = new ArrayList<>();
    List<String> hints = new ArrayList<>();
    Path dir = null;
    try {
      dir = sandbox.prepare(code);
      RunResult c = sandbox.compile(dir);
      if (!c.ok()) {
        status = c.timedOut() ? "TIMEOUT" : "COMPILE_ERROR";
        compileOut = CodeDiagnostics.clean(c.output());
        hints.addAll(dockerHint(compileOut, c.timedOut()));
      } else {
        boolean timeout = false, revealed = false;
        for (int i = 0; i < tcs.size(); i++) {
          TestCase t = tcs.get(i);
          RunResult r = sandbox.run(dir, t.getInputData());
          String actual = CodeDiagnostics.clean(r.output());
          boolean ok = r.ok() && norm(r.output()).equals(norm(t.getExpectedOutput()));
          if (ok) passed++;
          if (r.timedOut()) timeout = true;
          boolean reveal = !ok && !revealed; // only the first failing test is revealed; the rest stay hidden
          if (reveal) {
            revealed = true;
            List<String> why = r.ok() ? List.of() : CodeDiagnostics.explain(actual, r.timedOut());
            if (!why.isEmpty()) hints.addAll(why);
            else hints.add("Your program ran, but its output is different from the expected output for this test. Compare 'Expected' and 'Your output' - spelling, spaces and line breaks all matter.");
          }
          outs.add(new TestOutcome(i + 1, ok, reveal ? t.getInputData() : null, reveal ? t.getExpectedOutput() : null,
              reveal ? Mappers.clip(actual, 600) : null));
        }
        status = (!tcs.isEmpty() && passed == tcs.size()) ? "PASSED" : timeout ? "TIMEOUT" : "FAILED";
      }
    } catch (IOException e) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not prepare the sandbox folder: " + e.getMessage());
    } finally { sandbox.cleanup(dir); }

    CodeSubmission s = new CodeSubmission();
    s.setStudent(u);
    s.setProblem(p);
    s.setSourceCode(code);
    s.setStatus(status);
    s.setPassedCount(passed);
    s.setTotalCount(tcs.size());
    submissions.save(s);

    int awarded = "PASSED".equals(status) ? xp.award(u, "PROBLEM_SOLVED", xp.rule("PROBLEM_SOLVED", 100), p.getId(), null, true) : 0;
    return new SubmissionResult(status, passed, tcs.size(), compileOut, outs, hints, awarded);
  }

  /** Optional AI hint, only when the student asks for it after a failed attempt. */
  public String hint(User u, Long problemId, String code, String error) {
    validate(code);
    CodingProblem p = problem(problemId);
    return ai.ask(u, Prompts.CODE_MENTOR, List.of(), "Problem:\n" + p.getDescription() + "\n\nStudent code:\n```java\n" + code + "\n```\nWhat went wrong:\n" + Mappers.clip(error, 1500));
  }

  private List<String> dockerHint(String out, boolean timedOut) {
    if (out != null && out.startsWith("Docker is not running")) return List.of(out);
    return CodeDiagnostics.explain(out, timedOut);
  }

  private void validate(String code) {
    if (code == null || code.isBlank()) throw ApiException.badRequest("Code is empty");
    if (code.length() > MAX_CODE) throw ApiException.badRequest("Code too long (max " + MAX_CODE + " characters)");
    if (!sandbox.mode().equals("docker")) {
      String bad = sandbox.forbidden(code);
      if (bad != null) throw ApiException.badRequest("'" + bad + "' is not allowed while code runs in local mode (Docker is not running).");
    }
  }

  private static String norm(String s) {
    if (s == null) return "";
    return Arrays.stream(s.replace("\r", "").strip().split("\n")).map(String::stripTrailing).collect(Collectors.joining("\n"));
  }

  private CodingProblem problem(Long id) { return problems.findById(id).orElseThrow(() -> ApiException.notFound("Problem")); }

  private ProblemDto dto(CodingProblem p, boolean solved) {
    List<TestCase> t = tests.findByProblem(p);
    String in = t.isEmpty() || t.get(0).getInputData() == null ? "" : t.get(0).getInputData();
    String out = t.isEmpty() || t.get(0).getExpectedOutput() == null ? "" : t.get(0).getExpectedOutput();
    return new ProblemDto(p.getId(), p.getTitle(), p.getDescription(), p.getStarterCode(), p.getTopic(), p.getDifficulty(), solved, in, out);
  }
}
