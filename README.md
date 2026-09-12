# GRE Vocab 词汇学习分享平台

基于 Vue 3 + Spring Boot 3 的多用户词汇学习平台：PDF 词书上传（含扫描版 OCR 兜底）、课时自动切分、阅读篇章、多引擎 TTS 朗读（浏览器离线 + 服务器端 Kokoro）、多语言界面（中/英/法/德）、注册审核制与学习进度同步。

## 技术栈

- **后端**：Java 17 · Spring Boot 3.2.5 · MyBatis 3 · MySQL 8+ · JWT 认证 · 外置 Tomcat 10.1 WAR 部署（context `/gre-vocab`）
- **前端**：Vue 3 · Vite 5 · Element Plus（桌面）· Vant 4（移动端）· vue-i18n · Pinia · ECharts · KaTeX
- **TTS**：Web Speech / meSpeak（离线）/ Kokoro-82M（浏览器 ONNX 推理 + 服务器端 Node 推理双路）

## 功能一览

- 📚 **词书管理**：管理员导入公共书；用户上传自己的 PDF 词书，按每课词数自动切分课时，支持重切分与删除
- 🔍 **OCR 兜底**：PDF 文本层损坏（Word 导出字体子集缺 ToUnicode 等）时自动回退 pymupdf 渲染 + RapidOCR 离线识别
- 🔊 **多引擎 TTS**：四引擎可切换，浏览器端 Kokoro（WebGPU 优先、wasm 兜底）与服务器端 Kokoro 推理服务双路，含用量统计与配额熔断
- 👥 **注册审核制**：手机号注册 → 管理员审核（通过/拒绝/禁用），登录日志记录 IP/端口
- 🌍 **国际化**：中文 / English / Français / Deutsch，含 Vant 组件语言同步
- 📊 **学习进度**：课时完成标记、书本进度看板、每日目标与预计完成日期
- 🔒 **数据安全**：腾讯云 TTS 凭据 AES-256 加密入库、书本公私可见性隔离、图形验证码

## 快速开始

### 1. 数据库

创建 MySQL 数据库 `gre_vocab`（utf8mb4），表结构在首次启动后按 `backend/src/main/resources/scripts/` 中的 SQL 初始化。

### 2. 后端

```bash
cd backend
# 通过环境变量注入配置（或参考 application-local.example.yml 建立 application-local.yml）
export SPRING_DATASOURCE_PASSWORD=your-db-password
export JWT_SECRET=your-jwt-secret-at-least-32-bytes-long
export APP_CRYPTO_SECRET=your-crypto-root-secret-at-least-32-chars
mvn spring-boot:run
```

> ⚠️ 仓库内不含任何真实密码/密钥，`application.yml` 中的占位符仅供结构参考，运行前必须通过环境变量注入真实值。

### 3. 前端

```bash
cd frontend
npm install
npm run dev        # 开发
npm run build      # 产物 dist/ 复制到 backend/src/main/resources/static 后打包 WAR
```

### 4. 模型资源下载（可选，离线 TTS）

大体积模型不入库，需要离线 Kokoro 朗读时手动下载放到对应目录：

- `frontend/public/kokoro/Kokoro-82M-v1.0-ONNX/onnx/model_quantized.onnx`
  来源：[kokoro-onnx](https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX)（约 87MB，Apache-2.0）
- `frontend/public/kokoro-ort/`：onnxruntime-web 的 wasm 文件（`ort-wasm-simd-threaded.jsep.wasm` 等，随 `onnxruntime-web` npm 包发布）

不下载时 TTS 自动回退其它引擎（Web Speech / meSpeak，均为内置）。

### 5. OCR 兜底依赖（可选）

服务器端 OCR 兜底需要 Python 3.10+ 并安装：

```bash
pip install pymupdf rapidocr-onnxruntime
```

路径通过环境变量 `PDF_PARSER_PYTHON` 指定。

## 目录结构

```
backend/            Spring Boot 后端（controller / service / mapper / tts / auth / learning）
frontend/           Vue 3 前端（桌面 views/ + 移动端 views/m/）
docs/               设计文档
```

## 安全说明

- 所有密码、JWT 密钥、加密根密钥均通过环境变量注入，仓库只保留占位符
- 部署脚本（含服务器凭据）与本地配置文件均在 `.gitignore` 中排除
- 第三方 TTS 凭据经 AES-256-GCM 加密存库，密钥由 `APP_CRYPTO_SECRET` 派生
