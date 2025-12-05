package com.homesweet.homesweetback.domain.settlement.util;

import com.homesweet.homesweetback.common.exception.BusinessException;
import com.homesweet.homesweetback.common.exception.ErrorCode;
import com.homesweet.homesweetback.domain.auth.entity.User;
import com.homesweet.homesweetback.domain.auth.service.UserService;
import com.homesweet.homesweetback.domain.order.entity.Order;
import com.homesweet.homesweetback.domain.settlement.entity.Settlement;
import com.homesweet.homesweetback.domain.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 판매자 정보 추출
@Component
@RequiredArgsConstructor
public class ExtractedSeller {
    private final SettlementRepository settlementRepository;

    public User extractSeller(Order order) {
        if(order.getOrderItems() == null || order.getOrderItems().isEmpty()){
            throw new BusinessException(ErrorCode.ORDER_ITEMS_EMPTY);
        }

        return order.getOrderItems()
                .stream()
                .findFirst()
                .map(item -> item.getSku().getProduct().getSeller())
                .orElseThrow(() -> new BusinessException(ErrorCode.SELLER_NOT_FOUND));
//        return settlementRepository.findBySellerId(order.getId()).orElseThrow(() -> new BusinessException(ErrorCode.SELLER_NOT_FOUND));
    }
}
