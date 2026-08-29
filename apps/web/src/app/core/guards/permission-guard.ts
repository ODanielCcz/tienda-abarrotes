import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthSessionStore } from '../auth/auth-session-store';

export const permissionGuard: CanActivateFn = (route) => {
  const sessionStore = inject(AuthSessionStore);
  const router = inject(Router);

  const permissions = route.data['permissions'];

  if (!Array.isArray(permissions)) {
    return true;
  }

  const authorized =
    route.data['permissionMatch'] === 'ANY'
      ? sessionStore.hasAnyPermission(permissions)
      : sessionStore.hasAllPermissions(permissions);

  return authorized ? true : router.createUrlTree(['/forbidden']);
};
