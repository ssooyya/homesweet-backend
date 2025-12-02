package com.homesweet.homesweetback.domain.settlement.repository.querydsl.impl;

import com.homesweet.homesweetback.domain.settlement.entity.QMonthlySettlement;
import com.homesweet.homesweetback.domain.settlement.entity.QYearlySettlement;
import com.homesweet.homesweetback.domain.settlement.entity.YearlySettlement;
import com.homesweet.homesweetback.domain.settlement.repository.querydsl.CustomYearlySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.util.vo.SettlementTotals;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
@Profile("!test")
@Repository
@RequiredArgsConstructor
public class YearlySettlementRepositoryImpl implements CustomYearlySettlementRepository {
    private final JPAQueryFactory jpaQueryFactory;
    private final QMonthlySettlement m = QMonthlySettlement.monthlySettlement;
    private final QYearlySettlement y = QYearlySettlement.yearlySettlement;
    private final EntityManager em;

    @Override
    @Transactional
    public void upsertYearly(Long userId, Short year, SettlementTotals totals) {

    em.createNativeQuery("""
        INSERT INTO yearly_settlements (
            user_id, year_value, total_sales, total_fee, total_vat, total_refund, total_settlement
        )
        VALUES (
            :userId, :yearValue, :totalSales, :totalFee, :totalVat, :totalRefund, :totalSettlement
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
            .setParameter("totalSales", totals.getTotalSales())
            .setParameter("totalFee", totals.getTotalFee())
            .setParameter("totalVat", totals.getTotalVat())
            .setParameter("totalRefund", totals.getTotalRefund())
            .setParameter("totalSettlement", totals.getTotalSettlement())
            .executeUpdate();
    }
}
