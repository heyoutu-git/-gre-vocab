package com.grevocab.gre.mapper;

import com.grevocab.gre.entity.Lesson;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface LessonMapper {

    @Select("SELECT * FROM t_lesson WHERE status = 1 ORDER BY sort_no ASC, id ASC")
    List<Lesson> findAll();

    @Select("<script>SELECT * FROM t_lesson WHERE status = 1 " +
            "<if test=\"bookId != null\"> AND book_id = #{bookId} </if>" +
            "ORDER BY sort_no ASC, id ASC</script>")
    List<Lesson> findByBook(@Param("bookId") Long bookId);

    @Select("SELECT * FROM t_lesson WHERE id = #{id}")
    Lesson findById(Long id);

    // 仅返回用户可见书本下的课时（bookIds 为空时返回空集合）
    @Select("<script>SELECT * FROM t_lesson WHERE status = 1 " +
            "AND book_id IN " +
            "<foreach item='bid' collection='bookIds' open='(' separator=',' close=')'>(#{bid})</foreach> " +
            "ORDER BY sort_no ASC, id ASC</script>")
    List<Lesson> findByVisibleBooks(@Param("bookIds") List<Long> bookIds);

    @Select("SELECT COALESCE(SUM(word_count),0) FROM t_lesson WHERE book_id=#{bookId} AND status=1")
    int sumWordCountByBook(@Param("bookId") Long bookId);

    // 本书中已被当前用户标记完成的课时所包含的词汇数
    @Select("SELECT COUNT(1) FROM t_vocabulary v " +
            "JOIN t_lesson l ON v.lesson_id = l.id " +
            "JOIN t_user_progress p ON p.lesson_id = l.id AND p.user_id = #{uid} AND p.finished = 1 " +
            "WHERE l.book_id = #{bookId}")
    int countLearnedVocab(@Param("bookId") Long bookId, @Param("uid") Long uid);

    @Insert("INSERT INTO t_lesson(book_id, title, sort_no, description, word_count, status, created_at, updated_at) " +
            "VALUES(#{bookId}, #{title}, #{sortNo}, #{description}, #{wordCount}, #{status}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Lesson l);

    @Update("UPDATE t_lesson SET book_id=#{bookId}, title=#{title}, sort_no=#{sortNo}, description=#{description}, " +
            "word_count=#{wordCount}, status=#{status}, updated_at=NOW() WHERE id=#{id}")
    int update(Lesson l);

    @Delete("DELETE FROM t_lesson WHERE id=#{id}")
    int delete(Long id);

    @Update("UPDATE t_lesson SET word_count=#{wordCount}, updated_at=NOW() WHERE id=#{id}")
    int updateWordCount(@Param("id") Long id, @Param("wordCount") int wordCount);

    @Delete("DELETE FROM t_lesson WHERE status = 1")
    int deleteAll();

    @Delete("DELETE FROM t_lesson WHERE book_id = #{bookId}")
    int deleteByBook(@Param("bookId") Long bookId);

    @Update("UPDATE t_lesson SET book_id=#{bookId}, updated_at=NOW() WHERE id=#{id}")
    int assignBook(@Param("id") Long id, @Param("bookId") Long bookId);

    @Update("UPDATE t_lesson SET book_id=#{bookId}, updated_at=NOW() WHERE book_id IS NULL")
    int assignBookToDefault(@Param("bookId") Long bookId);
}
