package com.homesweet.homesweetback.domain.settlement.repository.querydsl;

import com.homesweet.homesweetback.domain.order.entity.Order;
import com.homesweet.homesweetback.domain.order.entity.OrderStatus;
import com.homesweet.homesweetback.domain.settlement.util.vo.SettlementTotals;

import java.time.LocalDateTime;
import java.util.List;

public interface CustomDailySettlementRepository {
    void upsertDaily(Long userId, LocalDateTime settlementDate, SettlementTotals totals);

}
