export type ProductType = 'GOODS' | 'SERVICE';
export type ProductStatus = 'ACTIVE' | 'INACTIVE' | 'DISCONTINUED';
export type ProductPresentationStatus = ProductStatus;
export type ProductSortField = 'NAME' | 'CREATED_AT' | 'UPDATED_AT';
export type SortDirection = 'ASC' | 'DESC';

export interface Product {
  id: string;
  categoryId: string | null;
  brandId: string | null;
  name: string;
  description: string | null;
  productType: ProductType;
  tracksInventory: boolean;
  tracksLots: boolean;
  tracksExpiration: boolean;
  status: ProductStatus;
  createdAt: string;
  updatedAt: string;
}

export interface ProductListFilters {
  page: number;
  size: number;
  search?: string;
  status?: ProductStatus;
  categoryId?: string;
  brandId?: string;
  sortBy?: ProductSortField;
  direction?: SortDirection;
}

export interface ProductPresentation {
  id: string;
  productId: string;
  unitId: string;
  taxId: string | null;
  sku: string;
  name: string;
  conversionFactor: number;
  netContent: number | null;
  minimumStock: number;
  status: ProductPresentationStatus;
  createdAt: string;
  updatedAt: string;
}
