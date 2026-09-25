const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const branchId = '90000000-0000-0000-0000-000000000001';
const token = 'synthetic.jwt.token';
const expiresAt = new Date(Date.now() + 3600_000).toISOString();
const source = fs.readFileSync(path.join(__dirname, 'lib/auth.js'), 'utf8')
  .replace(/^import .*;\r?\n/gm, '')
  .replace(/export /g, '');

function authWithBranches(branches) {
  const calls = [];
  const context = vm.createContext({
    config: { username: 'perf_operator', password: 'synthetic-password' },
    contract: { branch: { id: branchId, code: 'PERF' } },
    apiRequest(method, url, bearer, options) {
      calls.push({ method, url, bearer, options });
      if (url === '/api/v1/auth/login') return { data: { accessToken: token, expiresAt } };
      if (url === '/api/v1/organization/branches?status=ACTIVE') return { data: branches };
      throw new Error(`Unexpected request: ${url}`);
    },
    requireApiData: (response) => response.data,
  });
  vm.runInContext(source, context);
  return { context, calls };
}

const valid = authWithBranches([{ branchId, code: 'PERF', status: 'ACTIVE' }]);
const session = valid.context.loginInSetup();
assert.equal(session.accessToken, token);
assert.equal(session.branchId, branchId);
assert.equal(valid.calls.length, 2);
assert.equal(valid.calls[0].method, 'POST');
assert.equal(valid.calls[1].method, 'GET');
assert.equal(valid.calls[1].bearer, token);
assert.equal(valid.calls[1].options.operation, 'read');
assert.deepEqual(Array.from(valid.calls[1].options.statuses), [200]);

for (const branches of [[], [{ branchId: 'other', code: 'OTHER', status: 'ACTIVE' }]]) {
  const invalid = authWithBranches(branches);
  assert.throws(() => invalid.context.loginInSetup(), /PERF branch context/);
}

console.log('PASS: setup reads and validates the restricted authenticated branch context.');
