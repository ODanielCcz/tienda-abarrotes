# Angular Catalog Read Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cerrar la vista de árbol de categorías y entregar un listado paginado de productos conectado a los contratos reales del backend.

**Architecture:** Mantener la estructura feature-first existente. Las páginas coordinan Signals y Reactive Forms, los servicios `data-access` encapsulan HTTP y los modelos compartidos representan el envelope y la paginación sin crear un CRUD genérico.

**Tech Stack:** Angular 22 standalone, TypeScript 6, Angular Material 22, RxJS 7.8, SCSS, Vitest y `HttpTestingController`.

**Spec:** `docs/superpowers/specs/2026-08-25-angular-catalog-design.md`

## Global Constraints

- La ejecución será manual y guiada; no se crearán subagentes ni hilos auxiliares.
- Conservar Angular Material con SCSS y el tema claro/oscuro existente.
- No agregar Tailwind, NgRx ni servicios genéricos de CRUD.
- Mantener rutas lazy y permisos del JWT.
- Consumir exclusivamente rutas bajo `/api/v1` mediante `API_CONFIG`.
- Mantener `ApiResponse<T>` con `reason` y `correlationId`.
- Ejecutar cada prueba antes de continuar al siguiente task.
- Hacer commits pequeños; no usar `git add .` mientras existan cambios ajenos.

---

## Mapa de archivos

### Archivos modificados

- `apps/web/src/app/features/catalog/categories/models/category.model.ts`: completar el contrato de categoría con `updatedAt`.
- `apps/web/src/app/features/catalog/categories/data-access/category-api.spec.ts`: caracterizar la consulta HTTP del árbol.
- `apps/web/src/app/features/catalog/categories/pages/category-list/category-list.spec.ts`: validar cambio de lista a árbol.
- `apps/web/src/app/features/catalog/categories/pages/category-list/category-list.ts`: conservar coordinación lista/árbol ya implementada.
- `apps/web/src/app/features/catalog/categories/pages/category-list/category-list.html`: conservar renderizado recursivo ya implementado.
- `apps/web/src/app/features/catalog/categories/pages/category-list/category-list.scss`: conservar estilos del árbol ya implementados.
- `apps/web/src/app/app.routes.ts`: registrar `/catalog/products` con `CATALOG_PRODUCT_READ`.
- `apps/web/src/app/layout/app-shell/app-shell.ts`: mostrar Productos en navegación autorizada.

### Archivos creados

- `apps/web/src/app/shared/models/page-response.model.ts`: contrato compartido de paginación del backend.
- `apps/web/src/app/shared/models/api-error-message.ts`: extraer mensaje y correlation id de errores HTTP.
- `apps/web/src/app/shared/models/api-error-message.spec.ts`: pruebas del formateador de errores.
- `apps/web/src/app/features/catalog/products/models/product.model.ts`: contratos de productos, filtros y estados.
- `apps/web/src/app/features/catalog/products/data-access/product-api.ts`: consulta paginada de productos.
- `apps/web/src/app/features/catalog/products/data-access/product-api.spec.ts`: prueba del request y deserialización.
- `apps/web/src/app/features/catalog/products/pages/product-list/product-list.ts`: estado y coordinación de la pantalla.
- `apps/web/src/app/features/catalog/products/pages/product-list/product-list.html`: filtros, tabla y paginador.
- `apps/web/src/app/features/catalog/products/pages/product-list/product-list.scss`: layout adaptable y tokens del tema.
- `apps/web/src/app/features/catalog/products/pages/product-list/product-list.spec.ts`: carga exitosa y error funcional.
- `apps/web/src/app/app.routes.spec.ts`: contrato de ruta y permiso de Productos.

---

### Task 1: Cerrar el árbol de categorías

**Files:**
- Modify: `apps/web/src/app/features/catalog/categories/models/category.model.ts`
- Modify: `apps/web/src/app/features/catalog/categories/data-access/category-api.spec.ts`
- Modify: `apps/web/src/app/features/catalog/categories/pages/category-list/category-list.spec.ts`
- Verify existing changes: `apps/web/src/app/features/catalog/categories/pages/category-list/category-list.ts`
- Verify existing changes: `apps/web/src/app/features/catalog/categories/pages/category-list/category-list.html`
- Verify existing changes: `apps/web/src/app/features/catalog/categories/pages/category-list/category-list.scss`

**Interfaces:**
- Consumes: `CategoryApi.tree(status?: CategoryStatus): Observable<CategoryTreeNode[]>`.
- Produces: `CategoryTreeNode` completo y una vista alternable entre `LIST` y `TREE`.

- [ ] **Step 1: Completar el contrato de categoría**

En `category.model.ts`, agrega `updatedAt`:

```ts
export type CategoryStatus = 'ACTIVE' | 'INACTIVE';

export interface Category {
  id: string;
  parentCategoryId: string | null;
  code: string;
  name: string;
  status: CategoryStatus;
  createdAt: string;
  updatedAt: string;
}

export interface CategoryTreeNode extends Category {
  children: CategoryTreeNode[];
}
```

