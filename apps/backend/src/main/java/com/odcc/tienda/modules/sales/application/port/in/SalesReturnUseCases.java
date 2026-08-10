package com.odcc.tienda.modules.sales.application.port.in;

import com.odcc.tienda.modules.sales.application.command.ConfirmSalesReturnCommand;
import com.odcc.tienda.modules.sales.application.command.CreateSalesReturnCommand;
import com.odcc.tienda.modules.sales.application.model.SalesReturn;

import java.util.UUID;

public interface SalesReturnUseCases {

    SalesReturn create(CreateSalesReturnCommand command);

    SalesReturn getById(UUID returnId, UUID actorUserId);

    SalesReturn confirm(ConfirmSalesReturnCommand command);

    SalesReturn cancel(UUID returnId, UUID actorUserId);
}
