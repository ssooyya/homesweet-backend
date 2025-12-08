package com.homesweet.homesweetback.domain.settlement.batch.step.aggregate;

import com.homesweet.homesweetback.common.exception.BusinessException;
import com.homesweet.homesweetback.common.exception.ErrorCode;
import com.homesweet.homesweetback.domain.grade.service.GradeService;
import com.homesweet.homesweetback.domain.settlement.repository.SettlementRepository;
import com.homesweet.homesweetback.domain.settlement.repository.WeeklySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.util.calculator.SettlementCalculator;
import com.homesweet.homesweetback.domain.settlement.util.saver.SettlementSaver;
import com.homesweet.homesweetback.domain.settlement.util.vo.SettlementTotals;
import com.homesweet.homesweetback.domain.settlement.validation.SettlementValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("월별 tasklet 단위 테스트")
class MonthlySettlementTaskletTest {

    @InjectMocks
    private MonthlySettlementTasklet monthlySettlementTasklet;

    @Mock
    private WeeklySettlementRepository weeklySettlementRepository;

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private SettlementValidator settlementValidator;

    @Mock
    private SettlementSaver settlementSaver;

    @Mock
    private GradeService gradeService;

    StepContribution contribution = mock(StepContribution.class);
    ChunkContext context = mock(ChunkContext.class);

    @BeforeEach
    void init() {
        ReflectionTestUtils.setField(
                monthlySettlementTasklet,
                "cutoffString",
                "2025-11-25T00:00:00"
        );
    }

    @Nested
    @DisplayName("성공 케이스")
    class Success {

        @Test
        @DisplayName("단일 사용자 월별 집계 성공")
        void execute_success_singleUser() {
            Long userId = 10L;
            YearMonth ym = YearMonth.of(2025, 11);

            given(settlementRepository.findDistinctUserIds())
                    .willReturn(List.of(userId));

            given(settlementRepository.sumTotals(eq(userId), any(), any()))
                    .willReturn(new SettlementTotals(
                            BigDecimal.TEN,
                            BigDecimal.ONE,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            BigDecimal.valueOf(9)
                    ));

            doNothing().when(settlementValidator).validateTotals(any());

            RepeatStatus result = monthlySettlementTasklet.execute(contribution, context);

            assertThat(result).isEqualTo(RepeatStatus.FINISHED);

            verify(settlementSaver).saveMonthly(eq(userId), eq(ym), any(SettlementTotals.class));
        }

        @Test
        @DisplayName("여러 사용자 월별 집계 성공")
        void execute_success_multiUsers() {
            List<Long> userIds = List.of(1L, 2L);

            given(settlementRepository.findDistinctUserIds())
                    .willReturn(userIds);

            given(settlementRepository.sumTotals(anyLong(), any(), any()))
                    .willReturn(SettlementTotals.empty());

            doNothing().when(settlementValidator).validateTotals(any());

            monthlySettlementTasklet.execute(contribution, context);

            verify(settlementSaver, times(2))
                    .saveMonthly(anyLong(), any(YearMonth.class), any(SettlementTotals.class));
        }
    }

    @Nested
    @DisplayName("실패 케이스")
    class Failure {

        @Test
        @DisplayName("cutoffString 파싱 실패 → 예외 발생")
        void execute_fail_invalidCutoff() {
            ReflectionTestUtils.setField(
                    monthlySettlementTasklet,
                    "cutoffString",
                    "INVALID_DATE"
            );

            assertThatThrownBy(() ->
                    monthlySettlementTasklet.execute(contribution, context)
            ).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("sumTotals가 null → NPE 발생")
        void execute_fail_sumTotals_null() {
            Long userId = 10L;

            given(settlementRepository.findDistinctUserIds())
                    .willReturn(List.of(userId));

            given(settlementRepository.sumTotals(anyLong(), any(), any()))
                    .willReturn(null);

            assertThatThrownBy(() ->
                    monthlySettlementTasklet.execute(contribution, context)
            ).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("saveMonthly 중 BusinessException 발생")
        void execute_fail_saveMonthlyError() {
            Long userId = 10L;

            given(settlementRepository.findDistinctUserIds())
                    .willReturn(List.of(userId));

            SettlementTotals totals = SettlementTotals.empty();

            given(settlementRepository.sumTotals(anyLong(), any(), any()))
                    .willReturn(totals);

            doNothing().when(settlementValidator).validateTotals(totals);

            doThrow(new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR))
                    .when(settlementSaver)
                    .saveMonthly(anyLong(), any(), any());

            assertThatThrownBy(() ->
                    monthlySettlementTasklet.execute(contribution, context)
            ).isInstanceOf(BusinessException.class);
        }
    }
}
