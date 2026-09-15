package com.grevocab.gre.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grevocab.common.Result;
import com.grevocab.gre.dto.ReadingSegment;
import com.grevocab.gre.entity.Book;
import com.grevocab.gre.entity.Lesson;
import com.grevocab.gre.entity.Passage;
import com.grevocab.gre.entity.Vocabulary;
import com.grevocab.auth.mapper.UserMapper;
import com.grevocab.gre.service.GreService;
import com.grevocab.gre.service.PdfParseService;
import lombok.Data;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final GreService service;
    private final PdfParseService pdfParseService;
    private final UserMapper userMapper;
    private final com.grevocab.auth.util.PasswordEncoderUtil passwordEncoder;

    public AdminController(GreService service, PdfParseService pdfParseService, UserMapper userMapper,
                           com.grevocab.auth.util.PasswordEncoderUtil passwordEncoder) {
        this.service = service;
        this.pdfParseService = pdfParseService;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    // ---- 书本管理 ----
    @GetMapping("/books")
    public Result<List<Book>> listBooks() {
        return Result.success(service.listBooks());
    }

    @PostMapping("/books")
    public Result<Long> saveBook(@RequestBody Book b) {
        return Result.success(service.saveBook(b));
    }

    @DeleteMapping("/books/{id}")
    public Result<Void> delBook(@PathVariable Long id) {
        service.removeBook(id);
        return Result.success();
    }

    // 设为「展示书」：允许未登录用户在目录中浏览（系统设定的非公共书）
    @PutMapping("/books/{id}/browse-public")
    public Result<Void> setBrowsePublic(@PathVariable Long id, @RequestParam Integer browsePublic) {
        service.setBookBrowsePublic(id, browsePublic);
        return Result.success();
    }

    // ---- 用户搜索（书本所有者选择用） ----
    @GetMapping("/users/search")
    public Result<List<Map<String, Object>>> searchUsers(@RequestParam String keyword) {
        String k = keyword == null ? "" : keyword.trim();
        if (k.isEmpty()) return Result.success(List.of());
        List<com.grevocab.auth.entity.User> users = userMapper.searchByKeyword(k);
        List<Map<String, Object>> list = users.stream().map(u -> {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("nickname", u.getNickname());
            return m;
        }).toList();
        return Result.success(list);
    }

    // ---- 用户管理（注册审核 / 启用禁用） ----
    @GetMapping("/users")
    public Result<List<com.grevocab.auth.entity.User>> listUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        String k = (keyword == null || keyword.trim().isEmpty()) ? null : keyword.trim();
        return Result.success(userMapper.listUsers(k, status));
    }

    @PutMapping("/users/{id}/status")
    public Result<Void> setUserStatus(@PathVariable Long id, @RequestParam Integer status) {
        // 0=禁用 1=正常 2=待审核（仅注册产生，不允许手工设置） 3=已拒绝
        if (status == null || status < 0 || status > 3 || status == 2) throw new IllegalArgumentException("非法状态");
        userMapper.updateStatus(id, status);
        return Result.success();
    }

    // 重置用户密码为默认密码（123456），请提醒用户登录后自行修改
    private static final String RESET_DEFAULT_PASSWORD = "123456";

    @PutMapping("/users/{id}/reset-password")
    public Result<Void> resetUserPassword(@PathVariable Long id) {
        com.grevocab.auth.entity.User u = userMapper.findById(id);
        if (u == null) throw new IllegalArgumentException("用户不存在");
        String hash = passwordEncoder.encode(RESET_DEFAULT_PASSWORD);
        userMapper.updatePassword(id, hash);
        return Result.success();
    }

    // ---- 课时管理 ----
    @GetMapping("/lessons")
    public Result<List<Lesson>> listLessons(@RequestParam(required = false) Long bookId) {
        return Result.success(bookId == null ? service.listLessons() : service.listLessons(bookId));
    }

    @PostMapping("/lessons")
    public Result<Long> saveLesson(@RequestBody Lesson l) {
        return Result.success(service.saveLesson(l));
    }

    @DeleteMapping("/lessons/{id}")
    public Result<Void> delLesson(@PathVariable Long id) {
        service.removeLesson(id);
        return Result.success();
    }

    @PostMapping("/lessons/{id}/assign-book")
    public Result<Void> assignLessonBook(@PathVariable Long id, @RequestParam Long bookId) {
        service.assignLessonBook(id, bookId);
        return Result.success();
    }

    // ---- 词汇管理 ----
    @PostMapping("/vocabularies")
    public Result<Long> saveVocab(@RequestBody Vocabulary v) {
        return Result.success(service.saveVocabulary(v));
    }

    @DeleteMapping("/vocabularies/{id}")
    public Result<Void> delVocab(@PathVariable Long id) {
        service.removeVocabulary(id);
        return Result.success();
    }

    @PostMapping("/vocabularies/{id}/reassign")
    public Result<Void> reassign(@PathVariable Long id,
                                 @RequestParam Long lessonId,
                                 @RequestParam(required = false) Integer sortNo) {
        service.reassign(id, lessonId, sortNo);
        return Result.success();
    }

    // ---- PDF 识别 ----
    @PostMapping("/parse-pdf")
    public Result<List<Vocabulary>> parsePdf(@RequestParam("file") MultipartFile file) {
        return Result.success(pdfParseService.parse(file));
    }

    // ---- 批量导入 ----
    @PostMapping("/import")
    public Result<GreService.ImportResult> importVocabularies(@RequestBody ImportRequest req) {
        return Result.success(service.importVocabularies(req.getBatchSize(), req.getBookId(), req.getVocabularies()));
    }

    @Data
    public static class ImportRequest {
        private int batchSize = 50;
        private Long bookId;
        private List<Vocabulary> vocabularies;
    }

    // ---- 阅读书导入（英文篇章 + 中文翻译） ----
    @PostMapping("/import-reading")
    public Result<GreService.ImportResult> importReading(@RequestBody ReadingImportRequest req) {
        return Result.success(service.importReadingBook(req.getBookId(), req.getSegments()));
    }

    // ---- 阅读篇章人工对齐：读取对齐数据 ----
    @GetMapping("/lessons/{id}/alignment")
    public Result<AlignmentResponse> getAlignment(@PathVariable Long id) {
        Passage p = service.getPassageForAdmin(id);
        if (p == null) return Result.success(new AlignmentResponse());
        AlignmentResponse r = new AlignmentResponse();
        r.setEnText(p.getEnText());
        r.setZhText(p.getZhText());
        r.setHasManualAlignment(p.getHasManualAlignment() != null && p.getHasManualAlignment() == 1);
        try {
            if (p.getAlignment() != null && !p.getAlignment().isBlank()) {
                r.setRows(new ObjectMapper().readValue(p.getAlignment(), List.class));
            }
        } catch (Exception e) {
            // ignore parse error
        }
        return Result.success(r);
    }

    // ---- 阅读篇章人工对齐：保存对齐数据 ----
    @PostMapping("/lessons/{id}/alignment")
    public Result<Void> saveAlignment(@PathVariable Long id, @RequestBody AlignmentSaveRequest req) {
        String json = null;
        try {
            if (req.getRows() != null) json = new ObjectMapper().writeValueAsString(req.getRows());
        } catch (Exception e) {
            throw new IllegalArgumentException("对齐数据 JSON 序列化失败");
        }
        service.savePassageAlignment(id, json);
        return Result.success();
    }

    @Data
    public static class ReadingImportRequest {
        private Long bookId;
        private List<ReadingSegment> segments;
    }

    @Data
    public static class AlignmentResponse {
        private String enText;
        private String zhText;
        private List<Map<String, Object>> rows;
        private boolean hasManualAlignment;
    }

    @Data
    public static class AlignmentSaveRequest {
        private List<Map<String, Object>> rows;
    }
}
