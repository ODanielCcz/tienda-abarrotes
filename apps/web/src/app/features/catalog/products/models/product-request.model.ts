import { ProductStatus, ProductType } from './product.model';

export interface ProductUpsertRequest {
  categoryId: string | null;
  brandId: string | null;
  name: string;
  description: string | null;
  productType: ProductType;
  tracksInventory: boolean;
  tracksLots: boolean;
  tracksExpiration: boolean;
}

export interface ChangeProductStatusRequest {
  status: ProductStatus;
}
