package com.homesweet.homesweetback.domain.product.product.command.repository.jpa.entity;

import com.homesweet.homesweetback.common.exception.StockInsufficientException;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


/**
 * 제품 재고 엔티티
 *
 * @author junnukim1007gmail.com
 * @date 25. 10. 20.
 */
@Entity
@Table(name = "sku")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Builder
@AllArgsConstructor
@BatchSize(size = 1000)
public class SkuEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sku_id")
    private Long id;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    @Column(name = "price_adjustment", nullable = false)
    @Builder.Default
    private Integer priceAdjustment = 0;

    @Column(name = "stock_quantity", nullable = false)
    @Builder.Default
    private Long stockQuantity = 0L;

    @BatchSize(size = 100)
    @Builder.Default
    @OneToMany(mappedBy = "sku", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductSkuOptionEntity> skuOptions = new ArrayList<>();

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

//    @Builder
//    public SkuEntity(Integer priceAdjustment, Long stockQuantity) {
//        this.priceAdjustment = priceAdjustment;
//        this.stockQuantity = stockQuantity;
//    }

    public void addSkuOption(ProductSkuOptionEntity skuOption) {
        this.skuOptions.add(skuOption);
        skuOption.setSku(this);
    }

    public void decreaseStock(Long quantity) {
        if (this.stockQuantity < quantity) {
            throw new StockInsufficientException("재고가 부족합니다. (상품 SKU: " + this.id + ")");        }
        this.stockQuantity -= quantity;
    }

    public void increaseStock(Long quantity) {
        this.stockQuantity += quantity;
    }

    public void updateStock(Long newStockQuantity, Integer newPriceAdjustment) {
        this.stockQuantity = newStockQuantity;
        if (newPriceAdjustment != null) {
            this.priceAdjustment = newPriceAdjustment;
        }
    }

    // Redis 동기화를 위한 재고 업데이트 메서드
    public void updateStock(Long newStock) {
        if (newStock < 0) {
            // 로그를 남기거나 0으로 보정하는 로직을 넣을 수 있음
            this.stockQuantity = 0L;
        } else {
            this.stockQuantity = newStock;
        }
    }

    //단가 * 수량 * 상품별(주문하나에 여러 상품이 있으닌깐)
    //단가 * 수량 * 상품별 * 크리스마스 부각세
    public long getFinalPrice() {
        ProductEntity product = this.getProduct();
        if (product == null) {
            throw new IllegalStateException("SKU(id=" + this.getId() + ")에 연결된 Product가 없습니다.");
        }

        // 1. 기본가(basePrice)에만 할인을 먼저 적용
        BigDecimal basePriceBD = BigDecimal.valueOf(product.getBasePrice());
        BigDecimal HUNDRED = new BigDecimal("100");
        BigDecimal rate = product.getDiscountRate().divide(HUNDRED, 2, java.math.RoundingMode.HALF_UP);
        BigDecimal discountAmount = basePriceBD.multiply(rate);
        BigDecimal discountedBasePrice = basePriceBD.subtract(discountAmount);

        // 2. 할인된 기본가에 옵션가(adjustment)를 더함
        // (priceAdjustment가 null일 경우 0으로 처리)
        Integer adjustment = (this.getPriceAdjustment() != null) ? this.getPriceAdjustment() : 0;
        BigDecimal finalPrice = discountedBasePrice.add(BigDecimal.valueOf(adjustment));

        return finalPrice.setScale(0, java.math.RoundingMode.FLOOR).longValue();
    }

    /**
     * 주문 수량에 따른 총 가격을 계산합니다.
     * (단가 * 수량)
     */
    public long calculateTotalPrice(long quantity) {
        // 1. 단가 계산 (기존 로직 재사용)
        long unitPrice = this.getFinalPrice();

        // 2. 총액 계산
        return unitPrice * quantity;
    }
}
