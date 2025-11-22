package com.logai.assint.util;

public class AiResponseCleaner {

    /**
     * 从AI响应中提取纯净的JSON字符串，处理Markdown代码块和其他 extraneous text。
     *
     * @param aiResponse The raw string response from the AI model.
     * @return A clean JSON string, ready for parsing. Returns an empty JSON array "[]" if the input is null or invalid.
     */
    public static String extractJsonString(String aiResponse) {
        // 1. 防御null或空字符串
        if (aiResponse == null || aiResponse.trim().isEmpty()) {
            return "[]";
        }

        String cleaned = aiResponse.trim();

        // 2. 查找JSON内容的实际起始位置
        int startPos = -1;
        int jsonArrayStart = cleaned.indexOf('[');
        int jsonObjectStart = cleaned.indexOf('{');

        if (jsonArrayStart != -1 && jsonObjectStart != -1) {
            startPos = Math.min(jsonArrayStart, jsonObjectStart);
        } else if (jsonArrayStart != -1) {
            startPos = jsonArrayStart;
        } else {
            startPos = jsonObjectStart;
        }

        // 如果找不到JSON的起始符号，返回空数组
        if (startPos == -1) {
            return "[]";
        }

        // 3. 查找JSON内容的实际结束位置
        int endPos = -1;
        int jsonArrayEnd = cleaned.lastIndexOf(']');
        int jsonObjectEnd = cleaned.lastIndexOf('}');

        endPos = Math.max(jsonArrayEnd, jsonObjectEnd);

        // 如果起始和结束位置有效，截取纯净的JSON字符串
        if (endPos > startPos) {
            return cleaned.substring(startPos, endPos + 1);
        }

        return "[]";
    }
}