CREATE TABLE sales.refund_allocations (
    refund_allocation_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    return_id UUID NOT NULL REFERENCES sales.returns(return_id),
    payment_id UUID NOT NULL REFERENCES sales.payments(payment_id),
    amount NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (return_id, payment_id)
);

CREATE INDEX idx_refund_allocations_payment
    ON sales.refund_allocations (payment_id, created_at);

INSERT INTO sales.refund_allocations (
    refund_allocation_id,
    return_id,
    payment_id,
    amount,
    created_at
)
SELECT
    gen_random_uuid(),
    cm.source_id,
    cm.payment_id,
    cm.amount,
    cm.created_at
FROM cash.cash_movements cm
JOIN sales.returns r ON r.return_id = cm.source_id
WHERE cm.movement_type = 'REFUND'
  AND cm.source_type = 'SALES_RETURN'
  AND cm.payment_id IS NOT NULL
ON CONFLICT (return_id, payment_id) DO NOTHING;
