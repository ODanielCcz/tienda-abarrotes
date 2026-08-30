import AxeBuilder from '@axe-core/playwright';
import { expect, Page, test } from '@playwright/test';

const username = process.env['E2E_USERNAME'];
const password = process.env['E2E_PASSWORD'];
const browserErrors = new WeakMap<Page, string[]>();

test.beforeEach(async ({ page }) => {
  const errors: string[] = [];
  browserErrors.set(page, errors);
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(`console: ${message.text()}`);
  });
  page.on('pageerror', (error) => errors.push(`page: ${error.message}`));
});

test.afterEach(async ({ page }) => {
  expect(browserErrors.get(page) ?? []).toEqual([]);
});

async function login(page: Page): Promise<void> {
  test.skip(
    !username || !password,
    'Define E2E_USERNAME y E2E_PASSWORD para ejecutar contra Docker.',
  );

  await page.goto('/login');
  await page.getByLabel('Usuario').fill(username!);
  await page.getByRole('textbox', { name: 'Contraseña', exact: true }).fill(password!);
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole('heading', { name: /Dashboard/i })).toBeVisible();
}

async function ensureSaleableContext(page: Page): Promise<void> {
  const context = await page.evaluate(async () => {
    const session = JSON.parse(sessionStorage.getItem('tienda.auth.session') ?? '{}') as {
      accessToken?: string;
    };
    if (!session.accessToken) throw new Error('No existe una sesión E2E autenticada.');

    const headers = {
      Authorization: `Bearer ${session.accessToken}`,
      'Content-Type': 'application/json',
    };
    const data = async <T>(url: string, init?: RequestInit): Promise<T> => {
      const response = await fetch(url, { ...init, headers: { ...headers, ...init?.headers } });
      const envelope = (await response.json()) as { data: T | null; message?: string };
      if (!response.ok || envelope.data === null) {
        throw new Error(envelope.message ?? `Falló la precondición E2E: ${url}`);
      }
      return envelope.data;
    };

    const branches = await data<Array<{ branchId: string }>>(
      '/api/v1/organization/branches?status=ACTIVE',
    );
    for (const branch of branches) {
      const prices = await data<Array<{ productPresentationId: string }>>(
        `/api/v1/catalog/prices?branchId=${branch.branchId}`,
      );
      if (prices.length === 0) continue;

      const warehouses = await data<Array<{ warehouseId: string }>>(
        `/api/v1/organization/warehouses?branchId=${branch.branchId}&status=ACTIVE`,
      );
      for (const warehouse of warehouses) {
        const stock = await data<
          Array<{ productPresentationId: string; availableQuantity: number }>
        >(`/api/v1/inventory/stock?warehouseId=${warehouse.warehouseId}`);
        const saleable = prices.find((price) =>
          stock.some(
            (balance) =>
              balance.productPresentationId === price.productPresentationId &&
              Number(balance.availableQuantity) > 0,
          ),
        );
        if (saleable) return { branchId: branch.branchId, warehouseId: warehouse.warehouseId };

        const price = prices[0];
        const presentation = await data<{ productId: string }>(
          `/api/v1/catalog/product-presentations/${price.productPresentationId}`,
        );
        const product = await data<{ tracksLots: boolean; tracksExpiration: boolean }>(
          `/api/v1/catalog/products/${presentation.productId}`,
        );
        const suffix = crypto.randomUUID().slice(0, 8).toUpperCase();
        await data('/api/v1/inventory/receipts', {
          method: 'POST',
          body: JSON.stringify({
            warehouseId: warehouse.warehouseId,
            supplierId: null,
            idempotencyKey: crypto.randomUUID(),
            reason: 'Precondición automática Playwright',
            items: [
              {
                productPresentationId: price.productPresentationId,
                lotNumber: product.tracksLots ? `E2E-${suffix}` : null,
                manufacturedAt: product.tracksLots ? new Date().toISOString().slice(0, 10) : null,
                expiresAt: product.tracksExpiration ? '2035-12-31' : null,
                quantity: 5,
                unitCost: 10,
              },
            ],
            pallets: [],
          }),
        });
        return { branchId: branch.branchId, warehouseId: warehouse.warehouseId };
      }
    }
    throw new Error('No existe una sucursal activa con precio GENERAL y almacén activo.');
  });

  await page.evaluate((value) => {
    sessionStorage.setItem('tienda.operational.context', JSON.stringify(value));
  }, context);
  await page.reload();
}

