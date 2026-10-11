CREATE TABLE xp_events (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id BIGINT NOT NULL,
  event_type VARCHAR(40) NOT NULL,
  xp INT NOT NULL,
  ref_id BIGINT,
  course_id BIGINT,
  created_at DATETIME NOT NULL,
  INDEX idx_xp_student (student_id),
  INDEX idx_xp_course (course_id),
  FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE TABLE badges (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(40) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(255)
);
CREATE TABLE user_badges (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  badge_id BIGINT NOT NULL,
  awarded_at DATETIME NOT NULL,
  UNIQUE (user_id, badge_id),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (badge_id) REFERENCES badges(id) ON DELETE CASCADE
);
