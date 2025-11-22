package com.logai.assint.service;

import com.logai.assint.entity.ChatInteraction;
import com.logai.assint.enums.IntentType;
import com.logai.assint.util.TokenCounter;
import com.logai.user.entity.User;

import java.util.List;

public interface AssistService {

    List<String> globalChat(User user, String message, String sessionId, String intentArray);

    List<String> handleIntentByType(User user, String message, String sessionId, IntentType intentType, TokenCounter counter);

    List<ChatInteraction> getChatHistoryBySessionId(String sessionId);
}