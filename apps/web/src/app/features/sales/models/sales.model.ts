export type SalesOrderChannel = 'POS' | 'WEB' | 'MOBILE';

export interface SalesOrderItem {
  salesOrderItemId: string;
  salesOrderId: string;
  productPresentationId: string;
  lotId: string | null;
  productNameSnapshot: string;
  skuSnapshot: string;
  quantity: number;
  unitPrice: number;
  unitCost: number;
  discountAmount: number;
  taxRate: number;
  taxAmount: number;
  lineTotal: number;
}

export interface SalesOrder {
  salesOrderId: string;
  orderNumber: string;
  branchId: string;
  warehouseId: string;
  customerId: string | null;
  deviceId: string | null;
  channel: SalesOrderChannel;
  status: string;
  paymentStatus: string;
  currencyCode: string;
  subtotal: number;
  discountTotal: number;
  taxTotal: number;
  total: number;
  idempotencyKey: string;
  createdAt: string;
  confirmedAt: string | null;
  cancelledAt: string | null;
  items: SalesOrderItem[];
}

export interface SalesOrderFilters {
  warehouseId?: string;
  customerId?: string;
  status?: string;
}

export interface CreateSalesOrderRequest {
  warehouseId: string;
  customerId: string | null;
  deviceId: string | null;
  channel: SalesOrderChannel;
  currencyCode: string;
  idempotencyKey: string;
  items: Array<{
    productPresentationId: string;
    quantity: number;
    unitPrice: number;
    discountAmount: number;
  }>;
}

export interface PosCatalogItem {
  productPresentationId: string;
  sku: string;
  productName: string;
  presentationName: string;
  availableQuantity: number;
  unitPrice: number;
  currencyCode: string;
}

export interface PosCartItem extends PosCatalogItem {
  quantity: number;
}
