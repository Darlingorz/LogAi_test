package com.logai.common.utils;

import jakarta.servlet.http.HttpServletRequest;

public class HttpRequestUtil {
    // 可能包含客户端真实 IP 的 HTTP 头（按优先级排序）
    private static final String[] IP_HEADER_CANDIDATES = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "X-Client-IP",
            "X-Cluster-Client-IP"
    };

    /**
     * 获取客户端 IP 地址（支持经过反向代理）
     *
     * @param request HttpServletRequest
     * @return 客户端真实 IP
     */
    public static String getClientIp(HttpServletRequest request) {

        for (String header : IP_HEADER_CANDIDATES) {
            String ipList = request.getHeader(header);

            if (ipList != null && !ipList.isEmpty() && !"unknown".equalsIgnoreCase(ipList)) {
                // 可能有多个 IP（代理链），取第一个
                return ipList.split(",")[0].trim();
            }
        }

        // 直接从 request 中取
        String remoteIp = request.getRemoteAddr();
        return (remoteIp != null) ? remoteIp : "unknown";
    }
}
