package com.logai.assint.mapper.custom.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.logai.assint.dto.RecordDetailDto;
import com.logai.assint.mapper.custom.UserRecordDetailMapperCustom;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
public class UserRecordDetailMapperCustomImpl implements UserRecordDetailMapperCustom {

    private final NamedParameterJdbcTemplate jdbc;

    public UserRecordDetailMapperCustomImpl(DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    }


    @Override
    public List<Map<String, Object>> executeCustomQuery(String sql) {

        List<Map<String, Object>> rows = jdbc.query(sql, (rs, rowNum) -> mapRow(rs));

        // JSON 自动转换
        for (Map<String, Object> row : rows) {
            for (Map.Entry<String, Object> e : row.entrySet()) {
                if (e.getValue() instanceof String str) {

                    String trimmed = str.trim();

                    // JSON object
                    if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                        try {
                            e.setValue(JSON.parseObject(trimmed, new TypeReference<Map<String, Object>>() {
                            }));
                        } catch (Exception ignore) {
                        }
                    }

                    // JSON array
                    if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                        try {
                            e.setValue(JSON.parseObject(trimmed, new TypeReference<List<Object>>() {
                            }));
                        } catch (Exception ignore) {
                        }
                    }
                }
            }
        }

        return rows;
    }


    private Map<String, Object> mapRow(ResultSet rs) throws SQLException {
        Map<String, Object> map = new LinkedHashMap<>();
        int count = rs.getMetaData().getColumnCount();
        for (int i = 1; i <= count; i++) {
            String name = JdbcUtils.lookupColumnName(rs.getMetaData(), i);
            Object val = rs.getObject(i);
            map.put(name, val);
        }
        return map;
    }


    @Override
    public List<RecordDetailDto> getUserRecordByChatIdAndUserId(String chatId, Long userId, String allAttribute) {

        String sql = """
                    SELECT
                        t.id AS theme_id,
                        t.theme_name,
                        ur.id AS record_id,
                        urd.id AS detail_id,
                        a.id AS attribute_id,
                        a.attribute_name,
                        a.data_type,
                        urd.number_unit,
                        urd.group_id,
                        COALESCE(urd.string_value, urd.number_value, urd.date_value,
                                 urd.boolean_value, JSON_UNQUOTE(urd.json_value)) AS value
                    FROM user_chat uc
                    JOIN user_record ur ON uc.id = ur.chat_id
                    JOIN themes t ON ur.theme_id = t.id
                    JOIN attributes a ON a.theme_id = t.id
                    %s JOIN user_record_detail urd
                        ON ur.id = urd.record_id AND urd.attribute_id = a.id
                    WHERE uc.user_id=:userId
                      AND uc.id=:chatId
                    ORDER BY
                        t.theme_name ASC,
                        urd.group_id ASC,
                        a.display_order ASC,
                        a.attribute_name ASC
                """.formatted("1".equals(allAttribute) ? "LEFT" : "");

        Map<String, Object> params = Map.of(
                "userId", userId,
                "chatId", chatId
        );

        return jdbc.query(sql, params, this::mapRecordDetail);
    }


    private RecordDetailDto mapRecordDetail(ResultSet rs, int rowNum) throws SQLException {
        RecordDetailDto dto = new RecordDetailDto();
        dto.setThemeId(rs.getLong("theme_id"));
        dto.setThemeName(rs.getString("theme_name"));
        dto.setRecordId(rs.getLong("record_id"));
        dto.setDetailId(rs.getLong("detail_id"));
        dto.setAttributeId(rs.getLong("attribute_id"));
        dto.setAttributeName(rs.getString("attribute_name"));
        dto.setDataType(rs.getString("data_type"));
        dto.setNumberUnit(rs.getString("number_unit"));
        dto.setGroupId(rs.getObject("group_id") == null ? null : rs.getInt("group_id"));
        dto.setValue(rs.getObject("value"));
        return dto;
    }


    @Override
    public List<RecordDetailDto> searchUserRecords(
            Long userId,
            Long themeId,
            String searchValue,
            BigDecimal numericSearchValue,
            LocalDate searchDate,
            LocalDateTime searchDateTime,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String allAttribute,
            int page,
            int size) {

        int validPage = Math.max(page, 0);
        int validSize = size <= 0 ? 20 : size;
        int offset = validPage * validSize;

        FilterContext ctx = buildFilterContext(
                userId, themeId, searchValue, numericSearchValue,
                searchDate, searchDateTime, startTime, endTime
        );

        String sql = """
                WITH filtered_records AS (
                    SELECT ur.id
                    FROM user_record ur
                    JOIN user_chat uc ON ur.chat_id = uc.id
                    JOIN themes t ON ur.theme_id = t.id
                    %s
                    ORDER BY COALESCE(ur.event_date, ur.record_date) DESC, ur.id DESC
                    LIMIT :limit OFFSET :offset
                )
                SELECT
                    t.id AS theme_id,
                    t.theme_name,
                    ur.id AS record_id,
                    urd.id AS detail_id,
                    a.id AS attribute_id,
                    a.attribute_name,
                    a.data_type,
                    urd.number_unit,
                    urd.group_id,
                    COALESCE(urd.string_value, urd.number_value, urd.date_value,
                             urd.boolean_value, JSON_UNQUOTE(urd.json_value)) AS value
                FROM filtered_records fr
                JOIN user_record ur ON ur.id = fr.id
                JOIN themes t ON ur.theme_id = t.id
                JOIN attributes a ON a.theme_id = t.id
                %s JOIN user_record_detail urd
                    ON ur.id = urd.record_id AND urd.attribute_id = a.id
                ORDER BY
                    COALESCE(ur.event_date, ur.record_date) DESC,
                    ur.id DESC,
                    a.display_order ASC,
                    a.attribute_name ASC
                """.formatted(
                ctx.filters,
                "1".equals(allAttribute) ? "LEFT" : ""
        );

        ctx.params.put("limit", validSize);
        ctx.params.put("offset", offset);

        return jdbc.query(sql, ctx.params, this::mapRecordDetail);
    }


    @Override
    public Long countUserRecords(
            Long userId,
            Long themeId,
            String searchValue,
            BigDecimal numericSearchValue,
            LocalDate searchDate,
            LocalDateTime searchDateTime,
            LocalDateTime startTime,
            LocalDateTime endTime) {

        FilterContext ctx = buildFilterContext(
                userId, themeId, searchValue, numericSearchValue,
                searchDate, searchDateTime, startTime, endTime
        );

        String sql = """
                SELECT COUNT(DISTINCT ur.id) AS total
                FROM user_record ur
                JOIN user_chat uc ON ur.chat_id = uc.id
                JOIN themes t ON ur.theme_id = t.id
                %s
                """.formatted(ctx.filters);

        return jdbc.queryForObject(sql, ctx.params, Long.class);
    }


    private FilterContext buildFilterContext(
            Long userId, Long themeId, String searchValue,
            BigDecimal numericSearchValue, LocalDate searchDate,
            LocalDateTime searchDateTime, LocalDateTime startTime,
            LocalDateTime endTime) {

        StringBuilder filters = new StringBuilder("\nWHERE uc.user_id = :userId ");
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);

        if (themeId != null) {
            filters.append("AND ur.theme_id = :themeId ");
            params.put("themeId", themeId);
        }

        // 时间条件（完全等价）
        String recordCond = buildTime("ur.record_date", startTime, endTime, params, "recordStart", "recordEnd");
        String eventCond = buildTime("ur.event_date", startTime, endTime, params, "eventStart", "eventEnd");

        if (recordCond != null && eventCond != null) {
            filters.append("AND (").append(recordCond).append(" OR ").append(eventCond).append(") ");
        } else if (recordCond != null) {
            filters.append("AND ").append(recordCond).append(" ");
        } else if (eventCond != null) {
            filters.append("AND ").append(eventCond).append(" ");
        }

        // 搜索条件（完全等价）
        if (StringUtils.isNotBlank(searchValue) ||
                numericSearchValue != null ||
                searchDateTime != null ||
                searchDate != null) {

            filters.append("AND EXISTS (\n")
                    .append("  SELECT 1 FROM user_record_detail urd_filter\n")
                    .append("  JOIN attributes attr_filter ON urd_filter.attribute_id = attr_filter.id\n")
                    .append("  WHERE urd_filter.record_id = ur.id\n")
                    .append("  AND (\n");

            List<String> condList = new ArrayList<>();

            if (StringUtils.isNotBlank(searchValue)) {
                params.put("searchValueLike", "%" + searchValue + "%");
                condList.add("(attr_filter.data_type='STRING' AND urd_filter.string_value LIKE :searchValueLike)");
            }

            if (numericSearchValue != null) {
                params.put("searchNumber", numericSearchValue);
                condList.add("(attr_filter.data_type='NUMBER' AND urd_filter.number_value = :searchNumber)");
            }

            if (searchDateTime != null) {
                params.put("searchDateTime", searchDateTime);
                condList.add("(attr_filter.data_type='DATETIME' AND urd_filter.date_value = :searchDateTime)");
            }

            if (searchDate != null) {
                params.put("searchDateStart", searchDate.atStartOfDay());
                params.put("searchDateEnd", searchDate.plusDays(1).atStartOfDay());

                condList.add("(attr_filter.data_type='DATETIME' AND DATE(urd_filter.date_value)=:searchDate)");
                params.put("searchDate", searchDate);

                condList.add("(attr_filter.data_type='DATE' AND urd_filter.date_value>=:searchDateStart AND urd_filter.date_value<:searchDateEnd)");
            }

            filters.append("    ")
                    .append(String.join("\n    OR ", condList))
                    .append("\n  ) ) ");
        }

        return new FilterContext(filters.toString(), params);
    }

    private String buildTime(String col, LocalDateTime start, LocalDateTime end,
                             Map<String, Object> params,
                             String startKey, String endKey) {

        List<String> parts = new ArrayList<>();

        if (start != null) {
            params.put(startKey, start);
            parts.add(col + " >= :" + startKey);
        }
        if (end != null) {
            params.put(endKey, end);
            parts.add(col + " <= :" + endKey);
        }

        if (parts.isEmpty()) {
            return null;
        }
        if (parts.size() == 1) {
            return "(" + parts.get(0) + ")";
        }
        return "(" + parts.get(0) + " AND " + parts.get(1) + ")";
    }


    record FilterContext(String filters, Map<String, Object> params) {
    }
}
