package com.odcc.tienda.modules.reports.adapter.out.persistence.jdbc;

import com.odcc.tienda.modules.reports.application.model.CashSummaryReport;
import com.odcc.tienda.modules.reports.application.model.CustomerSalesReport;
import com.odcc.tienda.modules.reports.application.model.InventoryMovementReport;
import com.odcc.tienda.modules.reports.application.model.LowStockReport;
import com.odcc.tienda.modules.reports.application.model.SalesSummaryReport;
import com.odcc.tienda.modules.reports.application.model.TopProductReport;
import com.odcc.tienda.modules.reports.application.port.out.ReportRepositoryPort;
import com.odcc.tienda.modules.reports.application.query.ReportFilter;
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

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public SalesSummaryReport salesSummary(ReportFilter filter) {
        return jdbc.queryForObject("""
            SELECT COUNT(*) AS ticket_count,
                   COALESCE(SUM(subtotal), 0) AS subtotal,
                   COALESCE(SUM(discount_total), 0) AS discount_total,
                   COALESCE(SUM(tax_total), 0) AS tax_total,
                   COALESCE(SUM(total), 0) AS total,
                   COALESCE(AVG(total), 0) AS average_ticket
            FROM sales.sales_orders so
            WHERE so.status = 'CONFIRMED'
              AND (CAST(:from AS date) IS NULL OR so.created_at >= CAST(:from AS date))
              AND (CAST(:to AS date) IS NULL OR so.created_at < CAST(:to AS date) + INTERVAL '1 day')
              AND (CAST(:branchId AS uuid) IS NULL OR so.branch_id = CAST(:branchId AS uuid))
              AND (CAST(:warehouseId AS uuid) IS NULL OR so.warehouse_id = CAST(:warehouseId AS uuid))
              AND (CAST(:customerId AS uuid) IS NULL OR so.customer_id = CAST(:customerId AS uuid))
            """, params(filter), (rs, rowNum) -> new SalesSummaryReport(
            rs.getLong("ticket_count"),
            rs.getBigDecimal("subtotal"),
            rs.getBigDecimal("discount_total"),
            rs.getBigDecimal("tax_total"),
            rs.getBigDecimal("total"),
            rs.getBigDecimal("average_ticket")
        ));
    }

    @Override
    public List<TopProductReport> topProducts(ReportFilter filter) {
        return jdbc.query("""
            SELECT soi.product_presentation_id,
                   soi.sku_snapshot AS sku,
                   soi.product_name_snapshot AS product_name,
                   COALESCE(SUM(soi.quantity), 0) AS quantity_sold,
                   COALESCE(SUM(soi.line_total), 0) AS gross_amount
            FROM sales.sales_order_items soi
            JOIN sales.sales_orders so ON so.sales_order_id = soi.sales_order_id
            WHERE so.status = 'CONFIRMED'
              AND (CAST(:from AS date) IS NULL OR so.created_at >= CAST(:from AS date))
              AND (CAST(:to AS date) IS NULL OR so.created_at < CAST(:to AS date) + INTERVAL '1 day')
              AND (CAST(:branchId AS uuid) IS NULL OR so.branch_id = CAST(:branchId AS uuid))
              AND (CAST(:warehouseId AS uuid) IS NULL OR so.warehouse_id = CAST(:warehouseId AS uuid))
              AND (CAST(:customerId AS uuid) IS NULL OR so.customer_id = CAST(:customerId AS uuid))
            GROUP BY soi.product_presentation_id, soi.sku_snapshot, soi.product_name_snapshot
            ORDER BY gross_amount DESC, quantity_sold DESC
            LIMIT :limit
            """, params(filter), (rs, rowNum) -> new TopProductReport(
            rs.getObject("product_presentation_id", UUID.class),
            rs.getString("sku"),
            rs.getString("product_name"),
            rs.getBigDecimal("quantity_sold"),
            rs.getBigDecimal("gross_amount")
        ));
    }

    @Override
    public List<CustomerSalesReport> customerSales(ReportFilter filter) {
        return jdbc.query("""
            SELECT so.customer_id,
                   COALESCE(c.customer_code, 'MOSTRADOR') AS customer_code,
                   COALESCE(c.display_name, 'Cliente de mostrador') AS display_name,
                   COUNT(*) AS ticket_count,
                   COALESCE(SUM(so.total), 0) AS total
            FROM sales.sales_orders so
            LEFT JOIN sales.customers c ON c.customer_id = so.customer_id
            WHERE so.status = 'CONFIRMED'
              AND so.customer_id IS NOT NULL
              AND (CAST(:from AS date) IS NULL OR so.created_at >= CAST(:from AS date))
              AND (CAST(:to AS date) IS NULL OR so.created_at < CAST(:to AS date) + INTERVAL '1 day')
              AND (CAST(:branchId AS uuid) IS NULL OR so.branch_id = CAST(:branchId AS uuid))
              AND (CAST(:warehouseId AS uuid) IS NULL OR so.warehouse_id = CAST(:warehouseId AS uuid))
              AND (CAST(:customerId AS uuid) IS NULL OR so.customer_id = CAST(:customerId AS uuid))
            GROUP BY so.customer_id, c.customer_code, c.display_name
            ORDER BY total DESC, ticket_count DESC
            LIMIT :limit
            """, params(filter), (rs, rowNum) -> new CustomerSalesReport(
            rs.getObject("customer_id", UUID.class),
            rs.getString("customer_code"),
            rs.getString("display_name"),
            rs.getLong("ticket_count"),
            rs.getBigDecimal("total")
        ));
    }

    @Override
    public List<LowStockReport> lowStock(ReportFilter filter) {
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
              AND (CAST(:branchId AS uuid) IS NULL OR cs.branch_id = CAST(:branchId AS uuid))
              AND (CAST(:warehouseId AS uuid) IS NULL OR cs.warehouse_id = CAST(:warehouseId AS uuid))
            ORDER BY (pp.minimum_stock - cs.available_quantity) DESC, cs.presentation_name
            LIMIT :limit
            """, params(filter), (rs, rowNum) -> new LowStockReport(
            rs.getObject("warehouse_id", UUID.class),
            rs.getString("warehouse_name"),
            rs.getObject("product_presentation_id", UUID.class),
            rs.getString("sku"),
            rs.getString("presentation_name"),
            rs.getBigDecimal("available_quantity"),
            rs.getBigDecimal("minimum_stock")
        ));
    }

    @Override
    public List<InventoryMovementReport> inventoryMovements(ReportFilter filter) {
        return jdbc.query("""
            SELECT sm.warehouse_id,
                   w.name AS warehouse_name,
                   sm.movement_type,
                   COUNT(DISTINCT sm.stock_movement_id) AS movement_count,
                   COALESCE(SUM(smi.quantity), 0) AS total_quantity
            FROM inventory.stock_movements sm
            JOIN organization.warehouses w ON w.warehouse_id = sm.warehouse_id
            LEFT JOIN inventory.stock_movement_items smi ON smi.stock_movement_id = sm.stock_movement_id
            WHERE sm.status = 'CONFIRMED'
              AND (CAST(:from AS date) IS NULL OR sm.created_at >= CAST(:from AS date))
              AND (CAST(:to AS date) IS NULL OR sm.created_at < CAST(:to AS date) + INTERVAL '1 day')
              AND (CAST(:branchId AS uuid) IS NULL OR sm.branch_id = CAST(:branchId AS uuid))
              AND (CAST(:warehouseId AS uuid) IS NULL OR sm.warehouse_id = CAST(:warehouseId AS uuid))
            GROUP BY sm.warehouse_id, w.name, sm.movement_type
            ORDER BY movement_count DESC, total_quantity DESC
            LIMIT :limit
            """, params(filter), (rs, rowNum) -> new InventoryMovementReport(
            rs.getObject("warehouse_id", UUID.class),
            rs.getString("warehouse_name"),
            rs.getString("movement_type"),
            rs.getLong("movement_count"),
            rs.getBigDecimal("total_quantity")
        ));
    }

    @Override
    public List<CashSummaryReport> cashSummary(ReportFilter filter) {
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
            LEFT JOIN cash.cash_movements cm ON cm.cash_session_id = cs.cash_session_id
            WHERE (CAST(:from AS date) IS NULL OR cs.opened_at >= CAST(:from AS date))
              AND (CAST(:to AS date) IS NULL OR cs.opened_at < CAST(:to AS date) + INTERVAL '1 day')
              AND (CAST(:branchId AS uuid) IS NULL OR cr.branch_id = CAST(:branchId AS uuid))
            GROUP BY cs.cash_session_id, cs.cash_register_id, cr.code, cs.status, cs.opening_amount, cs.expected_amount, cs.counted_amount, cs.difference_amount, cs.opened_at, cs.closed_at
            ORDER BY cs.opened_at DESC
            LIMIT :limit
            """, params(filter), this::mapCashSummary);
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

    private MapSqlParameterSource params(ReportFilter filter) {
        return new MapSqlParameterSource()
            .addValue("from", filter.from())
            .addValue("to", filter.to())
            .addValue("branchId", filter.branchId())
            .addValue("warehouseId", filter.warehouseId())
            .addValue("customerId", filter.customerId())
            .addValue("limit", filter.limit());
    }

    private BigDecimal value(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? BigDecimal.ZERO : value;
    }
}
