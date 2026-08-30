export interface SalesReturnItem {
  returnItemId: string;
  returnId: string;
  salesOrderItemId: string;
  productPresentationId: string;
  lotId: string | null;
  productNameSnapshot: string;
  skuSnapshot: string;
  quantity: number;
  amount: number;
}

export interface SalesReturn {
  returnId: string;
  salesOrderId: string;
  status: string;
  reason: string;
  total: number;
  createdBy: string;
  createdAt: string;
  confirmedAt: string | null;
  items: SalesReturnItem[];
}

export interface CreateSalesReturnRequest {
  reason: string;
  items: Array<{
    salesOrderItemId: string;
    quantity: number;
  }>;
}

export interface ConfirmSalesReturnRequest {
  cashSessionId: string | null;
}
