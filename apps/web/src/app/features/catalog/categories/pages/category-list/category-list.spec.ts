import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { AuthSessionStore } from '../../../../../core/auth/auth-session-store';
import { ConfirmationDialog } from '../../../../../shared/dialogs/confirmation-dialog/confirmation-dialog';
import { CategoryFormDialog } from '../../components/category-form-dialog/category-form-dialog';
import { CategoryApi } from '../../data-access/category-api.service';
import { Category } from '../../models/category.model';
import { CategoryList } from './category-list';

describe('CategoryList', () => {
  let component: CategoryList;
  let fixture: ComponentFixture<CategoryList>;
  const category: Category = {
    id: 'category-1',
    parentCategoryId: null,
    code: 'BEBIDAS',
    name: 'Bebidas',
    status: 'ACTIVE',
    createdAt: '2026-08-19T00:00:00Z',
    updatedAt: '2026-08-19T00:00:00Z',
  };
  const categoryApiMock = {
    list: vi.fn(),
    tree: vi.fn(),
    changeStatus: vi.fn(),
  };
  const sessionStoreMock = { hasPermission: vi.fn() };
  const dialogMock = { open: vi.fn() };

  beforeEach(async () => {
    categoryApiMock.list.mockClear();
    categoryApiMock.tree.mockClear();
    categoryApiMock.changeStatus.mockClear();
    categoryApiMock.list.mockReturnValue(of([category]));
    categoryApiMock.tree.mockReturnValue(of([]));
    sessionStoreMock.hasPermission.mockReturnValue(true);
    dialogMock.open.mockReset();
    dialogMock.open.mockReturnValue({ afterClosed: () => of(category) });

    await TestBed.configureTestingModule({
      imports: [CategoryList],
      providers: [
        {
          provide: CategoryApi,
          useValue: categoryApiMock,
        },
        { provide: AuthSessionStore, useValue: sessionStoreMock },
        { provide: MatDialog, useValue: dialogMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CategoryList);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('loads the category tree when the tree view is selected', async () => {
    const tree = [
      {
        id: 'root-1',
        parentCategoryId: null,
        code: 'BEBIDAS',
        name: 'Bebidas',
        status: 'ACTIVE',
        createdAt: '2026-08-19T00:00:00Z',
        updatedAt: '2026-08-19T00:00:00Z',
        children: [],
      },
    ];
    categoryApiMock.tree.mockReturnValue(of(tree));

    const viewModeControl = (
      component as unknown as {
        viewModeControl: { setValue(value: 'LIST' | 'TREE'): void };
      }
    ).viewModeControl;

    viewModeControl.setValue('TREE');
    await fixture.whenStable();

    expect(categoryApiMock.tree).toHaveBeenCalledWith(undefined);
    expect(
      (component as unknown as { categoryTree: () => unknown[] }).categoryTree(),
    ).toEqual(tree);
  });

  it('opens create and edit forms with current category options', () => {
    fixture.detectChanges();
    const createButton = fixture.nativeElement.querySelector(
      '[data-testid="create-category"]',
    ) as HTMLElement;
    const editButton = fixture.nativeElement.querySelector(
      '[data-testid="edit-category-1"]',
    ) as HTMLElement;

    createButton.click();
    editButton.click();

    expect(dialogMock.open).toHaveBeenCalledWith(
      CategoryFormDialog,
      expect.objectContaining({ data: { category: null, categories: [category] } }),
    );
    expect(dialogMock.open).toHaveBeenCalledWith(
      CategoryFormDialog,
      expect.objectContaining({ data: { category, categories: [category] } }),
    );
  });

  it('confirms category status changes', () => {
    dialogMock.open.mockReturnValueOnce({ afterClosed: () => of(true) });
    categoryApiMock.changeStatus.mockReturnValueOnce(of({ ...category, status: 'INACTIVE' }));
    fixture.detectChanges();

    const button = fixture.nativeElement.querySelector(
      '[data-testid="status-category-1"]',
    ) as HTMLElement;
    button.click();

    expect(dialogMock.open).toHaveBeenCalledWith(ConfirmationDialog, expect.any(Object));
    expect(categoryApiMock.changeStatus).toHaveBeenCalledWith(category.id, 'INACTIVE');
  });
});

describe('CategoryList permissions', () => {
  it('hides maintenance actions without write permissions', async () => {
    const categoryApi = {
      list: vi.fn().mockReturnValue(
        of([
          {
            id: 'category-1',
            parentCategoryId: null,
            code: 'BEBIDAS',
            name: 'Bebidas',
            status: 'ACTIVE',
            createdAt: '',
            updatedAt: '',
          },
        ]),
      ),
      tree: vi.fn().mockReturnValue(of([])),
      changeStatus: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [CategoryList],
      providers: [
        { provide: CategoryApi, useValue: categoryApi },
        { provide: AuthSessionStore, useValue: { hasPermission: () => false } },
        { provide: MatDialog, useValue: { open: vi.fn() } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(CategoryList);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="create-category"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="edit-category-1"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="status-category-1"]')).toBeNull();
  });
});
