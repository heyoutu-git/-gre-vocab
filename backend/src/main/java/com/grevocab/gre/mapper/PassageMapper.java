package com.grevocab.gre.mapper;

import com.grevocab.gre.entity.Passage;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface PassageMapper {

    @Select("SELECT * FROM t_passage WHERE lesson_id = #{lessonId} ORDER BY seq ASC")
    List<Passage> findByLesson(Long lessonId);

    @Select("SELECT * FROM t_passage WHERE id = #{id}")
    Passage findById(Long id);

    @Insert("INSERT INTO t_passage(lesson_id, seq, en_text, zh_text, alignment, has_manual_alignment, status, created_at, updated_at) " +
            "VALUES(#{lessonId}, #{seq}, #{enText}, #{zhText}, #{alignment}, #{hasManualAlignment}, #{status}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Passage p);

    @Delete("DELETE FROM t_passage WHERE lesson_id = #{lessonId}")
    int deleteByLesson(Long lessonId);

    @Delete("DELETE t_passage FROM t_passage INNER JOIN t_lesson l ON t_passage.lesson_id = l.id " +
            "WHERE l.book_id = #{bookId}")
    int deleteByBook(Long bookId);

    @Update("UPDATE t_passage SET zh_text = #{zhText}, updated_at = NOW() WHERE id = #{id}")
    int updateZh(@Param("id") Long id, @Param("zhText") String zhText);

    @Update("UPDATE t_passage SET alignment = #{alignment}, has_manual_alignment = #{hasManualAlignment}, updated_at = NOW() WHERE id = #{id}")
    int updateAlignment(@Param("id") Long id,
                        @Param("alignment") String alignment,
                        @Param("hasManualAlignment") Integer hasManualAlignment);
}
