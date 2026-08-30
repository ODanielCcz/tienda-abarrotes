export type UserAccountStatus = 'PENDING' | 'ACTIVE' | 'LOCKED' | 'DISABLED';
export type RoleStatus = 'ACTIVE' | 'INACTIVE';

export interface ManagedUser {
  userId: string;
  username: string;
  displayName: string;
  status: UserAccountStatus;
  roles: string[];
  permissions: string[];
  branchIds: string[];
  createdAt: string;
  updatedAt: string;
}

export interface UserFilters {
  search?: string;
  status?: UserAccountStatus;
  roleCode?: string;
}

export interface CreateUserRequest {
  username: string;
  displayName: string;
  password: string;
  roleCodes: string[];
}

export interface UpdateUserRequest {
  username: string;
  displayName: string;
}

export interface RoleSummary {
  roleId: string;
  code: string;
  name: string;
  description: string | null;
  system: boolean;
  status: RoleStatus;
  createdAt: string;
}

export interface RoleDetail extends RoleSummary {
  permissions: string[];
}

export interface RoleRequest {
  code: string;
  name: string;
  description: string | null;
}

export interface PermissionSummary {
  permissionId: string;
  code: string;
  name: string;
  module: string;
  description: string | null;
  createdAt: string;
}
