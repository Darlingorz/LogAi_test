package com.logai.common.service;

import com.google.cloud.recaptchaenterprise.v1.RecaptchaEnterpriseServiceClient;
import com.google.recaptchaenterprise.v1.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class AssessmentService {

    @Value("${assessment.project-id}")
    private String projectId;

    @Value("${assessment.recaptcha-key}")
    private String recaptchaKey;

    private static final float LOGIN_THRESHOLD = 0.5f;
    private static final float PAYMENT_THRESHOLD = 0.8f;
    private static final float COMMENT_THRESHOLD = 0.4f;
    private static final float DEFAULT_THRESHOLD = 0.5f;


    public List<String> createAssessment(String token, String action, String userIpAddress)
            throws IOException {
        try (RecaptchaEnterpriseServiceClient client = RecaptchaEnterpriseServiceClient.create()) {
            // 设置要跟踪的事件的属性。
            Event.Builder eventBuilder = Event.newBuilder()
                    .setSiteKey(this.recaptchaKey)
                    .setToken(token);

            if (StringUtils.hasText(userIpAddress)) {
                eventBuilder.setUserIpAddress(userIpAddress);
            }

            Event event = eventBuilder.build();

            // 构建评估请求。
            CreateAssessmentRequest createAssessmentRequest =
                    CreateAssessmentRequest.newBuilder()
                            .setParent(ProjectName.of(this.projectId).toString())
                            .setAssessment(Assessment.newBuilder().setEvent(event).build())
                            .build();

            Assessment response = client.createAssessment(createAssessmentRequest);

            // 检查令牌是否有效。
            if (!response.getTokenProperties().getValid()) {
                String reason = response.getTokenProperties().getInvalidReason().name();
                log.error("CreateAssessment调用失败，因为令牌为：{} ，无效原因：{}", token, reason);
                return List.of("INVALID_TOKEN: " + reason);
            }

            // 检查是否执行了预期操作。
            if (!response.getTokenProperties().getAction().equals(action)) {
                log.error("reCAPTCHA 标签中的 action ('{}') 与预期操作 ('{}') 不匹配", response.getTokenProperties().getAction(), action);
                // 直接返回不合规原因
                return List.of("ACTION_MISMATCH");
            }

            float recaptchaScore = response.getRiskAnalysis().getScore();
            boolean scoreCompliant = isScoreCompliant(recaptchaScore, action);

            if (scoreCompliant) {
                return Collections.emptyList();
            } else {
                ArrayList<String> reasons = new ArrayList<>();
                if (!response.getRiskAnalysis().getReasonsList().isEmpty()) {
                    log.warn("具体风险原因:");
                    for (RiskAnalysis.ClassificationReason reason : response.getRiskAnalysis().getReasonsList()) {
                        reasons.add(reason.name());
                        log.warn("- {}", reason.name());
                    }
                } else {
                    reasons.add("LOW_SCORE");
                }
                return reasons;
            }
        }
    }

    public boolean isScoreCompliant(float score, String action) {
        float threshold = switch (action.toUpperCase()) {
            case "LOGIN" -> LOGIN_THRESHOLD;
            case "PAYMENT" -> PAYMENT_THRESHOLD;
            case "COMMENT" -> COMMENT_THRESHOLD;
            default -> DEFAULT_THRESHOLD;
        };
        return score >= threshold;
    }

}
