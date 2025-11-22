package com.logai.context.service.impl;

import com.logai.context.entity.OnboardContext;
import com.logai.context.mapper.OnboardContextMapper;
import com.logai.context.service.OnboardContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OnboardContextServiceImpl implements OnboardContextService {
    private final OnboardContextMapper onboardContextMapper;

    @Override
    public List<OnboardContext> getOnboardContexts(List<Long> themeIds) {

        // 过滤重复
        if (themeIds != null && !themeIds.isEmpty()) {
            themeIds = themeIds.stream().distinct().toList();
        }

        return onboardContextMapper.selectByThemeIds(themeIds);
    }
}
