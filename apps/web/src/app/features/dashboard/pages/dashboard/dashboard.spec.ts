import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { vi } from 'vitest';

import { AuthApi } from '../../../authentication/data-access/auth-api';
import { Dashboard } from './dashboard';

describe('Dashboard', () => {
  const authApiMock = {
    logout: vi.fn(),
  };

  const routerMock = {
    navigateByUrl: vi.fn().mockResolvedValue(true),
  };

  beforeEach(async () => {
    authApiMock.logout.mockClear();
    routerMock.navigateByUrl.mockClear();

    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [
        {
          provide: AuthApi,
          useValue: authApiMock,
        },
        {
          provide: Router,
          useValue: routerMock,
        },
      ],
    }).compileComponents();
  });

  it('should create the dashboard', () => {
    const fixture = TestBed.createComponent(Dashboard);

    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should clear the session and navigate to login', () => {
    const fixture = TestBed.createComponent(Dashboard);

    const component = fixture.componentInstance as unknown as {
      logout(): void;
    };

    component.logout();

    expect(authApiMock.logout).toHaveBeenCalled();
    expect(routerMock.navigateByUrl).toHaveBeenCalledWith('/login');
  });
});
