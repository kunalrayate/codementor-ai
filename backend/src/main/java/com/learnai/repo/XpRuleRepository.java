package com.learnai.repo;
import com.learnai.model.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface XpRuleRepository extends JpaRepository<XpRule, Long> {
  Optional<XpRule> findByCode(String code);
}
