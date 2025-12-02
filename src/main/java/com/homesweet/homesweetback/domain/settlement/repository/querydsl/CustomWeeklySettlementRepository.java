package com.homesweet.homesweetback.domain.settlement.repository.querydsl;

import com.homesweet.homesweetback.domain.settlement.util.vo.SettlementTotals;

import java.time.LocalDate;

public interface CustomWeeklySettlementRepository {
    void upsertWeekly(Long userId, Short year, Byte month,
                      LocalDate weekStartDate, LocalDate weekEndDate,
                      SettlementTotals totals);
}
