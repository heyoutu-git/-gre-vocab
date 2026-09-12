package com.grevocab.gre.mapper;

import com.grevocab.gre.entity.Book;
import org.apache.ibatis.annotations.*;

import java.util.Date;
import java.util.List;

@Mapper
public interface BookMapper {

    // 全部书本（后台管理 / 启动迁移用），连带所有者用户名
    @Select("SELECT b.*, u.username AS owner_name FROM t_book b LEFT JOIN t_user u ON b.user_id = u.id " +
            "WHERE b.status = 1 ORDER BY b.sort_no ASC, b.id ASC")
    List<Book> findAll();

    // 可见书本（已登录非管理员）：公共书(is_public=1) + 当前用户的私有书
    // 展示书(browse_public=1)仅匿名目录预览可见，普通用户登录后不可见
    @Select("SELECT * FROM t_book WHERE status = 1 " +
            "AND (is_public = 1 OR user_id = #{uid}) ORDER BY sort_no ASC, id ASC")
    List<Book> findAllVisible(@Param("uid") Long uid);

    // 匿名目录可见书本：公共书 + 展示书
    @Select("SELECT * FROM t_book WHERE status = 1 " +
            "AND (is_public = 1 OR browse_public = 1) ORDER BY sort_no ASC, id ASC")
    List<Book> findCatalogVisible();

    @Select("SELECT * FROM t_book WHERE id = #{id}")
    Book findById(Long id);

    @Select("SELECT COUNT(1) FROM t_book WHERE user_id = #{uid}")
    int countOwned(@Param("uid") Long uid);

    @Insert("INSERT INTO t_book(title, description, sort_no, status, user_id, is_public, browse_public, words_per_lesson, book_type, created_at, updated_at) " +
            "VALUES(#{title}, #{description}, #{sortNo}, #{status}, #{userId}, #{isPublic}, #{browsePublic}, #{wordsPerLesson}, #{bookType}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Book b);

    // 管理员设定「展示书」（未登录可见）
    @Update("UPDATE t_book SET browse_public = #{flag}, updated_at = NOW() WHERE id = #{id}")
    int updateBrowsePublic(@Param("id") Long id, @Param("flag") Integer flag);

    // 后台管理用：更新基础字段 + 公开范围（公共/私有/展示）
    // book_type 为 null 时保留原值，避免老数据/未设置书本被误改为词汇书
    @Update("UPDATE t_book SET title=#{title}, description=#{description}, sort_no=#{sortNo}, " +
            "status=#{status}, book_type=COALESCE(#{bookType}, book_type), " +
            "is_public=#{isPublic}, browse_public=#{browsePublic}, user_id=#{userId}, updated_at=NOW() WHERE id=#{id}")
    int update(Book b);

    // 学习计划 / 每课词数更新（用户级）
    @Update("UPDATE t_book SET words_per_lesson=#{wordsPerLesson}, plan_daily_words=#{planDailyWords}, " +
            "plan_start_date=#{planStartDate}, plan_end_date=#{planEndDate}, updated_at=NOW() WHERE id=#{id}")
    int updatePlan(@Param("id") Long id,
                   @Param("wordsPerLesson") Integer wordsPerLesson,
                   @Param("planDailyWords") Integer planDailyWords,
                   @Param("planStartDate") Date planStartDate,
                   @Param("planEndDate") Date planEndDate);

    @Delete("DELETE FROM t_book WHERE id=#{id}")
    int delete(Long id);
}
