export type ActiveStatus = 'ACTIVE' | 'INACTIVE';
export type CashRegisterStatus = ActiveStatus | 'MAINTENANCE';
export type WarehouseType = 'STORE' | 'BACKROOM' | 'TRANSIT' | 'DAMAGED' | 'VIRTUAL';

export interface Branch {
  branchId: string;
  code: string;
  name: string;
  legalName: string;
  timezone: string;
  currencyCode: string;
  status: ActiveStatus;
  createdAt: string;
  updatedAt: string;
}

export interface Warehouse {
  warehouseId: string;
  branchId: string;
  code: string;
  name: string;
  warehouseType: WarehouseType;
  status: ActiveStatus;
  createdAt: string;
  updatedAt: string;
}

export interface CashRegister {
  cashRegisterId: string;
  branchId: string;
  deviceId: string | null;
  code: string;
  name: string;
  status: CashRegisterStatus;
  createdAt: string;
}
