package com.homesweet.homesweetback.domain.settlement.util;

import com.homesweet.homesweetback.domain.settlement.util.calculator.WeeklyDateRangeCalculator;
import com.homesweet.homesweetback.domain.settlement.util.calculator.YearlyDateRangeCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component("settlementKeyBuilder")
@RequiredArgsConstructor
public class SettlementKeyBuilder {
    private final WeeklyDateRangeCalculator weeklyRangeCalculator;
    private final YearlyDateRangeCalculator yearlyCalc;

    // 일
    public String dailySummaryKey(Long userId, LocalDate start, LocalDate end, Pageable pageable)
    {
        String from = start.toString();  // 항상 yyyy-MM-dd
        String to = end.toString();      // 항상 yyyy-MM-dd

        return String.format("daily:summary:u%d:%s:%s:p%d:s%d", userId, from, to, pageable.getPageNumber(), pageable.getPageSize());
    }

    // 주
    public String weeklySummaryKey(Long userId, LocalDate startDate, Pageable pageable) {

        WeeklyDateRangeCalculator.WeeklyDateRange range =
                WeeklyDateRangeCalculator.getWeeklyDateRange(startDate, startDate);

        LocalDate weekStart = range.firstWeekStart();
        byte week = range.week();

        return String.format(
                "weekly:summary:u%d:y%d:m%d:w%d:p%d:s%d",
                userId,
                weekStart.getYear(),
                weekStart.getMonthValue(),
                week,
                pageable.getPageNumber(),
                pageable.getPageSize()
        );
    }
    // 월
    public String monthlySummaryKey(Long userId, LocalDate start, LocalDate end, Pageable pageable) {
        return String.format("monthly:summary:u%d:%s:%s:p%d:s%d", userId, start, end, pageable.getPageNumber(), pageable.getPageSize());
    }
    // 연
    public String yearlySummaryKey(Long userId, LocalDate start, LocalDate end, Pageable pageable)
    {
        var range = yearlyCalc.calculate(start, end);

        return String.format("yearly:summary:u%d:%s:%s:p%d:s%d", userId, range.fromYear(),
                range.toYearExclusive(), pageable.getPageNumber(), pageable.getPageSize());
    }
}
