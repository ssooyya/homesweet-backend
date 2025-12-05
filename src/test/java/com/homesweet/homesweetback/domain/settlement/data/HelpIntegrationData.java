package com.homesweet.homesweetback.domain.settlement.data;

import com.homesweet.homesweetback.domain.auth.entity.OAuth2Provider;
import com.homesweet.homesweetback.domain.auth.entity.User;
import com.homesweet.homesweetback.domain.auth.entity.UserRole;
import com.homesweet.homesweetback.domain.auth.repository.UserRepository;
import com.homesweet.homesweetback.domain.grade.entity.Grade;
import com.homesweet.homesweetback.domain.order.entity.*;
import com.homesweet.homesweetback.domain.order.repository.OrderRepository;
import com.homesweet.homesweetback.domain.order.repository.PaymentRepository;
import com.homesweet.homesweetback.domain.product.category.repository.jpa.ProductCategoryJPARepository;
import com.homesweet.homesweetback.domain.product.category.repository.jpa.entity.ProductCategoryEntity;
import com.homesweet.homesweetback.domain.product.product.command.domain.ProductStatus;
import com.homesweet.homesweetback.domain.product.product.command.repository.jpa.ProductJPARepository;
import com.homesweet.homesweetback.domain.product.product.command.repository.jpa.SkuJPARepository;
import com.homesweet.homesweetback.domain.product.product.command.repository.jpa.entity.ProductEntity;
import com.homesweet.homesweetback.domain.product.product.command.repository.jpa.entity.SkuEntity;
import com.homesweet.homesweetback.domain.settlement.entity.MonthlySettlement;
import com.homesweet.homesweetback.domain.settlement.entity.Settlement;
import com.homesweet.homesweetback.domain.settlement.entity.WeeklySettlement;
import com.homesweet.homesweetback.domain.settlement.repository.SettlementRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

@Component
public class HelpIntegrationData {
    private final UserRepository userRepository;
    private final ProductCategoryJPARepository categoryRepository;
    private final ProductJPARepository productRepository;
    private final SkuJPARepository skuRepository;
    private final OrderRepository orderRepository;
    private final ProductJPARepository productJPARepository;
    private final ProductCategoryJPARepository productCategoryJPARepository;
    private final SettlementRepository settlementRepository;
    private final PaymentRepository paymentRepository;

