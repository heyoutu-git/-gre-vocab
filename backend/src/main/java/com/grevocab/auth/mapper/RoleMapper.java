package com.grevocab.auth.mapper;

import com.grevocab.auth.entity.Role;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RoleMapper {

    @Select("SELECT r.* FROM t_role r " +
            "JOIN t_user_role ur ON ur.role_id = r.id " +
            "WHERE ur.user_id = #{userId}")
    List<Role> findByUserId(Long userId);

    @Select("SELECT r.code FROM t_role r " +
            "JOIN t_user_role ur ON ur.role_id = r.id " +
            "WHERE ur.user_id = #{userId}")
    List<String> findCodesByUserId(Long userId);

    @Insert("INSERT IGNORE INTO t_role(code, name, description) VALUES(#{code}, #{name}, #{description})")
    int insertIgnore(Role role);
}
