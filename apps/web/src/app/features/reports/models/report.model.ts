export type ReportGroupBy = 'DAY' | 'WEEK' | 'MONTH';

export interface ReportFilters {
  from?: string;
  to?: string;
  branchId?: string;
  warehouseId?: string;
  customerId?: string;
  limit?: number;
}

export interface PeriodReportFilters extends ReportFilters {
  groupBy?: ReportGroupBy;
}

export interface ExpiringProductsFilters extends ReportFilters {
  days?: number;
}

export interface SalesSummaryReport {
  ticketCount: number;
  subtotal: number;
  discountTotal: number;
  taxTotal: number;
  total: number;
  averageTicket: number;
}

export interface TopProductReport {
  productPresentationId: string;
  sku: string;
  productName: string;
  quantitySold: number;
  grossAmount: number;
}

export interface CustomerSalesReport {
  customerId: string | null;
  customerCode: string | null;
  displayName: string;
  ticketCount: number;
  total: number;
}

export interface LowStockReport {
  warehouseId: string;
  warehouseName: string;
  productPresentationId: string;
  sku: string;
  presentationName: string;
  availableQuantity: number;
  minimumStock: number;
}

export interface InventoryMovementReport {
  warehouseId: string;
  warehouseName: string;
  movementType: string;
  movementCount: number;
  totalQuantity: number;
}

export interface CashSummaryReport {
  cashSessionId: string;
  cashRegisterId: string;
  cashRegisterCode: string;
  status: string;
  openingAmount: number;
  expectedAmount: number;
  countedAmount: number | null;
  differenceAmount: number | null;
  cashIn: number;
  cashOut: number;
  openedAt: string;
  closedAt: string | null;
}

export interface SalesByPeriodReport {
  periodStart: string;
  periodEnd: string;
  ticketCount: number;
  grossSubtotal: number;
  discountTotal: number;
  taxTotal: number;
  grossSales: number;
  returnsAmount: number;
  netSales: number;
}

export interface GrossMarginReport {
  grossRevenueExcludingTax: number;
  returnedRevenueExcludingTax: number;
  netRevenueExcludingTax: number;
  grossCost: number;
  returnedCost: number;
  netCost: number;
  grossProfit: number;
  grossMarginPercent: number;
}

export interface ProductProfitabilityReport {
  productPresentationId: string;
  sku: string;
  productName: string;
  quantitySold: number;
  quantityReturned: number;
  netQuantity: number;
  netRevenueExcludingTax: number;
  netCost: number;
  grossProfit: number;
  grossMarginPercent: number;
}

export interface StockValuationItem {
  warehouseId: string;
  warehouseName: string;
  productPresentationId: string;
  sku: string;
  productName: string;
  presentationName: string;
  onHandQuantity: number;
  availableQuantity: number;
  averageUnitCost: number;
  stockValue: number;
}

export interface StockValuationReport {
  totalOnHandQuantity: number;
  totalStockValue: number;
  items: StockValuationItem[];
}

export interface ExpiringProductReport {
  lotId: string;
  lotNumber: string;
  warehouseId: string;
  warehouseName: string;
  productPresentationId: string;
  sku: string;
  productName: string;
  presentationName: string;
  expiresAt: string;
  daysRemaining: number;
  onHandQuantity: number;
  availableQuantity: number;
  averageUnitCost: number;
  estimatedValue: number;
}

export interface ReturnPeriodReport {
  periodStart: string;
  periodEnd: string;
  returnCount: number;
  returnedQuantity: number;
  returnedAmount: number;
}

export interface ReturnsSummaryReport {
  returnCount: number;
  returnedQuantity: number;
  returnedAmount: number;
  periods: ReturnPeriodReport[];
}
