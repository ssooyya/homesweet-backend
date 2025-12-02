package com.homesweet.homesweetback.domain.settlement.repository.querydsl;

import com.homesweet.homesweetback.domain.settlement.util.vo.SettlementTotals;

public interface CustomYearlySettlementRepository {
    void upsertYearly(Long userId, Short year, SettlementTotals totals);
}