- [ ] **Step 2: Sustituir el spec superficial de `CategoryApi` por una prueba HTTP real**

Reemplaza `category-api.spec.ts` por:

```ts
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_CONFIG } from '../../../../core/config/api.config';
import { CategoryTreeNode } from '../models/category.model';
import { CategoryApi } from './category-api.service';

describe('CategoryApi', () => {
  let service: CategoryApi;
  let httpTesting: HttpTestingController;

  const categoryTree: CategoryTreeNode[] = [
    {
      id: '00000000-0000-0000-0000-000000000201',
      parentCategoryId: null,
      code: 'BEBIDAS',
      name: 'Bebidas',
      status: 'ACTIVE',
      createdAt: '2026-08-19T00:00:00Z',
      updatedAt: '2026-08-19T00:00:00Z',
      children: [],
    },
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: API_CONFIG,
          useValue: { baseUrl: '/api/v1' },
        },
      ],
    });

    service = TestBed.inject(CategoryApi);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('requests the active category tree', () => {
    service.tree('ACTIVE').subscribe((result) => expect(result).toEqual(categoryTree));

    const request = httpTesting.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/catalog/categories/tree' &&
        candidate.params.get('status') === 'ACTIVE',
    );

    expect(request.request.method).toBe('GET');
    request.flush({
      timestamp: '2026-08-25T00:00:00Z',
      status: 200,
      code: 'CATEGORY_TREE_FOUND',
      reason: 'OK',
      message: 'Árbol consultado',
      data: categoryTree,
      errors: null,
      path: '/api/v1/catalog/categories/tree',
      correlationId: 'category-tree-test',
    });
  });
});
```

- [ ] **Step 3: Completar el fixture del árbol en la página**

En el objeto `tree` de `category-list.spec.ts`, agrega:

```ts
updatedAt: '2026-08-19T00:00:00Z',
```

- [ ] **Step 4: Ejecutar las pruebas de categorías**

```powershell
cd "C:\Users\mine_\OneDrive\Documentos\ODCC\odcc\tienda-abarrotes\apps\web"
npm test -- --watch=false --include src/app/features/catalog/categories/**/*.spec.ts
```

Resultado esperado: los specs de `CategoryApi` y `CategoryList` terminan en `PASS`.

- [ ] **Step 5: Ejecutar el build**

```powershell
npm run build
```

Resultado esperado: `Application bundle generation complete` sin errores.

- [ ] **Step 6: Revisar y crear el commit del árbol**

```powershell
git status --short
git diff --check -- src/app/features/catalog/categories
git add src/app/features/catalog/categories/models/category.model.ts
git add src/app/features/catalog/categories/data-access/category-api.spec.ts
git add src/app/features/catalog/categories/pages/category-list/category-list.ts
git add src/app/features/catalog/categories/pages/category-list/category-list.html
git add src/app/features/catalog/categories/pages/category-list/category-list.scss
git add src/app/features/catalog/categories/pages/category-list/category-list.spec.ts
git diff --cached --check
git diff --cached --stat
git commit -m "feat(web): add category tree view"
```

Resultado esperado: un commit que contiene únicamente los archivos de categorías.

---

### Task 2: Compartir paginación y formatear errores API

**Files:**
- Create: `apps/web/src/app/shared/models/page-response.model.ts`
- Create: `apps/web/src/app/shared/models/api-error-message.ts`
- Create: `apps/web/src/app/shared/models/api-error-message.spec.ts`
- Modify: `apps/web/src/app/features/catalog/categories/data-access/category-api.service.ts`
- Modify: `apps/web/src/app/features/catalog/brands/data-access/brand-api.ts`

**Interfaces:**
- Consumes: `ApiResponse<T>` y `HttpErrorResponse`.
- Produces: `PageResponse<T>` y `apiErrorMessage(error, fallback): string`.

- [ ] **Step 1: Crear los archivos**

```powershell
New-Item -ItemType File -Force src/app/shared/models/page-response.model.ts
New-Item -ItemType File -Force src/app/shared/models/api-error-message.ts
New-Item -ItemType File -Force src/app/shared/models/api-error-message.spec.ts
```

- [ ] **Step 2: Escribir primero la prueba del error API**

Contenido de `api-error-message.spec.ts`:

```ts
import { HttpErrorResponse } from '@angular/common/http';

import { apiErrorMessage } from './api-error-message';

describe('apiErrorMessage', () => {
  it('returns the backend message and correlation id', () => {
    const error = new HttpErrorResponse({
      status: 409,
      error: {
        message: 'El producto ya existe',
        correlationId: 'correlation-409',
      },
    });

    expect(apiErrorMessage(error, 'No fue posible completar la operación.')).toBe(
      'El producto ya existe (Referencia: correlation-409)',
    );
  });

  it('returns the fallback for an unknown error', () => {
    expect(apiErrorMessage(new Error('network'), 'Error de consulta.')).toBe(
      'Error de consulta.',
    );
  });
});
```

