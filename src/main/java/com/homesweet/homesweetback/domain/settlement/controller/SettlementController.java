package com.homesweet.homesweetback.domain.settlement.controller;

import com.homesweet.homesweetback.domain.order.dto.response.OrderReadyResponse;
import com.homesweet.homesweetback.domain.settlement.dto.response.*;
import com.homesweet.homesweetback.domain.settlement.entity.Settlement;
import com.homesweet.homesweetback.domain.settlement.service.*;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.internal.util.StringUtils;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/settlement")
public class SettlementController {
    private final DailySettlementService dailySettlementService;
    private final MonthlySettlementService monthlySettlementService;
    private final WeeklySettlementService weeklySettlementService;
    private final YearlySettlementService yearlySettlementService;
    private final SettlementService settlementService;
    private final JobLauncher jobLauncher;
    private final Job settlementJob;

    // 1. 전체 주문건별 +  정산 상태별 조회
    @GetMapping("/all/{userId}")
    public ResponseEntity<Page<SettlementResponse>> getSettlementStatus(@PathVariable Long userId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDate startDate, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDate endDate, @RequestParam(required = false) String settlementStatus, Pageable pageable) {
        // 기본 기간 : 30일
        LocalDate defaultEnd = (endDate != null) ? endDate : LocalDate.now();
        LocalDate defaultStart = (startDate != null) ? startDate : defaultEnd.minusDays(29);

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime endEx = endDate.plusDays(1).atStartOfDay();
        String status = StringUtils.hasText(settlementStatus) ? settlementStatus.trim() : null;
        // 기간 + 상태 조회
        Page<SettlementResponse> res = settlementService.getSettlementStatusList(userId, start, endEx, status, pageable);
        return ResponseEntity.ok(res);
    }

    // 2. 일별 정산내역 조회
    @GetMapping("/daily/{userId}")
    public ResponseEntity<Page<DailySettlementResponse>> getDailySummary(@PathVariable Long userId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate, Pageable pageable) {
        Page<DailySettlementResponse> res = dailySettlementService.getDailySummary(userId, startDate, endDate, pageable);
        return ResponseEntity.ok(res);
    }

    // 3. 주별 정산내역 조회
    @GetMapping("/weekly/{userId}")
    public ResponseEntity<Page<WeeklySettlementResponse>> getWeeklySummary(@PathVariable Long userId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate, Pageable pageable) {
        Page<WeeklySettlementResponse> res = weeklySettlementService.getWeeklySummary(userId, startDate, endDate, pageable);
        return ResponseEntity.ok(res);
    }

    // 4. 월별 정산내역 조회
    @GetMapping("/monthly/{userId}")
    public ResponseEntity<Page<MonthlySettlementResponse>> getMonthlySummary(@PathVariable Long userId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate , Pageable pageable) {
        Page<MonthlySettlementResponse> res = monthlySettlementService.getMonthlySummary(userId, startDate, endDate, pageable);
        return ResponseEntity.ok(res);
    }

    // 5. 연별 정산내역 조회
    @GetMapping("/yearly/{userId}")
    public ResponseEntity<Page<YearlySettlementResponse>> getYearlySummary(@PathVariable Long userId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate, Pageable pageable) {
        Page<YearlySettlementResponse> yearSummary = yearlySettlementService.getYearlySummary(userId, startDate, endDate, pageable);
        return ResponseEntity.ok(yearSummary);
    }

//    @PostMapping("/daily/{userId}/generate")
//    public ResponseEntity<Void> getDailySettlement(@PathVariable Long userId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDateTime startDate, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDateTime endDate) {
//        dailySettlementService.getSettlement(userId, startDate, endDate);
//        return ResponseEntity.ok().build();
//    }
//
//    @PostMapping("/weekly/{userId}/generate")
//    public ResponseEntity<Void> getWeeklySettlement(@PathVariable Long userId, @RequestParam LocalDate weekStart, @RequestParam LocalDate weekEnd) {
//        weeklySettlementService.getWeeklySettlement(userId, weekStart, weekEnd);
//        return ResponseEntity.ok().build();
//    }
//
//    @PostMapping("/monthly/{userId}/generate")
//    public ResponseEntity<Void> generateMonthly(@PathVariable Long userId) {
//        monthlySettlementService.getMonthlySettlement(userId);
//        return ResponseEntity.ok().build();
//    }
//
//    @PostMapping("/yearly/{userId}/generate")
//    public ResponseEntity<Void> getYearlySettlement(@PathVariable Long userId) {
//        yearlySettlementService.getYearlySettlement(userId);
//        return ResponseEntity.ok().build();
//    }
    // 부하테스트용
    @PostMapping("/batch/run")
    public ResponseEntity<String> runJob(){
        LocalDateTime cutoff = LocalDateTime.now(); // 오늘 전체

        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("cutoff", cutoff.toString())
                    .addLong("timestamp", System.currentTimeMillis()) // 매번 새로운 JobInstance
                    .toJobParameters();

            JobExecution jobExecution = jobLauncher.run(settlementJob, params);

            return ResponseEntity.ok("SETTLEMENT BATCH STARTED (executionId=" + jobExecution.getId() + ")");

        } catch (JobExecutionAlreadyRunningException e) {
            return ResponseEntity.status(409).body("SETTLEMENT BATCH ALREADY RUNNING");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("SETTLEMENT BATCH FAILED: " + e.getMessage());
        }
    }
}
