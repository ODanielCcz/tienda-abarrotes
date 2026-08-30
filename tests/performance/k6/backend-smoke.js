import { check, fail } from 'k6';
import { Counter } from 'k6/metrics';
import { config } from './lib/config.js';
import { deterministicUuid, smokeFixtureSelection } from './lib/fixtures.js';
import { loginInSetup, renewTokenInSetup, requireUsableSetupToken } from './lib/auth.js';
import { apiRequest, readinessRequest, requireApiData, requireApiEnvelope, requireApiField } from './lib/checks.js';

const smokeCompletion = new Counter('smoke_completion');

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    checks: ['rate==1'],
    unexpected_5xx: ['count==0'],
    unexpected_functional_error: ['count==0'],
    schema_error: ['count==0'],
    smoke_completion: ['count==1'],
  },
};

// EXPECT_INVALID_URL=true is resolved in config.js and intentionally drives every URL invalid.
// SMOKE_RUN_ID is likewise resolved in config.js and is the sole seed for write identifiers.
// typeof __ITER is guarded in checks.js because k6 does not define it during setup().

export function setup() {
  const identifiers = ['sales-order', 'card-payment', 'sync-operation', 'sync-idempotency', 'sync-cart']
    .map((label) => deterministicUuid(label));
  const identifiersValid = check(identifiers, {
    'deterministic write IDs are valid and distinct': (ids) => new Set(ids).size === ids.length
      && ids.every((id) => /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(id)),
  });
  if (!identifiersValid) fail('Deterministic write IDs must be valid and distinct.');
  readinessRequest();
  // Authentication is deliberately confined to setup; no iteration performs a login.
  return renewTokenInSetup(loginInSetup());
}

export default function smoke(session) {
  smokeCompletion.add(0);
  const token = requireUsableSetupToken(session);
  const fixture = smokeFixtureSelection();
  const presentation = apiRequest('GET', `/api/v1/catalog/product-presentations/${fixture.productPresentationId}`, token, {
    name: 'catalog-presentation', operation: 'read', statuses: [200],
  });
  const stock = apiRequest('GET', `/api/v1/inventory/stock/${fixture.productPresentationId}?warehouseId=${fixture.warehouseId}`, token, {
    name: 'known-stock', operation: 'read', statuses: [200],
  });
  const stockData = requireApiData(stock, 'known stock');
  const presentationData = requireApiData(presentation, 'catalogue presentation');
  const stockBefore = requireAvailableStock(stockData, fixture.productPresentationId, 'known stock');
  check(stockData, { 'known stock contains the fixture presentation': (data) => data.length > 0 });
  check(presentationData, { 'catalogue returns the selected non-lot presentation': (data) => data.id === fixture.productPresentationId });

  if (config.expectAbortMidway) {
    fail('Controlled smoke abort requested after read checks.');
  }

  const salesPayload = createSalesOrder(fixture);
  const orderResponse = apiRequest('POST', '/api/v1/sales/orders', token, {
    name: 'sales-order', operation: 'write', statuses: [201], body: salesPayload,
  });
  const order = requireApiData(orderResponse, 'sales order');
  const orderId = requireApiField(order, 'salesOrderId', 'sales order');
  const orderTotal = requireApiField(order, 'total', 'sales order');
  const replayResponse = apiRequest('POST', '/api/v1/sales/orders', token, {
    name: 'sales-order-replay', operation: 'write', statuses: [201], body: salesPayload,
  });
  const replay = requireApiData(replayResponse, 'sales order replay');
  const replayOrderId = requireApiField(replay, 'salesOrderId', 'sales order replay');
  check(replay, { 'sales replay resolves to the original sale': () => replayOrderId === orderId });

  const paymentPayload = createCardPayment(orderTotal);
  const payment = requireApiData(apiRequest('POST', `/api/v1/sales/orders/${orderId}/payments`, token, {
    name: 'card-payment', operation: 'write', statuses: [201], body: paymentPayload,
  }), 'card payment');
  const paymentId = requireApiField(payment, 'paymentId', 'card payment');
  const paymentReplay = requireApiData(apiRequest('POST', `/api/v1/sales/orders/${orderId}/payments`, token, {
    name: 'card-payment-replay', operation: 'write', statuses: [201], body: paymentPayload,
  }), 'card payment replay');
  const paymentReplayId = requireApiField(paymentReplay, 'paymentId', 'card payment replay');
  check(paymentReplay, { 'payment replay resolves to the original payment': () => paymentReplayId === paymentId });

  const stockAfter = requireApiData(apiRequest('GET', `/api/v1/inventory/stock/${fixture.productPresentationId}?warehouseId=${fixture.warehouseId}`, token, {
    name: 'stock-after-sale', operation: 'read', statuses: [200],
  }), 'stock after sale');
  const stockAfterAvailable = requireAvailableStock(stockAfter, fixture.productPresentationId, 'stock after sale');
  check({ stockBefore, stockAfterAvailable }, {
    'stock decrement is bounded by one sale': (stock) => {
      return (stock.stockBefore === fixture.initialAvailableQuantity
        || stock.stockBefore === fixture.initialAvailableQuantity - 1)
        && stock.stockAfterAvailable === fixture.initialAvailableQuantity - 1;
    },
  });

  const checkpointResponse = apiRequest('GET', `/api/v1/sync/devices/${fixture.deviceId}/checkpoint`, token, {
    name: 'sync-checkpoint', operation: 'read', statuses: [200, 404],
  });
  const checkpointEnvelope = requireApiEnvelope(checkpointResponse, 'sync checkpoint');
  const checkpoint = checkpointEnvelope.data;
  if (checkpointResponse.status === 200 && (!checkpoint || typeof checkpoint.lastAcknowledgedOutboxSequence !== 'number')) {
    fail('Sync checkpoint succeeded without the required cursor data.');
  }
  if (checkpointResponse.status !== 200 && checkpointResponse.status !== 404) {
    fail(`Sync checkpoint returned unexpected HTTP status ${checkpointResponse.status}.`);
  }
  // A 404 is the documented initial state for a device without a checkpoint; sequence 1 is then valid.
  acknowledgeCheckpoint(token, fixture.deviceId, checkpointResponse.status === 404 ? 0 : checkpoint.lastAcknowledgedOutboxSequence);

  const syncPayload = createCartUpsert(fixture);
  const syncResponse = apiRequest('POST', '/api/v1/sync/inbox', token, {
    name: 'sync-cart-upsert', operation: 'write', statuses: [201], body: syncPayload,
  });
  const sync = requireApiData(syncResponse, 'sync CART_UPSERT');
  const syncOperationId = requireApiField(sync, 'operationId', 'sync CART_UPSERT');
  check(sync, { 'sync CART_UPSERT is ACCEPTED': (data) => data.status === 'ACCEPTED' });
  const replaySyncResponse = apiRequest('POST', '/api/v1/sync/inbox', token, {
    name: 'sync-cart-upsert-replay', operation: 'write', statuses: [201], body: syncPayload,
  });
  const replaySync = requireApiData(replaySyncResponse, 'sync CART_UPSERT replay');
  const replaySyncOperationId = requireApiField(replaySync, 'operationId', 'sync CART_UPSERT replay');
  check(replaySync, {
    'sync replay is ACCEPTED': (data) => data.status === 'ACCEPTED',
    'sync replay resolves to the original operation': () => replaySyncOperationId === syncOperationId,
  });

  apiRequest('GET', `/api/v1/reports/sales-by-period?branchId=${fixture.branchId}`, token, {
    name: 'allowed-sales-report', operation: 'read', statuses: [200],
  });
  smokeCompletion.add(1);
}

