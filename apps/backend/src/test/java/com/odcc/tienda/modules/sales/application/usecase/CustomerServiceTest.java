package com.odcc.tienda.modules.sales.application.usecase;

import com.odcc.tienda.modules.sales.application.command.ChangeCustomerStatusCommand;
import com.odcc.tienda.modules.sales.application.command.CreateCustomerCommand;
import com.odcc.tienda.modules.sales.application.command.UpdateCustomerCommand;
import com.odcc.tienda.modules.sales.application.exception.CustomerCodeAlreadyExistsException;
import com.odcc.tienda.modules.sales.application.exception.SalesException;
import com.odcc.tienda.modules.sales.application.model.Customer;
import com.odcc.tienda.modules.sales.support.InMemoryCustomerRepository;
import com.odcc.tienda.shared.support.ImmediateTransactionRunner;
import com.odcc.tienda.shared.support.InMemoryBusinessAuditPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CustomerServiceTest {

    private CustomerService service;
    private InMemoryBusinessAuditPort auditPort;

    @BeforeEach
    void setUp() {
        auditPort = new InMemoryBusinessAuditPort();
        service = new CustomerService(new InMemoryCustomerRepository(), new ImmediateTransactionRunner(), auditPort);
    }

    @Test
    void shouldCreateCustomerWithNormalizedCodeAndDefaultType() {
        Customer customer = service.create(new CreateCustomerCommand(" cliente-1 ", null, " Cliente Uno ", "cliente@test.com", "555"));

        assertEquals("CLIENTE-1", customer.customerCode());
        assertEquals("PERSON", customer.customerType());
        assertEquals("Cliente Uno", customer.displayName());
        assertEquals("ACTIVE", customer.status());
        assertEquals("CUSTOMER_CREATED", auditPort.events().getFirst().eventType());
    }

    @Test
    void shouldRejectDuplicatedCustomerCode() {
        service.create(new CreateCustomerCommand("CLI", "PERSON", "Cliente Uno", null, null));

        assertThrows(CustomerCodeAlreadyExistsException.class, () ->
            service.create(new CreateCustomerCommand("cli", "PERSON", "Cliente Dos", null, null))
        );
    }

    @Test
    void shouldRejectBlankDisplayName() {
        assertThrows(SalesException.class, () ->
            service.create(new CreateCustomerCommand(null, "PERSON", " ", null, null))
        );
    }

    @Test
    void shouldUpdateCustomerAndAuditIt() {
        Customer customer = service.create(new CreateCustomerCommand("CLI", "PERSON", "Cliente Uno", null, null));

        Customer updated = service.update(new UpdateCustomerCommand(customer.customerId(), "cli-2", "BUSINESS", "Cliente Dos", "dos@test.com", "777"));

        assertEquals("CLI-2", updated.customerCode());
        assertEquals("BUSINESS", updated.customerType());
        assertEquals("CUSTOMER_UPDATED", auditPort.events().get(1).eventType());
    }

    @Test
    void shouldChangeCustomerStatus() {
        Customer customer = service.create(new CreateCustomerCommand("CLI", "PERSON", "Cliente Uno", null, null));

        Customer updated = service.changeStatus(new ChangeCustomerStatusCommand(customer.customerId(), "blocked"));

        assertEquals("BLOCKED", updated.status());
        assertEquals("CUSTOMER_STATUS_CHANGED", auditPort.events().get(1).eventType());
    }
}
