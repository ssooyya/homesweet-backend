package com.homesweet.homesweetback.domain.settlement.batch.step.aggregate;

import com.homesweet.homesweetback.domain.settlement.aggregate.SettlementAggregator;
import com.homesweet.homesweetback.domain.settlement.entity.MonthlySettlement;
import com.homesweet.homesweetback.domain.settlement.repository.MonthlySettlementRepository;
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
import org.springframework.validation.Validator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class YearlySettlementTasklet implements Tasklet {
    private final SettlementRepository settlementRepository;
    private final SettlementValidator settlementValidator;
    private final SettlementAggregator settlementAggregator;
    private final MonthlySettlementRepository monthlySettlementRepository;
    private final SettlementSaver settlementSaver;
    private final Validator validator;

    @Value("#{jobParameters['cutoff']}")
    private String cutoffString;

    @Override
    public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) {
        // 1. 연도 계산
        LocalDate cutoffDate = LocalDateTime.parse(cutoffString).toLocalDate();
//        Year year = Year.from(cutoffDate);
        Short year = (short) cutoffDate.getYear();
        LocalDateTime start = LocalDate.of(year, 1, 1).atStartOfDay();
        LocalDateTime end = LocalDate.of(year + 1, 1, 1).atStartOfDay();

        // 2. 정산 대상 사용자 목록 조회
        List<Long> userIds = settlementRepository.findDistinctUserIds();
        for (Long userId : userIds) {
            SettlementTotals totals = settlementRepository.sumTotals(userId, start, end);
            // 4. 검증
            settlementValidator.validateTotals(totals);
            // 5. 저장
            settlementSaver.saveYearly(userId, year, totals);
            log.info("[연별 집계] userId={} 날짜={} 총 정산금액={}", userId, cutoffDate, totals.getTotalSettlement());
        }
        log.info("YearlySettlementTasklet 성공");
        return RepeatStatus.FINISHED;
    }
}
