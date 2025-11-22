package com.logai.assint.service;

import com.logai.assint.dto.SaveRecordDetailRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface UserRecordService {
    Map<String, Object> getUserRecordByChatIdAndUserId(String chatId, Long userId, String allAttribute);

    Map<String, Object> searchUserRecords(Long userId, Long themeId, String searchValue, LocalDate searchDate,
                                          LocalDateTime searchDateTime, LocalDateTime startTime,
                                          LocalDateTime endTime, String allAttribute, int page, int size);

    void saveUserRecord(Long userId, SaveRecordDetailRequest saveRecordDetailRequest);

    void deleteUserRecord(Long userId, Long recordId);

    void deleteUserRecords(Long userId, List<Long> recordIds);
}
