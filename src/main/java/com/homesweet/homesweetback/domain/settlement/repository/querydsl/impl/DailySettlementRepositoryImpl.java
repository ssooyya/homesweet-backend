package com.homesweet.homesweetback.domain.settlement.repository.querydsl.impl;

import com.homesweet.homesweetback.domain.settlement.repository.DailySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.repository.querydsl.CustomDailySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.util.vo.SettlementTotals;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Profile("!test")
@Repository
@RequiredArgsConstructor
public class DailySettlementRepositoryImpl implements CustomDailySettlementRepository {
    private final EntityManager em;

    @Override
    @Transactional
    public void upsertDaily(Long userId, LocalDateTime settlementDate,
                            SettlementTotals totals) {
        em.createNativeQuery("""
                            INSERT INTO daily_settlements (
                                user_id, settlement_date, total_sales, total_fee, total_vat, total_refund, total_settlement
                            )
                            VALUES (:userId, :settlementDate, :totalSales, :totalFee, :totalVat, :totalRefund, :totalSettlement)
                            AS new
                            ON DUPLICATE KEY UPDATE
                                total_sales = new.total_sales,
                                total_fee = new.total_fee,
                                total_vat = new.total_vat,
                                total_refund = new.total_refund,
                                total_settlement = new.total_settlement
                        """)
                .setParameter("userId", userId)
                .setParameter("settlementDate", settlementDate)
                .setParameter("totalSales", totals.getTotalSales())
                .setParameter("totalFee", totals.getTotalFee())
                .setParameter("totalVat", totals.getTotalVat())
                .setParameter("totalRefund", totals.getTotalRefund())
                .setParameter("totalSettlement", totals.getTotalSettlement())
                .executeUpdate();
    }
}