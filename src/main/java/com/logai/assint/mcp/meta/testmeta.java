package com.logai.assint.mcp.meta;

import org.springaicommunity.mcp.context.MetaProvider;

import java.util.Map;

public class testmeta implements MetaProvider {
    @Override
    public Map<String, Object> getMeta() {
        return Map.of(
                "openai/widgetPrefersBorder", true,
                "openai/widgetDomain", "https://www.logai.chat"
        );
    }
}