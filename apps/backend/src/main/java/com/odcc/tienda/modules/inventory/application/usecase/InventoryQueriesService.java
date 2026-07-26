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
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class InventoryQueriesService implements InventoryQueriesUseCase {

    private final InventoryQueryRepositoryPort repository;

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
