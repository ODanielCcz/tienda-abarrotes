import { check, fail, sleep } from 'k6';
import { config } from './config.js';
import { apiRequest, requireApiData, requireApiField } from './checks.js';
import { fixtureForVirtualUser, workloadUuid } from './performance-ids.js';
import { loadCompletion, recordWorkloadResponse } from './workload-metrics.js';

let lastSale = null;
let lastPayableSale = null;

export function executeWorkloadIteration(token) {
  const fixture = fixtureForVirtualUser(__VU);
  const operationIndex = __ITER % 20;

  switch (operationIndex) {
    case 0: createSale(token, fixture); break;
    case 1: readStock(token, fixture); break;
    case 2: readPresentation(token, fixture); break;
    case 3: readDevice(token, fixture); break;
    case 4: payLatestSale(token, fixture); break;
    case 5: readLatestSale(token, fixture); break;
    case 6: readUnits(token); break;
    case 7: createSale(token, fixture); break;
    case 8: readStock(token, fixture); break;
    case 9: readSalesSummary(token, fixture); break;
    case 10: upsertCart(token, fixture); break;
    case 11: readPrices(token, fixture); break;
    case 12: readDevice(token, fixture); break;
    case 13: createSale(token, fixture); break;
    case 14: readLatestSale(token, fixture); break;
    case 15: readTopProducts(token, fixture); break;
    case 16: readWarehouses(token, fixture); break;
    case 17: readInventoryMovements(token, fixture); break;
    case 18: readStock(token, fixture); break;
    default: readAlternateFinancialReport(token, fixture); break;
  }

  loadCompletion.add(1);
  sleep(config.iterationSleepSeconds);
}

function request(token, method, path, descriptor) {
  const response = apiRequest(method, path, token, descriptor);
  recordWorkloadResponse(response, descriptor);
  return response;
}

function createSale(token, fixture) {
  const descriptor = {
    module: 'sales', name: 'sales-order', operation: 'write', statuses: [201],
    body: {
      warehouseId: fixture.warehouseId,
      customerId: fixture.customerId,
      deviceId: fixture.deviceId,
      channel: 'POS',
      currencyCode: 'MXN',
      idempotencyKey: workloadUuid('sales-order'),
      items: [{
        productPresentationId: fixture.productPresentationId,
        quantity: 1,
        unitPrice: fixture.unitPrice,
        discountAmount: 0,
      }],
    },
  };
  const sale = requireApiData(request(token, 'POST', '/api/v1/sales/orders', descriptor), 'load sales order');
  lastSale = {
    id: requireApiField(sale, 'salesOrderId', 'load sales order'),
    total: Number(requireApiField(sale, 'total', 'load sales order')),
  };
  if (!Number.isFinite(lastSale.total) || lastSale.total <= 0) fail('Load sale returned an invalid total.');
  lastPayableSale = lastSale;
}

function payLatestSale(token, fixture) {
  if (!lastPayableSale) {
    readDevice(token, fixture);
    return;
  }
  const descriptor = {
    module: 'payments', name: 'card-payment', operation: 'write', statuses: [201],
    body: {
      paymentMethod: 'CARD',
      amount: lastPayableSale.total,
      currencyCode: 'MXN',
      reference: `load-${config.runId.slice(0, 8)}-${__VU}-${__ITER}`,
      idempotencyKey: workloadUuid(`card-payment-${lastPayableSale.id}`),
    },
  };
  const payment = requireApiData(request(
    token,
    'POST',
    `/api/v1/sales/orders/${lastPayableSale.id}/payments`,
    descriptor,
  ), 'load card payment');
  check(payment, { 'load card payment is CAPTURED': (data) => data.status === 'CAPTURED' });
  lastPayableSale = null;
}

