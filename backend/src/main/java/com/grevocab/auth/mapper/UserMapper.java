package com.grevocab.auth.mapper;

import com.grevocab.auth.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UserMapper {

    @Select("SELECT * FROM t_user WHERE username = #{username} LIMIT 1")
    User findByUsername(String username);

    @Select("SELECT * FROM t_user WHERE id = #{id} LIMIT 1")
    User findById(Long id);

    @Insert("INSERT INTO t_user(username, password_hash, nickname, email, phone, status, register_ip, register_port, created_at, updated_at) " +
            "VALUES(#{username}, #{passwordHash}, #{nickname}, #{email}, #{phone}, #{status}, #{registerIp}, #{registerPort}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @Select("SELECT COUNT(1) FROM t_user WHERE username = #{username}")
    int countByUsername(String username);

    @Select("SELECT id, username, nickname FROM t_user WHERE username LIKE CONCAT('%', #{keyword}, '%') OR nickname LIKE CONCAT('%', #{keyword}, '%') LIMIT 20")
    List<User> searchByKeyword(@Param("keyword") String keyword);

    @Select("<script>SELECT * FROM t_user WHERE 1=1" +
            "<if test='keyword != null and keyword != \"\"'> AND (username LIKE CONCAT('%',#{keyword},'%') OR nickname LIKE CONCAT('%',#{keyword},'%') OR phone LIKE CONCAT('%',#{keyword},'%'))</if>" +
            "<if test='status != null'> AND status = #{status}</if>" +
            " ORDER BY status=2 DESC, id DESC LIMIT 200</script>")
    List<User> listUsers(@Param("keyword") String keyword, @Param("status") Integer status);

    @Update("UPDATE t_user SET status = #{status}, updated_at = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    @Insert("INSERT INTO t_user_role(user_id, role_id) " +
            "VALUES(#{userId}, (SELECT id FROM t_role WHERE code = #{roleCode}))")
    int assignRole(@Param("userId") Long userId, @Param("roleCode") String roleCode);
}
