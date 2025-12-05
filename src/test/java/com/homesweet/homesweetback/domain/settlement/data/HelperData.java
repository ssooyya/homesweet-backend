package com.homesweet.homesweetback.domain.settlement.data;

import com.homesweet.homesweetback.domain.auth.entity.OAuth2Provider;
import com.homesweet.homesweetback.domain.auth.entity.User;
import com.homesweet.homesweetback.domain.auth.entity.UserRole;
import com.homesweet.homesweetback.domain.grade.entity.Grade;
import com.homesweet.homesweetback.domain.order.entity.DeliveryStatus;
import com.homesweet.homesweetback.domain.order.entity.Order;
import com.homesweet.homesweetback.domain.order.entity.OrderItem;
import com.homesweet.homesweetback.domain.order.entity.OrderStatus;
import com.homesweet.homesweetback.domain.product.category.repository.jpa.entity.ProductCategoryEntity;
import com.homesweet.homesweetback.domain.product.product.command.repository.jpa.entity.ProductEntity;
import com.homesweet.homesweetback.domain.product.product.command.repository.jpa.entity.SkuEntity;
import com.homesweet.homesweetback.domain.settlement.dto.response.DailySettlementResponse;
import com.homesweet.homesweetback.domain.settlement.entity.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class HelperData {
    public static OrderItem getOrderItem(SkuEntity sku) {
        OrderItem orderItem = OrderItem.builder()
                .sku(sku)
                .quantity(1L)
                .price(150000L)
                .build();
        return orderItem;
    }

    public static ProductCategoryEntity getCategory() {
        ProductCategoryEntity productCategoryEntity = ProductCategoryEntity.builder()
                .id(1L)
                .depth(1)
                .name("원목")
                .parentId(1L)
                .build();
        return productCategoryEntity;
    }

    public static ProductEntity getProduct(User seller, ProductCategoryEntity productCategoryEntity) {
        ProductEntity product = ProductEntity.builder()
                .id(1L)
                .category(productCategoryEntity)
                .seller(seller)
                .name("침대")
                .brand("리바트")
                .basePrice(150000)
                .build();
        return product;
    }

    public static SkuEntity getSkuEntity(ProductEntity productEntity) {
        SkuEntity sku = SkuEntity.builder()
                .id(1L)
                .priceAdjustment(10)
                .stockQuantity(11L)
                .product(productEntity)
                .build();
        return sku;
    }

    public static Order getOrder(User user) {
        Order order = com.homesweet.homesweetback.domain.order.entity.Order.builder()
                .user(user)
                .orderNumber(UUID.randomUUID().toString())
                .orderStatus(OrderStatus.COMPLETED)
                .deliveryStatus(DeliveryStatus.DELIVERED)
                .totalAmount(150000L)
                .orderedAt(LocalDateTime.of(2025, 11, 25, 0, 0))
                .build();
        return order;
    }

    public static User getUser() {
        User user = User.builder()
//                .id(13L)
                .name("chulsoo")
                .phoneNumber("010-1234-1234")
                .address("서울시 강남구 논현로 1")
                .email("chulsoo@gmail.com")
                .provider(OAuth2Provider.GOOGLE)
                .providerId(UUID.randomUUID().toString())
                .role(UserRole.USER)
                .build();
        return user;
    }

    public static User getSeller(Grade grade) {
        User seller = User.builder()
                .id(14L)
                .name("kildong")
                .phoneNumber("010-1111-2345")
                .address("서울시 강남구 역삼로 1")
                .email("kildonghong@gmail.com")
                .role(UserRole.SELLER)
                .grade(grade)
                .build();
        return seller;
    }

    public static Grade getGrade() {
        Grade grade = Grade.builder()
                .gradeId(1)
                .grade("VIP")
                .feeRate(BigDecimal.valueOf(0.5))
                .build();
        return grade;
    }

    public static Settlement getSettlement() {
        Settlement settlement = Settlement.builder()
                .settlementId(UUID.randomUUID())
                .salesAmount(BigDecimal.valueOf(150000))
                .fee(BigDecimal.valueOf(7500))
                .vat(BigDecimal.valueOf(15000))
                .refundAmount(BigDecimal.ZERO)
                .settlementAmount(BigDecimal.valueOf(127500))
                .build();
        return settlement;
    }

    public static DailySettlement getDailySettlement() {
        DailySettlement dailySettlement = DailySettlement.builder()
                .dailyId(1L)
                .totalSales(BigDecimal.valueOf(1500000))
                .totalFee(BigDecimal.valueOf(75000))
                .totalVat(BigDecimal.valueOf(150000))
                .totalRefund(BigDecimal.ZERO)
                .totalSettlement(BigDecimal.valueOf(1575000))
                .settlementDate(LocalDateTime.of(2025, 11, 10, 0, 0))
                .build();
        return dailySettlement;
    }

    public static DailySettlementResponse getDailySettlementResponse() {
        DailySettlementResponse dailySettlementResponse = new DailySettlementResponse(
                BigDecimal.valueOf(1500000),
                BigDecimal.valueOf(75000),
                BigDecimal.valueOf(150000),
                BigDecimal.ZERO,
                BigDecimal.valueOf(1575000),
                LocalDate.of(2025, 11, 10),
                "COMPLETED",
                80.0,
                10L
        );
        return dailySettlementResponse;
    }

    public static DailySettlementResponse emptyDailySettlementResponse(LocalDate date) {
        return new DailySettlementResponse(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                date,
                "CANCELED",
                0.0,
                0L
        );
    }

    // 정산 날짜
    public static Settlement getSettlementWithDate(LocalDate date) {
        return Settlement.builder()
                .settlementId(UUID.randomUUID())
                .salesAmount(BigDecimal.valueOf(150000))
                .fee(BigDecimal.valueOf(7500))
                .vat(BigDecimal.valueOf(15000))
                .refundAmount(BigDecimal.ZERO)
                .settlementAmount(BigDecimal.valueOf(127500))
                .settlementDate(date.atTime(10, 0))
                .build();
    }

    // 일별 정산내역 데이터
    public static DailySettlement getDailySettlementWithDate(LocalDate date) {
        return DailySettlement.builder()
                .dailyId(1L)
                .userId(1L)
                .totalSales(BigDecimal.valueOf(1500000))
                .totalFee(BigDecimal.valueOf(75000))
                .totalVat(BigDecimal.valueOf(150000))
                .totalRefund(BigDecimal.ZERO)
                .totalSettlement(BigDecimal.valueOf(1275000))
                .settlementDate(date.atStartOfDay())
                .build();
    }

    // 주별 데이터
    public static WeeklySettlement getWeeklySettlement() {
        return WeeklySettlement.builder()
                .year((short) 2025)
                .month((byte) 11)
                .weekStartDate(LocalDate.of(2025, 11, 3))
                .weekEndDate(LocalDate.of(2025, 11, 9))
                .totalSales(BigDecimal.valueOf(100000))
                .totalFee(BigDecimal.valueOf(5000))
                .totalVat(BigDecimal.valueOf(10000))
                .totalRefund(BigDecimal.ZERO)
                .totalSettlement(BigDecimal.valueOf(85000))
                .build();
    }

    public static WeeklySettlement getWeeklySettlementWithDate(LocalDate date) {

        LocalDate weekStart = date.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = date.with(DayOfWeek.SUNDAY);

        return WeeklySettlement.builder()
                .year((short) weekStart.getYear())
                .month((byte) weekStart.getMonthValue())
                .weekStartDate(weekStart)
                .weekEndDate(weekEnd)
                .totalSales(BigDecimal.valueOf(100000))
                .totalFee(BigDecimal.valueOf(5000))
                .totalVat(BigDecimal.valueOf(10000))
                .totalRefund(BigDecimal.ZERO)
                .totalSettlement(BigDecimal.valueOf(85000))
                .build();
    }

    public static MonthlySettlement getMonthlySettlementWithYearMonth(int year, int month) {
        return MonthlySettlement.builder()
                .monthlyId(1L)
                .userId(1L)
                .year((short) year)
                .month((byte) month)
                .totalSales(BigDecimal.valueOf(100000))
                .totalFee(BigDecimal.valueOf(5000))
                .totalVat(BigDecimal.valueOf(10000))
                .totalRefund(BigDecimal.ZERO)
                .totalSettlement(BigDecimal.valueOf(85000))
                .build();
    }

    public static MonthlySettlement getMonthlySettlement() {
        return MonthlySettlement.builder()
                .monthlyId(1L)
                .userId(1L)
                .year((short) 2025)
                .month((byte) 11)
                .totalSales(BigDecimal.valueOf(100000))
                .totalFee(BigDecimal.valueOf(5000))
                .totalVat(BigDecimal.valueOf(10000))
                .totalRefund(BigDecimal.ZERO)
                .totalSettlement(BigDecimal.valueOf(85000))
                .build();
    }

    public static YearlySettlement getYearlySettlement() {
        return YearlySettlement.builder()
                .year((short) 2025)
                .totalSales(BigDecimal.valueOf(1500000))
                .totalFee(BigDecimal.valueOf(50000))
                .totalVat(BigDecimal.valueOf(100000))
                .totalRefund(BigDecimal.ZERO)
                .totalSettlement(BigDecimal.valueOf(1350000))
                .build();
    }

    // 정산 생성
    public static Settlement createSettlement(Long uuid, Order order) {
        return Settlement.builder()
                .settlementId(UUID.randomUUID())
                .userId(11L)
                .salesAmount(BigDecimal.valueOf(150000))
                .fee(BigDecimal.valueOf(7500))
                .vat(BigDecimal.valueOf(15000))
                .refundAmount(BigDecimal.ZERO)
                .settlementAmount(BigDecimal.valueOf(127500))
                .settlementDate(order.getOrderedAt())
                .orderId(1L)
                .build();
    }
}