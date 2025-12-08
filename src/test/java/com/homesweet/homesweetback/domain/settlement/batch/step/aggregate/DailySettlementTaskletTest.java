package com.homesweet.homesweetback.domain.settlement.batch.step.aggregate;

import com.homesweet.homesweetback.common.exception.BusinessException;
import com.homesweet.homesweetback.common.exception.ErrorCode;
import com.homesweet.homesweetback.domain.grade.service.GradeService;
import com.homesweet.homesweetback.domain.settlement.data.BatchHelperData;
import com.homesweet.homesweetback.domain.settlement.repository.SettlementRepository;
import com.homesweet.homesweetback.domain.settlement.util.SettlementStatusUpdater;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("일별 집계 tasklet 단위테스트")
class DailySettlementTaskletTest {

    @InjectMocks
    private DailySettlementTasklet dailySettlementTasklet;

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private SettlementValidator settlementValidator;

    @Mock
    private SettlementSaver settlementSaver;

    @Mock
    private SettlementStatusUpdater settlementStatusUpdater;

    @Mock
    private GradeService gradeService;

    StepContribution contribution = mock(StepContribution.class);
    ChunkContext context = mock(ChunkContext.class);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                dailySettlementTasklet,
                "cutoffString",
                "2025-11-25T00:00:00"
        );
    }

    // --------------------------------------
    // 성공 케이스
    // --------------------------------------
    @Nested
    @DisplayName("성공 케이스")
    class Success {

        @Test
        @DisplayName("정상적으로 일별 집계가 실행된다")
        void execute_success_singleUser() {
            Long userId = 1L;

            SettlementTotals totals = new SettlementTotals(
                    BigDecimal.TEN,
                    BigDecimal.ONE,
                    BigDecimal.ONE,
                    BigDecimal.ZERO,
                    BigDecimal.valueOf(8)
            );

            given(settlementRepository.findDistinctUserIds())
                    .willReturn(List.of(userId));

            given(settlementRepository.sumTotals(anyLong(), any(), any()))
                    .willReturn(totals);

            doNothing().when(settlementValidator).validateTotals(totals);

            doNothing().when(settlementSaver).saveDaily(eq(userId), any(), eq(totals));

            doNothing().when(settlementStatusUpdater).markDailyCompleted(eq(userId), any(), any());

            RepeatStatus status =
                    dailySettlementTasklet.execute(contribution, context);

            assertThat(status).isEqualTo(RepeatStatus.FINISHED);

            verify(settlementSaver).saveDaily(eq(userId), any(), eq(totals));
            verify(settlementStatusUpdater).markDailyCompleted(eq(userId), any(), any());
        }
    }

    // --------------------------------------
    // 실패 케이스
    // --------------------------------------
    @Nested
    @DisplayName("실패 케이스")
    class Failure {

        @Test
        @DisplayName("cutoffString 파싱 실패 시 예외 발생")
        void execute_fail_invalidCutoff() {

            ReflectionTestUtils.setField(
                    dailySettlementTasklet,
                    "cutoffString",
                    "INVALID_DATE"
            );

            assertThatThrownBy(() ->
                    dailySettlementTasklet.execute(contribution, context)
            ).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("sumTotals() 가 null이면 validator 에서 BusinessException 발생")
        void execute_fail_nullTotals() {
            Long userId = 10L;

            given(settlementRepository.findDistinctUserIds())
                    .willReturn(List.of(userId));

            given(settlementRepository.sumTotals(anyLong(), any(), any()))
                    .willReturn(null);

            doThrow(new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND))
                    .when(settlementValidator).validateTotals(null);

            assertThatThrownBy(() ->
                    dailySettlementTasklet.execute(contribution, context)
            ).isInstanceOf(BusinessException.class)
                    .hasMessage(ErrorCode.SETTLEMENT_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("saveDaily() 내부 예외 발생 시 전파된다")
        void execute_fail_saveDailyThrows() {
            Long userId = 1L;
            SettlementTotals totals = BatchHelperData.totals();

            given(settlementRepository.findDistinctUserIds())
                    .willReturn(List.of(userId));

            given(settlementRepository.sumTotals(anyLong(), any(), any()))
                    .willReturn(totals);

            doNothing().when(settlementValidator).validateTotals(totals);

            doThrow(new RuntimeException("save fail"))
                    .when(settlementSaver)
                    .saveDaily(anyLong(), any(), any());

            assertThatThrownBy(() ->
                    dailySettlementTasklet.execute(contribution, context)
            ).isInstanceOf(RuntimeException.class)
                    .hasMessage("save fail");
        }

        @Test
        @DisplayName("repository 에러 발생 시 예외 전파된다")
        void execute_fail_repoError() {

            given(settlementRepository.findDistinctUserIds())
                    .willThrow(new RuntimeException("DB error"));

            assertThatThrownBy(() ->
                    dailySettlementTasklet.execute(contribution, context)
            ).isInstanceOf(RuntimeException.class)
                    .hasMessage("DB error");
        }
    }
}