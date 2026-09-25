const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

// Removing or misclassifying a custom metric tag must fail this test.
const samples = {};
class Metric {
  constructor(name) {
    this.name = name;
    samples[name] = [];
  }
  add(value, tags) { samples[this.name].push({ value, tags }); }
}

const source = fs.readFileSync(path.join(__dirname, 'lib/workload-metrics.js'), 'utf8')
  .replace(/^import .*;\r?\n/gm, '')
  .replace(/export /g, '');
const context = vm.createContext({ Counter: Metric, Trend: Metric });
vm.runInContext(source, context);

const descriptor = { module: 'sales', name: 'sales-order', operation: 'write', statuses: [201] };
assert.equal(context.recordWorkloadResponse({ status: 201, timings: { duration: 12 } }, descriptor), true);
assert.equal(context.recordWorkloadResponse({ status: 500, timings: { duration: 34 } }, descriptor), false);

for (const metricName of ['workload_requests', 'business_p99', 'write_p95']) {
  assert.equal(samples[metricName][0].tags.functional_result, 'success');
  assert.equal(samples[metricName][1].tags.functional_result, 'unexpected');
  for (const sample of samples[metricName]) {
    assert.equal(sample.tags.module, 'sales');
    assert.equal(sample.tags.endpoint, 'sales-order');
    assert.equal(sample.tags.operation, 'write');
  }
}

const read = { module: 'inventory', name: 'stock', operation: 'read', statuses: [200] };
assert.equal(context.recordWorkloadResponse({ status: 200, timings: { duration: 8 } }, read), true);
assert.equal(context.recordWorkloadResponse({ status: 404, timings: { duration: 9 } }, read), false);
assert.equal(samples.read_p95[0].tags.functional_result, 'success');
assert.equal(samples.read_p95[1].tags.functional_result, 'unexpected');
assert.equal(samples.read_p95[0].tags.module, 'inventory');
assert.equal(samples.read_p95[0].tags.endpoint, 'stock');
assert.equal(samples.read_p95[0].tags.operation, 'read');

console.log('PASS: workload metrics emit stable operation and functional-result tags.');
