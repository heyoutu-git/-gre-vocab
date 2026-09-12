package com.grevocab.gre.entity;

import lombok.Data;

@Data
public class Vocabulary {
    private Long id;
    private Long lessonId;
    private String word;          // 单词
    private String phonetic;      // 源 Barron's ASCII 音标（如 E5beis）
    private String phoneticIpa;   // 转换后的标准 IPA（如 /əˈbeɪs/）
    private String pos;           // 词性 v/adj/n/adv/...
    private String inflection;    // 变形（动词/形容词，可选）
    private String definition;    // 英文释义
    private String meaningCn;     // 中文释义（ECDICT 来源，可空）
    private String example;       // 英文例句
    private Integer sortNo;
    private Integer status;       // 1 发布 0 草稿
}
