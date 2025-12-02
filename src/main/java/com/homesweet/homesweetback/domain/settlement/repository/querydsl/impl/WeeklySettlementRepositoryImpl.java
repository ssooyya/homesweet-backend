package com.homesweet.homesweetback.domain.settlement.repository.querydsl.impl;

import com.homesweet.homesweetback.domain.settlement.entity.QWeeklySettlement;
import com.homesweet.homesweetback.domain.settlement.entity.WeeklySettlement;
import com.homesweet.homesweetback.domain.settlement.repository.querydsl.CustomWeeklySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.util.vo.SettlementTotals;
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
import java.time.LocalDate;

@Profile("!test")
@Repository
@RequiredArgsConstructor
public class WeeklySettlementRepositoryImpl implements CustomWeeklySettlementRepository {
    private final EntityManager em;

    @Override
    @Transactional
    public void upsertWeekly(Long userId, Short year, Byte month,
                             LocalDate weekStartDate, LocalDate weekEndDate,
                             SettlementTotals totals) {
        // 값 변환
        em.createNativeQuery("""
                        INSERT INTO weekly_settlements (
                            user_id, year_value, month_value, week_start_date,  week_end_date, total_sales, total_fee, total_vat, total_refund, total_settlement
                        )
                        VALUES (
                            :userId, :yearValue, :monthValue, :weekStartDate, :weekEndDate, :totalSales, :totalFee, :totalVat, :totalRefund, :totalSettlement
                        ) AS new
                        ON DUPLICATE KEY UPDATE
                            total_sales = new.total_sales,
                            total_fee = new.total_fee,
                            total_vat = new.total_vat,
                            total_refund = new.total_refund,
                            total_settlement = new.total_settlement,
                            month_value = new.month_value,
                            week_end_date = new.week_end_date
                        """)
                .setParameter("userId", userId)
                .setParameter("yearValue", year)
                .setParameter("monthValue", month)
                .setParameter("weekStartDate", weekStartDate)
                .setParameter("weekEndDate", weekEndDate)
                .setParameter("totalSales", totals.getTotalSales())
                .setParameter("totalFee", totals.getTotalFee())
                .setParameter("totalVat", totals.getTotalVat())
                .setParameter("totalRefund", totals.getTotalRefund())
                .setParameter("totalSettlement", totals.getTotalSettlement())
                .executeUpdate();
    }
}
