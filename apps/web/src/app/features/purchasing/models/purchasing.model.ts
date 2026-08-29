export type SupplierStatus = 'ACTIVE' | 'INACTIVE';
export type PurchaseStatus =
  'DRAFT' | 'CONFIRMED' | 'PARTIALLY_RECEIVED' | 'RECEIVED' | 'CANCELLED';

export interface Supplier {
  supplierId: string;
  supplierCode: string;
  legalName: string;
  tradeName: string | null;
  taxId: string | null;
  email: string | null;
  phone: string | null;
  creditDays: number;
  status: SupplierStatus;
  createdAt: string;
  updatedAt: string;
}

export interface SupplierUpsertRequest {
  supplierCode: string;
  legalName: string;
  tradeName: string | null;
  taxId: string | null;
  email: string | null;
  phone: string | null;
  creditDays: number;
}

export interface PurchaseItem {
  purchaseItemId: string;
  purchaseId: string;
  productPresentationId: string;
  lotId: string | null;
  productNameSnapshot: string;
  skuSnapshot: string;
  quantity: number;
  receivedQuantity: number;
  unitCost: number;
  discountAmount: number;
  taxAmount: number;
  lineTotal: number;
}

export interface Purchase {
  purchaseId: string;
  branchId: string;
  warehouseId: string;
  supplierId: string;
  supplierDocument: string | null;
  status: PurchaseStatus;
  paymentStatus: string;
  currencyCode: string;
  subtotal: number;
  discountTotal: number;
  taxTotal: number;
  total: number;
  idempotencyKey: string | null;
  purchasedAt: string;
  confirmedAt: string | null;
  createdAt: string;
  items: PurchaseItem[];
}

export interface CreatePurchaseItemRequest {
  productPresentationId: string;
  quantity: number;
  unitCost: number;
  discountAmount: number;
  taxAmount: number;
}

export interface CreatePurchaseRequest {
  warehouseId: string;
  supplierId: string;
  supplierDocument: string | null;
  currencyCode: string;
  idempotencyKey: string;
  items: CreatePurchaseItemRequest[];
}

export interface ReceivePurchaseItemRequest {
  purchaseItemId: string;
  lotNumber: string | null;
  manufacturedAt: string | null;
  expiresAt: string | null;
  quantity: number;
}

export interface ReceivePurchaseRequest {
  idempotencyKey: string;
  items: ReceivePurchaseItemRequest[];
  pallets: Array<{
    externalPalletCode: string | null;
    items: ReceivePurchaseItemRequest[];
  }>;
}

export interface SupplierFilters {
  search?: string;
  status?: SupplierStatus;
}

export interface PurchaseFilters {
  supplierId?: string;
  warehouseId?: string;
  status?: PurchaseStatus;
}