- [ ] **Step 3: Ejecutar la prueba para comprobar que falla**

```powershell
npm test -- --watch=false --include src/app/shared/models/api-error-message.spec.ts
```

Resultado esperado: `FAIL` porque `api-error-message.ts` todavía no exporta la función.

- [ ] **Step 4: Implementar los contratos compartidos**

Contenido de `page-response.model.ts`:

```ts
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}
```

Contenido de `api-error-message.ts`:

```ts
import { HttpErrorResponse } from '@angular/common/http';

interface ApiErrorPayload {
  message?: unknown;
  correlationId?: unknown;
}

export function apiErrorMessage(error: unknown, fallback: string): string {
  if (!(error instanceof HttpErrorResponse) || !isApiErrorPayload(error.error)) {
    return fallback;
  }

  const message = typeof error.error.message === 'string' ? error.error.message : fallback;
  const correlationId =
    typeof error.error.correlationId === 'string' ? error.error.correlationId : null;

  return correlationId === null ? message : `${message} (Referencia: ${correlationId})`;
}

function isApiErrorPayload(value: unknown): value is ApiErrorPayload {
  return typeof value === 'object' && value !== null;
}
```

- [ ] **Step 5: Reutilizar `PageResponse` en marcas y categorías**

En ambos APIs elimina la interfaz local `PageResponse<T>` e importa:

```ts
import { PageResponse } from '../../../../shared/models/page-response.model';
```

No cambies las firmas públicas actuales de `BrandApi.list` ni `CategoryApi.list`.

- [ ] **Step 6: Ejecutar pruebas y build**

```powershell
npm test -- --watch=false --include src/app/shared/models/api-error-message.spec.ts
npm test -- --watch=false --include src/app/features/catalog/categories/**/*.spec.ts
npm run build
```

Resultado esperado: todas las pruebas terminan en `PASS` y el build finaliza correctamente.

- [ ] **Step 7: Crear el commit**

```powershell
git add src/app/shared/models/page-response.model.ts
git add src/app/shared/models/api-error-message.ts
git add src/app/shared/models/api-error-message.spec.ts
git add src/app/features/catalog/brands/data-access/brand-api.ts
git add src/app/features/catalog/categories/data-access/category-api.service.ts
git diff --cached --check
git commit -m "refactor(web): share API pagination and error handling"
```

---

### Task 3: Crear contratos y API de productos

**Files:**
- Create: `apps/web/src/app/features/catalog/products/models/product.model.ts`
- Create: `apps/web/src/app/features/catalog/products/data-access/product-api.ts`
- Create: `apps/web/src/app/features/catalog/products/data-access/product-api.spec.ts`

**Interfaces:**
- Consumes: `ApiResponse<PageResponse<Product>>`, `API_CONFIG` y `HttpClient`.
- Produces: `ProductApi.list(filters: ProductListFilters): Observable<PageResponse<Product>>`.

- [ ] **Step 1: Crear carpetas y archivos**

```powershell
New-Item -ItemType Directory -Force src/app/features/catalog/products/models
New-Item -ItemType Directory -Force src/app/features/catalog/products/data-access
New-Item -ItemType File -Force src/app/features/catalog/products/models/product.model.ts
New-Item -ItemType File -Force src/app/features/catalog/products/data-access/product-api.ts
New-Item -ItemType File -Force src/app/features/catalog/products/data-access/product-api.spec.ts
```

- [ ] **Step 2: Definir los contratos de lectura**

Contenido de `product.model.ts`:

```ts
export type ProductType = 'GOODS' | 'SERVICE';
export type ProductStatus = 'ACTIVE' | 'INACTIVE' | 'DISCONTINUED';
export type ProductSortField = 'NAME' | 'CREATED_AT' | 'UPDATED_AT';
export type SortDirection = 'ASC' | 'DESC';

export interface Product {
  id: string;
  categoryId: string;
  brandId: string | null;
  name: string;
  description: string | null;
  productType: ProductType;
  tracksInventory: boolean;
  tracksLots: boolean;
  tracksExpiration: boolean;
  status: ProductStatus;
  createdAt: string;
  updatedAt: string;
}

export interface ProductListFilters {
  page: number;
  size: number;
  search?: string;
  status?: ProductStatus;
  categoryId?: string;
  brandId?: string;
  sortBy?: ProductSortField;
  direction?: SortDirection;
}
```

- [ ] **Step 3: Escribir la prueba del API antes de implementarlo**

Contenido de `product-api.spec.ts`:

