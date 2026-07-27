package com.odcc.tienda.modules.sales.support;

import com.odcc.tienda.modules.sales.application.model.Customer;
import com.odcc.tienda.modules.sales.application.port.out.CustomerRepositoryPort;
import com.odcc.tienda.modules.sales.application.query.ListCustomersQuery;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryCustomerRepository implements CustomerRepositoryPort {

    private final Map<UUID, Customer> customers = new LinkedHashMap<>();

    @Override
    public boolean existsByCode(String customerCode) {
        if (customerCode == null) return false;
        return customers.values().stream().anyMatch(customer -> customerCode.equals(customer.customerCode()));
    }

    @Override
    public boolean existsByCodeAndIdNot(String customerCode, UUID customerId) {
        if (customerCode == null) return false;
        return customers.values().stream()
            .filter(customer -> !customer.customerId().equals(customerId))
            .anyMatch(customer -> customerCode.equals(customer.customerCode()));
    }

    @Override
    public Customer save(Customer customer) {
        customers.put(customer.customerId(), customer);
        return customer;
    }

    @Override
    public Optional<Customer> findById(UUID customerId) {
        return Optional.ofNullable(customers.get(customerId));
    }

    @Override
    public List<Customer> findAll(ListCustomersQuery query) {
        return List.copyOf(customers.values());
    }
}
