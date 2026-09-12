package com.grevocab.gre.entity;

import lombok.Data;

import java.util.Date;

@Data
public class Book {
    private Long id;
    private String title;
    private String description;
    private Integer sortNo;
    private Integer status;        // 1 发布 0 草稿
    private Date createdAt;
    private Date updatedAt;

    // ===== 多用户字段 =====
    private Long userId;           // 所有者；NULL = 系统公共书
    private Integer isPublic;      // 是否公共书标记（实际以 user_id IS NULL 判定）
    private Integer browsePublic;  // 是否允许未登录用户在目录中浏览（系统设定的展示书）
    private Integer wordsPerLesson; // 每课时默认单词数（导入切分粒度）

    // ===== 内容类型 =====
    private Integer bookType;       // 1=词汇书 2=阅读书

    // ===== 学习计划 =====
    private Integer planDailyWords; // 每日目标词数
    private Date planStartDate;
    private Date planEndDate;

    // ===== 视图辅助字段（非 DB 列） =====
    private Boolean mine;          // 当前用户是否所有者
    private Integer totalWords;    // 总词数（看板）
    private Integer learnedWords;  // 已学词数（看板）
    private Integer percent;       // 进度百分比
    private String ownerName;      // 所有者用户名（后台管理展示用）
}
