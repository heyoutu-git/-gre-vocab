package com.grevocab.tts.mapper;

import com.grevocab.tts.entity.TtsEngine;
import com.grevocab.tts.entity.TtsProvider;
import com.grevocab.tts.entity.TtsUsage;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface TtsMapper {

    // ---- engine ----
    @Select("SELECT id, code, name, enabled, sort_no, created_at, updated_at FROM t_tts_engine ORDER BY sort_no, id")
    List<TtsEngine> listEngines();

    @Select("SELECT id, code, name, enabled, sort_no, created_at, updated_at FROM t_tts_engine WHERE code = #{code} LIMIT 1")
    TtsEngine findEngineByCode(String code);

    @Update("UPDATE t_tts_engine SET enabled = #{enabled}, updated_at = NOW() WHERE id = #{id}")
    int updateEngineEnabled(@Param("id") Long id, @Param("enabled") Integer enabled);

    @Insert("INSERT INTO t_tts_engine (code, name, enabled, sort_no) VALUES (#{code}, #{name}, #{enabled}, #{sortNo}) " +
            "ON DUPLICATE KEY UPDATE name = VALUES(name), enabled = VALUES(enabled), sort_no = VALUES(sort_no), updated_at = NOW()")
    int upsertEngine(TtsEngine engine);

    // ---- provider ----
    @Select("SELECT id, engine_code, provider, app_id, secret_id_encrypted, secret_key_encrypted, region, endpoint, extra_json, enabled, sort_no, created_at, updated_at FROM t_tts_provider ORDER BY sort_no, id")
    List<TtsProvider> listProviders();

    @Select("SELECT id, engine_code, provider, app_id, secret_id_encrypted, secret_key_encrypted, region, endpoint, extra_json, enabled, sort_no, created_at, updated_at FROM t_tts_provider WHERE id = #{id} LIMIT 1")
    TtsProvider findProviderById(Long id);

    @Select("SELECT id, engine_code, provider, app_id, secret_id_encrypted, secret_key_encrypted, region, endpoint, extra_json, enabled, sort_no, created_at, updated_at FROM t_tts_provider WHERE engine_code = #{engineCode} AND enabled = 1 ORDER BY sort_no, id LIMIT 1")
    TtsProvider findActiveProviderByEngine(String engineCode);

    @Insert("INSERT INTO t_tts_provider (engine_code, provider, app_id, secret_id_encrypted, secret_key_encrypted, region, endpoint, extra_json, enabled, sort_no) " +
            "VALUES (#{engineCode}, #{provider}, #{appId}, #{secretIdEncrypted}, #{secretKeyEncrypted}, #{region}, #{endpoint}, #{extraJson}, #{enabled}, #{sortNo})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertProvider(TtsProvider provider);

    @Update("UPDATE t_tts_provider SET engine_code = #{engineCode}, provider = #{provider}, app_id = #{appId}, " +
            "secret_id_encrypted = #{secretIdEncrypted}, secret_key_encrypted = #{secretKeyEncrypted}, " +
            "region = #{region}, endpoint = #{endpoint}, extra_json = #{extraJson}, enabled = #{enabled}, sort_no = #{sortNo}, updated_at = NOW() " +
            "WHERE id = #{id}")
    int updateProvider(TtsProvider provider);

    @Delete("DELETE FROM t_tts_provider WHERE id = #{id}")
    int deleteProvider(Long id);

    @Update("UPDATE t_tts_provider SET extra_json = #{extraJson}, updated_at = NOW() WHERE id = #{id}")
    int updateProviderExtraJson(@Param("id") Long id, @Param("extraJson") String extraJson);

    // ---- usage ----
    @Select("SELECT id, provider, usage_date, char_count, request_count, quota, created_at, updated_at FROM t_tts_usage WHERE provider = #{provider} AND usage_date = #{usageDate} LIMIT 1")
    TtsUsage findUsage(@Param("provider") String provider, @Param("usageDate") LocalDate usageDate);

    @Insert("INSERT INTO t_tts_usage (provider, usage_date, char_count, request_count, quota) " +
            "VALUES (#{provider}, #{usageDate}, #{charCount}, #{requestCount}, #{quota}) " +
            "ON DUPLICATE KEY UPDATE char_count = char_count + VALUES(char_count), request_count = request_count + VALUES(request_count), updated_at = NOW()")
    int addUsage(@Param("provider") String provider, @Param("usageDate") LocalDate usageDate,
                 @Param("charCount") long charCount, @Param("requestCount") int requestCount,
                 @Param("quota") long quota);

    @Update("UPDATE t_tts_usage SET quota = #{quota}, updated_at = NOW() WHERE provider = #{provider} AND usage_date = #{usageDate}")
    int setQuota(@Param("provider") String provider, @Param("usageDate") LocalDate usageDate, @Param("quota") long quota);
}
