package com.logai.creem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.logai.creem.entity.Membership;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MembershipMapper extends BaseMapper<Membership> {
    @Select("SELECT * FROM memberships WHERE name = #{name}")
    Membership findByName(String name);

    Membership findByProductId(Long id);

}