function upsertCart(token, fixture) {
  const sequence = Math.floor(__ITER / 20) + 1;
  const descriptor = {
    module: 'sync', name: 'sync-cart-upsert', operation: 'write', statuses: [201],
    body: {
      operationId: workloadUuid('sync-operation'),
      deviceId: fixture.deviceId,
      deviceSequence: sequence,
      idempotencyKey: workloadUuid('sync-idempotency'),
      operationType: 'CART_UPSERT',
      aggregateType: 'CART',
      aggregateId: workloadUuid('sync-cart'),
      clientCreatedAt: new Date().toISOString(),
      payload: {
        branchId: fixture.branchId,
        currencyCode: 'MXN',
        expiresAt: '2030-12-31T00:00:00.000Z',
        items: [{
          productPresentationId: fixture.productPresentationId,
          quantity: 1,
          unitPriceSnapshot: fixture.unitPrice,
        }],
      },
    },
  };
  const operation = requireApiData(request(token, 'POST', '/api/v1/sync/inbox', descriptor), 'load CART_UPSERT');
  check(operation, { 'load CART_UPSERT is ACCEPTED': (data) => data.status === 'ACCEPTED' });
}

function readStock(token, fixture) {
  request(token, 'GET', `/api/v1/inventory/stock/${fixture.productPresentationId}?warehouseId=${fixture.warehouseId}`, {
    module: 'inventory', name: 'stock', operation: 'read', statuses: [200],
  });
}

function readPresentation(token, fixture) {
  request(token, 'GET', `/api/v1/catalog/product-presentations/${fixture.productPresentationId}`, {
    module: 'catalog', name: 'presentation', operation: 'read', statuses: [200],
  });
}

function readDevice(token, fixture) {
  request(token, 'GET', `/api/v1/organization/devices/${fixture.deviceId}`, {
    module: 'organization', name: 'device', operation: 'read', statuses: [200],
  });
}

function readLatestSale(token, fixture) {
  const path = lastSale
    ? `/api/v1/sales/orders/${lastSale.id}`
    : `/api/v1/sales/orders?warehouseId=${fixture.warehouseId}&customerId=${fixture.customerId}`;
  request(token, 'GET', path, {
    module: 'sales', name: lastSale ? 'sales-order-detail' : 'sales-orders', operation: 'read', statuses: [200],
  });
}

function readUnits(token) {
  request(token, 'GET', '/api/v1/catalog/units', {
    module: 'catalog', name: 'units', operation: 'read', statuses: [200],
  });
}

function readPrices(token, fixture) {
  request(token, 'GET', `/api/v1/catalog/prices?branchId=${fixture.branchId}`, {
    module: 'catalog', name: 'prices', operation: 'read', statuses: [200],
  });
}

function readSalesSummary(token, fixture) {
  request(token, 'GET', `/api/v1/reports/sales-summary?branchId=${fixture.branchId}&warehouseId=${fixture.warehouseId}`, {
    module: 'reports', name: 'sales-summary', operation: 'read', statuses: [200],
  });
}

function readTopProducts(token, fixture) {
  request(token, 'GET', `/api/v1/reports/top-products?branchId=${fixture.branchId}&warehouseId=${fixture.warehouseId}&limit=10`, {
    module: 'reports', name: 'top-products', operation: 'read', statuses: [200],
  });
}

function readWarehouses(token, fixture) {
  request(token, 'GET', `/api/v1/organization/warehouses?branchId=${fixture.branchId}&status=ACTIVE`, {
    module: 'organization', name: 'warehouses', operation: 'read', statuses: [200],
  });
}

function readInventoryMovements(token, fixture) {
  request(token, 'GET', `/api/v1/reports/inventory-movements?branchId=${fixture.branchId}&warehouseId=${fixture.warehouseId}&limit=10`, {
    module: 'reports', name: 'inventory-movements', operation: 'read', statuses: [200],
  });
}

function readAlternateFinancialReport(token, fixture) {
  const grossMargin = Math.floor(__ITER / 20) % 2 === 0;
  const path = grossMargin
    ? `/api/v1/reports/gross-margin?branchId=${fixture.branchId}&warehouseId=${fixture.warehouseId}`
    : `/api/v1/reports/stock-valuation?branchId=${fixture.branchId}&warehouseId=${fixture.warehouseId}&limit=10`;
  request(token, 'GET', path, {
    module: 'reports', name: grossMargin ? 'gross-margin' : 'stock-valuation', operation: 'read', statuses: [200],
  });
}
