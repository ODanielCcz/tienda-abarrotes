export type FiscalProfileStatus = 'ACTIVE' | 'INACTIVE';
export type FiscalDocumentStatus = 'DRAFT' | 'READY';

export interface IssuerProfile {
  issuerProfileId: string;
  branchId: string;
  rfc: string;
  legalName: string;
  postalCode: string;
  fiscalRegimeCode: string;
  defaultSeries: string | null;
  status: FiscalProfileStatus;
  createdAt: string;
  updatedAt: string;
}

export interface IssuerProfileRequest {
  branchId: string;
  rfc: string;
  legalName: string;
  postalCode: string;
  fiscalRegimeCode: string;
  defaultSeries: string | null;
}

export interface FiscalProfile {
  fiscalProfileId: string;
  customerId: string;
  rfc: string;
  legalName: string;
  postalCode: string;
  fiscalRegimeCode: string;
  cfdiUseCode: string | null;
  email: string | null;
  status: FiscalProfileStatus;
  createdAt: string;
  updatedAt: string;
}

export interface FiscalProfileRequest {
  customerId: string;
  rfc: string;
  legalName: string;
  postalCode: string;
  fiscalRegimeCode: string;
  cfdiUseCode: string | null;
  email: string | null;
}

export interface FiscalDocumentItem {
  fiscalDocumentItemId: string;
  salesOrderItemId: string;
  satProductServiceCode: string;
  satUnitCode: string;
  description: string;
  quantity: number;
  unitValue: number;
  discountAmount: number;
  taxAmount: number;
  amount: number;
}

export interface FiscalDocument {
  fiscalDocumentId: string;
  salesOrderId: string;
  issuerProfileId: string;
  fiscalProfileId: string;
  documentType: string;
  cfdiVersion: string;
  status: FiscalDocumentStatus;
  series: string | null;
  folio: string | null;
  issuerRfc: string;
  issuerName: string;
  receiverRfc: string;
  receiverName: string;
  paymentFormCode: string | null;
  paymentMethodCode: string | null;
  currencyCode: string;
  subtotal: number;
  discountTotal: number;
  taxTotal: number;
  total: number;
  issuedAt: string | null;
  createdAt: string;
  items: FiscalDocumentItem[];
}

export interface CreateFiscalDocumentRequest {
  salesOrderId: string;
  issuerProfileId: string;
  fiscalProfileId: string;
  series: string | null;
  folio: string | null;
  paymentFormCode: string | null;
  paymentMethodCode: string | null;
}

export interface FiscalClassificationResult {
  productId?: string;
  unitId?: string;
  satProductServiceCode?: string;
  satUnitCode?: string;
}
