package com.grevocab.tts.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TtsEngine {
    private Long id;
    private String code;
    private String name;
    private Integer enabled;
    private Integer sortNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
