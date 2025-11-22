package com.logai.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.logai.user.entity.SocialAccount;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SocialAccountMapper extends BaseMapper<SocialAccount> {
    @Select("select * from social_accounts where provider = #{provider} and provider_user_id = #{providerUserId}")
    SocialAccount findByProviderAndProviderUserId(@Param("provider") String provider, @Param("providerUserId") String providerUserId);

    @Select("select * from social_accounts where user_id = #{userId} and provider = #{provider}")
    SocialAccount findByUserIdAndProvider(@Param("userId") Long userId, @Param("provider") String provider);

    @Delete("delete from social_accounts where user_id = #{userId} and provider = #{provider}")
    int deleteByUserIdAndProvider(@Param("userId") Long userId, @Param("provider") String provider);
}

