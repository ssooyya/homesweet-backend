package com.homesweet.homesweetback.domain.settlement.batch.step.aggregate;

import com.homesweet.homesweetback.common.exception.BusinessException;
import com.homesweet.homesweetback.common.exception.ErrorCode;
import com.homesweet.homesweetback.domain.settlement.aggregate.SettlementAggregator;
import com.homesweet.homesweetback.domain.settlement.entity.Settlement;
import com.homesweet.homesweetback.domain.settlement.repository.DailySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.repository.SettlementRepository;
import com.homesweet.homesweetback.domain.settlement.util.SettlementStatusUpdater;
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

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;


@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
/**
 * 여러 settlement -> 한 건의 dailySettlement -> tasklet
 * */
public class DailySettlementTasklet implements Tasklet {
    private final SettlementRepository settlementRepository;
    private final SettlementValidator settlementValidator;
    private final SettlementAggregator settlementAggregator;
    private final SettlementSaver settlementSaver;
    private final SettlementStatusUpdater settlementStatusUpdater;
    private final DailySettlementRepository dailySettlementRepository;
    private final Clock clock;

    @Value("#{jobParameters['cutoff']}")
    private String cutoffString;

    @Override
    public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) {
        // 1. 일자 계산
        LocalDate cutoffDate = LocalDateTime.parse(cutoffString).toLocalDate();
//        LocalDate cutoffDate;
//
//        try {
//            cutoffDate = LocalDateTime.parse(cutoffString).toLocalDate();
//        } catch (Exception e) {
//            // fallback → 테스트는 fixed clock 적용됨
//            cutoffDate = LocalDate.now(clock);
//        }

        LocalDateTime start = cutoffDate.atStartOfDay();
        LocalDateTime end = cutoffDate.plusDays(1).atStartOfDay();
        log.info("DailySettlementTasklet 시작: {}", cutoffDate);

        // 2. 정산 대상 사용자 목록 조회
        List<Long> userIds = settlementRepository.findDistinctUserIds();
        for (Long userId : userIds) {
            // 3. DB SUM 한번에
            SettlementTotals totals = settlementRepository.sumTotals(userId, start, end);
            // 4. 검증
            settlementValidator.validateTotals(totals);
            // 5. 저장
            settlementSaver.saveDaily(userId, cutoffDate, totals);
            // 6. 상태 업데이트
            settlementStatusUpdater.markDailyCompleted(userId, start, end);
            log.info("[일별 집계] userId={} 날짜={} 총 정산금액={}", userId, cutoffDate, totals.getTotalSettlement());
        }
        log.info("DailySettlementTasklet 성공");
        return RepeatStatus.FINISHED;
    }
}