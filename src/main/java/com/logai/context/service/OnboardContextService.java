package com.logai.context.service;

import com.logai.context.entity.OnboardContext;

import java.util.List;

public interface OnboardContextService {

    List<OnboardContext> getOnboardContexts(List<Long> themeIds);
}