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
});
