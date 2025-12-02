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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.batch.job.enabled=true")
@SpringBatchTest
@ActiveProfiles("test")
@Import({BatchConfig.class,  TestAuditingConfig.class})
@DisplayName("정산 생성 step 통합테스트")
@TestPropertySource(properties = {
        "logging.level.com.homesweet=DEBUG",
        "logging.level.org.springframework.batch=DEBUG"
})
public class SettlementCreateStepIntegrationTest {

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

    @BeforeEach
    void clean() {
        // 정산 관련 삭제
        settlementRepository.deleteAll();

        // 주문 및 하위 엔티티 삭제
        orderRepository.deleteAll();

        // 필요하면 product/sku 등도 함께 삭제
    }

    @Test
    @DisplayName("정산 생성 Step이 정상적으로 실행되어 Settlement가 저장된다")
    void settlementCreateStep_success() throws Exception {

        // -------------------------------------
        // 1) cutoff = 내일 00:00
        // -------------------------------------
        LocalDateTime now = LocalDateTime.now();
        String cutoff = now.plusDays(1).toLocalDate().atStartOfDay().toString();

        // orderedAt = cutoff 하루 전 12:00
        LocalDateTime orderedAt = now.toLocalDate().minusDays(1).atStartOfDay().withHour(12);

        System.out.println("cutoff = " + cutoff);
        System.out.println("orderedAt = " + orderedAt);

        // -------------------------------------
        // 2) 테스트 데이터 생성
        // -------------------------------------
        Grade grade = gradeRepository.save(BatchHelperData.createGrade());
        User seller = userRepository.save(BatchHelperData.createSeller(grade));

        ProductCategoryEntity category = categoryRepository.save(BatchHelperData.createCategory());
        ProductEntity product = productRepository.save(BatchHelperData.createProduct(seller, category));
        SkuEntity sku = skuRepository.save(BatchHelperData.createSku(product));

        Order order = BatchHelperData.createCompletedOrder(seller, orderedAt);
        order = BatchHelperData.setupFullOrderGraph(order, sku);
        orderRepository.saveAndFlush(order);

        // -------------------------------------
        // 3) settlementCreateStep 실행
        // -------------------------------------
        JobParameters params = new JobParametersBuilder()
                .addString("cutoff", cutoff)
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncherTestUtils.launchStep("settlementCreateStep", params);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // -------------------------------------
        // 4) Settlement 생성 검증
        // -------------------------------------
        List<Settlement> settlements = settlementRepository.findAll();
        assertThat(settlements).hasSize(1);
    }
}
