package com.logai.creem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.logai.creem.entity.MembershipFeature;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MembershipFeatureMapper extends BaseMapper<MembershipFeature> {
    @Select("SELECT * FROM membership_features WHERE membership_id = #{membershipId} AND feature_key = #{featureKey}")
    MembershipFeature findByMembershipIdAndFeatureKey(Long membershipId, String featureKey);
}
