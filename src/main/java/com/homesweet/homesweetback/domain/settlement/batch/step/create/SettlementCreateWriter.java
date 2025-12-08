package com.homesweet.homesweetback.domain.settlement.batch.step.create;

import com.homesweet.homesweetback.domain.settlement.entity.Settlement;
import com.homesweet.homesweetback.domain.settlement.repository.SettlementRepository;
import com.homesweet.homesweetback.domain.settlement.repository.TempSettlementRepository;
import com.homesweet.homesweetback.domain.settlement.repository.bulk.SettlementBulkRepository;
import com.homesweet.homesweetback.domain.settlement.validation.SettlementValidator;
import org.apache.commons.collections4.ListUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.AfterStep;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
    private final SettlementRepository settlementRepository;
    private final SettlementValidator settlementValidator;


    // 모든 orderId를 모아두기 위한 buffer
    private final List<Long> orderIdBuffer = new ArrayList<>();
    private final TempSettlementRepository tempSettlementRepository;
    private final SettlementBulkRepository settlementBulkRepository;

    @Value("#{stepExecution}")
    private StepExecution stepExecution;


    @Override
    public void write(Chunk<? extends Settlement> chunk) {
        // 1. writer가 chunk 단위로 호출, settlements는 1000개 묶음으로 전달됨
        List<? extends Settlement> settlements = chunk.getItems();
        log.info("chunk 단위로 호출: {}", settlements);
        try {
            // 2. 저장할 값이 있는지 검증
            settlementValidator.validateNotEmpty(settlements);
            // 3. orderId 추출
//            List<Long> orderIds = settlements.stream().map(s -> s.getOrderId()).toList();
//            log.info("[정산 생성] chunk= {} / orderIds= {} ~ {}", settlements.size(), orderIds.get(0), orderIds.get(orderIds.size() - 1));

//             orderIds 누적
//            settlements.forEach(s -> orderIdBuffer.add(s.getOrderId()));
            // 2. orderId 수집

            for (Settlement s : settlements) {
                orderIdBuffer.add(s.getOrderId());
            }


            // 4. 정산 여부 true로 변경 50개 단위로 UPDATE 분할 실행 (병목 해결 핵심)
//            List<List<Long>> partitions = ListUtils.partition(orderIds, 300);
//
//            for (List<Long> part : partitions) {
//                settlementRepository.markUpdateFlag(part);
//            }
//            settlements.forEach(s -> orderIdBuffer.add(s.getOrderId()));

            // 5. chunk 단위 DB에 저장
            settlementBulkRepository.bulkInsert(settlements);
            log.info("[정산 생성 writer] {}건 정산 저장 완료", settlements.size());
        } catch (Exception e) {
            log.error("[Writer] 예외 발생! message={}", e.getMessage(), e);

            // ➤ 문제 발생한 settlement 들 상세히 찍기
            settlements.forEach(s -> {
                try {
                    log.error("실패 Settlement: orderId={}, userId={}, settlementDate={}",
                            s.getOrderId(),
                            s.getUserId(),
                            s.getSettlementDate()
                    );
                } catch (Exception ex) {
                    log.error("Settlement 로그 중 오류: {}", ex.getMessage());
                }
            });
            throw e; // 반드시 다시 던져야 Batch 가 실패 상태로 종료됨
        }
    }

    // Step 종료 시점에 실행되도록 별도 메서드 추가
    @AfterStep
    public ExitStatus afterStep(StepExecution stepExecution) {

        log.info("[Writer] Updating {} orders using temp table...", orderIdBuffer.size());

        if (orderIdBuffer.isEmpty()) {
            return stepExecution.getExitStatus();
        }

        // 1) temp table 생성
        tempSettlementRepository.createTempTable();

        // 2) orderIds bulk insert
        tempSettlementRepository.insertOrderIds(orderIdBuffer);

        // 3) UPDATE 1번 실행
        int updated = tempSettlementRepository.updateOrders();
        log.info("[Writer] Bulk updated {} orders", updated);

        // 4) temp table 삭제
        tempSettlementRepository.dropTempTable();

        return stepExecution.getExitStatus();
    }
}