const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const context = vm.createContext({
  config: { runId: 'test-run' },
  comparableSummary: () => ({ metrics: { http_reqs: { count: 20 } } }),
});
const source = fs.readFileSync(path.join(__dirname, 'lib/profile-runtime.js'), 'utf8')
  .replace(/^import .*;\r?\n/gm, '').replace(/export /g, '');
vm.runInContext(source, context);
const profile = {
  name: 'backend-stress', levels: [
    { target: 50, startSeconds: 0, durationSeconds: 240 },
    { target: 75, startSeconds: 240, durationSeconds: 240 },
  ],
};
function summary(duration, secondStageFailed) {
  const metrics = {};
  for (const target of [50, 75]) {
    metrics[`http_reqs{stress_level:${target}}`] = { values: { count: 10 } };
    metrics[`checks{stress_level:${target}}`] = {
      values: { rate: target === 75 && secondStageFailed ? 0.9 : 1 },
      thresholds: { 'rate>0.97': { ok: target !== 75 || !secondStageFailed } },
    };
  }
  const result = context.summaryFor({
    state: { testRunDurationMs: duration * 1000 }, metrics, root_group: {},
  }, profile);
  return JSON.parse(result['/artifacts/test-run-backend-stress-summary.json']);
}
assert.equal(summary(480, false).lastStableTarget, 75);
assert.equal(summary(300, true).lastStableTarget, 50);
assert.equal(summary(300, false).levels[1].status, 'INCOMPLETE');
assert.equal(summary(300, true).levels[1].status, 'THRESHOLD_BREACHED');
console.log('PASS: only completed, passing stress levels count as stable.');
