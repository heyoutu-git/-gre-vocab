package com.grevocab.gre.entity;

import lombok.Data;

import java.util.Date;

@Data
public class Lesson {
    private Long id;
    private Long bookId;
    private String title;
    private Integer sortNo;
    private String description;
    private Integer wordCount;
    private Integer status;        // 1 发布 0 草稿
    private Date createdAt;
    private Date updatedAt;
}
