package com.learnai.service;

import com.learnai.common.ApiException;
import com.learnai.model.AppSetting;
import com.learnai.repo.AppSettingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.web.client.RestClient;

/**
 * AI provider settings. An admin can pick a provider and paste an API key in the UI (stored encrypted in MySQL),
 * which takes effect immediately without restarting. If nothing is stored, the environment / application.yml values are used.
 * Every provider is reached through Spring AI's OpenAI-compatible client (OpenAI, Gemini, Groq, Ollama, custom).
 */
@Service
@Slf4j
public class AiSettingsService {
  public record Cfg(String provider, String apiKey, String baseUrl, String chatPath, String embeddingsPath, String model, String embeddingModel, String source) {}
  public record View(String provider, String baseUrl, String chatPath, String embeddingsPath, String model, String embeddingModel, String apiKeyMasked, boolean configured, String source) {}
  public record Request(String provider, String apiKey, String baseUrl, String chatPath, String embeddingsPath, String model, String embeddingModel) {}

  /** base URL, chat path, embeddings path, chat model, embedding model */
  private static final Map<String, String[]> PRESETS = Map.of(
      "GEMINI", new String[]{"https://generativelanguage.googleapis.com", "/v1beta/openai/chat/completions", "/v1beta/openai/embeddings", "gemini-flash-lite-latest", "gemini-embedding-001"},
      "GROQ", new String[]{"https://api.groq.com/openai", "/v1/chat/completions", "/v1/embeddings", "llama-3.3-70b-versatile", ""},
      "OPENAI", new String[]{"https://api.openai.com", "/v1/chat/completions", "/v1/embeddings", "gpt-4o-mini", "text-embedding-3-small"},
      "OLLAMA", new String[]{"http://localhost:11434", "/v1/chat/completions", "/v1/embeddings", "llama3.2", "nomic-embed-text"});

  private final AppSettingRepository repo;
  private final ChatClient.Builder envChatBuilder;
  private final EmbeddingModel envEmbedding;
  private final String envKey, envBase, envChatPath, envEmbPath, envModel, envEmbModel;
  private final byte[] secret;
  private final Map<String, ChatClient> clients = new java.util.concurrent.ConcurrentHashMap<>();
  private volatile List<String> modelCache;
  private volatile long modelCacheAt;
  private static final RetryTemplate RETRY = RetryTemplate.builder().maxAttempts(1).retryOn(TransientAiException.class).build(); // the gateway does the failover
  private EmbeddingModel cachedEmb;
  private String cachedEmbKey;

