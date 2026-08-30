export interface CashSession {
  cashSessionId: string;
  cashRegisterId: string;
  branchId: string;
  cashRegisterCode: string;
  cashRegisterName: string;
  openedBy: string;
  closedBy: string | null;
  status: string;
  openingAmount: number;
  expectedAmount: number;
  countedAmount: number | null;
  differenceAmount: number | null;
  openedAt: string;
  closedAt: string | null;
  notes: string | null;
}

export interface CashMovement {
  cashMovementId: string;
  cashSessionId: string;
  movementType: string;
  direction: 'IN' | 'OUT';
  amount: number;
  paymentId: string | null;
  reference: string | null;
  reason: string | null;
  createdBy: string;
  createdAt: string;
}

export interface CashSessionFilters {
  cashRegisterId?: string;
  status?: string;
}

export interface OpenCashSessionRequest {
  cashRegisterId: string;
  openingAmount: number;
  notes: string | null;
}

export interface CloseCashSessionRequest {
  countedCashAmount: number;
  notes: string | null;
}

export interface CreateCashMovementRequest {
  movementType: string;
  direction: 'IN' | 'OUT';
  amount: number;
  reference: string | null;
  reason: string | null;
}
