export type BrandStatus = 'ACTIVE' | 'INACTIVE';

export interface Brand {
  id: string;
  code: string;
  name: string;
  status: BrandStatus;
  createdAt: string;
}
