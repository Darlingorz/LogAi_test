package com.logai.assint.service;

import com.logai.assint.dto.GlobalAiAssintResponse;
import com.logai.assint.util.TokenCounter;
import com.logai.user.entity.User;

import java.util.List;

public interface AssistService {

    List<GlobalAiAssintResponse> globalChat(User user, String message, String intentArray);

    GlobalAiAssintResponse handleAnalysisIntent(User user, String message, TokenCounter counter);

    GlobalAiAssintResponse handleRecordIntent(User user, String message, TokenCounter counter);
}