```ts
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_CONFIG } from '../../../../core/config/api.config';
import { PageResponse } from '../../../../shared/models/page-response.model';
import { Product } from '../models/product.model';
import { ProductApi } from './product-api';

describe('ProductApi', () => {
  let service: ProductApi;
  let httpTesting: HttpTestingController;

  const page: PageResponse<Product> = {
    content: [
      {
        id: '00000000-0000-0000-0000-000000000401',
        categoryId: '00000000-0000-0000-0000-000000000201',
        brandId: '00000000-0000-0000-0000-000000000301',
        name: 'Arroz 1 kg',
        description: 'Arroz de grano largo',
        productType: 'GOODS',
        tracksInventory: true,
        tracksLots: true,
        tracksExpiration: true,
        status: 'ACTIVE',
        createdAt: '2026-08-20T00:00:00Z',
        updatedAt: '2026-08-20T00:00:00Z',
      },
    ],
    page: 1,
    size: 20,
    totalElements: 21,
    totalPages: 2,
    first: false,
    last: true,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_CONFIG, useValue: { baseUrl: '/api/v1' } },
      ],
    });

    service = TestBed.inject(ProductApi);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('lists products using backend pagination and filters', () => {
    service
      .list({
        page: 1,
        size: 20,
        search: 'arroz',
        status: 'ACTIVE',
        categoryId: '00000000-0000-0000-0000-000000000201',
        brandId: '00000000-0000-0000-0000-000000000301',
        sortBy: 'NAME',
        direction: 'ASC',
      })
      .subscribe((result) => expect(result).toEqual(page));

    const request = httpTesting.expectOne((candidate) => {
      const params = candidate.params;
      return (
        candidate.url === '/api/v1/catalog/products' &&
        params.get('page') === '1' &&
        params.get('size') === '20' &&
        params.get('search') === 'arroz' &&
        params.get('status') === 'ACTIVE' &&
        params.get('categoryId') === '00000000-0000-0000-0000-000000000201' &&
        params.get('brandId') === '00000000-0000-0000-0000-000000000301' &&
        params.get('sortBy') === 'NAME' &&
        params.get('direction') === 'ASC'
      );
    });

    expect(request.request.method).toBe('GET');
    request.flush({
      timestamp: '2026-08-25T00:00:00Z',
      status: 200,
      code: 'PRODUCTS_FOUND',
      reason: 'OK',
      message: 'Productos consultados',
      data: page,
      errors: null,
      path: '/api/v1/catalog/products',
      correlationId: 'products-page-test',
    });
  });
});
```

- [ ] **Step 4: Ejecutar la prueba y confirmar el fallo esperado**

```powershell
npm test -- --watch=false --include src/app/features/catalog/products/data-access/product-api.spec.ts
```

Resultado esperado: `FAIL` porque `ProductApi` todavía no está implementado.

- [ ] **Step 5: Implementar `ProductApi`**

Contenido de `product-api.ts`:

```ts
import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { API_CONFIG } from '../../../../core/config/api.config';
import { ApiResponse } from '../../../../shared/models/api-response.model';
import { PageResponse } from '../../../../shared/models/page-response.model';
import { Product, ProductListFilters } from '../models/product.model';

@Injectable({ providedIn: 'root' })
export class ProductApi {
  private readonly http = inject(HttpClient);
  private readonly apiConfig = inject(API_CONFIG);

  list(filters: ProductListFilters): Observable<PageResponse<Product>> {
    let params = new HttpParams()
      .set('page', filters.page)
      .set('size', filters.size)
      .set('sortBy', filters.sortBy ?? 'NAME')
      .set('direction', filters.direction ?? 'ASC');

    if (filters.search) params = params.set('search', filters.search);
    if (filters.status) params = params.set('status', filters.status);
    if (filters.categoryId) params = params.set('categoryId', filters.categoryId);
    if (filters.brandId) params = params.set('brandId', filters.brandId);

    return this.http
      .get<ApiResponse<PageResponse<Product>>>(`${this.apiConfig.baseUrl}/catalog/products`, {
        params,
      })
      .pipe(
        map((response) =>
          response.data ?? {
            content: [],
            page: filters.page,
            size: filters.size,
            totalElements: 0,
            totalPages: 0,
            first: true,
            last: true,
          },
        ),
      );
  }
}
```

- [ ] **Step 6: Ejecutar prueba y build**

```powershell
npm test -- --watch=false --include src/app/features/catalog/products/data-access/product-api.spec.ts
npm run build
```

Resultado esperado: `PASS` y build exitoso.

- [ ] **Step 7: Crear el commit**

```powershell
git add src/app/features/catalog/products/models/product.model.ts
git add src/app/features/catalog/products/data-access/product-api.ts
git add src/app/features/catalog/products/data-access/product-api.spec.ts
git diff --cached --check
git commit -m "feat(web): add product catalog API"
```

---

### Task 4: Implementar el listado de productos

**Files:**
- Create: `apps/web/src/app/features/catalog/products/pages/product-list/product-list.ts`
- Create: `apps/web/src/app/features/catalog/products/pages/product-list/product-list.html`
- Create: `apps/web/src/app/features/catalog/products/pages/product-list/product-list.scss`
- Create: `apps/web/src/app/features/catalog/products/pages/product-list/product-list.spec.ts`

