package com.logai.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.logai.user.entity.SocialAccount;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;

public interface SocialAccountMapper extends BaseMapper<SocialAccount> {
    @Select("select * from social_accounts where provider = #{provider} and provider_user_id = #{providerUserId}")
    SocialAccount findByProviderAndProviderUserId(String provider, String providerUserId);

    @Select("select * from social_accounts where user_id = #{userId} and provider = #{provider}")
    SocialAccount findByUserIdAndProvider(Long userId, String provider);

    @Delete("delete from social_accounts where user_id = #{userId} and provider = #{provider}")
    int deleteByUserIdAndProvider(Long userId, String provider);
}

