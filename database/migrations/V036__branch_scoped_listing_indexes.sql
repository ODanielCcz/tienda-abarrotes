CREATE INDEX IF NOT EXISTS idx_sales_orders_branch_created_id
    ON sales.sales_orders (branch_id, created_at DESC, sales_order_id);

CREATE INDEX IF NOT EXISTS idx_purchases_branch_purchased_id
    ON purchasing.purchases (branch_id, purchased_at DESC, purchase_id);
