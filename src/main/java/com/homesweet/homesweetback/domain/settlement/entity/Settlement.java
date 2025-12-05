package com.homesweet.homesweetback.domain.settlement.entity;

import com.homesweet.homesweetback.domain.order.entity.Order;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.util.UUID;

@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Settlement {
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTIFY)
//    @Column(name = "settlement_id")
//    private Long settlementId;

    // batch insert를 하기 위해
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "settlement_id", columnDefinition = "BINARY(16)")
    private UUID settlementId;

    // 판매자 번호
    @Column(name = "user_id")
    private Long userId;

    @Setter
    @Column(name = "settlement_status", length = 10)
    private String settlementStatus;

    @Column(name = "sales_amount", precision = 15, scale = 2)
    private BigDecimal salesAmount;

    @Column(precision = 15, scale = 2)
    private BigDecimal fee;

    @Column(precision = 15, scale = 2)
    private BigDecimal vat;

    @Column(name = "refund_amount", precision = 15, scale = 2)
    private BigDecimal refundAmount;

    @Setter
    @Column(name = "settlement_amount", precision = 15, scale = 2)
    private BigDecimal settlementAmount;

    @Column(name = "settlement_date")
    private LocalDateTime settlementDate;

    // 지연로딩 발생, 병목의 원인 -> 연관관계 제거
//    @ManyToOne
//    @JoinColumn(name = "order_id")
//    private Order order;
    @Column(name = "order_id")
    private Long orderId;
}