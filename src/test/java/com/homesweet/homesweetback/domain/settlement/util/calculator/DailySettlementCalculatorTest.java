package com.homesweet.homesweetback.domain.settlement.util.calculator;

import com.homesweet.homesweetback.domain.settlement.dto.response.SettlementStatsDto;
import com.homesweet.homesweetback.domain.settlement.repository.SettlementRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@DisplayName("통계 계산 테스트")
class DailySettlementCalculatorTest {
    @InjectMocks
    private SettlementCalculator settlementCalculator;
    @Mock
    private SettlementRepository settlementRepository;
    @Test
    @DisplayName("[성공] 총 주문 건수, 총 정산완료건수, 총 정산 완료율 계산 성공")
    void calculateStats() {
        // given
        // given
        Long userId = 1L;
        LocalDate startDate = LocalDate.of(2025, 11, 10);
        LocalDate endDate = LocalDate.of(2025, 11, 11);

        SettlementStatsDto mockResult = new SettlementStatsDto(10L, 8L);

        given(settlementRepository.findStats(
                eq(userId),
                any(LocalDateTime.class),
                any(LocalDateTime.class))
        ).willReturn(mockResult);

        // when
        SettlementCalculator.SettlementStats stats =
                settlementCalculator.calculateStats(userId, startDate, endDate);

        // then
        assertThat(stats.totalCount()).isEqualTo(10L);
        assertThat(stats.completedCount()).isEqualTo(8L);
        assertThat(stats.completedRate()).isEqualTo(80.0); // 반올림 적용
    }
    @Nested
    @DisplayName("실패 케이스")
    class Fail {
        @Test
        @DisplayName("총 주문 건수가 0이면 정산 완료율도 0입니다.")
        void calculateTotalCount_Zero() {
            Long userId = 1L;
            LocalDate startDate = LocalDate.of(2025, 11, 10);
            LocalDate endDate = LocalDate.of(2025, 11, 11);

            SettlementStatsDto mockResult = new SettlementStatsDto(0L, 8L);

            given(settlementRepository.findStats(eq(userId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .willReturn(mockResult);

            // when
            SettlementCalculator.SettlementStats stats =
                    settlementCalculator.calculateStats(userId, startDate, endDate);

            // then
            assertThat(stats.totalCount()).isEqualTo(0L);
            assertThat(stats.completedCount()).isEqualTo(8L);
            assertThat(stats.completedRate()).isEqualTo(0.0);
        }
        @Test
        @DisplayName("총 주문건수보다 정산 완료건수가 클 수 없습니다.")
        void calculate_morethan_totalCount() {
            Long userId = 1L;
            LocalDate startDate = LocalDate.of(2025, 11, 10);
            LocalDate endDate = LocalDate.of(2025, 11, 11);

            SettlementStatsDto mockResult = new SettlementStatsDto(5L, 8L);

            given(settlementRepository.findStats(eq(userId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .willReturn(mockResult);

            // when
            SettlementCalculator.SettlementStats stats =
                    settlementCalculator.calculateStats(userId, startDate, endDate);

            // then
            assertThat(stats.totalCount()).isEqualTo(5L);
            assertThat(stats.completedCount()).isEqualTo(8L); // 로직상 별도 제한 없음
            assertThat(stats.completedRate()).isEqualTo(160.0); // 그대로 계산됨
        }
    }
}