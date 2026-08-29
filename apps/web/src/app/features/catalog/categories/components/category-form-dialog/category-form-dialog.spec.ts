import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormGroup } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { CategoryApi } from '../../data-access/category-api.service';
import { Category } from '../../models/category.model';
import {
  CategoryFormDialog,
  CategoryFormDialogData,
  eligibleCategoryParents,
} from './category-form-dialog';

describe('CategoryFormDialog', () => {
  let fixture: ComponentFixture<CategoryFormDialog>;
  const categoryApi = { create: vi.fn(), update: vi.fn() };
  const dialogRef = { close: vi.fn() };
  const root: Category = {
    id: 'root-1',
    parentCategoryId: null,
    code: 'BEBIDAS',
    name: 'Bebidas',
    status: 'ACTIVE',
    createdAt: '',
    updatedAt: '',
  };
  const child: Category = {
    ...root,
    id: 'child-1',
    parentCategoryId: root.id,
    code: 'REFRESCOS',
    name: 'Refrescos',
  };
  const unrelated: Category = {
    ...root,
    id: 'other-1',
    code: 'ABARROTES',
    name: 'Abarrotes',
  };

  afterEach(() => TestBed.resetTestingModule());

  it('excludes the edited category and its descendants from possible parents', () => {
    expect(eligibleCategoryParents([root, child, unrelated], root.id).map((item) => item.id)).toEqual([
      'other-1',
    ]);
  });

  it('creates a normalized child category', async () => {
    await setup({ category: null, categories: [root, child, unrelated] });
    const component = fixture.componentInstance as unknown as {
      form: FormGroup;
      submit(): void;
    };
    component.form.setValue({ code: ' jugos ', name: ' Jugos ', parentCategoryId: root.id });
    categoryApi.create.mockReturnValueOnce(
      of({ ...child, id: 'child-2', code: 'JUGOS', name: 'Jugos' }),
    );

    component.submit();

    expect(categoryApi.create).toHaveBeenCalledWith({
      code: 'JUGOS',
      name: 'Jugos',
      parentCategoryId: root.id,
    });
    expect(dialogRef.close).toHaveBeenCalled();
  });

  it('keeps the form data when the backend rejects a hierarchy cycle', async () => {
    await setup({ category: child, categories: [root, child, unrelated] });
    const component = fixture.componentInstance as unknown as {
      form: FormGroup;
      submit(): void;
    };
    component.form.setValue({
      code: child.code,
      name: child.name,
      parentCategoryId: unrelated.id,
    });
    categoryApi.update.mockReturnValueOnce(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: { message: 'La jerarquía genera un ciclo', correlationId: 'category-cycle-ref' },
          }),
      ),
    );

    component.submit();
    fixture.detectChanges();

    expect(dialogRef.close).not.toHaveBeenCalled();
    expect(component.form.value.parentCategoryId).toBe(unrelated.id);
    expect(fixture.nativeElement.textContent).toContain(
      'La jerarquía genera un ciclo (Referencia: category-cycle-ref)',
    );
  });

  async function setup(data: CategoryFormDialogData): Promise<void> {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [CategoryFormDialog],
      providers: [
        { provide: CategoryApi, useValue: categoryApi },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CategoryFormDialog);
    fixture.detectChanges();
  }
});
