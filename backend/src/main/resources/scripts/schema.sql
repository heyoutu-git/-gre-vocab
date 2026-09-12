-- ============================================================
-- GRE 词汇知识分享应用 · 数据库初始化
-- MySQL 8.0+  /  utf8mb4
-- 执行：mysql -uroot -p < schema.sql
-- 默认管理员账号由应用 DataInitializer 创建：admin / admin123
-- ============================================================

CREATE DATABASE IF NOT EXISTS gre_vocab
  DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_general_ci;
USE gre_vocab;

-- ---------- 用户 / 角色 / 权限 (RBAC) ----------
CREATE TABLE IF NOT EXISTS t_user (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  username      VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  nickname      VARCHAR(64),
  email         VARCHAR(128),
  phone         VARCHAR(20),
  status        TINYINT DEFAULT 1,
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_role (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  code        VARCHAR(50) NOT NULL UNIQUE,
  name        VARCHAR(50) NOT NULL,
  description VARCHAR(200)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_permission (
  id      BIGINT PRIMARY KEY AUTO_INCREMENT,
  code    VARCHAR(80) NOT NULL UNIQUE,
  name    VARCHAR(80) NOT NULL,
  module  VARCHAR(50)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_role_permission (
  role_id       BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  PRIMARY KEY (role_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_user_role (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 书本 ----------
CREATE TABLE IF NOT EXISTS t_book (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  title       VARCHAR(200) NOT NULL,
  description VARCHAR(255),
  sort_no     INT DEFAULT 0,
  status      TINYINT DEFAULT 1,
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- GRE 课时 ----------
CREATE TABLE IF NOT EXISTS t_lesson (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  book_id     BIGINT,
  title       VARCHAR(200) NOT NULL,
  sort_no     INT,
  description VARCHAR(255),
  word_count  INT DEFAULT 0,
  status      TINYINT DEFAULT 1,
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_lesson_book FOREIGN KEY (book_id) REFERENCES t_book(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- GRE 词汇（从 PDF 自动识别）----------
CREATE TABLE IF NOT EXISTS t_vocabulary (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  lesson_id    BIGINT NOT NULL,
  word         VARCHAR(80)  NOT NULL,
  phonetic     VARCHAR(80),            -- 源 Barron's ASCII 音标
  phonetic_ipa VARCHAR(120),           -- 转换后的标准 IPA
  pos          VARCHAR(20),            -- v/adj/n/adv/...
  inflection   VARCHAR(160),           -- 动词/形容词变形（可选）
  definition   VARCHAR(600),           -- 英文释义
  example      TEXT,                   -- 英文例句
  sort_no      INT,
  status       TINYINT DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 索引（MySQL 不支持 CREATE INDEX IF NOT EXISTS，用存储过程守卫，可重复执行）
DROP PROCEDURE IF EXISTS add_vocab_idx;
DELIMITER $$
CREATE PROCEDURE add_vocab_idx()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'gre_vocab' AND TABLE_NAME = 't_vocabulary' AND INDEX_NAME = 'idx_vocab_lesson'
  ) THEN
    CREATE INDEX idx_vocab_lesson ON t_vocabulary(lesson_id);
  END IF;
END$$
DELIMITER ;
CALL add_vocab_idx();
DROP PROCEDURE IF EXISTS add_vocab_idx;

-- ---------- 学习行为（收藏 / 笔记 / 进度 / 日志）----------
CREATE TABLE IF NOT EXISTS t_user_progress (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id    BIGINT NOT NULL,
  lesson_id  BIGINT NOT NULL,
  finished   TINYINT DEFAULT 0,
  finished_at DATETIME,
  UNIQUE KEY uk_user_lesson (user_id, lesson_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_favorite (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id    BIGINT NOT NULL,
  res_type   VARCHAR(30),
  res_id     BIGINT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_note (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id    BIGINT NOT NULL,
  res_type   VARCHAR(30),
  res_id     BIGINT,
  content    TEXT,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_operation_log (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id    BIGINT,
  action     VARCHAR(80),
  target     VARCHAR(120),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
