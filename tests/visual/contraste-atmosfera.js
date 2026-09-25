/**
 * Contraste del texto que cae sobre la atmósfera — UX-GAME-1.
 *
 * Desde UX-GAME-1 las 35 vistas viven sobre un fondo índigo con halos
 * (`body` en `base.css`). axe-core **no mide** el contraste de un texto cuyo
 * fondo efectivo es un degradado: lo apunta como «incomplete» y sigue. Así
 * pasaron cinco textos oscuros sobre fondo oscuro con axe en verde —el título
 * de auditoría, el alcance de la consola, una pista de campo, la paginación.
 *
 * Este módulo cubre justo ese hueco y nada más: recorre el texto visible de
 * la página, se queda con el que NO está sobre una superficie (ningún
 * ancestro con color de fondo opaco ni imagen de fondo) y calcula su
 * contraste WCAG contra los dos extremos del degradado, `--cromo` y
 * `--cromo-elevado`. El peor de los dos tiene que dar AA: 4,5:1 en texto
 * normal, 3:1 en texto grande. El texto que sí está sobre una superficie ya
 * lo mide axe; aquí se ignora.
 *
 * Se llama desde el navegador con `page.evaluate`, así que no puede importar
 * nada: la función va entera.
 */

/**
 * @param {import('@playwright/test').Page} pagina
 * @returns {Promise<Array<{selector: string, color: string, contraste: string, texto: string}>>}
 */
export function textoSinContrasteSobreAtmosfera(pagina) {
  return pagina.evaluate(() => {
    const parsear = (c) => {
      // UXC-8 — `color-mix()` se resuelve como `color(srgb r g b)`, con los
      // canales entre 0 y 1: sin leerlo, una fila con fondo mezclado (la
      // subasta superada) contaba como transparente y su texto como si
      // estuviera sobre la atmósfera.
      const srgb = c.match(/color\(srgb\s+([^)]+)\)/);
      if (srgb) {
        const [canales, alfa] = srgb[1].split('/');
        const [r, g, b] = canales
          .trim()
          .split(/\s+/)
          .map((v) => Number(v) * 255);
        return { r, g, b, a: alfa === undefined ? 1 : Number(alfa) };
      }
      const m = c.match(/rgba?\(([^)]+)\)/);
      if (!m) {
        return null;
      }
      const p = m[1]
        .split(/[\s,/]+/)
        .filter(Boolean)
        .map(Number);
      return { r: p[0], g: p[1], b: p[2], a: p.length > 3 ? p[3] : 1 };
    };
    const luminancia = ({ r, g, b }) => {
      const f = (c) => {
        c /= 255;
        return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
      };
      return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b);
    };
    const contraste = (a, b) => {
      const la = luminancia(a);
      const lb = luminancia(b);
      return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    };
    const raiz = getComputedStyle(document.documentElement);
    const token = (nombre) => {
      const sonda = document.createElement('div');
      sonda.style.color = raiz.getPropertyValue(nombre);
      document.body.append(sonda);
      const c = parsear(getComputedStyle(sonda).color);
      sonda.remove();
      return c;
    };
    const cromo = token('--cromo');
    const elevado = token('--cromo-elevado');
    if (!cromo || !elevado) {
      return [];
    }

    const hallazgos = [];
    const vistos = new Set();
    const paseo = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    let nodo;
    while ((nodo = paseo.nextNode())) {
      if (!nodo.textContent.trim()) {
        continue;
      }
      const el = nodo.parentElement;
      if (
        !el ||
        vistos.has(el) ||
        ['SCRIPT', 'STYLE', 'TEMPLATE', 'NOSCRIPT'].includes(el.tagName)
      ) {
        continue;
      }
      vistos.add(el);
      const caja = el.getBoundingClientRect();
      if (caja.width === 0 || caja.height === 0) {
        continue;
      }
      const estilo = getComputedStyle(el);
      if (
        estilo.visibility === 'hidden' ||
        estilo.display === 'none' ||
        Number(estilo.opacity) === 0
      ) {
        continue;
      }
      // ¿Sobre una superficie? Entonces lo mide axe y aquí no cuenta.
      let ancestro = el;
      let sobreSuperficie = false;
      while (ancestro && ancestro !== document.body) {
        const s = getComputedStyle(ancestro);
        const fondo = parsear(s.backgroundColor);
        if ((fondo && fondo.a > 0.9) || s.backgroundImage !== 'none') {
          sobreSuperficie = true;
          break;
        }
        ancestro = ancestro.parentElement;
      }
      if (sobreSuperficie) {
        continue;
      }
      const color = parsear(estilo.color);
      if (!color || color.a === 0) {
        continue;
      }
      const tamano = parseFloat(estilo.fontSize);
      const negrita = Number(estilo.fontWeight) >= 700;
      const grande = tamano >= 24 || (tamano >= 18.66 && negrita);
      const minimo = grande ? 3 : 4.5;
      const peor = Math.min(contraste(color, cromo), contraste(color, elevado));
      if (peor < minimo) {
        const clases =
          typeof el.className === 'string' && el.className.trim()
            ? `.${el.className.trim().split(/\s+/).join('.')}`
            : '';
        hallazgos.push({
          selector: el.tagName.toLowerCase() + clases,
          color: estilo.color,
          contraste: peor.toFixed(2),
          texto: nodo.textContent.trim().slice(0, 60),
        });
      }
    }
    return hallazgos;
  });
}
