package com.homesweet.homesweetback.domain.settlement.service;

import com.homesweet.homesweetback.domain.settlement.aggregate.SettlementAggregator;
import com.homesweet.homesweetback.domain.settlement.dto.response.MonthlySettlementResponse;
import com.homesweet.homesweetback.domain.settlement.entity.MonthlySettlement;
import com.homesweet.homesweetback.domain.settlement.entity.WeeklySettlement;
import com.homesweet.homesweetback.domain.settlement.mapper.SettlementMapper;
import com.homesweet.homesweetback.domain.settlement.repository.MonthlySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.repository.SettlementRepository;
import com.homesweet.homesweetback.domain.settlement.repository.WeeklySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.dto.response.EmptyResponse;
import com.homesweet.homesweetback.domain.settlement.util.calculator.MonthlyDateRangeCalculator;
import com.homesweet.homesweetback.domain.settlement.util.saver.SettlementSaver;
import com.homesweet.homesweetback.domain.settlement.util.vo.SettlementTotals;
import com.homesweet.homesweetback.domain.settlement.validation.SettlementValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MonthlySettlementService {
    private final MonthlySettlementRepository monthlySettlementRepository;
    private final WeeklySettlementRepository weeklySettlementRepository;
    private final SettlementRepository settlementRepository;
    private final MonthlyDateRangeCalculator monthlyDateRangeCalculator;
    private final EmptyResponse emptyResponse;
    private final SettlementMapper settlementMapper;
    private final SettlementValidator settlementValidator;
    private final SettlementAggregator settlementAggregator;
    private final SettlementSaver settlementSaver;
    private final SettlementCacheService settlementCacheService;

    // redis cache 적용

    // 월별 데이터 조회(페이지 처리)
    @Transactional(readOnly = true)
    public Page<MonthlySettlementResponse> getMonthlySummary(Long userId, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        // 1. 월 구하기
        MonthlyDateRangeCalculator.MonthlyDateRange range = monthlyDateRangeCalculator.MonthlyDateRangeCalculate(startDate, endDate);

        Page<MonthlySettlement> pageInfo =
                monthlySettlementRepository.findByMonthlySettlementByRange(userId, range.fromYear(), range.fromMonth(), range.toYear(), range.toMonth(), pageable);
        // 2. 총 주문건수 계산
        long totalCount = pageInfo.getTotalElements();
//        long totalCount = settlementRepository.countAllByOrderedAt(userId, range.from(), range.toExclusive());

        // 3. 월별 집계 조회
        List<MonthlySettlementResponse> monthlySettlements =
                settlementCacheService.getMonthlyContentCache(userId, startDate, endDate, pageable);

        // 4. 데이터가 없으면 빈 페이지
        if (monthlySettlements.isEmpty()) {
            return emptyResponse.createEmptyMonthly(range.fromYM(), pageable);
        }
        // 5. 응답 반환
//        List<MonthlySettlementResponse> monthlySettlement = settlementMapper.toMonthlyResponses(monthlySettlements.getContent(), totalCount);

        // 6. page 반환
        return new PageImpl<>(monthlySettlements, pageable, totalCount);
    }

    // 월별 집계
    public void getMonthlySettlement(Long userId) {
        // 1. 주별 집계내역 조회
        List<WeeklySettlement> settlements = findWeeklySettlements(userId);
        // 2. 검증
        settlementValidator.validateMonthly(settlements);
        // 3. 공통 월별 집계 처리
        Map<YearMonth, SettlementTotals> monthlyTotalsMap =
                settlementAggregator.aggregate(
                        settlements,
                        w -> YearMonth.of(w.getYear(), w.getMonth()),   // 월별 그룹핑 Key
                        w -> new SettlementTotals(
                                w.getTotalSales(),
                                w.getTotalFee(),
                                w.getTotalVat(),
                                w.getTotalRefund(),
                                w.getTotalSettlement()
                        )
                );
        // 4. upsert
        monthlyTotalsMap.forEach((yearMonth, totals) ->
                settlementSaver.saveMonthly(userId, yearMonth, totals)
        );
    }
    private List<WeeklySettlement> findWeeklySettlements(Long userId) {
        return weeklySettlementRepository.findByWeeklySettlement(userId);
    }
}