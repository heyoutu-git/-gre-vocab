-- ============================================================
-- GRE 词汇应用 · 书本层迁移脚本
-- 用途：在已有 gre_vocab 数据库上新增 t_book 表与 t_lesson.book_id
-- 并自动把现有课时归入默认书本「GRE 词汇」。
-- 幂等：可重复执行。
-- ============================================================
USE gre_vocab;
SET NAMES utf8mb4;

-- 1) 书本表
CREATE TABLE IF NOT EXISTS t_book (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  title       VARCHAR(200) NOT NULL,
  description VARCHAR(255),
  sort_no     INT DEFAULT 0,
  status      TINYINT DEFAULT 1,
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2) 课时增加 book_id（幂等：列已存在则跳过；MySQL 9.x 不支持 ADD COLUMN IF NOT EXISTS）
DROP PROCEDURE IF EXISTS add_book_id_col;
DELIMITER $$
CREATE PROCEDURE add_book_id_col()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'gre_vocab' AND TABLE_NAME = 't_lesson' AND COLUMN_NAME = 'book_id'
  ) THEN
    ALTER TABLE t_lesson ADD COLUMN book_id BIGINT NULL;
  END IF;
END$$
DELIMITER ;
CALL add_book_id_col();
DROP PROCEDURE IF EXISTS add_book_id_col;

-- 3) 若默认书本不存在则插入（幂等：用 NOT EXISTS 守卫，避免重跑产生重复）
INSERT INTO t_book (title, description, sort_no, status)
SELECT 'GRE 词汇', '系统默认书本，自动收纳未归类的课时', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM t_book WHERE title = 'GRE 词汇');

-- 4) 把没有归属书本的现有课时归到默认书本
UPDATE t_lesson
SET book_id = (SELECT id FROM t_book WHERE title = 'GRE 词汇' LIMIT 1)
WHERE book_id IS NULL;

-- 5) 可选：外键（首次迁移时添加；若已存在则忽略错误）
DROP PROCEDURE IF EXISTS add_book_fk;
DELIMITER $$
CREATE PROCEDURE add_book_fk()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = 'gre_vocab'
      AND TABLE_NAME = 't_lesson'
      AND CONSTRAINT_NAME = 'fk_lesson_book'
  ) THEN
    ALTER TABLE t_lesson
      ADD CONSTRAINT fk_lesson_book
      FOREIGN KEY (book_id) REFERENCES t_book(id)
      ON DELETE SET NULL;
  END IF;
END$$
DELIMITER ;
CALL add_book_fk();
DROP PROCEDURE IF EXISTS add_book_fk;

-- ============================================================
-- 6) TTS 引擎开关
-- ============================================================
CREATE TABLE IF NOT EXISTS t_tts_engine (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  code        VARCHAR(32)  NOT NULL COMMENT '引擎标识：system/mespeak/tencent',
  name        VARCHAR(64)  NOT NULL COMMENT '显示名',
  enabled     TINYINT      DEFAULT 1 COMMENT '是否启用',
  sort_no     INT          DEFAULT 0,
  created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO t_tts_engine (code, name, enabled, sort_no) VALUES
  ('system',  '系统语音 (Web Speech)', 1, 1),
  ('mespeak', '离线语音 (meSpeak)',    1, 2),
  ('tencent', '腾讯云 TTS',            0, 3);

-- ============================================================
-- 7) TTS 服务商凭据（加密存储）
-- ============================================================
CREATE TABLE IF NOT EXISTS t_tts_provider (
  id                   BIGINT PRIMARY KEY AUTO_INCREMENT,
  engine_code          VARCHAR(32)  NOT NULL COMMENT '关联引擎',
  provider             VARCHAR(32)  NOT NULL COMMENT '服务商：tencent',
  app_id               VARCHAR(64)  COMMENT '应用ID/APPID',
  secret_id_encrypted  VARCHAR(255) COMMENT '加密后的 SecretId',
  secret_key_encrypted VARCHAR(255) COMMENT '加密后的 SecretKey',
  region               VARCHAR(32)  DEFAULT 'ap-guangzhou' COMMENT '区域',
  endpoint             VARCHAR(128) COMMENT '接入点',
  extra_json           TEXT         COMMENT '额外配置JSON',
  enabled              TINYINT      DEFAULT 1,
  sort_no              INT          DEFAULT 0,
  created_at           DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at           DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 8) TTS 用量（按天统计，用于免费额度监控与熔断）
