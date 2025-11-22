package com.logai.assint.service.impl;

import cn.hutool.core.convert.Convert;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.logai.assint.dto.*;
import com.logai.assint.entity.*;
import com.logai.assint.enums.DataType;
import com.logai.assint.enums.IntentType;
import com.logai.assint.mapper.*;
import com.logai.assint.service.AssistService;
import com.logai.assint.util.AiResponseCleaner;
import com.logai.assint.util.TokenCounter;
import com.logai.common.exception.BusinessException;
import com.logai.common.exception.ReactiveBusinessException;
import com.logai.common.utils.TimeUtil;
import com.logai.creem.entity.MembershipFeature;
import com.logai.creem.mapper.MembershipFeatureMapper;
import com.logai.user.entity.User;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssistServiceImpl implements AssistService {
    private static final String STATUS_PROCESSING = "processing";
    private static final String STATUS_COMPLETED = "completed";
    private static final String STATUS_ERROR = "error";
    private static final DateTimeFormatter EVENT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ChatClient intentChatClient;
    private final ChatClient themeChatClient;
    private final ChatClient attributeChatClient;
    private final ChatClient generalChatClient;
    private final ChatClient analysisChatClient;
    private final ChatClient analysisThemeChatClient;
    private final ChatClient generateDateRangeChatClient;
    private final UserChatMapper userChatMapper;
    private final ThemeMapper themeMapper;
    private final AttributeMapper attributeMapper;
    private final UserRecordDetailMapper recordAttributeValueMapper;
    private final UserRecordMapper userRecordMapper;
    private final MembershipFeatureMapper membershipFeatureMapper;
    private final ChatInteractionMapper chatInteractionMapper;
    private final TransactionalOperator transactionalOperator;
    private final TimeUtil timeUtil;

    /**
     * AI全局对话接口 - 增强版
     * <p>
     * 改进点：
     * 1. 增强参数验证
     * 2. 完善的错误处理（异常向上抛出，不内部消化）
     * 3. 响应式编程优化
     * 4. 性能监控
     * 5. 超时处理
     * 6. SSE流式响应的异常处理
     *
     * @param user      用户
     * @param message   用户输入的消息内容
     * @param sessionId 会话ID
     * @return AI助手的响应内容
     * @throws BusinessException 业务异常
     * @throws TimeoutException  超时异常
     */
    @Override
    public List<String> globalChat(User user, String message, String sessionId, String intentArray) {
        // 参数验证 - 直接抛出异常，让全局异常处理器处理
        Long userId = user.getId();
        if (StringUtils.isBlank(message)) {
            // 消息内容不能为空
            throw BusinessException.validationError("message", "Message content cannot be empty");
        }
        if (StringUtils.isBlank(sessionId)) {
            // 会话ID不能为空
            throw BusinessException.validationError("sessionId", "Session ID cannot be empty");
        }
        TokenCounter counter = new TokenCounter();
        long startTime = System.currentTimeMillis();

        log.info("开始处理AI对话请求 - 用户ID: {}, 会话ID: {}, 消息长度: {}", userId, sessionId, message.length());

        // 主要的处理逻辑 - 异常向上抛出
        // 直接传递响应式业务异常
        Flux<IntentType> intentFlux;
        List<IntentType> providedIntents = parseIntentArray(intentArray);
        if (!providedIntents.isEmpty()) {
            intentFlux = Flux.fromIterable(providedIntents);
            log.debug("使用前端提供的意图类型: {} - 用户ID: {}", providedIntents, userId);
        } else {
            intentFlux = analyzeIntent(message, counter);
        }

        return intentFlux
                .switchIfEmpty(Mono.error(BusinessException.aiServiceError("IntentAnalysis", "Intent analysis failed"))) // 意图分析失败
                .flatMap(intentType -> {
                    log.debug("识别到的意图类型: {} - 用户ID: {}", intentType, userId);
                    return handleIntentByType(user, message, sessionId, intentType, counter);

                })
                .doOnNext(response -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.debug("AI响应生成成功 - 用户ID: {}, 响应长度: {}, 耗时: {}ms", userId, response.length(), duration);
                })
                .doOnError(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.error("AI响应生成失败 - 用户ID: {}, 会话ID: {}, 耗时: {}ms, 输入Tokens: {}, 输出Tokens: {}, 总Tokens: {}, 错误类型: {}, 错误: {}",
                            userId,
                            sessionId,
                            duration,
                            counter.getPromptTokens(),
                            counter.getCompletionTokens(),
                            counter.getTotalTokens(),
                            error.getClass().getSimpleName(),
                            error.getMessage(),
                            error);
                })
                .timeout(Duration.ofSeconds(180))
                .onErrorMap(TimeoutException.class, e -> {
                    log.error("AI对话请求超时 - 用户ID: {}, 会话ID: {}", userId, sessionId);
                    return BusinessException.timeout("AI conversation", 60000); // AI对话
                })
                .onErrorMap(JSONException.class, e -> {
                    log.error("JSON解析异常 - 用户ID: {}, 错误: {}", userId, e.getMessage(), e);
                    return BusinessException.withDetail("JSON_PARSE_ERROR", "Data format parsing failed", e.getMessage()); // 数据格式解析失败
                })
                .onErrorMap(ReactiveBusinessException.class, ReactiveBusinessException::getCause)
                .doFinally(signalType -> logAiInteractionSummary(userId, sessionId, startTime, counter, signalType));
    }

    /**
     * 根据意图类型处理请求
     * 异常向上抛出，不内部消化
     */
    @Override
    public Flux<String> handleIntentByType(User user, String message, String sessionId,
                                           IntentType intentType, TokenCounter counter) {
        return saveUserInput(user, sessionId, message, intentType.name())
                // 保存用户输入失败
                .switchIfEmpty(Mono.error(BusinessException.databaseError("SaveUserInput", "Failed to save user input")))
                .flatMapMany(parentId -> {
                    log.debug("用户输入已保存 - 记录ID: {}, 意图: {}", parentId, intentType);
                    Long userId = user.getId();
                    return switch (intentType) {
                        case CHAT -> handleChatIntent(userId, message, sessionId, counter);
                        case ANALYZE -> handleAnalysisIntent(user, message, sessionId, counter);
                        case RECORD -> handleRecordIntentAsync(user, message, sessionId, counter);
                        default -> handleIllegalIntent(userId, sessionId, counter);
                    };
                });

    }

    /**
     * 处理聊天意图
     * 异常向上抛出，不内部消化
     */
    private String handleChatIntent(Long userId, String message, String sessionId,
                                    TokenCounter counter) {
        createUserChat(userId, message, sessionId, "chat");
        String fullResponse = generalChatClient.prompt()
                .user(message)
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, sessionId))
                .call()
                .content();
        saveAiResponse(userId, sessionId, fullResponse, IntentType.CHAT.name(), counter);
        return fullResponse;
    }

    /**
     * 处理分析意图
     * 异常向上抛出，不内部消化
     */
    private String handleAnalysisIntent(User user, String message, String sessionId,
                                        TokenCounter counter) {
        Long userId = user.getId();
        enforceUsageLimit(user, "text_analysis", "analysis");
        List<AnalysisResponse> analysisResponses = processAnalysisIntent(user, message, sessionId, counter);
        if (analysisResponses.isEmpty()) {
            String emptyMsg = "根据您的请求，我没有找到可以分析的数据。";
            GlobalAiAssintResponse emptyDto = new GlobalAiAssintResponse(IntentType.ANALYZE, emptyMsg);
            String responseJson = JSON.toJSONString(emptyDto);
            saveAiResponse(userId, sessionId, responseJson, IntentType.ANALYZE.name(), counter);
            return responseJson;
        }
        String response = JSON.toJSONString(new GlobalAiAssintResponse(IntentType.ANALYZE, analysisResponses));
        saveAiResponse(userId, sessionId, response, IntentType.ANALYZE.name(), counter);
        return response;
    }

    /**
     * 处理记录意图
     * 异常向上抛出，不内部消化
     */
    private Flux<String> handleRecordIntentAsync(User user, String message, String sessionId,
                                                 TokenCounter counter) {
        Long userId = user.getId();

        return enforceUsageLimit(user, "text_record", "record")
                .thenMany(createUserChat(userId, message, sessionId, "record", STATUS_PROCESSING)
                        .flatMapMany(userChat -> {
                            Mono<List<ManualRecordResponse>> recordProcessing = processRecordIntent(user, message, userChat, counter)
                                    .collectList()
                                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)));

                            Mono<Void> asyncTask = recordProcessing
                                    .flatMap(manualResponses -> {
                                        Object data;
                                        if (manualResponses == null || manualResponses.isEmpty()) {
                                            data = "我没有从您的话中识别出可以记录的内容。";
                                        } else {
                                            data = manualResponses;
                                        }

                                        String responseJson = JSON.toJSONString(new GlobalAiAssintResponse(IntentType.RECORD, data));
                                        return saveAiResponse(userId, sessionId, responseJson, IntentType.RECORD.name(), counter)
                                                .then(updateUserChatStatus(userChat.getId(), STATUS_COMPLETED))
                                                .then();
                                    })
                                    .onErrorResume(error -> updateUserChatStatus(userChat.getId(), STATUS_ERROR)
                                            .then(Mono.fromRunnable(() -> log.error("记录意图异步处理失败 - 用户ID: {}, 聊天ID: {}, 错误: {}",
                                                    userId,
                                                    userChat.getId(),
                                                    error.getMessage(),
                                                    error))))
                                    .then();

                            asyncTask.subscribeOn(Schedulers.boundedElastic()).subscribe();

                            GlobalAiAssintResponse ackResponse = new GlobalAiAssintResponse(IntentType.RECORD, Map.of("chatId", userChat.getId()));
                            return Flux.just(JSON.toJSONString(ackResponse));
                        }));
    }

    /**
     * 处理非法意图
     */
    private String handleIllegalIntent(Long userId, String sessionId, TokenCounter counter) {
        String response = "我不理解你的意思，请重新输入。";
        saveAiResponse(userId, sessionId, response, IntentType.ILLEGAL.name(), counter)
        return response;
    }

    /**
     * 校验用户在指定功能下的使用次数限制
     */
    private void enforceUsageLimit(User user, String featureKey, String conversationType) {
        Integer membershipId = user.getRole();
        if (membershipId == null) {
            return;
        }
        MembershipFeature feature = membershipFeatureMapper.findByMembershipIdAndFeatureKey(Convert.toLong(membershipId), featureKey);
        Integer dailyLimit = feature.getDailyLimit();
        Integer monthlyLimit = feature.getMonthlyLimit();
        if ((dailyLimit == null || dailyLimit <= 0) && (monthlyLimit == null || monthlyLimit <= 0)) {
            return;
        }
        LocalDate today = timeUtil.getNowInTimezone(user.getTimeZone()).toLocalDate();
        String actionName = resolveActionName(conversationType);

        if (dailyLimit != null && dailyLimit > 0) {
            Long count = userChatMapper.countByUserIdAndConversationTypeAndRecordDate(user.getId(), conversationType, today);
            if (count >= dailyLimit) {
                throw BusinessException.withDetail(
                        "USAGE_LIMIT_EXCEEDED",
                        String.format("Today's %s usage has reached the limit", actionName), // 今日%s次数已达上限
                        String.format("membershipId=%d, featureKey=%s, limit=%d, count=%d",
                                membershipId, featureKey, dailyLimit, count)
                );
            }
        }

        if (monthlyLimit != null && monthlyLimit > 0) {
            LocalDate startOfMonth = today.withDayOfMonth(1);
            LocalDate nextMonth = startOfMonth.plusMonths(1);
            Long count = userChatMapper.countByUserIdAndConversationTypeBetweenDates(user.getId(), conversationType, startOfMonth, nextMonth);
            if (count >= monthlyLimit) {
                throw BusinessException.withDetail(
                        "USAGE_LIMIT_EXCEEDED",
                        String.format("This month's %s usage has reached the limit", actionName), // 本月%s次数已达上限
                        String.format("membershipId=%d, featureKey=%s, limit=%d, count=%d",
                                membershipId, featureKey, monthlyLimit, count)
                );
            }
        }
    }

    private String resolveActionName(String conversationType) {
        return switch (conversationType) {
            case "record" -> "record"; // 记录
            case "analysis" -> "analysis"; // 分析
            default -> "operation"; // 操作
        };
    }

    /**
     * 创建用户对话记录
     */
    private UserChat createUserChat(Long userId, String message, String sessionId, String conversationType) {
        return createUserChat(userId, message, sessionId, conversationType, STATUS_COMPLETED);
    }

    private UserChat createUserChat(Long userId, String message, String sessionId, String conversationType, String status) {
        UserChat userChat = new UserChat();
        userChat.setUserId(userId);
        userChat.setOriginalContent(message);
        userChat.setSessionId(sessionId);
        userChat.setConversationType(conversationType);
        userChat.setStatus(status);
        userChat.setRecordDate(LocalDate.now());
        userChat.setCreatedAt(LocalDateTime.now());
        userChat.setUpdatedAt(LocalDateTime.now());
        userChatMapper.insert(userChat);
        return userChat;
    }

    private UserChat updateUserChatStatus(Long chatId, String status) {
        UserChat userChat = userChatMapper.selectById(chatId);
        if (userChat == null) {
            log.warn("未找到需要更新状态的用户对话记录，chatId={}", chatId);
            return new UserChat();
        }
        userChat.setStatus(status);
        userChat.setUpdatedAt(LocalDateTime.now());
        userChatMapper.updateById(userChat);
        return userChat;
    }

    /**
     * 创建用户记录
     */
    private UserRecord createUserRecord(User user, Long chatId, Long themeId, String eventTimeText) {
        UserRecord userRecord = new UserRecord();
        userRecord.setUserId(user.getId());
        userRecord.setChatId(chatId);
        userRecord.setThemeId(themeId);
        LocalDateTime now = timeUtil.getNowInTimezone(user.getTimeZone());
        userRecord.setRecordDate(now);
        LocalDateTime eventDate = resolveEventDate(eventTimeText, now);
        userRecord.setEventDate(eventDate);
        userRecord.setCreatedAt(LocalDateTime.now());
        userRecord.setUpdatedAt(LocalDateTime.now());
        userRecordMapper.insert(userRecord);
        return userRecord;
    }

    private LocalDateTime resolveEventDate(String eventTimeText, LocalDateTime fallback) {
        if (StringUtils.isNotBlank(eventTimeText)) {
            LocalDateTime parsedDateTime = timeUtil.tryParseFlexibleDateTime(eventTimeText);
            if (parsedDateTime != null) {
                return parsedDateTime;
            }
            LocalDate parsedDate = timeUtil.tryParseFlexibleDate(eventTimeText);
            if (parsedDate != null) {
                return parsedDate.atStartOfDay();
            }
            log.warn("Failed to parse event time: {}. Using fallback time instead.", eventTimeText);
        }
        return fallback;
    }


    private List<IntentType> parseIntentArray(String intentArray) {
        if (StringUtils.isBlank(intentArray)) {
            return Collections.emptyList();
        }

        String trimmed = intentArray.trim();
        if (trimmed.isEmpty()) {
            return Collections.emptyList();
        }

        String[] segments = trimmed.split("[,，]");
        List<String> rawIntentValues = new ArrayList<>(segments.length);
        for (String segment : segments) {
            if (segment == null) {
                continue;
            }
            String cleaned = segment.trim();
            if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length() > 1) {
                cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
            }
            if (!cleaned.isEmpty()) {
                rawIntentValues.add(cleaned);
            }
        }

        return rawIntentValues.stream()
                .map(value -> value.replace("\"", "").trim())
                .filter(value -> !value.isEmpty())
                .map(value -> {
                    try {
                        return IntentType.valueOf(value.toUpperCase());
                    } catch (IllegalArgumentException ex) {
                        log.warn("无法识别的意图类型: {}", value);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }


    /**
     * 分析用户消息意图
     * 使用意图分析AI判断用户消息的类型，支持四种意图：
     * - RECORD：用户想要记录信息
     * - ANALYZE：用户想要分析已有记录
     * - CHAT：普通对话或咨询
     * - ILLEGAL：非法意图
     * <p>
     * 异常向上抛出，不内部消化
     */
    private List<IntentType> analyzeIntent(String message, TokenCounter counter) {
        String content;
        try {
            ChatResponse response = intentChatClient.prompt()
                    .user(message)
                    .call()
                    .chatResponse();

            processTokenUsage(response, counter);
            content = response.getResult().getOutput().getText();

            if (StringUtils.isBlank(content)) {
                throw BusinessException.aiServiceError("IntentAnalysis", "Intent analysis AI returned empty content"); // 意图分析AI返回内容为空
            }

        } catch (Exception e) {
            log.error("调用意图分析AI时发生异常: {}", e.getMessage(), e);
            throw BusinessException.aiServiceError("IntentAnalysis",
                    String.format("Intent analysis failed: %s", e.getMessage())); // 意图分析失败
        }

        return Arrays.stream(content.split(",")).map(String::trim).map(intentStr -> {
            try {
                return IntentType.valueOf(intentStr);
            } catch (IllegalArgumentException e) {
                log.warn("无法解析AI返回的意图: '{}', 将其视为ILLEGAL", intentStr);
                return IntentType.ILLEGAL;
            }
        }).distinct().toList();
    }


    /**
     * [REFACTORED] 处理记录意图
     * 整个流程现在被包裹在一个事务中。
     * 调用方需提前创建 user_chat 记录并传入 originalRecord。
     * 在事务中执行以下步骤：
     * a. 调用AI提取主题。
     * b. 查找或创建主题。
     * c. 调用AI提取属性。
     * d. 创建主记录 (UserRecord)。
     * e. 查找或创建属性定义。
     * f. 保存属性值 (UserRecordDetail)。
     * 若流程中任何一步失败，所有在事务中的数据库操作都会被回滚。
     */
    public Flux<ManualRecordResponse> processRecordIntent(User user, String message, UserChat originalRecord, TokenCounter counter) {
        Long userId = user.getId();
        Flux<ManualRecordResponse> transactionalFlow = extractThemesWithSegments(message, user, counter)
                .flatMap(themeSegment ->
                        findOrCreateTheme(userId, themeSegment.getTheme())
                                .flatMapMany(theme -> {
                                    List<ThemeSegment.PromptItem> promptItems = Optional.ofNullable(themeSegment.getPrompts())
                                            .orElse(Collections.emptyList());

                                    // 2b. 为主题中的每个语句提取属性
                                    return Flux.fromIterable(promptItems)
                                            .filter(promptItem -> promptItem != null && StringUtils.isNotBlank(promptItem.getPrompt()))
                                            .flatMap(promptItem -> extractAttributesManual(promptItem.getPrompt(), theme, counter)
                                                    .map(response -> new AbstractMap.SimpleEntry<>(promptItem, response)))
                                            .filter(entry -> entry.getValue() != null
                                                    && entry.getValue().getRecords() != null
                                                    && !entry.getValue().getRecords().isEmpty())
                                            // 2c. 为每个提取出的记录条目，将其完整地保存到数据库
                                            .flatMap(entry ->
                                                    Flux.fromIterable(entry.getValue().getRecords())
                                                            .flatMap(recordEntry ->
                                                                    // 为此条目创建主记录
                                                                    createUserRecord(user, originalRecord.getId(), theme.getId(), entry.getKey().getEventTime())
                                                                            .flatMapMany(newRecord ->
                                                                                    // 保存其所有属性值
                                                                                    saveRecordAttributes(userId, newRecord.getId(), theme.getId(), recordEntry.getAttributes())
                                                                                            // 保存成功后，组装响应对象
                                                                                            .then(Mono.just(createManualResponse(theme.getThemeName(), originalRecord.getId(), recordEntry, newRecord.getEventDate())))
                                                                            )
                                                            )
                                            );
                                })
                );

        // 步骤 3: 使用 TransactionalOperator 执行整个流程，确保其原子性
        return transactionalFlow.as(transactionalOperator::transactional)
                .doOnError(error -> log.error("记录意图的事务处理失败，将进行回滚: {}", error.getMessage()));
    }

    /**
     * 创建手动响应对象
     */
    private ManualRecordResponse createManualResponse(String themeName, Long chatId, ManualRecordResponse.ManualRecordEntry record, LocalDateTime eventDate) {
        ManualRecordResponse response = new ManualRecordResponse();
        response.setChatId(chatId);
        response.setThemeName(themeName);
        if (record != null) {
            record.setEventTime(eventDate != null ? EVENT_TIME_FORMATTER.format(eventDate) : null);
        }
        response.setRecords(List.of(record));
        return response;
    }

    /**
     * 保存记录的属性值
     */
    private List<UserRecordDetail> saveRecordAttributes(Long userId, Long recordId, Long themeId,
                                                        List<ManualRecordResponse.AttributeValue> attributes) {
        List<UserRecordDetail> userRecordDetails = new ArrayList<>();
        for (ManualRecordResponse.AttributeValue attribute : attributes) {
            UserRecordDetail userRecordDetail = createAndSaveAttributeValue(userId, recordId, themeId, attribute);
            userRecordDetails.add(userRecordDetail);
        }
        return userRecordDetails;

    }

    /**
     * 创建并保存单个属性值
     */
    private UserRecordDetail createAndSaveAttributeValue(Long userId, Long recordId, Long themeId,
                                                         ManualRecordResponse.AttributeValue attr) {
        Attribute attribute = findOrCreateAttribute(userId, themeId, attr.getAttributeName(), DataType.valueOf(attr.getDataType()));
        UserRecordDetail value = new UserRecordDetail();
        value.setRecordId(recordId);
        value.setGroupId(attr.getGroupId());
        value.setAttributeId(attribute.getId());
        value.setNumberUnit(attr.getUnit());
        value.setCreatedAt(LocalDateTime.now());
        value.setUpdatedAt(LocalDateTime.now());
        setAttributeValueByType(value, attr.getValue(), DataType.valueOf(attr.getDataType()));
        recordAttributeValueMapper.insert(value);
        return value;
    }

    /**
     * 根据数据类型设置属性值
     */
    private void setAttributeValueByType(UserRecordDetail value, String attrValue, DataType dataType) {
        switch (dataType) {
            case NUMBER:
                try {
                    value.setNumberValue(Double.parseDouble(attrValue));
                } catch (NumberFormatException e) {
                    value.setStringValue(attrValue);
                }
                break;
            case BOOLEAN:
                value.setBooleanValue(Boolean.parseBoolean(attrValue));
                break;
            case DATE:
                try {
                    value.setDateValue(timeUtil.tryParseFlexibleDate(attrValue).atTime(0, 0, 0, 0));
                } catch (Exception e) {
                    value.setStringValue(attrValue);
                }
                break;
            case DATETIME:
                try {
                    value.setDateValue(timeUtil.tryParseFlexibleDateTime(attrValue));
                } catch (Exception e) {
                    value.setStringValue(attrValue);
                }
                break;
            case STRING:
            default:
                value.setStringValue(attrValue);
        }
    }


    /**
     * 提取用户主题并按主题拆分语句
     */
    private List<ThemeSegment> extractThemesWithSegments(String message, User user, TokenCounter counter) {
        List<Theme> themes = themeMapper.queryByUserIdOrIsPublic(user.getId());
        String themesPrompt;
        if (themes.isEmpty()) {
            themesPrompt = "无现有主题";
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("| theme_name | theme_id | theme_description |\n");
            sb.append("| :------- | :------ | :------- |\n");
            for (Theme theme : themes) {
                sb.append("| ")
                        .append(theme.getThemeName()).append(" | ")
                        .append(theme.getId()).append(" | ")
                        .append(theme.getDescription()).append(" |\n");
            }
            themesPrompt = sb.toString();
        }
        String content = null;
        try {
            ChatResponse response = themeChatClient.prompt()
                    .templateRenderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
                    .system(prompt -> {
                        prompt.param("themesPrompt", themesPrompt);
                        prompt.param("timeZone", user.getTimeZone());
                    })
                    .user(message)
                    .call()
                    .chatResponse();

            processTokenUsage(response, counter);


            if (response != null) {
                content = AiResponseCleaner.extractJsonString(response.getResult().getOutput().getText());
                if (content.trim().isEmpty()) {
                    log.warn("主题提取结果为空，用户消息: {}", message);
                    throw BusinessException.aiServiceError("ThemeExtraction", "Theme extraction AI returned empty content"); // 主题提取AI返回内容为空
                }
            }
        } catch (Exception e) {
            log.error("提取主题时出错: {}", e.getMessage(), e);
            throw BusinessException.aiServiceError("ThemeExtraction",
                    String.format("Theme extraction failed: %s", e.getMessage())); // 主题提取失败
        }

        try {
            List<ThemeSegment> segments = JSON.parseArray(content, ThemeSegment.class);
            return segments;
        } catch (JSONException e) {
            log.error("解析主题JSON失败 - 内容: {}, 错误: {}", content, e.getMessage(), e);
            throw BusinessException.withDetail("JSON_PARSE_ERROR", "Theme data parsing failed",
                    String.format("JSON content: %s, error: %s", content, e.getMessage())); // JSON内容: %s, 错误: %s
        }
    }

    /**
     * 提取属性（手动拼接模式）
     *
     * @param message 用户消息内容
     * @param theme   主题对象
     * @return 返回提取到的属性Mono
     */
    private ManualRecordResponse extractAttributesManual(String message, Theme theme, TokenCounter counter) {
        List<Attribute> attributes = attributeMapper.findByThemeId(theme.getId());

        String attributesPrompt;
        if (attributes.isEmpty()) {
            attributesPrompt = "无属性";
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("| attribute_name | attribute_id | attributes_description | data_type |\n");
            sb.append("| :------- | :------ | :------- | :------- |\n");
            for (Attribute attr : attributes) {
                sb.append("| ")
                        .append(attr.getAttributeName()).append(" | ")
                        .append(attr.getId()).append(" | ")
                        .append(attr.getDescription() != null ? attr.getDescription() : "").append(" | ")
                        .append(attr.getDataType()).append(" |\n");
            }
            attributesPrompt = sb.toString();
        }
        String content;
        try {
            ChatResponse response = attributeChatClient.prompt()
                    .templateRenderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
                    .system(prompt -> {
                        prompt.param("theme", theme.getThemeName());
                        prompt.param("attributesPrompt", attributesPrompt);
                    })
                    .user(message + "\n请提取与" + theme.getThemeName() + "主题直接相关的属性")
                    .call()
                    .chatResponse();

            processTokenUsage(response, counter);
            content = AiResponseCleaner.extractJsonString(response.getResult().getOutput().getText());

            if (content.trim().isEmpty()) {
                log.warn("属性提取结果为空，主题: {}", theme.getThemeName());
                throw BusinessException.aiServiceError("AttributeExtraction", "Attribute extraction AI returned empty content"); // 属性提取AI返回内容为空
            }
        } catch (Exception e) {
            log.error("提取属性时出错: {}", e.getMessage(), e);
            throw BusinessException.aiServiceError("AttributeExtraction",
                    String.format("Attribute extraction failed: %s", e.getMessage())); // 属性提取失败
        }

        try {
            ManualRecordResponse result = JSON.parseObject(content, ManualRecordResponse.class);
            result.setThemeName(theme.getThemeName());
            return result;
        } catch (JSONException e) {
            log.error("解析属性JSON失败 - 内容: {}, 错误: {}", content, e.getMessage(), e);
            throw BusinessException.withDetail("JSON_PARSE_ERROR", "Attribute data parsing failed",
                    String.format("JSON content: %s, error: %s", content, e.getMessage())); // JSON内容: %s, 错误: %s
        }
    }

    /**
     * 创建或查找主题
     *
     * @param userId    用户ID
     * @param themeName 主题名称
     * @return 返回主题Mono
     */
    private Theme findOrCreateTheme(Long userId, String themeName) {
        Theme theme = themeMapper.queryByThemeNameAndUserId(themeName, userId);
        if (theme != null) {
            return theme;
        }
        Theme newTheme = new Theme();
        newTheme.setThemeName(themeName);
        newTheme.setDescription("用户自定义主题：" + themeName);
        newTheme.setUserId(userId);
        newTheme.setIsPublic(0);
        newTheme.setStatus(0);
        newTheme.setCreatedAt(LocalDateTime.now());
        newTheme.setUpdatedAt(LocalDateTime.now());
        themeMapper.insert(newTheme);
        return newTheme;
    }

    /**
     * 创建或查找属性
     *
     * @param userId        用户ID
     * @param themeId       主题ID
     * @param attributeName 属性名称
     * @param dataType      数据类型
     * @return 属性Mono
     */
    private Attribute findOrCreateAttribute(Long userId, Long themeId, String attributeName, DataType dataType) {
        Attribute attribute = attributeMapper.findByThemeIdAndAttributeName(themeId, attributeName, userId);
        if (attribute != null) {
            return attribute;
        } else {
            Attribute newAttribute = new Attribute();
            newAttribute.setThemeId(themeId);
            newAttribute.setUserId(userId);
            newAttribute.setAttributeName(attributeName);
            newAttribute.setDataType(dataType);
            newAttribute.setDescription("用户自定义属性：" + attributeName);
            newAttribute.setDisplayOrder(0);
            newAttribute.setStatus(0);
            newAttribute.setCreatedAt(LocalDateTime.now());
            newAttribute.setUpdatedAt(LocalDateTime.now());
            int insert = attributeMapper.insert(newAttribute);
            if (insert <= 0) {
                log.warn("属性重复创建，重新查询已存在的属性: {}", attributeName);
                return attributeMapper.findByThemeIdAndAttributeName(themeId, attributeName, userId);
            } else {
                return newAttribute;
            }

        }
    }

    /**
     * 处理分析意图
     */
    public List<AnalysisResponse> processAnalysisIntent(User user, String message, String sessionId, TokenCounter counter) {
        Long userId = user.getId();
        createUserChat(userId, message, sessionId, "analysis");
        List<String> themes = identifyAnalysisThemes(userId, message, counter);
        if (themes.contains("ALL")) {
            AnalysisRequest analysisRequest = generateQueryTimeSQL(user, message, counter);
            return executeAnalysisSQL(Collections.singletonList(analysisRequest));
        } else {
            for (String theme : themes) {
                List<AnalysisRequest> analysisRequests = generateAnalysisSQL(user, theme, message, counter);
                return executeAnalysisSQL(analysisRequests);
            }
        }
    }

    /**
     * 识别分析主题
     */
    private List<String> identifyAnalysisThemes(Long userId, String message, TokenCounter counter) {
        List<Theme> existingThemes = themeMapper.queryByUserIdOrIsPublic(userId);
        if (existingThemes.isEmpty()) {
            log.warn("用户无可用主题，用户ID: {}", userId);
            return Collections.emptyList();
        }
        String themesPrompt = "现有主题：" + existingThemes.stream()
                .map(Theme::getThemeName)
                .reduce((a, b) -> a + "," + b)
                .orElse("");


        try {
            ChatResponse response = analysisThemeChatClient.prompt()
                    .system(prompt -> {
                        prompt.param("themesPrompt", themesPrompt);
                    })
                    .user("用户分析需求：" + message + "\n请判断要分析的主题")
                    .call()
                    .chatResponse();

            processTokenUsage(response, counter);
            String content = AiResponseCleaner.extractJsonString(response.getResult().getOutput().getText());

            if (StringUtils.isBlank(content)) {
                log.warn("主题识别结果为空，用户消息: {}", message);
                return Collections.emptyList();
            }
            return JSON.parseArray(content, String.class);

        } catch (Exception e) {
            log.error("识别分析主题时出错: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 以时间范围查询的sql
     *
     * @param user
     * @param message
     * @param counter
     * @return
     */
    private AnalysisRequest generateQueryTimeSQL(User user, String message, TokenCounter counter) {
        String sqlTemplate = "WITH CommonAttributes AS (\n" +
                "  SELECT\n" +
                "    b.record_id,\n" +
                "    JSON_OBJECTAGG(\n" +
                "      d.attribute_name,\n" +
                "      CASE\n" +
                "          LOWER(d.data_type) \n" +
                "          WHEN 'string' THEN\n" +
                "          b.string_value \n" +
                "          WHEN 'number' THEN\n" +
                "          CONCAT(b.number_value, COALESCE(b.number_unit, '')) \n" +
                "          WHEN 'date' THEN\n" +
                "          b.date_value \n" +
                "          WHEN 'boolean' THEN\n" +
                "          b.boolean_value \n" +
                "          WHEN 'json' THEN\n" +
                "          b.json_value ELSE b.string_value \n" +
                "        END) AS common_attrs_json \n" +
                "  FROM\n" +
                "    user_record_detail b\n" +
                "    JOIN attributes d ON d.id = b.attribute_id \n" +
                "  WHERE\n" +
                "    b.group_id IS NULL \n" +
                "  GROUP BY\n" +
                "b.record_id),\n" +
                "GroupedAttributes AS (\n" +
                "  SELECT\n" +
                "    b.record_id,\n" +
                "    b.group_id,\n" +
                "    JSON_OBJECTAGG(\n" +
                "        d.attribute_name,\n" +
                "      CASE\n" +
                "          LOWER(d.data_type) \n" +
                "          WHEN 'string' THEN\n" +
                "          b.string_value \n" +
                "          WHEN 'number' THEN\n" +
                "          CONCAT(b.number_value, COALESCE(b.number_unit, '')) \n" +
                "          WHEN 'date' THEN\n" +
                "          b.date_value \n" +
                "          WHEN 'boolean' THEN\n" +
                "          b.boolean_value \n" +
                "          WHEN 'json' THEN\n" +
                "          b.json_value ELSE b.string_value \n" +
                "        END) AS grouped_attrs_json \n" +
                "  FROM\n" +
                "    user_record_detail b\n" +
                "    JOIN attributes d ON d.id = b.attribute_id \n" +
                "  WHERE\n" +
                "    b.group_id IS NOT NULL \n" +
                "  GROUP BY\n" +
                "    b.record_id,\n" +
                "b.group_id),\n" +
                "ArrayAggregated AS (\n" +
                "  SELECT\n" +
                "    ga.record_id,\n" +
                "    JSON_ARRAYAGG(JSON_MERGE_PRESERVE(COALESCE(ca.common_attrs_json, '{}'), ga.grouped_attrs_json)) AS attributes_json_array \n" +
                "  FROM\n" +
                "    GroupedAttributes ga\n" +
                "    LEFT JOIN CommonAttributes ca ON ga.record_id = ca.record_id \n" +
                "  GROUP BY\n" +
                "ga.record_id) SELECT\n" +
                "c.theme_name,\n" +
                "a.id AS record_id,\n" +
                "COALESCE(aa.attributes_json_array, ca.common_attrs_json) AS attributes_json \n" +
                "FROM\n" +
                "  user_record a\n" +
                "  JOIN themes c ON c.id = a.theme_id\n" +
                "  LEFT JOIN CommonAttributes ca ON a.id = ca.record_id\n" +
                "  LEFT JOIN ArrayAggregated aa ON a.id = aa.record_id \n" +
                "WHERE\n" +
                "  (ca.record_id IS NOT NULL OR aa.record_id IS NOT NULL) \n" +
                "  AND a.user_id = " + user.getId() + " \n" +
                "  AND (%s)"; // <-- 时间条件的模板，后续动态填充

        ChatResponse chatResponse = generateDateRangeChatClient.prompt()
                .templateRenderer(StTemplateRenderer.builder()
                        .startDelimiterToken('^')
                        .endDelimiterToken('^')
                        .build())
                .system(prompt -> prompt.param("timezone", user.getTimeZone()))
                .user(message)
                .call()
                .chatResponse();

        processTokenUsage(chatResponse, counter);
        String text = chatResponse.getResult().getOutput().getText();
        TimeRangeResponse timeRangeResponse;
        try {
            // 清理并提取JSON字符串
            String json = AiResponseCleaner.extractJsonString(text);
            if (StringUtils.isBlank(json)) {
                throw new IllegalStateException("AI did not produce a valid JSON time range."); // AI未能生成有效的JSON时间范围。
            }

            // 进行反序列化
            timeRangeResponse = JSON.parseObject(json, TimeRangeResponse.class);

        } catch (Exception e) {
            // 如果JSON解析失败，返回一个包含错误的 Mono
            throw new IllegalStateException("AI returned an invalid JSON time range format: " + text, e); // AI返回的时间范围JSON格式无效
        }
        List<TimeRange> eventRanges = Optional.ofNullable(timeRangeResponse.getEventDate()).orElse(Collections.emptyList());
        List<TimeRange> recordRanges = Optional.ofNullable(timeRangeResponse.getRecordDate()).orElse(Collections.emptyList());

        if (eventRanges.isEmpty() && recordRanges.isEmpty()) {
            throw new IllegalStateException("AI could not parse a valid time range from the user input."); // AI未能从用户输入中解析出有效的时间范围。
        }

        List<String> timeConditionSegments = new ArrayList<>();

        if (!eventRanges.isEmpty()) {
            String eventCondition = eventRanges.stream()
                    .map(range -> String.format("(a.event_date BETWEEN '%s' AND '%s')",
                            range.getStartTime(), range.getEndTime()))
                    .collect(Collectors.joining(" OR "));

            if (StringUtils.isNotBlank(eventCondition)) {
                timeConditionSegments.add("(" + eventCondition + ")");
            }
        }

        if (!recordRanges.isEmpty()) {
            String recordCondition = recordRanges.stream()
                    .map(range -> String.format("(a.record_date BETWEEN '%s' AND '%s')",
                            range.getStartTime(), range.getEndTime()))
                    .collect(Collectors.joining(" OR "));

            if (StringUtils.isNotBlank(recordCondition)) {
                timeConditionSegments.add("(" + recordCondition + ")");
            }
        }

        if (timeConditionSegments.isEmpty()) {
            throw new IllegalStateException("AI did not provide usable time ranges for event_date or record_date.");
        }

        String timeConditions = String.join(" OR ", timeConditionSegments);

        String finalSql = String.format(sqlTemplate, timeConditions);

        AnalysisRequest request = new AnalysisRequest();
        request.setSql(finalSql);
        return request;
    }

    /**
     * 生成分析SQL
     */
    private List<AnalysisRequest> generateAnalysisSQL(User user, String themeName, String message, TokenCounter counter) {
        Long userId = user.getId();
        Theme theme = themeMapper.queryByThemeNameAndUserId(themeName, userId);
        String attributesPrompt = buildAttributesPrompt(theme);
        List<AnalysisRequest> requests = new ArrayList<>();
        try {
            ChatResponse chatResponse = analysisChatClient.prompt()
                    .templateRenderer(StTemplateRenderer.builder()
                            .startDelimiterToken('^')
                            .endDelimiterToken('^')
                            .build())
                    .system(prompt -> {
                        prompt.param("theme", themeName);
                        prompt.param("themeId", theme.getId());
                        prompt.param("attributesPrompt", attributesPrompt);
                        prompt.param("userId", userId);
                        prompt.param("timezone", user.getTimeZone());
                    })
                    .user(message)
                    .call()
                    .chatResponse();

            processTokenUsage(chatResponse, counter);

            String text = chatResponse.getResult().getOutput().getText();
            String json = AiResponseCleaner.extractJsonString(text);
            requests = JSON.parseArray(json, AnalysisRequest.class);
        } catch (Exception e) {
            log.error("AI generateAnalysisSQL Exception", e);
        }
        return requests;
    }

    private String buildAttributesPrompt(Theme theme) {
        if (theme.getAttributes().isEmpty()) {
            return "无属性";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("| attribute_name | attribute_id | attributes_description | data_type |\n");
        sb.append("| :------- | :------ | :------- | :------- |\n");
        for (Attribute attr : theme.getAttributes()) {
            sb.append("| ")
                    .append(attr.getAttributeName()).append(" | ")
                    .append(attr.getId()).append(" | ")
                    .append(attr.getDescription() != null ? attr.getDescription() : "").append(" | ")
                    .append(attr.getDataType()).append(" |\n");
        }
        return sb.toString();
    }

    /**
     * 执行分析SQL
     */
    private List<AnalysisResponse> executeAnalysisSQL(List<AnalysisRequest> analysisRequests) {
        List<AnalysisResponse> responses = new ArrayList<>();
        for (AnalysisRequest request : analysisRequests) {
            List<Map<String, Object>> data = recordAttributeValueMapper.executeCustomQuery(request.getSql());
            AnalysisResponse response = new AnalysisResponse();
            response.setDescription(request.getDescription());
            response.setSchema(request.getSchema());
            response.setData(data);
            responses.add(response);
        }
        return responses;
    }


    /**
     * 处理AI响应的token使用情况
     */
    private void processTokenUsage(ChatResponse response, TokenCounter counter) {
        if (response != null && response.getMetadata().getUsage() != null) {
            var usage = response.getMetadata().getUsage();
            counter.addPromptTokens(usage.getPromptTokens());
            counter.addCompletionTokens(usage.getCompletionTokens());
        }
    }

    private void logAiInteractionSummary(Long userId, String sessionId, long startTime, TokenCounter counter, SignalType signalType) {
        long duration = System.currentTimeMillis() - startTime;
        log.info("AI对话请求结束 - 用户ID: {}, 会话ID: {}, 总耗时: {}ms, 输入Tokens: {}, 输出Tokens: {}, 总Tokens: {}, 结束状态: {}",
                userId,
                sessionId,
                duration,
                counter.getPromptTokens(),
                counter.getCompletionTokens(),
                counter.getTotalTokens(),
                signalType);
    }


    /**
     * 保存用户输入记录
     */
    private Long saveUserInput(User user, String sessionId, String userInput, String intentType) {
        ChatInteraction interaction = new ChatInteraction();
        interaction.setUserId(user.getId());
        interaction.setSessionId(sessionId);
        interaction.setUserInput(userInput);
        interaction.setAiResponse(null); // 用户输入时AI响应为空
        interaction.setIntentType(intentType);
        interaction.setTotalPromptTokens(0);
        interaction.setTotalCompletionTokens(0);
        interaction.setTotalTokens(0);
        interaction.setCreatedAt(LocalDateTime.now());
        interaction.setUpdatedAt(LocalDateTime.now());
        int insert = chatInteractionMapper.insert(interaction);
        Long id = interaction.getId();
        if (insert > 0) {
            log.debug("保存用户输入记录成功: {}", id);
        } else {
            log.error("保存用户输入记录失败: {}", id);
        }
        return id;

    }

    /**
     * 保存AI响应记录
     */
    private ChatInteraction saveAiResponse(Long userId, String sessionId, String aiResponse, String intentType, TokenCounter
            counter) {
        ChatInteraction interaction = new ChatInteraction();
        interaction.setUserId(userId);
        interaction.setSessionId(sessionId);
        interaction.setUserInput(null); // AI响应时用户输入为空
        interaction.setAiResponse(aiResponse);
        interaction.setIntentType(intentType);
        interaction.setTotalPromptTokens(counter.getPromptTokens());
        interaction.setTotalCompletionTokens(counter.getCompletionTokens());
        interaction.setTotalTokens(counter.getTotalTokens());
        interaction.setCreatedAt(LocalDateTime.now());
        interaction.setUpdatedAt(LocalDateTime.now());
        int insert = chatInteractionMapper.insert(interaction);
        if (insert > 0) {
            log.debug("保存AI响应记录成功: {}", interaction.getId());
        } else {
            log.error("保存AI响应记录失败: {}", interaction.getId());
        }
        return interaction;
    }


    /**
     * 根据sessionId查询聊天历史记录
     */
    @Override
    public List<ChatInteraction> getChatHistoryBySessionId(String sessionId) {
        return chatInteractionMapper.findBySessionIdOrderByCreateTimeAsc(sessionId);
    }
}
