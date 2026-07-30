package com.odcc.tienda.modules.sales.application.port.in;

import com.odcc.tienda.modules.sales.application.command.UpsertSalesCartCommand;
import com.odcc.tienda.modules.sales.application.model.SalesCart;

public interface SalesCartUseCases {
    SalesCart upsert(UpsertSalesCartCommand command);
}
