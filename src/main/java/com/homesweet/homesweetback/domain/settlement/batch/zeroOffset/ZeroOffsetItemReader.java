package com.homesweet.homesweetback.domain.settlement.batch.zeroOffset;

import com.homesweet.homesweetback.domain.order.entity.Order;
import com.homesweet.homesweetback.domain.order.entity.OrderStatus;
import com.homesweet.homesweetback.domain.settlement.dto.response.SettlementCreateDto;
import com.homesweet.homesweetback.domain.settlement.repository.SettlementRepository;
import com.homesweet.homesweetback.domain.settlement.repository.querydsl.CustomSettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@StepScope
@RequiredArgsConstructor
public class ZeroOffsetItemReader implements ItemReader<SettlementCreateDto> {
    private final CustomSettlementRepository customSettlementRepository;
    private final LocalDateTime cutoff;

    private static final int PAGE_SIZE = 1000;

    private boolean initialized = false;
    private Long lastId = 0L;

    private List<SettlementCreateDto> buffer = new ArrayList<>();
    private int bufferIdx = 0;

    public ZeroOffsetItemReader(
            @Value("#{jobParameters['cutoff']}") String cutoffString,
            CustomSettlementRepository customSettlementRepository
    ) {
        this.customSettlementRepository = customSettlementRepository;
        this.cutoff = LocalDateTime.parse(cutoffString);
    }

    @Override
    @Transactional(readOnly = true)
    public SettlementCreateDto read() {

        if (!initialized) {
            log.info("[ZeroOffsetItemReader] 최초 실행 cutoff={}", cutoff);
            initialized = true;
        }

        // 버퍼 다 읽었으면 다시 채우기
        if (bufferIdx >= buffer.size()) {
            fillBuffer();
            if (buffer.isEmpty()) {
                return null; // Step 끝
            }
        }

        return buffer.get(bufferIdx++);
    }


    private void fillBuffer() {

        // 1) ID 목록만 조회 (가장 빠른 쿼리)
        List<Long> ids = customSettlementRepository.findUnsettledOrderIds(
                OrderStatus.COMPLETED,
                cutoff,
                lastId,
                PAGE_SIZE
        );

        if (ids.isEmpty()) {
            buffer = List.of();
            return;
        }

        // 2) 실제 데이터 조회 (JOIN)
        buffer = customSettlementRepository.findOrdersByIds(ids);

        // cursor 업데이트
        lastId = ids.get(ids.size() - 1);
        bufferIdx = 0;

        log.info("[ZeroOffsetItemReader] Loaded chunk: size={} lastId={}", buffer.size(), lastId);
    }

}
