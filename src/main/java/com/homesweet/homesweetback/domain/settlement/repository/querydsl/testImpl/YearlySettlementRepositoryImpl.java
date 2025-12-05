package com.homesweet.homesweetback.domain.settlement.repository.querydsl.testImpl;

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
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
@Profile("test")
@Repository
@RequiredArgsConstructor
public class YearlySettlementRepositoryImpl implements CustomYearlySettlementRepository {
    private final JPAQueryFactory jpaQueryFactory;
    private final QMonthlySettlement m = QMonthlySettlement.monthlySettlement;
    private final QYearlySettlement y = QYearlySettlement.yearlySettlement;
    private final EntityManager em;

    @Override
    @Transactional
    public void upsertYearly(Long userId, Short yearValue, SettlementTotals totals) {
        Long count = jpaQueryFactory
                .select(m.count())
                .from(m)
                .where(
                        m.userId.eq(userId),
                        m.year.eq(yearValue)
                )
                .fetchOne();

//        if (count == null || count == 0) {
//            return 0; // monthly 데이터가 없으면 INSERT도 하지 않음
//        }

        // 1) monthly SUM 조회
        Tuple sums = jpaQueryFactory
                .select(
                        m.totalSales.sum().coalesce(BigDecimal.ZERO),
                        m.totalFee.sum().coalesce(BigDecimal.ZERO),
                        m.totalVat.sum().coalesce(BigDecimal.ZERO),
                        m.totalRefund.sum().coalesce(BigDecimal.ZERO),
                        m.totalSettlement.sum().coalesce(BigDecimal.ZERO)
                )
                .from(m)
                .where(
                        m.userId.eq(userId),
                        m.year.eq(yearValue)
                )
                .fetchOne();

//        if (sums == null) {
//            return 0;
//        }

        BigDecimal totalSales =
                sums.get(m.totalSales.sum().coalesce(BigDecimal.ZERO));
        BigDecimal totalFee =
                sums.get(m.totalFee.sum().coalesce(BigDecimal.ZERO));
        BigDecimal totalVat =
                sums.get(m.totalVat.sum().coalesce(BigDecimal.ZERO));
        BigDecimal totalRefund =
                sums.get(m.totalRefund.sum().coalesce(BigDecimal.ZERO));
        BigDecimal totalSettlement =
                sums.get(m.totalSettlement.sum().coalesce(BigDecimal.ZERO));

        // 2) 기존 yearly row 있는지 확인
        YearlySettlement exists = jpaQueryFactory
                .selectFrom(y)
                .where(
                        y.userId.eq(userId),
                        y.year.eq(yearValue)
                )
                .fetchOne();

        // 3) INSERT
        if (exists == null) {
            YearlySettlement newRow = YearlySettlement.builder()
                    .userId(userId)
                    .year(yearValue)
                    .totalSales(totalSales)
                    .totalFee(totalFee)
                    .totalVat(totalVat)
                    .totalRefund(totalRefund)
                    .totalSettlement(totalSettlement)
                    .build();

            em.persist(newRow);
            return;
        }

        // 4) UPDATE
        jpaQueryFactory.update(y)
                .set(y.totalSales, totalSales)
                .set(y.totalFee, totalFee)
                .set(y.totalVat, totalVat)
                .set(y.totalRefund, totalRefund)
                .set(y.totalSettlement, totalSettlement)
                .where(y.yearlyId.eq(exists.getYearlyId()))
                .execute();
        em.flush();
        em.clear();
    }
}
