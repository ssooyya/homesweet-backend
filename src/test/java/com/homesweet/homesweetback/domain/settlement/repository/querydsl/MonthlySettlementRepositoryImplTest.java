package com.homesweet.homesweetback.domain.settlement.repository.querydsl;

import com.homesweet.homesweetback.domain.settlement.data.HelpIntegrationData;
import com.homesweet.homesweetback.domain.settlement.entity.MonthlySettlement;
import com.homesweet.homesweetback.domain.settlement.repository.MonthlySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.repository.WeeklySettlementRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
@ActiveProfiles("test")
@SpringBootTest
@Transactional
public class MonthlySettlementRepositoryImplTest {
    @Autowired
    private CustomMonthlySettlementRepository monthlyImpl;

    @Autowired
    private MonthlySettlementRepository monthlyRepository;

    @Autowired
    private WeeklySettlementRepository weeklyRepository;

    @Autowired
    private EntityManager em;
    @Autowired
    private HelpIntegrationData helpIntegrationData;

    @BeforeEach
    void clean() {
        monthlyRepository.deleteAll();
        weeklyRepository.deleteAll();
        em.flush();
        em.clear();
    }
    @Test
    @DisplayName("[성공] upsertMonthly → INSERT 확인")
    void upsertMonthly_insert_success() {

        Long userId = 11L;
        short year = 2025;
        byte month = 3;

        // Weekly 데이터: 1주차 + 2주차
        weeklyRepository.save(helpIntegrationData.weekly(
                userId, year, month, 0,
                BigDecimal.valueOf(50000),
                BigDecimal.valueOf(5000),
                BigDecimal.valueOf(5000),
                BigDecimal.ZERO,
                BigDecimal.valueOf(40000)
        ));

        weeklyRepository.save(helpIntegrationData.weekly(
                userId, year, month, 1,
                BigDecimal.valueOf(25000),
                BigDecimal.valueOf(2500),
                BigDecimal.valueOf(2500),
                BigDecimal.ZERO,
                BigDecimal.valueOf(20000)
        ));

        em.flush();
        em.clear();

        // when
        monthlyImpl.upsertMonthly(userId, year, month, SettlementTotals.empty());

        // then
        List<MonthlySettlement> list = monthlyRepository.findByMonthlySettlement(userId);
        assertThat(list).hasSize(1);

        MonthlySettlement saved = list.get(0);

        assertThat(saved.getTotalSales()).isEqualByComparingTo("75000");   // 50,000 + 25,000
        assertThat(saved.getTotalFee()).isEqualByComparingTo("7500");
        assertThat(saved.getTotalVat()).isEqualByComparingTo("7500");
        assertThat(saved.getTotalSettlement()).isEqualByComparingTo("60000");
    }
    @Test
    @DisplayName("[성공] upsertMonthly → UPDATE 확인")
    void upsertMonthly_update_success() {

        Long userId = 11L;
        short year = 2025;
        byte month = 3;

        // 1) 먼저 weekly 데이터 생성
        weeklyRepository.save(helpIntegrationData.weekly(
                userId, year, month, 0,
                BigDecimal.valueOf(90000),
                BigDecimal.valueOf(9000),
                BigDecimal.valueOf(9000),
                BigDecimal.ZERO,
                BigDecimal.valueOf(72000)
        ));

        em.flush();
        em.clear();

        // 2) 기존 Monthly row 저장
        MonthlySettlement before = MonthlySettlement.builder()
                .userId(userId)
                .year(year)
                .month(month)
                .totalSales(BigDecimal.valueOf(10000))
                .totalFee(BigDecimal.valueOf(1000))
                .totalVat(BigDecimal.valueOf(1000))
                .totalRefund(BigDecimal.ZERO)
                .totalSettlement(BigDecimal.valueOf(8000))
                .build();

        monthlyRepository.save(before);

        em.flush();
        em.clear();

        // UPDATE 호출
        monthlyImpl.upsertMonthly(userId, year, month, SettlementTotals.empty());

        // then
        MonthlySettlement saved = monthlyRepository.findByMonthlySettlement(userId).get(0);

        assertThat(saved.getTotalSales()).isEqualByComparingTo("90000");
        assertThat(saved.getTotalFee()).isEqualByComparingTo("9000");
        assertThat(saved.getTotalVat()).isEqualByComparingTo("9000");
        assertThat(saved.getTotalSettlement()).isEqualByComparingTo("72000");
    }


    @Nested
    @DisplayName("실패 케이스")
    class FailTest {

        @Test
        @DisplayName("totals = null → NPE")
        void fail_totals_null() {
            assertThatThrownBy(() ->
                    monthlyImpl.upsertMonthly(1L, (short)2025, (byte)1, null)
            ).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Weekly 데이터가 없어 SUM=null → return 0")
        void fail_no_weekly_data() {
            monthlyImpl.upsertMonthly(1L, (short)2025, (byte)1, SettlementTotals.empty());
        }
    }
}
