import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { AuthSessionStore } from '../../../../core/auth/auth-session-store';
import { CustomerApi } from '../../data-access/customer-api';
import { CustomerList } from './customer-list';
describe('CustomerList', () => {
  let fixture: ComponentFixture<CustomerList>;
  const api = { list: vi.fn(), changeStatus: vi.fn() };
  const session = { hasPermission: vi.fn(() => true) };
  beforeEach(async () => {
    api.list.mockReturnValue(
      of([
        {
          customerId: 'c1',
          customerCode: 'CLI-1',
          customerType: 'PERSON',
          displayName: 'Ana López',
          email: null,
          phone: null,
          status: 'ACTIVE',
          createdAt: '',
          updatedAt: '',
        },
      ]),
    );
    await TestBed.configureTestingModule({
      imports: [CustomerList],
      providers: [
        { provide: CustomerApi, useValue: api },
        { provide: AuthSessionStore, useValue: session },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(CustomerList);
    fixture.detectChanges();
  });
  it('loads customers and renders their name', () => {
    expect(api.list).toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Ana López');
  });
});
