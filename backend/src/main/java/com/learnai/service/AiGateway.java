package com.learnai.service;

import com.learnai.common.ApiException;
import com.learnai.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * The single door to the LLM. Every AI feature goes through here:
 * config check -> rate limit (Bucket4j) -> daily quota -> Spring AI call -> token accounting -> output validation.
 * If the provider says the model no longer exists, a working model is discovered, saved and the call is retried once.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AiGateway {
  public static final String NOT_CONFIGURED = "AI is not configured yet. An administrator can add a free API key in Admin > AI Settings (takes about a minute).";
  private final AiSettingsService settings;
  private final RateLimitService rateLimit;
  private final UsageService usage;

  public boolean configured() { return settings.configured(); }

  public String ask(User user, String system, List<Message> history, String userPrompt) {
    if (!settings.configured()) throw new ApiException(HttpStatus.BAD_GATEWAY, NOT_CONFIGURED);
    rateLimit.check(user.getId());
    usage.assertQuota(user);
    ChatResponse resp = null;
    Exception last = null;
    java.util.Set<String> tried = new java.util.LinkedHashSet<>();
    tried.add(settings.effective().model());
    String model = null;
    long started = System.currentTimeMillis();
    for (int attempt = 0; attempt < 3; attempt++) {
      try {
        resp = call(model, system, history, userPrompt);
        if (model != null) settings.remember(model);
        last = null;
        break;
      } catch (Exception e) {
        last = e;
        if (!fallbackWorthy(e) || System.currentTimeMillis() - started > 40_000) break;
        String next = settings.nextModel(tried);
        if (next == null) break;
        String msg = chain(e).replaceAll("\\s+", " ");
        log.warn("AI model failed ({}). Trying '{}'", msg.substring(0, Math.min(120, msg.length())), next);
        tried.add(next);
        model = next;
        try { Thread.sleep(600); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
      }
    }
    if (resp == null) {
      AiSettingsService.Cfg cfg = settings.effective();
      log.error("LLM call failed (provider={}, model={}, baseUrl={})", cfg.provider(), cfg.model(), cfg.baseUrl(), last);
      throw new ApiException(HttpStatus.BAD_GATEWAY, explain(last == null ? new IllegalStateException("no response") : last, cfg));
    }
    if (resp == null || resp.getResult() == null) throw new ApiException(HttpStatus.BAD_GATEWAY, "Empty AI response");
    String text = resp.getResult().getOutput().getText();
    Usage u = resp.getMetadata() != null ? resp.getMetadata().getUsage() : null;
    usage.record(user, u == null ? 0 : n(u.getPromptTokens()), u == null ? 0 : n(u.getCompletionTokens()));
    if (text == null || text.isBlank()) throw new ApiException(HttpStatus.BAD_GATEWAY, "The AI returned an empty answer. Please try again.");
    return text.length() > 12000 ? text.substring(0, 12000) : text; // output validation: hard cap
  }

  private ChatResponse call(String model, String system, List<Message> history, String userPrompt) {
    return settings.chatClient(model).prompt().system(system).messages(history).user(userPrompt).call().chatResponse();
  }

  private static String chain(Throwable e) {
    StringBuilder sb = new StringBuilder();
    for (Throwable t = e; t != null; t = t.getCause()) if (t.getMessage() != null) sb.append(t.getMessage()).append(' ');
    return sb.toString();
  }

  /** Wrong/retired model, quota used up on this model, or provider overloaded: another model may work. */
  private boolean fallbackWorthy(Throwable e) {
    String all = chain(e).toLowerCase();
    return modelMissing(e) || all.contains("429") || all.contains("quota") || all.contains("resource_exhausted") || all.contains("rate limit")
        || all.contains("read timed out") || all.contains("503") || all.contains("500") || all.contains("overloaded") || all.contains("unavailable") || all.contains("high demand");
  }

  private boolean modelMissing(Throwable e) {
    String all = chain(e).toLowerCase();
    return all.contains("404") || all.contains("model_not_found") || all.contains("is not found") || all.contains("is not supported");
  }

  private String explain(Exception e, AiSettingsService.Cfg cfg) {
    String raw = chain(e);
    String all = raw.toLowerCase();
    String clean = raw.replaceAll("[\\[\\]{}\"]", " ").replaceAll("\\s+", " ").trim();
    String snippet = clean.length() > 160 ? clean.substring(0, 160) : clean;
    if (all.contains("401") || all.contains("invalid_api_key") || all.contains("incorrect api key") || all.contains("api key not valid")
        || all.contains("unauthorized") || all.contains("permission_denied") || all.contains("403"))
      return "The AI provider rejected the API key. Re-enter it in Admin > AI Settings.";
    if (all.contains("429") || all.contains("quota") || all.contains("rate limit") || all.contains("insufficient"))
      return "The AI provider says the quota/rate limit is used up (free-tier limit or billing). Wait a minute and try again, or use another provider in Admin > AI Settings.";
    if (all.contains("503") || all.contains("high demand") || all.contains("overloaded") || all.contains("unavailable"))
      return "The AI service is busy right now (high demand at the provider). Please try again in a minute.";
    if (modelMissing(e))
      return "Model or endpoint not found (model: " + cfg.model() + "). In Admin > AI Settings click 'Load available models', pick one and Save. Provider said: " + snippet;
    if (all.contains("read timed out"))
      return "The AI did not answer in time (the provider is slow right now). Please try again in a moment.";
    if (all.contains("connection refused") || all.contains("unknownhost") || all.contains("timed out") || all.contains("i/o error") || all.contains("connect"))
      return "Cannot reach the AI provider at " + cfg.baseUrl() + ". Check your internet connection (or that Ollama is running).";
    return "The AI provider returned an error: " + snippet;
  }

  private static int n(Integer i) { return i == null ? 0 : i; }
}
