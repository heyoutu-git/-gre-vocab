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
