package com.homesweet.homesweetback.domain.settlement.repository.querydsl.testImpl;

import com.homesweet.homesweetback.domain.settlement.entity.DailySettlement;
import com.homesweet.homesweetback.domain.settlement.entity.QDailySettlement;
import com.homesweet.homesweetback.domain.settlement.repository.querydsl.CustomDailySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.util.vo.SettlementTotals;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Profile("test")
@Repository
@RequiredArgsConstructor
public class DailySettlementRepositoryImpl implements CustomDailySettlementRepository {
    private final JPAQueryFactory jpaQueryFactory;
    private final QDailySettlement qDailySettlement = QDailySettlement.dailySettlement;
    private final EntityManager em;

    //    @Override
//    @Transactional
//    public void upsertDaily(Long userId, LocalDateTime settlementDate, SettlementTotals totals) {
//
//        LocalDateTime normalized = settlementDate
//                .withHour(0)
//                .withMinute(0)
//                .withSecond(0)
//                .withNano(0);
//
//        // 1) 기존 row 존재 여부 확인
//        DailySettlement exists = jpaQueryFactory
//                .selectFrom(qDailySettlement)
//                .where(
//                        qDailySettlement.userId.eq(userId)
//                                .and(qDailySettlement.settlementDate.eq(normalized))
//                )
//                .fetchOne();
//
//        // 2) Insert
//        if (exists == null) {
//            DailySettlement newDaily = DailySettlement.builder()
//                    .userId(userId)
//                    .settlementDate(normalized)
//                    .totalSales(totals.getTotalSales())
//                    .totalFee(totals.getTotalFee())
//                    .totalVat(totals.getTotalVat())
//                    .totalRefund(totals.getTotalRefund())
//                    .totalSettlement(totals.getTotalSettlement())
//                    .build();
//
//            em.persist(newDaily);
//            return;
//        }
//
//        // 3) Update
//        // h2와 mysql의 timestamp의 정밀도 차이때문
//        jpaQueryFactory
//                .update(qDailySettlement)
//                .set(qDailySettlement.totalSales, totals.getTotalSales())
//                .set(qDailySettlement.totalFee, totals.getTotalFee())
//                .set(qDailySettlement.totalVat, totals.getTotalVat())
//                .set(qDailySettlement.totalRefund, totals.getTotalRefund())
//                .set(qDailySettlement.totalSettlement, totals.getTotalSettlement())
//                .where(
//                        qDailySettlement.userId.eq(userId)
//                                .and(qDailySettlement.settlementDate.eq(normalized))
//                )
//                .execute();
//
//    }
    @Override
    @Transactional
    public void upsertDaily(Long userId, LocalDateTime settlementDate, SettlementTotals totals) {
        LocalDateTime normalized = settlementDate.withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime start = normalized;
        LocalDateTime end = normalized.plusDays(1);
        // 1) 기존 row 존재 여부 확인 — 비관적 잠금(PESSIMISTIC_WRITE) 적용
        DailySettlement exists = jpaQueryFactory
                .selectFrom(qDailySettlement)
                .where(
                        qDailySettlement.userId.eq(userId)
                                .and(qDailySettlement.settlementDate.goe(start))
                                .and(qDailySettlement.settlementDate.lt(end))
                )
                // 🚨 핵심 수정: SELECT FOR UPDATE 힌트를 DB에 전달하여 잠금 획득
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetchOne();
        // 2) Insert
        if (exists == null) {
            DailySettlement newDaily = DailySettlement.builder()
                    .userId(userId)
                    .settlementDate(normalized)
                    .totalSales(totals.getTotalSales())
                    .totalFee(totals.getTotalFee())
                    .totalVat(totals.getTotalVat())
                    .totalRefund(totals.getTotalRefund())
                    .totalSettlement(totals.getTotalSettlement())
                    .build();
            em.persist(newDaily);
            return;
        }
        // 3) Update
        jpaQueryFactory
                .update(qDailySettlement)
                .set(qDailySettlement.totalSales, totals.getTotalSales())
                .set(qDailySettlement.totalFee, totals.getTotalFee())
                .set(qDailySettlement.totalVat, totals.getTotalVat())
                .set(qDailySettlement.totalRefund, totals.getTotalRefund())
                .set(qDailySettlement.totalSettlement, totals.getTotalSettlement())
                .where(
                        qDailySettlement.userId.eq(userId)
                                .and(qDailySettlement.settlementDate.goe(start))
                                .and(qDailySettlement.settlementDate.lt(end))
                )
                .execute();
        em.flush(); // db 반영
        em.clear(); // 캐시 초기화(캐시엔티티만 바라봄)
    }
}

