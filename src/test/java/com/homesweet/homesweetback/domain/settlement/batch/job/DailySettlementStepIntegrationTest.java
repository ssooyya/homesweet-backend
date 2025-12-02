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
import com.homesweet.homesweetback.domain.settlement.repository.DailySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.repository.SettlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.*;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest(properties = "spring.batch.job.enabled=true")
@SpringBatchTest
@ActiveProfiles("test")
@Import(BatchConfig.class)
@DisplayName("일별 집계 step 통합테스트")
class DailySettlementStepIntegrationTest {
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
    private DailySettlementRepository dailySettlementRepository;
    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(settlementJob);
    }
    @Test
    @DisplayName("dailyStep 실행 시 일별 집계가 된다.")
    void dailyStep() {
//        jobRepositoryTestUtils.removeJobExecutions();

        Long userId = 10L;
        LocalDate cutoffDate = LocalDate.of(2025, 1, 10);

        // --- 테스트용 Settlement 데이터 삽입 ---
        Settlement settlement1 = Settlement.builder()
                .userId(userId)
                .salesAmount(BigDecimal.valueOf(10000))
                .fee(BigDecimal.valueOf(500))
                .vat(BigDecimal.valueOf(1000))
                .refundAmount(BigDecimal.ZERO)
                .settlementAmount(BigDecimal.valueOf(8500))
                .settlementDate(LocalDateTime.of(2025, 1, 10, 10, 0))
                .build();

        Settlement settlement2 = Settlement.builder()
                .userId(userId)
                .salesAmount(BigDecimal.valueOf(20000))
                .fee(BigDecimal.valueOf(1000))
                .vat(BigDecimal.valueOf(2000))
                .refundAmount(BigDecimal.ZERO)
                .settlementAmount(BigDecimal.valueOf(17000))
                .settlementDate(LocalDateTime.of(2025, 1, 10, 15, 0))
                .build();

        settlementRepository.saveAll(List.of(settlement1, settlement2));

        // --- job parameter 준비 ---
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("cutoff", "2025-01-10T00:00:00")
                .toJobParameters();

        // when: job 실행
        JobExecution execution = jobLauncherTestUtils.launchStep("dailyStep", jobParameters);
        System.out.println(execution);

        // then: 상태 확인
        assertThat(execution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");

        // DailySettlement row 확인
        List<DailySettlement> results =
                dailySettlementRepository.findByDailySettlementByRange(
                        userId,
                        cutoffDate.atStartOfDay(),
                        cutoffDate.plusDays(1).atStartOfDay(),
                        Pageable.unpaged()
                ).getContent();

        assertThat(results).hasSize(1);

        DailySettlement daily = results.get(0);

        assertThat(daily.getTotalSales()).isEqualByComparingTo("30000");
        assertThat(daily.getTotalFee()).isEqualByComparingTo("1500");
        assertThat(daily.getTotalVat()).isEqualByComparingTo("3000");
        assertThat(daily.getTotalSettlement()).isEqualByComparingTo("25500");
    }
}