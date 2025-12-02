package com.homesweet.homesweetback.domain.settlement.repository;

import com.homesweet.homesweetback.domain.settlement.entity.DailySettlement;
import com.homesweet.homesweetback.domain.settlement.repository.querydsl.CustomDailySettlementRepository;
import com.homesweet.homesweetback.domain.settlement.util.vo.SettlementTotals;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface DailySettlementRepository extends JpaRepository<DailySettlement, Long> {
    // 일별 집계 조회
    @Query("SELECT d FROM DailySettlement d WHERE d.userId = :userId")
    List<DailySettlement> findByDailySettlement(@Param("userId") Long userId);

    // 일별 정산 건수 조회
//    @Query("""
//    SELECT COUNT(d) FROM DailySettlement d
//    WHERE d.userId = :userId
//       AND d.settlementDate >= :startDate
//       AND d.settlementDate < :endDate
//    """)
//    int countByUserIdInRange(Long userId, LocalDate startDate, LocalDate endDate);

    // 일별 집계 조회
    @Query(value = """
    SELECT d FROM DailySettlement d
    WHERE d.userId =:userId
      AND d.settlementDate >= :startDate
      AND d.settlementDate < :endDate
    ORDER BY d.settlementDate DESC
    """, countQuery = """
    SELECT COUNT(d) FROM DailySettlement d
        WHERE d.userId = :userId
        AND d.settlementDate >= :startDate
        AND d.settlementDate < :endDate
    """)
    Page<DailySettlement> findByDailySettlementByRange(@Param("userId") Long userId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate, Pageable pageable);

//    @Modifying
//    @Query(value = """
//    INSERT INTO daily_settlements (
//        user_id, settlement_date, total_sales, total_fee, total_vat, total_refund, total_settlement
//    )
//    VALUES (:userId, :settlementDate, :totalSales, :totalFee, :totalVat, :totalRefund, :totalSettlement
//    ) AS new
//    ON DUPLICATE KEY UPDATE
//        total_sales = new.total_sales,
//        total_fee = new.total_fee,
//        total_vat = new.total_vat,
//        total_refund = new.total_refund,
//        total_settlement = new.total_settlement
//    """, nativeQuery = true)
//    void upsertDaily(
//            @Param("userId") Long userId,
//            @Param("settlementDate") LocalDateTime settlementDate,
//            @Param("totalSales") BigDecimal totalSales,
//            @Param("totalFee") BigDecimal totalFee,
//            @Param("totalVat") BigDecimal totalVat,
//            @Param("totalRefund") BigDecimal totalRefund,
//            @Param("totalSettlement") BigDecimal totalSettlement
//    );
}
