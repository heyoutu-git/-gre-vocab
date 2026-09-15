package com.grevocab.gre.mapper;

import com.grevocab.gre.entity.UserBookPlan;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserBookPlanMapper {

    @Select("SELECT * FROM t_user_book_plan WHERE book_id = #{bookId} AND user_id = #{userId}")
    UserBookPlan findByBookAndUser(@Param("bookId") Long bookId, @Param("userId") Long userId);

    // 用户级计划 upsert：登录用户对任何可见书都可写自己的计划行
    @Insert("INSERT INTO t_user_book_plan(book_id, user_id, plan_daily_words, plan_start_date, plan_end_date, updated_at) " +
            "VALUES(#{bookId}, #{userId}, #{planDailyWords}, #{planStartDate}, #{planEndDate}, NOW()) " +
            "ON DUPLICATE KEY UPDATE plan_daily_words = VALUES(plan_daily_words), " +
            "plan_start_date = VALUES(plan_start_date), plan_end_date = VALUES(plan_end_date), updated_at = NOW()")
    int upsert(UserBookPlan p);

    // 删除书时清理相关计划行
    @Delete("DELETE FROM t_user_book_plan WHERE book_id = #{bookId}")
    int deleteByBook(@Param("bookId") Long bookId);
}
