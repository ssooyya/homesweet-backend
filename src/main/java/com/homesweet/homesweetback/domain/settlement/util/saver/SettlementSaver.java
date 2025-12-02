package com.homesweet.homesweetback.domain.settlement.util.saver;

import com.homesweet.homesweetback.domain.settlement.repository.DailySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.repository.MonthlySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.repository.WeeklySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.repository.YearlySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.repository.querydsl.CustomDailySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.repository.querydsl.CustomMonthlySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.repository.querydsl.CustomWeeklySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.repository.querydsl.CustomYearlySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.util.vo.SettlementTotals;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementSaver {
    private final CustomDailySettlementRepository customDailySettlementRepository;
    private final CustomWeeklySettlementRepository customWeeklySettlementRepository;
    private final CustomMonthlySettlementRepository customMonthlySettlementRepository;
    private final CustomYearlySettlementRepository customYearlySettlementRepository;

    private final DailySettlementRepository dailySettlementRepository;
    private final WeeklySettlementRepository weeklySettlementRepository;
    private final MonthlySettlementRepository monthlySettlementRepository;
    private final YearlySettlementRepository yearlySettlementRepository;

    private int count = 0;
    // 일별
    public void saveDaily(Long userId, LocalDate date, SettlementTotals totals) {
//        customDailySettlementRepository.upsertDaily(
//                userId,
//                date.atStartOfDay(),
//                totals
//        );
        count++;
        log.info("Daily Upsert count = {}", count);
        customDailySettlementRepository.upsertDaily(
                userId,
                date.atStartOfDay(),
                totals
        );

    }

    // 주별
    public void saveWeekly(Long userId, Short year, Byte month, LocalDate weekStartDate, LocalDate weekEndDate, SettlementTotals totals) {
        if (weekStartDate == null) {
            throw new NullPointerException("weekStartDate cannot be null");
        }
        if (totals == null) {
            throw new NullPointerException("totals cannot be null");
        }
//        customWeeklySettlementRepository.upsertWeekly(
//                userId,
//                weekStartDate,
//                totals
//        );
        Short yearValue = (short) weekStartDate.getYear();
        Byte monthValue = (byte) weekStartDate.getMonthValue();
        weekEndDate = weekStartDate.plusDays(6);
        customWeeklySettlementRepository.upsertWeekly(
                userId,
                yearValue,
                monthValue,
                weekStartDate,
                weekEndDate,
                totals
        );
    }

    // 월별
    public void saveMonthly(Long userId, YearMonth ym, SettlementTotals totals) {
//        customMonthlySettlementRepository.upsertMonthly(
//                userId,
//                (short) ym.getYear(),
//                (byte) ym.getMonthValue(),
//                totals
//        );
        short yearVal = (short) ym.getYear();
        byte monthVal = (byte) ym.getMonthValue();

        customMonthlySettlementRepository.upsertMonthly(
                userId,
                yearVal,
                monthVal,
                totals
        );
    }

    // 연별
    public void saveYearly(Long userId, Short year, SettlementTotals totals) {

//        customYearlySettlementRepository.upsertYearly(
//                userId,
//                year,
//                totals
//        );
        customYearlySettlementRepository.upsertYearly(
                userId,
                year,
                totals
        );
    }
}
