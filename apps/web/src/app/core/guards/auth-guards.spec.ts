import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';

import { AuthSessionStore } from '../auth/auth-session-store';
import { authGuard } from './auth-guard';
import { guestGuard } from './guest-guard';

describe('authentication guards', () => {
  let sessionStore: AuthSessionStore;
  let router: Router;

  beforeEach(() => {
    sessionStorage.clear();

    TestBed.configureTestingModule({
      providers: [provideRouter([])],
    });

    sessionStore = TestBed.inject(AuthSessionStore);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    sessionStorage.clear();
  });

  function saveValidSession(): void {
    sessionStore.save({
      accessToken: 'jwt-de-prueba',
      tokenType: 'Bearer',
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
    });
  }

  it('authGuard should allow an authenticated user', () => {
    saveValidSession();

    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, { url: '/dashboard' } as RouterStateSnapshot),
    );

    expect(result).toBe(true);
  });

  it('authGuard should redirect an unauthenticated user', () => {
    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, { url: '/inventory/stock' } as RouterStateSnapshot),
    );

    expect(router.serializeUrl(result as UrlTree)).toBe('/login?returnUrl=%2Finventory%2Fstock');
  });

  it('guestGuard should allow an unauthenticated user', () => {
    const result = TestBed.runInInjectionContext(() =>
      guestGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );

    expect(result).toBe(true);
  });

  it('guestGuard should redirect an authenticated user', () => {
    saveValidSession();

    const result = TestBed.runInInjectionContext(() =>
      guestGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );

    expect(router.serializeUrl(result as UrlTree)).toBe('/dashboard');
  });
});
