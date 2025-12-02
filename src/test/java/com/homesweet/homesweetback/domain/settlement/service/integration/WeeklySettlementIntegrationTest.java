package com.homesweet.homesweetback.domain.settlement.service.integration;

import com.homesweet.homesweetback.common.exception.BusinessException;
import com.homesweet.homesweetback.domain.auth.entity.User;
import com.homesweet.homesweetback.domain.grade.entity.Grade;
import com.homesweet.homesweetback.domain.grade.repository.GradeRepository;
import com.homesweet.homesweetback.domain.order.entity.Order;
import com.homesweet.homesweetback.domain.product.product.command.repository.jpa.entity.SkuEntity;
import com.homesweet.homesweetback.domain.settlement.data.HelpIntegrationData;
import com.homesweet.homesweetback.domain.settlement.dto.response.WeeklySettlementResponse;
import com.homesweet.homesweetback.domain.settlement.repository.SettlementRepository;
import com.homesweet.homesweetback.domain.settlement.service.DailySettlementService;
import com.homesweet.homesweetback.domain.settlement.service.SettlementService;
import com.homesweet.homesweetback.domain.settlement.service.WeeklySettlementService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest()
@ActiveProfiles("test")
@DisplayName("WeeklyService 통합 테스트")
public class WeeklySettlementIntegrationTest {
    @Autowired
    EntityManager em;

    @Test
    void debug_entities() {
        em.getMetamodel().getEntities().forEach(e ->
                System.out.println("ENTITY: " + e.getName())
        );
    }

    @Autowired
    private WeeklySettlementService weeklySettlementService;
    @Autowired
    private HelpIntegrationData helpIntegrationData;

    @Autowired
    private GradeRepository gradeRepository;

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private DailySettlementService dailySettlementService;

    @Autowired
    private SettlementRepository settlementRepository;

    @Nested
    @DisplayName("성공 케이스")
    class Success {

        @Test
        @DisplayName("주간 집계 생성 성공")
        void weeklySettlement_success() {
            Grade grade = gradeRepository.findById(5).orElseThrow();
            User seller = helpIntegrationData.createSeller(grade);
            User buyer = helpIntegrationData.createBuyer();
            SkuEntity sku = helpIntegrationData.createSku(seller, "상품", 10000);

            LocalDateTime d1 = LocalDateTime.of(2025, 11, 10, 10, 0);
            LocalDateTime d2 = LocalDateTime.of(2025, 11, 11, 12, 0);
            LocalDateTime d3 = LocalDateTime.of(2025, 11, 14, 15, 0);

            Order o1 = helpIntegrationData.createOrder(buyer, sku, 10000, d1);
            settlementService.createSettlement(o1);
            settlementRepository.findByOrderId(o1.getId())
                    .ifPresent(s->{ s.setSettlementDate(d1); settlementRepository.save(s); });

            Order o2 = helpIntegrationData.createOrder(buyer, sku, 20000, d2);
            settlementService.createSettlement(o2);
            settlementRepository.findByOrderId(o2.getId())
                    .ifPresent(s->{ s.setSettlementDate(d2); settlementRepository.save(s); });

            Order o3 = helpIntegrationData.createOrder(buyer, sku, 30000, d3);
            settlementService.createSettlement(o3);
            settlementRepository.findByOrderId(o3.getId())
                    .ifPresent(s->{ s.setSettlementDate(d3); settlementRepository.save(s); });

            // 1) daily 집계 먼저 실행
            dailySettlementService.getSettlement(
                    seller.getId(),
                    LocalDateTime.of(2025, 11, 10, 0, 0),
                    LocalDateTime.of(2025, 11, 14, 23, 59)
            );

            // 2) weekly 집계
            weeklySettlementService.getWeeklySettlement(
                    seller.getId(),
                    LocalDate.of(2025, 11, 10),
                    LocalDate.of(2025, 11, 16)
            );

            // 검증
            Pageable pageable = PageRequest.of(0, 10);
            Page<WeeklySettlementResponse> weekly = weeklySettlementService.getWeeklySummary(
                    seller.getId(),
                    LocalDate.of(2025, 11, 10),
                    LocalDate.of(2025, 11, 16),
                    pageable
            );

            WeeklySettlementResponse res = weekly.getContent().get(0);
            assertThat(res.totalSales()).isEqualByComparingTo("60000");

        }
    }
    @Nested
    @DisplayName("실패 케이스")
    class Failure {
        @Test
        @DisplayName("주간 집계 실패 - 정산 데이터 없음")
        void weeklySettlement_fail_noSettlement() {

            Grade grade = gradeRepository.findById(5).orElseThrow();
            User seller = helpIntegrationData.createSeller(grade);

            LocalDate weekStart = LocalDate.of(2025, 11, 10);
            LocalDate weekEnd = LocalDate.of(2025, 11, 16);

            assertThatThrownBy(() ->
                    weeklySettlementService.getWeeklySettlement(
                            seller.getId(),
                            weekStart,
                            weekEnd
                    )
            ).isInstanceOf(BusinessException.class)
                    .hasMessageContaining("조회된 정산 데이터가 없습니다.");
        }
        @Test
        @DisplayName("주간 집계 실패 - 주간 범위에 해당하는 정산 없음")
        void weeklySettlement_fail_wrongWeek() {

            Grade grade = gradeRepository.findById(5).orElseThrow();
            User seller = helpIntegrationData.createSeller(grade);
            User buyer = helpIntegrationData.createBuyer();
            SkuEntity sku = helpIntegrationData.createSku(seller, "상품", 10000);

            // 정산일 = 11월 20일 (셋째 주)
            LocalDateTime settledAt = LocalDateTime.of(2025, 11, 20, 14, 0);

            Order o = helpIntegrationData.createOrder(buyer, sku, 20000, settledAt);
            settlementService.createSettlement(o);

            // 주간 범위 = 11월 10 ~ 16 (둘째 주)
            LocalDate weekStart = LocalDate.of(2025, 11, 10);
            LocalDate weekEnd   = LocalDate.of(2025, 11, 16);

            assertThatThrownBy(() ->
                    weeklySettlementService.getWeeklySettlement(
                            seller.getId(),
                            weekStart,
                            weekEnd
                    )
            ).isInstanceOf(BusinessException.class)
                    .hasMessageContaining("조회된 정산 데이터가 없습니다.");
        }
        @Test
        @DisplayName("주간 집계 실패 - 잘못된 날짜 범위(start >= end)")
        void weeklySettlement_fail_invalidRange() {

            Grade grade = gradeRepository.findById(5).orElseThrow();
            User seller = helpIntegrationData.createSeller(grade);

            LocalDate weekStart = LocalDate.of(2025, 11, 15);
            LocalDate weekEnd   = LocalDate.of(2025, 11, 10); // 역순

            assertThatThrownBy(() ->
                    weeklySettlementService.getWeeklySettlement(
                            seller.getId(),
                            weekStart,
                            weekEnd
                    )
            ).isInstanceOf(BusinessException.class);
        }
    }
}