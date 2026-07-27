package com.odcc.tienda.modules.sales.application.port.out;

import com.odcc.tienda.modules.sales.application.command.ConfirmSalesReturnCommand;
import com.odcc.tienda.modules.sales.application.command.CreateSalesReturnCommand;
import com.odcc.tienda.modules.sales.application.model.SalesReturn;

import java.util.Optional;
import java.util.UUID;

public interface SalesReturnRepositoryPort {

    SalesReturn createDraft(CreateSalesReturnCommand command);

    Optional<SalesReturn> findById(UUID returnId);

    SalesReturn confirm(ConfirmSalesReturnCommand command);

    SalesReturn cancel(UUID returnId);
}