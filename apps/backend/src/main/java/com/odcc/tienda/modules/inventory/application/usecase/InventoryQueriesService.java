package com.odcc.tienda.modules.inventory.application.usecase;

import com.odcc.tienda.modules.inventory.application.exception.InventoryResourceNotFoundException;
import com.odcc.tienda.modules.inventory.application.model.LotView;
import com.odcc.tienda.modules.inventory.application.model.PalletView;
import com.odcc.tienda.modules.inventory.application.model.StockBalanceView;
import com.odcc.tienda.modules.inventory.application.model.StockMovementView;
import com.odcc.tienda.modules.inventory.application.port.in.InventoryQueriesUseCase;
import com.odcc.tienda.modules.inventory.application.port.out.InventoryQueryRepositoryPort;
import com.odcc.tienda.modules.inventory.application.query.LotQuery;
import com.odcc.tienda.modules.inventory.application.query.PalletQuery;
import com.odcc.tienda.modules.inventory.application.query.StockMovementQuery;
import com.odcc.tienda.modules.inventory.application.query.StockQuery;
import com.odcc.tienda.shared.application.authorization.BranchAccessPort;
import com.odcc.tienda.shared.application.authorization.BranchScope;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class InventoryQueriesService implements InventoryQueriesUseCase {

    private final InventoryQueryRepositoryPort repository;
    private final BranchAccessPort branchAccessPort;

    @Override
    public List<StockBalanceView> findStock(StockQuery query, UUID actorUserId) {
        requireRequestedWarehouse(actorUserId, query == null ? null : query.warehouseId());
        BranchScope scope = branchAccessPort.resolveScope(actorUserId);
        return filterByWarehouse(findStock(query), actorUserId, scope, StockBalanceView::warehouseId);
    }

    @Override
    public List<LotView> findLots(LotQuery query, UUID actorUserId) {
        requireRequestedWarehouse(actorUserId, query == null ? null : query.warehouseId());
        BranchScope scope = branchAccessPort.resolveScope(actorUserId);
        return filterByWarehouse(findLots(query), actorUserId, scope, LotView::warehouseId);
    }

    @Override
    public LotView getLotById(UUID lotId, UUID actorUserId) {
        LotView lot = getLotById(lotId);
        requireWarehouse(actorUserId, lot.warehouseId());
        return lot;
    }

    @Override
    public List<PalletView> findPallets(PalletQuery query, UUID actorUserId) {
        requireRequestedWarehouse(actorUserId, query == null ? null : query.warehouseId());
        BranchScope scope = branchAccessPort.resolveScope(actorUserId);
        return filterByWarehouse(findPallets(query), actorUserId, scope, PalletView::warehouseId);
    }

    @Override
    public PalletView getPalletById(UUID palletId, UUID actorUserId) {
        PalletView pallet = getPalletById(palletId);
        requireWarehouse(actorUserId, pallet.warehouseId());
        return pallet;
    }

    @Override
    public List<StockMovementView> findMovements(StockMovementQuery query, UUID actorUserId) {
        requireRequestedWarehouse(actorUserId, query == null ? null : query.warehouseId());
        BranchScope scope = branchAccessPort.resolveScope(actorUserId);
        if (scope.globalAccess()) return findMovements(query);
        return findMovements(query).stream().filter(movement -> scope.branchIds().contains(movement.branchId())).toList();
    }

    @Override
    public StockMovementView getMovementById(UUID movementId, UUID actorUserId) {
        StockMovementView movement = getMovementById(movementId);
        branchAccessPort.requireAccess(actorUserId, movement.branchId());
        return movement;
    }

    private void requireRequestedWarehouse(UUID actorUserId, UUID warehouseId) {
        if (warehouseId != null) requireWarehouse(actorUserId, warehouseId);
    }

    private void requireWarehouse(UUID actorUserId, UUID warehouseId) {
        branchAccessPort.requireAccess(actorUserId, repository.findBranchIdByWarehouseId(warehouseId));
    }

    private <T> List<T> filterByWarehouse(List<T> values, UUID actorUserId, BranchScope scope, java.util.function.Function<T, UUID> warehouse) {
        if (scope.globalAccess()) return values;
        return values.stream()
            .filter(value -> scope.branchIds().contains(repository.findBranchIdByWarehouseId(warehouse.apply(value))))
            .toList();
    }

    @Override
    public List<StockBalanceView> findStock(StockQuery query) {
        return repository.findStock(query);
    }

    @Override
    public List<LotView> findLots(LotQuery query) {
        return repository.findLots(query);
    }

    @Override
    public LotView getLotById(UUID lotId) {
        return repository.findLotById(lotId).orElseThrow(() -> new InventoryResourceNotFoundException("un lote", lotId));
    }

    @Override
    public List<PalletView> findPallets(PalletQuery query) {
        return repository.findPallets(query);
    }

    @Override
    public PalletView getPalletById(UUID palletId) {
        return repository.findPalletById(palletId).orElseThrow(() -> new InventoryResourceNotFoundException("un pallet", palletId));
    }

    @Override
    public List<StockMovementView> findMovements(StockMovementQuery query) {
        return repository.findMovements(query);
    }

    @Override
    public StockMovementView getMovementById(UUID movementId) {
        return repository.findMovementById(movementId).orElseThrow(() -> new InventoryResourceNotFoundException("un movimiento de inventario", movementId));
    }
}
