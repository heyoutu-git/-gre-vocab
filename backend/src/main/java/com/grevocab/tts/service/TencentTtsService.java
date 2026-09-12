package com.grevocab.tts.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grevocab.tts.entity.TtsProvider;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 腾讯云 TTS TextToVoice 调用（TC3-HMAC-SHA256 签名）。
 * 返回 base64 音频数据，前端可直接 new Audio('data:audio/mp3;base64,...') 播放。
 */
@Service
public class TencentTtsService {

    private static final String SERVICE = "tts";
    private static final String HOST = "tts.tencentcloudapi.com";
    private static final String ACTION = "TextToVoice";
    private static final String VERSION = "2019-08-23";
    private static final String REGION = "ap-guangzhou";

    private final TtsConfigService ttsConfigService;
    private final ObjectMapper objectMapper;

    public TencentTtsService(TtsConfigService ttsConfigService, ObjectMapper objectMapper) {
        this.ttsConfigService = ttsConfigService;
        this.objectMapper = objectMapper;
    }

    /**
     * 调用腾讯云 TTS。若当日已超配额返回 null（前端应回退其他引擎）。
     */
    public String synthesize(String text) throws Exception {
        TtsProvider provider = ttsConfigService.getActiveProvider("tencent");
        if (provider == null) return null;

        long quota = parseQuota(provider.getExtraJson());
        if (ttsConfigService.recordAndCheckFuse("tencent", text.length(), quota)) {
            return null; // 已熔断
        }

        TtsConfigService.Decrypted cred = ttsConfigService.decryptProvider(provider);
        return doRequest(cred.getSecretId(), cred.getSecretKey(), text);
    }

    private String doRequest(String secretId, String secretKey, String text) throws Exception {
        String payload = buildPayload(text);
        long timestamp = System.currentTimeMillis() / 1000;
        String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date(timestamp * 1000));

        String hashedPayload = sha256Hex(payload);
        String canonicalHeaders = "content-type:application/json\n" +
                "host:" + HOST + "\n" +
                "x-tc-action:" + ACTION.toLowerCase() + "\n";
        String signedHeaders = "content-type;host;x-tc-action";
        String canonicalRequest = "POST\n/\n\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + hashedPayload;

        String credentialScope = date + "/" + SERVICE + "/tc3_request";
        String stringToSign = "TC3-HMAC-SHA256\n" + timestamp + "\n" + credentialScope + "\n" + sha256Hex(canonicalRequest);

        byte[] secretDate = hmacSha256(("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmacSha256(secretDate, SERVICE);
        byte[] secretSigning = hmacSha256(secretService, "tc3_request");
        String signature = bytesToHex(hmacSha256(secretSigning, stringToSign));

        String authorization = "TC3-HMAC-SHA256 Credential=" + secretId + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;

        HttpURLConnection conn = (HttpURLConnection) new URL("https://" + HOST).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Host", HOST);
        conn.setRequestProperty("Authorization", authorization);
        conn.setRequestProperty("X-TC-Action", ACTION);
        conn.setRequestProperty("X-TC-Version", VERSION);
        conn.setRequestProperty("X-TC-Timestamp", String.valueOf(timestamp));
        conn.setRequestProperty("X-TC-Region", REGION);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();

        if (code != 200) {
            throw new RuntimeException("腾讯云 TTS 请求失败 HTTP " + code + ": " + sb);
        }

        Map<String, Object> resp = objectMapper.readValue(sb.toString(), Map.class);
        Map<String, Object> response = (Map<String, Object>) resp.get("Response");
        if (response == null) throw new RuntimeException("腾讯云 TTS 响应异常：" + sb);
        if (response.containsKey("Error")) {
            throw new RuntimeException("腾讯云 TTS 错误：" + response.get("Error"));
        }
        return (String) response.get("Audio");
    }

    private String buildPayload(String text) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("Text", text);
        m.put("SessionId", UUID.randomUUID().toString().replace("-", ""));
        m.put("ModelType", 1);
        m.put("Volume", 0);
        m.put("Speed", 0);
        m.put("VoiceType", 101001);
        m.put("PrimaryLanguage", 1);
        m.put("SampleRate", 16000);
        m.put("Codec", "mp3");
        return objectMapper.writeValueAsString(m);
    }

    private long parseQuota(String extraJson) {
        if (extraJson == null || extraJson.isEmpty()) return 0;
        try {
            Map<String, Object> m = objectMapper.readValue(extraJson, Map.class);
            Object q = m.get("quota");
            if (q instanceof Number) return ((Number) q).longValue();
            if (q instanceof String) return Long.parseLong((String) q);
        } catch (Exception ignored) { }
        return 0;
    }

    private String sha256Hex(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return bytesToHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
    }

    private byte[] hmacSha256(byte[] key, String msg) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(msg.getBytes(StandardCharsets.UTF_8));
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
