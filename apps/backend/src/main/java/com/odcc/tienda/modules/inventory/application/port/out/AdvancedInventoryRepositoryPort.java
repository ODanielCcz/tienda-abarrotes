package com.odcc.tienda.modules.inventory.application.port.out;

import com.odcc.tienda.modules.inventory.application.command.ConfirmInventoryCountCommand;
import com.odcc.tienda.modules.inventory.application.command.CreateInventoryAdjustmentCommand;
import com.odcc.tienda.modules.inventory.application.command.CreateInventoryCountCommand;
import com.odcc.tienda.modules.inventory.application.command.CreateInventoryTransferCommand;
import com.odcc.tienda.modules.inventory.application.command.CreateReservationCommand;
import com.odcc.tienda.modules.inventory.application.command.ReleaseReservationCommand;
import com.odcc.tienda.modules.inventory.application.model.InventoryCountView;
import com.odcc.tienda.modules.inventory.application.model.LotView;
import com.odcc.tienda.modules.inventory.application.model.ReservationView;
import com.odcc.tienda.modules.inventory.application.model.StockMovementView;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AdvancedInventoryRepositoryPort {
    StockMovementView adjust(CreateInventoryAdjustmentCommand command);

    List<StockMovementView> transfer(CreateInventoryTransferCommand command);

    InventoryCountView createCount(CreateInventoryCountCommand command);

    InventoryCountView confirmCount(ConfirmInventoryCountCommand command);

    ReservationView reserve(CreateReservationCommand command);

    ReservationView releaseReservation(ReleaseReservationCommand command);

    List<LotView> findExpiringLots(LocalDate expiresBefore);

    UUID findBranchIdByWarehouseId(UUID warehouseId);

    UUID findBranchIdByCountId(UUID inventoryCountId);

    UUID findBranchIdByReservationId(UUID reservationId);
}
