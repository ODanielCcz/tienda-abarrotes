package com.odcc.tienda.modules.sales.application.port.in;

import com.odcc.tienda.modules.sales.application.command.ChangeCustomerStatusCommand;
import com.odcc.tienda.modules.sales.application.command.CreateCustomerCommand;
import com.odcc.tienda.modules.sales.application.command.UpdateCustomerCommand;
import com.odcc.tienda.modules.sales.application.model.Customer;
import com.odcc.tienda.modules.sales.application.query.ListCustomersQuery;

import java.util.List;
import java.util.UUID;

public interface CustomerUseCases {
    Customer create(CreateCustomerCommand command);

    Customer getById(UUID customerId);

    List<Customer> list(ListCustomersQuery query);

    Customer update(UpdateCustomerCommand command);

    Customer changeStatus(ChangeCustomerStatusCommand command);
}
