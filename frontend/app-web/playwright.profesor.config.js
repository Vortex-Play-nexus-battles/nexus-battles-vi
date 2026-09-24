/**
 * R17 · La prueba del profesor (`tests/e2e/profesor.profesor.spec.js`).
 *
 *   # contra AWS DEV
 *   PROFESOR_URL=http://<host> npx playwright test --config=playwright.profesor.config.js
 *
 *   # contra el banco de tests/e2e/compose.yml
 *   PROFESOR_URL=http://localhost:8099 npx playwright test --config=playwright.profesor.config.js
 *
 * Separada del smoke y de los canarios porque mide otra cosa: no si cada
 * servicio responde, sino si una persona sin conocimiento previo puede entrar,
 * crear su cuenta y jugar. Tres anchuras: 1440 recorre los veinte pasos; 375 y
 * 768, la vertical de entrada (registro, alta, sesión, vuelta).
 */
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: '../../tests/e2e',
  testMatch: '**/*.profesor.spec.js',
  // En serie: el host de DEV es uno y pequeño, y cada anchura crea su cuenta.
  workers: 1,
  fullyParallel: false,
  // Sin reintentos: si el profesor tropieza a la primera, tropezó.
  retries: 0,
  timeout: 12 * 60_000,
  expect: { timeout: 20_000 },
  // Carpetas propias: en el banco E2E corre detrás de la suite de siempre y
  // no debe pisar su informe.
  outputDir: 'test-results-profesor',
  reporter: process.env.CI
    ? [['list'], ['html', { open: 'never', outputFolder: 'playwright-report-profesor' }]]
    : [['list']],
  use: {
    baseURL: process.env.PROFESOR_URL ?? process.env.E2E_AWS ?? 'http://localhost:8099',
    locale: 'es-CO',
    timezoneId: 'America/Bogota',
    // Sin traza ni vídeo: guardarían la contraseña que se escribe en el
    // registro y en el login. Las capturas no la enseñan (el campo la tapa).
    trace: 'off',
    video: 'off',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'movil-375',
      use: { ...devices['Pixel 5'], viewport: { width: 375, height: 812 } },
    },
    {
      name: 'tableta-768',
      use: { ...devices['Desktop Chrome'], viewport: { width: 768, height: 1024 } },
    },
    {
      name: 'escritorio-1440',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } },
    },
  ],
});
