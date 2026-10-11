package com.learnai.repo;
import com.learnai.model.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface AppSettingRepository extends JpaRepository<AppSetting, Long> {
  Optional<AppSetting> findBySkey(String skey);
}
