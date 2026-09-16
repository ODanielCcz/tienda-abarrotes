const assert = require('node:assert/strict');
const { parseEnvironment } = require('./perf-web-environment.cjs');
assert.deepEqual(parseEnvironment('E2E_USERNAME=perf_admin\nPERF_USERNAME=perf_operator\n# ignored\n'), {
  E2E_USERNAME: 'perf_admin', PERF_USERNAME: 'perf_operator',
});
console.log('PASS: isolated fixture keys may contain digits.');
