package com.grevocab.tts.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class TtsUsage {
    private Long id;
    private String provider;
    private LocalDate usageDate;
    private Long charCount;
    private Integer requestCount;
    private Long quota;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
