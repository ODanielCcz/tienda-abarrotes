import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';
import { readFileSync, mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
const requireWeb = createRequire(new URL('../../../apps/web/package.json', import.meta.url));
const { default: lighthouse } = await import(pathToFileURL(requireWeb.resolve('lighthouse')).href);
const { default: puppeteer } = await import(pathToFileURL(requireWeb.resolve('puppeteer-core')).href);
const { chromium } = requireWeb('playwright');
const require = createRequire(import.meta.url);
const { baseURL, credentials, artifactDirectory, sanitize } = require('./perf-web-environment.cjs');
const { percentile } = require('./web-metrics.cjs');
const { ci } = JSON.parse(readFileSync(new URL('./lighthouserc.json', import.meta.url), 'utf8'));
const contract = require('../fixtures/fixture-contract.json');
const output = artifactDirectory();
mkdirSync(output, { recursive: true });
const routes = [];
const executablePath = process.env.PERF_LIGHTHOUSE_CHROME_PATH || chromium.executablePath();
let failed = false;
for (const url of ci.collect.url) {
  if (new URL(url).origin !== baseURL) throw new Error('Only isolated localhost audits are allowed');
  const runs = [];
  for (let run = 1; run <= ci.collect.numberOfRuns; run++) {
    const browser = await puppeteer.launch({ executablePath, headless: true });
    try {
      const page = await browser.newPage();
      await page.goto(`${baseURL}/login`);
      if (new URL(url).pathname !== '/login') {
        const account = credentials();
        await page.waitForSelector('input[formcontrolname="username"]', { visible: true });
        await page.type('input[formcontrolname="username"]', account.username);
        await page.type('input[formcontrolname="password"]', account.password);
        await page.click('button[type="submit"]');
        await page.waitForFunction(() => location.pathname === '/dashboard');
        await page.evaluate((context) => sessionStorage.setItem('tienda.operational.context', JSON.stringify(context)), {
          branchId: contract.branch.id, warehouseId: contract.warehouses[0].id,
        });
      }
      const cdp = await page.createCDPSession();
      await cdp.send('Network.clearBrowserCache');
      // Same page is essential: this app stores authentication in sessionStorage.
      const result = await lighthouse(url, { ...ci.collect.settings, logLevel: 'error' }, undefined, page);
      if (!result || result.lhr.runtimeError) throw new Error('Lighthouse runtime failed');
      const lhr = result.lhr;
      if (new URL(lhr.finalDisplayedUrl || lhr.finalUrl).pathname !== new URL(url).pathname)
        throw new Error('Audit redirected away from its intended route');
      const metrics = {
        performance: lhr.categories.performance.score,
        accessibility: lhr.categories.accessibility.score,
        bestPractices: lhr.categories['best-practices'].score,
        lcpMs: lhr.audits['largest-contentful-paint'].numericValue,
        cls: lhr.audits['cumulative-layout-shift'].numericValue,
      };
      if (!Object.values(metrics).every(Number.isFinite)) throw new Error('Incomplete Lighthouse metrics');
      const evidence = { run, url, lighthouseVersion: lhr.lighthouseVersion,
        fetchTime: lhr.fetchTime, userAgent: lhr.userAgent, environment: lhr.environment,
        configSettings: lhr.configSettings, metrics, warnings: lhr.runWarnings,
        audits: Object.fromEntries(Object.entries(lhr.audits).map(([key, audit]) => [key, {
          score: audit.score, title: audit.title, numericValue: audit.numericValue,
          displayValue: audit.displayValue, explanation: audit.explanation,
        }])),
      };
      runs.push(evidence);
      writeFileSync(join(output, `lighthouse-${new URL(url).pathname.replaceAll('/', '-')}-${run}.json`),
        sanitize(JSON.stringify(evidence, null, 2)));
    } catch (error) { failed = true; runs.push({ run, error: sanitize(String(error)) }); }
    finally { await browser.close(); }
  }
  const complete = runs.length === 3 && runs.every((run) => run.metrics);
  const median = complete ? Object.fromEntries(Object.keys(runs[0].metrics).map((key) => [
    key, percentile(runs.map((run) => run.metrics[key]), 0.5),
  ])) : null;
  const passed = complete && median.performance >= 0.85 && median.accessibility >= 0.95
    && median.bestPractices >= 0.9 && median.lcpMs <= 2500 && median.cls <= 0.1;
  failed ||= !passed;
  routes.push({ url, complete, median, passed, runs });
}
writeFileSync(join(output, 'lighthouse-summary.json'), sanitize(JSON.stringify({
  methodology: 'Lighthouse same-page Node API using lighthouserc collection and median thresholds',
  executablePath,
  authStorage: 'sessionStorage preserved; browser cache cleared before each audit', routes,
}, null, 2)));
console.log(`Lighthouse: ${failed ? 'findings or incomplete evidence' : 'PASS'}. Evidence: ${output}`);
process.exitCode = failed ? 1 : 0;
