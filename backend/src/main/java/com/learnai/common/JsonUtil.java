package com.learnai.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;

/** Extracts and parses JSON that an LLM returned (handles ``` fences and stray text). */
public final class JsonUtil {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private JsonUtil() {}

  public static <T> T parseArray(String raw, TypeReference<T> type) {
    try {
      int s = raw.indexOf('['), e = raw.lastIndexOf(']');
      if (s < 0 || e <= s) throw new IllegalArgumentException("no json array");
      return MAPPER.readValue(raw.substring(s, e + 1), type);
    } catch (Exception ex) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "AI returned an invalid format, please try again");
    }
  }
}
