package com.grevocab.gre.entity;

import lombok.Data;

@Data
public class Passage {
    private Long id;
    private Long lessonId;
    private Integer seq;
    private String enText;     // 英文篇章
    private String zhText;     // 中文翻译
    private String alignment;  // JSON [{en,zh}]
    private Integer hasManualAlignment; // 1 已人工调整 0 未调整
    private Integer status;    // 1 发布 0 草稿
}
