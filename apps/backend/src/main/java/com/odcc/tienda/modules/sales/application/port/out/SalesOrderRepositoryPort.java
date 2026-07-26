package com.odcc.tienda.modules.sales.application.port.out;

import com.odcc.tienda.modules.sales.application.command.CreateSalesOrderCommand;
import com.odcc.tienda.modules.sales.application.model.SalesOrder;
import com.odcc.tienda.modules.sales.application.query.ListSalesOrdersQuery;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalesOrderRepositoryPort {
    Optional<SalesOrder> findByIdempotencyKey(UUID idempotencyKey, String fingerprint);

    boolean existsByIdempotencyKeyWithDifferentFingerprint(UUID idempotencyKey, String fingerprint);

    SalesOrder createConfirmed(CreateSalesOrderCommand command, String fingerprint);

    Optional<SalesOrder> findById(UUID salesOrderId);

    List<SalesOrder> findAll(ListSalesOrdersQuery query);

    SalesOrder cancel(UUID salesOrderId);
}
