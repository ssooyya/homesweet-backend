package com.homesweet.homesweetback.domain.settlement.dto.response;

import com.homesweet.homesweetback.domain.settlement.util.calculator.WeeklyDateRangeCalculator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;


// 빈 응답 -> 0
@Component
public class EmptyResponse {
    // 일별
    public DailySettlementResponse createEmptyDaily(LocalDate startDate) {
        return new DailySettlementResponse(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, startDate, "CANCELED", 0.0, 0L
        );
    }
    public Page<DailySettlementResponse> createEmptyDaily(LocalDate startDate, Pageable pageable) {
        DailySettlementResponse res = createEmptyDaily(startDate);
        return new PageImpl<>(List.of(res), pageable, 0);
    }

    // 주별
    public Page<WeeklySettlementResponse> createEmptyWeekly(WeeklyDateRangeCalculator.WeeklyDateRange range, Pageable pageable) {
        WeeklySettlementResponse res = new WeeklySettlementResponse(
                (short) range.firstWeekStart().getYear(),
                (byte) range.firstWeekStart().getMonthValue(),
                range.week(),
                range.firstWeekStart(),
                range.firstWeekStart().plusDays(6),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                0.0, 0L
        );
        return new PageImpl<>(List.of(res), pageable, 0);
    }
    // 월별
    public Page<MonthlySettlementResponse> createEmptyMonthly(YearMonth fromYM, Pageable pageable){
        MonthlySettlementResponse res = new MonthlySettlementResponse(
                (short) fromYM.getYear(),
                (byte) fromYM.getMonthValue(),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                0.0, 0L
        );
        return new  PageImpl<>(List.of(res), pageable, 0);
    }
    // 연별
    public Page<YearlySettlementResponse> createEmptyYearly(short year, Pageable pageable) {
        YearlySettlementResponse res = new YearlySettlementResponse(
                year,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                0L
        );
        return new PageImpl<>(List.of(res), pageable, 0);
    }
}
