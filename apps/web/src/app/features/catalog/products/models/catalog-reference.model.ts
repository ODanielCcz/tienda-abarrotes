export interface CatalogUnit {
  id: string;
  code: string;
  name: string;
  symbol: string;
  quantityScale: number;
}

export interface CatalogTax {
  id: string;
  code: string;
  name: string;
  rate: number;
}

export interface CatalogPrice {
  priceId: string;
  productPresentationId: string;
  branchId: string | null;
  amount: number;
  currencyCode: string;
}
