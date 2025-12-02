ALTER TABLE orders
ADD INDEX IDX_ORDERS_STATUS_ORDERED_AT (order_status, ordered_at);
-- 정산 여부 확인하는 플래그 추가 ->
ALTER TABLE orders
ADD COLUMN settlement_processed TINYINT(1) NOT NULL DEFAULT 0;

ALTER TABLE orders
ADD INDEX idx_orders_settlement_processed (settlement_processed);

-- 중복 인덱스 삭제
DROP INDEX idx_settlement_order_id ON settlement;

-- settlementDate 단일 인덱스
ALTER TABLE settlement
ADD INDEX idx_settlement_date (settlement_date);
-- user_id + settlement_date
ALTER TABLE settlement
ADD INDEX idx_settlement_user_date (user_id, settlement_date);

