package com.homesweet.homesweetback.domain.settlement.service;

import com.homesweet.homesweetback.domain.settlement.aggregate.SettlementAggregator;
import com.homesweet.homesweetback.domain.settlement.dto.response.*;
import com.homesweet.homesweetback.domain.settlement.entity.DailySettlement;
import com.homesweet.homesweetback.domain.settlement.entity.MonthlySettlement;
import com.homesweet.homesweetback.domain.settlement.entity.WeeklySettlement;
import com.homesweet.homesweetback.domain.settlement.entity.YearlySettlement;
import com.homesweet.homesweetback.domain.settlement.mapper.SettlementMapper;
import com.homesweet.homesweetback.domain.settlement.repository.*;
import com.homesweet.homesweetback.domain.settlement.util.SettlementStatusUpdater;
import com.homesweet.homesweetback.domain.settlement.util.calculator.MonthlyDateRangeCalculator;
import com.homesweet.homesweetback.domain.settlement.util.calculator.SettlementCalculator;
import com.homesweet.homesweetback.domain.settlement.util.calculator.WeeklyDateRangeCalculator;
import com.homesweet.homesweetback.domain.settlement.util.calculator.YearlyDateRangeCalculator;
import com.homesweet.homesweetback.domain.settlement.util.saver.SettlementSaver;
import com.homesweet.homesweetback.domain.settlement.validation.SettlementValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SettlementCacheService {
    private final DailySettlementRepository dailySettlementRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementMapper settlementMapper;
    private final SettlementCalculator settlementCalculator;
    private final YearlySettlementRepository yearlySettlementRepository;
    private final YearlyDateRangeCalculator yearlyDateRangeCalculator;
    private final WeeklySettlementRepository weeklySettlementRepository;
    private final MonthlyDateRangeCalculator monthlyDateRangeCalculator;
    private final MonthlySettlementRepository monthlySettlementRepository;

    // redis cache 적용
    @Cacheable(
            value = "daily:summary",
            key = "@settlementKeyBuilder.dailySummaryKey(#userId, #startDate, #endDate, #pageable)"
    )
    public List<DailySettlementResponse> getDailyContentCache(
            Long userId, LocalDate startDate, LocalDate endDate, Pageable pageable
    ) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();

        Page<DailySettlement> page =
                dailySettlementRepository.findByDailySettlementByRange(userId, start, end, pageable);

        if (page.isEmpty()) return List.of();

        SettlementCalculator.SettlementStats stats =
                settlementCalculator.calculateStats(userId, startDate, endDate);

        return settlementMapper.toDailySettlementResponseList(page.getContent(), stats);
    }

    // ==============================
    //   2) WEEKLY CACHE (월 기준 주차)
    // ==============================
    @Cacheable(
            value = "weekly:summary",
            key = "@settlementKeyBuilder.weeklySummaryKey(#userId, #startDate, #pageable)"
    )
    public List<WeeklySettlementResponse> getWeeklyContentCache(
            Long userId, LocalDate startDate, LocalDate endDate, Pageable pageable
    ) {
        WeeklyDateRangeCalculator.WeeklyDateRange range =
                WeeklyDateRangeCalculator.getWeeklyDateRange(startDate, endDate);

        Page<WeeklySettlement> weeklyPage =
                weeklySettlementRepository.findByWeeklySettlementByRange(
                        userId,
                        range.firstWeekStart(),
                        range.lastWeekStartEx(),
                        pageable
                );

        if (weeklyPage.isEmpty()) return List.of();

        SettlementCalculator.SettlementStats stats =
                settlementCalculator.calculateStats(userId, startDate, endDate);

        return settlementMapper.toWeeklySettlementResponse(
                weeklyPage.getContent(),
                stats,
                range.week()
        );
    }


    @Cacheable(value = "monthly:summary", key = "@settlementKeyBuilder.monthlySummaryKey(#userId, #startDate, #endDate, #pageable)")
    public List<MonthlySettlementResponse> getMonthlyContentCache(Long userId, LocalDate startDate, LocalDate endDate, Pageable pageable){
        MonthlyDateRangeCalculator.MonthlyDateRange range = monthlyDateRangeCalculator.MonthlyDateRangeCalculate(startDate, endDate);
        Page<MonthlySettlement> monthlyPage = monthlySettlementRepository.findByMonthlySettlementByRange(userId,range.fromYear(), range.fromMonth(), range.toYear(), range.toMonth(), pageable);
        if(monthlyPage.isEmpty()){
            List.of();
        }
        // 4. 총 카운트는 mapper에서 사용하므로 계산
//        long totalCount = settlementRepository.countAllByOrderedAt(
//                userId,
//                range.from(),
//                range.toExclusive()
//        );
        Page<MonthlySettlement> pageInfo =
                monthlySettlementRepository.findByMonthlySettlementByRange(userId, range.fromYear(), range.fromMonth(), range.toYear(), range.toMonth(), pageable);
        // 2. 총 주문건수 계산
        long totalCount = pageInfo.getTotalElements();

        // 5. 캐싱될 Content만 반환
        return settlementMapper.toMonthlyResponses(monthlyPage.getContent(), totalCount);

    }

    // ==============================
    //   3) YEARLY CACHE
    // ==============================
    @Cacheable(
            value = "yearly:summary",
            key = "@settlementKeyBuilder.yearlySummaryKey(#userId, #startDate, #endDate, #pageable)"
    )
    public List<YearlySettlementResponse> getYearlyContentCache(
            Long userId, LocalDate startDate, LocalDate endDate, Pageable pageable
    ) {

        YearlyDateRangeCalculator.YearlyDateRange range =
                yearlyDateRangeCalculator.calculate(startDate, endDate);

        Page<YearlySettlement> yearlyPage =
                yearlySettlementRepository.findByYearlySettlementByRange(
                        userId,
                        range.fromYear(),
                        range.toYearExclusive(),
                        pageable
                );

        if (yearlyPage.isEmpty()) return List.of();
        Page<YearlySettlement> pageInfo =
                yearlySettlementRepository.findByYearlySettlementByRange(
                        userId, range.fromYear(), range.toYearExclusive(), pageable
                );
        long totalCount = pageInfo.getTotalElements();

        return settlementMapper.toYearlyResponses(yearlyPage.getContent(), totalCount);
    }
}
