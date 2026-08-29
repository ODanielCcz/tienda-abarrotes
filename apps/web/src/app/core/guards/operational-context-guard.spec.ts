import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';

import { OperationalContextStore } from '../context/operational-context.store';
import { operationalContextGuard } from './operational-context-guard';

describe('operationalContextGuard', () => {
  let router: Router;
  const store = { context: () => ({}) };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: OperationalContextStore, useValue: store },
      ],
    });
    router = TestBed.inject(Router);
  });

  it('allows a route when the required warehouse is selected', () => {
    store.context = () => ({ branchId: 'branch-a', warehouseId: 'warehouse-a' });
    const route = { data: { operationalContext: 'WAREHOUSE' } } as unknown as ActivatedRouteSnapshot;

    const result = TestBed.runInInjectionContext(() =>
      operationalContextGuard(route, { url: '/inventory' } as RouterStateSnapshot),
    );

    expect(result).toBe(true);
  });

  it('redirects to dashboard when the required context is missing', () => {
    store.context = () => ({ branchId: 'branch-a' });
    const route = { data: { operationalContext: 'CASH_REGISTER' } } as unknown as ActivatedRouteSnapshot;

    const result = TestBed.runInInjectionContext(() =>
      operationalContextGuard(route, { url: '/cash' } as RouterStateSnapshot),
    );

    expect(router.serializeUrl(result as UrlTree)).toBe(
      '/dashboard?contextRequired=CASH_REGISTER&returnUrl=%2Fcash',
    );
  });
});
