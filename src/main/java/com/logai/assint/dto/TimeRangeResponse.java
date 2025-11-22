package com.logai.assint.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeRangeResponse {
    private List<TimeRange> eventDate;
    private List<TimeRange> recordDate;
}