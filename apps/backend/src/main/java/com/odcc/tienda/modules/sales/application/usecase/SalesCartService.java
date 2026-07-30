package com.odcc.tienda.modules.sales.application.usecase;

import com.odcc.tienda.modules.sales.application.command.UpsertSalesCartCommand;
import com.odcc.tienda.modules.sales.application.exception.SalesException;
import com.odcc.tienda.modules.sales.application.model.SalesCart;
import com.odcc.tienda.modules.sales.application.port.in.SalesCartUseCases;
import com.odcc.tienda.modules.sales.application.port.out.SalesCartRepositoryPort;
import com.odcc.tienda.shared.application.audit.BusinessAuditEvent;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public final class SalesCartService implements SalesCartUseCases {

    private final SalesCartRepositoryPort repository;
    private final TransactionRunner transactionRunner;
    private final BusinessAuditPort auditPort;

    @Override
    public SalesCart upsert(UpsertSalesCartCommand command) {
        validate(command);
        UUID cartId = command.cartId() == null ? UUID.randomUUID() : command.cartId();
        UpsertSalesCartCommand normalized = new UpsertSalesCartCommand(
            cartId,
            command.customerId(),
            command.branchId(),
            command.deviceId(),
            command.currencyCode() == null || command.currencyCode().isBlank()
                ? "MXN"
                : command.currencyCode().trim().toUpperCase(),
            command.expiresAt(),
            command.items()
        );
        return transactionRunner.required(() -> {
            SalesCart cart = repository.upsert(normalized);
            auditPort.record(new BusinessAuditEvent(
                "CART_UPSERTED",
                "CART",
                cart.cartId(),
                Map.of(),
                Map.of("branchId", cart.branchId(), "status", cart.status(), "itemCount", cart.items().size()),
                Map.of()
            ));
            return cart;
        });
    }

    private void validate(UpsertSalesCartCommand command) {
        if (command == null) throw new SalesException("El carrito es obligatorio");
        if (command.branchId() == null || !repository.branchIsActive(command.branchId())) {
            throw new SalesException("La sucursal del carrito no existe o no esta activa");
        }
        if (command.deviceId() == null) throw new SalesException("El dispositivo del carrito es obligatorio");
        if (command.customerId() != null && !repository.customerIsActive(command.customerId())) {
            throw new SalesException("El cliente del carrito no existe o no esta activo");
        }
        if (command.expiresAt() != null && !command.expiresAt().isAfter(Instant.now())) {
            throw new SalesException("La expiracion del carrito debe ser futura");
        }
        List<UpsertSalesCartCommand.Item> items = command.items();
        if (items == null || items.isEmpty()) throw new SalesException("El carrito debe incluir items");
        for (UpsertSalesCartCommand.Item item : items) {
            if (item.productPresentationId() == null || !repository.presentationIsActive(item.productPresentationId())) {
                throw new SalesException("La presentacion del carrito no existe o no esta activa");
            }
            if (item.quantity() == null || item.quantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new SalesException("La cantidad del carrito debe ser mayor a cero");
            }
            if (item.unitPriceSnapshot() == null || item.unitPriceSnapshot().compareTo(BigDecimal.ZERO) < 0) {
                throw new SalesException("El precio del carrito no puede ser negativo");
            }
        }
        long uniquePresentations = items.stream().map(UpsertSalesCartCommand.Item::productPresentationId).distinct().count();
        if (uniquePresentations != items.size()) throw new SalesException("El carrito no puede repetir presentaciones");
    }
}
