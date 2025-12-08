package com.homesweet.homesweetback.domain.settlement.mapper;

import com.homesweet.homesweetback.domain.settlement.dto.response.*;
import com.homesweet.homesweetback.domain.settlement.entity.DailySettlement;
import com.homesweet.homesweetback.domain.settlement.entity.MonthlySettlement;
import com.homesweet.homesweetback.domain.settlement.entity.WeeklySettlement;
import com.homesweet.homesweetback.domain.settlement.entity.YearlySettlement;
import com.homesweet.homesweetback.domain.settlement.util.calculator.MonthlyGrowthCalculator;
import com.homesweet.homesweetback.domain.settlement.util.calculator.SettlementCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

// 기간별 응답 매핑
@Component
@RequiredArgsConstructor
public class SettlementMapper {
    // 일별 응답 매핑
    public DailySettlementResponse toDailySettlementResponse(
            DailySettlement d,
            SettlementStatsDto stats
    ) {
        LocalDate date = d.getSettlementDate().toLocalDate();

        long total = stats.totalCount();
        long completed = stats.completedCount();
        double rate = (total == 0) ? 0.0 : Math.round(((double) completed * 100 / total) * 10) / 10.0;

        String status = (completed == total) ? "COMPLETED" : "PENDING";

        return new DailySettlementResponse(
                d.getTotalSales(),
                d.getTotalFee(),
                d.getTotalVat(),
                d.getTotalRefund(),
                d.getTotalSettlement(),
                date,
                status,
                rate,
                total
        );
    }

    public List<DailySettlementResponse> toDailySettlementResponseList(
            List<DailySettlement> settlements,
            SettlementStatsDto stats
    ) {
        List<DailySettlementResponse> list = new ArrayList<>(settlements.size());
        for (DailySettlement s : settlements) {
            list.add(toDailySettlementResponse(s, stats));
        }
        return list;
    }


    // 주별 매핑
    public List<WeeklySettlementResponse> toWeeklySettlementResponse(
            List<WeeklySettlement> list,
            SettlementStatsDto stats,
            byte week
    ) {
        long total = stats.totalCount();
        long completed = stats.completedCount();
        double rate = (total == 0) ? 0.0 : Math.round(((double) completed * 100 / total) * 10) / 10.0;

        return list.stream()
                .map(w -> new WeeklySettlementResponse(
                        w.getYear(),
                        w.getMonth(),
                        (byte) WeekFields.ISO.weekOfMonth().getFrom(w.getWeekStartDate()),
                        w.getWeekStartDate(),
                        w.getWeekEndDate(),
                        w.getTotalSales(),
                        w.getTotalFee(),
                        w.getTotalVat(),
                        w.getTotalRefund(),
                        w.getTotalSettlement(),
                        rate,
                        total
                ))
                .toList();
    }

    private final MonthlyGrowthCalculator monthlyGrowthCalculator;

    // 월별 리스트 매핑
    public List<MonthlySettlementResponse> toMonthlyResponses(
            List<MonthlySettlement> list,
            long totalCount
    ) {
        List<MonthlySettlementResponse> responses = new ArrayList<>();
        BigDecimal previous = null;

        for (MonthlySettlement m : list) {
            BigDecimal current = m.getTotalSales();
            double growth = monthlyGrowthCalculator.growthCalculate(previous, current);

            responses.add(new MonthlySettlementResponse(
                    m.getYear(),
                    m.getMonth(),
                    m.getTotalSales(),
                    m.getTotalFee(),
                    m.getTotalVat(),
                    m.getTotalRefund(),
                    m.getTotalSettlement(),
                    growth,
                    totalCount
            ));

            previous = current;
        }

        return responses;
    }
    // 연별 리스트 매핑
    public List<YearlySettlementResponse> toYearlyResponses(
            List<YearlySettlement> list,
            long totalCount
    ) {
        return list.stream()
                .map(y -> new YearlySettlementResponse(
                        y.getYear(),
                        y.getTotalSales(),
                        y.getTotalFee(),
                        y.getTotalVat(),
                        y.getTotalRefund(),
                        y.getTotalSettlement(),
                        totalCount
                ))
                .toList();
    }
}