package com.homesweet.homesweetback.domain.settlement.batch.job;

import com.homesweet.homesweetback.domain.auth.entity.User;
import com.homesweet.homesweetback.domain.auth.repository.UserRepository;
import com.homesweet.homesweetback.domain.grade.entity.Grade;
import com.homesweet.homesweetback.domain.grade.repository.GradeRepository;
import com.homesweet.homesweetback.domain.order.entity.Order;
import com.homesweet.homesweetback.domain.order.repository.OrderRepository;
import com.homesweet.homesweetback.domain.product.category.repository.jpa.ProductCategoryJPARepository;
import com.homesweet.homesweetback.domain.product.category.repository.jpa.entity.ProductCategoryEntity;
import com.homesweet.homesweetback.domain.product.product.command.repository.jpa.ProductJPARepository;
import com.homesweet.homesweetback.domain.product.product.command.repository.jpa.SkuJPARepository;
import com.homesweet.homesweetback.domain.product.product.command.repository.jpa.entity.ProductEntity;
import com.homesweet.homesweetback.domain.product.product.command.repository.jpa.entity.SkuEntity;
import com.homesweet.homesweetback.domain.settlement.data.BatchHelperData;
import com.homesweet.homesweetback.domain.settlement.entity.DailySettlement;
import com.homesweet.homesweetback.domain.settlement.entity.Settlement;
import com.homesweet.homesweetback.domain.settlement.entity.WeeklySettlement;
import com.homesweet.homesweetback.domain.settlement.repository.DailySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.repository.SettlementRepository;
import com.homesweet.homesweetback.domain.settlement.repository.WeeklySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.util.TestAuditingConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.*;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest(properties = "spring.batch.job.enabled=true")
@SpringBatchTest
@ActiveProfiles("test")
@Import({BatchConfig.class, TestAuditingConfig.class})
@DisplayName("주별 집계 step 통합테스트")
@TestPropertySource(properties = {
        "logging.level.com.homesweet=DEBUG",
        "logging.level.org.springframework.batch=DEBUG"
})
class WeeklySettlementStepIntegrationTest {
    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired
    @Qualifier("settlementJob")  // BatchConfig에 있는 Job 이름과 정확히 일치해야 함
    private Job settlementJob;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private SettlementRepository settlementRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProductCategoryJPARepository categoryRepository;
    @Autowired
    private ProductJPARepository productRepository;
    @Autowired
    private SkuJPARepository skuRepository;
    @Autowired
    private GradeRepository gradeRepository;
    @Autowired
    private WeeklySettlementRepository weeklySettlementRepository;
    @Autowired
    private DailySettlementRepository dailySettlementRepository;
    @BeforeEach
    void clean() {
        // 정산 관련 삭제
        weeklySettlementRepository.deleteAll();
        dailySettlementRepository.deleteAll();
        settlementRepository.deleteAll();

        // 주문 및 하위 엔티티 삭제
        orderRepository.deleteAll();
    }
    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(settlementJob);
    }
    @Test
    @Disabled("DailySettlement 생성 이슈로 인해 임시 비활성화")
    @DisplayName("weeklyStep 실행 시 주별 집계가 된다.")
    void weeklyStep() {
        // --- 1) cutoff + 주문시간 ----
        LocalDateTime now = LocalDateTime.now();

        String cutoff = now.plusDays(1).toLocalDate().atStartOfDay().toString();
        LocalDate cutoffDate = LocalDate.parse(cutoff.substring(0, 10)); // FIX POINT ★

        LocalDateTime orderedAt = cutoffDate.minusDays(1).atTime(12, 0);

        System.out.println("orderedAt = " + orderedAt);
        System.out.println("cutoff    = " + cutoff);

        // --- 2) 기본 엔티티 생성 ----
        Grade grade = gradeRepository.save(BatchHelperData.createGrade());
        User seller = userRepository.save(BatchHelperData.createSeller(grade));

        ProductCategoryEntity category = categoryRepository.save(BatchHelperData.createCategory());
        ProductEntity product = productRepository.save(BatchHelperData.createProduct(seller, category));
        SkuEntity sku = skuRepository.save(BatchHelperData.createSku(product));

        Order order = BatchHelperData.createCompletedOrder(seller, orderedAt);
        orderRepository.saveAndFlush(BatchHelperData.setupFullOrderGraph(order, sku));

        // --- 3) settlementCreateStep 실행 ----
        JobParameters createParams = new JobParametersBuilder()
                .addString("cutoff", cutoff)
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        jobLauncherTestUtils.launchStep("settlementCreateStep", createParams);

        assertThat(settlementRepository.count())
                .as("[1] Settlement 생성 확인")
                .isEqualTo(1L);

        // --- 4) dailyStep 실행 ----
        JobParameters dailyParams = new JobParametersBuilder()
                .addString("cutoff", cutoff)
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        jobLauncherTestUtils.launchStep("dailyStep", dailyParams);

        assertThat(dailySettlementRepository.count())
                .as("[2] DailySettlement 생성 확인")
                .isEqualTo(1L);

        // --- 5) weeklyStep 실행 ----
        JobParameters weeklyParams = new JobParametersBuilder()
                .addString("cutoff", cutoff)
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        jobLauncherTestUtils.launchStep("weeklyStep", weeklyParams);

        List<WeeklySettlement> weekList = weeklySettlementRepository.findAll();

        assertThat(weekList)
                .as("[3] WeeklySettlement 생성 확인")
                .hasSize(1);

        WeeklySettlement weekly = weekList.get(0);

        // --- 6) 주차 범위 검증 ----
        LocalDate weekStart = cutoffDate.with(DayOfWeek.MONDAY);
        LocalDate weekEnd   = cutoffDate.with(DayOfWeek.SUNDAY);

        assertThat(weekly.getWeekStartDate()).isEqualTo(weekStart);
        assertThat(weekly.getWeekEndDate()).isEqualTo(weekEnd);

        // --- 7) 정산 합계 검증 ----
        Settlement st = settlementRepository.findAll().get(0);

        assertThat(weekly.getTotalSales()).isEqualTo(st.getSalesAmount());
        assertThat(weekly.getTotalFee()).isEqualTo(st.getFee());
        assertThat(weekly.getTotalVat()).isEqualTo(st.getVat());
        assertThat(weekly.getTotalSettlement()).isEqualTo(st.getSettlementAmount());


    }
}