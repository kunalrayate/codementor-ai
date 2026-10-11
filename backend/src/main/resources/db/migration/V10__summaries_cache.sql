CREATE TABLE summaries (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  lesson_id BIGINT NOT NULL,
  kind VARCHAR(20) NOT NULL,
  level VARCHAR(20) NOT NULL,
  body LONGTEXT NOT NULL,
  created_at DATETIME NOT NULL,
  UNIQUE (lesson_id, kind, level),
  FOREIGN KEY (lesson_id) REFERENCES lessons(id) ON DELETE CASCADE
);
