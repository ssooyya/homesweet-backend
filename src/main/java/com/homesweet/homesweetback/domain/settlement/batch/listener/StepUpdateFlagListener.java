package com.homesweet.homesweetback.domain.settlement.batch.listener;

import com.homesweet.homesweetback.domain.settlement.repository.TempSettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StepUpdateFlagListener implements StepExecutionListener {
    private final TempSettlementRepository tempSettlementRepository;

    @Override
    public void beforeStep(StepExecution stepExecution) {
        log.info("[TempTable] Creating temp_order_ids");
        tempSettlementRepository.createTempTable();
    }
    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        log.info("[TempTable] Bulk update join start");

        long start = System.currentTimeMillis();

        tempSettlementRepository.updateOrders();
        tempSettlementRepository.dropTempTable();

        long duration = System.currentTimeMillis() - start;
        log.info("[TempTable] Bulk update join completed in {} ms", duration);

        return stepExecution.getExitStatus();
    }
}
