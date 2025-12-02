package com.homesweet.homesweetback.domain.settlement.batch.job;

import com.homesweet.homesweetback.domain.auth.entity.User;
import com.homesweet.homesweetback.domain.auth.repository.UserRepository;
import com.homesweet.homesweetback.domain.grade.entity.Grade;
import com.homesweet.homesweetback.domain.grade.repository.GradeRepository;
import com.homesweet.homesweetback.domain.order.entity.DeliveryStatus;
import com.homesweet.homesweetback.domain.order.entity.Order;
import com.homesweet.homesweetback.domain.order.repository.OrderRepository;
import com.homesweet.homesweetback.domain.product.category.repository.jpa.ProductCategoryJPARepository;
import com.homesweet.homesweetback.domain.product.category.repository.jpa.entity.ProductCategoryEntity;
import com.homesweet.homesweetback.domain.product.product.command.repository.jpa.ProductJPARepository;
import com.homesweet.homesweetback.domain.product.product.command.repository.jpa.SkuJPARepository;
import com.homesweet.homesweetback.domain.product.product.command.repository.jpa.entity.ProductEntity;
import com.homesweet.homesweetback.domain.product.product.command.repository.jpa.entity.SkuEntity;
import com.homesweet.homesweetback.domain.settlement.data.BatchHelperData;
import com.homesweet.homesweetback.domain.settlement.entity.Settlement;
import com.homesweet.homesweetback.domain.settlement.repository.SettlementRepository;
import com.homesweet.homesweetback.domain.settlement.util.TestAuditingConfig;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@SpringBatchTest
@ActiveProfiles("test")
@Import({BatchConfig.class, TestAuditingConfig.class})
@DisplayName("정산 취소 step 통합테스트")
@TestPropertySource(properties = {
        "logging.level.com.homesweet=DEBUG",
        "logging.level.org.springframework.batch=DEBUG"
})
public class SettlementCancelStepIntegrationTest {
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

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(settlementJob);
    }

    @Test
    @DisplayName("정산 취소 Step이 정상적으로 실행되어 Settlement가 취소된다.")
    void settlementCancelStep_success() {
        // -------------------------------------
        // 1) cutoff = "내일 00:00"
        // -------------------------------------
        LocalDateTime now = LocalDateTime.now();
        String cutoff = now.plusDays(1).toLocalDate().atStartOfDay().toString();

        // orderedAt = cutoff 하루 전 12시 (항상 cutoff보다 과거)
        LocalDateTime orderedAt = now.toLocalDate().atStartOfDay().minusDays(1).withHour(12);

        System.out.println("cutoff = " + cutoff);
        System.out.println("orderedAt = " + orderedAt);

        // -------------------------------------
        // 2) 판매자 + 상품 + SKU 저장
        // -------------------------------------
        Grade grade = gradeRepository.save(BatchHelperData.createGrade());
        User seller = userRepository.save(BatchHelperData.createSeller(grade));

        ProductCategoryEntity category = categoryRepository.save(BatchHelperData.createCategory());
        ProductEntity product = productRepository.save(BatchHelperData.createProduct(seller, category));
        SkuEntity sku = skuRepository.save(BatchHelperData.createSku(product));

        // -------------------------------------
        // 3) 주문 생성 (cutoff기준 포함됨)
        // -------------------------------------
        Order order = BatchHelperData.createCompletedOrder(seller, orderedAt);
        order = BatchHelperData.setupFullOrderGraph(order, sku);
        orderRepository.saveAndFlush(order);

        System.out.println("saved orderedAt = " + order.getOrderedAt());

        // -------------------------------------
        // 4) settlementCreateStep 실행
        // -------------------------------------
        JobParameters createParams = new JobParametersBuilder()
                .addString("cutoff", cutoff)
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        jobLauncherTestUtils.launchStep("settlementCreateStep", createParams);

        assertThat(settlementRepository.count()).isEqualTo(1);

        Settlement created = settlementRepository.findAll().get(0);
        assertThat(created.getSettlementStatus()).isEqualTo("PENDING");

        // -------------------------------------
        // 5) 주문 배송상태를 CANCELLED로 변경
        // -------------------------------------
        order.setDeliveryStatus(DeliveryStatus.CANCELLED);
        orderRepository.saveAndFlush(order);

        // -------------------------------------
        // 6) settlementCancelStep 실행
        // -------------------------------------
        JobParameters cancelParams = new JobParametersBuilder()
                .addString("cutoff", cutoff)
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        JobExecution cancelExe = jobLauncherTestUtils.launchStep("settlementCancelStep", cancelParams);

        assertThat(cancelExe.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // -------------------------------------
        // 7) 취소 여부 확인
        // -------------------------------------
        Settlement canceled = settlementRepository.findAll().get(0);
        assertThat(canceled.getSettlementStatus()).isEqualTo("CANCELED");

        System.out.println("최종 Settlement Status = " + canceled.getSettlementStatus());
    }
}
