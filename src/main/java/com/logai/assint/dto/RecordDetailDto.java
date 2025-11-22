package com.logai.assint.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class RecordDetailDto {

    @JsonProperty("record_id")
    private Long recordId;

    @JsonProperty("detail_id")
    private Long detailId;

    @JsonProperty("theme_id")
    private Long themeId;

    @JsonProperty("theme_name")
    private String themeName;

    @JsonProperty("attribute_id")
    private Long attributeId;

    @JsonProperty("attribute_name")
    private String attributeName;

    @JsonProperty("data_type")
    private String dataType;

    @JsonProperty("number_unit")
    private String numberUnit;

    @JsonProperty("group_id")
    private Integer groupId;

    @JsonProperty("value")
    private Object value;

}
