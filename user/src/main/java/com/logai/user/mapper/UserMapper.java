package com.logai.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.logai.user.dto.UserInfoResponse;
import com.logai.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT id, username, email, status, uuid, role_name, created_at, updated_at FROM users WHERE id = #{id}")
    UserInfoResponse findUserInfo(@Param("id") Long id);

    @Select("SELECT * FROM users WHERE email = #{email} LIMIT 1")
    User findByEmail(@Param("email") String email);

    @Select("SELECT * FROM users WHERE uuid = #{uuid} LIMIT 1")
    User findByUuid(@Param("uuid") String uuid);
}
