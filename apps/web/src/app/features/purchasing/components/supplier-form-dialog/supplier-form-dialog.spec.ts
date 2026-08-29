import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormGroup } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { PurchasingApi } from '../../data-access/purchasing-api';
import { Supplier } from '../../models/purchasing.model';
import { SupplierFormDialog } from './supplier-form-dialog';

describe('SupplierFormDialog', () => {
  let fixture: ComponentFixture<SupplierFormDialog>;
  const api = { createSupplier: vi.fn(), updateSupplier: vi.fn() };
  const dialogRef = { close: vi.fn() };
  const supplier: Supplier = {
    supplierId: 'supplier-1',
    supplierCode: 'PROV-01',
    legalName: 'Proveedor Uno SA de CV',
    tradeName: 'Proveedor Uno',
    taxId: 'PUO010101AA1',
    email: 'ventas@example.com',
    phone: '5555555555',
    creditDays: 15,
    status: 'ACTIVE',
    createdAt: '',
    updatedAt: '',
  };

  afterEach(() => TestBed.resetTestingModule());

  it('normalizes and creates a supplier', async () => {
    await setup(null);
    const component = fixture.componentInstance as unknown as { form: FormGroup; submit(): void };
    component.form.setValue({
      supplierCode: ' prov-01 ',
      legalName: ' Proveedor Uno SA de CV ',
      tradeName: ' Proveedor Uno ',
      taxId: ' puo010101aa1 ',
      email: ' ventas@example.com ',
      phone: ' 5555555555 ',
      creditDays: 15,
    });
    api.createSupplier.mockReturnValueOnce(of(supplier));

    component.submit();

    expect(api.createSupplier).toHaveBeenCalledWith({
      supplierCode: 'PROV-01',
      legalName: 'Proveedor Uno SA de CV',
      tradeName: 'Proveedor Uno',
      taxId: 'PUO010101AA1',
      email: 'ventas@example.com',
      phone: '5555555555',
      creditDays: 15,
    });
    expect(dialogRef.close).toHaveBeenCalledWith(supplier);
  });

  async function setup(value: Supplier | null): Promise<void> {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [SupplierFormDialog],
      providers: [
        { provide: PurchasingApi, useValue: api },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: { supplier: value } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(SupplierFormDialog);
    fixture.detectChanges();
  }
});
