package com.grevocab.gre.controller;

import com.grevocab.common.BizException;
import com.grevocab.common.Result;
import com.grevocab.gre.dto.BookPlan;
import com.grevocab.gre.entity.Book;
import com.grevocab.gre.entity.Vocabulary;
import com.grevocab.gre.service.GreService;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private final GreService service;

    public BookController(GreService service) {
        this.service = service;
    }

    private Long uid(HttpServletRequest r) {
        Long uid = (Long) r.getAttribute("userId");
        if (uid == null) throw new BizException(401, "请先登录");
        return uid;
    }

    // 当前用户可见的书本（公共书 + 展示书 + 我的书），带 mine 标记；匿名可见公共书+展示书
    @GetMapping
    public Result<List<Book>> list(HttpServletRequest r) {
        return Result.success(service.listBooks((Long) r.getAttribute("userId")));
    }

    @GetMapping("/{id}")
    public Result<Book> get(@PathVariable Long id, HttpServletRequest r) {
        return Result.success(service.getBook(id, (Long) r.getAttribute("userId")));
    }

    // 学习计划进度看板
    @GetMapping("/{id}/plan")
    public Result<BookPlan> plan(@PathVariable Long id, HttpServletRequest r) {
        return Result.success(service.getBookPlan(id, (Long) r.getAttribute("userId")));
    }

    // 用户级 PDF 导入：导入到已有私有书(bookId) 或新建私有书(bookTitle)
    @PostMapping("/import")
    public Result<GreService.ImportResult> importBook(HttpServletRequest r,
                                                     @RequestParam("file") MultipartFile file,
                                                     @RequestParam(required = false) Long bookId,
                                                     @RequestParam(required = false) String bookTitle,
                                                     @RequestParam(required = false) Integer wordsPerLesson) {
        return Result.success(service.importUserBook(uid(r), file, bookId, bookTitle, wordsPerLesson));
    }

    // 更新每课词数 + 学习计划
    @PutMapping("/{id}")
    public Result<Void> updatePlan(@PathVariable Long id, HttpServletRequest r,
                                  @RequestBody BookPlanUpdate req) {
        service.updateBookPlan(id, uid(r), req.getWordsPerLesson(),
                req.getPlanDailyWords(), req.getPlanStartDate(), req.getPlanEndDate());
        return Result.success();
    }

    // 按新每课词数重切分课时（保留词汇）
    @PostMapping("/{id}/resplit")
    public Result<GreService.ImportResult> resplit(@PathVariable Long id, HttpServletRequest r,
                                                  @RequestParam(required = false) Integer wordsPerLesson) {
        return Result.success(service.resplitBook(id, uid(r), wordsPerLesson));
    }

    // 删除自己的书本
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id, HttpServletRequest r) {
        service.removeUserBook(id, uid(r));
        return Result.success();
    }

    @Data
    public static class BookPlanUpdate {
        private Integer wordsPerLesson;
        private Integer planDailyWords;

        @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
        private Date planStartDate;

        @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
        private Date planEndDate;
    }
}
