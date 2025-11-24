package com.logai.assint.service.impl;

import cn.hutool.core.convert.Convert;
import com.logai.assint.dto.RecordDetailDto;
import com.logai.assint.dto.SaveRecordDetailRequest;
import com.logai.assint.entity.UserRecord;
import com.logai.assint.entity.UserRecordDetail;
import com.logai.assint.jdbc.UserRecordDetailMapperCustom;
import com.logai.assint.mapper.UserChatMapper;
import com.logai.assint.mapper.UserRecordDetailMapper;
import com.logai.assint.mapper.UserRecordMapper;
import com.logai.assint.service.UserRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserRecordServiceImpl implements UserRecordService {
    private static final List<DateTimeFormatter> SUPPORTED_DATE_TIME_FORMATTERS = List.of(
            DateTimeFormatter.ISO_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    );

    private static final List<DateTimeFormatter> SUPPORTED_DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
    );
    private final UserRecordDetailMapper userRecordDetailMapper;
    private final UserRecordDetailMapperCustom userRecordDetailMapperCustom;
    private final UserRecordMapper userRecordMapper;
    private final UserChatMapper userChatMapper;

    @Override
    public Map<String, Object> getUserRecordByChatIdAndUserId(String chatId, Long userId, String allAttribute) {
        List<RecordDetailDto> records = userRecordDetailMapperCustom
                .getUserRecordByChatIdAndUserId(chatId, userId, allAttribute);

        String status = userChatMapper.findStatusByChatIdAndUserId(chatId, userId);
        if (StringUtils.isEmpty(status)) {
            throw new RuntimeException("ChatId not found");
        }
        long total = records.stream()
                .map(RecordDetailDto::getRecordId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        Map<String, Object> payload = new HashMap<>();
        payload.put("records", records);
        payload.put("total", total);
        payload.put("status", status);
        return payload;
    }

    @Override
    public Map<String, Object> searchUserRecords(Long userId, Long themeId, String searchValue, LocalDate searchDate,
                                                 LocalDateTime searchDateTime, LocalDateTime startTime,
                                                 LocalDateTime endTime, String allAttribute, int page, int size) {
        String trimmedSearch = StringUtils.isBlank(searchValue) ? null : searchValue.trim();
        BigDecimal numericSearchValue = null;
        if (StringUtils.isNotBlank(trimmedSearch) && NumberUtils.isCreatable(trimmedSearch)) {
            try {
                numericSearchValue = new BigDecimal(trimmedSearch);
            } catch (NumberFormatException ex) {
                log.warn("无法将搜索值解析为数字，将按字符串处理: {}", trimmedSearch, ex);
            }
        }

        List<RecordDetailDto> records = userRecordDetailMapperCustom
                .searchUserRecords(userId, themeId, trimmedSearch, numericSearchValue,
                        searchDate, searchDateTime, startTime, endTime,
                        allAttribute, page, size);

        Long total = userRecordDetailMapperCustom
                .countUserRecords(userId, themeId, trimmedSearch, numericSearchValue,
                        searchDate, searchDateTime, startTime, endTime);
        Map<String, Object> payload = new HashMap<>();
        payload.put("records", records);
        payload.put("total", total);
        return payload;
    }


    @Override
    public void saveUserRecord(Long userId, SaveRecordDetailRequest req) {
        Objects.requireNonNull(userId, "User information is missing"); // 用户信息缺失
        Assert.notNull(req.getRecordId(), "recordId must not be null"); // recordId不能为空
        Assert.notNull(req.getAttributeId(), "attributeId must not be null"); // attributeId不能为空
        Assert.notNull(req.getDataType(), "dataType must not be null"); // dataType不能为空
        if (req.getDetailId() == null) {
            verifyRecordOwnership(req.getRecordId(), userId);
            UserRecordDetail newDetail = new UserRecordDetail();
            newDetail.setRecordId(req.getRecordId());
            newDetail.setAttributeId(req.getAttributeId());
            newDetail.setCreatedAt(LocalDateTime.now());
            newDetail.setUpdatedAt(LocalDateTime.now());
            applyNewValue(newDetail, req);
            userRecordDetailMapper.insert(newDetail);
        } else {
            UserRecordDetail userRecordDetail = userRecordDetailMapper.selectById(req.getDetailId());
            if (userRecordDetail == null) {
                throw new IllegalArgumentException("Record detail not found"); // 未找到对应的记录明细
            }
            verifyRecordOwnership(userRecordDetail.getRecordId(), userId);

            if (!Objects.equals(userRecordDetail.getRecordId(), req.getRecordId())) {
                throw new IllegalArgumentException("Record ID does not match the detail"); // 记录ID与明细不匹配
            }
            applyNewValue(userRecordDetail, req);
            userRecordDetail.setUpdatedAt(LocalDateTime.now());
            userRecordDetailMapper.insert(userRecordDetail);
        }
    }

    @Override
    public void deleteUserRecord(Long userId, Long recordId) {
        // 用户信息缺失
        Objects.requireNonNull(userId, "User information is missing");
        // recordId不能为空
        Objects.requireNonNull(recordId, "recordId must not be null");
        verifyRecordOwnership(recordId, userId);
        List<UserRecordDetail> userRecordDetails = userRecordDetailMapper.findByRecordId(recordId);
        for (UserRecordDetail userRecordDetail : userRecordDetails) {
            userRecordDetailMapper.deleteById(userRecordDetail.getId());
        }
        userRecordMapper.deleteById(recordId);
    }

    @Override
    public void deleteUserRecords(Long userId, List<Long> recordIds) {
        Objects.requireNonNull(userId, "User information is missing"); // 用户信息缺失
        if (recordIds == null || recordIds.isEmpty()) {
            throw new IllegalArgumentException("recordIds must not be empty");// recordIds不能为空
        }
        List<Long> distinctIds = recordIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (distinctIds.isEmpty()) {
            throw new IllegalArgumentException("recordIds must not be empty");// recordIds不能为空
        }
        distinctIds.forEach(recordId -> {
            deleteUserRecord(userId, recordId);
        });
    }

    private void applyNewValue(UserRecordDetail detail, SaveRecordDetailRequest req) {
        detail.setStringValue(null);
        detail.setNumberValue(null);
        detail.setDateValue(null);
        detail.setBooleanValue(null);
        detail.setJsonValue(null);

        switch (req.getDataType()) {
            case NUMBER -> {
                String newValue = req.getNewValue();
                if (StringUtils.isBlank(newValue)) {
                    detail.setNumberValue(null);
                } else {
                    try {
                        detail.setNumberValue(Double.parseDouble(newValue));
                    } catch (NumberFormatException ex) {
                        throw new IllegalArgumentException("Invalid number format", ex); // 数值格式不正确
                    }
                }
            }
            case DATE, DATETIME -> {
                String newValue = req.getNewValue();
                if (StringUtils.isBlank(newValue)) {
                    detail.setDateValue(null);
                } else {
                    try {
                        detail.setDateValue(parseFlexibleDateTime(newValue));
                    } catch (Exception ex) {
                        throw new IllegalArgumentException("Invalid time format, ISO-8601 required", ex); // 时间格式不正确，需要ISO-8601格式
                    }
                }
            }
            case BOOLEAN -> detail.setBooleanValue(Convert.toBool(req.getNewValue(), false));
            case JSON -> detail.setJsonValue(req.getNewValue());
            default -> detail.setStringValue(req.getNewValue());
        }
    }

    private LocalDateTime parseFlexibleDateTime(String value) {
        DateTimeParseException lastException = null;
        for (DateTimeFormatter formatter : SUPPORTED_DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException ex) {
                lastException = ex;
            }
        }

        for (DateTimeFormatter formatter : SUPPORTED_DATE_FORMATTERS) {
            try {
                return LocalDate.parse(value, formatter).atStartOfDay();
            } catch (DateTimeParseException ex) {
                lastException = ex;
            }
        }

        throw lastException != null
                ? new IllegalArgumentException("Invalid time format, ISO-8601 required", lastException)
                : new IllegalArgumentException("Invalid time format, ISO-8601 required");
    }

    private UserRecord verifyRecordOwnership(Long recordId, Long userId) {
        UserRecord userRecord = userRecordMapper.selectById(recordId);
        if (userRecord == null) {
            throw new IllegalArgumentException("Record not found"); // 未找到对应的记录
        }
        if (!Objects.equals(userRecord.getUserId(), userId)) {
            throw new IllegalArgumentException("Not authorized to operate on another user's record");// 无权操作他人的记录
        }
        return userRecord;
    }
}
