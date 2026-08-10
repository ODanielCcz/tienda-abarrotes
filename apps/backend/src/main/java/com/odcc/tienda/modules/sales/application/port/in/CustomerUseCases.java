package com.odcc.tienda.modules.sales.application.port.in;

import com.odcc.tienda.modules.sales.application.command.ChangeCustomerStatusCommand;
import com.odcc.tienda.modules.sales.application.command.CreateCustomerCommand;
import com.odcc.tienda.modules.sales.application.command.UpdateCustomerCommand;
import com.odcc.tienda.modules.sales.application.model.Customer;
import com.odcc.tienda.modules.sales.application.query.ListCustomersQuery;

import java.util.List;
import java.util.UUID;

public interface CustomerUseCases {
    Customer create(CreateCustomerCommand command, UUID actorUserId);

    Customer getById(UUID customerId, UUID actorUserId);

    List<Customer> list(ListCustomersQuery query, UUID actorUserId);

    Customer update(UpdateCustomerCommand command, UUID actorUserId);

    Customer changeStatus(ChangeCustomerStatusCommand command, UUID actorUserId);
}
