package com.logai.context.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.logai.context.entity.UserContext;
import com.logai.context.enums.ContextType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserContextMapper extends BaseMapper<UserContext> {

    @Select("SELECT COUNT(*) FROM user_context WHERE user_id = #{userId} AND theme_id = #{themeId} AND context_type = #{contextType}")
    int existsByUserIdAndThemeIdAndContextType(@Param("userId") Long userId, @Param("themeId") Long themeId, @Param("contextType") ContextType contextType);
}
