package com.logai.security.access;

import com.logai.common.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Validates that the authenticated user has member privileges.
 */
@Component("memberAccessGuard")
public class MemberAccessGuard {

    private static final String MEMBER_ROLE = "ROLE_MEMBER";
    private static final String UNAUTHORIZED_MESSAGE = "Non-member users cannot use AI function: Please upgrade to member https://www.logai.chat/dashboard/subscription";

    public boolean requireMember(Authentication authentication) {
        if (authentication == null) {
            throw BusinessException.unauthorized(UNAUTHORIZED_MESSAGE);
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (MEMBER_ROLE.equals(authority.getAuthority())) {
                return true;
            }
        }
        throw BusinessException.unauthorized(UNAUTHORIZED_MESSAGE);
    }
}
