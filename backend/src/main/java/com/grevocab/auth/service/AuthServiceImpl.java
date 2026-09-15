package com.grevocab.auth.service;

import com.grevocab.auth.dto.AuthResponse;
import com.grevocab.auth.dto.LoginRequest;
import com.grevocab.auth.dto.RegisterRequest;
import com.grevocab.auth.dto.UserInfo;
import com.grevocab.auth.entity.LoginLog;
import com.grevocab.auth.entity.User;
import com.grevocab.auth.mapper.LoginLogMapper;
import com.grevocab.auth.mapper.RoleMapper;
import com.grevocab.auth.mapper.UserMapper;
import com.grevocab.auth.util.PasswordEncoderUtil;
import com.grevocab.common.BizException;
import com.grevocab.common.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoderUtil passwordEncoder;
    private final JwtUtil jwtUtil;
    private final LoginLogMapper loginLogMapper;
    private final CaptchaService captchaService;

    public AuthServiceImpl(UserMapper userMapper, RoleMapper roleMapper,
                           PasswordEncoderUtil passwordEncoder, JwtUtil jwtUtil,
                           LoginLogMapper loginLogMapper, CaptchaService captchaService) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.loginLogMapper = loginLogMapper;
        this.captchaService = captchaService;
    }

    private String clientIp() {
        try {
            HttpServletRequest r = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            String ip = r.getHeader("X-Forwarded-For");
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) ip = r.getRemoteAddr();
            // 多级代理取第一个 IP
            if (ip != null && ip.contains(",")) ip = ip.split(",")[0].trim();
            return ip;
        } catch (Exception e) { return null; }
    }

    private Integer clientPort() {
        try {
            HttpServletRequest r = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            String fp = r.getHeader("X-Forwarded-Port");
            if (fp != null && fp.matches("\\d+")) return Integer.parseInt(fp);
            return r.getRemotePort();
        } catch (Exception e) { return null; }
    }

    private String userAgent() {
        try {
            HttpServletRequest r = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            return r.getHeader("User-Agent");
        } catch (Exception e) { return null; }
    }

    private void log(String type, Long userId, String username, int result, String failReason) {
        try {
            LoginLog l = new LoginLog();
            l.setLoginType(type);
            l.setUserId(userId);
            l.setUsername(username);
            l.setIp(clientIp());
            l.setPort(clientPort());
            l.setUserAgent(userAgent());
            l.setResult(result);
            l.setFailReason(failReason);
            loginLogMapper.insert(l);
        } catch (Exception ignored) { /* 日志写入失败不影响主流程 */ }
    }

    @Override
    public AuthResponse login(LoginRequest req) {
        // 用户名统一 trim（历史数据曾出现末尾带空格导致登录失败）
        if (req.getUsername() != null) req.setUsername(req.getUsername().trim());
        if (req.getPassword() != null) req.setPassword(req.getPassword().trim());
        if (req.getUsername() == null || req.getUsername().isEmpty() || req.getPassword() == null || req.getPassword().isEmpty()) {
            log("login", null, req.getUsername(), 0, "用户名或密码为空");
            throw new BizException(400, "用户名和密码不能为空");
        }
        if (req.getAgreedTerms() == null || !req.getAgreedTerms()) {
            log("login", null, req.getUsername(), 0, "未同意隐私条款");
            throw new BizException(400, "请先同意隐私条款");
        }
        // 图形验证码校验（防爆破 / 防机器登录）
        if (!captchaService.validate(req.getCaptchaToken(), req.getCaptcha())) {
            log("login", null, req.getUsername(), 0, "验证码错误");
            throw new BizException(400, "验证码错误");
        }
        User user = userMapper.findByUsername(req.getUsername());
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            log("login", null, req.getUsername(), 0, "用户名或密码错误");
            throw new BizException(401, "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            log("login", user.getId(), req.getUsername(), 0, "账号已被禁用");
            throw new BizException(403, "账号已被禁用");
        }
        if (user.getStatus() != null && user.getStatus() == 2) {
            log("login", user.getId(), req.getUsername(), 0, "待管理员审核");
            throw new BizException(403, "注册成功，等待管理员审核通过后即可登录");
        }
        if (user.getStatus() != null && user.getStatus() == 3) {
            log("login", user.getId(), req.getUsername(), 0, "注册申请已拒绝");
            throw new BizException(403, "您的注册申请未通过审核，如有疑问请联系管理员");
        }
        List<String> roles = roleMapper.findCodesByUserId(user.getId());
        String role = roles.isEmpty() ? "student" : roles.get(0);
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), role);
        log("login", user.getId(), req.getUsername(), 1, null);
        return toResponse(token, user, role);
    }

    @Override
    @Transactional
    public void register(RegisterRequest req) {
        // 用户名/昵称统一 trim（防止注册时带入首尾空格导致后续登录失败）
        if (req.getUsername() != null) req.setUsername(req.getUsername().trim());
        if (req.getNickname() != null) req.setNickname(req.getNickname().trim());
        if (req.getUsername() == null || req.getUsername().isEmpty() || req.getPassword() == null) {
            log("register", null, req.getUsername(), 0, "用户名或密码为空");
            throw new BizException(400, "用户名和密码不能为空");
        }
        if (req.getAgreedTerms() == null || !req.getAgreedTerms()) {
            log("register", null, req.getUsername(), 0, "未同意隐私条款");
            throw new BizException(400, "请先同意隐私条款");
        }
        // 手机号必填（中国大陆手机号格式）
        if (req.getPhone() == null || !req.getPhone().matches("1[3-9]\\d{9}")) {
            log("register", null, req.getUsername(), 0, "手机号缺失或格式错误");
            throw new BizException(400, "请输入正确的手机号码");
        }
        if (userMapper.countByUsername(req.getUsername()) > 0) {
            log("register", null, req.getUsername(), 0, "用户名已存在");
            throw new BizException(409, "用户名已存在");
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setNickname(req.getNickname() != null && !req.getNickname().isEmpty() ? req.getNickname() : req.getUsername());
        user.setEmail(req.getEmail());
        user.setPhone(req.getPhone());
        user.setStatus(2); // 待管理员审核
        user.setRegisterIp(clientIp());
        user.setRegisterPort(clientPort());
        userMapper.insert(user);
        userMapper.assignRole(user.getId(), "student");
        log("register", user.getId(), req.getUsername(), 1, null);
    }

    @Override
    public UserInfo me(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) throw new BizException(404, "用户不存在");
        List<String> roles = roleMapper.findCodesByUserId(userId);
        UserInfo info = new UserInfo();
        info.setUserId(user.getId());
        info.setUsername(user.getUsername());
        info.setNickname(user.getNickname());
        info.setRole(roles.isEmpty() ? "student" : roles.get(0));
        return info;
    }

    private AuthResponse toResponse(String token, User user, String role) {
        AuthResponse r = new AuthResponse();
        r.setToken(token);
        r.setUserId(user.getId());
        r.setUsername(user.getUsername());
        r.setNickname(user.getNickname());
        r.setRole(role);
        return r;
    }
}
