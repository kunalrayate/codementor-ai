CREATE TABLE documents (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(200) NOT NULL,
  course_id BIGINT,
  uploaded_by_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL,
  FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
  FOREIGN KEY (uploaded_by_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE TABLE document_chunks (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  document_id BIGINT NOT NULL,
  content TEXT NOT NULL,
  embedding LONGTEXT NOT NULL,
  FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE
);
CREATE TABLE coding_problems (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(200) NOT NULL,
  description TEXT NOT NULL,
  starter_code TEXT,
  created_by_id BIGINT,
  FOREIGN KEY (created_by_id) REFERENCES users(id) ON DELETE SET NULL
);
CREATE TABLE test_cases (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  problem_id BIGINT NOT NULL,
  input_data TEXT,
  expected_output TEXT NOT NULL,
  FOREIGN KEY (problem_id) REFERENCES coding_problems(id) ON DELETE CASCADE
);
CREATE TABLE code_submissions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id BIGINT NOT NULL,
  problem_id BIGINT,
  source_code LONGTEXT NOT NULL,
  status VARCHAR(30) NOT NULL,
  passed_count INT NOT NULL DEFAULT 0,
  total_count INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (problem_id) REFERENCES coding_problems(id) ON DELETE SET NULL
);
