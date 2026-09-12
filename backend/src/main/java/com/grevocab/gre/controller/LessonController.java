package com.grevocab.gre.controller;

import com.grevocab.common.Result;
import com.grevocab.gre.entity.Book;
import com.grevocab.gre.entity.Lesson;
import com.grevocab.gre.entity.Passage;
import com.grevocab.gre.entity.Vocabulary;
import com.grevocab.gre.service.GreService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lessons")
public class LessonController {

    private final GreService service;

    public LessonController(GreService service) {
        this.service = service;
    }

    private Long uid(HttpServletRequest r) {
        return (Long) r.getAttribute("userId");
    }

    @GetMapping
    public Result<List<Lesson>> list(@RequestParam(required = false) Long bookId, HttpServletRequest r) {
        Long uid = uid(r);
        return Result.success(bookId == null ? service.listVisibleLessons(uid) : service.listLessonsOfBook(bookId, uid));
    }

    @GetMapping("/{id}")
    public Result<Lesson> get(@PathVariable Long id, HttpServletRequest r) {
        return Result.success(service.getLesson(id, uid(r)));
    }

    @GetMapping("/{id}/vocabularies")
    public Result<List<Vocabulary>> vocabularies(@PathVariable Long id, HttpServletRequest r) {
        return Result.success(service.listVocabularies(id, uid(r)));
    }

    // 阅读书：取某课时的英文篇章 + 中文翻译（学习需登录）
    @GetMapping("/{id}/passage")
    public Result<Passage> passage(@PathVariable Long id, HttpServletRequest r) {
        return Result.success(service.getPassage(id, uid(r)));
    }

    // 当前用户可见的书本列表（学习端用，带 mine 标记）
    @GetMapping("/books")
    public Result<List<Book>> listBooks(HttpServletRequest r) {
        return Result.success(service.listBooks(uid(r)));
    }
}
