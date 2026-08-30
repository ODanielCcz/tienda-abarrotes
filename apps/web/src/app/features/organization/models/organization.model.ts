export type ActiveStatus = 'ACTIVE' | 'INACTIVE';
export type CashRegisterStatus = ActiveStatus | 'MAINTENANCE';
export type DeviceStatus = 'ACTIVE' | 'BLOCKED' | 'RETIRED';
export type DeviceType = 'POS' | 'MOBILE_EMPLOYEE' | 'MOBILE_CUSTOMER' | 'WEB';
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

export interface Device {
  deviceId: string;
  branchId: string;
  warehouseId: string | null;
  deviceCode: string;
  deviceType: DeviceType;
  platform: string | null;
  appVersion: string | null;
  status: DeviceStatus;
  lastSeenAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface BranchRequest {
  code: string;
  name: string;
  legalName: string | null;
  timezone: string | null;
  currencyCode: string | null;
}

export interface WarehouseRequest {
  branchId: string;
  code: string;
  name: string;
  warehouseType: WarehouseType;
}

export interface CashRegisterRequest {
  branchId: string;
  deviceId: string | null;
  code: string;
  name: string;
}

export interface DeviceRequest {
  branchId: string;
  warehouseId: string | null;
  deviceCode: string;
  deviceType: DeviceType;
  platform: string | null;
  appVersion: string | null;
}
