package com.homesweet.homesweetback.domain.settlement.batch.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.stereotype.Component;

/**
 * 개별 Step의 시작과 종료, 처리 건수(read/write/skip)를 기록하는 리스너
 * - Step별 성능 모니터링 (예: 일별 집계 소요시간)
 * - 장애 구간을 빠르게 특정 가능 (예: weeklyStep에서 실패 여부 추적)
 */
@Slf4j
@Component
public class SettlementStepListener implements StepExecutionListener {
    private Long startTime;
    // 실행 전
    @Override
    public void beforeStep(StepExecution stepExecution) {
        startTime = System.currentTimeMillis();
        log.info("[STEP 시작] {}", stepExecution.getStepName());
    }

    // 실행 후
    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        long duration = System.currentTimeMillis() - startTime;
        log.info("[STEP 종료] {} / status= {} / duration= {}ms / read ={} / skip= {}", stepExecution.getStepName(), stepExecution.getStatus(), duration, stepExecution.getReadCount(), stepExecution.getSkipCount());
        return stepExecution.getExitStatus();
    }
}