package com.grevocab.tts.service;

import com.grevocab.common.CryptoUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grevocab.tts.dto.TtsProviderForm;
import com.grevocab.tts.dto.TtsProviderVo;
import com.grevocab.tts.entity.TtsEngine;
import com.grevocab.tts.entity.TtsProvider;
import com.grevocab.tts.entity.TtsUsage;
import com.grevocab.tts.mapper.TtsMapper;
import lombok.Data;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TtsConfigService {

    private final TtsMapper ttsMapper;
    private final CryptoUtil cryptoUtil;
    private final ObjectMapper objectMapper;

    public TtsConfigService(TtsMapper ttsMapper, CryptoUtil cryptoUtil, ObjectMapper objectMapper) {
        this.ttsMapper = ttsMapper;
        this.cryptoUtil = cryptoUtil;
        this.objectMapper = objectMapper;
    }

    // ---- engine ----
    public List<TtsEngine> listEngines() {
        return ttsMapper.listEngines();
    }

    @Transactional
    public void setEngineEnabled(Long id, boolean enabled) {
        ttsMapper.updateEngineEnabled(id, enabled ? 1 : 0);
    }

    // ---- provider ----
    public List<TtsProviderVo> listProviders() {
        return ttsMapper.listProviders().stream().map(this::toVo).collect(Collectors.toList());
    }

    public TtsProviderVo getProvider(Long id) {
        TtsProvider p = ttsMapper.findProviderById(id);
        return p == null ? null : toVo(p);
    }

    public TtsProvider getActiveProvider(String engineCode) {
        TtsEngine engine = ttsMapper.findEngineByCode(engineCode);
        if (engine == null || Integer.valueOf(0).equals(engine.getEnabled())) return null;
        TtsProvider provider = ttsMapper.findActiveProviderByEngine(engineCode);
        if (provider == null || Integer.valueOf(0).equals(provider.getEnabled())) return null;
        return provider;
    }

    @Transactional
    public Long saveProvider(TtsProviderForm form) {
        TtsProvider p = new TtsProvider();
        p.setId(form.getId());
        p.setEngineCode(form.getEngineCode());
        p.setProvider(form.getProvider());
        p.setAppId(form.getAppId());
        p.setRegion(form.getRegion());
        p.setEndpoint(form.getEndpoint());
        p.setExtraJson(form.getExtraJson());
        p.setEnabled(form.getEnabled() == null ? 1 : form.getEnabled());
        p.setSortNo(form.getSortNo() == null ? 0 : form.getSortNo());

        // 加密凭据：仅当提交新值时才覆盖；空字符串表示清空
        if (form.getSecretId() != null) {
            p.setSecretIdEncrypted(form.getSecretId().isEmpty() ? "" : cryptoUtil.encrypt(form.getSecretId()));
        }
        if (form.getSecretKey() != null) {
            p.setSecretKeyEncrypted(form.getSecretKey().isEmpty() ? "" : cryptoUtil.encrypt(form.getSecretKey()));
        }

        if (p.getId() == null) {
            ttsMapper.insertProvider(p);
        } else {
            // 更新时若未提交 secret，保留原值
            TtsProvider old = ttsMapper.findProviderById(p.getId());
            if (old != null) {
                if (form.getSecretId() == null) p.setSecretIdEncrypted(old.getSecretIdEncrypted());
                if (form.getSecretKey() == null) p.setSecretKeyEncrypted(old.getSecretKeyEncrypted());
            }
            ttsMapper.updateProvider(p);
        }
        return p.getId();
    }

    @Transactional
    public void removeProvider(Long id) {
        ttsMapper.deleteProvider(id);
    }

    public Decrypted decryptProvider(TtsProvider provider) {
        Decrypted d = new Decrypted();
        d.setId(provider.getId());
        d.setEngineCode(provider.getEngineCode());
        d.setProvider(provider.getProvider());
        d.setAppId(provider.getAppId());
        d.setSecretId(cryptoUtil.decrypt(provider.getSecretIdEncrypted()));
        d.setSecretKey(cryptoUtil.decrypt(provider.getSecretKeyEncrypted()));
        d.setRegion(provider.getRegion());
        d.setEndpoint(provider.getEndpoint());
        d.setExtraJson(provider.getExtraJson());
        d.setEnabled(provider.getEnabled());
        d.setSortNo(provider.getSortNo());
        return d;
    }

    // ---- usage / 熔断 ----
    public TtsUsage getTodayUsage(String provider) {
        return ttsMapper.findUsage(provider, LocalDate.now());
    }

    /**
     * 记录本次调用用量。若超过 quota 则返回 true（已熔断）。
     * quota <= 0 表示不限制。
     */
    @Transactional
    public boolean recordAndCheckFuse(String provider, long charCount, long quota) {
        if (quota > 0) {
            TtsUsage today = ttsMapper.findUsage(provider, LocalDate.now());
            long used = (today != null && today.getCharCount() != null) ? today.getCharCount() : 0;
            if (used + charCount > quota) {
                return true; // 已超当日配额，熔断
            }
        }
        ttsMapper.addUsage(provider, LocalDate.now(), charCount, 1, quota);
        return false;
    }

    @Transactional
    public void setQuota(String provider, long quota) {
        // 同步写入 provider 的 extra_json（熔断读取的配额源），保证后台设置的限额真正生效
        TtsProvider p = ttsMapper.findActiveProviderByEngine(provider);
        if (p == null) {
            p = ttsMapper.listProviders().stream()
                    .filter(x -> provider.equals(x.getEngineCode())).findFirst().orElse(null);
        }
        if (p != null) {
            Map<String, Object> m = new HashMap<>();
            if (p.getExtraJson() != null && !p.getExtraJson().isEmpty()) {
                try { m = objectMapper.readValue(p.getExtraJson(), Map.class); } catch (Exception ignored) {}
            }
            m.put("quota", quota);
            try { ttsMapper.updateProviderExtraJson(p.getId(), objectMapper.writeValueAsString(m)); } catch (Exception ignored) {}
        }
        ttsMapper.setQuota(provider, LocalDate.now(), quota);
    }

    private TtsProviderVo toVo(TtsProvider p) {
        TtsProviderVo vo = new TtsProviderVo();
        vo.setId(p.getId());
        vo.setEngineCode(p.getEngineCode());
        vo.setProvider(p.getProvider());
        vo.setAppId(p.getAppId());
        vo.setRegion(p.getRegion());
        vo.setEndpoint(p.getEndpoint());
        vo.setExtraJson(p.getExtraJson());
        vo.setEnabled(p.getEnabled());
        vo.setSortNo(p.getSortNo());
        vo.setCreatedAt(p.getCreatedAt());
        vo.setUpdatedAt(p.getUpdatedAt());
        return vo;
    }

    @Data
    public static class Decrypted {
        private Long id;
        private String engineCode;
        private String provider;
        private String appId;
        private String secretId;
        private String secretKey;
        private String region;
        private String endpoint;
        private String extraJson;
        private Integer enabled;
        private Integer sortNo;
    }
}
