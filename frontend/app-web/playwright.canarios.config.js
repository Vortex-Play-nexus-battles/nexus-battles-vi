/**
 * Canarios del jugador (R16): las pantallas que el jugador ve, contra AWS DEV.
 *
 * Separado de `playwright.smoke.config.js` a propósito mientras dura la
 * recuperación de R16: estos canarios nacen en rojo —documentan los fallos
 * que se están corrigiendo— y meterlos en el smoke pondría en rojo la señal
 * que usan los despliegues para algo que ya se sabe. Cuando pasen, se unen al
 * smoke y pasan a bloquear.
 *
 *   E2E_AWS=http://35.168.124.119 npx playwright test --config=playwright.canarios.config.js
 */
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: '../../tests/e2e',
  testMatch: '**/*.canario.spec.js',
  workers: 1,
  fullyParallel: false,
  retries: process.env.CI ? 1 : 0,
  timeout: 90_000,
  expect: { timeout: 15_000 },
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : [['list']],
  use: {
    baseURL: process.env.E2E_AWS ?? 'http://35.168.124.119',
    trace: 'retain-on-failure',
    screenshot: 'on',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
