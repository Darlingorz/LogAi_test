package com.logai.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logai.creem.entity.Membership;
import com.logai.creem.entity.UserMembership;
import com.logai.creem.mapper.MembershipMapper;
import com.logai.creem.mapper.UserMembershipMapper;
import com.logai.oauth2.service.OAuth2TokenService;
import com.logai.security.dto.TokenInfo;
import com.logai.security.service.TokenService;
import com.logai.security.util.JwtUtils;
import com.logai.user.entity.User;
import com.logai.user.mapper.UserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final TokenService tokenService;
    private final OAuth2TokenService oauth2TokenService;
    private final UserMapper userMapper;
    private final UserMembershipMapper userMembershipMapper;
    private final MembershipMapper membershipMapper;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);

        String requestId = UUID.randomUUID().toString().substring(0, 8);
        String method = request.getMethod();
        String path = request.getRequestURI();
        String query = request.getQueryString();

        printRequestLog(requestId, wrappedRequest, method, path, query);

        String token = extractToken(wrappedRequest);

        // 1. 放行指定路径
        if (shouldSkip(path)) {
            chain.doFilter(request, response);
            return;
        }

        // 2. 检查是否已认证
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }

        // 3. 检查 Token
        if (token == null) {
            chain.doFilter(request, response);
            return;
        }

        var claimsOpt = jwtUtils.extractClaims(token);
        if (claimsOpt.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        var claims = claimsOpt.get();

        String userUuid = claims.getSubject();
        if (userUuid == null || userUuid.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        // OAuth2 access token 会带 client_id
        String clientId = claims.get("client_id", String.class);

        // 4. 执行同步认证流程
        try {

            TokenInfo tokenInfo = (clientId != null && !clientId.isBlank())
                    ? oauth2TokenService.validateAccessToken(token)
                    : tokenService.validateAccessToken(token);

            if (tokenInfo == null) {
                chain.doFilter(request, response);
                return;
            }

            if (!userUuid.equals(tokenInfo.getUserUuid())) {
                chain.doFilter(request, response);
                return;
            }

            User user = userMapper.findByUuid(tokenInfo.getUserUuid());

            if (user == null) {
                chain.doFilter(request, response);
                return;
            }

            Authentication auth = buildAuthentication(user);

            SecurityContextHolder.getContext().setAuthentication(auth);

        } catch (Exception e) {
            // Token 验证失败或用户未找到
            log.warn("JWT/OAuth2 认证失败: {}", e.getMessage());
        }

        // 继续过滤器链
        chain.doFilter(request, response);
    }

    // ===========================================================
    //                     日志打印部分
    // ===========================================================

    private void printRequestLog(String requestId, ContentCachingRequestWrapper req,
                                 String method, String path, String query) throws IOException {

        log.debug("[{}] JWT 过滤器请求：{} {}?{}", requestId, method, path, (query == null ? "" : query));

        Map<String, String> headerMap = new LinkedHashMap<>();
        Collections.list(req.getHeaderNames()).forEach(
                h -> headerMap.put(h, req.getHeader(h))
        );

        String prettyHeaders = prettyPrint(headerMap);
        log.info("[{}] 请求头:\n{}", requestId, prettyHeaders);

        // GET 参数
        Map<String, Object> queryMap = new LinkedHashMap<>();
        req.getParameterMap().forEach((k, v) -> queryMap.put(k, Arrays.asList(v)));
        log.info("[{}] GET 参数:\n{}", requestId, prettyPrint(queryMap));

        // body 解析
        String contentType = Optional.ofNullable(req.getContentType()).orElse("");

        // 读取 body 内容
        String body = readRequestBody(req);

        String prettyBody = "{}";

        if (!body.isBlank()) {
            if (contentType.contains("application/json")) {
                // JSON 格式
                try {
                    Object json = objectMapper.readValue(body, Object.class);
                    prettyBody = prettyPrint(json);
                } catch (Exception e) {
                    prettyBody = body; // 保底
                }

                log.info("[{}] {} JSON 请求体:\n{}", requestId, req.getMethod(), prettyBody);
            } else if (contentType.contains("application/x-www-form-urlencoded")) {
                // FormData 格式
                prettyBody = prettyPrint(parseFormData(body));
                log.info("[{}] {} FORM 请求体:\n{}", requestId, req.getMethod(), prettyBody);
            } else if (contentType.contains("multipart/form-data")) {
                // multipart 不打印二进制
                log.info("[{}] 文件上传 multipart/form-data（已跳过详细打印）", requestId);
            } else {
                log.info("[{}] {} 原始请求体:\n{}", requestId, req.getMethod(), body);
            }
        }
    }

    private Map<String, String> parseFormData(String body) {
        Map<String, String> map = new LinkedHashMap<>();
        Arrays.stream(body.split("&")).forEach(kv -> {
            String[] pair = kv.split("=", 2);
            if (pair.length == 2) {
                map.put(pair[0], URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
            }
        });
        return map;
    }

    private String readRequestBody(ContentCachingRequestWrapper request) {
        byte[] buf = request.getContentAsByteArray();
        return (buf.length > 0) ? new String(buf, StandardCharsets.UTF_8) : "";
    }

    private String prettyPrint(Object obj) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (Exception e) {
            return obj.toString();
        }
    }

    // ========================= 辅助方法（同步/阻塞） =========================

    private boolean shouldSkip(String path) {
        return path.startsWith("/api/auth") ||
                path.startsWith("/api/user/login") ||
                path.startsWith("/api/user/register") ||
                path.startsWith("/error") ||
                path.startsWith("/api/creem/order/checkout_unauthenticated") ||
                path.equals("/");
    }

    private String extractToken(HttpServletRequest request) {
        // 尝试从 Cookie 中获取 session
        if (request.getCookies() != null) {
            Optional<String> session = Arrays.stream(request.getCookies())
                    .filter(c -> "session".equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst();
            if (session.isPresent()) {
                return session.get();
            }
        }
        // 尝试从 Authorization Header 中获取 Bearer Token
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private Authentication buildAuthentication(User user) {
        SimpleGrantedAuthority role = resolveUserAuthority(user);
        return new UsernamePasswordAuthenticationToken(user, null, Collections.singleton(role));
    }


    /**
     * 同步/阻塞获取用户权限。
     */

    private SimpleGrantedAuthority resolveUserAuthority(User user) {
        try {
            UserMembership userMembership = userMembershipMapper.findByUserIdAndStatus(user.getId());
            if (userMembership == null) {
                user.setRole(0);
                return new SimpleGrantedAuthority("ROLE_GUEST");
            }
            Membership membership = membershipMapper.selectById(userMembership.getMembershipId());
            user.setRole(membership.getId().intValue());
            return new SimpleGrantedAuthority(
                    Optional.ofNullable(membership.getRoleName()).orElse("ROLE_GUEST")
            );
        } catch (Exception e) {
            log.error("权限解析失败: {}", e.getMessage());
            user.setRole(0);
            return new SimpleGrantedAuthority("ROLE_GUEST");
        }
    }
}
