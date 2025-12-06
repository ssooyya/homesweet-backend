package com.homesweet.homesweetback.domain.settlement.service;

import com.homesweet.homesweetback.common.exception.BusinessException;
import com.homesweet.homesweetback.common.exception.ErrorCode;
import com.homesweet.homesweetback.domain.auth.entity.User;
import com.homesweet.homesweetback.domain.grade.service.GradeService;
import com.homesweet.homesweetback.domain.order.entity.Order;
import com.homesweet.homesweetback.domain.settlement.repository.querydsl.CustomSettlementRepository;
import com.homesweet.homesweetback.domain.settlement.util.ExtractedSeller;
import com.homesweet.homesweetback.domain.settlement.util.calculator.SettlementCalculator;
import com.homesweet.homesweetback.domain.settlement.dto.response.SettlementResponse;
import com.homesweet.homesweetback.domain.settlement.entity.Settlement;
import com.homesweet.homesweetback.domain.settlement.repository.SettlementRepository;
import com.homesweet.homesweetback.domain.settlement.util.SettlementStatusUtil;
import com.homesweet.homesweetback.domain.settlement.util.ValidateAndDateRange;
import com.homesweet.homesweetback.domain.settlement.validation.SettlementValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {
    private final SettlementRepository settlementRepository;
    private final SettlementValidator settlementValidator;
    private final SettlementCalculator settlementCalculator;
    private final ExtractedSeller extractedSeller;
    // 주문 확정(결제 완료)시 정산 생성
    @Transactional
    public void createSettlement(Order order) {
        // 1. 주문 검증
        settlementValidator.validateOrder(order);

        // 2. 판매자 정보 가져오기
        User seller = extractedSeller.extractSeller(order);

        // 3. 판매자 검증
        settlementValidator.validateSeller(seller);

        // 4. 정산 금액 계산(메소드 분리)
        SettlementCalculator.Result result = settlementCalculator.getResult(order, seller);

        // 5. 계산된 금액 저장
        Settlement settlement = Settlement.builder()
                .orderId(order.getId())
                .salesAmount(result.totalAmount())
                .fee(result.fee())
                .vat(result.vat())
                .refundAmount(result.refundAmount())
                .settlementAmount(result.settlementAmount())
                .settlementDate(LocalDateTime.now())
                .settlementStatus("PENDING")
                .userId(seller.getId())
                .build();
        settlementRepository.save(settlement);
    }

    // 전체 주문건별 정산내역 상태별 조회(기간 + 상태)
    @Transactional(readOnly = true)
    public Page<SettlementResponse> getSettlementStatusList(Long userId, LocalDateTime startDate, LocalDateTime endDate, String settlementStatus, Pageable pageable) {
        // 상태
        String normal = SettlementStatusUtil.normalizeStatus(settlementStatus);
        // 기간 범위 검증
        ValidateAndDateRange.DateRange range = ValidateAndDateRange.validateAndDateRange(startDate, endDate);

        return settlementRepository.findBySettlement(userId, range.start(), range.end(), normal, pageable);
    }
    // 주문 취소시 환불 금액 반영 및 정산 금액 변경
//    @Transactional
//    public void orderCanceled(Order order) {
//        Long orderId = order.getId();
//
//        // 1. 정산 데이터 확인
//        Settlement settlement = settlementRepository.findByOrderId(orderId)
//                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND));
//        // 2.주문취소시 검증
//        settlementValidator.validateCanceled(settlement, order);
//        // 3. 환불금액 계산 (판매금액 + 부가세 - 수수료)
//        BigDecimal refundAmount = settlementCalculator.refundResult(settlement);
//
//        // 4. 환불 금액 반영 및 정산 금액 재계산
//        int updated = customSettlementRepository.applyRefundAmount(orderId, refundAmount);
//
//        // 5. 반영 실패시
//        if(updated != 1){
//            throw new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND);
//        }
//    }
}