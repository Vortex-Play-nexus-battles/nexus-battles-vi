/**
 * Smoke del entorno desplegado.
 *
 * Separado de `playwright.e2e.config.js` porque apunta a otro sitio y tiene
 * otras reglas: contra AWS hay latencia de red y el host puede estar
 * arrancando, asi que los tiempos son mas largos y se reintenta.
 *
 *   E2E_AWS=http://35.168.124.119 npx playwright test --config=playwright.smoke.config.js
 */
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: '../../tests/e2e',
  testMatch: '**/*.smoke.spec.js',
  // En serie: las pruebas comparten la cuenta que crea la primera.
  workers: 1,
  fullyParallel: false,
  // Dos reintentos contra un entorno remoto: la latencia y un servicio
  // recien arrancado no son defectos del codigo.
  retries: process.env.CI ? 2 : 0,
  timeout: 60_000,
  expect: { timeout: 25_000 },
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : [['list']],
  use: {
    baseURL: process.env.E2E_AWS ?? 'http://35.168.124.119',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
