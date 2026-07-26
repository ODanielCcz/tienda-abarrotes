package com.odcc.tienda.modules.sales.application.port.in;

import com.odcc.tienda.modules.sales.application.command.CreateSalesOrderCommand;
import com.odcc.tienda.modules.sales.application.model.SalesOrder;
import com.odcc.tienda.modules.sales.application.query.ListSalesOrdersQuery;

import java.util.List;
import java.util.UUID;

public interface SalesOrderUseCases {
    SalesOrder create(CreateSalesOrderCommand command);

    SalesOrder getById(UUID salesOrderId);

    List<SalesOrder> list(ListSalesOrdersQuery query);

    SalesOrder cancel(UUID salesOrderId);
}
