package com.homesweet.homesweetback.domain.settlement.service;

import com.homesweet.homesweetback.domain.settlement.dto.response.*;
import com.homesweet.homesweetback.domain.settlement.entity.DailySettlement;
import com.homesweet.homesweetback.domain.settlement.entity.MonthlySettlement;
import com.homesweet.homesweetback.domain.settlement.entity.WeeklySettlement;
import com.homesweet.homesweetback.domain.settlement.entity.YearlySettlement;
import com.homesweet.homesweetback.domain.settlement.mapper.SettlementMapper;
import com.homesweet.homesweetback.domain.settlement.repository.*;
import com.homesweet.homesweetback.domain.settlement.util.calculator.MonthlyDateRangeCalculator;
import com.homesweet.homesweetback.domain.settlement.util.calculator.SettlementCalculator;
import com.homesweet.homesweetback.domain.settlement.util.calculator.WeeklyDateRangeCalculator;
import com.homesweet.homesweetback.domain.settlement.util.calculator.YearlyDateRangeCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementCacheService {
    private final DailySettlementRepository dailySettlementRepository;
    private final SettlementMapper mapper;
    private final SettlementCalculator settlementCalculator;
    private final YearlySettlementRepository yearlySettlementRepository;
    private final WeeklySettlementRepository weeklySettlementRepository;
    private final MonthlySettlementRepository monthlySettlementRepository;
    private final SettlementRepository settlementRepository;
    private final WeeklyDateRangeCalculator weeklyCalc;
    private final YearlyDateRangeCalculator yearlyCalc;
    private final MonthlyDateRangeCalculator monthlyCalc;

    @Cacheable(
            value = "daily:summary",
            key = "@settlementKeyBuilder.dailySummaryKey(#userId, #startDate, #endDate, #pageable.pageNumber, #pageable.pageSize)"
    )
    public List<DailySettlementResponse> getDailyContentCache(
            Long userId, LocalDate startDate, LocalDate endDate, Pageable pageable
    ) {

        LocalDateTime from = startDate.atStartOfDay();
        LocalDateTime to   = endDate.plusDays(1).atStartOfDay();

        Page<DailySettlement> page =
                dailySettlementRepository.findByDailySettlementByRange(userId, from, to, pageable);

        if (page.isEmpty()) {return List.of();}

        SettlementStatsDto stats = settlementRepository.findStats(userId, from, to);

        return mapper.toDailySettlementResponseList(page.getContent(), stats);
    }

    @Cacheable(
            value = "weekly:summary",
            key = "@settlementKeyBuilder.weeklySummaryKey(#userId, #startDate, #pageable.pageNumber, #pageable.pageSize)"
    )
    public List<WeeklySettlementResponse> getWeeklyContentCache(
            Long userId, LocalDate startDate, LocalDate endDate, Pageable pageable
    ) {

        WeeklyDateRangeCalculator.WeeklyDateRange range =
                weeklyCalc.getWeeklyDateRange(startDate, endDate);

        Page<WeeklySettlement> page =
                weeklySettlementRepository.findByWeeklySettlementByRange(
                        userId,
                        range.firstWeekStart(),
                        range.lastWeekStartEx(),
                        pageable
                );

        if (page.isEmpty()) {return List.of();}

        SettlementStatsDto stats = settlementRepository.findStats(
                userId,
                range.firstWeekStart().atStartOfDay(),
                range.lastWeekStartEx().atStartOfDay()
        );

        return mapper.toWeeklySettlementResponse(page.getContent(), stats, range.week());
    }

    @Cacheable(
            value = "monthly:summary",
            key = "@settlementKeyBuilder.monthlySummaryKey(#userId, #startDate, #endDate, #pageable.pageNumber, #pageable.pageSize)"
    )
    public List<MonthlySettlementResponse> getMonthlyContentCache(
            Long userId, LocalDate startDate, LocalDate endDate, Pageable pageable
    ) {

        MonthlyDateRangeCalculator.MonthlyDateRange range =
                monthlyCalc.MonthlyDateRangeCalculate(startDate, endDate);

        Page<MonthlySettlement> page =
                monthlySettlementRepository.findByMonthlySettlementByRange(
                        userId,
                        range.fromYear(),
                        range.fromMonth(),
                        range.toYear(),
                        range.toMonth(),
                        pageable
                );

        if (page.isEmpty()) {
            return List.of();
        }

        return mapper.toMonthlyResponses(page.getContent(), page.getTotalElements());
    }

    // ============================
    // YEARLY CACHE
    // ============================
    @Cacheable(
            value = "yearly:summary",
            key = "@settlementKeyBuilder.yearlySummaryKey(#userId, #startDate, #endDate, #pageable.pageNumber, #pageable.pageSize)"
    )
    public List<YearlySettlementResponse> getYearlyContentCache(
            Long userId, LocalDate startDate, LocalDate endDate, Pageable pageable
    ) {

        YearlyDateRangeCalculator.YearlyDateRange range =
                yearlyCalc.calculate(startDate, endDate);

        Page<YearlySettlement> page =
                yearlySettlementRepository.findByYearlySettlementByRange(
                        userId,
                        range.fromYear(),
                        range.toYearExclusive(),
                        pageable
                );

        if (page.isEmpty()) {
            return List.of();
        }

        return mapper.toYearlyResponses(page.getContent(), page.getTotalElements());
    }

    @Cacheable(
            value = "stats",
            key = "#userId + ':' + #startDate + ':' + #endDate"
    )
    public SettlementStatsDto getSettlementStats(Long userId, LocalDateTime startDate, LocalDateTime endDate) {
        return settlementRepository.findStats(userId, startDate, endDate);
    }
}
