package com.logai.creem.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logai.creem.dto.objects.CheckoutObject;
import com.logai.creem.dto.objects.SubscriptionObject;
import com.logai.creem.enums.CreemEventType;
import com.logai.creem.enums.CreemObjectType;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Data
public class CreemWebhookEvent {

    private String id;

    @JsonProperty("eventType")
    private String eventTypeRaw;

    @JsonProperty("created_at")
    private Long createdAt;

    private EventObject object;

    public CreemEventType getCreemEventType() {
        return CreemEventType.fromString(eventTypeRaw);
    }

    public CreemObjectType getCreemObjectType() {
        return object != null ? CreemObjectType.fromString(object.getObjectType()) : CreemObjectType.UNKNOWN;
    }

    @Data
    public static class EventObject {
        private String id;

        @JsonProperty("object")
        private String objectType;

        private Map<String, Object> rawFields = new HashMap<>();

        @JsonAnySetter
        public void add(String key, Object value) {
            rawFields.put(key, value);
        }

        @JsonAnyGetter
        public Map<String, Object> any() {
            return rawFields;
        }


        /**
         * 强类型字段，按需填充
         */
        @JsonIgnore
        private CheckoutObject checkout;

        @JsonIgnore
        private SubscriptionObject subscription;
//
//        @JsonIgnore
//        private RefundObject refund;

        /**
         * 工厂方法：根据 objectType 构建强类型 bean
         */
        public void buildTypedObject(ObjectMapper objectMapper) {
            try {
                switch (CreemObjectType.fromString(objectType)) {
                    case CHECKOUT:
                        this.checkout = objectMapper.convertValue(rawFields, CheckoutObject.class);
                        log.info("成功解析 CheckoutObject: {}", checkout);
                        break;
                    case SUBSCRIPTION:
                        this.subscription = objectMapper.convertValue(rawFields, SubscriptionObject.class);
                        break;
//                    case REFUND:
//                        this.refund = objectMapper.convertValue(rawFields, RefundObject.class);
//                        break;
                    default:
                        // 其他类型不转换
                }
            } catch (Exception e) {
                log.warn("无法解析 {} 为强类型对象，保留 rawFields", objectType, e);
            }
        }
    }
}