**Interfaces:**
- Consumes: `ProductApi.list`, `BrandApi.list`, `CategoryApi.list` y `apiErrorMessage`.
- Produces: componente standalone `ProductList`.

- [ ] **Step 1: Crear archivos**

```powershell
New-Item -ItemType Directory -Force src/app/features/catalog/products/pages/product-list
New-Item -ItemType File -Force src/app/features/catalog/products/pages/product-list/product-list.ts
New-Item -ItemType File -Force src/app/features/catalog/products/pages/product-list/product-list.html
New-Item -ItemType File -Force src/app/features/catalog/products/pages/product-list/product-list.scss
New-Item -ItemType File -Force src/app/features/catalog/products/pages/product-list/product-list.spec.ts
```

- [ ] **Step 2: Escribir primero la prueba de la página**

Contenido de `product-list.spec.ts`:

```ts
import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { BrandApi } from '../../../brands/data-access/brand-api';
import { CategoryApi } from '../../../categories/data-access/category-api.service';
import { ProductApi } from '../../data-access/product-api';
import { ProductList } from './product-list';

describe('ProductList', () => {
  let fixture: ComponentFixture<ProductList>;
  const productApi = { list: vi.fn() };
  const brandApi = { list: vi.fn() };
  const categoryApi = { list: vi.fn() };

  beforeEach(async () => {
    productApi.list.mockReturnValue(
      of({
        content: [
          {
            id: 'product-1',
            categoryId: 'category-1',
            brandId: 'brand-1',
            name: 'Arroz 1 kg',
            description: null,
            productType: 'GOODS',
            tracksInventory: true,
            tracksLots: true,
            tracksExpiration: true,
            status: 'ACTIVE',
            createdAt: '2026-08-20T00:00:00Z',
            updatedAt: '2026-08-20T00:00:00Z',
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
        first: true,
        last: true,
      }),
    );
    brandApi.list.mockReturnValue(
      of([{ id: 'brand-1', code: 'MARCA', name: 'Marca', status: 'ACTIVE', createdAt: '' }]),
    );
    categoryApi.list.mockReturnValue(
      of([
        {
          id: 'category-1',
          parentCategoryId: null,
          code: 'ABARROTES',
          name: 'Abarrotes',
          status: 'ACTIVE',
          createdAt: '',
          updatedAt: '',
        },
      ]),
    );

    await TestBed.configureTestingModule({
      imports: [ProductList],
      providers: [
        { provide: ProductApi, useValue: productApi },
        { provide: BrandApi, useValue: brandApi },
        { provide: CategoryApi, useValue: categoryApi },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProductList);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('renders products returned by the API', () => {
    expect(productApi.list).toHaveBeenCalledWith(
      expect.objectContaining({ page: 0, size: 20, sortBy: 'NAME', direction: 'ASC' }),
    );
    expect(fixture.nativeElement.textContent).toContain('Arroz 1 kg');
    expect(fixture.nativeElement.textContent).toContain('Marca');
    expect(fixture.nativeElement.textContent).toContain('Abarrotes');
  });

  it('shows the backend error reference', async () => {
    productApi.list.mockReturnValueOnce(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 500,
            error: { message: 'Error de productos', correlationId: 'ref-1' },
          }),
      ),
    );

    const component = fixture.componentInstance as unknown as { refresh(): void };
    component.refresh();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain(
      'Error de productos (Referencia: ref-1)',
    );
  });
});
```

- [ ] **Step 3: Ejecutar el spec para comprobar que falla**

```powershell
npm test -- --watch=false --include src/app/features/catalog/products/pages/product-list/product-list.spec.ts
```

Resultado esperado: `FAIL` porque `ProductList` no existe.

- [ ] **Step 4: Implementar la clase de la página**

Contenido de `product-list.ts`:

