package com.learnai.service;

import com.learnai.common.ApiException;
import com.learnai.model.Lesson;
import com.learnai.repo.LessonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds an embeddable YouTube video for each lesson and stores the link on the lesson (one lookup per lesson).
 *  1. Official YouTube Data API (search.list) when a key works - recommended.
 *  2. Otherwise, if app.video.web-fallback=true, YouTube's public search page is read (unofficial, no setup) and every
 *     candidate is verified with YouTube's public oEmbed endpoint, which only succeeds for existing, embeddable videos.
 * A background job fills lessons that have no video shortly after start-up, so lessons open with a video ready.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VideoService {
  private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";
  private static final Pattern RESULT = Pattern.compile("\"videoRenderer\":\\{\"videoId\":\"([\\w-]{11})\".{0,3000}?\"title\":\\{\"runs\":\\[\\{\"text\":\"(.*?)\"\\}");

  private final AiSettingsService settings;
  private final LessonRepository lessons;
  @Value("${app.video.web-fallback:true}") private boolean webFallback;
  private final RestClient http = RestClient.create();
  private final RestClient web = RestClient.builder().requestFactory(timeouts()).build();
  private final Map<Long, Long> misses = new ConcurrentHashMap<>();

  private static SimpleClientHttpRequestFactory timeouts() {
    SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
    f.setConnectTimeout(5_000);
    f.setReadTimeout(9_000);
    return f;
  }

  private String ownKey() { return settings.getSecret("yt.apiKey"); }

  public String apiKey() {
    String own = ownKey();
    if (!own.isBlank()) return own;
    AiSettingsService.Cfg c = settings.effective();
    if (settings.configured() && c.baseUrl().contains("googleapis.com") && c.apiKey() != null) return c.apiKey();
    return "";
  }

  public boolean enabled() { return webFallback || !apiKey().isBlank(); }
  public String source() { return !ownKey().isBlank() ? "own YouTube API key" : (!apiKey().isBlank() ? "Gemini key (YouTube API must be enabled)" : (webFallback ? "YouTube web search (no key)" : "none")); }

  private static Object get(Object map, String key) { return map instanceof Map<?, ?> m ? m.get(key) : null; }

  /** Best video id for the query, or empty. Throws ApiException only when the official API failed and the fallback found nothing. */
  public Optional<String> search(String query) {
    ApiException official = null;
    if (!apiKey().isBlank()) {
      try {
        Optional<String> id = officialSearch(query);
        if (id.isPresent()) return id;
      } catch (ApiException e) { official = e; }
    }
    if (webFallback) {
      Optional<String> id = webSearch(query);
      if (id.isPresent()) return id;
    }
    if (official != null) throw official;
    return Optional.empty();
  }

  @SuppressWarnings("unchecked")
  private Optional<String> officialSearch(String query) {
    URI uri = UriComponentsBuilder.fromUriString("https://www.googleapis.com/youtube/v3/search")
        .queryParam("part", "snippet").queryParam("type", "video").queryParam("videoEmbeddable", "true")
        .queryParam("safeSearch", "strict").queryParam("relevanceLanguage", "en").queryParam("maxResults", 8)
        .queryParam("q", query).queryParam("key", apiKey()).build().encode().toUri();
    Map<String, Object> body;
    try {
      body = http.get().uri(uri).retrieve().body(Map.class);
    } catch (RestClientResponseException e) {
      String t = String.valueOf(e.getResponseBodyAsString()).toLowerCase();
      int code = e.getStatusCode().value();
      if (code == 403 && (t.contains("accessnotconfigured") || t.contains("has not been used") || t.contains("disabled")))
        throw new ApiException(HttpStatus.BAD_GATEWAY, "YouTube Data API v3 is not enabled for this key. Open https://console.cloud.google.com/apis/library/youtube.googleapis.com , choose your project and click Enable.");
      if (code == 403 && t.contains("quota")) throw new ApiException(HttpStatus.BAD_GATEWAY, "The daily YouTube quota is used up. Try again tomorrow.");
      if (code == 400 || code == 403) throw new ApiException(HttpStatus.BAD_GATEWAY, "YouTube rejected the API key (check the key and its API restrictions).");
      throw new ApiException(HttpStatus.BAD_GATEWAY, "YouTube search failed (HTTP " + code + ").");
    } catch (Exception e) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "Cannot reach YouTube: " + e.getMessage());
    }
    String first = null;
    Object items = body == null ? null : body.get("items");
    if (items instanceof List<?> list) {
      for (Object it : list) {
        String id = String.valueOf(get(get(it, "id"), "videoId"));
        if (id.length() != 11) continue;
        String title = String.valueOf(get(get(it, "snippet"), "title")).toLowerCase();
        if (first == null) first = id;
        if (title.contains("java") && !title.contains("javascript")) return Optional.of(id);
      }
    }
    return Optional.ofNullable(first);
  }

  /** No-key fallback: read the public search page, then keep the first result that oEmbed confirms is embeddable. */
  private Optional<String> webSearch(String query) {
    try {
      URI uri = UriComponentsBuilder.fromUriString("https://www.youtube.com/results")
          .queryParam("search_query", query).queryParam("sp", "EgIQAQ==").build().encode().toUri();
      String html = web.get().uri(uri).header("User-Agent", UA).header("Accept-Language", "en-US,en;q=0.9")
          .header("Cookie", "CONSENT=YES+1; SOCS=CAI").retrieve().body(String.class);
      if (html == null) return Optional.empty();
      Matcher m = RESULT.matcher(html);
      List<String[]> candidates = new ArrayList<>();
      Set<String> seen = new HashSet<>();
      while (m.find() && candidates.size() < 8) {
        String id = m.group(1);
        String title = m.group(2).toLowerCase();
        if (!seen.add(id) || title.contains("#shorts") || title.contains("javascript")) continue;
        candidates.add(new String[]{id, title});
      }
      candidates.sort(Comparator.comparingInt((String[] c) -> c[1].contains("java") ? 0 : 1)); // stable: keeps YouTube's order inside each group
      for (String[] c : candidates) if (embeddable(c[0])) return Optional.of(c[0]);
    } catch (Exception e) {
      log.warn("YouTube web search failed: {}", e.getMessage());
    }
    return Optional.empty();
  }

  /** YouTube's public oEmbed answers 200 only for existing videos that allow embedding. */
  private boolean embeddable(String id) {
    try {
      URI uri = UriComponentsBuilder.fromUriString("https://www.youtube.com/oembed")
          .queryParam("url", "https://www.youtube.com/watch?v=" + id).queryParam("format", "json").build().encode().toUri();
      web.get().uri(uri).header("User-Agent", UA).retrieve().toBodilessEntity();
      return true;
    } catch (Exception e) { return false; }
  }

  public Optional<String> resolve(Lesson l) {
    if (l.getVideoUrl() != null && !l.getVideoUrl().isBlank()) return Optional.of(l.getVideoUrl());
    Long miss = misses.get(l.getId());
    if (miss != null && System.currentTimeMillis() - miss < 600_000) return Optional.empty();
    String q = "Java " + (l.getTitle().length() <= 4 ? l.getModule().getTitle() + " " : "") + l.getTitle() + " tutorial";
    try {
      Optional<String> id = search(q);
      if (id.isEmpty()) { misses.put(l.getId(), System.currentTimeMillis()); return Optional.empty(); }
      l.setVideoUrl("https://www.youtube.com/watch?v=" + id.get());
      lessons.save(l);
      return Optional.of(l.getVideoUrl());
    } catch (ApiException e) {
      misses.put(l.getId(), System.currentTimeMillis());
      throw e;
    }
  }

  public Map<String, Object> fillAll() { return fillAll(0); }

  public Map<String, Object> fillAll(long pauseMs) {
    int ok = 0, failed = 0, checked = 0;
    String message = null;
    for (Lesson l : lessons.findAll()) {
      if (l.getVideoUrl() != null && !l.getVideoUrl().isBlank()) continue;
      if (++checked > 60) break;
      try {
        if (resolve(l).isPresent()) ok++; else failed++;
        if (pauseMs > 0) Thread.sleep(pauseMs);
      } catch (ApiException e) { message = e.getMessage(); failed++; break;
      } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("filled", ok);
    out.put("failed", failed);
    out.put("message", message != null ? message : (ok + " lesson(s) now have a video." + (failed > 0 ? " " + failed + " could not be matched." : "")));
    return out;
  }

  /** Fill lessons without a video in the background shortly after start-up, so students never wait for a lookup. */
  @EventListener(ApplicationReadyEvent.class)
  public void warmUp() {
    Thread t = new Thread(() -> {
      try {
        Thread.sleep(10_000);
        Map<String, Object> r = fillAll(1_200);
        log.info("Lesson video warm-up: {}", r.get("message"));
      } catch (Exception e) { log.warn("Lesson video warm-up skipped: {}", e.getMessage()); }
    }, "video-warmup");
    t.setDaemon(true);
    t.start();
  }
}
