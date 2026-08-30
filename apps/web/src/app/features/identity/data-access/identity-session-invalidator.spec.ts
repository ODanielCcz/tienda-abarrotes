import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { vi } from 'vitest';

import { AuthSessionStore } from '../../../core/auth/auth-session-store';
import { IdentitySessionInvalidator } from './identity-session-invalidator';

describe('IdentitySessionInvalidator', () => {
  const session = {
    currentUser: vi.fn(),
    clear: vi.fn(),
  };
  const router = { navigateByUrl: vi.fn().mockResolvedValue(true) };

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthSessionStore, useValue: session },
        { provide: Router, useValue: router },
      ],
    });
  });

  it('clears the local session when current user authorization changes', () => {
    session.currentUser.mockReturnValue({ userId: 'user-1' });

    TestBed.inject(IdentitySessionInvalidator).afterAuthorizationChange('user-1');

    expect(session.clear).toHaveBeenCalled();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/login');
  });

  it('keeps the session when another user changes', () => {
    session.currentUser.mockReturnValue({ userId: 'user-1' });

    TestBed.inject(IdentitySessionInvalidator).afterAuthorizationChange('user-2');

    expect(session.clear).not.toHaveBeenCalled();
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('clears the session when a role held by the current user changes', () => {
    session.currentUser.mockReturnValue({ userId: 'user-1', roles: ['CASHIER'] });

    TestBed.inject(IdentitySessionInvalidator).afterRoleAuthorizationChange('CASHIER');

    expect(session.clear).toHaveBeenCalled();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/login');
  });
});
