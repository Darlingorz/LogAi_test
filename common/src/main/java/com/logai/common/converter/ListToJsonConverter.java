package com.logai.common.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@WritingConverter
public class ListToJsonConverter implements Converter<List<String>, String> {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String convert(List<String> source) {
        try {
            return mapper.writeValueAsString(source);
        } catch (Exception e) {
            throw new RuntimeException("List<String> 转 JSON 失败", e);
        }
    }
}