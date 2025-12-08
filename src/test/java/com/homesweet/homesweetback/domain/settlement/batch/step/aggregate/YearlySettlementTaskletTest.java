package com.homesweet.homesweetback.domain.settlement.batch.step.aggregate;

import com.homesweet.homesweetback.common.exception.BusinessException;
import com.homesweet.homesweetback.common.exception.ErrorCode;
import com.homesweet.homesweetback.domain.grade.service.GradeService;
import com.homesweet.homesweetback.domain.settlement.repository.SettlementRepository;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("연별 tasklet 단위 테스트")
class YearlySettlementTaskletTest {

    @InjectMocks
    private YearlySettlementTasklet yearlySettlementTasklet;

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
    void setUp() {
        ReflectionTestUtils.setField(
                yearlySettlementTasklet,
                "cutoffString",
                "2025-11-25T00:00:00"
        );
    }

    @Nested
    @DisplayName("성공 케이스")
    class Success {

        @Test
        @DisplayName("단일 사용자 연별 집계 성공")
        void success_singleUser() {
            Long userId = 10L;

            given(settlementRepository.findDistinctUserIds())
                    .willReturn(List.of(userId));

            given(settlementRepository.sumTotals(anyLong(), any(), any()))
                    .willReturn(SettlementTotals.empty());

            doNothing().when(settlementValidator).validateTotals(any());

            RepeatStatus status =
                    yearlySettlementTasklet.execute(contribution, context);

            assertThat(status).isEqualTo(RepeatStatus.FINISHED);

            verify(settlementSaver).saveYearly(
                    eq(userId),
                    eq((short) 2025),
                    any(SettlementTotals.class)
            );
        }

        @Test
        @DisplayName("여러 사용자 연별 집계 성공")
        void success_multiUsers() {
            List<Long> userIds = List.of(1L, 2L);

            given(settlementRepository.findDistinctUserIds())
                    .willReturn(userIds);

            given(settlementRepository.sumTotals(anyLong(), any(), any()))
                    .willReturn(SettlementTotals.empty());

            doNothing().when(settlementValidator).validateTotals(any());

            yearlySettlementTasklet.execute(contribution, context);

            verify(settlementSaver, times(2))
                    .saveYearly(anyLong(), eq((short) 2025), any());
        }
    }

    // ---------------------------------------------------
    // 실패 케이스
    // ---------------------------------------------------
    @Nested
    @DisplayName("실패 케이스")
    class Failure {

        @Test
        @DisplayName("cutoffString 파싱 실패 → 예외 발생")
        void execute_fail_invalidCutoff() {
            ReflectionTestUtils.setField(
                    yearlySettlementTasklet,
                    "cutoffString",
                    "INVALID_DATE"
            );

            assertThatThrownBy(() ->
                    yearlySettlementTasklet.execute(contribution, context)
            ).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("validator.validateTotals() 에서 BusinessException 발생 → 예외 전파됨")
        void execute_fail_validatorThrows() {
            Long userId = 11L;
            SettlementTotals totals = SettlementTotals.empty();

            given(settlementRepository.findDistinctUserIds())
                    .willReturn(List.of(userId));

            given(settlementRepository.sumTotals(anyLong(), any(), any()))
                    .willReturn(totals);

            doThrow(new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND))
                    .when(settlementValidator).validateTotals(totals);

            assertThatThrownBy(() ->
                    yearlySettlementTasklet.execute(contribution, context)
            ).isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("findDistinctUserIds() 단계에서 예외 → 전파됨")
        void execute_fail_repoError() {
            given(settlementRepository.findDistinctUserIds())
                    .willThrow(new RuntimeException("DB ERROR"));

            assertThatThrownBy(() ->
                    yearlySettlementTasklet.execute(contribution, context)
            ).isInstanceOf(RuntimeException.class)
                    .hasMessage("DB ERROR");
        }

        @Test
        @DisplayName("saveYearly() 중 예외 발생 → 전파됨")
        void execute_fail_saveError() {
            Long userId = 11L;
            SettlementTotals totals = SettlementTotals.empty();

            given(settlementRepository.findDistinctUserIds())
                    .willReturn(List.of(userId));

            given(settlementRepository.sumTotals(anyLong(), any(), any()))
                    .willReturn(totals);

            doNothing().when(settlementValidator).validateTotals(totals);

            doThrow(new RuntimeException("SAVE ERROR"))
                    .when(settlementSaver)
                    .saveYearly(anyLong(), anyShort(), any());

            assertThatThrownBy(() ->
                    yearlySettlementTasklet.execute(contribution, context)
            ).isInstanceOf(RuntimeException.class)
                    .hasMessage("SAVE ERROR");
        }
    }
}
