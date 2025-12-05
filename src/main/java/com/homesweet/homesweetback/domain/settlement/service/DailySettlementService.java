package com.homesweet.homesweetback.domain.settlement.service;

import com.homesweet.homesweetback.domain.settlement.aggregate.SettlementAggregator;
import com.homesweet.homesweetback.domain.settlement.dto.response.DailySettlementResponse;
import com.homesweet.homesweetback.domain.settlement.entity.DailySettlement;
import com.homesweet.homesweetback.domain.settlement.entity.Settlement;
import com.homesweet.homesweetback.domain.settlement.util.SettlementStatusUpdater;
import com.homesweet.homesweetback.domain.settlement.mapper.SettlementMapper;
import com.homesweet.homesweetback.domain.settlement.repository.DailySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.repository.SettlementRepository;
import com.homesweet.homesweetback.domain.settlement.dto.response.EmptyResponse;
import com.homesweet.homesweetback.domain.settlement.util.calculator.SettlementCalculator;
import com.homesweet.homesweetback.domain.settlement.util.saver.SettlementSaver;
import com.homesweet.homesweetback.domain.settlement.util.vo.SettlementTotals;
import com.homesweet.homesweetback.domain.settlement.validation.SettlementValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor

public class DailySettlementService {
    private final DailySettlementRepository dailySettlementRepository;
    private final SettlementRepository settlementRepository;
    private final EmptyResponse emptyResponse;
    private final SettlementMapper settlementMapper;
    private final SettlementValidator settlementValidator;
    private final SettlementAggregator settlementAggregator;
    private final SettlementStatusUpdater settlementStatusUpdater;
    private final SettlementSaver settlementSaver;
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
//        // 4. 페이지의 실제 리스트를 response에 매핑
//        List<DailySettlementResponse> dailySettlementResponses = settlementMapper.toDailySettlementResponseList(
//                dailySettlementPage,pageable,stats);
        long t4 = System.currentTimeMillis();
        log.info("[PERF] DB={}ms, CALC={}ms, DTO={}ms",
                (t2 - t1), (t3 - t2), (t4 - t3));
        return new PageImpl<>(dailySettlementPage, pageable, totalCount);   // 전체 페이지수
    }
    public Page<DailySettlement> findDailySettlements(Long userId, Pageable pageable, LocalDateTime start, LocalDateTime end) {
        Page<DailySettlement> dailySettlements = dailySettlementRepository.findByDailySettlementByRange(userId, start, end, pageable);
        return dailySettlements;
    }
    // 일별 집계
    @Transactional
    public void getSettlement(Long userId, LocalDateTime dailyStartDate, LocalDateTime dailyEndDate) {
        // 1. 정산일 기준 정산 목록 조회
        List<Settlement> settlements = settlementRepository.findBySettlementDateRange(userId, dailyStartDate, dailyEndDate);

        // 2. 검증
        settlementValidator.validateDaily(settlements);

        // 3. 공통 집계 처리
        Map<LocalDate, SettlementTotals> dailyTotalsMap =
                settlementAggregator.aggregate(
                        settlements,
                        s -> s.getSettlementDate().toLocalDate(),
                        s -> new SettlementTotals(
                                s.getSalesAmount(),
                                s.getFee(),
                                s.getVat(),
                                s.getRefundAmount(),
                                s.getSettlementAmount()
                        )
                );

        // 4. upsert (저장)
        dailyTotalsMap.forEach((date, totals) -> {
            settlementSaver.saveDaily(userId, date, totals);
        });

        // 5. 정산 상태 변경 -> 'COMPLETED'
        settlementStatusUpdater.markDailyCompleted(userId, dailyStartDate, dailyEndDate);
    }
}