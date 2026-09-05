const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const samples = {};
class Metric {
  constructor(name) { this.name = name; samples[name] = []; }
  add(value) { samples[this.name].push(value); }
}
const source = fs.readFileSync(path.join(__dirname, 'lib/checks.js'), 'utf8')
  .replace(/^import .*;\r?\n/gm, '')
  .replace(/export /g, '');
const context = vm.createContext({
  Counter: Metric, Rate: Metric, Trend: Metric,
  config: { maxReadLatencyMs: 1000, maxWriteLatencyMs: 1000 },
  check: () => {}, fail: (message) => { throw new Error(message); },
});
vm.runInContext(source, context);

function classify(status, code, expected) {
  const envelope = {
    status, code, reason: 'test', timestamp: new Date().toISOString(),
    message: 'test', data: null, errors: null, path: '/test', correlationId: 'test-id',
  };
  context.validateApiResponse({
    status, json: () => envelope, timings: { duration: 1 },
    headers: { 'X-Correlation-ID': 'test-id' },
  }, 'test-id', '/test', { name: 'test', statuses: [200], ...expected });
  return samples.unexpected_functional_error_rate.at(-1);
}
assert.equal(classify(200, 'OK'), false);
assert.equal(classify(409, 'STOCK_INSUFFICIENT'), true);
assert.equal(classify(429, 'SYNC_RATE_LIMITED'), true);
assert.equal(classify(409, 'WRONG_CODE', { statuses: [409] }), true);
assert.equal(classify(409, 'STOCK_INSUFFICIENT', {
  statuses: [409], allowedErrorCodes: { 409: ['STOCK_INSUFFICIENT'] },
}), false);
assert.equal(classify(409, 'OTHER_CONFLICT', {
  statuses: [409], allowedErrorCodes: { 409: ['STOCK_INSUFFICIENT'] },
}), true);
assert.equal(classify(500, 'INTERNAL_ERROR'), true);
console.log('PASS: expected statuses and controlled error codes are classified explicitly.');
