package com.homesweet.homesweetback.domain.settlement.batch.step.create;

import com.homesweet.homesweetback.common.exception.BusinessException;
import com.homesweet.homesweetback.common.exception.ErrorCode;
import com.homesweet.homesweetback.domain.settlement.data.HelperData;
import com.homesweet.homesweetback.domain.settlement.entity.Settlement;
import com.homesweet.homesweetback.domain.settlement.repository.SettlementRepository;
import com.homesweet.homesweetback.domain.settlement.validation.SettlementValidator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.Chunk;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("정산 생성 writer 단위 테스트")
@Disabled
class SettlementCreateWriterTest {

    @InjectMocks
    private SettlementCreateWriter settlementCreateWriter;
    @Mock
    private SettlementRepository settlementRepository;
    @Mock
    private SettlementValidator settlementValidator;

    @BeforeEach
    void beforeEach() {
        ReflectionTestUtils.setField(settlementCreateWriter, "settlementRepository", settlementRepository);
    }

    @DisplayName("성공 케이스")
    @Nested
    class success {
        @Test
        @DisplayName("정산 생성 writer 완료")
        void success_writer() {
            // given
            Settlement s1 = HelperData.createSettlement(1L, HelperData.getOrder(HelperData.getUser()));
            Settlement s2 = HelperData.createSettlement(2L, HelperData.getOrder(HelperData.getUser()));
            List<Settlement> settlements = List.of(s1, s2);

            Chunk<Settlement> chunk = new Chunk<>(settlements);

            // when
            settlementCreateWriter.write(chunk);

            // then
            // 1. 검증 호출되었는지 확인
            verify(settlementValidator, times(1)).validateNotEmpty(settlements);
            // 2. saveAll 호출되었는지 확인
            verify(settlementRepository, times(1)).saveAll(settlements);
        }

        @Test
        @DisplayName("여러 Settlement를 writer가 그대로 saveAll()에 전달한다")
        void write_success_multiItems() throws Exception {

            Settlement s1 = HelperData.createSettlement(1L, HelperData.getOrder(HelperData.getUser()));
            Settlement s2 = HelperData.createSettlement(2L, HelperData.getOrder(HelperData.getUser()));
            Settlement s3 = HelperData.createSettlement(3L, HelperData.getOrder(HelperData.getUser()));

            Chunk<Settlement> chunk = Chunk.of(s1, s2, s3);

            doNothing().when(settlementValidator).validateNotEmpty(anyList());

            settlementCreateWriter.write(chunk);

            verify(settlementRepository).saveAll(List.of(s1, s2, s3));
        }
    }

    @Nested
    @DisplayName("실패 케이스")
    class failure {
        @Test
        @DisplayName("빈 리스트면 validateNotEmpty()에서 예외 발생")
        void write_fail_emptyList() {

            Chunk<Settlement> chunk = Chunk.of(); // empty chunk

            doThrow(new IllegalArgumentException("정산 데이터가 비었습니다"))
                    .when(settlementValidator)
                    .validateNotEmpty(anyList());

            assertThatThrownBy(() -> settlementCreateWriter.write(chunk))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("정산 데이터가 비었습니다");

            verify(settlementRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("validator가 BusinessException을 던지면 writer는 그대로 예외 전파")
        void write_fail_validatorError() {

            Settlement s1 = HelperData.createSettlement(1L, HelperData.getOrder(HelperData.getUser()));
            Chunk<Settlement> chunk = Chunk.of(s1);

            doThrow(new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND))
                    .when(settlementValidator)
                    .validateNotEmpty(anyList());

            assertThatThrownBy(() -> settlementCreateWriter.write(chunk))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(ErrorCode.SETTLEMENT_NOT_FOUND.getMessage());

            verify(settlementRepository, never()).saveAll(anyList());
        }
    }
}