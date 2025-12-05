//package com.homesweet.homesweetback.domain.settlement.batch.step.create;
//
//import com.homesweet.homesweetback.common.exception.BusinessException;
//import com.homesweet.homesweetback.common.exception.ErrorCode;
//import com.homesweet.homesweetback.domain.auth.entity.User;
//import com.homesweet.homesweetback.domain.grade.entity.Grade;
//import com.homesweet.homesweetback.domain.grade.service.GradeService;
//import com.homesweet.homesweetback.domain.order.entity.DeliveryStatus;
//import com.homesweet.homesweetback.domain.order.entity.Order;
//import com.homesweet.homesweetback.domain.order.entity.OrderItem;
//import com.homesweet.homesweetback.domain.order.entity.OrderStatus;
//import com.homesweet.homesweetback.domain.product.category.repository.jpa.entity.ProductCategoryEntity;
//import com.homesweet.homesweetback.domain.product.product.repository.jpa.entity.ProductEntity;
//import com.homesweet.homesweetback.domain.product.product.repository.jpa.entity.SkuEntity;
//import com.homesweet.homesweetback.domain.settlement.data.HelperData;
//import com.homesweet.homesweetback.domain.settlement.entity.Settlement;
//import com.homesweet.homesweetback.domain.settlement.repository.SettlementRepository;
//import com.homesweet.homesweetback.domain.settlement.util.ExtractedSeller;
//import com.homesweet.homesweetback.domain.settlement.util.calculator.SettlementCalculator;
//import com.homesweet.homesweetback.domain.settlement.validation.SettlementValidator;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Nested;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.Spy;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.test.util.ReflectionTestUtils;
//
//import java.math.BigDecimal;
//import java.time.LocalDate;
//import java.util.List;
//import java.util.Optional;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.assertj.core.api.Assertions.assertThatThrownBy;
//import static org.mockito.BDDMockito.given;
//
//@ExtendWith(MockitoExtension.class)
//@DisplayName("정산 생성 processor 단위 테스트")
//class SettlementCreateProcessorTest {
//    @Mock
//    private SettlementRepository settlementRepository;
//
//    @InjectMocks
//    private SettlementCreateProcessor processor;
//
//
//    @Mock
//    private SettlementValidator settlementValidator;
//
//    @Mock
//    private SettlementCalculator settlementCalculator;
//    @Mock
//    private ExtractedSeller extractedSeller;
//
//    @BeforeEach
//    void init() {
//        settlementValidator = new SettlementValidator(settlementRepository);
//        processor = new SettlementCreateProcessor(
//                extractedSeller,
//                settlementCalculator
////                settlementValidator      // ★ 실제 Validator 주입
//        );
//    }
//
//    @Test
//    void process() {
//        // given
//        Grade grade = HelperData.getGrade();
//        User seller = HelperData.getSeller(grade);
//        User buyer = HelperData.getUser();
//
//        ProductCategoryEntity category = HelperData.getCategory();
//        ProductEntity product = HelperData.getProduct(seller, category);
//        SkuEntity sku = HelperData.getSkuEntity(product);
//        OrderItem orderItem = HelperData.getOrderItem(sku);
//
//        Order order = HelperData.getOrder(buyer);
//        ReflectionTestUtils.setField(order, "orderItems", List.of(orderItem));
//        ReflectionTestUtils.setField(order, "deliveryStatus", DeliveryStatus.DELIVERED);
//        ReflectionTestUtils.setField(order, "orderStatus", OrderStatus.COMPLETED);
//
//        // 기존 정산 데이터 준비 (취소 대상)
//        Settlement existing = HelperData.getSettlementWithDate(LocalDate.now());
//
//        // Mock: 기존 정산 존재
//        given(extractedSeller.extractSeller(order)).willReturn(seller);
//
//        SettlementCalculator.Result calcResult =
//                new SettlementCalculator.Result(
//                        BigDecimal.valueOf(7500),     // fee
//                        BigDecimal.ZERO,              // refundAmount
//                        BigDecimal.valueOf(15000),    // vat
//                        BigDecimal.valueOf(150000),   // totalAmount
//                        BigDecimal.valueOf(127500)    // settlementAmount
//                );
//
//
//        given(settlementCalculator.getResult(order, seller))
//                .willReturn(calcResult);
//
//        // when
//        Settlement result = processor.process(order);
//
//        // then
//        assertThat(result.getSalesAmount()).isEqualTo(BigDecimal.valueOf(150000));
//        assertThat(result.getFee()).isEqualTo(BigDecimal.valueOf(7500));
//        assertThat(result.getVat()).isEqualTo(BigDecimal.valueOf(15000));
//        assertThat(result.getRefundAmount()).isZero();
//        assertThat(result.getSettlementAmount()).isEqualTo(BigDecimal.valueOf(127500));
//    }
//
//    @Nested
//    @DisplayName("실패 케이스")
//    class fail {
//        @Test
//        @DisplayName("판매자 추출이 null이면 BusinessException 발생")
//        void process_fail_nullSeller() {
//            // given
//            Order order = HelperData.getOrder(HelperData.getUser());
//
//            given(extractedSeller.extractSeller(order)).willReturn(null);
//
//            // when & then
//            assertThatThrownBy(() -> processor.process(order))
//                    .isInstanceOf(BusinessException.class)
//                    .hasMessage(ErrorCode.SELLER_NOT_FOUND.getMessage());
//        }
//
//        @Test
//        @DisplayName("SettlementCalculator 가 null 반환하면 NPE 발생")
//        void process_fail_nullResult() {
//            // given
//            Order order = HelperData.getOrder(HelperData.getUser());
//            User seller = HelperData.getSeller(HelperData.getGrade());
//
//            given(extractedSeller.extractSeller(order)).willReturn(seller);
//            given(settlementCalculator.getResult(order, seller)).willReturn(null);
//
//            // when & then
//            assertThatThrownBy(() -> processor.process(order))
//                    .isInstanceOf(NullPointerException.class);
//        }
//
//        @Test
//        @DisplayName("판매자 ID가 null이면 BusinessException 발생")
//        void process_fail_sellerIdNull() {
//            // given
//            User seller = HelperData.getSeller(HelperData.getGrade());
//            ReflectionTestUtils.setField(seller, "id", null);
//            Order order = HelperData.getOrder(HelperData.getUser());
//
//            given(extractedSeller.extractSeller(order)).willReturn(seller);
//
//            // when & then
//            assertThatThrownBy(() -> processor.process(order))
//                    .isInstanceOf(BusinessException.class)
//                    .hasMessage(ErrorCode.SELLER_NOT_FOUND.getMessage());
//        }
//
//        @Test
//        @DisplayName("계산 결과 값에 null이 포함되면 NPE 발생")
//        void process_fail_nullValuesInResult() {
//            // given
//            User seller = HelperData.getSeller(HelperData.getGrade());
//            Order order = HelperData.getOrder(HelperData.getUser());
//
//            given(extractedSeller.extractSeller(order)).willReturn(seller);
//
//            SettlementCalculator.Result r =
//                    new SettlementCalculator.Result(null, null, null, null, null);
//
//            given(settlementCalculator.getResult(order, seller)).willReturn(r);
//
//            // when & then
//            assertThatThrownBy(() -> processor.process(order))
//                    .isInstanceOf(NullPointerException.class)
//                    .hasMessage("정산 계산 결과에 NULL이 포함되어있습니다.");
//        }
//    }
//}