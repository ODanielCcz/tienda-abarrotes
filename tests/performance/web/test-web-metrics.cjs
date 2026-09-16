const assert = require('node:assert/strict');
const { percentile, summarizeCycles } = require('./web-metrics.cjs');
assert.equal(percentile([4, 1, 3, 2], 0.5), 2);
assert.throws(() => percentile([], 0.95));
assert.throws(() => percentile([NaN], 0.95));
assert.throws(() => percentile([1], NaN));
const cycles = Array.from({ length: 50 }, (_, i) => ({
  cycle: i + 1, heapBytes: 110, navigationMs: [100, 200], domNodes: 30,
}));
const good = summarizeCycles(100, cycles, 50);
assert.equal(good.complete, true);
assert.equal(good.heapGrowthPercent, 10);
assert.equal(good.navigationP95Ms, 200);
assert.equal(summarizeCycles(100, cycles.slice(1), 50).complete, false);
assert.equal(summarizeCycles(100, [{ ...cycles[0], heapBytes: 150 }], 1).heapGrowthPercent, 50);
assert.throws(() => summarizeCycles(0, cycles, 50));
console.log('PASS: browser metrics reject missing evidence and retain real regressions.');
