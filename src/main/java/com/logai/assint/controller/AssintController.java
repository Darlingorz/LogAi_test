package com.logai.assint.controller;

import com.logai.assint.dto.GlobalAiAssintResponse;
import com.logai.assint.dto.SaveRecordDetailRequest;
import com.logai.assint.dto.ThemeRecordSummaryDto;
import com.logai.assint.mapper.ThemeMapper;
import com.logai.assint.service.AssistService;
import com.logai.assint.service.UserRecordService;
import com.logai.common.exception.BusinessException;
import com.logai.common.model.Result;
import com.logai.common.utils.TimeUtil;
import com.logai.security.annotation.MemberOnly;
import com.logai.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@RestController
@RequestMapping("/api/assist")
@RequiredArgsConstructor
public class AssintController {
    private final UserRecordService userRecordService;
    private final ThemeMapper themeRepository;
    private final AssistService assistService;
    private final TimeUtil timeUtil;

    /**
     * AI对话接口
     *
     * @return 响应
     */
    @CrossOrigin(origins = "*")
    @GetMapping(value = "/chat")
    @MemberOnly
    public List<GlobalAiAssintResponse> chat(@RequestParam String message,
                                             @RequestParam(required = false) String intentArray,
                                             @AuthenticationPrincipal User user) {
        if (io.micrometer.common.util.StringUtils.isBlank(message)) {
            // 消息内容不能为空
            throw BusinessException.validationError("message", "Message content cannot be empty");
        }
        return assistService.globalChat(user, message, intentArray);
    }


    @PostMapping("/saveUserRecord")
    @MemberOnly
    public Result saveUserRecord(@AuthenticationPrincipal User user,
                                 @RequestBody SaveRecordDetailRequest saveRecordDetailRequest) {
        Objects.requireNonNull(user, "User information is missing"); // 用户信息缺失
        userRecordService.saveUserRecord(user.getId(), saveRecordDetailRequest);
        return Result.success("更新记录成功");
    }

    @GetMapping("/deleteUserRecords/{recordId}")
    @MemberOnly
    public Result deleteUserRecords(@AuthenticationPrincipal User user,
                                    @PathVariable Long recordId) {
        Objects.requireNonNull(user, "User information is missing"); // 用户信息缺失
        userRecordService.deleteUserRecord(user.getId(), recordId);
        return Result.success("删除记录成功");
    }

    @PostMapping("/deleteUserRecords/batchDelete")
    @MemberOnly
    public Result deleteUserRecordsBatch(@AuthenticationPrincipal User user,
                                         @RequestBody List<Long> recordIds) {
        Objects.requireNonNull(user, "User information is missing"); // 用户信息缺失
        userRecordService.deleteUserRecords(user.getId(), recordIds);
        return Result.success("批量删除记录成功");
    }

    @GetMapping("/searchUserRecords")
    public Result searchUserRecords(@AuthenticationPrincipal User user,
                                    @RequestParam(required = false) String chatId,
                                    @RequestParam(required = false) String allAttribute,
                                    @RequestParam(required = false) Long themeId,
                                    @RequestParam(required = false) String searchValue,
                                    @RequestParam(required = false) String startTime,
                                    @RequestParam(required = false) String endTime,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        Objects.requireNonNull(user, "User information is missing"); // 用户信息缺失
        if (StringUtils.isNotBlank(chatId)) {
            return Result.success(userRecordService.getUserRecordByChatIdAndUserId(chatId, user.getId(), allAttribute));
        }
        String normalizedSearchValue = StringUtils.isBlank(searchValue) ? null : searchValue.trim();
        LocalDateTime parsedSearchDateTime = timeUtil.tryParseFlexibleDateTime(normalizedSearchValue);
        LocalDate parsedSearchDate = parsedSearchDateTime == null ? timeUtil.tryParseFlexibleDate(normalizedSearchValue) : null;
        LocalDateTime parsedStart = timeUtil.parseFlexibleDateTime(startTime);
        LocalDateTime parsedEnd = timeUtil.parseFlexibleDateTime(endTime);
        return Result.success(userRecordService.searchUserRecords(user.getId(), themeId, normalizedSearchValue,
                parsedSearchDate, parsedSearchDateTime, parsedStart, parsedEnd,
                allAttribute, page, size));
    }

    @GetMapping("/getTheme")
    public List<ThemeRecordSummaryDto> getTheme(@AuthenticationPrincipal User user) {
        Objects.requireNonNull(user, "User information is missing"); // 用户信息缺失
        return themeRepository.queryWithRecordCountByUserIdOrIsPublic(user.getId());
    }
}
