package com.logai.assint.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.logai.assint.entity.ChatInteraction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChatInteractionMapper extends BaseMapper<ChatInteraction> {

    @Select("SELECT * FROM chat_interaction WHERE session_id = #{sessionId} ORDER BY create_time ASC")
    List<ChatInteraction> findBySessionIdOrderByCreateTimeAsc(@Param("sessionId") String sessionId);
}