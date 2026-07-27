package com.odcc.tienda.modules.sales.application.port.out;

import com.odcc.tienda.modules.sales.application.model.Customer;
import com.odcc.tienda.modules.sales.application.query.ListCustomersQuery;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepositoryPort {
    boolean existsByCode(String customerCode);

    boolean existsByCodeAndIdNot(String customerCode, UUID customerId);

    Customer save(Customer customer);

    Optional<Customer> findById(UUID customerId);

    List<Customer> findAll(ListCustomersQuery query);
}