export function createSalesOrder(fixture) {
  return {
    warehouseId: fixture.warehouseId,
    deviceId: fixture.deviceId,
    channel: 'POS',
    currencyCode: 'MXN',
    idempotencyKey: deterministicUuid('sales-order'),
    items: [{
      productPresentationId: fixture.productPresentationId,
      quantity: 1,
      unitPrice: fixture.unitPrice,
      discountAmount: 0,
    }],
  };
}

function requireAvailableStock(entries, productPresentationId, name) {
  if (!Array.isArray(entries)) fail(`${name} did not return a stock array.`);
  const entry = entries.find((candidate) => candidate.productPresentationId === productPresentationId);
  const available = entry && Number(entry.availableQuantity);
  if (!Number.isFinite(available)) fail(`${name} did not return availableQuantity for the fixture presentation.`);
  return available;
}

export function createCardPayment(total) {
  return {
    paymentMethod: 'CARD',
    amount: total,
    currencyCode: 'MXN',
    reference: `k6-${config.smokeRunId.slice(0, 8)}`,
    idempotencyKey: deterministicUuid('card-payment'),
  };
}

export function acknowledgeCheckpoint(token, deviceId, outboxSequence) {
  return apiRequest('POST', `/api/v1/sync/devices/${deviceId}/checkpoint`, token, {
    name: 'sync-checkpoint-ack', operation: 'write', statuses: [200], body: { outboxSequence },
  });
}

export function createCartUpsert(fixture) {
  return {
    operationId: deterministicUuid('sync-operation'),
    deviceId: fixture.deviceId,
    deviceSequence: 1,
    idempotencyKey: deterministicUuid('sync-idempotency'),
    operationType: 'CART_UPSERT',
    aggregateType: 'CART',
    aggregateId: deterministicUuid('sync-cart'),
    clientCreatedAt: '2030-01-01T00:00:00.000Z',
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
  };
}

export function handleSummary(data) {
  const failures = failedCheckNames(data.root_group);
  const completed = data.metrics.smoke_completion
    && data.metrics.smoke_completion.values.count === 1;
  if (!completed) {
    failures.push('smoke completion was not emitted exactly once');
  }
  if (failures.length > 0) {
    return { stdout: `Smoke failed checks: ${failures.join(', ')}\n` };
  }
  return { stdout: 'Smoke completed with all checks passing.\n' };
}

function failedCheckNames(group) {
  const direct = (group.checks || [])
    .filter((result) => result.fails > 0)
    .map((result) => result.name);
  return Object.keys(group.groups || {}).reduce(
    (all, name) => all.concat(failedCheckNames(group.groups[name])),
    direct,
  );
}
