package com.learnai.service;

import com.learnai.common.ApiException;
import com.learnai.dto.Dtos.UsageStatus;
import com.learnai.model.*;
import com.learnai.repo.AiUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

/** Daily AI quota + token accounting (table ai_usage). */
@Service
@RequiredArgsConstructor
public class UsageService {
  private final AiUsageRepository repo;
  @Value("${app.ai.daily-limit}") private int dailyLimit;

  @Transactional
  public void assertQuota(User u) {
    if (today(u).getRequests() >= dailyLimit)
      throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Daily AI limit reached (" + dailyLimit + " requests). Try again tomorrow.");
  }

  @Transactional
  public void record(User u, int in, int out) {
    AiUsage a = today(u);
    a.setRequests(a.getRequests() + 1);
    a.setInputTokens(a.getInputTokens() + in);
    a.setOutputTokens(a.getOutputTokens() + out);
    a.setTotalTokens(a.getTotalTokens() + in + out);
    repo.save(a);
  }

  @Transactional
  public UsageStatus status(User u) {
    int used = today(u).getRequests();
    return new UsageStatus(dailyLimit, used, Math.max(0, dailyLimit - used));
  }

  private AiUsage today(User u) {
    return repo.findByUserAndUsageDate(u, LocalDate.now()).orElseGet(() -> {
      AiUsage a = new AiUsage();
      a.setUser(u);
      a.setUsageDate(LocalDate.now());
      return repo.save(a);
    });
  }
}
