package com.learnai.repo;
import com.learnai.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.*;

public interface UserBadgeRepository extends JpaRepository<UserBadge, Long> {
  List<UserBadge> findByUser(User user);
  boolean existsByUserAndBadge(User user, Badge badge);
}
