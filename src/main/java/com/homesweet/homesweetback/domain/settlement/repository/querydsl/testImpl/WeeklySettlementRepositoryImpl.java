package com.homesweet.homesweetback.domain.settlement.repository.querydsl.testImpl;

import com.homesweet.homesweetback.domain.settlement.entity.QWeeklySettlement;
import com.homesweet.homesweetback.domain.settlement.entity.WeeklySettlement;
import com.homesweet.homesweetback.domain.settlement.repository.querydsl.CustomWeeklySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.util.vo.SettlementTotals;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Profile("test")
@Repository
@RequiredArgsConstructor
public class WeeklySettlementRepositoryImpl implements CustomWeeklySettlementRepository {
    private final JPAQueryFactory jpaQueryFactory;
    private final QWeeklySettlement qWeeklySettlement = QWeeklySettlement.weeklySettlement;
    private final EntityManager em;

    @Override
    @Transactional
    public void upsertWeekly(Long userId, Short year, Byte month,
                             LocalDate weekStartDate, LocalDate weekEndDate,
                             SettlementTotals totals) {
        // 값 변환
        year = (short) weekStartDate.getYear();
        month = (byte) weekStartDate.getMonthValue();
        LocalDate weekEnd = weekStartDate.plusDays(6);

        BigDecimal totalSales = totals.getTotalSales();
        BigDecimal totalFee = totals.getTotalFee();
        BigDecimal totalVat = totals.getTotalVat();
        BigDecimal totalRefund = totals.getTotalRefund();
        BigDecimal totalSettlement = totals.getTotalSettlement();

        // 기존 row 있는지 확인
        WeeklySettlement exists = jpaQueryFactory
                .selectFrom(qWeeklySettlement)
                .where(
                        qWeeklySettlement.userId.eq(userId)
                                .and(qWeeklySettlement.weekStartDate.eq(weekStartDate))
                )
                .fetchOne();

        if (exists == null) {
            // Insert
            WeeklySettlement newWeekly = WeeklySettlement.builder()
                    .userId(userId)
                    .year(year)
                    .month(month)
                    .weekStartDate(weekStartDate)
                    .weekEndDate(weekEnd)
                    .totalSales(totalSales)
                    .totalFee(totalFee)
                    .totalVat(totalVat)
                    .totalRefund(totalRefund)
                    .totalSettlement(totalSettlement)
                    .build();

            em.persist(newWeekly);
            return;
//            return 1;
        }

        // Update
        jpaQueryFactory.update(qWeeklySettlement)
                .set(qWeeklySettlement.totalSales, totalSales)
                .set(qWeeklySettlement.totalFee, totalFee)
                .set(qWeeklySettlement.totalVat, totalVat)
                .set(qWeeklySettlement.totalRefund, totalRefund)
                .set(qWeeklySettlement.totalSettlement, totalSettlement)
                .set(qWeeklySettlement.month, month)
                .set(qWeeklySettlement.weekEndDate, weekEnd)
                .where(qWeeklySettlement.weeklyId.eq(exists.getWeeklyId()))
                .execute();
    }
}
