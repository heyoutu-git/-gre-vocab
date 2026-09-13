package com.grevocab.learning.controller;

import com.grevocab.common.Result;
import com.grevocab.learning.entity.Favorite;
import com.grevocab.learning.entity.Note;
import com.grevocab.learning.service.LearningService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/learning")
public class LearningController {

    private final LearningService service;

    public LearningController(LearningService service) {
        this.service = service;
    }

    private Long uid(HttpServletRequest r) { return (Long) r.getAttribute("userId"); }

    @PostMapping("/progress")
    public Result<Void> markProgress(HttpServletRequest r, @RequestBody Map<String, Long> body) {
        service.markFinished(uid(r), body.get("lessonId"));
        return Result.success();
    }

    // 查询单课时是否已标记完成（学习页按钮回显）
    @GetMapping("/progress")
    public Result<Boolean> getProgress(HttpServletRequest r, @RequestParam Long lessonId) {
        return Result.success(service.isFinished(uid(r), lessonId));
    }

    @GetMapping("/progress/count")
    public Result<Integer> progressCount(HttpServletRequest r) {
        return Result.success(service.finishedCount(uid(r)));
    }

    // 学习位置上报（自动进度，前端节流调用）：{ lessonId, wordIndex }
    @PostMapping("/progress/visit")
    public Result<Void> recordVisit(HttpServletRequest r, @RequestBody Map<String, Object> body) {
        Long lessonId = Long.valueOf(String.valueOf(body.get("lessonId")));
        Integer wordIndex = body.get("wordIndex") == null ? 0
                : Integer.valueOf(String.valueOf(body.get("wordIndex")));
        service.recordVisit(uid(r), lessonId, wordIndex);
        return Result.success();
    }

    // 最近学的未完成一课（无记录返回 null），供「继续学习」提示
    @GetMapping("/progress/resume")
    public Result<com.grevocab.learning.entity.UserProgress> resume(HttpServletRequest r) {
        return Result.success(service.resumeLesson(uid(r)));
    }

    // 单课时进度明细（含 last_word_index，供进入课时页的「继续上次」提示）
    @GetMapping("/progress/detail")
    public Result<com.grevocab.learning.entity.UserProgress> progressDetail(
            HttpServletRequest r, @RequestParam Long lessonId) {
        return Result.success(service.progressDetail(uid(r), lessonId));
    }

    @PostMapping("/favorite")
    public Result<Void> addFav(HttpServletRequest r, @RequestBody Favorite f) {
        service.addFavorite(uid(r), f.getResType(), f.getResId());
        return Result.success();
    }

    @DeleteMapping("/favorite")
    public Result<Void> delFav(HttpServletRequest r,
                              @RequestParam String resType, @RequestParam Long resId) {
        service.removeFavorite(uid(r), resType, resId);
        return Result.success();
    }

    @GetMapping("/favorites")
    public Result<List<Favorite>> favorites(HttpServletRequest r) {
        return Result.success(service.listFavorites(uid(r)));
    }

    @PostMapping("/note")
    public Result<Void> saveNote(HttpServletRequest r, @RequestBody Map<String, Object> body) {
        service.saveNote(uid(r),
                (String) body.get("resType"),
                Long.valueOf(body.get("resId").toString()),
                (String) body.get("content"));
        return Result.success();
    }

    @GetMapping("/note")
    public Result<Note> getNote(HttpServletRequest r,
                                @RequestParam String resType, @RequestParam Long resId) {
        return Result.success(service.getNote(uid(r), resType, resId));
    }
}
