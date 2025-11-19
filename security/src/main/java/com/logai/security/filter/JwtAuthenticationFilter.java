package com.logai.security.filter;

import com.logai.security.oauth2.service.OAuth2TokenService;
import com.logai.security.service.TokenService;
import com.logai.security.util.JwtUtils;
import com.logai.user.mapper.UserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final TokenService tokenService;
    private final OAuth2TokenService oauth2TokenService;
    private final UserMapper userMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

    }
//    private final MembershipRepository membershipRepository;
//    private final UserMembershipRepository userMembershipRepository;
//
//    private static final String FILTER_ALREADY_APPLIED = "jwt_filter_applied";
//
//    @Override
//    protected void doFilterInternal(HttpServletRequest request,
//                                    HttpServletResponse response,
//                                    FilterChain chain)
//            throws ServletException, IOException {
//
//        String requestId = UUID.randomUUID().toString();
//        String method = request.getMethod();
//        String path = request.getRequestURI();
//        String query = request.getQueryString();
//
//        // 避免重复应用过滤器
//        if (request.getAttribute(FILTER_ALREADY_APPLIED) == null) {
//            request.setAttribute(FILTER_ALREADY_APPLIED, true);
//            log.debug("[{}] JWT 过滤器首次应用：{} {}?{}", requestId, method, path, query);
//
//            printHeaders(requestId, request);
//        }
//
//        String token = extractToken(request);
//
//        // GET 请求打印参数
//        if ("GET".equalsIgnoreCase(method)) {
//            printQuery(requestId, request);
//            processJwt(request, response, chain, token, path);
//            return;
//        }
//
//        boolean isJson = Optional.ofNullable(request.getContentType())
//                .map(ct -> ct.contains(MediaType.APPLICATION_JSON_VALUE))
//                .orElse(false);
//
//        HttpServletRequest requestWrapper = request;
//
//        // POST / PUT JSON 需要打印 body
//        if (("POST".equals(method) || "PUT".equals(method)) && isJson) {
//            requestWrapper = new CachedBodyHttpServletRequest(request);
//            printJsonBody(requestId, requestWrapper);
//        }
//
//        processJwt(requestWrapper, response, chain, token, path);
//    }
//
//    // ---------------- JWT 校验核心逻辑 ----------------
//
//    private void processJwt(HttpServletRequest request,
//                            HttpServletResponse response,
//                            FilterChain chain,
//                            String token,
//                            String path)
//            throws IOException, ServletException {
//
//        if (shouldSkip(path)) {
//            chain.doFilter(request, response);
//            return;
//        }
//
//        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
//
//            Claims claims = jwtUtils.extractClaims(token).orElse(null);
//            if (claims != null) {
//                String userId = claims.getSubject();
//                String clientId = claims.get("client_id", String.class);
//
//                TokenInfo info = resolve(token, clientId);
//                if (info != null && userId.equals(info.getUserUuid())) {
//
//                    User user = userMapper.findByUuid(info.getUserUuid()).orElse(null);
//                    if (user != null) {
//
//                        Authentication auth = buildAuth(user);
//                        SecurityContextHolder.getContext().setAuthentication(auth);
//                    }
//                }
//            }
//        }
//
//        chain.doFilter(request, response);
//    }
//
//    private boolean shouldSkip(String path) {
//        return path.startsWith("/api/auth") ||
//                path.startsWith("/api/user/login") ||
//                path.startsWith("/api/user/register") ||
//                path.startsWith("/error") ||
//                path.equals("/");
//    }
//
//    private TokenInfo resolve(String token, String clientId) {
//        try {
//            return (clientId != null && !clientId.isBlank())
//                    ? oauth2TokenService.validateAccessTokenSync(token)
//                    : tokenService.validateAccessTokenSync(token);
//        } catch (Exception e) {
//            return null;
//        }
//    }
//
//    private String extractToken(HttpServletRequest request) {
//        if (request.getCookies() != null) {
//            for (var c : request.getCookies()) {
//                if ("session".equals(c.getName())) {
//                    return c.getValue();
//                }
//            }
//        }
//
//        String h = request.getHeader("Authorization");
//        return (h != null && h.startsWith("Bearer ")) ? h.substring(7) : null;
//    }
//
//    private Authentication buildAuth(User user) {
//        SimpleGrantedAuthority role = determineAuthority(user);
//        return new UsernamePasswordAuthenticationToken(user, null, Collections.singleton(role));
//    }
//
//    private SimpleGrantedAuthority determineAuthority(User user) {
//        try {
//            LocalDateTime now = LocalDateTime.now();
//            return userMembershipRepository.findByUserIdAndStatus(user.getId())
//                    .filter(um -> um.getEndTime() == null || um.getEndTime().isAfter(now))
//                    .flatMap(um -> membershipRepository.findById(um.getMembershipId()))
//                    .map(mem -> {
//                        user.setRole(Convert.toInt(mem.getId()));
//                        return new SimpleGrantedAuthority(
//                                Optional.ofNullable(mem.getRoleName()).orElse("ROLE_GUEST")
//                        );
//                    })
//                    .orElseGet(() -> new SimpleGrantedAuthority("ROLE_GUEST"));
//        } catch (Exception e) {
//            return new SimpleGrantedAuthority("ROLE_GUEST");
//        }
//    }
//
//    // ------------------- 日志打印逻辑 -------------------
//
//    private void printHeaders(String requestId, HttpServletRequest request) {
//        Map<String, Object> map = new LinkedHashMap<>();
//        Enumeration<String> names = request.getHeaderNames();
//        while (names.hasMoreElements()) {
//            String k = names.nextElement();
//            map.put(k, request.getHeader(k));
//        }
//        log.info("[{}] 请求头:\n{}", requestId, pretty(map));
//    }
//
//    private void printQuery(String requestId, HttpServletRequest request) {
//        Map<String, Object> map = new LinkedHashMap<>();
//        request.getParameterMap().forEach((k, v) -> map.put(k, v.length == 1 ? v[0] : Arrays.asList(v)));
//        log.info("[{}] GET 参数:\n{}", requestId, pretty(map));
//    }
//
//    private void printJsonBody(String requestId, HttpServletRequest request) throws IOException {
//        String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
//        log.info("[{}] JSON 请求体:\n{}", requestId, prettyJson(body));
//    }
//
//    private String pretty(Object obj) {
//        try {
//            ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
//            return mapper.writeValueAsString(obj);
//        } catch (Exception e) {
//            return String.valueOf(obj);
//        }
//    }
//
//    private String prettyJson(String json) {
//        try {
//            ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
//            return mapper.writerWithDefaultPrettyPrinter()
//                    .writeValueAsString(mapper.readValue(json, Object.class));
//        } catch (Exception e) {
//            return json;
//        }
//    }

    // ----------- 可重复读取 Body 的包装类 -----------
    public static class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

        private final byte[] cached;

        public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            cached = request.getInputStream().readAllBytes();
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream bais = new ByteArrayInputStream(cached);
            return new ServletInputStream() {

                @Override
                public boolean isFinished() {
                    return bais.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                }

                @Override
                public int read() {
                    return bais.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream()));
        }
    }
}
