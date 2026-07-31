import { HttpErrorResponse } from '@angular/common/http';
import { FormGroup } from '@angular/forms';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AuthApi } from '../../data-access/auth-api';
import { LoginResponse } from '../../models/login-response';
import { Login } from './login';

describe('Login', () => {
  const authApiMock = {
    login: vi.fn(),
  };

  const routerMock = {
    navigateByUrl: vi.fn().mockResolvedValue(true),
  };

  beforeEach(async () => {
    authApiMock.login.mockReset();
    routerMock.navigateByUrl.mockClear();

    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        {
          provide: AuthApi,
          useValue: authApiMock,
        },
        {
          provide: Router,
          useValue: routerMock,
        },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: convertToParamMap({}),
            },
          },
        },
      ],
    }).compileComponents();
  });

  function createComponent() {
    const fixture = TestBed.createComponent(Login);
    fixture.detectChanges();

    const component = fixture.componentInstance as unknown as {
      form: FormGroup;
      submit(): void;
      errorMessage(): string | null;
      loading(): boolean;
    };

    return { fixture, component };
  }

  it('should not call the API when the form is invalid', () => {
    const { component } = createComponent();

    component.submit();

    expect(authApiMock.login).not.toHaveBeenCalled();
  });

  it('should authenticate and navigate to dashboard', () => {
    const session: LoginResponse = {
      accessToken: 'jwt-de-prueba',
      tokenType: 'Bearer',
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
    };

    authApiMock.login.mockReturnValue(of(session));

    const { component } = createComponent();

    component.form.setValue({
      username: 'admin',
      password: 'AdminLocal12345!',
    });

    component.submit();

    expect(authApiMock.login).toHaveBeenCalledWith({
      username: 'admin',
      password: 'AdminLocal12345!',
    });

    expect(routerMock.navigateByUrl).toHaveBeenCalledWith('/dashboard');

    expect(component.loading()).toBe(false);
  });

  it('should display the error returned by the backend', () => {
    authApiMock.login.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 401,
            statusText: 'Unauthorized',
            error: {
              message: 'Usuario o contraseña incorrectos',
            },
          }),
      ),
    );

    const { component } = createComponent();

    component.form.setValue({
      username: 'admin',
      password: 'incorrecta',
    });

    component.submit();

    expect(component.errorMessage()).toBe('Usuario o contraseña incorrectos');

    expect(component.loading()).toBe(false);
  });
});
