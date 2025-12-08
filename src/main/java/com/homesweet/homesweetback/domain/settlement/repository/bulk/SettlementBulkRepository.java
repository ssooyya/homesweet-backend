package com.homesweet.homesweetback.domain.settlement.repository.bulk;

import com.homesweet.homesweetback.domain.settlement.entity.Settlement;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SettlementBulkRepository {
    private final JdbcTemplate jdbc;
    public void bulkInsert(List<? extends Settlement> list) {
        if (list.isEmpty()) return;

        jdbc.batchUpdate(
                "INSERT INTO settlement(" +
                        "settlement_id, order_id, user_id, settlement_status, " +
                        "sales_amount, fee, vat, refund_amount, settlement_amount, settlement_date" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?, ?,?, ?)" +
                        "ON DUPLICATE KEY UPDATE " + "settlement_id = settlement_id",
                list,
                1000,
                (ps, s) -> {

                    ps.setBytes(1, uuidToBytes(s.getSettlementId())); // 신규 추가
                    ps.setLong(2, s.getOrderId());
                    ps.setLong(3, s.getUserId());
                    ps.setString(4, s.getSettlementStatus());
                    ps.setBigDecimal(5, s.getSalesAmount());
                    ps.setBigDecimal(6, s.getFee());
                    ps.setBigDecimal(7, s.getVat());
                    ps.setBigDecimal(8, s.getRefundAmount());
                    ps.setBigDecimal(9, s.getSettlementAmount());
                    ps.setTimestamp(10, Timestamp.valueOf(s.getSettlementDate()));
                }
        );
    }
    private byte[] uuidToBytes(UUID uuid) {
        ByteBuffer buf = ByteBuffer.wrap(new byte[16]);
        buf.putLong(uuid.getMostSignificantBits());
        buf.putLong(uuid.getLeastSignificantBits());
        return buf.array();
    }
}
