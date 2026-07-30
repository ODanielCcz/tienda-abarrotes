package com.odcc.tienda.modules.sales.application.port.out;

import com.odcc.tienda.modules.sales.application.command.UpsertSalesCartCommand;
import com.odcc.tienda.modules.sales.application.model.SalesCart;

import java.util.UUID;

public interface SalesCartRepositoryPort {
    boolean branchIsActive(UUID branchId);
    boolean customerIsActive(UUID customerId);
    boolean presentationIsActive(UUID productPresentationId);
    SalesCart upsert(UpsertSalesCartCommand command);
}
