package com.grevocab.auth.entity;

import lombok.Data;

@Data
public class Role {
    private Long id;
    private String code;   // visitor/student/editor/admin
    private String name;
    private String description;
}
