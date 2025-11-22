package com.logai.creem.service;

import com.logai.creem.dto.CreemWebhookEvent;

public interface CreemWebhookService {

    /**
     * 处理 Creem Webhook 事件
     *
     * @param event webhook事件对象
     */
    void handleEvent(CreemWebhookEvent event);
}
