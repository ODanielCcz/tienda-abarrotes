import { test, expect, type Page } from '../../../apps/web/node_modules/@playwright/test';
import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { performance } from 'node:perf_hooks';
const { credentials, artifactDirectory, sanitize } = require('./perf-web-environment.cjs');
const { summarizeCycles } = require('./web-metrics.cjs');
const contract = require('../fixtures/fixture-contract.json');
const cycles = process.env['PERF_WEB_QUICK'] === 'true' ? 2 : 50;

async function authenticate(page: Page, admin = false) {
  const account = credentials(admin);
  await page.goto('/login');
  await page.getByLabel('Usuario', { exact: true }).fill(account.username);
  await page.getByLabel('Contraseña', { exact: true }).fill(account.password);
  await page.getByRole('button', { name: 'Iniciar sesión', exact: true }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
}

async function login(page: Page, admin = false) {
  await authenticate(page, admin);
  const viewport = page.viewportSize();
  // Only setup is widened: preserve and separately report the narrow-screen defect.
  if (viewport && viewport.width <= 1050) await page.setViewportSize({ width: 1440, height: 900 });
  await page.getByRole('combobox', { name: 'Sucursal', exact: true }).click();
  await page.getByRole('option', { name: contract.branch.name, exact: true }).click();
  await page.getByRole('combobox', { name: 'Almacén', exact: true }).click();
  await page.getByRole('option', { name: contract.warehouses[0].name, exact: true }).click();
  await page.locator('app-dashboard').getByRole('button', { name: 'Actualizar', exact: true }).click();
  await dashboardReady(page);
  if (viewport) await page.setViewportSize(viewport);
}

async function dashboardReady(page: Page) {
  await expect(page.locator('app-dashboard .dashboard-grid')).toBeVisible();
  await expect(page.locator('app-dashboard [role="alert"]')).toHaveCount(0);
}

async function productsReady(page: Page) {
  await expect(page.locator('app-product-list table tbody tr').first()).toBeVisible();
  await expect(page.locator('app-product-list [role="alert"]')).toHaveCount(0);
}

async function productQuery(page: Page, action: () => Promise<unknown>, key: string, value: string | null) {
  const response = page.waitForResponse((res) => {
    const url = new URL(res.url());
    return url.pathname === '/api/v1/catalog/products' && url.searchParams.get(key) === value;
  });
  await action();
  expect((await response).status()).toBe(200);
  await productsReady(page);
}

async function navigationCycle(page: Page) {
  const navigationMs: number[] = [];
  let start = performance.now();
  await page.locator('mat-sidenav a[href="/catalog/products"]').click();
  await productsReady(page);
  navigationMs.push(performance.now() - start);
  await page.getByRole('button', { name: 'Activar modo oscuro', exact: true }).click();
  await expect(page.locator('html')).toHaveClass(/theme-dark/);
  const list = page.locator('app-product-list');
  await productQuery(page, () => list.getByPlaceholder('Nombre', { exact: true }).fill('PERF'), 'search', 'PERF');
  await productQuery(page, async () => {
    await list.getByRole('combobox', { name: 'Estado', exact: true }).click();
    await page.getByRole('option', { name: 'Activos', exact: true }).click();
  }, 'status', 'ACTIVE');
  for (const [label, option] of [
    ['Marca', 'Marca sintética de rendimiento'], ['Categoría', 'Categoría sintética de rendimiento'],
  ]) {
    await list.getByRole('combobox', { name: label, exact: true }).click();
    const response = page.waitForResponse((res) => new URL(res.url()).pathname === '/api/v1/catalog/products');
    await page.getByRole('option', { name: option, exact: true }).click();
    expect((await response).status()).toBe(200);
    await productsReady(page);
  }
  const paginator = list.locator('mat-paginator');
  await productQuery(page, async () => {
    await paginator.getByRole('combobox').focus();
    await paginator.getByRole('combobox').press('Enter');
    await page.getByRole('option', { name: '50', exact: true }).click();
  }, 'size', '50');
  await productQuery(page, () => paginator.locator('.mat-mdc-paginator-navigation-next').click(), 'page', '1');
  await productQuery(page, () => paginator.locator('.mat-mdc-paginator-navigation-previous').click(), 'page', '0');
  await page.getByRole('link', { name: /^Ver detalle de Producto .* PERF / }).first().click();
  await expect(page.getByRole('heading', { name: 'Detalle del producto', exact: true })).toBeVisible();
  await expect(page.locator('app-product-detail .summary-card')).toBeVisible();
  await page.getByRole('link', { name: 'Volver al listado de productos', exact: true }).click();
  await productsReady(page);
  await productQuery(page, () => list.getByRole('button', { name: 'Limpiar', exact: true }).click(), 'search', null);
  await page.getByRole('button', { name: 'Activar modo claro', exact: true }).click();
  await expect(page.locator('html')).toHaveClass(/theme-light/);
  start = performance.now();
  await page.locator('mat-sidenav a[href="/dashboard"]').click();
  await dashboardReady(page);
  navigationMs.push(performance.now() - start);
  return navigationMs;
}

function writeEvidence(name: string, value: unknown) {
  const directory = artifactDirectory();
  mkdirSync(directory, { recursive: true });
  writeFileSync(join(directory, name), sanitize(JSON.stringify(value, null, 2)));
}

async function observe(page: Page) {
  const errors: { kind: string; message: string }[] = [];
  const httpErrors: { status: number; path: string; correlationId: string | null }[] = [];
  page.on('response', (response) => {
    const url = new URL(response.url());
    if (url.pathname.startsWith('/api/') && response.status() >= 400) {
      httpErrors.push({ status: response.status(), path: url.pathname,
        correlationId: response.headers()['x-correlation-id'] || null });
    }
  });
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push({ kind: 'console', message: sanitize(message.text()) });
  });
  page.on('pageerror', (error) => errors.push({ kind: 'pageerror', message: sanitize(error.message) }));
  await page.addInitScript(() => {
    const state = { count: 0, duration: 0 };
    (window as any).__perfLongTasks = state;
    new PerformanceObserver((entries) => {
      for (const entry of entries.getEntries()) { state.count++; state.duration += entry.duration; }
    }).observe({ type: 'longtask', buffered: true });
  });
  const cdp = await page.context().newCDPSession(page);
  const browserVersion = await cdp.send('Browser.getVersion');
  await cdp.send('HeapProfiler.enable');
  const sample = async () => {
    await cdp.send('HeapProfiler.collectGarbage');
    const heap = await cdp.send('Runtime.getHeapUsage');
    const dom = await cdp.send('Memory.getDOMCounters');
    const browser = await page.evaluate(() => ({
      longTasks: { ...(window as any).__perfLongTasks },
      horizontalOverflow: document.documentElement.scrollWidth > window.innerWidth,
      viewportWidth: window.innerWidth,
    }));
    return { heapBytes: heap.usedSize, domNodes: dom.nodes,
      errorCount: errors.length, httpErrorCount: httpErrors.length,
      documents: dom.documents, eventListeners: dom.jsEventListeners, ...browser };
  };
  return { errors, httpErrors, sample, cdp, browserVersion };
}

