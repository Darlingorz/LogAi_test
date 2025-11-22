package com.logai.oauth2.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.logai.oauth2.entity.AuthorizationCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuthorizationCodeMapper extends BaseMapper<AuthorizationCode> {

    @Select("SELECT * FROM oauth2_authorization_code WHERE code = #{code}")
    AuthorizationCode findByCode(@Param("code") String code);

    @Select("SELECT * FROM oauth2_authorization_code WHERE user_id = #{userId} AND client_id = #{clientId}")
    AuthorizationCode findByUserIdAndClientId(@Param("userId") Long userId, @Param("clientId") String clientId);
}