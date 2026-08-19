export type CategoryStatus = 'ACTIVE' | 'INACTIVE';

export interface Category {
  id: string;
  parentCategoryId: string | null;
  code: string;
  name: string;
  status: CategoryStatus;
  createdAt: string;
}

export interface CategoryTreeNode extends Category {
  children: CategoryTreeNode[];
}
