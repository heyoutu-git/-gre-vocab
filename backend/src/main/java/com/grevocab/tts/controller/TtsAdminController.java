package com.grevocab.tts.controller;

import com.grevocab.common.Result;
import com.grevocab.tts.dto.TtsProviderForm;
import com.grevocab.tts.dto.TtsProviderVo;
import com.grevocab.tts.entity.TtsEngine;
import com.grevocab.tts.entity.TtsUsage;
import com.grevocab.tts.service.KokoroWatchdogService;
import com.grevocab.tts.service.TtsConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/tts")
public class TtsAdminController {

    private final TtsConfigService ttsConfigService;
    private final KokoroWatchdogService kokoroWatchdogService;

    public TtsAdminController(TtsConfigService ttsConfigService, KokoroWatchdogService kokoroWatchdogService) {
        this.ttsConfigService = ttsConfigService;
        this.kokoroWatchdogService = kokoroWatchdogService;
    }

    @GetMapping("/engines")
    public Result<List<TtsEngine>> listEngines() {
        return Result.success(ttsConfigService.listEngines());
    }

    @PostMapping("/engines/{id}/enabled")
    public Result<Void> setEngineEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        ttsConfigService.setEngineEnabled(id, enabled);
        return Result.success();
    }

    @GetMapping("/providers")
    public Result<List<TtsProviderVo>> listProviders() {
        return Result.success(ttsConfigService.listProviders());
    }

    @PostMapping("/providers")
    public Result<Long> saveProvider(@RequestBody TtsProviderForm form) {
        return Result.success(ttsConfigService.saveProvider(form));
    }

    @DeleteMapping("/providers/{id}")
    public Result<Void> removeProvider(@PathVariable Long id) {
        ttsConfigService.removeProvider(id);
        return Result.success();
    }

    @GetMapping("/usage/{provider}")
    public Result<TtsUsage> todayUsage(@PathVariable String provider) {
        return Result.success(ttsConfigService.getTodayUsage(provider));
    }

    @PostMapping("/usage/{provider}/quota")
    public Result<Void> setQuota(@PathVariable String provider, @RequestParam long quota) {
        ttsConfigService.setQuota(provider, quota);
        return Result.success();
    }

    // ===== 本机 Kokoro 推理服务（看门狗 + 管理端手动操作） =====

    /** Kokoro 服务状态（probe=true 时立即探测一次，否则返回缓存状态） */
    @GetMapping("/kokoro/status")
    public Result<Map<String, Object>> kokoroStatus(@RequestParam(defaultValue = "true") boolean probe) {
        return Result.success(kokoroWatchdogService.status(probe));
    }

    @PostMapping("/kokoro/start")
    public Result<Map<String, Object>> kokoroStart() {
        kokoroWatchdogService.start();
        return Result.success(kokoroWatchdogService.status(false));
    }

    @PostMapping("/kokoro/stop")
    public Result<Map<String, Object>> kokoroStop() {
        kokoroWatchdogService.stop();
        return Result.success(kokoroWatchdogService.status(false));
    }

    @PostMapping("/kokoro/restart")
    public Result<Map<String, Object>> kokoroRestart() {
        kokoroWatchdogService.restart();
        return Result.success(kokoroWatchdogService.status(false));
    }
}