test.describe.serial('Angular Web MVP', () => {
  test('inicia sesión y consulta el catálogo de productos', async ({ page }) => {
    await login(page);
    await page.goto('/catalog/products');

    await expect(page.getByRole('heading', { name: 'Productos' })).toBeVisible();
    await expect(page.getByRole('table')).toBeVisible();
  });

  test('crea una venta con precio del backend y registra el pago', async ({ page }) => {
    await login(page);
    await ensureSaleableContext(page);

    await page.goto('/sales/new');
    await expect(page.getByRole('heading', { name: 'Nueva venta' })).toBeVisible();

    const product = page.locator('button.product-card').first();
    await expect(
      product,
      'El ambiente E2E necesita al menos una presentación activa con stock y precio vigente.',
    ).toBeVisible();
    await product.click();
    await page.getByRole('button', { name: 'Confirmar venta' }).click();

    await expect(page).toHaveURL(/\/sales\/[0-9a-f-]+$/i);
    await expect(page.getByText('Total confirmado')).toBeVisible();

    await page.getByLabel('Método').click();
    await page.getByRole('option', { name: 'Tarjeta' }).click();
    await page.getByRole('button', { name: 'Registrar pago' }).click();

    await expect(page.getByText('PAID', { exact: true }).first()).toBeVisible();
    await expect(page.getByRole('cell', { name: 'CARD' })).toBeVisible();
  });

  test('redirige a acceso restringido cuando falta el permiso', async ({ page }) => {
    const payload = Buffer.from(
      JSON.stringify({
        sub: '00000000-0000-0000-0000-000000000999',
        username: 'e2e_sin_permiso',
        display_name: 'E2E sin permiso',
        roles: [],
        permissions: [],
        authorities: [],
        exp: Math.floor(Date.now() / 1000) + 3600,
      }),
    ).toString('base64url');
    const fakeToken = `e30.${payload}.signature`;

    await page.addInitScript(
      ({ token, expiresAt }) => {
        sessionStorage.setItem(
          'tienda.auth.session',
          JSON.stringify({ accessToken: token, tokenType: 'Bearer', expiresAt }),
        );
      },
      { token: fakeToken, expiresAt: new Date(Date.now() + 3_600_000).toISOString() },
    );

    await page.goto('/identity');
    await expect(page).toHaveURL(/\/forbidden$/);
    await expect(page.getByRole('heading', { name: 'Acceso restringido' })).toBeVisible();
  });
});

test.describe('Accesibilidad esencial', () => {
  test('login no presenta violaciones WCAG A/AA detectables', async ({ page }) => {
    await page.goto('/login');
    await page.keyboard.press('Tab');
    await expect(page.getByLabel('Usuario')).toBeFocused();
    await page.keyboard.press('Tab');
    await expect(page.getByRole('textbox', { name: 'Contraseña', exact: true })).toBeFocused();
    await expect(page.getByText('El usuario es obligatorio')).toHaveCSS('opacity', '1');
    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();

    expect(results.violations).toEqual([]);
  });

  test('dashboard y catálogo no presentan violaciones WCAG A/AA detectables', async ({ page }) => {
    await login(page);
    await page.getByRole('button', { name: 'Activar modo oscuro' }).click();
    await expect(page.locator('html')).toHaveClass(/theme-dark/);
    let results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();
    expect(results.violations).toEqual([]);

    await page.setViewportSize({ width: 768, height: 1024 });
    await page.goto('/catalog/products');
    await expect(page.getByRole('heading', { name: 'Productos' })).toBeVisible();
    await expect(page.locator('.table-shell')).toHaveCSS('overflow-x', 'auto');
    results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();
    expect(results.violations).toEqual([]);
  });
});
