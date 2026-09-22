/**
 * Laboratorio visual — UX-R2.1.
 *
 *   # sin backend: cada vista en su estado degradado, rápido y hermético
 *   npx playwright test --config=playwright.visual.config.js
 *
 *   # con backend: contra el banco de tests/e2e o contra dev
 *   VISUAL_BASE=http://localhost:8099 npx playwright test --config=playwright.visual.config.js
 *
 * Separado de `playwright.config.js` (aceptación de HU-INV-001) y de
 * `playwright.e2e.config.js` (corte vertical) porque tiene otro propósito:
 * aquí no se comprueba que el producto funcione, sino que **se vea entero y
 * sin romperse** en cinco anchuras.
 */
import { defineConfig, devices } from '@playwright/test';

const PUERTO = 4330;

export default defineConfig({
  testDir: '../../tests/visual',
  testMatch: '**/*.spec.js',

  // En serie: las vistas comparten la identidad efímera que crea la primera,
  // y el informe se acumula en memoria.
  workers: 1,
  fullyParallel: false,
  // Sin reintentos: un hallazgo estructural no se arregla repitiendo.
  retries: 0,
  timeout: 45_000,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : [['list']],

  use: {
    baseURL: process.env.VISUAL_BASE ?? `http://127.0.0.1:${PUERTO}`,
    // La captura ya se hace a mano en cada prueba; esta es para los fallos.
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },

  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],

  // Solo se levanta el servidor estático cuando NO se apunta a un backend.
  //
  // Se sirve **la raíz del monorepo**, no `src/`. Las vistas enlazan el kit
  // con `../../../../shared/ui-kit/...`, que sale de `src/`: sirviendo `src/`
  // los tres CSS dan 404 y las capturas salen sin estilos. El borde real hace
  // exactamente esto (`root /srv/nexus` + `location /shared/`), así que además
  // las rutas coinciden con las de dev.
  ...(process.env.VISUAL_BASE
    ? {}
    : {
        webServer: {
          command: `npx http-server ../.. -p ${PUERTO} -c-1 --silent`,
          url: `http://127.0.0.1:${PUERTO}/frontend/app-web/src/cuentas/login.html`,
          reuseExistingServer: true,
          timeout: 60_000,
        },
      }),
});
