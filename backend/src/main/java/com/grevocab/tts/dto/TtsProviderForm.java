package com.grevocab.tts.dto;

import lombok.Data;

@Data
public class TtsProviderForm {
    private Long id;
    private String engineCode;
    private String provider;
    private String appId;
    private String secretId;
    private String secretKey;
    private String region;
    private String endpoint;
    private String extraJson;
    private Integer enabled;
    private Integer sortNo;
}
