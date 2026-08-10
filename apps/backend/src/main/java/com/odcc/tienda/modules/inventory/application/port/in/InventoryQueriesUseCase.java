package com.odcc.tienda.modules.inventory.application.port.in;

import com.odcc.tienda.modules.inventory.application.model.LotView;
import com.odcc.tienda.modules.inventory.application.model.PalletView;
import com.odcc.tienda.modules.inventory.application.model.StockBalanceView;
import com.odcc.tienda.modules.inventory.application.model.StockMovementView;
import com.odcc.tienda.modules.inventory.application.query.LotQuery;
import com.odcc.tienda.modules.inventory.application.query.PalletQuery;
import com.odcc.tienda.modules.inventory.application.query.StockMovementQuery;
import com.odcc.tienda.modules.inventory.application.query.StockQuery;

import java.util.List;
import java.util.UUID;

public interface InventoryQueriesUseCase {
    List<StockBalanceView> findStock(StockQuery query);

    List<LotView> findLots(LotQuery query);

    LotView getLotById(UUID lotId);

    List<PalletView> findPallets(PalletQuery query);

    PalletView getPalletById(UUID palletId);

    List<StockMovementView> findMovements(StockMovementQuery query);

    StockMovementView getMovementById(UUID movementId);

    default List<StockBalanceView> findStock(StockQuery query, UUID actorUserId) { return findStock(query); }
    default List<LotView> findLots(LotQuery query, UUID actorUserId) { return findLots(query); }
    default LotView getLotById(UUID lotId, UUID actorUserId) { return getLotById(lotId); }
    default List<PalletView> findPallets(PalletQuery query, UUID actorUserId) { return findPallets(query); }
    default PalletView getPalletById(UUID palletId, UUID actorUserId) { return getPalletById(palletId); }
    default List<StockMovementView> findMovements(StockMovementQuery query, UUID actorUserId) { return findMovements(query); }
    default StockMovementView getMovementById(UUID movementId, UUID actorUserId) { return getMovementById(movementId); }
}
