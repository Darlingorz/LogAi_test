package com.logai.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.logai.user.dto.UserInfoResponse;
import com.logai.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT m.product_id,u.username,u.email,u.featurebase_user_hash,u.uuid AS user_id,um.`status` AS membership_status,m.`name` AS membership_name,um.subscription_id,um.start_time,um.end_time,u.created_at,u.updated_at FROM users u LEFT JOIN user_memberships um ON u.id=um.user_id LEFT JOIN memberships m ON m.id=um.membership_id WHERE u.id=#{id}")
    UserInfoResponse findUserInfo(@Param("id") Long id);

    @Select("SELECT * FROM users WHERE email = #{email} LIMIT 1")
    User findByEmail(@Param("email") String email);

    @Select("SELECT * FROM users WHERE uuid = #{uuid} LIMIT 1")
    User findByUuid(@Param("uuid") String uuid);
}
