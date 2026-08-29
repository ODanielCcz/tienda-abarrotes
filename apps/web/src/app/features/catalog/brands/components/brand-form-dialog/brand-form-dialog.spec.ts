import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { FormGroup } from '@angular/forms';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { BrandApi } from '../../data-access/brand-api';
import { Brand } from '../../models/brand.model';
import { BrandFormDialog, BrandFormDialogData } from './brand-form-dialog';

describe('BrandFormDialog', () => {
  let fixture: ComponentFixture<BrandFormDialog>;
  const brandApi = { create: vi.fn(), update: vi.fn() };
  const dialogRef = { close: vi.fn() };
  const savedBrand: Brand = {
    id: 'brand-1',
    code: 'COCA_COLA',
    name: 'Coca-Cola',
    status: 'ACTIVE',
    createdAt: '2026-08-20T00:00:00Z',
  };

  afterEach(() => TestBed.resetTestingModule());

  it('validates and creates a normalized brand', async () => {
    await setup({ brand: null });
    const component = fixture.componentInstance as unknown as {
      form: FormGroup;
      submit(): void;
    };

    component.form.setValue({ code: ' coca_cola ', name: ' Coca-Cola ' });
    brandApi.create.mockReturnValueOnce(of(savedBrand));
    component.submit();

    expect(brandApi.create).toHaveBeenCalledWith({ code: 'COCA_COLA', name: 'Coca-Cola' });
    expect(dialogRef.close).toHaveBeenCalledWith(savedBrand);
  });

  it('updates an existing brand', async () => {
    await setup({ brand: savedBrand });
    const component = fixture.componentInstance as unknown as {
      form: FormGroup;
      submit(): void;
    };

    component.form.setValue({ code: 'COCA_COLA', name: 'Coca-Cola México' });
    brandApi.update.mockReturnValueOnce(of({ ...savedBrand, name: 'Coca-Cola México' }));
    component.submit();

    expect(brandApi.update).toHaveBeenCalledWith('brand-1', {
      code: 'COCA_COLA',
      name: 'Coca-Cola México',
    });
  });

  it('keeps the dialog open and displays the backend reference on conflict', async () => {
    await setup({ brand: null });
    const component = fixture.componentInstance as unknown as {
      form: FormGroup;
      submit(): void;
    };
    component.form.setValue({ code: 'COCA_COLA', name: 'Coca-Cola' });
    brandApi.create.mockReturnValueOnce(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { message: 'El código ya existe', correlationId: 'brand-conflict-ref' },
          }),
      ),
    );

    component.submit();
    fixture.detectChanges();

    expect(dialogRef.close).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain(
      'El código ya existe (Referencia: brand-conflict-ref)',
    );
  });

  async function setup(data: BrandFormDialogData): Promise<void> {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [BrandFormDialog],
      providers: [
        { provide: BrandApi, useValue: brandApi },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(BrandFormDialog);
    fixture.detectChanges();
  }
});
