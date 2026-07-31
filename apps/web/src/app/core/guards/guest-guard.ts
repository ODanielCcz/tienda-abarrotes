import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthSessionStore } from '../auth/auth-session-store';

export const guestGuard: CanActivateFn = () => {
  const sessionStore = inject(AuthSessionStore);
  const router = inject(Router);

  return sessionStore.isAuthenticated() ? router.createUrlTree(['/dashboard']) : true;
};