```ts
import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { catchError, debounceTime, distinctUntilChanged, map, merge, of, startWith, Subject, switchMap, tap } from 'rxjs';

import { apiErrorMessage } from '../../../../../shared/models/api-error-message';
import { BrandApi } from '../../../brands/data-access/brand-api';
import { Brand } from '../../../brands/models/brand.model';
import { CategoryApi } from '../../../categories/data-access/category-api.service';
import { Category } from '../../../categories/models/category.model';
import { ProductApi } from '../../data-access/product-api';
import { Product, ProductStatus } from '../../models/product.model';

@Component({
  selector: 'app-product-list',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatTableModule,
  ],
  templateUrl: './product-list.html',
  styleUrl: './product-list.scss',
})
export class ProductList {
  private readonly productApi = inject(ProductApi);
  private readonly brandApi = inject(BrandApi);
  private readonly categoryApi = inject(CategoryApi);
  private readonly destroyRef = inject(DestroyRef);
  private readonly queryTrigger = new Subject<void>();

  protected readonly products = signal<Product[]>([]);
  protected readonly brands = signal<Brand[]>([]);
  protected readonly categories = signal<Category[]>([]);
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(20);
  protected readonly totalElements = signal(0);
  protected readonly displayedColumns = ['name', 'brand', 'category', 'type', 'policies', 'status', 'updatedAt'];

  protected readonly searchControl = new FormControl('', { nonNullable: true });
  protected readonly statusControl = new FormControl<ProductStatus | 'ALL'>('ALL', { nonNullable: true });
  protected readonly brandControl = new FormControl('ALL', { nonNullable: true });
  protected readonly categoryControl = new FormControl('ALL', { nonNullable: true });

  constructor() {
    merge(
      this.searchControl.valueChanges.pipe(debounceTime(300), distinctUntilChanged()),
      this.statusControl.valueChanges.pipe(distinctUntilChanged()),
      this.brandControl.valueChanges.pipe(distinctUntilChanged()),
      this.categoryControl.valueChanges.pipe(distinctUntilChanged()),
    )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.pageIndex.set(0);
        this.queryTrigger.next();
      });

    this.queryTrigger
      .pipe(
        startWith(undefined),
        tap(() => {
          this.loading.set(true);
          this.errorMessage.set(null);
        }),
        switchMap(() =>
          this.productApi.list(this.currentFilters()).pipe(
            map((page) => ({ ok: true as const, page })),
            catchError((error: unknown) =>
              of({
                ok: false as const,
                message: apiErrorMessage(error, 'No fue posible consultar los productos.'),
              }),
            ),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        this.loading.set(false);
        if (!result.ok) {
          this.products.set([]);
          this.totalElements.set(0);
          this.errorMessage.set(result.message);
          return;
        }
        this.products.set(result.page.content);
        this.totalElements.set(result.page.totalElements);
      });

    this.brandApi.list({ status: 'ACTIVE' }).subscribe((brands) => this.brands.set(brands));
    this.categoryApi
      .list({ status: 'ACTIVE' })
      .subscribe((categories) => this.categories.set(categories));
  }

  protected refresh(): void {
    this.queryTrigger.next();
  }

  protected pageChanged(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.queryTrigger.next();
  }

  protected clearFilters(): void {
    this.searchControl.setValue('', { emitEvent: false });
    this.statusControl.setValue('ALL', { emitEvent: false });
    this.brandControl.setValue('ALL', { emitEvent: false });
    this.categoryControl.setValue('ALL', { emitEvent: false });
    this.pageIndex.set(0);
    this.queryTrigger.next();
  }

  protected brandName(brandId: string | null): string {
    if (brandId === null) return 'Sin marca';
    return this.brands().find((brand) => brand.id === brandId)?.name ?? 'Marca no disponible';
  }

  protected categoryName(categoryId: string): string {
    return this.categories().find((category) => category.id === categoryId)?.name ?? 'Categoría no disponible';
  }

  private currentFilters() {
    return {
      page: this.pageIndex(),
      size: this.pageSize(),
      search: this.searchControl.value.trim() || undefined,
      status: this.statusControl.value === 'ALL' ? undefined : this.statusControl.value,
      brandId: this.brandControl.value === 'ALL' ? undefined : this.brandControl.value,
      categoryId: this.categoryControl.value === 'ALL' ? undefined : this.categoryControl.value,
      sortBy: 'NAME' as const,
      direction: 'ASC' as const,
    };
  }
}
```

- [ ] **Step 5: Implementar la plantilla**

Contenido de `product-list.html`:

