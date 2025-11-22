package com.logai.creem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.logai.creem.entity.UserMembership;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMembershipMapper extends BaseMapper<UserMembership> {
    @Select("SELECT * FROM user_memberships WHERE user_id = #{userId}")
    UserMembership findByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM user_memberships WHERE subscription_id = #{subscriptionId}")
    UserMembership findBySubscriptionId(@Param("subscriptionId") String subscriptionId);

    @Select("SELECT * FROM user_memberships WHERE user_id = #{userId} AND NOW() BETWEEN start_time AND end_time limit 1")
    UserMembership findByUserIdAndStatus(@Param("userId") Long userId);
}
