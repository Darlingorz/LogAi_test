package com.logai.common.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
@ReadingConverter
public class JsonToListConverter implements Converter<String, List<String>> {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public List<String> convert(String source) {
        if (!StringUtils.hasText(source)) {
            return List.of();
        }
        try {
            return mapper.readValue(source, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            throw new RuntimeException("JSON 转 List<String> 失败: " + source, e);
        }
    }
}
