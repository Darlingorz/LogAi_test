package com.logai.assint.mcp.server;

import com.logai.assint.enums.IntentType;
import com.logai.assint.mcp.meta.RecordMeta;
import com.logai.assint.mcp.meta.testmeta;
import com.logai.assint.service.AssistService;
import com.logai.assint.util.TokenCounter;
import com.logai.common.exception.BusinessException;
import com.logai.security.annotation.MemberOnly;
import com.logai.security.utils.AuthContextHolder;
import com.logai.user.repository.UserRepository;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssintMcpService {
    private static final String OUTPUT_TEMPLATE_SOURCE_URL = "https://www.logai.chat/demo.html";
    private static final String OUTPUT_TEMPLATE_RESOURCE_URI = "ui://widget/logai-record-card.html";

    private final AssistService assistService;
    private final UserRepository userRepository;

    @McpTool(description = """
              Records user-reported daily life events into their personal data log.
              This includes: meals, exercise, sleep, weight, physical sensations,
              emotional state, mood changes, and general lifestyle activities.
            
              When to call this tool:
              - Call this tool whenever the user expresses something about their own
                actions, body, mood, or daily condition (e.g. "I ate...", "I worked out...",
                "I feel...", "Today I...").
              - The purpose of this tool is **recording**, not analysis or advice.
            
              About the `message` parameter:
              - `message` may be a **concise and accurate summary** of what the user said.
              - It does NOT need to be the exact raw text.
              - Do not add assumptions, judgments, health advice, or emotional interpretation.
              - Example:
                User: "I just ran 2 kilometers. I'm a bit tired, but feeling ok."
                message = "Ran 2 km. Feeling slightly tired but overall okay."
            
              About the `sessionId` parameter:
              - Used to associate the recorded entry with the correct user/session context.
            
              What NOT to do:
              - Do not provide health recommendations.
              - Do not reinterpret emotions or motivations.
              - Do not evaluate or judge the user's behavior.
              - Only extract and record the factual content.
            """
            , name = "record"
            , annotations = @McpTool.McpAnnotations(
            destructiveHint = false,
            idempotentHint = true)
            , metaProvider = RecordMeta.class
    )
    @MemberOnly
    public Mono<McpSchema.CallToolResult> record(
            @McpToolParam(description = "Factual summary of the user’s reported activity or condition") String message,
            @McpToolParam(description = "Conversation session identifier") String sessionId) {
        TokenCounter counter = new TokenCounter();
        Map<String, Object> meta = new HashMap<>();

        if (message == null || message.trim().isEmpty()) {
            return Mono.error(BusinessException.validationError("message", "Message content cannot be empty"));
        }
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return Mono.error(BusinessException.validationError("sessionId", "Session ID cannot be empty"));
        }
        return AuthContextHolder.getCurrentUser()
                .switchIfEmpty(Mono.error(BusinessException.unauthorized("No security context found")))
                .flatMap(user -> assistService.handleIntentByType(
                                user,
                                message,
                                sessionId,
                                IntentType.RECORD,
                                counter
                        )
                        .timeout(Duration.ofSeconds(60))
                        .onErrorResume(error -> {
                            log.error("记录事件时发生错误: {}", error.getMessage(), error);
                            return Flux.just("{\"error\":\"" + error.getMessage() + "\"}");
                        })
                        .collectList()
                        .map(results -> {

                            meta.put("tokens_used", counter.getTotalTokens());
                            meta.put("intent", IntentType.RECORD.name());
                            meta.put("status", "completed");

                            Map<String, Object> structuredContent = new LinkedHashMap<>();
                            structuredContent.put("intentType", IntentType.RECORD.name());
                            structuredContent.put("entries", results);
                            structuredContent.put("timestamp", new Date());
                            structuredContent.put("sessionId", sessionId);

                            List<McpSchema.Content> content = Collections.emptyList();

                            log.info("事件记录成功：tokens={}, sessionId={}", counter.getTotalTokens(), sessionId);

                            return new McpSchema.CallToolResult(
                                    content,
                                    false,
                                    structuredContent,
                                    meta
                            );
                        }));
    }

    @McpResource(
            uri = "ui://widget/widget.html",
            name = "WidgetHtml",
            description = "Provides the HTML for the widget",
            metaProvider = testmeta.class
    )
    public Mono<McpSchema.ReadResourceResult> getWidgetHtml() {

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(OUTPUT_TEMPLATE_SOURCE_URL))
                .build();

        return Mono.fromCompletionStage(HttpClient.newBuilder().build().sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString()))
                .map(HttpResponse::body)
                .onErrorResume(err -> {
                    // Fallback minimal template embedding the remote app
                    String fallback = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>LogAI Record Card</title></head>" +
                            "<body style=\"margin:0;padding:0;height:100vh\">" +
                            "<iframe src=\"" + OUTPUT_TEMPLATE_SOURCE_URL + "\" style=\"border:0;width:100%;height:100%\"></iframe>" +
                            "</body></html>";
                    return Mono.just(fallback);
                })
                .map(html -> {
                    Map<String, Object> meta = Map.of(
                            "openai/widgetPrefersBorder", true,
                            "openai/widgetDomain", "https://www.logai.chat",
                            "openai/widgetCSP", Map.of(
                                    "connect_domains", List.of(OUTPUT_TEMPLATE_SOURCE_URL),
                                    "resource_domains", List.of("https://*.oaistatic.com", OUTPUT_TEMPLATE_SOURCE_URL)
                            )
                    );
                    McpSchema.TextResourceContents contents = new McpSchema.TextResourceContents(
                            "ui://widget/widget.html",
                            "text/html+skybridge",
                            html,
                            meta
                    );
                    return new McpSchema.ReadResourceResult(List.of(contents));
                });
    }
}
