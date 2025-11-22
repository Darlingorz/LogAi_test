package com.logai.assint.mapper.custom;

import com.logai.assint.dto.RecordDetailDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface UserRecordDetailMapperCustom {

    /**
     * 执行自定义SQL查询
     *
     * @param sql 自定义SQL语句
     * @return 查询结果Map列表
     */
    List<Map<String, Object>> executeCustomQuery(String sql);

    List<RecordDetailDto> getUserRecordByChatIdAndUserId(String chatId, Long userId, String allAttribute);

    List<RecordDetailDto> searchUserRecords(Long userId,
                                            Long themeId,
                                            String searchValue,
                                            BigDecimal numericSearchValue,
                                            LocalDate searchDate,
                                            LocalDateTime searchDateTime,
                                            LocalDateTime startTime,
                                            LocalDateTime endTime,
                                            String allAttribute,
                                            int page,
                                            int size);

    Long countUserRecords(Long userId,
                          Long themeId,
                          String searchValue,
                          BigDecimal numericSearchValue,
                          LocalDate searchDate,
                          LocalDateTime searchDateTime,
                          LocalDateTime startTime,
                          LocalDateTime endTime);
}
