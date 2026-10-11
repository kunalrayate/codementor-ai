package com.learnai.web;

import com.learnai.dto.Dtos.*;
import com.learnai.model.*;
import com.learnai.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CodeController {
  private final CodeService code;

  @GetMapping("/problems") public List<ProblemDto> problems(@AuthenticationPrincipal User u, @RequestParam(required = false) String topic) { return code.list(u, topic); }
  @GetMapping("/problems/topics") public List<String> topics() { return code.topics(); }
  @GetMapping("/problems/{id}") public ProblemDto problem(@PathVariable Long id) { return code.get(id); }
  @GetMapping("/code/info") public Map<String, Object> info() { return code.info(); }

  @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
  @PostMapping("/problems") public ProblemDto create(@AuthenticationPrincipal User u, @Valid @RequestBody ProblemRequest r) { return code.create(u, r); }

  @PreAuthorize("hasRole('STUDENT')")
  @PostMapping("/problems/{id}/submit") public SubmissionResult submit(@AuthenticationPrincipal User u, @PathVariable Long id, @Valid @RequestBody CodeSubmitRequest r) { return code.submit(u, id, r.code()); }

  @PreAuthorize("hasRole('STUDENT')")
  @PostMapping("/problems/{id}/hint") public TextResponse hint(@AuthenticationPrincipal User u, @PathVariable Long id, @RequestBody HintRequest r) { return new TextResponse(code.hint(u, id, r.code(), r.error())); }

  @PostMapping("/code/execute") public RunResponse execute(@Valid @RequestBody CodeRunRequest r) { return code.run(r); }
}
