package com.homesweet.homesweetback.domain.settlement.repository.querydsl.testImpl;

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
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;

@Profile("test")
@Repository
@RequiredArgsConstructor
public class MonthlySettlementRepositoryImpl implements CustomMonthlySettlementRepository {
    private final JPAQueryFactory jpaQueryFactory;
    private final QWeeklySettlement w = QWeeklySettlement.weeklySettlement;
    private final QMonthlySettlement m = QMonthlySettlement.monthlySettlement;
    private final EntityManager em;

    @Override
    @Transactional
    public void upsertMonthly(Long userId, Short year, Byte month, SettlementTotals totals) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(year, "year must not be null");
        Objects.requireNonNull(month, "month must not be null");
        Objects.requireNonNull(totals, "totals must not be null");

//        Long count = jpaQueryFactory
//                .select(w.count())
//                .from(w)
//                .where(
//                        w.userId.eq(userId),
//                        w.year.eq(year),
//                        w.month.eq(month)
//                )
//                .fetchOne();

        // ✔ weekly 데이터 없음 → 월 집계할 데이터 없음
//        if (count == null || count == 0) {
//            return 0;
//        }

        // 1) weekly SUM 조회
        Tuple sums = jpaQueryFactory
                .select(
                        w.totalSales.sum().coalesce(BigDecimal.ZERO),
                        w.totalFee.sum().coalesce(BigDecimal.ZERO),
                        w.totalVat.sum().coalesce(BigDecimal.ZERO),
                        w.totalRefund.sum().coalesce(BigDecimal.ZERO),
                        w.totalSettlement.sum().coalesce(BigDecimal.ZERO)
                )
                .from(w)
                .where(
                        w.userId.eq(userId),
                        w.year.eq(year),
                        w.month.eq(month)
                )
                .fetchOne();

//        if (sums == null) {
//            return 0;
//        }

        BigDecimal totalSales = sums.get(0, BigDecimal.class);
        BigDecimal totalFee = sums.get(1, BigDecimal.class);
        BigDecimal totalVat = sums.get(2, BigDecimal.class);
        BigDecimal totalRefund = sums.get(3, BigDecimal.class);
        BigDecimal totalSettlement = sums.get(4, BigDecimal.class);

        // 2) 기존 월 데이터 조회
        MonthlySettlement exists = jpaQueryFactory
                .selectFrom(m)
                .where(
                        m.userId.eq(userId),
                        m.year.eq(year),
                        m.month.eq(month)
                )
                .fetchOne();

        // 3) INSERT
        if (exists == null) {
            MonthlySettlement newRow = MonthlySettlement.builder()
                    .userId(userId)
                    .year(year)
                    .month(month)
                    .totalSales(totalSales)
                    .totalFee(totalFee)
                    .totalVat(totalVat)
                    .totalRefund(totalRefund)
                    .totalSettlement(totalSettlement)
                    .build();

            em.persist(newRow);
            em.flush();
            em.clear();
            return;
//            return 1;
        }

        // 4) UPDATE
        jpaQueryFactory.update(m)
                .set(m.totalSales, totalSales)
                .set(m.totalFee, totalFee)
                .set(m.totalVat, totalVat)
                .set(m.totalRefund, totalRefund)
                .set(m.totalSettlement, totalSettlement)
                .where(m.monthlyId.eq(exists.getMonthlyId()))
                .execute();

        em.flush();
        em.clear();


//        return result;
    }
}