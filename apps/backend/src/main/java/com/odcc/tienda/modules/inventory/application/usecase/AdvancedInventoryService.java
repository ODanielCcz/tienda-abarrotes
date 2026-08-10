package com.odcc.tienda.modules.inventory.application.usecase;

import com.odcc.tienda.modules.inventory.application.command.ConfirmInventoryCountCommand;
import com.odcc.tienda.modules.inventory.application.command.CreateInventoryAdjustmentCommand;
import com.odcc.tienda.modules.inventory.application.command.CreateInventoryCountCommand;
import com.odcc.tienda.modules.inventory.application.command.CreateInventoryTransferCommand;
import com.odcc.tienda.modules.inventory.application.command.CreateReservationCommand;
import com.odcc.tienda.modules.inventory.application.command.ReleaseReservationCommand;
import com.odcc.tienda.modules.inventory.application.exception.InventoryReceiptException;
import com.odcc.tienda.modules.inventory.application.model.InventoryCountView;
import com.odcc.tienda.modules.inventory.application.model.LotView;
import com.odcc.tienda.modules.inventory.application.model.ReservationView;
import com.odcc.tienda.modules.inventory.application.model.StockMovementView;
import com.odcc.tienda.modules.inventory.application.port.in.AdvancedInventoryUseCases;
import com.odcc.tienda.modules.inventory.application.port.out.AdvancedInventoryRepositoryPort;
import com.odcc.tienda.shared.application.audit.BusinessAuditEvent;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import com.odcc.tienda.shared.application.authorization.BranchAccessPort;
import com.odcc.tienda.shared.application.authorization.BranchScope;
import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class AdvancedInventoryService implements AdvancedInventoryUseCases {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final AdvancedInventoryRepositoryPort repository;
    private final TransactionRunner transactionRunner;
    private final BusinessAuditPort auditPort;
    private final BranchAccessPort branchAccessPort;

    @Override
    public StockMovementView adjust(CreateInventoryAdjustmentCommand command) {
        validateAdjustment(command);
        requireWarehouseAccess(command.createdBy(), command.warehouseId());
        return transactionRunner.required(() -> {
            StockMovementView movement = repository.adjust(command);
            auditPort.record(new BusinessAuditEvent("INVENTORY_ADJUSTMENT_CREATED", "STOCK_MOVEMENT", movement.stockMovementId(), Map.of(), Map.of("movementType", movement.movementType()), Map.of()));
            return movement;
        });
    }

    @Override
    public List<StockMovementView> transfer(CreateInventoryTransferCommand command) {
        validateTransfer(command);
        requireWarehouseAccess(command.createdBy(), command.fromWarehouseId());
        requireWarehouseAccess(command.createdBy(), command.toWarehouseId());
        return transactionRunner.required(() -> {
            List<StockMovementView> movements = repository.transfer(command);
            auditPort.record(new BusinessAuditEvent("INVENTORY_TRANSFER_CREATED", "STOCK_MOVEMENT", movements.getFirst().stockMovementId(), Map.of(), Map.of("movements", movements.size()), Map.of()));
            return movements;
        });
    }

    @Override
    public InventoryCountView createCount(CreateInventoryCountCommand command) {
        validateCount(command);
        requireWarehouseAccess(command.startedBy(), command.warehouseId());
        return transactionRunner.required(() -> {
            InventoryCountView count = repository.createCount(command);
            auditPort.record(new BusinessAuditEvent("INVENTORY_COUNT_CREATED", "INVENTORY_COUNT", count.inventoryCountId(), Map.of(), Map.of("warehouseId", count.warehouseId()), Map.of()));
            return count;
        });
    }

    @Override
    public InventoryCountView confirmCount(ConfirmInventoryCountCommand command) {
        if (command == null || command.inventoryCountId() == null) throw new InventoryReceiptException("El conteo es obligatorio");
        if (command.confirmedBy() == null) throw new InventoryReceiptException("El usuario que confirma es obligatorio");
        branchAccessPort.requireAccess(command.confirmedBy(), repository.findBranchIdByCountId(command.inventoryCountId()));
        return transactionRunner.required(() -> {
            InventoryCountView count = repository.confirmCount(command);
            auditPort.record(new BusinessAuditEvent("INVENTORY_COUNT_CONFIRMED", "INVENTORY_COUNT", count.inventoryCountId(), Map.of(), Map.of("status", count.status()), Map.of()));
            return count;
        });
    }

    @Override
    public ReservationView reserve(CreateReservationCommand command) {
        validateReservation(command);
        command.items().stream()
            .map(item -> item.warehouseId())
            .distinct()
            .forEach(warehouseId -> requireWarehouseAccess(command.createdBy(), warehouseId));
        return transactionRunner.required(() -> {
            ReservationView reservation = repository.reserve(command);
            auditPort.record(new BusinessAuditEvent("INVENTORY_RESERVATION_CREATED", "INVENTORY_RESERVATION", reservation.reservationId(), Map.of(), Map.of("status", reservation.status()), Map.of()));
            return reservation;
        });
    }

    @Override
    public ReservationView releaseReservation(ReleaseReservationCommand command) {
        if (command == null || command.reservationId() == null) throw new InventoryReceiptException("La reserva es obligatoria");
        if (command.releasedBy() == null) throw new InventoryReceiptException("El usuario que libera es obligatorio");
        branchAccessPort.requireAccess(command.releasedBy(), repository.findBranchIdByReservationId(command.reservationId()));
        return transactionRunner.required(() -> {
            ReservationView reservation = repository.releaseReservation(command);
            auditPort.record(new BusinessAuditEvent("INVENTORY_RESERVATION_RELEASED", "INVENTORY_RESERVATION", reservation.reservationId(), Map.of(), Map.of("status", reservation.status()), Map.of()));
            return reservation;
        });
    }

    @Override
    public List<LotView> findExpiringLots(LocalDate expiresBefore, UUID actorUserId) {
        LocalDate limit = expiresBefore == null ? LocalDate.now().plusDays(30) : expiresBefore;
        BranchScope scope = branchAccessPort.resolveScope(actorUserId);
        if (scope.globalAccess()) return repository.findExpiringLots(limit);
        return repository.findExpiringLots(limit).stream()
            .filter(lot -> scope.branchIds().contains(repository.findBranchIdByWarehouseId(lot.warehouseId())))
            .toList();
    }

    private void requireWarehouseAccess(UUID actorUserId, UUID warehouseId) {
        branchAccessPort.requireAccess(actorUserId, repository.findBranchIdByWarehouseId(warehouseId));
    }

    private void validateAdjustment(CreateInventoryAdjustmentCommand command) {
        if (command == null) throw new InventoryReceiptException("El ajuste es obligatorio");
        if (command.warehouseId() == null) throw new InventoryReceiptException("El almacen es obligatorio");
        if (command.createdBy() == null) throw new InventoryReceiptException("El usuario es obligatorio");
        if (command.items() == null || command.items().isEmpty()) throw new InventoryReceiptException("El ajuste requiere items");
        command.items().forEach(item -> {
            if (item.productPresentationId() == null) throw new InventoryReceiptException("La presentacion es obligatoria");
            String direction = item.direction() == null ? "" : item.direction().trim().toUpperCase();
            if (!List.of("IN", "OUT").contains(direction)) throw new InventoryReceiptException("La direccion del ajuste debe ser IN u OUT");
            if (item.quantity() == null || item.quantity().compareTo(ZERO) <= 0) throw new InventoryReceiptException("La cantidad debe ser mayor a cero");
        });
    }

    private void validateTransfer(CreateInventoryTransferCommand command) {
        if (command == null) throw new InventoryReceiptException("El traspaso es obligatorio");
        if (command.fromWarehouseId() == null || command.toWarehouseId() == null) throw new InventoryReceiptException("Los almacenes origen y destino son obligatorios");
        if (command.fromWarehouseId().equals(command.toWarehouseId())) throw new InventoryReceiptException("El almacen origen y destino no pueden ser el mismo");
        if (command.createdBy() == null) throw new InventoryReceiptException("El usuario es obligatorio");
        if (command.items() == null || command.items().isEmpty()) throw new InventoryReceiptException("El traspaso requiere items");
        command.items().forEach(item -> {
            if (item.productPresentationId() == null) throw new InventoryReceiptException("La presentacion es obligatoria");
            if (item.quantity() == null || item.quantity().compareTo(ZERO) <= 0) throw new InventoryReceiptException("La cantidad debe ser mayor a cero");
        });
    }

    private void validateCount(CreateInventoryCountCommand command) {
        if (command == null) throw new InventoryReceiptException("El conteo es obligatorio");
        if (command.warehouseId() == null) throw new InventoryReceiptException("El almacen es obligatorio");
        if (command.startedBy() == null) throw new InventoryReceiptException("El usuario es obligatorio");
        if (command.items() == null || command.items().isEmpty()) throw new InventoryReceiptException("El conteo requiere items");
        command.items().forEach(item -> {
            if (item.productPresentationId() == null) throw new InventoryReceiptException("La presentacion es obligatoria");
            if (item.countedQuantity() == null || item.countedQuantity().compareTo(ZERO) < 0) throw new InventoryReceiptException("La cantidad contada no puede ser negativa");
        });
    }

    private void validateReservation(CreateReservationCommand command) {
        if (command == null) throw new InventoryReceiptException("La reserva es obligatoria");
        if (command.sourceType() == null || command.sourceType().isBlank()) throw new InventoryReceiptException("El origen de reserva es obligatorio");
        if (command.sourceId() == null) throw new InventoryReceiptException("El id de origen es obligatorio");
        if (command.idempotencyKey() == null) throw new InventoryReceiptException("La llave de idempotencia es obligatoria");
        if (command.expiresAt() == null || !command.expiresAt().isAfter(Instant.now())) throw new InventoryReceiptException("La expiracion debe ser futura");
        if (command.createdBy() == null) throw new InventoryReceiptException("El usuario es obligatorio");
        if (command.items() == null || command.items().isEmpty()) throw new InventoryReceiptException("La reserva requiere items");
        command.items().forEach(item -> {
            if (item.warehouseId() == null) throw new InventoryReceiptException("El almacen es obligatorio");
            if (item.productPresentationId() == null) throw new InventoryReceiptException("La presentacion es obligatoria");
            if (item.quantity() == null || item.quantity().compareTo(ZERO) <= 0) throw new InventoryReceiptException("La cantidad debe ser mayor a cero");
        });
    }
}
