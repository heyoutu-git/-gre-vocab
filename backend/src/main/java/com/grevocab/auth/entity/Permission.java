package com.grevocab.auth.entity;

import lombok.Data;

@Data
public class Permission {
    private Long id;
    private String code;     // experiment:read 等
    private String name;
    private String module;
}
