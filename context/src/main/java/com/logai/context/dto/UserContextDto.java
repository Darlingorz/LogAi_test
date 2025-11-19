package com.logai.context.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class UserContextDto {

    @JsonProperty("common_info")
    private List<CommonInfoDTO> commonInfo;

    @JsonProperty("contexts")
    private List<ContextEntryDTO> contexts;

    @JsonProperty("user_time")
    private String userTime;

    @Data
    public static class ContextEntryDTO {
        @JsonProperty("theme_name")
        private String themeName;

        @JsonProperty("theme_id")
        private Long themeId;

        @JsonProperty("context")
        private ContextDetailDTO context;
    }

    @Data
    public static class CommonInfoDTO {
        private String info;
        @JsonProperty("info_id")
        private String infoId;
    }

    @Data
    public static class ContextDetailDTO {
        @JsonProperty("temp_info")
        private List<InfoItemDTO> tempInfo;
        @JsonProperty("theme_context_info")
        private List<InfoItemDTO> themeContextInfo;

        private List<InfoItemDTO> workflow;
    }

    @Data
    public static class InfoItemDTO {
        private String info;
        @JsonProperty("expire_time")
        private String expireTime;
        @JsonProperty("info_id")
        private String infoId;
    }
}
