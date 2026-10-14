/**
 * La página quieta antes de auditarla — HU-UX-003 (MOTION-4).
 *
 * ## Por qué
 *
 * El Nexus Motion System hace entrar tarjetas y cifras con un fundido
 * escalonado (hasta ~500 ms en el tablón de misiones). axe mide el contraste
 * con la opacidad del instante: un chip a medio fundido sale como
 * «color-contrast [serious]» aunque su estado final cumpla AA, y el elemento
 * señalado cambia de corrida en corrida según dónde estuviera el fundido.
 * `toBeVisible()` no protege de eso: para Playwright un elemento con
 * `opacity: 0.4` ya es visible.
 *
 * La auditoría se hace sobre lo que lee una persona: la página quieta. Las
 * animaciones infinitas (indicador de carga, halo del asistente, respiración
 * del héroe en el campo) no se esperan, y hay un plazo máximo para que una
 * animación pausada o encadenada no cuelgue la prueba. Es la misma espera que
 * ya hacía el laboratorio en `tests/visual/axe-con-datos.spec.js` (UXC);
 * el barrido del laboratorio además entra con `data-test-motion="off"`. El
 * E2E corre el juego tal cual, con su movimiento, y por eso espera.
 *
 * @module tests/e2e/ayudantes/movimiento
 */

/**
 * Espera a que terminen las animaciones finitas que haya en marcha.
 *
 * @param {import('@playwright/test').Page} page
 * @param {{ plazoMs?: number }} [opciones] plazo máximo de espera (3 s por defecto)
 */
export async function conLaPaginaQuieta(page, { plazoMs = 3000 } = {}) {
  await page
    .waitForFunction(
      () =>
        document
          .getAnimations()
          .filter((a) => Number.isFinite(a.effect?.getTiming?.().iterations ?? 1))
          .every((a) => a.playState !== 'running'),
      null,
      { timeout: plazoMs },
    )
    .catch(() => {});
}