test('50 navigation filter table and theme cycles retain bounded memory', async ({ page }, info) => {
  const observer = await observe(page);
  const samples: any[] = [];
  let baseline: any = null;
  let failure: string | null = null;
  try {
    await login(page);
    for (let warm = 0; warm < 2; warm++) await navigationCycle(page);
    baseline = await observer.sample();
    for (let cycle = 1; cycle <= cycles; cycle++) {
      const navigationMs = await navigationCycle(page);
      samples.push({ cycle, navigationMs, ...(await observer.sample()) });
    }
    const summary = summarizeCycles(baseline.heapBytes, samples, cycles);
    expect.soft(summary.complete).toBe(true);
    expect.soft(summary.navigationP95Ms).toBeLessThanOrEqual(1000);
    expect.soft(summary.heapGrowthPercent).toBeLessThanOrEqual(20);
    expect.soft(observer.errors).toEqual([]);
    expect.soft(observer.httpErrors).toEqual([]);
  } catch (error) { failure = sanitize(String(error)); throw error; }
  finally {
    writeEvidence(`${info.project.name}-navigation.json`, {
      mode: process.env['PERF_WEB_MODE'] || 'standalone', quick: cycles !== 50,
      browserVersion: observer.browserVersion,
      setupContextAtDesktop: (page.viewportSize()?.width ?? 1440) <= 1050,
      baseline, samples, errors: observer.errors, httpErrors: observer.httpErrors, failure,
      summary: baseline ? summarizeCycles(baseline.heapBytes, samples, cycles) : { complete: false },
    });
    await observer.cdp.detach();
  }
});

