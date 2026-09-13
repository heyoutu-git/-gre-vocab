package com.grevocab.learning.service;

import com.grevocab.learning.entity.Favorite;
import com.grevocab.learning.entity.Note;
import com.grevocab.learning.mapper.LearningMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LearningService {

    private final LearningMapper mapper;

    public LearningService(LearningMapper mapper) {
        this.mapper = mapper;
    }

    public void markFinished(Long userId, Long lessonId) {
        mapper.markFinished(userId, lessonId);
    }

    public boolean isFinished(Long userId, Long lessonId) {
        com.grevocab.learning.entity.UserProgress p = mapper.findProgress(userId, lessonId);
        return p != null && p.getFinished() != null && p.getFinished() == 1;
    }

    public int finishedCount(Long userId) {
        return mapper.finishedCount(userId);
    }

    // 学习位置上报（自动进度）：登录后由前端节流调用
    public void recordVisit(Long userId, Long lessonId, Integer wordIndex) {
        mapper.upsertVisit(userId, lessonId, wordIndex == null ? 0 : wordIndex);
    }

    // 最近学的未完成一课（无记录返回 null）
    public com.grevocab.learning.entity.UserProgress resumeLesson(Long userId) {
        return mapper.findResume(userId);
    }

    // 单课时进度明细（含 last_word_index，供「继续上次学习」提示）
    public com.grevocab.learning.entity.UserProgress progressDetail(Long userId, Long lessonId) {
        return mapper.findProgress(userId, lessonId);
    }

    public void addFavorite(Long userId, String resType, Long resId) {
        mapper.addFavorite(new Favorite() {{
            setUserId(userId); setResType(resType); setResId(resId);
        }});
    }

    public void removeFavorite(Long userId, String resType, Long resId) {
        mapper.removeFavorite(userId, resType, resId);
    }

    public List<Favorite> listFavorites(Long userId) {
        return mapper.listFavorites(userId);
    }

    public void saveNote(Long userId, String resType, Long resId, String content) {
        Note exist = mapper.findNote(userId, resType, resId);
        if (exist == null) {
            Note n = new Note();
            n.setUserId(userId); n.setResType(resType); n.setResId(resId); n.setContent(content);
            mapper.insertNote(n);
        } else {
            exist.setContent(content);
            mapper.updateNote(exist);
        }
    }

    public Note getNote(Long userId, String resType, Long resId) {
        return mapper.findNote(userId, resType, resId);
    }
}
