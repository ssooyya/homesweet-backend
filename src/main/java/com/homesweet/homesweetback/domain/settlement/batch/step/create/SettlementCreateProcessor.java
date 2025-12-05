package com.homesweet.homesweetback.domain.settlement.batch.step.create;

import com.homesweet.homesweetback.common.exception.BusinessException;
import com.homesweet.homesweetback.common.exception.ErrorCode;
import com.homesweet.homesweetback.domain.auth.entity.User;
import com.homesweet.homesweetback.domain.order.entity.Order;
import com.homesweet.homesweetback.domain.order.repository.OrderRepository;
import com.homesweet.homesweetback.domain.settlement.dto.response.SettlementCreateDto;
import com.homesweet.homesweetback.domain.settlement.entity.Settlement;
import com.homesweet.homesweetback.domain.settlement.repository.SettlementRepository;
import com.homesweet.homesweetback.domain.settlement.util.ExtractedSeller;
import com.homesweet.homesweetback.domain.settlement.util.calculator.SettlementCalculator;
import com.homesweet.homesweetback.domain.settlement.validation.SettlementValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SettlementCreateProcessor
 * - Reader가 전달한 Order를 Settlement 엔티티로 변환하는 Processor
 * - 수수료 / VAT / 환불 / 최종 정산금액 계산을 수행
 */
@Slf4j
//@Component
@StepScope
@RequiredArgsConstructor
public class SettlementCreateProcessor implements ItemProcessor<SettlementCreateDto, Settlement> {
    private final SettlementCalculator settlementCalculator;
    private final SettlementValidator settlementValidator;
    private final Map<Long, User> sellerCache;

    @Override
    public Settlement process(SettlementCreateDto dto) {
        Long sellerId = dto.sellerId();
        log.info("Processor called for order = {}", dto.orderId());
        // 1. 판매자 추출
//        User seller = extractedSeller.extractSeller(order);
        // 100만번 부르는 중-> map 처리
//        User seller = settlementRepository.findBySellerId(sellerId).orElseThrow(() -> new BusinessException(ErrorCode.SELLER_NOT_FOUND));

        User seller = sellerCache.get(dto.sellerId());

//        settlementValidator.validateSeller(dto.sellerId());
        // Order은 영속성 객체로 가져오기
//        Order order = orderRepository.getReferenceById(dto.orderId());

        // 2. 정산 금액 계산
        SettlementCalculator.Result result = settlementCalculator.getResult(dto, seller);
        settlementValidator.validateResultNotNull(result);

        // 3. Settlement 엔티티 생성
        return Settlement.builder()
                .settlementId(UUID.randomUUID())
                .orderId(dto.orderId())
                .userId(sellerId)
                .salesAmount(result.totalAmount())
                .fee(result.fee())
                .vat(result.vat())
                .refundAmount(result.refundAmount())
                .settlementAmount(result.settlementAmount())
                .settlementStatus("PENDING")
                .settlementDate(LocalDateTime.now())
                .build();
    }
}
