package com.homesweet.homesweetback.domain.settlement.service.integration;

import com.homesweet.homesweetback.common.exception.BusinessException;
import com.homesweet.homesweetback.common.exception.ErrorCode;
import com.homesweet.homesweetback.domain.settlement.data.HelpIntegrationData;
import com.homesweet.homesweetback.domain.settlement.dto.response.EmptyResponse;
import com.homesweet.homesweetback.domain.settlement.dto.response.MonthlySettlementResponse;
import com.homesweet.homesweetback.domain.settlement.entity.MonthlySettlement;
import com.homesweet.homesweetback.domain.settlement.repository.MonthlySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.repository.WeeklySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.service.MonthlySettlementService;
import com.homesweet.homesweetback.domain.settlement.service.SettlementCacheService;
import com.homesweet.homesweetback.domain.settlement.util.calculator.MonthlyDateRangeCalculator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("MonthlySettlementService 통합 테스트")
@Disabled
public class MonthlySettlementIntegrationTest {

    @Autowired
    private MonthlySettlementService monthlySettlementService;

    @Autowired
    private SettlementCacheService settlementCacheService;

    @Autowired
    private MonthlySettlementRepository monthlySettlementRepository;

    @Autowired
    private HelpIntegrationData helper;
    @Autowired
    private MonthlyDateRangeCalculator monthlyCalc;
    @Autowired
    private EmptyResponse emptyResponse;
    @PersistenceContext
    private EntityManager em;

    private final Long userId = 11L;
    Pageable pageable = PageRequest.of(0, 10);
    @Nested
    @DisplayName("성공 케이스")
    class Success {

        @Test
        @DisplayName("캐시에 데이터가 존재하면 count 조회 후 정상 Page 반환")
        void getMonthlySummary_success() {

            Long userId = 1L;
            LocalDate start = LocalDate.of(2025, 3, 1);
            LocalDate end   = LocalDate.of(2025, 3, 31);

            MonthlySettlementResponse resp =
                    new MonthlySettlementResponse(
                            (short) 2025,
                            (byte) 3,
                            BigDecimal.valueOf(100000),
                            BigDecimal.valueOf(5000),
                            BigDecimal.valueOf(10000),
                            BigDecimal.ZERO,
                            BigDecimal.valueOf(85000),
                            12.5,
                            10L
                    );

            // 1) 캐시 HIT
            when(settlementCacheService.getMonthlyContentCache(userId, start, end, pageable))
                    .thenReturn(List.of(resp));

            // 2) 범위 계산
            MonthlyDateRangeCalculator.MonthlyDateRange range =
                    monthlyCalc.MonthlyDateRangeCalculate(start, end);

            // 3) count 조회 stub
            when(monthlySettlementRepository.countByRange(
                    userId,
                    range.fromYear(),
                    range.fromMonth(),
                    range.toYear(),
                    range.toMonth()
            )).thenReturn(10L);

            // WHEN
            Page<MonthlySettlementResponse> result =
                    monthlySettlementService.getMonthlySummary(userId, start, end, pageable);

            // THEN
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(10L);
            assertThat(result.getContent().get(0).totalSales())
                    .isEqualByComparingTo("100000");

            verify(emptyResponse, never()).createEmptyMonthly(any(), any());
        }
    }
    @Nested
    @DisplayName("실패 케이스")
    class Fail {

        @Test
        @DisplayName("캐시가 empty면 EmptyResponse 반환 (count 조회 X)")
        void getMonthlySummary_empty() {

            Long userId = 1L;
            LocalDate start = LocalDate.of(2025, 3, 1);
            LocalDate end   = LocalDate.of(2025, 3, 31);

            // 1) 캐시 MISS
            when(settlementCacheService.getMonthlyContentCache(userId, start, end, pageable))
                    .thenReturn(List.of());

            YearMonth ym = YearMonth.from(start);

            Page<MonthlySettlementResponse> emptyPage =
                    new PageImpl<>(List.of(), pageable, 0);

            when(emptyResponse.createEmptyMonthly(ym, pageable))
                    .thenReturn(emptyPage);

            // WHEN
            Page<MonthlySettlementResponse> result =
                    monthlySettlementService.getMonthlySummary(userId, start, end, pageable);

            // THEN
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isEqualTo(0);

            verify(emptyResponse, times(1)).createEmptyMonthly(ym, pageable);

            // count 조회 되면 안 됨
            verify(monthlySettlementRepository, never())
                    .countByRange(any(), any(), any(), any(), any());
        }
    }
}