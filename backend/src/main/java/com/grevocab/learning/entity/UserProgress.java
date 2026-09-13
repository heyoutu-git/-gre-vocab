package com.grevocab.learning.entity;

import lombok.Data;

@Data
public class UserProgress {
    private Long id;
    private Long userId;
    private Long lessonId;
    private Integer finished;
    private java.util.Date finishedAt;
    private Integer lastWordIndex;
    private java.util.Date lastVisitAt;
    private Integer visitCount;
    // JOIN 附加字段（非本表列）
    private Long bookId;
    private String lessonTitle;
}
