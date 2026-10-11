-- NULL or 0 = unlimited attempts, 1 = single attempt per student, N = at most N attempts
ALTER TABLE quizzes ADD COLUMN max_attempts INT NULL;
