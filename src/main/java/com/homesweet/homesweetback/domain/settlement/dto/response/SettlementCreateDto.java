package com.homesweet.homesweetback.domain.settlement.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// 배치에서 lazy loading
public record SettlementCreateDto(
        Long orderId,
        Long sellerId,
        Long totalAmount,
        BigDecimal refundAmount,
        BigDecimal feeRate,
        BigDecimal vatRate,
        LocalDateTime orderedAt
) {
}
