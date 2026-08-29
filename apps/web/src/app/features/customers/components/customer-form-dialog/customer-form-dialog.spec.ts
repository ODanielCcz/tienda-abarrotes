import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormGroup } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { CustomerApi } from '../../data-access/customer-api';
import { CustomerFormDialog } from './customer-form-dialog';
describe('CustomerFormDialog', () => {
  let fixture: ComponentFixture<CustomerFormDialog>;
  const api = { create: vi.fn(), update: vi.fn() };
  const ref = { close: vi.fn() };
  afterEach(() => TestBed.resetTestingModule());
  it('normalizes and creates a customer', async () => {
    await TestBed.configureTestingModule({
      imports: [CustomerFormDialog],
      providers: [
        { provide: CustomerApi, useValue: api },
        { provide: MatDialogRef, useValue: ref },
        { provide: MAT_DIALOG_DATA, useValue: { customer: null } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(CustomerFormDialog);
    fixture.detectChanges();
    const component = fixture.componentInstance as unknown as { form: FormGroup; submit(): void };
    component.form.setValue({
      customerCode: ' cli-1 ',
      customerType: 'PERSON',
      displayName: ' Ana López ',
      email: ' ANA@EXAMPLE.COM ',
      phone: ' 5555 ',
    });
    api.create.mockReturnValueOnce(of({ customerId: 'customer-1' }));
    component.submit();
    expect(api.create).toHaveBeenCalledWith({
      customerCode: 'CLI-1',
      customerType: 'PERSON',
      displayName: 'Ana López',
      email: 'ana@example.com',
      phone: '5555',
    });
    expect(ref.close).toHaveBeenCalled();
  });
});
