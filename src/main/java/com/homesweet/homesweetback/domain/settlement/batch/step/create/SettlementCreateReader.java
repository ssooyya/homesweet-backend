package com.homesweet.homesweetback.domain.settlement.batch.step.create;

import com.homesweet.homesweetback.domain.order.entity.Order;
import com.homesweet.homesweetback.domain.order.entity.OrderStatus;
import com.homesweet.homesweetback.domain.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SettlementCreateReader
 * - 신규 정산 대상(Order)을 DB에서 조회하여 읽어오는 Reader
 * - Step 시작 시 단 1회 DB 조회하여 List<Order>를 생성
 * - 이후 ListItemReader가 Order를 하나씩 차례대로 반환
 */
@Component
@StepScope
@Deprecated
@RequiredArgsConstructor
public class SettlementCreateReader implements ItemReader<Order> {
    private final SettlementRepository settlementRepository;

    // JobParameters는 string 지원
    @Value("#{jobParameters['cutoff']}")
    private String cutoffString;
    // 실제 주문을 하나씩 반환하는 내부 List 기반 reader
    private ListItemReader<Order> orderListItemReader;

    @Override
    public Order read() {
        // 1. step 시작시 최초 1회만 DB 조회를 위해 초기화 ->  DB 호출 이후에는 조회 X -> 신규 정산 건이 있으면
        if (orderListItemReader == null) {
            // LocalDateTime 직접 주입 불가
            LocalDateTime cutoff = LocalDateTime.parse(cutoffString);
            // 신규 정산 대상 주문 조회
            List<Order> unSettledOrders = settlementRepository.findUnSettlementOrders(OrderStatus.COMPLETED, cutoff);
            // 조회 결과를 reader에게 위임 -> 이후 order 하나씩 반환
            orderListItemReader = new ListItemReader<>(unSettledOrders);
        }
        // 2. 초기화 이후 Order을 1개씩 반환
        return orderListItemReader.read();
    }
}
