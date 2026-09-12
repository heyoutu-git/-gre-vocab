package com.grevocab.auth.mapper;

import com.grevocab.auth.entity.LoginLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface LoginLogMapper {

    @Insert("INSERT INTO t_login_log(user_id, username, ip, port, user_agent, login_type, result, fail_reason, created_at) " +
            "VALUES(#{userId}, #{username}, #{ip}, #{port}, #{userAgent}, #{loginType}, #{result}, #{failReason}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(LoginLog log);
}
