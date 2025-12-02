package com.homesweet.homesweetback.domain.order.entity;

import com.homesweet.homesweetback.common.exception.PaymentMismatchException;
import com.homesweet.homesweetback.domain.auth.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder.Default;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long id; //TODO: 총 몇개까지 저장될까요? TSID

    // (N:1) 한 명의 사용자(User)는 여러 주문(Order)을 생성 가능
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // (1:N) 한 주문은 여러 개의 SKU(상품 옵션)을 포함 */
    @Builder.Default
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

//    주문 상태 (결제, 취소 등)
    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false, length = 20)
    private OrderStatus orderStatus;

    // 배송 상태 (배송 준비, 배송 중, 배송 완료 등)
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    private DeliveryStatus deliveryStatus;

    // 총 결제 금액
    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    //주문 일시
    @CreatedDate
    @Column(name = "ordered_at", nullable = false, updatable = false)
    private LocalDateTime orderedAt;

    @Column(name = "order_number", nullable = false, unique = true, length = 36)
    private String orderNumber;

    // 마지막 수정 시각
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 정산 여부 확인
    @Column(name = "settlement_processed", nullable = false)
    private boolean settlementProcessed = false;

    // 연관관계 편의 메서드 (양방향 관계 동기화)
    public void addOrderItem(OrderItem orderItem) {
        this.orderItems.add(orderItem);
        orderItem.setOrder(this);
    }

    public Order(User user, OrderStatus orderStatus, DeliveryStatus deliveryStatus, Long totalAmount, String orderNumber) {
        this.user = user;
        this.orderStatus = orderStatus;
        this.deliveryStatus = deliveryStatus;
        this.totalAmount = totalAmount;
        this.orderNumber = orderNumber;
    }

    public void setOrderStatus(OrderStatus orderStatus) {
        this.orderStatus = orderStatus;
    }

    // 이 주문의 소유자가 맞는지 확인한다능.
    public void setDeliveryStatus(DeliveryStatus deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    // 주문자 검증
    public void validateOwner(Long userId) {
        if (!this.user.getId().equals(userId)) {
            // (OrderService에 있던 예외를 Order 엔티티가 직접 던지도록 함)
            throw new PaymentMismatchException("주문자 정보가 일치하지 않습니다.");
        }
    }

    // 금액 검증
    public void validatePaymentAmount(Long amount) {
        if (!this.totalAmount.equals(amount)) {
            throw new PaymentMismatchException("결제 금액이 일치하지 않습니다.");
        }
    }

    // 상태 검증 (결제 가능 상태인지)
    public void validatePaymentStatus() {
        if (this.orderStatus != OrderStatus.PENDING) {
            throw new PaymentMismatchException("이미 처리된 주문입니다.");
        }
    }

    // 취소 가능 상태 검증
    public void validateCancelStatus() {
        if (this.deliveryStatus == DeliveryStatus.CANCELLED) {
            // (기존 PaymentService에서 던지던 예외와 동일하게 처리)
            throw new RuntimeException("이미 취소된 주문입니다.");
        }
    }

    public boolean isOrderItemEmpty() {
        return this.orderItems == null || this.orderItems.isEmpty();
    }

}