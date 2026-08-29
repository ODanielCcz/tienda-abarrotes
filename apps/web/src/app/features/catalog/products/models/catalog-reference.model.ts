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
