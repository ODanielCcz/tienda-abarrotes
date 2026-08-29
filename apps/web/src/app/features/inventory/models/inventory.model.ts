export interface StockBalance {
  stockBalanceId: string;
  warehouseId: string;
  productPresentationId: string;
  sku: string;
  presentationName: string;
  productName: string;
  onHandQuantity: number;
  reservedQuantity: number;
  allocatedQuantity: number;
  availableQuantity: number;
  averageUnitCost: number;
  updatedAt: string;
}

export interface InventoryLot {
  lotId: string;
  productPresentationId: string;
  supplierId: string | null;
  lotNumber: string;
  manufacturedAt: string | null;
  expiresAt: string | null;
  status: string;
  warehouseId: string;
  onHandQuantity: number;
  availableQuantity: number;
  createdAt: string;
}

export interface PalletItem {
  palletItemId: string;
  productPresentationId: string;
  sku: string;
  presentationName: string;
  lotId: string | null;
  lotNumber: string | null;
  quantity: number;
}

export interface InventoryPallet {
  palletId: string;
  warehouseId: string;
  stockMovementId: string;
  palletCode: string;
  externalPalletCode: string | null;
  status: string;
  receivedAt: string;
  items: PalletItem[];
}

export interface StockMovementItem {
  stockMovementItemId: string;
  productPresentationId: string;
  sku: string;
  presentationName: string;
  lotId: string | null;
  lotNumber: string | null;
  direction: string;
  quantity: number;
  unitCost: number;
  quantityBefore: number;
  quantityAfter: number;
}

export interface StockMovement {
  stockMovementId: string;
  branchId: string;
  warehouseId: string;
  movementType: string;
  status: string;
  sourceType: string | null;
  sourceId: string | null;
  reason: string | null;
  idempotencyKey: string | null;
  createdAt: string;
  confirmedAt: string | null;
  items: StockMovementItem[];
}

export interface LotFilters {
  warehouseId: string;
  productPresentationId?: string;
  status?: string;
  expiresBefore?: string;
  expiresAfter?: string;
}

export interface MovementFilters {
  warehouseId: string;
  movementType?: string;
  status?: string;
}
