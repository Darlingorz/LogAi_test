package com.logai.assint.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeRange { // 修正点2
    private String startTime;
    private String endTime;
}