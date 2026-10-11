package com.learnai.repo;
import com.learnai.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.*;

public interface BadgeRepository extends JpaRepository<Badge, Long> {
  Optional<Badge> findByCode(String code);
  List<Badge> findByMinXpNotNull();
}
