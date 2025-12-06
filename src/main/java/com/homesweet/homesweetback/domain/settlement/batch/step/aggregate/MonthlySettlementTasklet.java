package com.homesweet.homesweetback.domain.settlement.batch.step.aggregate;

import com.homesweet.homesweetback.domain.settlement.repository.SettlementRepository;
import com.homesweet.homesweetback.domain.settlement.util.saver.SettlementSaver;
import com.homesweet.homesweetback.domain.settlement.util.vo.SettlementTotals;
import com.homesweet.homesweetback.domain.settlement.validation.SettlementValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class MonthlySettlementTasklet implements Tasklet {
    private final SettlementRepository settlementRepository;
    private final SettlementValidator settlementValidator;
    private final SettlementSaver settlementSaver;

    @Value("#{jobParameters['cutoff']}")
    private String cutoffString;

    @Override
    public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext){
        // 1. 월 계산
        LocalDate cutoffDate = LocalDateTime.parse(cutoffString).toLocalDate();
        YearMonth yearMonth = YearMonth.from(cutoffDate);
        LocalDateTime start = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime end = yearMonth.plusMonths(1).atDay(1).atStartOfDay();
        short year  = (short) cutoffDate.getYear();
        byte month  = (byte) cutoffDate.getMonthValue();

        log.info("MonthlySettlementTasklet 시작: {}월", yearMonth);
        // 2. 정산 대상 사용자 목록 조회
        List<Long> userIds = settlementRepository.findDistinctUserIds();
        for(Long userId : userIds){
            // 3. DB SUM 한번에
            SettlementTotals totals = settlementRepository.sumTotals(userId, start, end);
            // 4. 검증
            settlementValidator.validateTotals(totals);
            // 5. 저장
            settlementSaver.saveMonthly(userId, yearMonth, totals);
            log.info("[월별 집계] userId={} 날짜={} 총 정산금액={}", userId, cutoffDate, totals.getTotalSettlement());
        }
        log.info("MonthlySettlementTasklet 성공");
        return RepeatStatus.FINISHED;
    }
}
