package com.grevocab.common;

import com.grevocab.auth.entity.Role;
import com.grevocab.auth.mapper.RoleMapper;
import com.grevocab.auth.mapper.UserMapper;
import com.grevocab.auth.util.PasswordEncoderUtil;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.List;

/**
 * 开发期数据初始化：确保角色存在，并确保一个默认 admin 账号可用。
 * 默认账号 admin —— 仅用于本地开发初始化，生产请改密。
 * 种子密码从环境变量 GRE_SEED_ADMIN_PASSWORD 读取（敏感信息不入库不入 Git）；
 * 未配置时随机生成并打印到启动日志（仅全新库首次初始化生效，不覆盖已有账号）。
 */
@Component
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

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
            String seed = System.getenv("GRE_SEED_ADMIN_PASSWORD");
            if (seed == null || seed.isEmpty()) {
                seed = randomPassword();
                log.warn("[DataInitializer] 未配置 GRE_SEED_ADMIN_PASSWORD，已为全新 admin 账号生成随机密码：{}", seed);
            }
            com.grevocab.auth.entity.User u = new com.grevocab.auth.entity.User();
            u.setUsername("admin");
            u.setPasswordHash(passwordEncoder.encode(seed));
            u.setNickname("管理员");
            u.setStatus(1);
            userMapper.insert(u);
            userMapper.assignRole(u.getId(), "admin");
        }
    }

    private String randomPassword() {
        final String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789@#$%";
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) sb.append(chars.charAt(r.nextInt(chars.length())));
        return sb.toString();
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
