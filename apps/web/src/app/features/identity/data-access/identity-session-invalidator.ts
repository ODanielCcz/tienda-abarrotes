import { inject, Injectable } from '@angular/core';
import { Router } from '@angular/router';

import { AuthSessionStore } from '../../../core/auth/auth-session-store';

@Injectable({ providedIn: 'root' })
export class IdentitySessionInvalidator {
  private readonly session = inject(AuthSessionStore);
  private readonly router = inject(Router);

  afterAuthorizationChange(userId: string): void {
    if (this.session.currentUser()?.userId !== userId) return;

    this.session.clear();
    void this.router.navigateByUrl('/login');
  }

  afterRoleAuthorizationChange(roleCode: string): void {
    if (!this.session.currentUser()?.roles.includes(roleCode)) return;

    this.session.clear();
    void this.router.navigateByUrl('/login');
  }
}
