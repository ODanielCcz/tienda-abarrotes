import { Injectable, signal } from '@angular/core';
import { LoginResponse } from '../../features/authentication/models/login-response';
import { AuthSessionUser } from './auth-session-user';

const AUTH_SESSION_KEY = 'tienda.auth.session';

@Injectable({
  providedIn: 'root',
})
export class AuthSessionStore {
  private readonly sessionState = signal<LoginResponse | null>(this.restoreSession());

  readonly session = this.sessionState.asReadonly();

  save(session: LoginResponse): void {
    sessionStorage.setItem(AUTH_SESSION_KEY, JSON.stringify(session));
    this.sessionState.set(session);
  }

  clear(): void {
    sessionStorage.removeItem(AUTH_SESSION_KEY);
    this.sessionState.set(null);
  }

  accessToken(): string | null {
    return this.validSession()?.accessToken ?? null;
  }

  isAuthenticated(): boolean {
    return this.validSession() !== null;
  }

  currentUser(): AuthSessionUser | null {
    const token = this.accessToken();

    if (token === null) {
      return null;
    }

    return this.decodeUser(token);
  }

  hasPermission(permission: string): boolean {
    return this.grantedPermissions().has(permission);
  }

  hasAnyPermission(permissions: string[]): boolean {
    if (permissions.length === 0) {
      return true;
    }

    const grantedPermissions = this.grantedPermissions();
    return permissions.some((permission) => grantedPermissions.has(permission));
  }

  hasAllPermissions(permissions: string[]): boolean {
    if (permissions.length === 0) {
      return true;
    }

    const grantedPermissions = this.grantedPermissions();
    return permissions.every((permission) => grantedPermissions.has(permission));
  }

  private grantedPermissions(): Set<string> {
    const user = this.currentUser();

    if (user === null) {
      return new Set<string>();
    }

    return new Set([...user.permissions, ...user.authorities]);
  }

  private validSession(): LoginResponse | null {
    const session = this.sessionState();

    if (session === null) {
      return null;
    }

    const expiration = Date.parse(session.expiresAt);

    if (Number.isNaN(expiration) || expiration <= Date.now()) {
      this.clear();
      return null;
    }

    return session;
  }

  private restoreSession(): LoginResponse | null {
    const storedSession = sessionStorage.getItem(AUTH_SESSION_KEY);

    if (storedSession === null) {
      return null;
    }

    try {
      const parsedSession: unknown = JSON.parse(storedSession);

      if (!this.isLoginResponse(parsedSession)) {
        sessionStorage.removeItem(AUTH_SESSION_KEY);
        return null;
      }

      if (Date.parse(parsedSession.expiresAt) <= Date.now()) {
        sessionStorage.removeItem(AUTH_SESSION_KEY);
        return null;
      }

      return parsedSession;
    } catch {
      sessionStorage.removeItem(AUTH_SESSION_KEY);
      return null;
    }
  }

  private isLoginResponse(value: unknown): value is LoginResponse {
    if (typeof value !== 'object' || value === null) {
      return false;
    }

    const candidate = value as Record<string, unknown>;

    return (
      typeof candidate['accessToken'] === 'string' &&
      typeof candidate['tokenType'] === 'string' &&
      typeof candidate['expiresAt'] === 'string'
    );
  }

  private decodeUser(token: string): AuthSessionUser | null {
    const payload = this.decodeJwtPayload(token);

    if (payload === null) {
      return null;
    }

    return {
      userId: this.asString(payload['sub']),
      username: this.asString(payload['username']),
      displayName: this.asString(payload['display_name']),
      roles: this.asStringArray(payload['roles']),
      permissions: this.asStringArray(payload['permissions']),
      authorities: this.asStringArray(payload['authorities']),
    };
  }

  private decodeJwtPayload(token: string): Record<string, unknown> | null {
    const [, payload] = token.split('.');

    if (!payload) {
      return null;
    }

    try {
      const normalizedPayload = payload.replace(/-/g, '+').replace(/_/g, '/');
      const paddedPayload = normalizedPayload.padEnd(
        normalizedPayload.length + ((4 - (normalizedPayload.length % 4)) % 4),
        '=',
      );

      return JSON.parse(atob(paddedPayload)) as Record<string, unknown>;
    } catch {
      return null;
    }
  }

  private asString(value: unknown): string {
    return typeof value === 'string' ? value : '';
  }

  private asStringArray(value: unknown): string[] {
    return Array.isArray(value)
      ? value.filter((item): item is string => typeof item === 'string')
      : [];
  }
}

