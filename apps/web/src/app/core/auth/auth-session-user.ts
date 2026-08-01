export interface AuthSessionUser {
  userId: string;
  username: string;
  displayName: string;
  roles: string[];
  permissions: string[];
  authorities: string[];
}
