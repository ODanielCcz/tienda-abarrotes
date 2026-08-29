import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { AuthSessionStore } from '../../../../../core/auth/auth-session-store';
import { ConfirmationDialog } from '../../../../../shared/dialogs/confirmation-dialog/confirmation-dialog';
import { BrandFormDialog } from '../../components/brand-form-dialog/brand-form-dialog';
import { BrandApi } from '../../data-access/brand-api';
import { Brand } from '../../models/brand.model';
import { BrandList } from './brand-list';

describe('BrandList maintenance', () => {
  let fixture: ComponentFixture<BrandList>;
  const brand: Brand = {
    id: 'brand-1',
    code: 'COCA_COLA',
    name: 'Coca-Cola',
    status: 'ACTIVE',
    createdAt: '2026-08-20T00:00:00Z',
  };
  const brandApi = {
    list: vi.fn(),
    changeStatus: vi.fn(),
  };
  const sessionStore = { hasPermission: vi.fn() };
  const dialog = { open: vi.fn() };

  afterEach(() => TestBed.resetTestingModule());

  it('opens create and edit forms when permissions are granted', async () => {
    await setup(true);
    const createButton = fixture.nativeElement.querySelector(
      '[data-testid="create-brand"]',
    ) as HTMLElement;
    const editButton = fixture.nativeElement.querySelector(
      '[data-testid="edit-brand-1"]',
    ) as HTMLElement;

    createButton.click();
    editButton.click();

    expect(dialog.open).toHaveBeenCalledWith(
      BrandFormDialog,
      expect.objectContaining({ data: { brand: null } }),
    );
    expect(dialog.open).toHaveBeenCalledWith(
      BrandFormDialog,
      expect.objectContaining({ data: { brand } }),
    );
  });

  it('confirms status changes and refreshes the list', async () => {
    await setup(true);
    dialog.open.mockReturnValueOnce({ afterClosed: () => of(true) });
    brandApi.changeStatus.mockReturnValueOnce(of({ ...brand, status: 'INACTIVE' }));

    const statusButton = fixture.nativeElement.querySelector(
      '[data-testid="status-brand-1"]',
    ) as HTMLElement;
    statusButton.click();

    expect(dialog.open).toHaveBeenCalledWith(ConfirmationDialog, expect.any(Object));
    expect(brandApi.changeStatus).toHaveBeenCalledWith('brand-1', 'INACTIVE');
    expect(brandApi.list).toHaveBeenCalledTimes(2);
  });

  it('hides maintenance actions without write permissions', async () => {
    await setup(false);
    expect(fixture.nativeElement.querySelector('[data-testid="create-brand"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="edit-brand-1"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="status-brand-1"]')).toBeNull();
  });

  async function setup(grantWritePermissions: boolean): Promise<void> {
    vi.clearAllMocks();
    brandApi.list.mockReturnValue(of([brand]));
    sessionStore.hasPermission.mockImplementation(() => grantWritePermissions);
    dialog.open.mockReturnValue({ afterClosed: () => of(brand) });

    await TestBed.configureTestingModule({
      imports: [BrandList],
      providers: [
        { provide: BrandApi, useValue: brandApi },
        { provide: AuthSessionStore, useValue: sessionStore },
        { provide: MatDialog, useValue: dialog },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(BrandList);
    fixture.detectChanges();
    await new Promise((resolve) => setTimeout(resolve, 350));
    fixture.detectChanges();
  }
});
