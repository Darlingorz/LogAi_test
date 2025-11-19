package com.logai.creem.dto;

import com.logai.creem.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderStatusResponse {
    private OrderStatus status;
    private LocalDateTime updatedAt;
}