  public AiSettingsService(AppSettingRepository repo, ChatClient.Builder envChatBuilder, EmbeddingModel envEmbedding,
                           @Value("${spring.ai.openai.api-key}") String envKey,
                           @Value("${spring.ai.openai.base-url}") String envBase,
                           @Value("${spring.ai.openai.chat.completions-path}") String envChatPath,
                           @Value("${spring.ai.openai.embedding.embeddings-path}") String envEmbPath,
                           @Value("${spring.ai.openai.chat.options.model}") String envModel,
                           @Value("${spring.ai.openai.embedding.options.model}") String envEmbModel,
                           @Value("${app.jwt.secret}") String jwtSecret) {
    this.repo = repo;
    this.envChatBuilder = envChatBuilder;
    this.envEmbedding = envEmbedding;
    this.envKey = envKey;
    this.envBase = envBase;
    this.envChatPath = envChatPath;
    this.envEmbPath = envEmbPath;
    this.envModel = envModel;
    this.envEmbModel = envEmbModel;
    try {
      this.secret = MessageDigest.getInstance("SHA-256").digest(jwtSecret.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) { throw new IllegalStateException(e); }
  }

  // ---------- effective configuration ----------
  public Cfg effective() {
    String provider = get("ai.provider");
    if (provider != null && !provider.isBlank()) {
      String key = decrypt(get("ai.apiKey"));
      String[] p = PRESETS.getOrDefault(provider, new String[]{envBase, envChatPath, envEmbPath, envModel, envEmbModel});
      if (!key.isBlank() || provider.equals("OLLAMA"))
        return new Cfg(provider, key, or(get("ai.baseUrl"), p[0]), or(get("ai.chatPath"), p[1]), or(get("ai.embeddingsPath"), p[2]),
            or(get("ai.model"), p[3]), or(get("ai.embeddingModel"), p[4]), "database");
    }
    return new Cfg("ENV", envKey, envBase, envChatPath, envEmbPath, envModel, envEmbModel, "environment");
  }

  public boolean configured() {
    Cfg c = effective();
    boolean local = c.baseUrl().contains("localhost") || c.baseUrl().contains("127.0.0.1");
    boolean hasKey = c.apiKey() != null && !c.apiKey().isBlank() && !c.apiKey().equals("not-configured");
    return hasKey || local;
  }

  public ChatClient chatClient() { return chatClient(null); }

  /** One ChatClient per (provider, key, model); modelOverride lets the gateway try another model. */
  public ChatClient chatClient(String modelOverride) {
    Cfg c = effective();
    String model = modelOverride != null ? modelOverride : c.model();
    String k = c.provider() + "|" + c.apiKey() + "|" + c.baseUrl() + "|" + c.chatPath() + "|" + model;
    return clients.computeIfAbsent(k, x -> ChatClient.create(OpenAiChatModel.builder().openAiApi(api(c, 25_000))
        .defaultOptions(OpenAiChatOptions.builder().model(model).temperature(0.4).maxTokens(4096).build())
        .retryTemplate(RETRY).build()));
  }

  public synchronized EmbeddingModel embeddingModel() {
    Cfg c = effective();
    if (c.source().equals("environment")) return envEmbedding;
    if (c.embeddingModel() == null || c.embeddingModel().isBlank())
      throw new IllegalStateException("This provider has no embedding model configured");
    String k = c.provider() + "|" + c.apiKey() + "|" + c.baseUrl() + "|" + c.embeddingsPath() + "|" + c.embeddingModel();
    if (cachedEmb == null || !k.equals(cachedEmbKey)) {
      cachedEmb = new OpenAiEmbeddingModel(api(c, 8_000), MetadataMode.EMBED, OpenAiEmbeddingOptions.builder().model(c.embeddingModel()).build(), RETRY);
      cachedEmbKey = k;
    }
    return cachedEmb;
  }

  private OpenAiApi api(Cfg c, int readTimeoutMs) {
    SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
    f.setConnectTimeout(10_000);
    f.setReadTimeout(readTimeoutMs);   // never hang for minutes
    return OpenAiApi.builder().baseUrl(c.baseUrl()).apiKey(c.apiKey() == null || c.apiKey().isBlank() ? "ollama" : c.apiKey())
        .completionsPath(c.chatPath()).embeddingsPath(c.embeddingsPath()).restClientBuilder(RestClient.builder().requestFactory(f)).build();
  }

  // ---------- model discovery ----------
  private static final Pattern NOT_CHAT = Pattern.compile(
      "embed|tts|image|imagen|veo|live|audio|aqa|vision|robotics|computer-use|gemma|learnai|learnlm|whisper|guard|moderation|dall|transcribe",
      Pattern.CASE_INSENSITIVE);

  /** Asks the provider which chat models this key can use (OpenAI-compatible GET /models). */
  @SuppressWarnings("unchecked")
  public List<String> listModels() {
    Cfg c = effective();
    String path = c.chatPath().contains("/chat/completions") ? c.chatPath().replace("/chat/completions", "/models") : "/v1/models";
    String key = c.apiKey() == null ? "" : c.apiKey();
    boolean hasKey = !key.isBlank() && !key.equals("not-configured");
    Map<String, Object> body = RestClient.create().get().uri(c.baseUrl() + path)
        .headers(h -> { if (hasKey) h.setBearerAuth(key); }).retrieve().body(Map.class);
    List<String> ids = new ArrayList<>();
    Object data = body == null ? null : body.get("data");
    if (data instanceof List<?> list) {
      for (Object o : list) {
        if (o instanceof Map<?, ?> m && m.get("id") != null) {
          String id = m.get("id").toString();
          if (id.startsWith("models/")) id = id.substring(7);
          if (!NOT_CHAT.matcher(id).find()) ids.add(id);
        }
      }
    }
    ids.sort(String::compareTo);
    return ids;
  }

  static String pick(List<String> ids) {
    List<String> cand = ids.stream().filter(id -> !id.toLowerCase().contains("lite")).toList();
    if (cand.isEmpty()) cand = ids;
    for (String id : cand) if (id.endsWith("flash-latest")) return id;
    String best = null;
    double bestScore = -1;
    Pattern num = Pattern.compile("(\\d+(?:\\.\\d+)?)");
    for (String id : cand) {
      String l = id.toLowerCase();
      double s = l.contains("flash") ? 1000 : 0;
      if (l.contains("preview") || l.contains("exp")) s -= 500;
      Matcher m = num.matcher(l);
      if (m.find()) s += Double.parseDouble(m.group(1));
      if (s > bestScore) { bestScore = s; best = id; }
    }
    return best;
  }

  /** When the saved model no longer exists, find a working one and remember it. Returns the new model or null. */
  public synchronized String autoSelectModel() {
    Cfg c = effective();
    if (!c.source().equals("database")) return null;
    try {
      String chosen = pick(listModels());
      if (chosen == null || chosen.equals(c.model())) return null;
      put("ai.model", chosen);
      clients.clear();
      return chosen;
    } catch (Exception e) {
      log.warn("Could not auto-select an AI model: {}", e.getMessage());
      return null;
    }
  }

  // ---------- admin view / save ----------
  public View view() {
    Cfg c = effective();
    String k = c.apiKey() == null ? "" : c.apiKey();
    String masked = (k.isBlank() || k.equals("not-configured")) ? "" : "••••" + k.substring(Math.max(0, k.length() - 4));
    return new View(c.provider(), c.baseUrl(), c.chatPath(), c.embeddingsPath(), c.model(), c.embeddingModel(), masked, configured(), c.source());
  }

  @Transactional
  public synchronized void save(Request r) {
    String provider = r.provider() == null ? "" : r.provider().trim().toUpperCase();
    if (provider.isEmpty() || provider.equals("ENV")) { // back to environment configuration
      for (String k : new String[]{"ai.provider", "ai.apiKey", "ai.baseUrl", "ai.chatPath", "ai.embeddingsPath", "ai.model", "ai.embeddingModel"}) remove(k);
    } else {
      if (!PRESETS.containsKey(provider) && !provider.equals("CUSTOM")) throw ApiException.badRequest("Unknown provider");
      boolean hasNewKey = r.apiKey() != null && !r.apiKey().isBlank();
      if (!hasNewKey && get("ai.apiKey") == null && !provider.equals("OLLAMA")) throw ApiException.badRequest("Please enter your API key");
      put("ai.provider", provider);
      if (hasNewKey) put("ai.apiKey", encrypt(r.apiKey().trim()));
      put("ai.model", r.model());
      if (provider.equals("CUSTOM")) {
        if (r.baseUrl() == null || r.baseUrl().isBlank()) throw ApiException.badRequest("Base URL is required for a custom provider");
        put("ai.baseUrl", r.baseUrl()); put("ai.chatPath", r.chatPath()); put("ai.embeddingsPath", r.embeddingsPath()); put("ai.embeddingModel", r.embeddingModel());
      } else {
        for (String k : new String[]{"ai.baseUrl", "ai.chatPath", "ai.embeddingsPath", "ai.embeddingModel"}) remove(k);
      }
    }
    clients.clear();
    modelCache = null;
    cachedEmb = null;
  }

  // ---------- storage helpers ----------
  private String get(String key) { return repo.findBySkey(key).map(AppSetting::getSvalue).orElse(null); }
  private void remove(String key) { repo.findBySkey(key).ifPresent(repo::delete); }
  private void put(String key, String value) {
    if (value == null || value.isBlank()) { remove(key); return; }
    AppSetting s = repo.findBySkey(key).orElseGet(() -> { AppSetting n = new AppSetting(); n.setSkey(key); return n; });
    s.setSvalue(value.trim());
    repo.save(s);
  }
  private static String or(String a, String b) { return a != null && !a.isBlank() ? a : b; }

  private String encrypt(String plain) {
    try {
      byte[] iv = new byte[12];
      new SecureRandom().nextBytes(iv);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(secret, "AES"), new GCMParameterSpec(128, iv));
      byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
      byte[] out = new byte[iv.length + ct.length];
      System.arraycopy(iv, 0, out, 0, iv.length);
      System.arraycopy(ct, 0, out, iv.length, ct.length);
      return Base64.getEncoder().encodeToString(out);
    } catch (Exception e) { throw new IllegalStateException("Encryption failed", e); }
  }

