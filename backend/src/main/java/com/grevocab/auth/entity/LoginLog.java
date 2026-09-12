package com.grevocab.auth.entity;

import lombok.Data;

import java.util.Date;

@Data
public class LoginLog {
    private Long id;
    private Long userId;
    private String username;
    private String ip;
    private Integer port;       // 客户端端口
    private String userAgent;
    private String loginType;   // login / register
    private Integer result;      // 1 成功 0 失败
    private String failReason;
    private Date createdAt;
}
