import { TestBed } from '@angular/core/testing';

import { AuthSessionStore } from './auth-session-store';

describe('AuthSessionStore', () => {
  let store: AuthSessionStore;

  beforeEach(() => {
    sessionStorage.clear();

    TestBed.configureTestingModule({});

    store = TestBed.inject(AuthSessionStore);
  });

  afterEach(() => {
    sessionStorage.clear();
  });

  it('should store a valid session', () => {
    store.save({
      accessToken: 'access-token',
      tokenType: 'Bearer',
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
    });

    expect(store.isAuthenticated()).toBe(true);
    expect(store.accessToken()).toBe('access-token');
  });

  it('should clear the current session', () => {
    store.save({
      accessToken: 'access-token',
      tokenType: 'Bearer',
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
    });

    store.clear();

    expect(store.isAuthenticated()).toBe(false);
    expect(store.accessToken()).toBeNull();
  });

  it('should reject an expired session', () => {
    store.save({
      accessToken: 'expired-token',
      tokenType: 'Bearer',
      expiresAt: new Date(Date.now() - 60_000).toISOString(),
    });

    expect(store.isAuthenticated()).toBe(false);
    expect(store.accessToken()).toBeNull();
  });
});
