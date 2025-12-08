package com.homesweet.homesweetback.domain.settlement.util;

import com.homesweet.homesweetback.domain.settlement.dto.response.*;
import com.homesweet.homesweetback.domain.settlement.util.calculator.WeeklyDateRangeCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmptyDailyResponse 테스트")
class EmptyResponseTest {
//    @InjectMocks
    private EmptyResponse emptyResponse;

    @BeforeEach
    void setUp() {
        emptyResponse = new EmptyResponse();
    }

    @Test
    @DisplayName("단일 EmptyDailyResponse 생성 성공")
    void createEmptyDaily() {
        // given
        LocalDate startDate = LocalDate.of(2025, 11, 10);

        // when
        DailySettlementResponse response = emptyResponse.createEmptyDaily(startDate);

        // then
        assertThat(response.totalSales()).isEqualTo(BigDecimal.ZERO);
        assertThat(response.totalFee()).isEqualTo(BigDecimal.ZERO);
        assertThat(response.totalVat()).isEqualTo(BigDecimal.ZERO);
        assertThat(response.totalRefund()).isEqualTo(BigDecimal.ZERO);
        assertThat(response.totalSettlement()).isEqualTo(BigDecimal.ZERO);

        assertThat(response.settlementDate()).isEqualTo(startDate);
        assertThat(response.settlementStatus()).isEqualTo("CANCELED");
        assertThat(response.completedRate()).isEqualTo(0.0);
        assertThat(response.totalCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("EmptyDailyResponse Page 생성 성공")
    void testCreateEmptyDaily() {
        System.out.println(emptyResponse.getClass());
        System.out.println(emptyResponse.getClass().getName());


        LocalDate startDate = LocalDate.of(2025, 11, 10);
        Pageable pageable = PageRequest.of(0, 10);

        Page<DailySettlementResponse> page =
                emptyResponse.createEmptyDaily(startDate, pageable);

        System.out.println(page);
        System.out.println("TotalElements=" + page.getTotalElements());
        System.out.println("Content=" + page.getContent());
        // 전체 데이터는 0개
        assertThat(page.getTotalElements()).isEqualTo(1L);

        // content 는 placeholder 0개
        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("[성공] WeeklyDateRange 기반으로 빈 주별 응답 Page가 생성된다.")
    void createEmptyWeekly_success() {
        // given
        WeeklyDateRangeCalculator.WeeklyDateRange range =
                new WeeklyDateRangeCalculator.WeeklyDateRange(
                        LocalDate.of(2025, 11, 10),
                        LocalDate.of(2025, 11, 17),
                        (byte) 2
                );

        Pageable pageable = PageRequest.of(0, 10);

        Page<WeeklySettlementResponse> result =
                emptyResponse.createEmptyWeekly(range, pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1); // ✔ empty list
        assertThat(result.getTotalElements()).isEqualTo(1L);

        // 나머지는 result 자체 값으로만 검증
        assertThat(result.getSize()).isEqualTo(10);
        assertThat(result.getNumber()).isEqualTo(0);
    }
    @Test
    @DisplayName("빈 월별 응답 생성 성공")
    void createEmptyMonthly_success() {
        YearMonth ym = YearMonth.of(2025, 3);
        Pageable pageable = PageRequest.of(0, 10);

        Page<MonthlySettlementResponse> page =
                emptyResponse.createEmptyMonthly(ym, pageable);

        assertThat(page).isNotNull();
        assertThat(page.getContent()).hasSize(1);

        MonthlySettlementResponse res = page.getContent().get(0);

        assertThat(res.year()).isEqualTo((short) 2025);
        assertThat(res.month()).isEqualTo((byte) 3);
        assertThat(res.totalSales()).isEqualTo(BigDecimal.ZERO);
        assertThat(res.totalSettlement()).isEqualTo(BigDecimal.ZERO);
        assertThat(res.totalCount()).isEqualTo(0L);
        assertThat(page.getTotalElements()).isEqualTo(1);
    }
    @Test
    @DisplayName("빈 연별 응답 생성 성공")
    void createEmptyYearly_success() {
        Short ym = 2025;
        Pageable pageable = PageRequest.of(0, 10);

        Page<YearlySettlementResponse> page =
                emptyResponse.createEmptyYearly(ym, pageable);

        assertThat(page).isNotNull();
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getTotalElements()).isEqualTo(1);

        YearlySettlementResponse res = page.getContent().get(0);

        // year 정상 설정
        assertThat(res.year()).isEqualTo((short) 2025);

        // 모든 금액 0
        assertThat(res.totalSales()).isEqualTo(BigDecimal.ZERO);
        assertThat(res.totalFee()).isEqualTo(BigDecimal.ZERO);
        assertThat(res.totalVat()).isEqualTo(BigDecimal.ZERO);
        assertThat(res.totalRefund()).isEqualTo(BigDecimal.ZERO);
        assertThat(res.totalSettlement()).isEqualTo(BigDecimal.ZERO);

        // totalCount = 0
        assertThat(res.totalCount()).isEqualTo(0L);
    }
    @Nested
    @DisplayName("실패 케이스")
    class Fail{
        @Test
        @DisplayName("startDate가 null이어도 예외가 발생하지 않는다")
        void createEmptyDaily_nullStartDate_fail() {
            DailySettlementResponse response = emptyResponse.createEmptyDaily(null);
            assertThat(response.settlementDate()).isNull();
        }

        @Test
        @DisplayName("WeeklyDateRange가 null이면 NullPointerException")
        void createEmptyWeekly_fail_null_range() {
            Pageable pageable = PageRequest.of(0, 10);
            assertThatThrownBy(() ->
                    emptyResponse.createEmptyWeekly(null, pageable)
            ).isInstanceOf(NullPointerException.class);
        }
        @Test
        @DisplayName("Pageable이 null이면 IllegalArgumentException 발생")
        void createEmptyWeekly_fail_null_pageable() {
            WeeklyDateRangeCalculator.WeeklyDateRange range =
                    new WeeklyDateRangeCalculator.WeeklyDateRange(
                            LocalDate.of(2025, 11, 10),
                            LocalDate.of(2025, 11, 17),
                            (byte) 2
                    );
            assertThatThrownBy(() ->
                    emptyResponse.createEmptyWeekly(range, null)
            ).isInstanceOf(IllegalArgumentException.class)   // ⬅ 수정됨
                    .hasMessageContaining("Pageable must not be null");
        }
        @Test
        @DisplayName("빈 월별 응답은 content가 비어있으면 안 된다")
        void createEmptyMonthly_fail_content_empty() {
            YearMonth ym = YearMonth.of(2025, 3);
            Pageable pageable = PageRequest.of(0, 10);

            Page<MonthlySettlementResponse> page =
                    emptyResponse.createEmptyMonthly(ym, pageable);

            assertThat(page.getContent())
                    .as("placeholder row must exist")
                    .isNotEmpty();
        }
        @Test
        @DisplayName("빈 월별 응답의 totalElements는 반드시 1이어야 한다")
        void createEmptyMonthly_fail_wrong_totalElements() {
            YearMonth ym = YearMonth.of(2025, 3);
            Pageable pageable = PageRequest.of(0, 10);

            Page<MonthlySettlementResponse> page =
                    emptyResponse.createEmptyMonthly(ym, pageable);

            assertThat(page.getTotalElements())
                    .as("totalElements must be exactly 1 due to placeholder")
                    .isEqualTo(1);
        }
        @Test
        @DisplayName("빈 월별 응답 placeholder의 모든 금액은 ZERO여야 한다")
        void createEmptyMonthly_fail_placeholder_values_not_zero() {
            YearMonth ym = YearMonth.of(2025, 3);
            Pageable pageable = PageRequest.of(0, 10);

            Page<MonthlySettlementResponse> page =
                    emptyResponse.createEmptyMonthly(ym, pageable);

            MonthlySettlementResponse res = page.getContent().get(0);

            assertThat(res.totalSales()).isZero();
            assertThat(res.totalFee()).isZero();
            assertThat(res.totalVat()).isZero();
            assertThat(res.totalRefund()).isZero();
            assertThat(res.totalSettlement()).isZero();
        }
        @Test
        @DisplayName("빈 월별 응답 placeholder의 연도/월은 입력값과 동일해야 한다")
        void createEmptyMonthly_fail_wrong_year_month() {
            YearMonth ym = YearMonth.of(2025, 3);
            Pageable pageable = PageRequest.of(0, 10);

            Page<MonthlySettlementResponse> page =
                    emptyResponse.createEmptyMonthly(ym, pageable);

            MonthlySettlementResponse res = page.getContent().get(0);

            assertThat(res.year()).isEqualTo((short) ym.getYear());
            assertThat(res.month()).isEqualTo((byte) ym.getMonthValue());
        }

        @Test
        @DisplayName("pageable 이 null이면 IllegalArgumentException 발생")
        void createEmptyYearly_fail_nullPageable() {
            Short ym = 2025;

            assertThatThrownBy(() ->
                    emptyResponse.createEmptyYearly(ym, null)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Pageable must not be null");
        }
        @Test
        @DisplayName("placeholder 크기가 1개가 아니면 실패")
        void createEmptyYearly_fail_wrongContentSize() {
            Short ym = 2025;
            Pageable pageable = PageRequest.of(0, 10);

            Page<YearlySettlementResponse> page =
                    emptyResponse.createEmptyYearly(ym, pageable);

            assertThat(page.getContent())
                    .as("Empty yearly content size must be 1")
                    .hasSize(1);   // 실패 목적: size가 1이 아니면 실패
        }
    }
}