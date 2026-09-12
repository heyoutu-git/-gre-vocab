package com.grevocab.gre.dto;

import lombok.Data;

import java.util.Date;

@Data
public class BookPlan {
    private Long bookId;
    private int totalWords;       // 本书总词数
    private int learnedWords;     // 已学词数（当前用户已完成课时内的词汇）
    private int remainWords;      // 剩余词数
    private int percent;          // 进度百分比 0-100
    private Integer dailyGoal;    // 每日目标词数
    private Date startDate;       // 计划开始日期
    private Date endDate;         // 计划结束日期
    private Date expectedFinishDate; // 预计完成日（无目标或已完成则为 null）
}