-- ============================================================
CREATE TABLE IF NOT EXISTS t_tts_usage (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  provider       VARCHAR(32) NOT NULL,
  usage_date     DATE        NOT NULL,
  char_count     BIGINT      DEFAULT 0 COMMENT '当日调用字符数',
  request_count  INT         DEFAULT 0,
  quota          BIGINT      DEFAULT 0 COMMENT '当日限额，0 表示无限制',
  created_at     DATETIME    DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_provider_date (provider, usage_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 9) 多用户书本字段（归属 / 每课词数 / 学习计划）
--    可见性规则：user_id IS NULL 视为系统公共书（所有登录用户可见）；
--                user_id = 当前用户 视为该用户的私有书。
--    MySQL 9.x 不支持 ADD COLUMN IF NOT EXISTS，用存储过程守卫。
-- ============================================================
DROP PROCEDURE IF EXISTS add_book_user_cols;
DELIMITER $$
CREATE PROCEDURE add_book_user_cols()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'gre_vocab' AND TABLE_NAME = 't_book' AND COLUMN_NAME = 'user_id'
  ) THEN
    ALTER TABLE t_book ADD COLUMN user_id BIGINT NULL COMMENT '所有者；NULL=系统公共书';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'gre_vocab' AND TABLE_NAME = 't_book' AND COLUMN_NAME = 'is_public'
  ) THEN
    ALTER TABLE t_book ADD COLUMN is_public TINYINT DEFAULT 0 COMMENT '是否公共书标记（实际以 user_id IS NULL 判定）';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'gre_vocab' AND TABLE_NAME = 't_book' AND COLUMN_NAME = 'words_per_lesson'
  ) THEN
    ALTER TABLE t_book ADD COLUMN words_per_lesson INT DEFAULT 50 COMMENT '每课时默认单词数(导入切分粒度)';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'gre_vocab' AND TABLE_NAME = 't_book' AND COLUMN_NAME = 'plan_daily_words'
  ) THEN
    ALTER TABLE t_book ADD COLUMN plan_daily_words INT NULL COMMENT '学习计划：每日目标词数';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'gre_vocab' AND TABLE_NAME = 't_book' AND COLUMN_NAME = 'plan_start_date'
  ) THEN
    ALTER TABLE t_book ADD COLUMN plan_start_date DATE NULL COMMENT '计划开始日期';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'gre_vocab' AND TABLE_NAME = 't_book' AND COLUMN_NAME = 'plan_end_date'
  ) THEN
    ALTER TABLE t_book ADD COLUMN plan_end_date DATE NULL COMMENT '计划结束日期';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'gre_vocab' AND TABLE_NAME = 't_book' AND COLUMN_NAME = 'browse_public'
  ) THEN
    ALTER TABLE t_book ADD COLUMN browse_public TINYINT DEFAULT 0 COMMENT '是否允许未登录用户在目录中浏览（系统设定的展示书）';
  END IF;
END$$
DELIMITER ;
CALL add_book_user_cols();
DROP PROCEDURE IF EXISTS add_book_user_cols;

-- 将现有默认「GRE 词汇」标记为公共书（所有者=系统）
UPDATE t_book SET user_id = NULL, is_public = 1, words_per_lesson = 50 WHERE title = 'GRE 词汇';

