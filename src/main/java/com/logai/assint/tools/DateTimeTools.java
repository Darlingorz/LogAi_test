package com.logai.assint.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.lang.Nullable;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.zone.ZoneRulesException;

/**
 * 日期时间工具类
 * 提供获取当前日期时间的功能
 */
@Slf4j
public class DateTimeTools {

    @Tool(name = "get_current_time",
            description = "获取当前日期和时间，用于解析相对时间。此工具可接受一个可选的 'timeZone' 字符串参数（例如 'America/New_York'），如果未提供该参数，则使用服务器的系统默认时区。")
    public String getCurrentTime(@Nullable String timeZone) {
        ZoneId zoneId;
        try {
            // 如果传入了有效的时区ID，则使用它
            if (timeZone != null && !timeZone.isBlank()) {
                zoneId = ZoneId.of(timeZone);
            } else {
                // 否则，使用系统默认时区
                zoneId = ZoneId.systemDefault();
            }
        } catch (ZoneRulesException e) {
            // 如果传入的时区ID无效，记录警告并回退到系统默认时区
            log.warn("Invalid timeZone '{}' provided. Falling back to system default.", timeZone);
            zoneId = ZoneId.systemDefault();
        }

        // 使用 ZonedDateTime 直接获取带时区的时间，代码更简洁
        ZonedDateTime now = ZonedDateTime.now(zoneId);

        // 始终返回标准的 ISO_ZONED_DATE_TIME 格式，这对 AI 解析最友好
        return now.format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
    }
}