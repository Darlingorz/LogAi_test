package com.logai.context.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logai.common.model.Result;
import com.logai.context.dto.EditContextRequest;
import com.logai.context.dto.UserContextDto;
import com.logai.context.entity.OnboardContext;
import com.logai.context.service.OnboardContextService;
import com.logai.context.service.UserContextService;
import com.logai.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/context")
@RequiredArgsConstructor
public class ContextController {

    private final ObjectMapper objectMapper;
    private final UserContextService userContextService;
    private final OnboardContextService onboardContextService;

    @CrossOrigin(origins = "*")
    @GetMapping(value = "/editContext")
    public Result editContext(@RequestParam String data,
                              @AuthenticationPrincipal User user) {
        try {
            List<EditContextRequest> infos = objectMapper.readValue(data, new TypeReference<>() {
            });
            return Result.success(userContextService.editContext(infos, user));
        } catch (JsonProcessingException e) {
            return Result.failure("editContext error: " + e.getMessage());
        }
    }

    @CrossOrigin(origins = "*")
    @GetMapping(value = "/getContext")
    public Result getContext(@RequestParam String themesIds,
                             @AuthenticationPrincipal User user) {
        List<Long> themeIds = parseThemeIds(themesIds);
        return Result.success(userContextService.getContext(themeIds, user));
    }

    @CrossOrigin(origins = "*")
    @GetMapping(value = "/getOnboardContext")
    public Result getOnboardContext(@RequestParam(required = false) String themesIds,
                                    @AuthenticationPrincipal User user) {
        List<Long> themeIds = parseThemeIds(themesIds);
        List<OnboardContext> onboardContexts = onboardContextService.getOnboardContexts(themeIds);
        UserContextDto userContext = userContextService.getContext(themeIds, user);
        return Result.success(Map.of("onboardContexts", onboardContexts, "userContext", userContext));
    }


    private List<Long> parseThemeIds(String themesIds) {
        if (themesIds == null || themesIds.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(themesIds.split(","))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .map(token -> {
                    try {
                        return Long.valueOf(token);
                    } catch (NumberFormatException ex) {
                        log.warn("Invalid theme id provided: {}", token);
                        return null;
                    }
                })
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }
}
