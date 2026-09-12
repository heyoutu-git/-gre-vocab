package com.grevocab.auth.entity;

import lombok.Data;

import java.util.Date;

@Data
public class User {
    private Long id;
    private String username;
    private String passwordHash;   // BCrypt
    private String nickname;
    private String email;
    private String phone;
    private Integer gradeLevel;    // 高中年级（如 高一/高二/高三）可选
    private Integer status;        // 1正常 0禁用 2待审核
    private String registerIp;     // 注册IP
    private Integer registerPort;  // 注册端口
    private Date createdAt;
    private Date updatedAt;
}
