/**
 * HU-INV-001 - Pruebas de aceptacion de la vitrina en navegador real.
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
    // Se sirve `src` completo: la pagina importa modulos de otras carpetas
    // hermanas (la barra de HU-INV-004 vive en `contenido/navegacion/`).
    command: `npx http-server src -p ${PUERTO} -c-1 --silent`,
    url: `http://127.0.0.1:${PUERTO}/contenido/inventario/inventario.html`,
    reuseExistingServer: true,
    timeout: 60000,
  },
});
