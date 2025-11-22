package com.logai.context.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class WorkFlowInitDto {
    @JsonProperty("theme_name")
    private String themeName;

    @JsonProperty("theme_id")
    private long themeId;

    private List<String> workflow;
}
