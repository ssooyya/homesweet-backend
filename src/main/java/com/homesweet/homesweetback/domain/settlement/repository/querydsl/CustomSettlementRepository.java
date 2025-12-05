package com.homesweet.homesweetback.domain.settlement.repository.querydsl;

import com.homesweet.homesweetback.domain.order.entity.OrderStatus;
import com.homesweet.homesweetback.domain.settlement.dto.response.SettlementCreateDto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface CustomSettlementRepository {
    int applyRefundAmount(Long orderId, BigDecimal refundAmount);

    //    List<SettlementCreateDto> findUnsettledOrdersCursor(
//            OrderStatus orderStatus, LocalDateTime cutoff, Long lastId, int limit
//    );
    List<Long> findUnsettledOrderIds(
            OrderStatus orderStatus,
            LocalDateTime cutoff,
            Long lastId,
            int limit
    );

    List<SettlementCreateDto> findOrdersByIds(List<Long> orderIds);
}
