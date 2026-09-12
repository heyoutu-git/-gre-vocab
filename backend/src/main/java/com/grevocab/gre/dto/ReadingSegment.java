package com.grevocab.gre.dto;

import lombok.Data;

@Data
public class ReadingSegment {
    private String title;   // 片段标题（如 "第1章 · 第1段"）
    private String en;      // 英文篇章
    private String zh;      // 中文翻译
    private Integer words;  // 词数（可选，用于展示）
}
