package com.grevocab.learning.entity;

import lombok.Data;

@Data
public class Note {
    private Long id;
    private Long userId;
    private String resType;   // knowledge / experiment
    private Long resId;
    private String content;
    private java.util.Date updatedAt;
}
