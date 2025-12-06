package com.homesweet.homesweetback.domain.settlement.service;

import com.homesweet.homesweetback.domain.settlement.dto.response.DailySettlementResponse;
import com.homesweet.homesweetback.domain.settlement.entity.DailySettlement;
import com.homesweet.homesweetback.domain.settlement.repository.DailySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.dto.response.EmptyResponse;
import com.homesweet.homesweetback.domain.settlement.util.calculator.SettlementCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor

public class DailySettlementService {
    private final DailySettlementRepository dailySettlementRepository;
    private final EmptyResponse emptyResponse;
    private final SettlementCalculator settlementCalculator;

    private final SettlementCacheService settlementCacheService;

    @Autowired(required = false)
    private Clock clock = Clock.systemDefaultZone();

    // 일별 데이터 조회 (페이지 처리)
    @Transactional(readOnly = true)
    public Page<DailySettlementResponse> getDailySummary(Long userId, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        // 주문일시 날짜[start, end] 끝일 포함
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();

        long t1 = System.currentTimeMillis();
        // 1. 페이지로 정산일시 기준 정산 목록 조회(실제 리스트)
        List<DailySettlementResponse> dailySettlementPage = settlementCacheService.getDailyContentCache(userId, startDate, endDate, pageable);
        Page<DailySettlement> pageinfo = findDailySettlements(userId,pageable, start, end);
        long totalCount = pageinfo.getTotalElements();
        long t2 = System.currentTimeMillis();
        // 2. 기간 전체의 총 주문 건수/총 정산 완료 건수/정산 완료율 계산 -> 미리 계산해두기
        SettlementCalculator.SettlementStats stats = settlementCalculator.calculateStats(userId, startDate, endDate);
        long t3 = System.currentTimeMillis();
        // 3. 데이터가 존재하지 않으면 0 반환
        if (dailySettlementPage.isEmpty()) {
            return emptyResponse.createEmptyDaily(startDate, pageable);
        }
        long t4 = System.currentTimeMillis();
        log.info("[PERF] DB={}ms, CALC={}ms, DTO={}ms",
                (t2 - t1), (t3 - t2), (t4 - t3));
        return new PageImpl<>(dailySettlementPage, pageable, totalCount);   // 전체 페이지수
    }
    public Page<DailySettlement> findDailySettlements(Long userId, Pageable pageable, LocalDateTime start, LocalDateTime end) {
        Page<DailySettlement> dailySettlements = dailySettlementRepository.findByDailySettlementByRange(userId, start, end, pageable);
        return dailySettlements;
    }
}