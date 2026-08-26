/**
 * SCRUM-321 - Pruebas de aceptacion de la vitrina en navegador real.
 *
 * jsdom no calcula diseno, asi que "sin desplazamiento horizontal" y "todo
 * texto permanece legible" no se pueden verificar con Jest: hacen falta un
 * motor de maquetacion y un viewport de verdad. La pila aprobada fija
 * Playwright para las pruebas de comportamiento de la interfaz.
 */
import { defineConfig, devices } from '@playwright/test';

const PUERTO = 4321;

export default defineConfig({
  testDir: './src',
  testMatch: '**/*.aceptacion.spec.js',
  reporter: [['list']],
  use: {
    baseURL: `http://127.0.0.1:${PUERTO}`,
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
  webServer: {
    command: `npx http-server src/contenido/inventario -p ${PUERTO} -c-1 --silent`,
    url: `http://127.0.0.1:${PUERTO}/index.html`,
    reuseExistingServer: true,
    timeout: 60000,
  },
});
