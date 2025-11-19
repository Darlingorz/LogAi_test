package com.logai.context.service.impl;

import com.logai.common.utils.TimeUtil;
import com.logai.context.dto.EditContextRequest;
import com.logai.context.dto.UserContextDto;
import com.logai.context.entity.UserContext;
import com.logai.context.entity.WorkflowInit;
import com.logai.context.enums.ContextType;
import com.logai.context.mapper.UserContextMapper;
import com.logai.context.mapper.WorkflowInitMapper;
import com.logai.context.service.UserContextService;
import com.logai.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserContextServiceImpl implements UserContextService {
    private final UserContextMapper userContextMapper;
    private final TimeUtil timeUtil;
    private final WorkflowInitMapper workflowInitMapper;


    @Override
    public UserContextDto editContext(List<EditContextRequest> contexts, User user) {

        List<EditContextRequest> safeContexts =
                (contexts == null ? Collections.emptyList() : contexts);

        LocalDateTime now = LocalDateTime.now();

        // 提取 themeId（去重 + null 过滤）
        List<Long> themeIdsFilter = safeContexts.stream()
                .map(EditContextRequest::getThemeId)
                .map(this::parseThemeId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        // ① 逐条处理 EditContextRequest
        for (EditContextRequest request : safeContexts) {

            // a. 查询或创建 UserContext
            UserContext context = fetchOrCreateUserContext(request);

            // b. 应用请求参数（user, now）
            UserContext updated = applyRequestToContext(context, request, user, now);

            // c. 保存更新记录
            userContextMapper.insert(updated);
            log.info("✅ 保存用户上下文成功: {}", updated);
        }

        // ② 初始化 workflow（同步版）
        initializeWorkflowContexts(user, themeIdsFilter);

        // ③ 加载并返回 UserContextDto
        return loadUserContextDto(user, themeIdsFilter);
    }

    @Override
    public UserContextDto getContext(List<Long> themeIds, User user) {
        List<Long> safeThemeIds = themeIds == null ? Collections.emptyList()
                : themeIds.stream().filter(Objects::nonNull).collect(Collectors.toList());
        initializeWorkflowContexts(user, safeThemeIds);
        return loadUserContextDto(user, safeThemeIds);
    }

    private UserContextDto loadUserContextDto(User user, List<Long> themeIds) {
        List<UserContext> allByUserId = userContextMapper.findAllByUserId(user.getId(), themeIds, user.getTimeZone());
        UserContextDto userContextDto = buildUserContextDto(allByUserId);
        return attachUserTime(userContextDto, user);
    }

    private UserContext fetchOrCreateUserContext(EditContextRequest request) {
        if (request.getInfoId() == null) {
            return new UserContext();
        }
        UserContext userContext = userContextMapper.selectById(request.getInfoId());
        if (userContext != null && userContext.getId() != null) {
            return userContext;
        }
        return new UserContext();
    }

    private UserContext applyRequestToContext(UserContext userContext, EditContextRequest request, User user, LocalDateTime now) {
        if (userContext.getId() == null) {
            userContext.setCreatedAt(now);
        }

        userContext.setUpdatedAt(now);
        userContext.setUserId(user.getId());
        userContext.setInfo(request.getInfo());
        userContext.setContextType(ContextType.fromString(request.getType()));
        userContext.setExpireTime(request.getExpireTime());
        if (request.getThemeId() != null && !request.getThemeId().trim().isEmpty()) {
            userContext.setThemeId(parseThemeId(request.getThemeId()));
        } else if (userContext.getId() == null) {
            userContext.setThemeId(null);
        }
        return userContext;
    }

    private void initializeWorkflowContexts(User user, List<Long> themeIds) {

        if (user == null || user.getId() == null || themeIds == null || themeIds.isEmpty()) {
            return;
        }

        // 去重
        List<Long> distinctThemeIds = themeIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        for (Long themeId : distinctThemeIds) {

            // ① 检查是否已有 WORKFLOW 记录
            boolean exists = userContextMapper
                    .existsByUserIdAndThemeIdAndContextType(
                            user.getId(),
                            themeId,
                            ContextType.WORKFLOW
                    ) > 0;

            if (exists) {
                continue; // 已存在则跳过
            }

            // ② 查询 workflow 初始化内容
            List<WorkflowInit> initList =
                    workflowInitMapper.findAllByThemeId(String.valueOf(themeId));

            if (initList == null || initList.isEmpty()) {
                continue;
            }

            // ③ 遍历 info，过滤为空的
            for (WorkflowInit init : initList) {
                String info = init.getInfo();
                if (info == null || info.isBlank()) {
                    continue;
                }

                // ④ 创建 workflow context
                createWorkflowContext(user, themeId, info);
            }
        }
    }

    private void createWorkflowContext(User user, Long themeId, String info) {
        LocalDateTime now = LocalDateTime.now();
        UserContext workflowContext = new UserContext();
        workflowContext.setUserId(user.getId());
        workflowContext.setThemeId(themeId);
        workflowContext.setContextType(ContextType.WORKFLOW);
        workflowContext.setInfo(info);
        workflowContext.setCreatedAt(now);
        workflowContext.setUpdatedAt(now);
        userContextMapper.insert(workflowContext);
    }

    private UserContextDto buildUserContextDto(List<UserContext> userContexts) {
        UserContextDto dto = new UserContextDto();
        List<UserContextDto.CommonInfoDTO> commonInfo = new ArrayList<>();
        Map<Long, List<UserContext>> contextsByTheme = new LinkedHashMap<>();

        for (UserContext context : userContexts) {
            if (context.getContextType() == null) {
                continue;
            }
            if (context.getContextType() == ContextType.COMMON_INFO) {
                commonInfo.add(toCommonInfoDto(context));
                continue;
            }

            Long themeId = context.getThemeId();
            if (themeId == null) {
                continue;
            }

            contextsByTheme.computeIfAbsent(themeId, key -> new ArrayList<>()).add(context);
        }

        dto.setCommonInfo(commonInfo.isEmpty() ? Collections.emptyList() : commonInfo);

        List<UserContextDto.ContextEntryDTO> contextEntries = new ArrayList<>();
        for (Map.Entry<Long, List<UserContext>> entry : contextsByTheme.entrySet()) {
            Long themeId = entry.getKey();
            List<UserContext> groupedContexts = entry.getValue();

            List<UserContextDto.InfoItemDTO> tempInfo = new ArrayList<>();
            List<UserContextDto.InfoItemDTO> themeContextInfo = new ArrayList<>();
            List<UserContextDto.InfoItemDTO> workflow = new ArrayList<>();

            groupedContexts.forEach(context -> {
                ContextType type = context.getContextType();
                if (type == null) {
                    return;
                }
                switch (type) {
                    case TEMP_INFO -> tempInfo.add(toInfoItemDto(context));
                    case THEME_CONTEXT_INFO -> themeContextInfo.add(toInfoItemDto(context));
                    case WORKFLOW -> workflow.add(toInfoItemDto(context));
                    default -> {
                    }
                }
            });

            UserContextDto.ContextDetailDTO contextDetail = new UserContextDto.ContextDetailDTO();
            contextDetail.setTempInfo(tempInfo);
            contextDetail.setThemeContextInfo(themeContextInfo);
            contextDetail.setWorkflow(workflow);

            UserContextDto.ContextEntryDTO contextEntryDTO = new UserContextDto.ContextEntryDTO();
            contextEntryDTO.setThemeId(themeId);
            contextEntryDTO.setThemeName(groupedContexts.stream()
                    .map(UserContext::getThemeName)
                    .filter(name -> name != null && !name.isBlank())
                    .findFirst()
                    .orElse(""));
            contextEntryDTO.setContext(contextDetail);

            contextEntries.add(contextEntryDTO);
        }

        dto.setContexts(contextEntries.isEmpty() ? Collections.emptyList() : contextEntries);
        return dto;
    }

    private UserContextDto attachUserTime(UserContextDto dto, User user) {
        LocalDateTime userNow = timeUtil.getNowInTimezone(user.getTimeZone());
        dto.setUserTime(formatExpireTime(userNow));
        return dto;
    }

    private UserContextDto.CommonInfoDTO toCommonInfoDto(UserContext context) {
        UserContextDto.CommonInfoDTO dto = new UserContextDto.CommonInfoDTO();
        dto.setInfo(context.getInfo());
        dto.setInfoId(context.getId() == null ? null : String.valueOf(context.getId()));
        return dto;
    }

    private UserContextDto.InfoItemDTO toInfoItemDto(UserContext context) {
        UserContextDto.InfoItemDTO dto = new UserContextDto.InfoItemDTO();
        dto.setInfo(context.getInfo());
        dto.setInfoId(context.getId() == null ? null : String.valueOf(context.getId()));
        dto.setExpireTime(formatExpireTime(context.getExpireTime()));
        return dto;
    }

    private String formatExpireTime(LocalDateTime expireTime) {
        if (expireTime == null) {
            return null;
        }
        return expireTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private Long parseThemeId(String themeId) {
        if (themeId == null || themeId.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(themeId.trim());
        } catch (NumberFormatException ex) {
            log.warn("无法解析的主题ID: {}", themeId);
            return null;
        }
    }
}
