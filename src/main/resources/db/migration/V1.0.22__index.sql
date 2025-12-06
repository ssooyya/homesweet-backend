
ALTER TABLE orders
    DROP INDEX idx_orders_user_status,
    DROP INDEX idx_orders_ordered_at,
    DROP INDEX IDX_ORDERS_STATUS_ORDERED_AT,
    DROP INDEX idx_orders_settlement_processed,
    DROP INDEX idx_order_settlement_query;


CREATE INDEX idx_settlement_cursor
ON orders (settlement_processed, order_status, ordered_at, order_id);

CREATE INDEX idx_settlement_user_date
    ON settlement (user_id, settlement_date);

CREATE INDEX idx_daily_user_date_desc
    ON daily_settlements (user_id, settlement_date DESC);
CREATE INDEX idx_weekly_user_week_desc
    ON weekly_settlements (user_id, week_start_date DESC);
CREATE INDEX idx_monthly_user_week_desc
    ON monthly_settlements (user_id, month_value DESC);
CREATE INDEX idx_yearly_user_week_desc
    ON yearly_settlements (user_id, year_value DESC);

DROP INDEX ux_daily_settlements ON daily_settlements;
DROP INDEX ux_weekly_settlements ON weekly_settlements;
DROP INDEX ux_monthly_settlements ON monthly_settlements;
DROP INDEX ux_yearly_settlements ON yearly_settlements;
