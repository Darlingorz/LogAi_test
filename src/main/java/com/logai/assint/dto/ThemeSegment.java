package com.logai.assint.dto;

import lombok.Data;

import java.util.List;

@Data
public class ThemeSegment {
    private String theme;
    private List<PromptItem> prompts;

    @Data
    public static class PromptItem {
        private String prompt;
        private String eventTime;
    }
}
