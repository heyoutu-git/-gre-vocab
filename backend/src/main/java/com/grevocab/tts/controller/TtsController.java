package com.grevocab.tts.controller;

import com.grevocab.common.Result;
import com.grevocab.tts.entity.TtsEngine;
import com.grevocab.tts.service.TtsConfigService;
import com.grevocab.tts.service.TencentTtsService;
import com.grevocab.tts.service.KokoroServerTtsService;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tts")
public class TtsController {

    private final TtsConfigService ttsConfigService;
    private final TencentTtsService tencentTtsService;
    private final KokoroServerTtsService kokoroServerTtsService;

    public TtsController(TtsConfigService ttsConfigService, TencentTtsService tencentTtsService,
                         KokoroServerTtsService kokoroServerTtsService) {
        this.ttsConfigService = ttsConfigService;
        this.tencentTtsService = tencentTtsService;
        this.kokoroServerTtsService = kokoroServerTtsService;
    }

    /**
     * 学习端获取当前启用的 TTS 引擎列表（按优先级排序）
     */
    @GetMapping("/engines")
    public Result<List<TtsEngine>> enabledEngines() {
        List<TtsEngine> list = ttsConfigService.listEngines().stream()
                .filter(e -> Integer.valueOf(1).equals(e.getEnabled()))
                .collect(Collectors.toList());
        return Result.success(list);
    }

    /**
     * 腾讯云 TTS 合成（单个文本）。返回 base64(mp3)，前端直接播放。
     * 若已熔断或凭据未配置，返回 code != 0，前端回退其他引擎。
     */
    @PostMapping("/tencent/synthesize")
    public Result<String> tencentSynthesize(@RequestBody SynthesizeRequest req) {
        try {
            String audio = tencentTtsService.synthesize(req.getText());
            if (audio == null) return Result.error("腾讯云 TTS 未启用或已超当日免费额度");
            return Result.success(audio);
        } catch (Exception e) {
            return Result.error("腾讯云 TTS 合成失败：" + e.getMessage());
        }
    }

    /**
     * 服务器端 Kokoro 合成（免费离线，188 本机推理）。返回 base64(wav)，前端直接播放。
     * 服务未启动/失败时返回 code != 0，前端回退浏览器端 Kokoro 推理或其它引擎。
     */
    @PostMapping("/kokoro/synthesize")
    public Result<String> kokoroSynthesize(@RequestBody SynthesizeRequest req) {
        try {
            byte[] wav = kokoroServerTtsService.synthesize(req.getText(), req.getSpeed(), req.getVoice());
            if (wav == null) return Result.error("服务器语音引擎不可用");
            return Result.success(Base64.getEncoder().encodeToString(wav));
        } catch (Exception e) {
            return Result.error("服务器语音合成失败：" + e.getMessage());
        }
    }

    @Data
    public static class SynthesizeRequest {
        private String text;
        private Double speed;
        private String voice;
    }
}
