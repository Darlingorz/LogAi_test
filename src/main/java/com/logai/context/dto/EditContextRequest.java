package com.logai.context.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EditContextRequest {
    @JsonProperty("info_id")
    private Long infoId;
    @JsonProperty("theme_id")
    private String themeId;
    @JsonProperty("info")
    private String info;
    @JsonProperty("type")
    private String type;
    @JsonProperty("expire_time")
    @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime expireTime;

}
