import { BrandStatus } from './brand.model';

export interface BrandUpsertRequest {
  code: string;
  name: string;
}

export interface ChangeBrandStatusRequest {
  status: BrandStatus;
}
