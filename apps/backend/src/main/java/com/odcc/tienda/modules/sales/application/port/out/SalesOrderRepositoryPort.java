package com.odcc.tienda.modules.sales.application.port.out;

import com.odcc.tienda.modules.sales.application.command.CreateSalesOrderCommand;
import com.odcc.tienda.modules.sales.application.model.SalesOrder;
import com.odcc.tienda.modules.sales.application.model.SalesOrderExecution;
import com.odcc.tienda.modules.sales.application.query.ListSalesOrdersQuery;
import com.odcc.tienda.shared.application.authorization.BranchScope;

import java.util.List;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface SalesOrderRepositoryPort {
    Optional<SalesOrder> findByIdempotencyKey(UUID idempotencyKey, String fingerprint);

    boolean existsByIdempotencyKeyWithDifferentFingerprint(UUID idempotencyKey, String fingerprint);

    boolean customerIsActive(UUID customerId);

    Optional<BigDecimal> findCurrentPrice(UUID warehouseId, UUID productPresentationId, String currencyCode);

    UUID findBranchIdByWarehouseId(UUID warehouseId);

    SalesOrder createConfirmed(CreateSalesOrderCommand command, String fingerprint);

    default SalesOrderExecution createConfirmedWithOutcome(
        CreateSalesOrderCommand command,
        String fingerprint
    ) {
        return SalesOrderExecution.created(createConfirmed(command, fingerprint));
    }

    Optional<SalesOrder> findById(UUID salesOrderId);

    List<SalesOrder> findAll(ListSalesOrdersQuery query);

    default List<SalesOrder> findAll(
        ListSalesOrdersQuery query,
        BranchScope scope
    ) {
        List<SalesOrder> orders = findAll(query);
        if (scope == null || scope.globalAccess()) return orders;
        return orders.stream().filter(order -> scope.allows(order.branchId())).toList();
    }

    SalesOrder cancel(UUID salesOrderId);
}
