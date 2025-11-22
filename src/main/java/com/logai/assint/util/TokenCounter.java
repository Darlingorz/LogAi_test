package com.logai.assint.util;

/**
 * Token计数器类，用于统计AI请求的token使用情况
 */
public class TokenCounter {
    private int promptTokens = 0;
    private int completionTokens = 0;

    public void addPromptTokens(int tokens) {
        this.promptTokens += tokens;
    }

    public void addCompletionTokens(int tokens) {
        this.completionTokens += tokens;
    }

    public int getTotalTokens() {
        return promptTokens + completionTokens;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }
}