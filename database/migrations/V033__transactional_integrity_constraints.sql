CREATE UNIQUE INDEX uq_return_item_per_sales_item
    ON sales.return_items (return_id, sales_order_item_id);

ALTER TABLE cash.cash_movements
    ADD COLUMN source_type VARCHAR(50),
    ADD COLUMN source_id UUID;

ALTER TABLE cash.cash_movements
    ADD CONSTRAINT ck_cash_movement_source_pair
    CHECK (
        (source_type IS NULL AND source_id IS NULL)
        OR (source_type IS NOT NULL AND source_id IS NOT NULL)
    );

CREATE UNIQUE INDEX uq_cash_refund_source
    ON cash.cash_movements (source_type, source_id)
    WHERE movement_type = 'REFUND'
      AND source_type IS NOT NULL
      AND source_id IS NOT NULL;
