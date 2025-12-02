package com.homesweet.homesweetback.domain.settlement.batch.step.create;

import com.homesweet.homesweetback.domain.settlement.entity.Settlement;
import com.homesweet.homesweetback.domain.settlement.repository.SettlementRepository;
import com.homesweet.homesweetback.domain.settlement.validation.SettlementValidator;
import org.apache.commons.collections4.ListUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SettlementCreateWriter
 * - Processor가 반환한 Settlement 리스트를 DB에 저장하는 Writer
 * - chunk 단위로 saveAll() 호출하여 대량 저장 처리
 */
@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class SettlementCreateWriter implements ItemWriter<Settlement> {
    private final SettlementRepository  settlementRepository;
    private final SettlementValidator settlementValidator;

    @Override
    public void write(Chunk<? extends Settlement> chunk){
        // 1. writer가 chunk 단위로 호출, settlements는 1000개 묶음으로 전달됨
        List<? extends Settlement> settlements = chunk.getItems();
        log.info("chunk 단위로 호출: {}", settlements);
        // 2. 저장할 값이 있는지 검증
        settlementValidator.validateNotEmpty(settlements);
        // 3. orderId 추출
        List<Long> orderIds = settlements.stream().map(s->s.getOrder().getId()).toList();
        log.info("[정산생성] chunk={} / orderIds={}", settlements.size(), orderIds.size());
        // 4. 정산 여부 true로 변경 50개 단위로 UPDATE 분할 실행 (병목 해결 핵심)
        List<List<Long>> partitions = ListUtils.partition(orderIds, 50);

        for (List<Long> part : partitions) {
            settlementRepository.markUpdateFlag(part);
        }
        // 5. chunk 단위 DB에 저장
        settlementRepository.saveAll(settlements);
        log.info("[정산 생성 writer] {}건 정산 저장 완료", settlements.size());
    }
}