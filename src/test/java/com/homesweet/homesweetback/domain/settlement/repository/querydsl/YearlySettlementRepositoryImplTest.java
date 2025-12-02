package com.homesweet.homesweetback.domain.settlement.repository.querydsl;

import com.homesweet.homesweetback.domain.settlement.data.HelpIntegrationData;
import com.homesweet.homesweetback.domain.settlement.entity.YearlySettlement;
import com.homesweet.homesweetback.domain.settlement.repository.MonthlySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.repository.YearlySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.repository.querydsl.testImpl.YearlySettlementRepositoryImpl;
import com.homesweet.homesweetback.domain.settlement.util.vo.SettlementTotals;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
@ActiveProfiles("test")
@SpringBootTest
@Transactional
public class YearlySettlementRepositoryImplTest {
    @Autowired
    private YearlySettlementRepositoryImpl yearlyImpl;

    @Autowired
    private MonthlySettlementRepository monthlyRepository;

    @Autowired
    private YearlySettlementRepository yearlyRepository;

    @Autowired
    private EntityManager em;

    private Long userId;
    private short year;
    @Autowired
    private HelpIntegrationData helpIntegrationData;

    @BeforeEach
    void setUp() {
        yearlyRepository.deleteAll();
        monthlyRepository.deleteAll();
        em.flush();
        em.clear();
        userId = 11L;
        year = 2025;
    }
    @Nested
    @DisplayName("성공 케이스")
    class Success {
        @Test
        @DisplayName("upsertYearly → INSERT")
        void upsertYearly_insert_success() {

            // monthly 데이터 2개 저장 (합계 → 90000)
            monthlyRepository.save(helpIntegrationData.monthly(
                    userId, year, (byte) 1,
                    new BigDecimal("50000"),
                    new BigDecimal("5000"),
                    new BigDecimal("5000"),
                    BigDecimal.ZERO,
                    new BigDecimal("40000")
            ));

            monthlyRepository.save(helpIntegrationData.monthly(
                    userId, year, (byte) 2,
                    new BigDecimal("40000"),
                    new BigDecimal("4000"),
                    new BigDecimal("4000"),
                    BigDecimal.ZERO,
                    new BigDecimal("32000")
            ));

            em.flush();
            em.clear();

            // when
            yearlyImpl.upsertYearly(userId, year, SettlementTotals.empty());

            // then

            YearlySettlement saved = yearlyRepository.findByYearlySettlement(userId).get(0);

            assertThat(saved.getTotalSales()).isEqualByComparingTo("90000");
            assertThat(saved.getTotalFee()).isEqualByComparingTo("9000");
            assertThat(saved.getTotalVat()).isEqualByComparingTo("9000");
            assertThat(saved.getTotalSettlement()).isEqualByComparingTo("72000");
        }
        @Test
        @DisplayName("upsertYearly → UPDATE")
        void upsertYearly_update_success() {
            // 기존 yearly 저장
            YearlySettlement before = YearlySettlement.builder()
                    .userId(userId)
                    .year(year)
                    .totalSales(new BigDecimal("10000"))
                    .totalFee(new BigDecimal("1000"))
                    .totalVat(new BigDecimal("1000"))
                    .totalRefund(BigDecimal.ZERO)
                    .totalSettlement(new BigDecimal("8000"))
                    .build();

            yearlyRepository.save(before);

            // monthly 데이터 저장 (합계 → 90000)
            monthlyRepository.save(helpIntegrationData.monthly(
                    userId, year, (byte) 1,
                    new BigDecimal("50000"),
                    new BigDecimal("5000"),
                    new BigDecimal("5000"),
                    BigDecimal.ZERO,
                    new BigDecimal("40000")
            ));

            monthlyRepository.save(helpIntegrationData.monthly(
                    userId, year, (byte) 2,
                    new BigDecimal("40000"),
                    new BigDecimal("4000"),
                    new BigDecimal("4000"),
                    BigDecimal.ZERO,
                    new BigDecimal("32000")
            ));

            em.flush();
            em.clear();

            // when
            yearlyImpl.upsertYearly(userId, year, SettlementTotals.empty());

            // then

            YearlySettlement updated = yearlyRepository.findByYearlySettlement(userId).get(0);

            assertThat(updated.getTotalSales()).isEqualByComparingTo("90000");
            assertThat(updated.getTotalFee()).isEqualByComparingTo("9000");
            assertThat(updated.getTotalVat()).isEqualByComparingTo("9000");
            assertThat(updated.getTotalSettlement()).isEqualByComparingTo("72000");
        }
    }
    @Nested
    @DisplayName("실패 케이스")
    class Fail{
        @Test
        @DisplayName("monthly 데이터 없음 → result = 0")
        void fail_no_monthly_data() {
            // 아무것도 저장하지 않음
            em.flush();
            em.clear();

            yearlyImpl.upsertYearly(userId, year, SettlementTotals.empty());
        }
    }
}
