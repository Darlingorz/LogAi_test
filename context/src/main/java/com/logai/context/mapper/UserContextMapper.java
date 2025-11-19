package com.logai.context.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.logai.context.entity.UserContext;
import com.logai.context.enums.ContextType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserContextMapper extends BaseMapper<UserContext> {

    @Select("SELECT COUNT(*) FROM user_context WHERE user_id = #{userId} AND theme_id = #{themeId} AND context_type = #{contextType}")
    int existsByUserIdAndThemeIdAndContextType(Long userId, Long themeId, ContextType contextType);

    List<UserContext> findAllByUserId(Long userId, List<Long> themeIds, String timeZone);
}
