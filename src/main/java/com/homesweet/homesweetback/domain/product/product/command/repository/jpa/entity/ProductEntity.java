package com.homesweet.homesweetback.domain.product.product.command.repository.jpa.entity;

import com.homesweet.homesweetback.domain.auth.entity.User;
import com.homesweet.homesweetback.domain.product.category.repository.jpa.entity.ProductCategoryEntity;
import com.homesweet.homesweetback.domain.product.product.command.domain.Product;
import com.homesweet.homesweetback.domain.product.product.command.domain.ProductStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 상품 엔티티
 *
 * @author junnukim1007gmail.com
 * @date 25. 10. 20.
 */

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@Builder(toBuilder = true)
@BatchSize(size = 1000)
public class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private ProductCategoryEntity category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User seller;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "image_url", nullable = false, length = 1000)
    private String imageUrl;

    @Column(nullable = false, length = 100)
    private String brand;

    @Column(name = "base_price", nullable = false)
    @Builder.Default
    private Integer basePrice = 0;

    @Column(name = "discount_rate", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal discountRate = BigDecimal.ZERO;

    @Column(length = 255)
    private String description;

    @Column(name = "shipping_price", nullable = false)
    @Builder.Default
    private Integer shippingPrice = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ProductStatus status;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProductDetailImageEntity> detailImages = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProductOptionGroupEntity> optionGroups = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SkuEntity> skus = new ArrayList<>();

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

//    @Builder
    public ProductEntity(Long id,ProductCategoryEntity category, User seller, String name, String imageUrl, String brand, Integer basePrice, BigDecimal discountRate, String description, Integer shippingPrice, ProductStatus status) {
        this.id = id;
        this.category = category;
        this.seller = seller;
        this.name = name;
        this.imageUrl = imageUrl;
        this.brand = brand;
        this.basePrice = basePrice;
        this.discountRate = discountRate;
        this.description = description;
        this.shippingPrice = shippingPrice;
        this.status = status;
    }

    public void addDetailImage(ProductDetailImageEntity image) {
        this.detailImages.add(image);
        image.setProduct(this);
    }

    public void addOption(ProductOptionGroupEntity optionGroup) {
        this.optionGroups.add(optionGroup);
        optionGroup.setProduct(this);
    }

    public void addSku(SkuEntity sku) {
        this.skus.add(sku);
        sku.setProduct(this);
    }

    /**
     * 상품 기본 정보 업데이트
     */
    public void updateBasicInfo(Product product) {
        this.name = product.getName();
        this.brand = product.getBrand();
        this.basePrice = product.getBasePrice();
        this.discountRate = product.getDiscountRate();
        this.description = product.getDescription();
        this.shippingPrice = product.getShippingPrice();
    }

    /**
     * 상품 상태 업데이트
     */
    public void updateStatus(ProductStatus newStatus) {
        this.status = newStatus;
    }

    /**
     * 대표 이미지 업데이트
     */
    public void updateMainImage(String newImageUrl) {
        this.imageUrl = newImageUrl;
    }

    /**
     * 상세 이미지 삭제
     */
    public void removeDetailImagesByUrls(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return;
        }
        this.detailImages.removeIf(image -> imageUrls.contains(image.getImageUrl()));
    }
}
