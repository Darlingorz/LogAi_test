package com.logai.common.utils;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class GenerateRandomUtil {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public String generateId(String prefix) {
        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
        int randomNum = ThreadLocalRandom.current().nextInt(100000, 1000000);
        return prefix + timestamp + randomNum;
    }
}
