package com.logai.context.service;

import com.logai.context.dto.EditContextRequest;
import com.logai.context.dto.UserContextDto;
import com.logai.user.entity.User;

import java.util.List;

public interface UserContextService {
    UserContextDto editContext(List<EditContextRequest> contexts, User user);

    UserContextDto getContext(List<Long> themeIds, User user);
}
