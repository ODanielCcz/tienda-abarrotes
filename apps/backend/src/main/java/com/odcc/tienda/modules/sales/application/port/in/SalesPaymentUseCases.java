package com.odcc.tienda.modules.sales.application.port.in;

import com.odcc.tienda.modules.sales.application.command.CreateSalesPaymentCommand;
import com.odcc.tienda.modules.sales.application.model.SalesPayment;

import java.util.List;
import java.util.UUID;

public interface SalesPaymentUseCases {
    SalesPayment create(CreateSalesPaymentCommand command);

    List<SalesPayment> listBySalesOrder(UUID salesOrderId, UUID actorUserId);

    SalesPayment getById(UUID paymentId, UUID actorUserId);

    SalesPayment cancel(UUID paymentId, UUID cancelledBy);
}
