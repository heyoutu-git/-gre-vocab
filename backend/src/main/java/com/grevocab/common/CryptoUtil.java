package com.grevocab.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 对称加密工具。
 *
 * 密钥来源：{@code ${app.crypto.secret}}，由 application.yml 通过环境变量
 * {@code APP_CRYPTO_SECRET} 注入（不硬编码明文）。任意长度字符串经 SHA-256
 * 派生为 32 字节 AES 密钥。
 *
 * 存储格式（base64）：[12 字节随机 IV][密文 + 16 字节 GCM 认证标签]
 * 该格式与 Python cryptography 库的 AESGCM 完全互通，便于跨语言加解密。
 */
@Component
public class CryptoUtil {

    private final String secret;
    private static final int IV_LEN = 12;
    private static final int TAG_LEN_BITS = 128;

    public CryptoUtil(@Value("${app.crypto.secret}") String secret) {
        this.secret = secret;
    }

    private SecretKeySpec deriveKey() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] key = md.digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(key, "AES");
        } catch (Exception e) {
            throw new RuntimeException("派生密钥失败", e);
        }
    }

    /** 明文 -> base64(IV + 密文+tag)。null 原样返回。 */
    public String encrypt(String plain) {
        if (plain == null) return null;
        try {
            byte[] iv = new byte[IV_LEN];
            SecureRandom.getInstanceStrong().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(), new GCMParameterSpec(TAG_LEN_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[IV_LEN + ct.length];
            System.arraycopy(iv, 0, out, 0, IV_LEN);
            System.arraycopy(ct, 0, out, IV_LEN, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new RuntimeException("加密失败", e);
        }
    }

    /** base64(IV + 密文+tag) -> 明文。null 原样返回。 */
    public String decrypt(String cipherText) {
        if (cipherText == null) return null;
        try {
            byte[] in = Base64.getDecoder().decode(cipherText);
            byte[] iv = new byte[IV_LEN];
            System.arraycopy(in, 0, iv, 0, IV_LEN);
            byte[] ct = new byte[in.length - IV_LEN];
            System.arraycopy(in, IV_LEN, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(), new GCMParameterSpec(TAG_LEN_BITS, iv));
            byte[] pt = cipher.doFinal(ct);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("解密失败", e);
        }
    }
}
