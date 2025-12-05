package com.homesweet.homesweetback.domain.settlement.repository.querydsl.impl;

import com.homesweet.homesweetback.domain.auth.entity.QUser;
import com.homesweet.homesweetback.domain.grade.entity.QGrade;
import com.homesweet.homesweetback.domain.order.entity.OrderStatus;
import com.homesweet.homesweetback.domain.order.entity.QOrder;
import com.homesweet.homesweetback.domain.order.entity.QOrderItem;
import com.homesweet.homesweetback.domain.product.product.repository.jpa.entity.QProductEntity;
import com.homesweet.homesweetback.domain.product.product.repository.jpa.entity.QSkuEntity;
import com.homesweet.homesweetback.domain.settlement.dto.response.SettlementCreateDto;
import com.homesweet.homesweetback.domain.settlement.entity.QSettlement;
import com.homesweet.homesweetback.domain.settlement.repository.querydsl.CustomSettlementRepository;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static com.homesweet.homesweetback.domain.order.entity.QOrderItem.orderItem;

@Repository
@RequiredArgsConstructor
public class CustomSettlementRepositoryImpl implements CustomSettlementRepository {
    private final JPAQueryFactory jpaQueryFactory;
    private final QSettlement qSettlement =  QSettlement.settlement;
    private final QOrder order = QOrder.order;
    private final QGrade grade = QGrade.grade1;
//    private final QOrderItem orderItem = QOrderItem.orderItem;
//    private final QProductEntity product = QProductEntity.productEntity;
//    private final QSkuEntity sku = QSkuEntity.skuEntity;

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
                .where(qSettlement.orderId.eq(orderId))
                .execute();
    }
    public List<Long> findUnsettledOrderIds(
            OrderStatus status,
            LocalDateTime cutoff,
            Long lastId,
            int limit
    ) {
        return jpaQueryFactory
                .select(order.id)
                .from(order)
                .where(
                        order.orderStatus.eq(status),
                        order.settlementProcessed.eq(false),
                        order.orderedAt.loe(cutoff),
                        order.id.gt(lastId)
                )
                .orderBy(order.id.asc())
                .limit(limit)
                .fetch();
    }
    public List<SettlementCreateDto> findOrdersByIds(List<Long> ids) {
        QOrder o = QOrder.order;
        QOrderItem oi = QOrderItem.orderItem;
        QSkuEntity sku = QSkuEntity.skuEntity;
        QProductEntity p = QProductEntity.productEntity;
        QUser seller = QUser.user;
        QGrade grade = QGrade.grade1;

        return jpaQueryFactory
                .select(Projections.constructor(
                        SettlementCreateDto.class,
                        o.id,
                        seller.id,
                        o.totalAmount,
                        Expressions.constant(BigDecimal.ZERO),
                        grade.feeRate,
                        Expressions.constant(BigDecimal.valueOf(0.1)),
                        o.orderedAt
                ))
                .from(o)
                .join(o.orderItems, oi)
                .join(oi.sku, sku)
                .join(sku.product, p)
                .join(p.seller, seller)
                .join(seller.grade, grade)
                .where(o.id.in(ids))
                .orderBy(o.id.asc())
                .fetch();
    }


//    @Override
//    public List<SettlementCreateDto> findUnsettledOrdersCursor(
//            OrderStatus orderStatus, LocalDateTime cutoff, Long lastId, int limit
//    ){
//        QOrder o = QOrder.order;
//        QOrderItem oi = QOrderItem.orderItem;
//        QSkuEntity sku = QSkuEntity.skuEntity;
//        QProductEntity p = QProductEntity.productEntity;
//        QUser seller = QUser.user;
//        QGrade grade = QGrade.grade1;
//
//        return jpaQueryFactory
//                .select(Projections.constructor(
//                        SettlementCreateDto.class,
//                        order.id,
//                        seller.id,
//                        order.totalAmount,
//                        Expressions.constant(BigDecimal.ZERO),
//                        seller.grade.feeRate,
//                        Expressions.constant(BigDecimal.valueOf(0.1)),
//                        order.orderedAt
//                ))
//                .from(o)
//                .join(o.orderItems, oi)
//                .join(oi.sku, sku)
//                .join(sku.product, p)
//                .join(p.seller, seller)
//                .join(seller.grade, grade)
//                .where(
//                        o.orderStatus.eq(orderStatus),
//                        o.settlementProcessed.eq(false),
//                        o.orderedAt.loe(cutoff),
//                        o.id.gt(lastId)
//                )
//                .orderBy(o.id.asc())
//                .limit(limit)
//                .fetch();
//    }
}
