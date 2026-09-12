package com.grevocab.gre.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grevocab.common.BizException;
import com.grevocab.gre.entity.Vocabulary;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * PDF 词汇识别服务：接收 PDF 文件，调用 tools/ingest_pdf.py 解析，返回结构化词汇列表。
 * 脚本随 WAR 打包在 classpath:tools/ingest_pdf.py，运行时解压到临时目录执行，
 * 不依赖外部文件路径；仅要求服务器 Python 环境已安装 pymupdf。
 */
@Service
public class PdfParseService {

    private static final String SCRIPT_NAME = "ingest_pdf.py";
    private static final String SCRIPT_CLASSPATH = "tools/" + SCRIPT_NAME;
    private static final String OCR_SCRIPT_NAME = "ocr_parse.py";
    private static final String OCR_SCRIPT_CLASSPATH = "tools/" + OCR_SCRIPT_NAME;

    @Value("${app.pdf-parser.python-path:python}")
    private String pythonPath;

    @Value("${app.pdf-parser.timeout-seconds:120}")
    private int timeoutSeconds;

    // OCR 兜底更慢（逐页渲染+推理），单独放宽超时
    @Value("${app.pdf-parser.ocr-timeout-seconds:600}")
    private int ocrTimeoutSeconds;

    private File scriptFile;
    private File ocrScriptFile;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() throws IOException {
        scriptFile = extractScript(SCRIPT_CLASSPATH, SCRIPT_NAME);
        ocrScriptFile = extractScript(OCR_SCRIPT_CLASSPATH, OCR_SCRIPT_NAME);
    }

    private File extractScript(String classpath, String name) throws IOException {
        ClassPathResource res = new ClassPathResource(classpath);
        if (!res.exists()) {
            throw new IllegalStateException("classpath 中找不到 " + classpath);
        }
        Path tempDir = Files.createTempDirectory("grevocab-pdf-parser-");
        File f = new File(tempDir.toFile(), name);
        try (InputStream in = res.getInputStream()) {
            Files.copy(in, f.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        f.setExecutable(true);
        return f;
    }

    public List<Vocabulary> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "请先选择 PDF 文件");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!original.endsWith(".pdf")) {
            throw new BizException(400, "仅支持 .pdf 文件");
        }

        File pdf = null;
        File jsonOut = null;
        try {
            pdf = File.createTempFile("grevocab-upload-", ".pdf");
            jsonOut = File.createTempFile("grevocab-parsed-", ".json");
            file.transferTo(pdf);

            // 第一优先：文本层解析（快）
            List<Vocabulary> items;
            try {
                items = runScript(scriptFile, pdf, jsonOut, timeoutSeconds);
            } catch (BizException e) {
                items = List.of();
            }
            // 兜底：文本层为乱码/空（如 Word 导出 CJK PDF 字体子集缺 ToUnicode）时走 OCR
            if (items == null || items.size() < 5) {
                items = runScript(ocrScriptFile, pdf, jsonOut, ocrTimeoutSeconds);
            }
            return items;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "PDF 识别异常：" + e.getMessage());
        } finally {
            if (pdf != null && pdf.exists()) pdf.delete();
            if (jsonOut != null && jsonOut.exists()) jsonOut.delete();
        }
    }

    private List<Vocabulary> runScript(File script, File pdf, File jsonOut, int timeoutSec) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                pythonPath,
                script.getAbsolutePath(),
                pdf.getAbsolutePath(),
                "--out",
                jsonOut.getAbsolutePath()
        );
        pb.redirectErrorStream(true);
        // 强制 Python 以 UTF-8 读写 stdin/stdout/stderr，避免 Windows 控制台 GBK 编码导致中文乱码
        pb.environment().put("PYTHONIOENCODING", "utf-8");
        pb.environment().put("PYTHONUTF8", "1");
        Process process = pb.start();

        StringBuilder logs = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logs.append(line).append("\n");
            }
        }

        boolean finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new BizException(500, "PDF 识别超时，请检查文件是否过大或 Python 环境异常");
        }
        if (process.exitValue() != 0) {
            throw new BizException(500, "PDF 识别失败：" + logs.toString().trim());
        }
        if (!jsonOut.exists() || jsonOut.length() == 0) {
            throw new BizException(500, "PDF 识别未生成数据文件");
        }
        return objectMapper.readValue(jsonOut, new TypeReference<List<Vocabulary>>() {});
    }
}
