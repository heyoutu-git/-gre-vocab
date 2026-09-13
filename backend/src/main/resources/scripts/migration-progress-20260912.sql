USE gre_vocab;
DELIMITER $$
DROP PROCEDURE IF EXISTS up_add_cols$$
CREATE PROCEDURE up_add_cols()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA='gre_vocab' AND TABLE_NAME='t_user_progress' AND COLUMN_NAME='last_word_index') THEN
    ALTER TABLE t_user_progress ADD COLUMN last_word_index INT NOT NULL DEFAULT 0;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA='gre_vocab' AND TABLE_NAME='t_user_progress' AND COLUMN_NAME='last_visit_at') THEN
    ALTER TABLE t_user_progress ADD COLUMN last_visit_at DATETIME NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA='gre_vocab' AND TABLE_NAME='t_user_progress' AND COLUMN_NAME='visit_count') THEN
    ALTER TABLE t_user_progress ADD COLUMN visit_count INT NOT NULL DEFAULT 0;
  END IF;
END$$
CALL up_add_cols()$$
DROP PROCEDURE up_add_cols$$
DELIMITER ;
SHOW COLUMNS FROM t_user_progress;
