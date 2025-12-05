package com.homesweet.homesweetback.domain.settlement.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor

public class TempSettlementRepository {

    private final JdbcTemplate jdbcTemplate;

    // 1) temp table 생성
    public void createTempTable() {
        jdbcTemplate.execute("""
                    CREATE TEMPORARY TABLE IF NOT EXISTS temp_order_ids (
                        order_id BIGINT PRIMARY KEY
                    ) ENGINE=MEMORY;
                """);
    }

    // 2) chunk 단위로 orderId 저장
    public void insertOrderIds(List<Long> orderIds) {
        jdbcTemplate.batchUpdate(
                "INSERT IGNORE INTO temp_order_ids(order_id) VALUES (?)",
                orderIds,
                1000,
                (ps, id) -> ps.setLong(1, id)
        );
    }

    // 3) 단 한 번 실행되는 UPDATE JOIN
    public int updateOrders() {
        return jdbcTemplate.update("""
            UPDATE orders o
            INNER JOIN temp_order_ids t 
                ON o.order_id = t.order_id
            SET o.settlement_processed = true
        """);
    }

    // 4) 종료 후 삭제
    public void dropTempTable() {
        jdbcTemplate.execute("DROP TEMPORARY TABLE IF EXISTS temp_order_ids");
    }


}
