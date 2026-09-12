package com.grevocab.gre.mapper;

import com.grevocab.gre.entity.Vocabulary;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface VocabularyMapper {

    @Select("SELECT * FROM t_vocabulary WHERE lesson_id = #{lessonId} AND status = 1 ORDER BY sort_no ASC, id ASC")
    List<Vocabulary> findByLesson(Long lessonId);

    // 某本书下所有词汇（重切分课时用），按课时与序号排序
    @Select("SELECT * FROM t_vocabulary WHERE lesson_id IN (SELECT id FROM t_lesson WHERE book_id = #{bookId}) " +
            "ORDER BY lesson_id ASC, sort_no ASC, id ASC")
    List<Vocabulary> findByBook(@Param("bookId") Long bookId);

    @Select("SELECT * FROM t_vocabulary WHERE id = #{id}")
    Vocabulary findById(Long id);

    @Select("SELECT * FROM t_vocabulary WHERE status = 1 ORDER BY id ASC")
    List<Vocabulary> findAll();

    @Insert("INSERT INTO t_vocabulary(lesson_id, word, phonetic, phonetic_ipa, pos, inflection, definition, meaning_cn, example, sort_no, status) " +
            "VALUES(#{lessonId}, #{word}, #{phonetic}, #{phoneticIpa}, #{pos}, #{inflection}, #{definition}, #{meaningCn}, #{example}, #{sortNo}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Vocabulary v);

    @Update("UPDATE t_vocabulary SET lesson_id=#{lessonId}, word=#{word}, phonetic=#{phonetic}, phonetic_ipa=#{phoneticIpa}, " +
            "pos=#{pos}, inflection=#{inflection}, definition=#{definition}, meaning_cn=#{meaningCn}, example=#{example}, sort_no=#{sortNo}, status=#{status} WHERE id=#{id}")
    int update(Vocabulary v);

    @Delete("DELETE FROM t_vocabulary WHERE id=#{id}")
    int delete(Long id);

    @Delete("DELETE FROM t_vocabulary WHERE lesson_id=#{lessonId}")
    int deleteByLesson(Long lessonId);

    @Delete("DELETE FROM t_vocabulary WHERE status = 1")
    int deleteAll();

    @Delete("DELETE FROM t_vocabulary WHERE lesson_id IN (SELECT id FROM t_lesson WHERE book_id = #{bookId})")
    int deleteByBook(@Param("bookId") Long bookId);

    @Update("UPDATE t_vocabulary SET lesson_id=#{lessonId}, sort_no=#{sortNo} WHERE id=#{id}")
    int reassign(@Param("id") Long id, @Param("lessonId") Long lessonId, @Param("sortNo") Integer sortNo);
}
