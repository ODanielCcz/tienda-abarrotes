import { defineConfig } from '@playwright/test';
const { baseURL, artifactDirectory } = require('../../tests/performance/web/perf-web-environment.cjs');

export default defineConfig({
  testDir: '../../tests/performance/web',
  testMatch: 'frontend-performance.spec.ts',
  workers: 1,
  fullyParallel: false,
  retries: 0,
  timeout: 900_000,
  expect: { timeout: 15_000 },
  outputDir: `${artifactDirectory()}/playwright`,
  reporter: [['list'], ['json', { outputFile: `${artifactDirectory()}/playwright.json` }]],
  use: {
    baseURL, browserName: 'chromium', headless: true,
    trace: 'off', screenshot: 'off', video: 'off',
    actionTimeout: 15_000,
  },
  projects: [
    { name: 'desktop', use: { viewport: { width: 1440, height: 900 } } },
    { name: 'tablet', use: { viewport: { width: 820, height: 1180 }, hasTouch: true, isMobile: true } },
    { name: 'mobile', use: { viewport: { width: 390, height: 844 }, hasTouch: true, isMobile: true } },
  ],
});
