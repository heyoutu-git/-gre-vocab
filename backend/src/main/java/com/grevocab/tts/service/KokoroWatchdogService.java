package com.grevocab.tts.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Kokoro 推理服务看门狗。
 *
 * 背景：188 上 kokoro-server 由计划任务 GreKokoroTTS（onstart，SYSTEM）常驻，
 * 但任务默认带「运行 72 小时后停止」限制，曾被自动终止导致前端持续报
 * 「服务器语音引擎不可用」。看门狗定期探测 /health，连续失败即通过
 * schtasks /run 自动拉起，并提供管理端手动 start/stop/restart。
 */
@Service
public class KokoroWatchdogService {

    private static final Logger log = LoggerFactory.getLogger(KokoroWatchdogService.class);

    private final KokoroServerTtsService kokoroServerTtsService;
    private final boolean enabled;
    private final String taskName;

    /** 连续失败达到该次数才触发自动重启（避免瞬时抖动误杀） */
    private static final int FAIL_THRESHOLD = 2;
    /** 自动重启冷却期：重启后至少等这么久再允许下一次自动重启（模型加载需数秒） */
    private static final long RESTART_COOLDOWN_SECONDS = 180;

    // --- 运行状态（供管理端展示） ---
    private volatile LocalDateTime lastCheckAt;
    private volatile boolean lastHealthy;
    private volatile int consecutiveFailures;
    private volatile int autoRestartCount;
    private volatile LocalDateTime lastAutoRestartAt;
    private volatile String lastAction;
    private volatile LocalDateTime lastActionAt;
    private volatile String lastActionMessage = "";

    public KokoroWatchdogService(
            KokoroServerTtsService kokoroServerTtsService,
            @Value("${app.kokoro-watchdog.enabled:true}") boolean enabled,
            @Value("${app.kokoro-watchdog.task-name:GreKokoroTTS}") String taskName) {
        this.kokoroServerTtsService = kokoroServerTtsService;
        this.enabled = enabled;
        this.taskName = taskName;
    }

    /**
     * 每 60 秒探测一次（启动后延迟 90 秒再开始，给本机服务和数据库留启动时间）。
     * 连续 2 次失败且不在冷却期内 → schtasks /run 拉起 kokoro-server。
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 90_000)
    public void watch() {
        if (!enabled) return;
        boolean healthy = kokoroServerTtsService.isHealthy();
        lastCheckAt = LocalDateTime.now();
        lastHealthy = healthy;
        if (healthy) {
            consecutiveFailures = 0;
            return;
        }
        consecutiveFailures++;
        log.warn("[KokoroWatchdog] 健康探测失败（连续第 {} 次）", consecutiveFailures);
        if (consecutiveFailures < FAIL_THRESHOLD) return;
        if (lastAutoRestartAt != null
                && lastAutoRestartAt.plusSeconds(RESTART_COOLDOWN_SECONDS).isAfter(LocalDateTime.now())) {
            log.info("[KokoroWatchdog] 处于自动重启冷却期，本次跳过");
            return;
        }
        String result = runSchtasks("/run");
        if (result != null && result.contains("成功")) {
            autoRestartCount++;
            lastAutoRestartAt = LocalDateTime.now();
            log.warn("[KokoroWatchdog] 已自动拉起计划任务 {}（累计 {} 次）", taskName, autoRestartCount);
        } else {
            log.error("[KokoroWatchdog] 自动拉起失败：{}", result);
        }
    }

    /** 管理端：手动启动 */
    public String start() {
        String result = runSchtasks("/run");
        recordAction("start", result);
        return result;
    }

    /** 管理端：手动停止（schtasks /end 终止任务进程） */
    public String stop() {
        String result = runSchtasks("/end");
        recordAction("stop", result);
        return result;
    }

    /** 管理端：重启 = 先 end 再 run（间隔 2 秒让端口释放） */
    public String restart() {
        String endResult = runSchtasks("/end");
        try {
            TimeUnit.SECONDS.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        String runResult = runSchtasks("/run");
        String result = "end: " + endResult + " | run: " + runResult;
        recordAction("restart", result);
        return result;
    }

    /** 管理端：立即探测一次并返回最新状态 */
    public Map<String, Object> status(boolean withProbe) {
        if (withProbe) {
            lastHealthy = kokoroServerTtsService.isHealthy();
            lastCheckAt = LocalDateTime.now();
            if (lastHealthy) consecutiveFailures = 0;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("healthy", lastHealthy);
        m.put("checkAt", lastCheckAt);
        m.put("consecutiveFailures", consecutiveFailures);
        m.put("autoRestartCount", autoRestartCount);
        m.put("lastAutoRestartAt", lastAutoRestartAt);
        m.put("lastAction", lastAction);
        m.put("lastActionAt", lastActionAt);
        m.put("lastActionMessage", lastActionMessage);
        m.put("watchdogEnabled", enabled);
        m.put("taskName", taskName);
        return m;
    }

    private void recordAction(String action, String result) {
        lastAction = action;
        lastActionAt = LocalDateTime.now();
        lastActionMessage = result == null ? "" : result;
        log.info("[KokoroWatchdog] 管理端操作 {}：{}", action, result);
    }

    /**
     * 执行 schtasks 命令（/run 或 /end）。返回 stdout 摘要；异常返回错误描述。
     */
    private String runSchtasks(String op) {
        try {
            Process p = new ProcessBuilder("schtasks", op, "/tn", taskName)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = p.waitFor(15, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return "执行超时";
            }
            String out = new String(p.getInputStream().readAllBytes(), "GBK");
            return out.trim();
        } catch (Exception e) {
            return "执行异常：" + e.getMessage();
        }
    }
}
