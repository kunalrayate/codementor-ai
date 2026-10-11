package com.learnai.repo;
import com.learnai.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.*;

public interface AiUsageRepository extends JpaRepository<AiUsage, Long> {
  Optional<AiUsage> findByUserAndUsageDate(User user, LocalDate date);
  List<AiUsage> findByUsageDateGreaterThanEqualOrderByUsageDateDesc(LocalDate from);
  @Query("select coalesce(sum(u.totalTokens),0L) from AiUsage u") long totalTokens();
  @Query("select coalesce(sum(u.requests),0L) from AiUsage u where u.usageDate = :d") long requestsOn(@Param("d") LocalDate d);
}
