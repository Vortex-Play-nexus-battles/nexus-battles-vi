/**
 * Campana QA · 31 HU del Grupo de Santiago (Sprints 1-3).
 * Corre contra el banco E2E (tests/e2e/compose.yml + compose.qa.yml) por el borde.
 *
 *   cd frontend/app-web
 *   npx playwright test --config=../../tests/qa-santiago/playwright.qa.config.js
 */
import path from 'node:path';
import { defineConfig, devices } from '@playwright/test';

const SALIDA = process.env.QA_SALIDA ?? path.resolve(process.cwd(), '../../tests/qa-santiago/salida');

export default defineConfig({
  testDir: '.',
  testMatch: '**/*.qa.spec.js',
  workers: 1,
  fullyParallel: false,
  retries: 0,
  timeout: 180_000,
  expect: { timeout: 20_000 },
  reporter: [
    ['list'],
    ['json', { outputFile: process.env.QA_JSON ?? path.join(SALIDA, 'resultados.json') }],
    ['html', { open: 'never', outputFolder: path.join(SALIDA, 'html') }],
  ],
  use: {
    baseURL: process.env.E2E_BORDE ?? 'http://localhost:8099',
    viewport: { width: 1366, height: 860 },
    locale: 'es-CO',
    timezoneId: 'America/Bogota',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'], viewport: { width: 1366, height: 860 } } }],
});
