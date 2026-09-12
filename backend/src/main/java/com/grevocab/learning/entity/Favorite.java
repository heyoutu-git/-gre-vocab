package com.grevocab.learning.entity;

import lombok.Data;

@Data
public class Favorite {
    private Long id;
    private Long userId;
    private String resType;   // knowledge / experiment / formula
    private Long resId;
    private java.util.Date createdAt;
}
