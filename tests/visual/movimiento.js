/**
 * Modo determinista del movimiento para el laboratorio — MOTION-1.
 *
 * Una captura que se toma a mitad de una animación sale distinta cada vez:
 * el héroe a medio lunge, la cifra a medio subir, el panel a medio entrar.
 * Con `<html data-test-motion="off">` el kit deja toda animación y
 * transición de CSS en un cuadro (tokens.css) y `motion.js` no arranca
 * ninguna por la Web Animations API. Lo que se fotografía es el estado
 * final, siempre el mismo.
 *
 * Es un atributo y no `reducedMotion: 'reduce'` del navegador porque son
 * dos cosas: la preferencia del sistema es de la persona y se prueba
 * aparte (`motion.spec.js`); esto es del laboratorio.
 *
 * @param {import('@playwright/test').BrowserContext|import('@playwright/test').Page} donde
 */
export async function sinMovimiento(donde) {
  await donde.addInitScript(() => {
    const poner = () => document.documentElement?.setAttribute('data-test-motion', 'off');
    if (document.documentElement) {
      poner();
      return;
    }
    // El script corre antes de que el analizador inserte <html>: se espera.
    new MutationObserver((_, observador) => {
      if (document.documentElement) {
        poner();
        observador.disconnect();
      }
    }).observe(document, { childList: true });
  });
}
