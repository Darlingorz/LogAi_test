package com.logai.assint.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.logai.assint.enums.DataType;
import lombok.Data;

@Data
public class SaveRecordDetailRequest {

    @JsonProperty("record_id")
    private Long recordId;

    @JsonProperty("detail_id")
    private Long detailId;

    @JsonProperty("new_value")
    private String newValue;

    @JsonProperty("data_type")
    private DataType dataType;

    @JsonProperty("attribute_id")
    private Long attributeId;
}