test('50 authorized dialog open cancel cycles do not mutate catalogue', async ({ page }, info) => {
  const observer = await observe(page);
  const samples: any[] = [];
  let baseline: any = null;
  const writes: string[] = [];
  page.on('request', (request) => {
    if (new URL(request.url()).pathname.startsWith('/api/v1/catalog/')
      && !['GET', 'HEAD', 'OPTIONS'].includes(request.method())) writes.push(request.method());
  });
  try {
    await login(page, true);
    await page.locator('mat-sidenav a[href="/catalog/products"]').click();
    await productsReady(page);
    await page.getByRole('link', { name: /^Ver detalle de Producto .* PERF / }).first().click();
    await expect(page.getByTestId('status-product')).toBeVisible();
    const dialogCycle = async () => {
      await page.getByTestId('status-product').click();
      await expect(page.getByRole('dialog')).toBeVisible();
      await page.getByRole('dialog').getByTestId('cancel').click();
      await expect(page.getByRole('dialog')).toHaveCount(0);
      await page.getByTestId('create-presentation').click();
      await expect(page.getByRole('dialog')).toBeVisible();
      await page.getByRole('dialog').getByRole('button', { name: 'Cancelar', exact: true }).click();
      await expect(page.getByRole('dialog')).toHaveCount(0);
    };
    await dialogCycle();
    baseline = await observer.sample();
    for (let cycle = 1; cycle <= cycles; cycle++) {
      await dialogCycle();
      samples.push({ cycle, navigationMs: [], ...(await observer.sample()) });
    }
    expect.soft(summarizeCycles(baseline.heapBytes, samples, cycles).heapGrowthPercent).toBeLessThanOrEqual(20);
    expect.soft(writes).toEqual([]);
    expect.soft(observer.errors).toEqual([]);
    expect.soft(observer.httpErrors).toEqual([]);
  } finally {
    writeEvidence(`${info.project.name}-dialogs.json`, { baseline, samples, writes, errors: observer.errors,
      browserVersion: observer.browserVersion,
      httpErrors: observer.httpErrors,
      summary: baseline ? summarizeCycles(baseline.heapBytes, samples, cycles) : { complete: false } });
    await observer.cdp.detach();
  }
});

test('controlled API error visibly preserves correlation id', async ({ page }, info) => {
  await login(page);
  let correlationId = '';
  await page.route((url) => url.pathname === '/api/v1/catalog/products', async (route) => {
    correlationId = route.request().headers()['x-correlation-id'];
    await route.fulfill({ status: 503, contentType: 'application/json',
      headers: { 'X-Correlation-ID': correlationId },
      body: JSON.stringify({ timestamp: new Date().toISOString(), status: 503,
        code: 'PERF_CONTROLLED_ERROR', reason: 'Service Unavailable', message: 'Error controlado de prueba',
        data: null, errors: null, path: '/api/v1/catalog/products', correlationId }),
    });
  });
  await page.locator('mat-sidenav a[href="/catalog/products"]').click();
  await expect(page.locator('app-product-list [role="alert"]')).toContainText('Referencia:');
  expect(correlationId).toBeTruthy();
  await expect(page.locator('app-product-list [role="alert"]')).toContainText(correlationId);
  writeEvidence(`${info.project.name}-controlled-error.json`, {
    kind: 'browser-route-fault-injection', backendFailure: false, correlationId, passed: true,
    mode: process.env['PERF_WEB_MODE'] || 'standalone',
  });
});

test('operational context is reachable at the tested viewport', async ({ page }, info) => {
  await authenticate(page);
  await dashboardReady(page);
  const visible = await page.locator('.context-selector').isVisible();
  writeEvidence(`${info.project.name}-context-availability.json`, {
    viewport: page.viewportSize(), visible, setupResized: false,
  });
  expect(visible, 'Branch/warehouse selection must remain reachable without enlarging the viewport').toBe(true);
});
