import { Injectable, signal } from '@angular/core';
import { LoginResponse } from '../../features/authentication/models/login-response';

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
}
