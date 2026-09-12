package com.grevocab.tts.controller;

import com.grevocab.common.Result;
import com.grevocab.tts.dto.TtsProviderForm;
import com.grevocab.tts.dto.TtsProviderVo;
import com.grevocab.tts.entity.TtsEngine;
import com.grevocab.tts.entity.TtsUsage;
import com.grevocab.tts.service.TtsConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/tts")
public class TtsAdminController {

    private final TtsConfigService ttsConfigService;

    public TtsAdminController(TtsConfigService ttsConfigService) {
        this.ttsConfigService = ttsConfigService;
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
}