  private String decrypt(String enc) {
    if (enc == null || enc.isBlank()) return "";
    try {
      byte[] in = Base64.getDecoder().decode(enc);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(secret, "AES"), new GCMParameterSpec(128, in, 0, 12));
      return new String(c.doFinal(in, 12, in.length - 12), StandardCharsets.UTF_8);
    } catch (Exception e) {
      log.warn("Stored AI key could not be decrypted (was JWT_SECRET changed?). Please enter it again in Admin -> AI Settings.");
      return "";
    }
  }

  // ---------- model fallback chain ----------
  private static final Pattern NUM = Pattern.compile("(\\d+(?:\\.\\d+)?)");

  private static double score(String id) {
    String l = id.toLowerCase();
    double s = l.contains("flash") ? 1000 : 0;
    if (l.contains("lite")) s -= 200;
    if (l.contains("preview") || l.contains("exp")) s -= 500;
    if (l.endsWith("-latest")) s += 50;
    Matcher m = NUM.matcher(l);
    if (m.find()) s += Double.parseDouble(m.group(1));
    return s;
  }

  /** Models the key can use, best first (cached for 10 minutes). */
  public List<String> rankedModels() {
    List<String> cached = modelCache;
    if (cached != null && System.currentTimeMillis() - modelCacheAt < 600_000) return cached;
    List<String> l = new ArrayList<>(listModels());
    l.sort(Comparator.comparingDouble(AiSettingsService::score).reversed());
    modelCache = l;
    modelCacheAt = System.currentTimeMillis();
    return l;
  }

  /** Next model to try after a failure, or null. */
  public String nextModel(Collection<String> tried) {
    try {
      for (String id : rankedModels()) if (!tried.contains(id)) return id;
    } catch (Exception e) {
      log.warn("Could not list AI models for fallback: {}", e.getMessage());
    }
    return null;
  }

  /** A model that just worked becomes the saved model (database configuration only). */
  public void remember(String model) {
    Cfg c = effective();
    if (c.source().equals("database") && model != null && !model.equals(c.model())) {
      put("ai.model", model);
      log.info("AI model switched to '{}'", model);
    }
  }

  /** Encrypted key/value storage reused by other integrations (e.g. the YouTube API key). */
  public String getSecret(String key) { return decrypt(get(key)); }
  public void putSecret(String key, String value) {
    if (value == null || value.isBlank()) remove(key); else put(key, encrypt(value.trim()));
  }
}