    @Autowired
    public HelpIntegrationData(
            UserRepository userRepository,
            ProductCategoryJPARepository categoryRepository,
            ProductJPARepository productRepository,
            SkuJPARepository skuRepository,
            OrderRepository orderRepository,
            ProductJPARepository productJPARepository,
            ProductCategoryJPARepository productCategoryJPARepository,
            SettlementRepository settlementRepository,
            PaymentRepository paymentRepository) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.skuRepository = skuRepository;
        this.orderRepository = orderRepository;
        this.productJPARepository = productJPARepository;
        this.productCategoryJPARepository = productCategoryJPARepository;
        this.settlementRepository = settlementRepository;
        this.paymentRepository = paymentRepository;
    }

    /* -----------------------------
       ✔ SELLER 생성
       ----------------------------- */
    public User createSeller(Grade grade) {
        return userRepository.save(
                User.builder()
                        .name("jjanggu")
                        .role(UserRole.SELLER)
                        .grade(grade)
                        .address("서울시 강남구 짱구네")
                        .provider(OAuth2Provider.GOOGLE)
                        .email("seller@test.com")
                        .build()
        );
    }

    /* -----------------------------
       ✔ BUYER 생성
       ----------------------------- */
    public User createBuyer() {
        return userRepository.save(
                User.builder()
                        .name("cheolsoo")
                        .role(UserRole.USER)
                        .provider(OAuth2Provider.GOOGLE)
                        .email("buyer@test.com")
                        .build()
        );
    }

    /* -----------------------------
       ✔ CATEGORY 생성 (JPA Entity)
       ----------------------------- */
    public ProductCategoryEntity createCategory(String name) {
        return productCategoryJPARepository.save(
                ProductCategoryEntity.builder()
                        .name(name)
                        .depth(0)
                        .parentId(null)
                        .build()
        );
    }

    /* -----------------------------
       ✔ PRODUCT + SKU 생성 (JPA Entity)
       ----------------------------- */
    public SkuEntity createSku(User seller, String productName, int basePrice) {

        ProductCategoryEntity category = createCategory("가구");

        ProductEntity product = productJPARepository.save(
                ProductEntity.builder()
                        .seller(seller)
                        .category(category)
                        .name(productName)
                        .imageUrl("https://hsweet-bucket-1007.s3.ap-northeast-2.amazonaws.com/product/main/391c65f7-d465-4373-8caf-60075264f57d_main-image.jpg")
                        .brand("TEST")
                        .basePrice(basePrice)
                        .discountRate(BigDecimal.ZERO)
                        .shippingPrice(3000)
                        .status(ProductStatus.ON_SALE)
                        .build()
        );

        return skuRepository.save(
                SkuEntity.builder()
                        .product(product)
                        .priceAdjustment(0)
                        .stockQuantity(100L)
                        .build()
        );
    }

    /* -----------------------------
       ✔ ORDER + ORDER ITEM 생성 (Cascade)
       ----------------------------- */
    public Order createOrder(User buyer, SkuEntity sku, long amount, LocalDateTime orderedAt) {

        Order order = Order.builder()
                .user(buyer)
                .orderStatus(OrderStatus.COMPLETED)
                .deliveryStatus(DeliveryStatus.DELIVERED)
                .totalAmount(amount)
                .orderedAt(orderedAt)
                .orderNumber("ORD-" + UUID.randomUUID().toString().replace("-", "").substring(0,32))
                .build();

        OrderItem item = OrderItem.builder()
                .order(order)
                .sku(sku)
                .price(amount)
                .quantity(1L)
                .build();

        order.addOrderItem(item);

        Order savedOrder = orderRepository.save(order);

        // 👇👇👇 [추가] 결제 정보(Payment)도 같이 만들어줘야 정산이 됩니다! 👇👇👇
        Payment payment = Payment.builder()
                .order(savedOrder)
                .pgTransactionId("pg_key_" + savedOrder.getOrderNumber())
                .amount(amount)
                .method("CARD")
                .paymentStatus("DONE")
                .paidAt(orderedAt) // 주문 시간과 동일하게 설정
                .build();
        paymentRepository.save(payment);

        return savedOrder;
    }

    /* -------settlement------- */
    public Settlement getSettlementData(Order order, User seller) {
        User user = order.getUser();

        System.err.println(">> [DEBUG] Order 날짜: " + order.getOrderedAt());

        Settlement saved = settlementRepository.save(
                Settlement.builder()
                        .orderId(1L)
                        .order(order)
                        .userId(seller.getId()) // 👈 [수정 1] User 정보 명시적 주입!
                        .settlementStatus("COMPLETED")
//                        .salesAmount(BigDecimal.valueOf(35000)) // 원래 코드 - 안채호
                        .salesAmount(BigDecimal.valueOf(order.getTotalAmount())) // 새로운 코드 - 안채호
//                        .fee(BigDecimal.valueOf(8750)) // 원본이라능 - 안
                        .fee(BigDecimal.valueOf(order.getTotalAmount() * 0.25)) // 새로운 코드 - 채
//                        .vat(BigDecimal.valueOf(3500)) // 원본. - ㅇ
                        .vat(BigDecimal.valueOf(0)) // 뉴 코드 - 에라토네스의 채
                        .refundAmount(BigDecimal.ZERO)
//                        .settlementAmount(BigDecimal.valueOf(29750)) // 원 & 본 - ㅇㅊㅎ
                        .settlementAmount(BigDecimal.valueOf(order.getTotalAmount() * 0.75)) // 새롭다..! - ㄴㅇㄱ

                        // 날짜 강제 고정 (테스트용)
                        // order.getOrderedAt()이 null이거나 현재 시간일 수 있으니,
                        // 확실하게 2025-11-10으로 박아버리는 게 속 편합니다.
                        .settlementDate(LocalDateTime.of(2025, 11, 10, 12, 0, 0))
                        .build()
        );

        System.err.println(">>> 저장된 정산 날짜: " + saved.getSettlementDate());
        System.err.println(">>> 저장된 유저 ID: " + (saved.getUserId() != null ? saved.getUserId() : "null"));

        return saved;
    }

    // ----------------------------------
    // Weekly Settlement Builder
    // ----------------------------------
    public WeeklySettlement weekly(
            Long userId,
            short year,
            byte month,
            int weekOffset, // 0 ⇒ 첫째 주, 1 ⇒ 둘째 주
            BigDecimal totalSales,
            BigDecimal totalFee,
            BigDecimal totalVat,
            BigDecimal totalRefund,
            BigDecimal totalSettlement
    ) {
        LocalDate firstMonday = LocalDate.of(year, month, 1)
                .with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY));

        LocalDate weekStart = firstMonday.plusWeeks(weekOffset);

        return WeeklySettlement.builder()
                .userId(userId)
                .year(year)
                .month(month)
                .weekStartDate(weekStart)
                .weekEndDate(weekStart.plusDays(6))
                .totalSales(totalSales)
                .totalFee(totalFee)
                .totalVat(totalVat)
                .totalRefund(totalRefund)
                .totalSettlement(totalSettlement)
                .build();
    }

    public MonthlySettlement monthly(Long userId,
                                      short year,
                                      byte month,
                                      BigDecimal sales,
                                      BigDecimal fee,
                                      BigDecimal vat,
                                      BigDecimal refund,
                                      BigDecimal settlement) {

        return MonthlySettlement.builder()
                .userId(userId)
                .year(year)
                .month(month)
                .totalSales(sales)
                .totalFee(fee)
                .totalVat(vat)
                .totalRefund(refund)
                .totalSettlement(settlement)
                .build();
    }
}
