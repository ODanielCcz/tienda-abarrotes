import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { CatalogReferenceApi } from '../../data-access/catalog-reference-api';
import { ProductApi } from '../../data-access/product-api';
import { ProductPresentation } from '../../models/product.model';
import { PresentationFormDialog } from './presentation-form-dialog';

describe('PresentationFormDialog', () => {
  const presentation: ProductPresentation = {
    id: 'presentation-1',
    productId: 'product-1',
    unitId: 'unit-1',
    taxId: 'tax-1',
    sku: 'ARROZ-1KG',
    name: 'Bolsa 1 kg',
    conversionFactor: 1,
    netContent: 1000,
    minimumStock: 10,
    status: 'ACTIVE',
    createdAt: '',
    updatedAt: '',
  };
  const productApi = {
    createPresentation: vi.fn(),
    updatePresentation: vi.fn(),
  };
  const references = {
    listUnits: vi.fn(),
    listTaxes: vi.fn(),
  };
  const dialogRef = { close: vi.fn() };

  afterEach(() => TestBed.resetTestingModule());

  it('loads reference options and creates a normalized presentation', async () => {
    const fixture = await setup(null);
    const component = fixture.componentInstance as unknown as {
      form: { setValue(value: Record<string, unknown>): void };
      submit(): void;
    };
    productApi.createPresentation.mockReturnValueOnce(of(presentation));

    component.form.setValue({
      unitId: 'unit-1',
      taxId: 'tax-1',
      sku: ' arroz-1kg ',
      name: ' Bolsa 1 kg ',
      conversionFactor: 1,
      netContent: 1000,
      minimumStock: 10,
    });
    component.submit();

    expect(references.listUnits).toHaveBeenCalled();
    expect(references.listTaxes).toHaveBeenCalled();
    expect(productApi.createPresentation).toHaveBeenCalledWith('product-1', {
      unitId: 'unit-1',
      taxId: 'tax-1',
      sku: 'ARROZ-1KG',
      name: 'Bolsa 1 kg',
      conversionFactor: 1,
      netContent: 1000,
      minimumStock: 10,
    });
    expect(dialogRef.close).toHaveBeenCalledWith(presentation);
  });

  it('updates the supplied presentation', async () => {
    const fixture = await setup(presentation);
    const component = fixture.componentInstance as unknown as { submit(): void };
    productApi.updatePresentation.mockReturnValueOnce(of({ ...presentation, name: 'Bolsa premium' }));

    const form = (fixture.componentInstance as unknown as {
      form: { patchValue(value: Record<string, unknown>): void };
    }).form;
    form.patchValue({ name: 'Bolsa premium' });
    component.submit();

    expect(productApi.updatePresentation).toHaveBeenCalledWith(
      presentation.id,
      expect.objectContaining({ name: 'Bolsa premium' }),
    );
  });

  it('keeps the form open and displays the backend correlation reference', async () => {
    const fixture = await setup(null);
    productApi.createPresentation.mockReturnValueOnce(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { message: 'El SKU ya existe', correlationId: 'sku-conflict-1' },
          }),
      ),
    );
    const component = fixture.componentInstance as unknown as {
      form: { patchValue(value: Record<string, unknown>): void };
      submit(): void;
    };
    component.form.patchValue({ unitId: 'unit-1', sku: 'SKU-1', name: 'Pieza' });

    component.submit();
    fixture.detectChanges();

    expect(dialogRef.close).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain(
      'El SKU ya existe (Referencia: sku-conflict-1)',
    );
  });

  async function setup(current: ProductPresentation | null): Promise<ComponentFixture<PresentationFormDialog>> {
    vi.clearAllMocks();
    references.listUnits.mockReturnValue(
      of([{ id: 'unit-1', code: 'PZA', name: 'Pieza', symbol: 'pza', quantityScale: 0 }]),
    );
    references.listTaxes.mockReturnValue(
      of([{ id: 'tax-1', code: 'IVA16', name: 'IVA 16%', rate: 0.16 }]),
    );

    await TestBed.configureTestingModule({
      imports: [PresentationFormDialog],
      providers: [
        { provide: ProductApi, useValue: productApi },
        { provide: CatalogReferenceApi, useValue: references },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: { productId: 'product-1', presentation: current } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(PresentationFormDialog);
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture;
  }
});
