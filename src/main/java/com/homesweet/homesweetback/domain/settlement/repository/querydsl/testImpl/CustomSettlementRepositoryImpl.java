package com.homesweet.homesweetback.domain.settlement.repository.querydsl.testImpl;

import com.homesweet.homesweetback.domain.settlement.entity.QSettlement;
import com.homesweet.homesweetback.domain.settlement.repository.SettlementRepository;
import com.homesweet.homesweetback.domain.settlement.repository.querydsl.CustomSettlementRepository;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
@Repository
@RequiredArgsConstructor
public class CustomSettlementRepositoryImpl implements CustomSettlementRepository {
    private final JPAQueryFactory jpaQueryFactory;
    private final QSettlement qSettlement = QSettlement.settlement;
    private final SettlementRepository settlementRepository; // JPA 레포를 여기서 사용

    @Override
    @Transactional
    public int applyRefundAmount(Long orderId, BigDecimal refundAmount) {
        return (int) jpaQueryFactory
                .update(qSettlement)
                // 1) refundAmount = COALESCE(refund_amount, 0) + :refundAmount
                .set(qSettlement.refundAmount, qSettlement.refundAmount.coalesce(BigDecimal.ZERO).add(refundAmount))
                // 2) settlementAmount = (sales + vat - fee - refund)
                .set(qSettlement.settlementAmount, qSettlement.salesAmount.coalesce(BigDecimal.ZERO)
                        .add(qSettlement.vat.coalesce(BigDecimal.ZERO))
                        .subtract(qSettlement.fee.coalesce(BigDecimal.ZERO))
                        .subtract(qSettlement.refundAmount
                                .coalesce(BigDecimal.ZERO)
                                .add(refundAmount)
                        )
                )
                // 3) 상태 변경
                .set(qSettlement.settlementStatus, "CANCELED")
                // 4) where 조건
                .where(qSettlement.order.id.eq(orderId))
                .execute();
    }

}
