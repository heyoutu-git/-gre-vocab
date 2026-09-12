package com.grevocab.learning.entity;

import lombok.Data;

@Data
public class UserProgress {
    private Long id;
    private Long userId;
    private Long lessonId;
    private Integer finished;
    private java.util.Date finishedAt;
}
