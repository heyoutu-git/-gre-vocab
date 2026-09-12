# 多用户私有书本 + 学习计划 实施方案

> 目标：把现有「全局共享」的书本/课时改为「公共默认书 + 用户私有书」模型；用户可上传自己的书本(PDF)、设置每课单词数、制定学习计划并查看进度看板。

## 一、数据模型改造（`t_book` 增加字段）

```sql
-- 幂等：列已存在则跳过（188 用存储过程守卫）
ALTER TABLE t_book
  ADD COLUMN user_id         BIGINT      NULL    COMMENT '所有者；NULL=系统公共书',
  ADD COLUMN is_public       TINYINT     DEFAULT 0 COMMENT '是否公共书(保留字段，实际以 user_id IS NULL 判定)',
  ADD COLUMN words_per_lesson INT        DEFAULT 50 COMMENT '每课时默认单词数(导入切分粒度)',
  ADD COLUMN plan_daily_words INT        NULL    COMMENT '学习计划：每日目标词数',
  ADD COLUMN plan_start_date DATE        NULL    COMMENT '计划开始日期',
  ADD COLUMN plan_end_date   DATE        NULL    COMMENT '计划结束日期';

-- 将现有默认「GRE 词汇」标记为公共书
UPDATE t_book SET user_id = NULL, is_public = 1 WHERE title = 'GRE 词汇';
```

- 归属链：用户 → 书本(`user_id`) → 课时(`book_id`) → 词汇(`lesson_id`)。
- 现有 75 课时 / 3628 词**不动**，仅把默认书标记为公共（`user_id=NULL`），所有用户可见。
- `t_lesson` / `t_vocabulary` 结构不变（已含 `book_id` / `lesson_id`）。

## 二、学习计划（不需要独立表）

用 `t_book` 上 3 个字段（`plan_daily_words` / `plan_start_date` / `plan_end_date`）即可满足「目标+进度看板」：
- **总词数** = 该书下所有课时 `word_count` 之和。
- **已学词数** = 当前用户对「该书已完成课时」的词汇数之和（复用 `t_user_progress.lesson_id + finished`）。
- **剩余词数** = 总 − 已学。
- **预计完成日** = 计划开始日 + ceil(剩余 / 每日目标) 天（无目标则返回「未设目标」）。
- **进度百分比** = 已学 / 总。

## 三、后端改动

### 1. 查询按用户过滤
- `BookMapper.findAllVisible(uid)`：`WHERE user_id IS NULL OR user_id = #{uid}`，结果带 `mine` 标记（当前用户是否所有者）。
- `LessonMapper.findByBook(bookId)` 之前需校验该书对当前用户可见（公开或自己）。
- 公开学习接口 `/api/lessons` 之前是白名单公开？需确认 `WebConfig`：若未拦截，改为**需登录**，后端注入 `userId` 过滤（与 `/api/learning` 一致）。

### 2. 导入支持私有书（核心）
新建用户级导入入口（保留 admin 全局导入不动）：
- `POST /api/books/import`
  - 入参：`file`(PDF) + `bookId`(可选，导入到已有私有书) 或 `bookTitle`(新建私有书) + `wordsPerLesson`(每课词数，默认取该书或 50)。
  - 逻辑：解析 PDF → `barron_to_ipa` 转换 → 按 `wordsPerLesson` 切课 → 写入 `user_id=当前用户` 的书本下（幂等：清空该书旧课时重建）。
  - `userId` 从 JWT 取（复用现有 `HttpServletRequest.getAttribute("userId")` 机制）。

### 3. 书本设置接口
- `PUT /api/books/{id}`：更新 `words_per_lesson`、`plan_*` 字段（仅所有者可改，公共书仅管理员可改）。
- 新增「重新切分课时」：`POST /api/books/{id}/resplit?wordsPerLesson=30`，按新粒度重建课时（满足「课时可改」）。

### 4. 进度统计接口
- `GET /api/books/{id}/plan`：返回 `{totalWords, learnedWords, remainWords, dailyGoal, startDate, endDate, expectedFinishDate, percent}`。

## 四、前端改动

| 页面 | 改动 |
|------|------|
| 书本列表(Home/Lessons) | 分「公共书 / 我的书」两栏；显示每书词数、进度 |
| 上传导入 | 弹窗：选「新建私有书」或「我的已有书」+ 填每课词数 |
| 书本设置 | 新页：编辑每课单词数、学习计划(起止日期/每日目标)、保存 |
| 进度看板 | 新页/弹层：进度条、已学/剩余、预计完成日 |
| 学习端 | 只展示当前用户可见书本下的课时词汇 |

## 五、部署与验证
- `backend/deploy-188.bat`：`BUILD_FRONTEND=1`、`AUTO_INIT_DB=0`（手动跑 migration SQL，保护 75 课时数据）。
- 验证点：
  1. 默认「GRE 词汇」公共书对所有登录用户可见；
  2. A 用户上传的书，B 用户看不到；
  3. 上传时填的每课词数生效；
  4. 设置学习计划后看板进度/预计完成日正确；
  5. 朗读/TTS 功能不受影响（本次未改动）。
- 部署后浏览器硬刷新。

## 六、关键技术点与确认

### 6.1 `/api/lessons` 当前是公开且无 userId（必须改）
`JwtInterceptor.preHandle` 对 `/api/lessons` **完全放行**（不校验 token、也不注入 `userId`）。因此：
- 改造后：对 `/api/lessons`（学习端只读）改为**需登录并注入 userId**（与 `/api/learning` 同待遇）。
- 后端据此过滤：公开书（`user_id IS NULL`）所有人可见；私有书仅所有者可见。
- **影响**：学习端将要求登录（多用户私有化后未登录无意义）。前端学习页/路由需确保已登录态（加路由守卫或在已登录后才请求）。现有 `/api/learning` 已带 token，前端有登录上下文。

### 6.2 公共书编辑权限
公共默认书（GRE 词汇）不允许普通用户改计划/重切分，仅管理员（admin 接口）可动；私有书仅所有者可改。

### 6.3 用户上传入口
新增 `POST /api/books/import`（用户级，需登录），保留 `/api/admin/import`（admin 全局）不动。PDF 解析复用现有 `ingest_pdf.py` 的 `barron_to_ipa`。

### 6.4 数据不动
本次只加列（`ALTER TABLE t_book ...`）+ 把默认书标记为公共（`user_id=NULL`），**不删不改现有 75 课时 / 3628 词**。

## 七、待你确认的一件事
是否接受「学习端改为需登录」？
- 推荐：是（多用户私有化后未登录无数据可看，且便于按 userId 隔离）。
- 若希望「未登录也能预览公共书」：则未登录时只返回公共书，登录后追加私有书——实现稍复杂，可后续再加。