```html
<section class="page">
  <header class="page-header">
    <div>
      <p class="eyebrow">Catálogo</p>
      <h1>Productos</h1>
      <p>Consulta productos, políticas operativas y estado comercial.</p>
    </div>

    <button mat-stroked-button type="button" (click)="refresh()">
      <mat-icon>refresh</mat-icon>
      Actualizar
    </button>
  </header>

  <div class="filters">
    <mat-form-field appearance="outline">
      <mat-label>Buscar</mat-label>
      <mat-icon matPrefix>search</mat-icon>
      <input matInput type="search" [formControl]="searchControl" placeholder="Nombre o SKU" />
    </mat-form-field>

    <mat-form-field appearance="outline">
      <mat-label>Estado</mat-label>
      <mat-select [formControl]="statusControl">
        <mat-option value="ALL">Todos</mat-option>
        <mat-option value="ACTIVE">Activos</mat-option>
        <mat-option value="INACTIVE">Inactivos</mat-option>
        <mat-option value="DISCONTINUED">Descontinuados</mat-option>
      </mat-select>
    </mat-form-field>

    <mat-form-field appearance="outline">
      <mat-label>Marca</mat-label>
      <mat-select [formControl]="brandControl">
        <mat-option value="ALL">Todas</mat-option>
        @for (brand of brands(); track brand.id) {
          <mat-option [value]="brand.id">{{ brand.name }}</mat-option>
        }
      </mat-select>
    </mat-form-field>

    <mat-form-field appearance="outline">
      <mat-label>Categoría</mat-label>
      <mat-select [formControl]="categoryControl">
        <mat-option value="ALL">Todas</mat-option>
        @for (category of categories(); track category.id) {
          <mat-option [value]="category.id">{{ category.name }}</mat-option>
        }
      </mat-select>
    </mat-form-field>

    <button mat-button type="button" (click)="clearFilters()">Limpiar</button>
  </div>

  @if (loading()) {
    <div class="state"><mat-spinner diameter="42" /></div>
  } @else if (errorMessage()) {
    <p class="error-state" role="alert">{{ errorMessage() }}</p>
  } @else if (products().length === 0) {
    <p class="state">No hay productos que coincidan con los filtros.</p>
  } @else {
    <div class="table-shell">
      <table mat-table [dataSource]="products()">
        <ng-container matColumnDef="name">
          <th mat-header-cell *matHeaderCellDef>Producto</th>
          <td mat-cell *matCellDef="let product">
            <strong>{{ product.name }}</strong>
            <small>{{ product.description || 'Sin descripción' }}</small>
          </td>
        </ng-container>

        <ng-container matColumnDef="brand">
          <th mat-header-cell *matHeaderCellDef>Marca</th>
          <td mat-cell *matCellDef="let product">{{ brandName(product.brandId) }}</td>
        </ng-container>

        <ng-container matColumnDef="category">
          <th mat-header-cell *matHeaderCellDef>Categoría</th>
          <td mat-cell *matCellDef="let product">{{ categoryName(product.categoryId) }}</td>
        </ng-container>

        <ng-container matColumnDef="type">
          <th mat-header-cell *matHeaderCellDef>Tipo</th>
          <td mat-cell *matCellDef="let product">{{ product.productType === 'GOODS' ? 'Producto' : 'Servicio' }}</td>
        </ng-container>

        <ng-container matColumnDef="policies">
          <th mat-header-cell *matHeaderCellDef>Control</th>
          <td mat-cell *matCellDef="let product">
            <span class="policy">{{ product.tracksInventory ? 'Inventario' : 'Sin inventario' }}</span>
            @if (product.tracksLots) { <span class="policy">Lotes</span> }
            @if (product.tracksExpiration) { <span class="policy">Caducidad</span> }
          </td>
        </ng-container>

        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Estado</th>
          <td mat-cell *matCellDef="let product">
            <span class="status-pill" [class.inactive]="product.status !== 'ACTIVE'">{{ product.status }}</span>
          </td>
        </ng-container>

        <ng-container matColumnDef="updatedAt">
          <th mat-header-cell *matHeaderCellDef>Actualizado</th>
          <td mat-cell *matCellDef="let product">{{ product.updatedAt | date: 'dd/MM/yyyy HH:mm' }}</td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: displayedColumns"></tr>
      </table>
    </div>

    <mat-paginator
      [length]="totalElements()"
      [pageIndex]="pageIndex()"
      [pageSize]="pageSize()"
      [pageSizeOptions]="[10, 20, 50]"
      (page)="pageChanged($event)"
      aria-label="Paginación de productos"
    />
  }
</section>
```

- [ ] **Step 6: Implementar estilos**

Contenido de `product-list.scss`:

```scss
:host { display: block; }
.page { display: grid; gap: 1.25rem; }
.page-header { display: flex; justify-content: space-between; gap: 1rem; align-items: flex-start; }
.page-header h1 { margin: 0; font-size: 2rem; }
.page-header p { margin: 0.25rem 0 0; color: var(--mat-sys-on-surface-variant); }
.eyebrow { color: var(--mat-sys-primary) !important; font-weight: 700; text-transform: uppercase; letter-spacing: 0.08em; }
.filters { display: grid; grid-template-columns: minmax(15rem, 2fr) repeat(3, minmax(10rem, 1fr)) auto; gap: 0.75rem; align-items: center; }
.filters mat-form-field { width: 100%; }
.table-shell { overflow-x: auto; border: 1px solid var(--mat-sys-outline-variant); border-radius: 0.75rem; }
table { width: 100%; min-width: 70rem; }
td strong, td small { display: block; }
td small { color: var(--mat-sys-on-surface-variant); margin-top: 0.2rem; }
.policy { display: inline-block; margin: 0.15rem; padding: 0.2rem 0.45rem; border-radius: 1rem; background: var(--mat-sys-secondary-container); color: var(--mat-sys-on-secondary-container); font-size: 0.75rem; }
.status-pill { display: inline-block; padding: 0.25rem 0.55rem; border-radius: 1rem; background: var(--mat-sys-primary-container); color: var(--mat-sys-on-primary-container); font-size: 0.75rem; font-weight: 700; }
.status-pill.inactive { background: var(--mat-sys-surface-container-highest); color: var(--mat-sys-on-surface-variant); }
.state { display: grid; min-height: 12rem; place-items: center; color: var(--mat-sys-on-surface-variant); }
.error-state { padding: 1rem; border-radius: 0.5rem; background: var(--mat-sys-error-container); color: var(--mat-sys-on-error-container); }
@media (max-width: 1100px) { .filters { grid-template-columns: repeat(2, minmax(12rem, 1fr)); } }
@media (max-width: 700px) { .page-header { flex-direction: column; } .filters { grid-template-columns: 1fr; } }
```

