package com.grevocab.tts.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TtsProvider {
    private Long id;
    private String engineCode;
    private String provider;
    private String appId;
    private String secretIdEncrypted;
    private String secretKeyEncrypted;
    private String region;
    private String endpoint;
    private String extraJson;
    private Integer enabled;
    private Integer sortNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
