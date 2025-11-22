package com.logai.assint.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.logai.assint.entity.Attribute;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AttributeMapper extends BaseMapper<Attribute> {
    List<Attribute> findByThemeId(@Param("themeId") Long themeId);

    Attribute findByThemeIdAndAttributeName(@Param("themeId") Long themeId, @Param("attributeName") String attributeName, @Param("userId") Long userId);
}