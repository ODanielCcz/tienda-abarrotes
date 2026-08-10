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
import com.odcc.tienda.shared.support.AllowAllBranchAccessPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

class CustomerServiceTest {

    private static final UUID ACTOR_ID = UUID.randomUUID();

    private CustomerService service;
    private InMemoryBusinessAuditPort auditPort;

    @BeforeEach
    void setUp() {
        auditPort = new InMemoryBusinessAuditPort();
        service = new CustomerService(new InMemoryCustomerRepository(), new ImmediateTransactionRunner(), auditPort, new AllowAllBranchAccessPort());
    }

    @Test
    void shouldCreateCustomerWithNormalizedCodeAndDefaultType() {
        Customer customer = service.create(new CreateCustomerCommand(" cliente-1 ", null, " Cliente Uno ", "cliente@test.com", "555"), ACTOR_ID);

        assertEquals("CLIENTE-1", customer.customerCode());
        assertEquals("PERSON", customer.customerType());
        assertEquals("Cliente Uno", customer.displayName());
        assertEquals("ACTIVE", customer.status());
        assertEquals("CUSTOMER_CREATED", auditPort.events().getFirst().eventType());
    }

    @Test
    void shouldRejectDuplicatedCustomerCode() {
        service.create(new CreateCustomerCommand("CLI", "PERSON", "Cliente Uno", null, null), ACTOR_ID);

        assertThrows(CustomerCodeAlreadyExistsException.class, () ->
            service.create(new CreateCustomerCommand("cli", "PERSON", "Cliente Dos", null, null), ACTOR_ID)
        );
    }

    @Test
    void shouldRejectBlankDisplayName() {
        assertThrows(SalesException.class, () ->
            service.create(new CreateCustomerCommand(null, "PERSON", " ", null, null), ACTOR_ID)
        );
    }

    @Test
    void shouldUpdateCustomerAndAuditIt() {
        Customer customer = service.create(new CreateCustomerCommand("CLI", "PERSON", "Cliente Uno", null, null), ACTOR_ID);

        Customer updated = service.update(new UpdateCustomerCommand(customer.customerId(), "cli-2", "BUSINESS", "Cliente Dos", "dos@test.com", "777"), ACTOR_ID);

        assertEquals("CLI-2", updated.customerCode());
        assertEquals("BUSINESS", updated.customerType());
        assertEquals("CUSTOMER_UPDATED", auditPort.events().get(1).eventType());
    }

    @Test
    void shouldChangeCustomerStatus() {
        Customer customer = service.create(new CreateCustomerCommand("CLI", "PERSON", "Cliente Uno", null, null), ACTOR_ID);

        Customer updated = service.changeStatus(new ChangeCustomerStatusCommand(customer.customerId(), "blocked"), ACTOR_ID);

        assertEquals("BLOCKED", updated.status());
        assertEquals("CUSTOMER_STATUS_CHANGED", auditPort.events().get(1).eventType());
    }
}
