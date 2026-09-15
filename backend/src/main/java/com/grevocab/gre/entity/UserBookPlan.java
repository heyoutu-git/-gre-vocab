package com.grevocab.gre.entity;

import lombok.Data;

import java.util.Date;

/**
 * 用户 × 书本 维度的学习计划（每人每书一行，互不影响）。
 * 书级(t_book)上的 plan_* 字段仅作存量兼容与书主默认值。
 */
@Data
public class UserBookPlan {
    private Long bookId;
    private Long userId;
    private Integer planDailyWords; // 每日目标词数
    private Date planStartDate;     // 计划开始日期
    private Date planEndDate;       // 计划结束日期
}
