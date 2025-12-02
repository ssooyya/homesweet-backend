package com.homesweet.homesweetback.domain.settlement.service;

import com.homesweet.homesweetback.domain.settlement.aggregate.SettlementAggregator;
import com.homesweet.homesweetback.domain.settlement.dto.response.WeeklySettlementResponse;
import com.homesweet.homesweetback.domain.settlement.entity.DailySettlement;
import com.homesweet.homesweetback.domain.settlement.entity.WeeklySettlement;
import com.homesweet.homesweetback.domain.settlement.mapper.SettlementMapper;
import com.homesweet.homesweetback.domain.settlement.repository.DailySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.repository.WeeklySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.dto.response.EmptyResponse;
import com.homesweet.homesweetback.domain.settlement.util.calculator.SettlementCalculator;
import com.homesweet.homesweetback.domain.settlement.util.calculator.WeeklyDateRangeCalculator;
import com.homesweet.homesweetback.domain.settlement.util.saver.SettlementSaver;
import com.homesweet.homesweetback.domain.settlement.util.vo.SettlementTotals;
import com.homesweet.homesweetback.domain.settlement.validation.SettlementValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WeeklySettlementService {
    private final WeeklySettlementRepository weeklySettlementRepository;
    private final DailySettlementRepository dailySettlementRepository;
    private final SettlementCalculator settlementCalculator;
    private final EmptyResponse emptyResponse;
    private final SettlementMapper settlementMapper;
    private final SettlementValidator settlementValidator;
    private final SettlementAggregator settlementAggregator;
    private final SettlementSaver settlementSaver;

    // 주별 데이터 조회(페이지 처리)
    @Transactional(readOnly = true)
    public Page<WeeklySettlementResponse> getWeeklySummary(Long userId, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        // 1. 주 시작일: 월요일, 주 종료일: 일요일 , 주차 구하기
        WeeklyDateRangeCalculator.WeeklyDateRange range =
                WeeklyDateRangeCalculator.getWeeklyDateRange(startDate, endDate);

        // 2. 페이지로 정산일시 기준 월별 정산목록 조회
        Page<WeeklySettlement> weeklySettlementsPage = findWeeklySettlements(userId, pageable, range.firstWeekStart(), range.lastWeekStartEx());

        // 3. 기간 전체의 총 주문 건수/총 정산 완료 건수/정산 완료율
        SettlementCalculator.SettlementStats stats = settlementCalculator.calculateStats(userId, startDate, endDate);

        // 4. 데이터가 존재하지 않으면 0 반환
        if (weeklySettlementsPage.isEmpty()) {
            return emptyResponse.createEmptyWeekly(range, pageable);
        }
        // 5. 응답 반환
        List<WeeklySettlementResponse> weeklySettlementResponses = settlementMapper.toWeeklySettlementResponse(
                weeklySettlementsPage.getContent(), stats, range.week()
        );
        return new PageImpl<>(weeklySettlementResponses, pageable, stats.totalCount());
    }

    private Page<WeeklySettlement> findWeeklySettlements(Long userId, Pageable pageable, LocalDate firstWeekStart, LocalDate lastWeekStartEx) {
        Page<WeeklySettlement> weeklySettlements = weeklySettlementRepository.findByWeeklySettlementByRange(userId, firstWeekStart, lastWeekStartEx, pageable);
        return weeklySettlements;
    }

    // 주차별 정산내역
    public void getWeeklySettlement(Long userId, LocalDate weekStart, LocalDate weekEnd) {
        // 1. 일별 집계내역 조회
        List<DailySettlement> settlements = findDailySettlements(userId);

        // 2. 검증
        settlementValidator.validateWeekly(settlements);

        // 3. 공통 집계 처리
        Map<LocalDate, SettlementTotals> weeklyTotalsMap =
                settlementAggregator.aggregate(
                        settlements,
                        d -> WeeklyDateRangeCalculator.monday(d.getSettlementDate().toLocalDate()),
                        d -> new SettlementTotals(
                                d.getTotalSales(),
                                d.getTotalFee(),
                                d.getTotalVat(),
                                d.getTotalRefund(),
                                d.getTotalSettlement()
                        )
                );
        // 4. upsert(저장)
        weeklyTotalsMap.forEach((weekStartDate, totals) -> {
                    LocalDate weekEndDate = weekStartDate.plusDays(6);
                    settlementSaver.saveWeekly(userId, (short) weekStartDate.getYear(), (byte) weekStartDate.getMonthValue(), weekStartDate, weekEndDate, totals);
                }
        );

    }

    private List<DailySettlement> findDailySettlements(Long userId) {
        List<DailySettlement> settlements = dailySettlementRepository.findByDailySettlement(userId);
        return settlements;
    }
}