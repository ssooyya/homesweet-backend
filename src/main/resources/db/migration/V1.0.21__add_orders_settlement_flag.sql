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

-- settlement 내의 order_id 연관관계 삭제
ALTER TABLE settlement DROP FOREIGN KEY fk_settlement_order;
ALTER TABLE settlement DROP INDEX UK_SETTLEMENT_ORDER;
-- 만약 unique 유지하려면 다시 생성
ALTER TABLE settlement ADD UNIQUE INDEX UK_SETTLEMENT_ORDER (order_id);

ALTER TABLE settlement
MODIFY COLUMN order_id BIGINT NOT NULL;

ALTER TABLE settlement
    CHANGE COLUMN settlement_id settlement_id BINARY(16) NOT NULL;

ALTER TABLE settlement
    DROP PRIMARY KEY;

ALTER TABLE settlement
    ADD PRIMARY KEY (settlement_id);
