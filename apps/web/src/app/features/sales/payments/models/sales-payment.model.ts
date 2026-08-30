export type PaymentMethod = 'CASH' | 'CARD' | 'TRANSFER';

export interface SalesPayment {
  paymentId: string;
  salesOrderId: string;
  cashSessionId: string | null;
  paymentMethod: PaymentMethod | string;
  status: string;
  amount: number;
  currencyCode: string;
  reference: string | null;
  idempotencyKey: string;
  paidAt: string;
  createdAt: string;
}

export interface CreateSalesPaymentRequest {
  cashSessionId: string | null;
  paymentMethod: PaymentMethod;
  amount: number;
  currencyCode: string;
  reference: string | null;
  idempotencyKey: string;
}
