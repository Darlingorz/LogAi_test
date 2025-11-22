package com.logai.context.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.logai.context.entity.OnboardContext;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OnboardContextMapper extends BaseMapper<OnboardContext> {
    List<OnboardContext> selectByThemeIds(@Param("themeIds") List<Long> themeIds);

}