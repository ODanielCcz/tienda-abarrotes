package com.odcc.tienda.modules.reports.adapter.out.persistence.jdbc;

import com.odcc.tienda.modules.reports.application.model.CashSummaryReport;
import com.odcc.tienda.modules.reports.application.model.CustomerSalesReport;
import com.odcc.tienda.modules.reports.application.model.ExpiringProductReport;
import com.odcc.tienda.modules.reports.application.model.GrossMarginReport;
import com.odcc.tienda.modules.reports.application.model.InventoryMovementReport;
import com.odcc.tienda.modules.reports.application.model.LowStockReport;
import com.odcc.tienda.modules.reports.application.model.ProductProfitabilityReport;
import com.odcc.tienda.modules.reports.application.model.ReturnPeriodReport;
import com.odcc.tienda.modules.reports.application.model.ReturnsSummaryReport;
import com.odcc.tienda.modules.reports.application.model.SalesByPeriodReport;
import com.odcc.tienda.modules.reports.application.model.SalesSummaryReport;
import com.odcc.tienda.modules.reports.application.model.StockValuationItem;
import com.odcc.tienda.modules.reports.application.model.StockValuationReport;
import com.odcc.tienda.modules.reports.application.model.TopProductReport;
import com.odcc.tienda.modules.reports.application.port.out.ReportRepositoryPort;
import com.odcc.tienda.modules.reports.application.query.ExpiringProductsQuery;
import com.odcc.tienda.modules.reports.application.query.PeriodReportQuery;
import com.odcc.tienda.modules.reports.application.query.ProductProfitabilityQuery;
import com.odcc.tienda.modules.reports.application.query.ReportFilter;
import com.odcc.tienda.modules.reports.application.query.StockValuationQuery;
import com.odcc.tienda.shared.application.authorization.BranchScope;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcReportRepositoryAdapter implements ReportRepositoryPort {

    private static final String DEFAULT_TIMEZONE = "America/Mexico_City";

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public SalesSummaryReport salesSummary(ReportFilter filter, BranchScope scope) {
        return jdbc.queryForObject("""
            SELECT COUNT(*) AS ticket_count,
                   COALESCE(SUM(subtotal), 0) AS subtotal,
                   COALESCE(SUM(discount_total), 0) AS discount_total,
                   COALESCE(SUM(tax_total), 0) AS tax_total,
                   COALESCE(SUM(total), 0) AS total,
                   COALESCE(AVG(total), 0) AS average_ticket
            FROM sales.sales_orders so
            JOIN organization.branches b ON b.branch_id = so.branch_id
            WHERE so.status IN ('CONFIRMED', 'PARTIALLY_RETURNED', 'RETURNED')
              AND (so.created_at AT TIME ZONE
                   CASE WHEN CAST(:branchId AS uuid) IS NULL
                        THEN :defaultTimezone ELSE b.timezone END)::date
                  BETWEEN CAST(:from AS date) AND CAST(:to AS date)
              AND (CAST(:globalAccess AS boolean) OR so.branch_id IN (:branchIds))
              AND (CAST(:branchId AS uuid) IS NULL OR so.branch_id = CAST(:branchId AS uuid))
              AND (CAST(:warehouseId AS uuid) IS NULL OR so.warehouse_id = CAST(:warehouseId AS uuid))
              AND (CAST(:customerId AS uuid) IS NULL OR so.customer_id = CAST(:customerId AS uuid))
            """, params(filter, scope), (rs, rowNum) -> new SalesSummaryReport(
            rs.getLong("ticket_count"),
            value(rs, "subtotal"),
            value(rs, "discount_total"),
            value(rs, "tax_total"),
            value(rs, "total"),
            value(rs, "average_ticket")
        ));
    }

    @Override
    public List<TopProductReport> topProducts(ReportFilter filter, BranchScope scope) {
        return jdbc.query("""
            SELECT soi.product_presentation_id,
                   soi.sku_snapshot AS sku,
                   soi.product_name_snapshot AS product_name,
                   COALESCE(SUM(soi.quantity), 0) AS quantity_sold,
                   COALESCE(SUM(soi.line_total), 0) AS gross_amount
            FROM sales.sales_order_items soi
            JOIN sales.sales_orders so ON so.sales_order_id = soi.sales_order_id
            JOIN organization.branches b ON b.branch_id = so.branch_id
            WHERE so.status IN ('CONFIRMED', 'PARTIALLY_RETURNED', 'RETURNED')
              AND (so.created_at AT TIME ZONE
                   CASE WHEN CAST(:branchId AS uuid) IS NULL
                        THEN :defaultTimezone ELSE b.timezone END)::date
                  BETWEEN CAST(:from AS date) AND CAST(:to AS date)
              AND (CAST(:globalAccess AS boolean) OR so.branch_id IN (:branchIds))
              AND (CAST(:branchId AS uuid) IS NULL OR so.branch_id = CAST(:branchId AS uuid))
              AND (CAST(:warehouseId AS uuid) IS NULL OR so.warehouse_id = CAST(:warehouseId AS uuid))
              AND (CAST(:customerId AS uuid) IS NULL OR so.customer_id = CAST(:customerId AS uuid))
            GROUP BY soi.product_presentation_id, soi.sku_snapshot, soi.product_name_snapshot
            ORDER BY gross_amount DESC, quantity_sold DESC
            LIMIT :limit
            """, params(filter, scope), (rs, rowNum) -> new TopProductReport(
            rs.getObject("product_presentation_id", UUID.class),
            rs.getString("sku"),
            rs.getString("product_name"),
            value(rs, "quantity_sold"),
            value(rs, "gross_amount")
        ));
    }

    @Override
    public List<CustomerSalesReport> customerSales(ReportFilter filter, BranchScope scope) {
        return jdbc.query("""
            SELECT so.customer_id,
                   COALESCE(c.customer_code, 'MOSTRADOR') AS customer_code,
                   COALESCE(c.display_name, 'Cliente de mostrador') AS display_name,
                   COUNT(*) AS ticket_count,
                   COALESCE(SUM(so.total), 0) AS total
            FROM sales.sales_orders so
            LEFT JOIN sales.customers c ON c.customer_id = so.customer_id
            JOIN organization.branches b ON b.branch_id = so.branch_id
            WHERE so.status IN ('CONFIRMED', 'PARTIALLY_RETURNED', 'RETURNED')
              AND so.customer_id IS NOT NULL
              AND (so.created_at AT TIME ZONE
                   CASE WHEN CAST(:branchId AS uuid) IS NULL
                        THEN :defaultTimezone ELSE b.timezone END)::date
                  BETWEEN CAST(:from AS date) AND CAST(:to AS date)
              AND (CAST(:globalAccess AS boolean) OR so.branch_id IN (:branchIds))
              AND (CAST(:branchId AS uuid) IS NULL OR so.branch_id = CAST(:branchId AS uuid))
              AND (CAST(:warehouseId AS uuid) IS NULL OR so.warehouse_id = CAST(:warehouseId AS uuid))
              AND (CAST(:customerId AS uuid) IS NULL OR so.customer_id = CAST(:customerId AS uuid))
            GROUP BY so.customer_id, c.customer_code, c.display_name
            ORDER BY total DESC, ticket_count DESC
            LIMIT :limit
            """, params(filter, scope), (rs, rowNum) -> new CustomerSalesReport(
            rs.getObject("customer_id", UUID.class),
            rs.getString("customer_code"),
            rs.getString("display_name"),
            rs.getLong("ticket_count"),
            value(rs, "total")
        ));
    }

    @Override
    public List<LowStockReport> lowStock(ReportFilter filter, BranchScope scope) {
        return jdbc.query("""
            SELECT cs.warehouse_id,
                   w.name AS warehouse_name,
                   cs.product_presentation_id,
                   cs.sku,
                   cs.presentation_name,
                   cs.available_quantity,
                   pp.minimum_stock
            FROM inventory.current_stock cs
            JOIN organization.warehouses w ON w.warehouse_id = cs.warehouse_id
            JOIN catalog.product_presentations pp ON pp.product_presentation_id = cs.product_presentation_id
            WHERE pp.minimum_stock > 0
              AND cs.available_quantity <= pp.minimum_stock
              AND (CAST(:globalAccess AS boolean) OR cs.branch_id IN (:branchIds))
              AND (CAST(:branchId AS uuid) IS NULL OR cs.branch_id = CAST(:branchId AS uuid))
              AND (CAST(:warehouseId AS uuid) IS NULL OR cs.warehouse_id = CAST(:warehouseId AS uuid))
            ORDER BY (pp.minimum_stock - cs.available_quantity) DESC, cs.presentation_name
            LIMIT :limit
            """, params(filter, scope), (rs, rowNum) -> new LowStockReport(
            rs.getObject("warehouse_id", UUID.class),
            rs.getString("warehouse_name"),
            rs.getObject("product_presentation_id", UUID.class),
            rs.getString("sku"),
            rs.getString("presentation_name"),
            value(rs, "available_quantity"),
            value(rs, "minimum_stock")
        ));
    }

    @Override
    public List<InventoryMovementReport> inventoryMovements(ReportFilter filter, BranchScope scope) {
        return jdbc.query("""
            SELECT sm.warehouse_id,
                   w.name AS warehouse_name,
                   sm.movement_type,
                   COUNT(DISTINCT sm.stock_movement_id) AS movement_count,
                   COALESCE(SUM(smi.quantity), 0) AS total_quantity
            FROM inventory.stock_movements sm
            JOIN organization.warehouses w ON w.warehouse_id = sm.warehouse_id
            JOIN organization.branches b ON b.branch_id = sm.branch_id
            LEFT JOIN inventory.stock_movement_items smi ON smi.stock_movement_id = sm.stock_movement_id
            WHERE sm.status = 'CONFIRMED'
              AND (sm.created_at AT TIME ZONE
                   CASE WHEN CAST(:branchId AS uuid) IS NULL
                        THEN :defaultTimezone ELSE b.timezone END)::date
                  BETWEEN CAST(:from AS date) AND CAST(:to AS date)
              AND (CAST(:globalAccess AS boolean) OR sm.branch_id IN (:branchIds))
              AND (CAST(:branchId AS uuid) IS NULL OR sm.branch_id = CAST(:branchId AS uuid))
              AND (CAST(:warehouseId AS uuid) IS NULL OR sm.warehouse_id = CAST(:warehouseId AS uuid))
            GROUP BY sm.warehouse_id, w.name, sm.movement_type
            ORDER BY movement_count DESC, total_quantity DESC
            LIMIT :limit
            """, params(filter, scope), (rs, rowNum) -> new InventoryMovementReport(
            rs.getObject("warehouse_id", UUID.class),
            rs.getString("warehouse_name"),
            rs.getString("movement_type"),
            rs.getLong("movement_count"),
            value(rs, "total_quantity")
        ));
    }

    @Override
    public List<CashSummaryReport> cashSummary(ReportFilter filter, BranchScope scope) {
        return jdbc.query("""
            SELECT cs.cash_session_id,
                   cs.cash_register_id,
                   cr.code AS cash_register_code,
                   cs.status,
                   cs.opening_amount,
                   cs.expected_amount,
                   cs.counted_amount,
                   cs.difference_amount,
                   COALESCE(SUM(CASE WHEN TRIM(cm.direction) = 'IN' THEN cm.amount ELSE 0 END), 0) AS cash_in,
                   COALESCE(SUM(CASE WHEN TRIM(cm.direction) = 'OUT' THEN cm.amount ELSE 0 END), 0) AS cash_out,
                   cs.opened_at,
                   cs.closed_at
            FROM cash.cash_sessions cs
            JOIN organization.cash_registers cr ON cr.cash_register_id = cs.cash_register_id
            JOIN organization.branches b ON b.branch_id = cr.branch_id
            LEFT JOIN cash.cash_movements cm ON cm.cash_session_id = cs.cash_session_id
            WHERE (cs.opened_at AT TIME ZONE
                   CASE WHEN CAST(:branchId AS uuid) IS NULL
                        THEN :defaultTimezone ELSE b.timezone END)::date
                  BETWEEN CAST(:from AS date) AND CAST(:to AS date)
              AND (CAST(:globalAccess AS boolean) OR cr.branch_id IN (:branchIds))
              AND (CAST(:branchId AS uuid) IS NULL OR cr.branch_id = CAST(:branchId AS uuid))
            GROUP BY cs.cash_session_id, cs.cash_register_id, cr.code, cs.status,
                     cs.opening_amount, cs.expected_amount, cs.counted_amount,
                     cs.difference_amount, cs.opened_at, cs.closed_at
            ORDER BY cs.opened_at DESC
            LIMIT :limit
            """, params(filter, scope), this::mapCashSummary);
    }

    @Override
    public List<SalesByPeriodReport> salesByPeriod(PeriodReportQuery query, BranchScope scope) {
        return jdbc.query("""
            WITH events AS (
                SELECT date_trunc(
                           :groupUnit,
                           so.created_at AT TIME ZONE
                           CASE WHEN CAST(:branchId AS uuid) IS NULL
                                THEN :defaultTimezone ELSE b.timezone END
                       )::date AS period_start,
                       1::bigint AS ticket_count,
                       so.subtotal AS gross_subtotal,
                       so.discount_total,
                       so.tax_total,
                       so.total AS gross_sales,
                       0::numeric AS returns_amount
                FROM sales.sales_orders so
                JOIN organization.branches b ON b.branch_id = so.branch_id
                WHERE so.status IN ('CONFIRMED', 'PARTIALLY_RETURNED', 'RETURNED')
                  AND (so.created_at AT TIME ZONE
                       CASE WHEN CAST(:branchId AS uuid) IS NULL
                            THEN :defaultTimezone ELSE b.timezone END)::date
                      BETWEEN CAST(:from AS date) AND CAST(:to AS date)
                  AND (CAST(:globalAccess AS boolean) OR so.branch_id IN (:branchIds))
                  AND (CAST(:branchId AS uuid) IS NULL OR so.branch_id = CAST(:branchId AS uuid))
                  AND (CAST(:warehouseId AS uuid) IS NULL OR so.warehouse_id = CAST(:warehouseId AS uuid))
                  AND (CAST(:customerId AS uuid) IS NULL OR so.customer_id = CAST(:customerId AS uuid))

                UNION ALL

                SELECT date_trunc(
                           :groupUnit,
                           r.confirmed_at AT TIME ZONE
                           CASE WHEN CAST(:branchId AS uuid) IS NULL
                                THEN :defaultTimezone ELSE b.timezone END
                       )::date AS period_start,
                       0::bigint AS ticket_count,
                       0::numeric AS gross_subtotal,
                       0::numeric AS discount_total,
                       0::numeric AS tax_total,
                       0::numeric AS gross_sales,
                       r.total AS returns_amount
                FROM sales.returns r
                JOIN sales.sales_orders so ON so.sales_order_id = r.sales_order_id
                JOIN organization.branches b ON b.branch_id = so.branch_id
                WHERE r.status = 'CONFIRMED'
                  AND (r.confirmed_at AT TIME ZONE
                       CASE WHEN CAST(:branchId AS uuid) IS NULL
                            THEN :defaultTimezone ELSE b.timezone END)::date
                      BETWEEN CAST(:from AS date) AND CAST(:to AS date)
                  AND (CAST(:globalAccess AS boolean) OR so.branch_id IN (:branchIds))
                  AND (CAST(:branchId AS uuid) IS NULL OR so.branch_id = CAST(:branchId AS uuid))
                  AND (CAST(:warehouseId AS uuid) IS NULL OR so.warehouse_id = CAST(:warehouseId AS uuid))
                  AND (CAST(:customerId AS uuid) IS NULL OR so.customer_id = CAST(:customerId AS uuid))
            )
            SELECT period_start,
                   CASE :groupUnit
                       WHEN 'day' THEN period_start
                       WHEN 'week' THEN period_start + 6
                       ELSE (period_start + INTERVAL '1 month - 1 day')::date
                   END AS period_end,
                   SUM(ticket_count) AS ticket_count,
                   COALESCE(SUM(gross_subtotal), 0) AS gross_subtotal,
                   COALESCE(SUM(discount_total), 0) AS discount_total,
                   COALESCE(SUM(tax_total), 0) AS tax_total,
                   COALESCE(SUM(gross_sales), 0) AS gross_sales,
                   COALESCE(SUM(returns_amount), 0) AS returns_amount,
                   COALESCE(SUM(gross_sales), 0) - COALESCE(SUM(returns_amount), 0) AS net_sales
            FROM events
            GROUP BY period_start
            ORDER BY period_start
            """, params(query, scope), (rs, rowNum) -> new SalesByPeriodReport(
            rs.getObject("period_start", java.time.LocalDate.class),
            rs.getObject("period_end", java.time.LocalDate.class),
            rs.getLong("ticket_count"),
            value(rs, "gross_subtotal"),
            value(rs, "discount_total"),
            value(rs, "tax_total"),
            value(rs, "gross_sales"),
            value(rs, "returns_amount"),
            value(rs, "net_sales")
        ));
    }

    @Override
    public GrossMarginReport grossMargin(PeriodReportQuery query, BranchScope scope) {
        return jdbc.queryForObject("""
            WITH metrics AS (
                SELECT COALESCE(SUM(soi.line_total - soi.tax_amount), 0) AS gross_revenue,
                       0::numeric AS returned_revenue,
                       COALESCE(SUM(soi.quantity * soi.unit_cost), 0) AS gross_cost,
                       0::numeric AS returned_cost
                FROM sales.sales_order_items soi
                JOIN sales.sales_orders so ON so.sales_order_id = soi.sales_order_id
                JOIN organization.branches b ON b.branch_id = so.branch_id
                WHERE so.status IN ('CONFIRMED', 'PARTIALLY_RETURNED', 'RETURNED')
                  AND (so.created_at AT TIME ZONE
                       CASE WHEN CAST(:branchId AS uuid) IS NULL
                            THEN :defaultTimezone ELSE b.timezone END)::date
                      BETWEEN CAST(:from AS date) AND CAST(:to AS date)
                  AND (CAST(:globalAccess AS boolean) OR so.branch_id IN (:branchIds))
                  AND (CAST(:branchId AS uuid) IS NULL OR so.branch_id = CAST(:branchId AS uuid))
                  AND (CAST(:warehouseId AS uuid) IS NULL OR so.warehouse_id = CAST(:warehouseId AS uuid))
                  AND (CAST(:customerId AS uuid) IS NULL OR so.customer_id = CAST(:customerId AS uuid))

                UNION ALL

                SELECT 0::numeric AS gross_revenue,
                       COALESCE(SUM(
                           CASE WHEN soi.line_total = 0 THEN 0
                                ELSE ri.amount * (soi.line_total - soi.tax_amount) / soi.line_total END
                       ), 0) AS returned_revenue,
                       0::numeric AS gross_cost,
                       COALESCE(SUM(ri.quantity * soi.unit_cost), 0) AS returned_cost
                FROM sales.return_items ri
                JOIN sales.returns r ON r.return_id = ri.return_id
                JOIN sales.sales_order_items soi ON soi.sales_order_item_id = ri.sales_order_item_id
                JOIN sales.sales_orders so ON so.sales_order_id = r.sales_order_id
                JOIN organization.branches b ON b.branch_id = so.branch_id
                WHERE r.status = 'CONFIRMED'
                  AND (r.confirmed_at AT TIME ZONE
                       CASE WHEN CAST(:branchId AS uuid) IS NULL
                            THEN :defaultTimezone ELSE b.timezone END)::date
                      BETWEEN CAST(:from AS date) AND CAST(:to AS date)
                  AND (CAST(:globalAccess AS boolean) OR so.branch_id IN (:branchIds))
                  AND (CAST(:branchId AS uuid) IS NULL OR so.branch_id = CAST(:branchId AS uuid))
                  AND (CAST(:warehouseId AS uuid) IS NULL OR so.warehouse_id = CAST(:warehouseId AS uuid))
                  AND (CAST(:customerId AS uuid) IS NULL OR so.customer_id = CAST(:customerId AS uuid))
            ),
            totals AS (
                SELECT COALESCE(SUM(gross_revenue), 0) AS gross_revenue,
                       COALESCE(SUM(returned_revenue), 0) AS returned_revenue,
                       COALESCE(SUM(gross_cost), 0) AS gross_cost,
                       COALESCE(SUM(returned_cost), 0) AS returned_cost
                FROM metrics
            ),
            calculated AS (
                SELECT gross_revenue,
                       returned_revenue,
                       gross_revenue - returned_revenue AS net_revenue,
                       gross_cost,
                       returned_cost,
                       gross_cost - returned_cost AS net_cost
                FROM totals
            )
            SELECT gross_revenue,
                   returned_revenue,
                   net_revenue,
                   gross_cost,
                   returned_cost,
                   net_cost,
                   net_revenue - net_cost AS gross_profit,
                   CASE WHEN net_revenue = 0 THEN 0
                        ELSE ROUND(((net_revenue - net_cost) / net_revenue) * 100, 4) END
                       AS gross_margin_percent
            FROM calculated
            """, params(query, scope), (rs, rowNum) -> new GrossMarginReport(
            value(rs, "gross_revenue"),
            value(rs, "returned_revenue"),
            value(rs, "net_revenue"),
            value(rs, "gross_cost"),
            value(rs, "returned_cost"),
            value(rs, "net_cost"),
            value(rs, "gross_profit"),
            value(rs, "gross_margin_percent")
        ));
    }

    @Override
    public List<ProductProfitabilityReport> productProfitability(ProductProfitabilityQuery query, BranchScope scope) {
        return jdbc.query("""
            WITH events AS (
                SELECT soi.product_presentation_id,
                       soi.sku_snapshot AS sku,
                       soi.product_name_snapshot AS product_name,
                       soi.quantity AS quantity_sold,
                       0::numeric AS quantity_returned,
                       soi.line_total - soi.tax_amount AS gross_revenue,
                       0::numeric AS returned_revenue,
                       soi.quantity * soi.unit_cost AS gross_cost,
                       0::numeric AS returned_cost
                FROM sales.sales_order_items soi
                JOIN sales.sales_orders so ON so.sales_order_id = soi.sales_order_id
                JOIN organization.branches b ON b.branch_id = so.branch_id
                WHERE so.status IN ('CONFIRMED', 'PARTIALLY_RETURNED', 'RETURNED')
                  AND (so.created_at AT TIME ZONE
                       CASE WHEN CAST(:branchId AS uuid) IS NULL
                            THEN :defaultTimezone ELSE b.timezone END)::date
                      BETWEEN CAST(:from AS date) AND CAST(:to AS date)
                  AND (CAST(:globalAccess AS boolean) OR so.branch_id IN (:branchIds))
                  AND (CAST(:branchId AS uuid) IS NULL OR so.branch_id = CAST(:branchId AS uuid))
                  AND (CAST(:warehouseId AS uuid) IS NULL OR so.warehouse_id = CAST(:warehouseId AS uuid))
                  AND (CAST(:customerId AS uuid) IS NULL OR so.customer_id = CAST(:customerId AS uuid))

                UNION ALL

                SELECT soi.product_presentation_id,
                       soi.sku_snapshot AS sku,
                       soi.product_name_snapshot AS product_name,
                       0::numeric AS quantity_sold,
                       ri.quantity AS quantity_returned,
                       0::numeric AS gross_revenue,
                       CASE WHEN soi.line_total = 0 THEN 0
                            ELSE ri.amount * (soi.line_total - soi.tax_amount) / soi.line_total END
                           AS returned_revenue,
                       0::numeric AS gross_cost,
                       ri.quantity * soi.unit_cost AS returned_cost
                FROM sales.return_items ri
                JOIN sales.returns r ON r.return_id = ri.return_id
                JOIN sales.sales_order_items soi ON soi.sales_order_item_id = ri.sales_order_item_id
                JOIN sales.sales_orders so ON so.sales_order_id = r.sales_order_id
                JOIN organization.branches b ON b.branch_id = so.branch_id
                WHERE r.status = 'CONFIRMED'
                  AND (r.confirmed_at AT TIME ZONE
                       CASE WHEN CAST(:branchId AS uuid) IS NULL
                            THEN :defaultTimezone ELSE b.timezone END)::date
                      BETWEEN CAST(:from AS date) AND CAST(:to AS date)
                  AND (CAST(:globalAccess AS boolean) OR so.branch_id IN (:branchIds))
                  AND (CAST(:branchId AS uuid) IS NULL OR so.branch_id = CAST(:branchId AS uuid))
                  AND (CAST(:warehouseId AS uuid) IS NULL OR so.warehouse_id = CAST(:warehouseId AS uuid))
                  AND (CAST(:customerId AS uuid) IS NULL OR so.customer_id = CAST(:customerId AS uuid))
            ),
            aggregated AS (
                SELECT product_presentation_id,
                       MAX(sku) AS sku,
                       MAX(product_name) AS product_name,
                       COALESCE(SUM(quantity_sold), 0) AS quantity_sold,
                       COALESCE(SUM(quantity_returned), 0) AS quantity_returned,
                       COALESCE(SUM(gross_revenue), 0) - COALESCE(SUM(returned_revenue), 0)
                           AS net_revenue,
                       COALESCE(SUM(gross_cost), 0) - COALESCE(SUM(returned_cost), 0)
                           AS net_cost
                FROM events
                GROUP BY product_presentation_id
            )
            SELECT product_presentation_id,
                   sku,
                   product_name,
                   quantity_sold,
                   quantity_returned,
                   quantity_sold - quantity_returned AS net_quantity,
                   net_revenue,
                   net_cost,
                   net_revenue - net_cost AS gross_profit,
                   CASE WHEN net_revenue = 0 THEN 0
                        ELSE ROUND(((net_revenue - net_cost) / net_revenue) * 100, 4) END
                       AS gross_margin_percent
            FROM aggregated
            ORDER BY gross_profit DESC, net_revenue DESC, product_name
            LIMIT :limit
            """, params(query, scope), (rs, rowNum) -> new ProductProfitabilityReport(
            rs.getObject("product_presentation_id", UUID.class),
            rs.getString("sku"),
            rs.getString("product_name"),
            value(rs, "quantity_sold"),
            value(rs, "quantity_returned"),
            value(rs, "net_quantity"),
            value(rs, "net_revenue"),
            value(rs, "net_cost"),
            value(rs, "gross_profit"),
            value(rs, "gross_margin_percent")
        ));
    }

    @Override
    public StockValuationReport stockValuation(StockValuationQuery query, BranchScope scope) {
        List<ValuationRow> rows = jdbc.query("""
            WITH valued AS (
                SELECT sb.warehouse_id,
                       w.name AS warehouse_name,
                       sb.product_presentation_id,
                       pp.sku,
                       p.name AS product_name,
                       pp.name AS presentation_name,
                       sb.on_hand_quantity,
                       sb.available_quantity,
                       sb.average_unit_cost,
                       ROUND(sb.on_hand_quantity * sb.average_unit_cost, 4) AS stock_value
                FROM inventory.stock_balances sb
                JOIN organization.warehouses w ON w.warehouse_id = sb.warehouse_id
                JOIN catalog.product_presentations pp
                  ON pp.product_presentation_id = sb.product_presentation_id
                JOIN catalog.products p ON p.product_id = pp.product_id
                WHERE sb.on_hand_quantity > 0
                  AND (CAST(:globalAccess AS boolean) OR w.branch_id IN (:branchIds))
                  AND (CAST(:branchId AS uuid) IS NULL OR w.branch_id = CAST(:branchId AS uuid))
                  AND (CAST(:warehouseId AS uuid) IS NULL OR sb.warehouse_id = CAST(:warehouseId AS uuid))
            )
            SELECT valued.*,
                   SUM(on_hand_quantity) OVER () AS total_on_hand_quantity,
                   SUM(stock_value) OVER () AS total_stock_value
            FROM valued
            ORDER BY stock_value DESC, product_name, presentation_name
            LIMIT :limit
            """, params(query, scope), (rs, rowNum) -> new ValuationRow(
            new StockValuationItem(
                rs.getObject("warehouse_id", UUID.class),
                rs.getString("warehouse_name"),
                rs.getObject("product_presentation_id", UUID.class),
                rs.getString("sku"),
                rs.getString("product_name"),
                rs.getString("presentation_name"),
                value(rs, "on_hand_quantity"),
                value(rs, "available_quantity"),
                value(rs, "average_unit_cost"),
                value(rs, "stock_value")
            ),
            value(rs, "total_on_hand_quantity"),
            value(rs, "total_stock_value")
        ));

        if (rows.isEmpty()) {
            return new StockValuationReport(BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        }
        return new StockValuationReport(
            rows.getFirst().totalOnHandQuantity(),
            rows.getFirst().totalStockValue(),
            rows.stream().map(ValuationRow::item).toList()
        );
    }

    @Override
    public List<ExpiringProductReport> expiringProducts(ExpiringProductsQuery query, BranchScope scope) {
        return jdbc.query("""
            SELECT l.lot_id,
                   l.lot_number,
                   lb.warehouse_id,
                   w.name AS warehouse_name,
                   l.product_presentation_id,
                   pp.sku,
                   p.name AS product_name,
                   pp.name AS presentation_name,
                   l.expires_at,
                   l.expires_at - CAST(:asOf AS date) AS days_remaining,
                   lb.on_hand_quantity,
                   lb.available_quantity,
                   sb.average_unit_cost,
                   ROUND(lb.on_hand_quantity * sb.average_unit_cost, 4) AS estimated_value
            FROM inventory.lots l
            JOIN inventory.lot_balances lb ON lb.lot_id = l.lot_id
            JOIN inventory.stock_balances sb
              ON sb.warehouse_id = lb.warehouse_id
             AND sb.product_presentation_id = l.product_presentation_id
            JOIN organization.warehouses w ON w.warehouse_id = lb.warehouse_id
            JOIN catalog.product_presentations pp
              ON pp.product_presentation_id = l.product_presentation_id
            JOIN catalog.products p ON p.product_id = pp.product_id
            WHERE l.status = 'ACTIVE'
              AND l.expires_at IS NOT NULL
              AND lb.on_hand_quantity > 0
              AND l.expires_at BETWEEN CAST(:asOf AS date)
                                   AND CAST(:asOf AS date) + CAST(:days AS integer)
              AND (CAST(:globalAccess AS boolean) OR w.branch_id IN (:branchIds))
              AND (CAST(:branchId AS uuid) IS NULL OR w.branch_id = CAST(:branchId AS uuid))
              AND (CAST(:warehouseId AS uuid) IS NULL OR lb.warehouse_id = CAST(:warehouseId AS uuid))
            ORDER BY l.expires_at, product_name, presentation_name, l.lot_number
            LIMIT :limit
            """, params(query, scope), (rs, rowNum) -> new ExpiringProductReport(
            rs.getObject("lot_id", UUID.class),
            rs.getString("lot_number"),
            rs.getObject("warehouse_id", UUID.class),
            rs.getString("warehouse_name"),
            rs.getObject("product_presentation_id", UUID.class),
            rs.getString("sku"),
            rs.getString("product_name"),
            rs.getString("presentation_name"),
            rs.getObject("expires_at", java.time.LocalDate.class),
            rs.getInt("days_remaining"),
            value(rs, "on_hand_quantity"),
            value(rs, "available_quantity"),
            value(rs, "average_unit_cost"),
            value(rs, "estimated_value")
        ));
    }

    @Override
    public ReturnsSummaryReport returnsSummary(PeriodReportQuery query, BranchScope scope) {
        List<ReturnSummaryRow> rows = jdbc.query("""
            WITH return_base AS (
                SELECT r.return_id,
                       date_trunc(
                           :groupUnit,
                           r.confirmed_at AT TIME ZONE
                           CASE WHEN CAST(:branchId AS uuid) IS NULL
                                THEN :defaultTimezone ELSE b.timezone END
                       )::date AS period_start,
                       r.total AS returned_amount,
                       COALESCE(SUM(ri.quantity), 0) AS returned_quantity
                FROM sales.returns r
                JOIN sales.sales_orders so ON so.sales_order_id = r.sales_order_id
                JOIN organization.branches b ON b.branch_id = so.branch_id
                LEFT JOIN sales.return_items ri ON ri.return_id = r.return_id
                WHERE r.status = 'CONFIRMED'
                  AND (r.confirmed_at AT TIME ZONE
                       CASE WHEN CAST(:branchId AS uuid) IS NULL
                            THEN :defaultTimezone ELSE b.timezone END)::date
                      BETWEEN CAST(:from AS date) AND CAST(:to AS date)
                  AND (CAST(:globalAccess AS boolean) OR so.branch_id IN (:branchIds))
                  AND (CAST(:branchId AS uuid) IS NULL OR so.branch_id = CAST(:branchId AS uuid))
                  AND (CAST(:warehouseId AS uuid) IS NULL OR so.warehouse_id = CAST(:warehouseId AS uuid))
                  AND (CAST(:customerId AS uuid) IS NULL OR so.customer_id = CAST(:customerId AS uuid))
                GROUP BY r.return_id, period_start, r.total
            ),
            periods AS (
                SELECT period_start,
                       COUNT(*) AS return_count,
                       COALESCE(SUM(returned_quantity), 0) AS returned_quantity,
                       COALESCE(SUM(returned_amount), 0) AS returned_amount
                FROM return_base
                GROUP BY period_start
            )
            SELECT period_start,
                   CASE :groupUnit
                       WHEN 'day' THEN period_start
                       WHEN 'week' THEN period_start + 6
                       ELSE (period_start + INTERVAL '1 month - 1 day')::date
                   END AS period_end,
                   return_count,
                   returned_quantity,
                   returned_amount,
                   SUM(return_count) OVER () AS total_return_count,
                   SUM(returned_quantity) OVER () AS total_returned_quantity,
                   SUM(returned_amount) OVER () AS total_returned_amount
            FROM periods
            ORDER BY period_start
            """, params(query, scope), (rs, rowNum) -> new ReturnSummaryRow(
            new ReturnPeriodReport(
                rs.getObject("period_start", java.time.LocalDate.class),
                rs.getObject("period_end", java.time.LocalDate.class),
                rs.getLong("return_count"),
                value(rs, "returned_quantity"),
                value(rs, "returned_amount")
            ),
            rs.getLong("total_return_count"),
            value(rs, "total_returned_quantity"),
            value(rs, "total_returned_amount")
        ));

        if (rows.isEmpty()) {
            return new ReturnsSummaryReport(0, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        }
        return new ReturnsSummaryReport(
            rows.getFirst().totalReturnCount(),
            rows.getFirst().totalReturnedQuantity(),
            rows.getFirst().totalReturnedAmount(),
            rows.stream().map(ReturnSummaryRow::period).toList()
        );
    }

    private CashSummaryReport mapCashSummary(ResultSet rs, int rowNum) throws SQLException {
        return new CashSummaryReport(
            rs.getObject("cash_session_id", UUID.class),
            rs.getObject("cash_register_id", UUID.class),
            rs.getString("cash_register_code"),
            rs.getString("status"),
            value(rs, "opening_amount"),
            value(rs, "expected_amount"),
            value(rs, "counted_amount"),
            value(rs, "difference_amount"),
            value(rs, "cash_in"),
            value(rs, "cash_out"),
            rs.getTimestamp("opened_at").toInstant(),
            rs.getTimestamp("closed_at") == null ? null : rs.getTimestamp("closed_at").toInstant()
        );
    }

    private MapSqlParameterSource params(ReportFilter filter, BranchScope scope) {
        return new MapSqlParameterSource()
            .addValue("from", filter.from())
            .addValue("to", filter.to())
            .addValue("branchId", filter.branchId())
            .addValue("warehouseId", filter.warehouseId())
            .addValue("customerId", filter.customerId())
            .addValue("limit", filter.limit())
            .addValue("defaultTimezone", DEFAULT_TIMEZONE)
            .addValue("globalAccess", scope.globalAccess())
            .addValue("branchIds", branchIds(scope));
    }

    private MapSqlParameterSource params(PeriodReportQuery query, BranchScope scope) {
        return new MapSqlParameterSource()
            .addValue("from", query.from())
            .addValue("to", query.to())
            .addValue("branchId", query.branchId())
            .addValue("warehouseId", query.warehouseId())
            .addValue("customerId", query.customerId())
            .addValue("groupUnit", sqlUnit(query.groupBy()))
            .addValue("defaultTimezone", DEFAULT_TIMEZONE)
            .addValue("globalAccess", scope.globalAccess())
            .addValue("branchIds", branchIds(scope));
    }

    private MapSqlParameterSource params(ProductProfitabilityQuery query, BranchScope scope) {
        return new MapSqlParameterSource()
            .addValue("from", query.from())
            .addValue("to", query.to())
            .addValue("branchId", query.branchId())
            .addValue("warehouseId", query.warehouseId())
            .addValue("customerId", query.customerId())
            .addValue("limit", query.limit())
            .addValue("defaultTimezone", DEFAULT_TIMEZONE)
            .addValue("globalAccess", scope.globalAccess())
            .addValue("branchIds", branchIds(scope));
    }

    private MapSqlParameterSource params(StockValuationQuery query, BranchScope scope) {
        return new MapSqlParameterSource()
            .addValue("branchId", query.branchId())
            .addValue("warehouseId", query.warehouseId())
            .addValue("limit", query.limit())
            .addValue("globalAccess", scope.globalAccess())
            .addValue("branchIds", branchIds(scope));
    }

    private MapSqlParameterSource params(ExpiringProductsQuery query, BranchScope scope) {
        return new MapSqlParameterSource()
            .addValue("branchId", query.branchId())
            .addValue("warehouseId", query.warehouseId())
            .addValue("days", query.days())
            .addValue("limit", query.limit())
            .addValue("asOf", query.asOf())
            .addValue("globalAccess", scope.globalAccess())
            .addValue("branchIds", branchIds(scope));
    }

    private List<UUID> branchIds(BranchScope scope) {
        return scope.branchIds().isEmpty() ? List.of(new UUID(0L, 0L)) : List.copyOf(scope.branchIds());
    }

    private String sqlUnit(com.odcc.tienda.modules.reports.application.query.ReportGroupBy groupBy) {
        return switch (groupBy) {
            case DAY -> "day";
            case WEEK -> "week";
            case MONTH -> "month";
        };
    }
    private BigDecimal value(ResultSet rs, String column) throws SQLException {
        BigDecimal result = rs.getBigDecimal(column);
        return result == null ? BigDecimal.ZERO : result;
    }

    private record ValuationRow(
        StockValuationItem item,
        BigDecimal totalOnHandQuantity,
        BigDecimal totalStockValue
    ) {
    }

    private record ReturnSummaryRow(
        ReturnPeriodReport period,
        long totalReturnCount,
        BigDecimal totalReturnedQuantity,
        BigDecimal totalReturnedAmount
    ) {
    }
}
