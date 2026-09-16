function percentile(values, quantile) {
  if (!values.length || values.some((v) => !Number.isFinite(v) || v < 0)
      || !Number.isFinite(quantile) || quantile <= 0 || quantile > 1) throw new Error('Invalid metric samples');
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.ceil(sorted.length * quantile) - 1];
}

function summarizeCycles(baselineHeap, cycles, expectedCycles) {
  if (!Number.isFinite(baselineHeap) || baselineHeap <= 0) throw new Error('Missing GC baseline');
  const complete = cycles.length === expectedCycles
    && cycles.every((sample, index) => sample.cycle === index + 1
      && Number.isFinite(sample.heapBytes) && sample.heapBytes > 0);
  const navigation = cycles.flatMap((sample) => sample.navigationMs);
  return {
    complete, completedCycles: cycles.length, expectedCycles,
    navigationP95Ms: navigation.length ? percentile(navigation, 0.95) : null,
    baselineHeapBytes: baselineHeap,
    finalHeapBytes: cycles.at(-1)?.heapBytes ?? null,
    heapGrowthPercent: cycles.length ? (cycles.at(-1).heapBytes - baselineHeap) / baselineHeap * 100 : null,
  };
}
module.exports = { percentile, summarizeCycles };
