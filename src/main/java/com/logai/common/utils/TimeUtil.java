package com.logai.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class TimeUtil {
    public static final String TIMEZONE_SHANGHAI = "Asia/Shanghai";

    private static final List<DateTimeFormatter> FLEXIBLE_DATETIME_FORMATTERS = Arrays.asList(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyyMMdd HHmmss"),
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
    );

    private static final List<DateTimeFormatter> FLEXIBLE_DATE_FORMATTERS = Arrays.asList(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyyMMdd")
    );

    public static final String SUPPORTED_DATETIME_FORMAT_EXAMPLES =
            "2024-01-01T00:00:00, 2024-01-01 00:00:00, or 2024/01/01 00:00:00"; // 2024-01-01T00:00:00、2024-01-01 00:00:00 或 2024/01/01 00:00:00


    /**
     * 获取指定时区的当前时间
     *
     * @param timezone 时区
     * @return 当前时间
     */
    public LocalDateTime getNowInTimezone(String timezone) {
        if (timezone == null || timezone.isEmpty()) {
            timezone = TIMEZONE_SHANGHAI;
        }
        try {
            ZoneId zoneId = ZoneId.of(timezone);
            ZonedDateTime now = ZonedDateTime.now(zoneId);
            return now.toLocalDateTime();
        } catch (Exception e) {
            log.error("获取时区时间失败，使用默认时区 Asia/Shanghai", e);
            return LocalDateTime.now();
        }
    }

    public LocalDateTime parseFlexibleDateTime(String value) {
        LocalDateTime parsed = tryParseFlexibleDateTime(value);
        if (parsed == null && StringUtils.isNotBlank(value)) {
            throw new IllegalArgumentException("Invalid time format. Please use one of the supported formats: " + SUPPORTED_DATETIME_FORMAT_EXAMPLES); // 时间格式不正确，请使用以下任一格式：
        }
        return parsed;
    }

    public LocalDateTime tryParseFlexibleDateTime(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        for (DateTimeFormatter formatter : FLEXIBLE_DATETIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    public LocalDate tryParseFlexibleDate(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        for (DateTimeFormatter formatter : FLEXIBLE_DATE_FORMATTERS) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }
}
