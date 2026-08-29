export type CustomerType = 'GENERAL' | 'PERSON' | 'BUSINESS';
export type CustomerStatus = 'ACTIVE' | 'INACTIVE' | 'BLOCKED';
export interface Customer {
  customerId: string;
  customerCode: string | null;
  customerType: CustomerType;
  displayName: string;
  email: string | null;
  phone: string | null;
  status: CustomerStatus;
  createdAt: string;
  updatedAt: string;
}
export interface CustomerRequest {
  customerCode: string | null;
  customerType: CustomerType;
  displayName: string;
  email: string | null;
  phone: string | null;
}
export interface CustomerFilters {
  search?: string;
  customerType?: CustomerType;
  status?: CustomerStatus;
}