- [ ] **Step 7: Ejecutar prueba y build**

```powershell
npm test -- --watch=false --include src/app/features/catalog/products/pages/product-list/product-list.spec.ts
npm run build
```

Resultado esperado: `PASS` y build exitoso.

- [ ] **Step 8: Crear el commit**

```powershell
git add src/app/features/catalog/products/pages/product-list
git diff --cached --check
git commit -m "feat(web): add paginated product list"
```

---

### Task 5: Registrar ruta, permiso y navegación

**Files:**
- Modify: `apps/web/src/app/app.routes.ts`
- Modify: `apps/web/src/app/layout/app-shell/app-shell.ts`
- Create: `apps/web/src/app/app.routes.spec.ts`

**Interfaces:**
- Consumes: `ProductList` lazy y `permissionGuard`.
- Produces: `/catalog/products` protegido por `CATALOG_PRODUCT_READ`.

- [ ] **Step 1: Crear primero la prueba de ruta**

```powershell
New-Item -ItemType File -Force src/app/app.routes.spec.ts
```

Contenido:

```ts
import { routes } from './app.routes';

describe('application routes', () => {
  it('protects the product catalog with the read permission', () => {
    const shell = routes.find((route) => route.path === '');
    const productRoute = shell?.children?.find((route) => route.path === 'catalog/products');

    expect(productRoute).toBeDefined();
    expect(productRoute?.data?.['permissions']).toEqual(['CATALOG_PRODUCT_READ']);
    expect(productRoute?.loadComponent).toBeDefined();
  });
});
```

- [ ] **Step 2: Ejecutar la prueba y confirmar el fallo**

```powershell
npm test -- --watch=false --include src/app/app.routes.spec.ts
```

Resultado esperado: `FAIL` porque la ruta todavía no existe.

- [ ] **Step 3: Agregar la ruta lazy**

En `app.routes.ts`, después de categorías, agrega:

```ts
{
  path: 'catalog/products',
  canActivate: [permissionGuard],
  data: { permissions: ['CATALOG_PRODUCT_READ'] },
  loadComponent: () =>
    import('./features/catalog/products/pages/product-list/product-list').then(
      (module) => module.ProductList,
    ),
},
```

Cambia el redirect de `catalog`:

```ts
{
  path: 'catalog',
  redirectTo: 'catalog/products',
  pathMatch: 'full',
},
```

- [ ] **Step 4: Agregar Productos al menú**

En `navigationItems`, después de Categorías, agrega:

```ts
{
  label: 'Productos',
  icon: 'inventory_2',
  route: '/catalog/products',
  permissions: ['CATALOG_PRODUCT_READ'],
},
```

- [ ] **Step 5: Ejecutar todas las pruebas y build**

```powershell
npm test -- --watch=false
npm run build
```

Resultado esperado: todos los specs terminan en `PASS` y la aplicación compila sin warnings de plantilla.

- [ ] **Step 6: Prueba manual**

```powershell
npm start
```

Abrir `http://localhost:4200`, iniciar sesión y validar:

1. El menú muestra Productos para el administrador.
2. `/catalog/products` carga datos reales.
3. Búsqueda y filtros generan nuevas consultas.
4. El paginador conserva página y tamaño.
5. Modo oscuro mantiene contraste legible.
6. Un usuario sin `CATALOG_PRODUCT_READ` recibe acceso restringido y no ve el menú.

- [ ] **Step 7: Crear el commit**

```powershell
git add src/app/app.routes.ts
git add src/app/app.routes.spec.ts
git add src/app/layout/app-shell/app-shell.ts
git diff --cached --check
git commit -m "feat(web): expose protected product catalog route"
```

---

## Validación de la entrega

Ejecutar desde `apps/web`:

```powershell
npm test -- --watch=false
npm run build
git status --short
git log --oneline -5
```

Aceptación:

- árbol de categorías disponible en lista y árbol;
- request del árbol probado con filtro de estado;
- listado de productos usa paginación real del backend;
- filtros incluyen búsqueda, estado, marca y categoría;
- errores muestran mensaje funcional y correlation id cuando existe;
- ruta lazy protegida por `CATALOG_PRODUCT_READ`;
- menú oculta Productos sin permiso;
- suite y build verdes;
- cada task queda en un commit independiente.

## Siguiente plan

Después de validar esta entrega se escribirá `Angular Catalog Product Detail and Presentations`, que agregará:

- `/catalog/products/:productId`;
- detalle del producto;
- listado de presentaciones;
- navegación desde la tabla;
- permisos de lectura correspondientes.
