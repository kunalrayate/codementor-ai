package com.learnai.service;

import com.learnai.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.regex.Pattern;

/** Input-side guardrails: length limit + prompt-injection heuristics. */
@Service
public class AiGuardrailService {
  private static final List<Pattern> INJECTION = List.of(
      "ignore (all |any |the )?(previous|prior|above|earlier) (instructions|prompts?|rules)",
      "disregard (all |any |the )?(previous|prior|above|earlier)",
      "(reveal|show|print|repeat|leak) (me )?(your|the) (system )?(prompt|instructions|rules)",
      "you are now (a|an|in|no longer)",
      "pretend (to be|you are) (?!a student)",
      "developer mode", "jailbreak", "\\bDAN\\b",
      "bypass (your |the )?(rules|restrictions|guardrails|filters)"
  ).stream().map(p -> Pattern.compile(p, Pattern.CASE_INSENSITIVE)).toList();

  @Value("${app.ai.max-input-chars}") private int maxChars;

  public void check(String text) { check(text, maxChars); }

  public void check(String text, int limit) {
    if (text == null || text.isBlank()) throw ApiException.badRequest("Message is empty");
    if (text.length() > limit) throw ApiException.badRequest("Message too long (max " + limit + " characters)");
    for (Pattern p : INJECTION)
      if (p.matcher(text).find()) throw ApiException.badRequest("That message looks like an attempt to override the tutor's rules.");
  }
}
