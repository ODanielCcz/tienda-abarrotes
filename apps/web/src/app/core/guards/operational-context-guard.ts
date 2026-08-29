import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { OperationalContextStore } from '../context/operational-context.store';

export type OperationalContextRequirement = 'BRANCH' | 'WAREHOUSE' | 'CASH_REGISTER';

export const operationalContextGuard: CanActivateFn = (route, state) => {
  const contextStore = inject(OperationalContextStore);
  const router = inject(Router);
  const requirement = route.data['operationalContext'] as
    | OperationalContextRequirement
    | undefined;
  if (requirement === undefined) return true;

  const context = contextStore.context();
  const satisfied =
    (requirement === 'BRANCH' && context.branchId !== undefined) ||
    (requirement === 'WAREHOUSE' &&
      context.branchId !== undefined &&
      context.warehouseId !== undefined) ||
    (requirement === 'CASH_REGISTER' &&
      context.branchId !== undefined &&
      context.cashRegisterId !== undefined);

  return satisfied
    ? true
    : router.createUrlTree(['/dashboard'], {
        queryParams: { contextRequired: requirement, returnUrl: state.url },
      });
};
