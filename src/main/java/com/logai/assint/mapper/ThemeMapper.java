package com.logai.assint.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.logai.assint.dto.ThemeRecordSummaryDto;
import com.logai.assint.entity.Theme;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ThemeMapper extends BaseMapper<Theme> {
    List<Theme> queryByUserIdOrIsPublic(@Param("userId") Long userId);

    List<ThemeRecordSummaryDto> queryWithRecordCountByUserIdOrIsPublic(@Param("userId") Long userId);

    Theme queryByThemeNameAndUserId(@Param("themeName") String themeName, @Param("userId") Long userId);
}