package com.homesweet.homesweetback.domain.settlement.repository.querydsl.impl;

import com.homesweet.homesweetback.domain.settlement.entity.MonthlySettlement;
import com.homesweet.homesweetback.domain.settlement.entity.QMonthlySettlement;
import com.homesweet.homesweetback.domain.settlement.entity.QWeeklySettlement;
import com.homesweet.homesweetback.domain.settlement.repository.querydsl.CustomMonthlySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.util.vo.SettlementTotals;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;

@Profile("!test")
@Repository
@RequiredArgsConstructor
public class MonthlySettlementRepositoryImpl implements CustomMonthlySettlementRepository {
    private final EntityManager em;

    @Override
    @Transactional
    public void upsertMonthly(Long userId, Short year, Byte month, SettlementTotals totals) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(year, "year must not be null");
        Objects.requireNonNull(month, "month must not be null");
        Objects.requireNonNull(totals, "totals must not be null");

        em.createNativeQuery("""
                        INSERT INTO monthly_settlements (
                            user_id, year_value, month_value, total_sales, total_fee, total_vat, total_refund, total_settlement
                        )
                        VALUES (
                            :userId, :yearValue, :monthValue, :totalSales, :totalFee, :totalVat, :totalRefund, :totalSettlement
                        ) AS new
                        ON DUPLICATE KEY UPDATE
                            total_sales = new.total_sales,
                            total_fee = new.total_fee,
                            total_vat = new.total_vat,
                            total_refund = new.total_refund,
                            total_settlement = new.total_settlement
                        """)
                .setParameter("userId", userId)
                .setParameter("yearValue", year)
                .setParameter("monthValue", month)
                .setParameter("totalSales", totals.getTotalSales())
                .setParameter("totalFee", totals.getTotalFee())
                .setParameter("totalVat", totals.getTotalVat())
                .setParameter("totalRefund", totals.getTotalRefund())
                .setParameter("totalSettlement", totals.getTotalSettlement())
                .executeUpdate();
    }
}