package com.learnai.repo;
import com.learnai.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.*;

public interface AiMessageRepository extends JpaRepository<AiMessage, Long> {
  List<AiMessage> findByConversationOrderByIdAsc(AiConversation c);
  List<AiMessage> findByConversationOrderByIdDesc(AiConversation c, Pageable pageable);
}
