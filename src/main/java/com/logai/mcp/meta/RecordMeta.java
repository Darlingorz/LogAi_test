package com.logai.mcp.meta;

import org.springaicommunity.mcp.context.MetaProvider;

import java.util.Map;

public class RecordMeta implements MetaProvider {
    @Override
    public Map<String, Object> getMeta() {
        return Map.of(
                "openai/outputTemplate", "ui://widget/widget.html"
        );
    }
}