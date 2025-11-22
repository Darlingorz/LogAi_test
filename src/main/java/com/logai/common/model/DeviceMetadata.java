package com.logai.common.model;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DeviceMetadata {

    String deviceId;
    String ipAddress;
    String userAgent;

    public static DeviceMetadata from(HttpServletRequest request, String providedDeviceId) {
        String deviceId = providedDeviceId == null || providedDeviceId.isBlank()
                ? "web" : providedDeviceId.trim();
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr()
                : forwarded.split(",")[0].trim();
        String agent = request.getHeader("User-Agent");
        return DeviceMetadata.builder()
                .deviceId(deviceId)
                .ipAddress(ip)
                .userAgent(agent)
                .build();
    }
}