-- ============================================================
-- 10) 登录日志（登录 / 注册均需记录，含成功失败与原因、IP、UA）
-- ============================================================
CREATE TABLE IF NOT EXISTS t_login_log (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id     BIGINT      NULL,
  username    VARCHAR(64) NULL,
  ip          VARCHAR(64) NULL,
  user_agent  VARCHAR(512) NULL,
  login_type  VARCHAR(16) DEFAULT 'login' COMMENT 'login / register',
  result      TINYINT     DEFAULT 1 COMMENT '1 成功 0 失败',
  fail_reason VARCHAR(255) NULL,
  created_at  DATETIME    DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 11) 阅读书支持：书本类型 + 篇章表
-- ============================================================

-- 11.1) t_book 增加 book_type（1=词汇书 2=阅读书）
DROP PROCEDURE IF EXISTS add_book_type_col;
DELIMITER $$
CREATE PROCEDURE add_book_type_col()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'gre_vocab' AND TABLE_NAME = 't_book' AND COLUMN_NAME = 'book_type'
  ) THEN
    ALTER TABLE t_book ADD COLUMN book_type TINYINT DEFAULT 1 COMMENT '1=词汇书 2=阅读书';
  END IF;
END$$
DELIMITER ;
CALL add_book_type_col();
DROP PROCEDURE IF EXISTS add_book_type_col;
-- 既有书本默认归为词汇书
UPDATE t_book SET book_type = 1 WHERE book_type IS NULL;

-- 11.2) 篇章表（阅读书每课时一段英文 + 对应中文翻译）
CREATE TABLE IF NOT EXISTS t_passage (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  lesson_id   BIGINT NOT NULL,
  seq         INT DEFAULT 1,
  en_text     MEDIUMTEXT,
  zh_text     MEDIUMTEXT,
  alignment   JSON COMMENT '中英文对齐段 [{en,zh}]',
  has_manual_alignment TINYINT DEFAULT 0 COMMENT '1=已人工调整 0=未调整',
  status      TINYINT DEFAULT 1,
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_passage_lesson (lesson_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 12) 阅读篇章：已有 t_passage 表补 alignment / has_manual_alignment
-- ============================================================
DROP PROCEDURE IF EXISTS add_passage_alignment_cols;
DELIMITER $$
CREATE PROCEDURE add_passage_alignment_cols()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'gre_vocab' AND TABLE_NAME = 't_passage' AND COLUMN_NAME = 'alignment'
  ) THEN
    ALTER TABLE t_passage ADD COLUMN alignment JSON COMMENT '中英文对齐段 [{en,zh}]';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'gre_vocab' AND TABLE_NAME = 't_passage' AND COLUMN_NAME = 'has_manual_alignment'
  ) THEN
    ALTER TABLE t_passage ADD COLUMN has_manual_alignment TINYINT DEFAULT 0 COMMENT '1=已人工调整 0=未调整';
  END IF;
END$$
DELIMITER ;
CALL add_passage_alignment_cols();
DROP PROCEDURE IF EXISTS add_passage_alignment_cols;

-- ============================================================
-- 13) 词汇中文释义（GRE 书 / 公共书显示用）
--     数据源：ECDICT 开源中英词典（CC BY-SA），按 word 匹配；
--     写入由 tools/import_meaning_cn.py 完成，本脚本只保证列存在。
-- ============================================================
DROP PROCEDURE IF EXISTS add_vocab_meaning_cn_col;
DELIMITER $$
CREATE PROCEDURE add_vocab_meaning_cn_col()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'gre_vocab' AND TABLE_NAME = 't_vocabulary' AND COLUMN_NAME = 'meaning_cn'
  ) THEN
    ALTER TABLE t_vocabulary ADD COLUMN meaning_cn VARCHAR(600) NULL COMMENT '中文释义（来源 ECDICT）';
  END IF;
END$$
DELIMITER ;
CALL add_vocab_meaning_cn_col();
DROP PROCEDURE IF EXISTS add_vocab_meaning_cn_col;

