package com.odcc.tienda.modules.purchasing.application.usecase;

import com.odcc.tienda.modules.purchasing.application.command.ChangeSupplierStatusCommand;
import com.odcc.tienda.modules.purchasing.application.command.CreateSupplierCommand;
import com.odcc.tienda.modules.purchasing.application.exception.SupplierCodeAlreadyExistsException;
import com.odcc.tienda.modules.purchasing.application.model.Supplier;
import com.odcc.tienda.modules.purchasing.support.InMemorySupplierRepository;
import com.odcc.tienda.shared.application.authorization.BranchAccessDeniedException;
import com.odcc.tienda.shared.application.authorization.BranchAccessPort;
import com.odcc.tienda.shared.application.authorization.BranchScope;
import com.odcc.tienda.shared.support.AllowAllBranchAccessPort;
import com.odcc.tienda.shared.support.ImmediateTransactionRunner;
import com.odcc.tienda.shared.support.InMemoryBusinessAuditPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SupplierServiceTest {

    private static final UUID ACTOR_ID = UUID.randomUUID();

    private SupplierService service;
    private InMemoryBusinessAuditPort auditPort;

    @BeforeEach
    void setUp() {
        auditPort = new InMemoryBusinessAuditPort();
        service = new SupplierService(
            new InMemorySupplierRepository(),
            new ImmediateTransactionRunner(),
            auditPort,
            new AllowAllBranchAccessPort()
        );
    }

    @Test
    void shouldCreateSupplierWithNormalizedCode() {
        Supplier supplier = service.create(new CreateSupplierCommand(" proveedor-1 ", "Proveedor Uno", null, null, null, null, 7), ACTOR_ID);

        assertEquals("PROVEEDOR-1", supplier.supplierCode());
        assertEquals("ACTIVE", supplier.status());
        assertEquals(7, supplier.creditDays());
        assertEquals("SUPPLIER_CREATED", auditPort.events().getFirst().eventType());
    }

    @Test
    void shouldRejectDuplicatedSupplierCode() {
        service.create(new CreateSupplierCommand("PROV", "Proveedor Uno", null, null, null, null, 0), ACTOR_ID);

        assertThrows(SupplierCodeAlreadyExistsException.class, () ->
            service.create(new CreateSupplierCommand("prov", "Proveedor Dos", null, null, null, null, 0), ACTOR_ID)
        );
    }

    @Test
    void shouldChangeSupplierStatus() {
        Supplier supplier = service.create(new CreateSupplierCommand("PROV", "Proveedor Uno", null, null, null, null, 0), ACTOR_ID);

        Supplier updated = service.changeStatus(new ChangeSupplierStatusCommand(supplier.supplierId(), "blocked"), ACTOR_ID);

        assertEquals("BLOCKED", updated.status());
        assertEquals("SUPPLIER_STATUS_CHANGED", auditPort.events().get(1).eventType());
    }

    @Test
    void shouldRequireGlobalAccessForSupplierCatalog() {
        BranchAccessPort restrictedAccess = new BranchAccessPort() {
            @Override
            public BranchScope resolveScope(UUID userId) {
                return BranchScope.restricted(Set.of(UUID.randomUUID()));
            }

            @Override
            public void requireAccess(UUID userId, UUID branchId) {
                throw new UnsupportedOperationException();
            }
        };
        service = new SupplierService(
            new InMemorySupplierRepository(),
            new ImmediateTransactionRunner(),
            auditPort,
            restrictedAccess
        );

        assertThrows(BranchAccessDeniedException.class, () ->
            service.create(new CreateSupplierCommand("PROV", "Proveedor Uno", null, null, null, null, 0), ACTOR_ID)
        );
    }
}
