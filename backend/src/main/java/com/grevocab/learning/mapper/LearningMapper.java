package com.grevocab.learning.mapper;

import com.grevocab.learning.entity.Favorite;
import com.grevocab.learning.entity.Note;
import com.grevocab.learning.entity.UserProgress;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface LearningMapper {

    @Select("SELECT * FROM t_user_progress WHERE user_id=#{userId} AND lesson_id=#{lessonId}")
    UserProgress findProgress(@Param("userId") Long userId, @Param("lessonId") Long lessonId);

    @Insert("INSERT INTO t_user_progress(user_id, lesson_id, finished, finished_at) " +
            "VALUES(#{userId}, #{lessonId}, 1, NOW()) " +
            "ON DUPLICATE KEY UPDATE finished=1, finished_at=NOW()")
    int markFinished(@Param("userId") Long userId, @Param("lessonId") Long lessonId);

    @Select("SELECT COUNT(1) FROM t_user_progress WHERE user_id=#{userId} AND finished=1")
    int finishedCount(Long userId);

    // 学习位置上报：upsert 更新最近位置/时间/进入次数（不影响 finished）。
    // wordIndex=-1 表示仅记录一次进入（保留已有位置不覆盖）
    @Insert("INSERT INTO t_user_progress(user_id, lesson_id, last_word_index, last_visit_at, visit_count) " +
            "VALUES(#{userId}, #{lessonId}, GREATEST(#{wordIndex}, 0), NOW(), 1) " +
            "ON DUPLICATE KEY UPDATE last_visit_at=NOW(), visit_count=visit_count+1, " +
            "last_word_index=IF(#{wordIndex} >= 0, #{wordIndex}, last_word_index)")
    int upsertVisit(@Param("userId") Long userId, @Param("lessonId") Long lessonId, @Param("wordIndex") Integer wordIndex);

    // 最近学的未完成一课（含书本/标题，供「继续学习」入口跳转）
    @Select("SELECT p.*, l.book_id AS bookId, l.title AS lessonTitle FROM t_user_progress p " +
            "JOIN t_lesson l ON l.id=p.lesson_id " +
            "WHERE p.user_id=#{userId} AND p.finished=0 AND p.last_visit_at IS NOT NULL " +
            "ORDER BY p.last_visit_at DESC LIMIT 1")
    UserProgress findResume(Long userId);

    @Insert("INSERT INTO t_favorite(user_id, res_type, res_id, created_at) " +
            "VALUES(#{userId}, #{resType}, #{resId}, NOW())")
    int addFavorite(Favorite f);

    @Delete("DELETE FROM t_favorite WHERE user_id=#{userId} AND res_type=#{resType} AND res_id=#{resId}")
    int removeFavorite(@Param("userId") Long userId, @Param("resType") String resType, @Param("resId") Long resId);

    @Select("SELECT * FROM t_favorite WHERE user_id=#{userId} ORDER BY id DESC")
    List<Favorite> listFavorites(Long userId);

    @Select("SELECT * FROM t_note WHERE user_id=#{userId} AND res_type=#{resType} AND res_id=#{resId} LIMIT 1")
    Note findNote(@Param("userId") Long userId, @Param("resType") String resType, @Param("resId") Long resId);

    @Insert("INSERT INTO t_note(user_id, res_type, res_id, content, updated_at) " +
            "VALUES(#{userId}, #{resType}, #{resId}, #{content}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertNote(Note n);

    @Update("UPDATE t_note SET content=#{content}, updated_at=NOW() WHERE id=#{id}")
    int updateNote(Note n);
}
