package com.grevocab.auth.service;

import com.grevocab.auth.dto.CaptchaResponse;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录图形验证码：服务端生成 4 位字符 + PNG 图片，以 token 为键存储在内存 Map 中，
 * 有效期 5 分钟，且一次性使用（校验成功后即销毁，防重放）。无需 Session / Redis。
 */
@Service
public class CaptchaService {

    private final Map<String, Entry> store = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private static final long TTL = 5 * 60 * 1000L; // 5 分钟
    // 去除易混淆字符（0/O、1/I/L 等）
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static class Entry {
        String code;
        long expireAt;
        Entry(String code, long expireAt) {
            this.code = code;
            this.expireAt = expireAt;
        }
    }

    public CaptchaResponse generate() {
        cleanup();
        String code = randomCode(4);
        BufferedImage img = render(code);
        String base64 = toBase64(img);
        String token = UUID.randomUUID().toString();
        store.put(token, new Entry(code.toUpperCase(), System.currentTimeMillis() + TTL));
        CaptchaResponse r = new CaptchaResponse();
        r.setToken(token);
        r.setImageBase64(base64);
        return r;
    }

    public boolean validate(String token, String code) {
        if (token == null || code == null) return false;
        Entry e = store.get(token);
        if (e == null) return false;
        store.remove(token); // 一次性使用，防重放
        if (System.currentTimeMillis() > e.expireAt) return false;
        return e.code.equals(code.trim().toUpperCase());
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(en -> en.getValue().expireAt < now);
    }

    private String randomCode(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        return sb.toString();
    }

    private BufferedImage render(String code) {
        int w = 110, h = 40;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setFont(new Font("Arial", Font.BOLD, 28));
        // 干扰线
        for (int i = 0; i < 6; i++) {
            g.setColor(new Color(random.nextInt(255), random.nextInt(255), random.nextInt(255)));
            g.drawLine(random.nextInt(w), random.nextInt(h), random.nextInt(w), random.nextInt(h));
        }
        // 字符（轻微随机基线，增加机器识别难度）
        for (int i = 0; i < code.length(); i++) {
            g.setColor(new Color(random.nextInt(120), random.nextInt(120), random.nextInt(120)));
            int y = 31 + random.nextInt(4);
            g.drawString(String.valueOf(code.charAt(i)), 16 + i * 22, y);
        }
        g.dispose();
        return img;
    }

    private String toBase64(BufferedImage img) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }
}
