export interface ProductPresentationUpsertRequest {
  unitId: string;
  taxId: string | null;
  sku: string;
  name: string;
  conversionFactor: number;
  netContent: number | null;
  minimumStock: number;
}
