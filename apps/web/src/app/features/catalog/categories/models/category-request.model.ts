import { CategoryStatus } from './category.model';

export interface CategoryUpsertRequest {
  code: string;
  name: string;
  parentCategoryId: string | null;
}

export interface ChangeCategoryStatusRequest {
  status: CategoryStatus;
}
