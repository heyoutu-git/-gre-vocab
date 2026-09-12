package com.grevocab.common;

import com.grevocab.auth.entity.Role;
import com.grevocab.auth.mapper.RoleMapper;
import com.grevocab.auth.mapper.UserMapper;
import com.grevocab.auth.util.PasswordEncoderUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 开发期数据初始化：确保角色存在，并确保一个默认 admin 账号可用。
 * 默认账号 admin / admin123 —— 仅用于本地开发，生产请改密或删除。
 */
@Component
public class DataInitializer {

    private final RoleMapper roleMapper;
    private final UserMapper userMapper;
    private final PasswordEncoderUtil passwordEncoder;

    public DataInitializer(RoleMapper roleMapper, UserMapper userMapper, PasswordEncoderUtil passwordEncoder) {
        this.roleMapper = roleMapper;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void init() {
        ensureRole("visitor", "访客");
        ensureRole("student", "学生");
        ensureRole("editor", "编辑");
        ensureRole("admin", "管理员");

        if (userMapper.countByUsername("admin") == 0) {
            com.grevocab.auth.entity.User u = new com.grevocab.auth.entity.User();
            u.setUsername("admin");
            u.setPasswordHash(passwordEncoder.encode("admin123"));
            u.setNickname("管理员");
            u.setStatus(1);
            userMapper.insert(u);
            userMapper.assignRole(u.getId(), "admin");
        }
    }

    private void ensureRole(String code, String name) {
        Role r = new Role();
        r.setCode(code);
        r.setName(name);
        r.setDescription(name);
        try {
            roleMapper.insertIgnore(r);
        } catch (Exception ignored) {
            // 已存在则忽略
        }
    }
}
