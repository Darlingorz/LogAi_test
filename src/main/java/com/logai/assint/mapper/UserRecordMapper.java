package com.logai.assint.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.logai.assint.entity.UserRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserRecordMapper extends BaseMapper<UserRecord> {
    @Select("SELECT * FROM user_record WHERE user_id = #{userId} AND theme_id = #{themeId}")
    List<UserRecord> findByUserIdAndThemeId(@Param("userId") Long userId, @Param("themeId") Long themeId);

    @Select("SELECT * FROM user_record WHERE user_id = #{userId}")
    List<UserRecord> findByUserId(@Param("userId") Long userId);
}