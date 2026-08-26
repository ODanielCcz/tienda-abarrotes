import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { CategoryApi } from '../../data-access/category-api.service';
import { CategoryList } from './category-list';

describe('CategoryList', () => {
  let component: CategoryList;
  let fixture: ComponentFixture<CategoryList>;
  const categoryApiMock = {
    list: vi.fn().mockReturnValue(of([])),
    tree: vi.fn(),
  };

  beforeEach(async () => {
    categoryApiMock.list.mockClear();
    categoryApiMock.tree.mockClear();
    categoryApiMock.tree.mockReturnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [CategoryList],
      providers: [
        {
          provide: CategoryApi,
          useValue: categoryApiMock,
        },
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
});
