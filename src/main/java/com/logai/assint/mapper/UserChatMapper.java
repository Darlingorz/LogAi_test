package com.logai.assint.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.logai.assint.entity.UserChat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

@Mapper
public interface UserChatMapper extends BaseMapper<UserChat> {
    @Select("SELECT COUNT(*) FROM user_chat WHERE user_id = #{userId} AND conversation_type = #{conversationType} AND record_date = #{recordDate}")
    Long countByUserIdAndConversationTypeAndRecordDate(@Param("userId") Long userId, @Param("conversationType") String conversationType, @Param("recordDate") LocalDate recordDate);

    @Select("SELECT COUNT(*) FROM user_chat WHERE user_id = #{userId} AND conversation_type = #{conversationType} AND record_date >= #{startDate} AND record_date < #{endDate}")
    Long countByUserIdAndConversationTypeBetweenDates(@Param("userId") Long userId, @Param("conversationType") String conversationType, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Select("SELECT status FROM user_chat WHERE id = #{chatId} AND user_id = #{userId}")
    String findStatusByChatIdAndUserId(@Param("chatId") String chatId, @Param("userId") Long userId);
}