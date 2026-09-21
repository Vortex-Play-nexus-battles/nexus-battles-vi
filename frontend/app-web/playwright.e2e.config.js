/**
 * Pruebas de aceptacion contra servicios REALES.
 *
 * Distinta de `playwright.config.js`, que sirve `src` estatico y prueba
 * comportamiento de interfaz sin backend. Esta no levanta ningun servidor:
 * espera que `tests/e2e/compose.yml` ya este en pie, o que `E2E_BORDE` apunte
 * a un entorno desplegado.
 *
 *   docker compose -f tests/e2e/compose.yml up -d --build --wait
 *   ./tests/e2e/sembrar.sh
 *   npx playwright test --config=playwright.e2e.config.js
 */
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: '../../tests/e2e',
  testMatch: '**/*.e2e.spec.js',
  // En serie a proposito: el flujo es una historia, y cada prueba se apoya en
  // el estado que dejo la anterior. Paralelizarlo no lo haria mas rapido
  // -lo que tarda es el combate- y lo haria mentir.
  workers: 1,
  fullyParallel: false,
  // Un reintento en CI: el unico no-determinismo real es el dado del motor de
  // combate. Ninguno en local, para que un fallo se vea.
  retries: process.env.CI ? 1 : 0,
  timeout: 90_000,
  expect: { timeout: 20_000 },
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : [['list']],
  use: {
    baseURL: process.env.E2E_BORDE ?? 'http://localhost:8099',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
