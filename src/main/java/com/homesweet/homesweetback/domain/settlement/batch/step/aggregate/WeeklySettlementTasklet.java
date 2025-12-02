package com.homesweet.homesweetback.domain.settlement.batch.step.aggregate;

import com.homesweet.homesweetback.domain.settlement.aggregate.SettlementAggregator;
import com.homesweet.homesweetback.domain.settlement.entity.DailySettlement;
import com.homesweet.homesweetback.domain.settlement.entity.Settlement;
import com.homesweet.homesweetback.domain.settlement.repository.DailySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.repository.SettlementRepository;
import com.homesweet.homesweetback.domain.settlement.repository.WeeklySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.util.calculator.WeeklyDateRangeCalculator;
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
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class WeeklySettlementTasklet implements Tasklet {
    private final SettlementRepository settlementRepository;
    private final SettlementValidator settlementValidator;
    private final SettlementAggregator settlementAggregator;
    private final DailySettlementRepository dailySettlementRepository;
    private final SettlementSaver settlementSaver;
    private final WeeklySettlementRepository weeklySettlementRepository;

    @Value("#{jobParameters['cutoff']}")
    private String cutoffString;

    @Override
    public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) {
        System.out.println("🟣 WeeklySettlementTasklet START");
        System.out.println("🟣 cutoffString = " + cutoffString);
        // 1. 주별 계산
        LocalDate cutoff = LocalDateTime.parse(cutoffString).toLocalDate();
        LocalDate weekStart = WeeklyDateRangeCalculator.monday(cutoff);
        LocalDate weekEnd = WeeklyDateRangeCalculator.sunday(cutoff);
        log.info("WeeklySettlementTasklet 시작: {} ~ {}", weekStart, weekEnd);

        // 2. 정산 대상 사용자 목록 조회
        List<Long> userIds = settlementRepository.findDistinctUserIds();
        for (Long userId : userIds) {
            short year = (short) weekStart.getYear();
            byte month = (byte) weekStart.getMonthValue();
            // 3. DB SUM 한번에
            SettlementTotals totals = settlementRepository.sumTotals(userId, weekStart.atStartOfDay(), weekEnd.atStartOfDay());
            // 4. 검증
            settlementValidator.validateTotals(totals);
            // 5. 저장
            settlementSaver.saveWeekly(userId, year, month, weekStart, weekEnd, totals);
            log.info("[주별 집계] userId={} 날짜={} 총 정산금액={}", userId, cutoff, totals.getTotalSettlement());
        }
        log.info("WeeklySettlementTasklet 성공");
        return RepeatStatus.FINISHED;
    }
}
