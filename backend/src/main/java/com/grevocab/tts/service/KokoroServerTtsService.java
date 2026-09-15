package com.grevocab.tts.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 本机 Kokoro 推理服务（kokoro-server，Apache-2.0 模型，完全免费离线）客户端。
 * 服务由计划任务常驻监听 127.0.0.1:8765，仅本机可达；此处反代给学习端。
 */
@Service
public class KokoroServerTtsService {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private final String baseUrl;

    public KokoroServerTtsService(
            @Value("${app.kokoro-server.url:http://127.0.0.1:8765}") String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * 健康探测：GET /health，2 秒超时。服务在跑返回 true。
     */
    public boolean isHealthy() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/health"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 合成英文文本，返回 WAV 字节。服务不可用/失败返回 null，由前端回退浏览器端推理或其它引擎。
     */
    public byte[] synthesize(String text, Double speed, String voice) {
        if (text == null || text.isBlank()) return null;
        try {
            StringBuilder url = new StringBuilder(baseUrl).append("/synthesize?text=")
                    .append(URLEncoder.encode(text, StandardCharsets.UTF_8));
            if (speed != null && speed > 0) url.append("&speed=").append(speed);
            if (voice != null && !voice.isBlank()) url.append("&voice=").append(URLEncoder.encode(voice, StandardCharsets.UTF_8));
            HttpRequest req = HttpRequest.newBuilder(URI.create(url.toString()))
                    .timeout(Duration.ofSeconds(60))
                    .header("Accept", "audio/wav")
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200 || resp.body() == null || resp.body().length == 0) return null;
            return resp.body();
        } catch (Exception e) {
            return null;
        }
    }
}
