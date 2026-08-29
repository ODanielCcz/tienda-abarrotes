import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormArray } from '@angular/forms';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { AuthSessionStore } from '../../../../core/auth/auth-session-store';
import { PurchasingApi } from '../../data-access/purchasing-api';
import { PurchaseDetail } from './purchase-detail';

describe('PurchaseDetail', () => {
  let fixture: ComponentFixture<PurchaseDetail>;
  const api = { getPurchase: vi.fn(), confirmPurchase: vi.fn(), receivePurchase: vi.fn() };
  const session = { hasPermission: vi.fn(() => true) };
  const purchase = {
    purchaseId: 'purchase-1',
    branchId: 'branch-1',
    warehouseId: 'warehouse-1',
    supplierId: 'supplier-1',
    supplierDocument: 'FAC-1',
    status: 'CONFIRMED',
    paymentStatus: 'PENDING',
    currencyCode: 'MXN',
    subtotal: 200,
    discountTotal: 0,
    taxTotal: 32,
    total: 232,
    idempotencyKey: 'key',
    purchasedAt: '',
    confirmedAt: '',
    createdAt: '',
    items: [
      {
        purchaseItemId: 'item-1',
        purchaseId: 'purchase-1',
        productPresentationId: 'presentation-1',
        lotId: null,
        productNameSnapshot: 'Arroz - Bolsa',
        skuSnapshot: 'ARROZ',
        quantity: 10,
        receivedQuantity: 2,
        unitCost: 20,
        discountAmount: 0,
        taxAmount: 32,
        lineTotal: 232,
      },
    ],
  };

  beforeEach(async () => {
    api.getPurchase.mockReturnValue(of(purchase));
    api.receivePurchase.mockReturnValue(of({ receiptId: 'receipt-1' }));
    await TestBed.configureTestingModule({
      imports: [PurchaseDetail],
      providers: [
        { provide: PurchasingApi, useValue: api },
        { provide: AuthSessionStore, useValue: session },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ purchaseId: 'purchase-1' }) } },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(PurchaseDetail);
    fixture.detectChanges();
  });

  it('receives only the outstanding purchase quantity with a stable key', () => {
    const component = fixture.componentInstance as unknown as {
      receiptItems: FormArray;
      receive(): void;
      receiptIdempotencyKey: string;
    };
    const operationKey = component.receiptIdempotencyKey;
    expect(component.receiptItems.at(0).value.quantity).toBe(8);
    component.receiptItems.at(0).patchValue({ lotNumber: 'LOT-001', expiresAt: '2027-08-01' });

    component.receive();

    expect(api.receivePurchase).toHaveBeenCalledWith(
      'purchase-1',
      expect.objectContaining({
        idempotencyKey: operationKey,
        items: [expect.objectContaining({ purchaseItemId: 'item-1', quantity: 8 })],
      }),
    );
    expect(component.receiptIdempotencyKey).not.toBe(operationKey);
  });
});
