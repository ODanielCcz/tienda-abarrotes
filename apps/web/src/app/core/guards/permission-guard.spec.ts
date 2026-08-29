import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, UrlTree, provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { AuthSessionStore } from '../auth/auth-session-store';
import { permissionGuard } from './permission-guard';

describe('permissionGuard', () => {
  const session = { hasAllPermissions: vi.fn(), hasAnyPermission: vi.fn() };

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthSessionStore, useValue: session }],
    });
  });

  it('allows a route configured for any one of its permissions', () => {
    session.hasAnyPermission.mockReturnValue(true);
    const route = {
      data: { permissions: ['A', 'B'], permissionMatch: 'ANY' },
    } as unknown as ActivatedRouteSnapshot;

    const result = TestBed.runInInjectionContext(() => permissionGuard(route, {} as never));

    expect(result).toBe(true);
    expect(session.hasAnyPermission).toHaveBeenCalledWith(['A', 'B']);
  });

  it('keeps requiring every permission by default', () => {
    session.hasAllPermissions.mockReturnValue(false);
    const route = { data: { permissions: ['A', 'B'] } } as unknown as ActivatedRouteSnapshot;
    const router = TestBed.inject(Router);

    const result = TestBed.runInInjectionContext(() => permissionGuard(route, {} as never));

    expect(router.serializeUrl(result as UrlTree)).toBe('/forbidden');
  });
});
