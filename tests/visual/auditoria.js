/**
 * Auditorías estructurales — UX-R2.1.
 *
 * ## Por qué no son capturas comparadas píxel a píxel
 *
 * Una comparación de imágenes falla con cada cambio de fuente, cada píxel de
 * antialiasing y cada actualización del navegador. Acaba desactivada en dos
 * semanas. Lo que de verdad hace falta detectar es **estructura rota**:
 * algo que desborda, un modal fuera de pantalla, la cabecera partida en tres
 * filas, un botón al que no se llega con el dedo.
 *
 * Eso se mide, no se compara. Cada función de aquí devuelve una lista de
 * hallazgos con su motivo; la captura queda como evidencia para mirarla, no
 * como oráculo.
 *
 * Todas se ejecutan dentro del navegador con `page.evaluate`, porque hacen
 * falta medidas reales de maquetación.
 */

import { OBJETIVO_TACTIL } from './vistas.js';

/**
 * Desbordamiento horizontal: el síntoma número uno de un responsive roto.
 * Se mide sobre el documento y sobre cada elemento, porque un hijo que se sale
 * a veces no mueve el `scrollWidth` del `body`.
 *
 * @param {import('@playwright/test').Page} pagina
 * @returns {Promise<Array<{motivo: string, detalle: string}>>}
 */
export async function desbordamientos(pagina) {
  return pagina.evaluate(() => {
    const hallazgos = [];
    const doc = document.documentElement;
    // 1 px de margen: los redondeos de subpíxel no son un defecto.
    if (doc.scrollWidth > doc.clientWidth + 1) {
      hallazgos.push({
        motivo: 'desbordamiento-horizontal',
        detalle: `el documento mide ${doc.scrollWidth}px en un viewport de ${doc.clientWidth}px`,
      });
    }
    const ancho = doc.clientWidth;
    for (const el of document.querySelectorAll('body *')) {
      const caja = el.getBoundingClientRect();
      if (caja.width === 0 || caja.height === 0) {
        continue;
      }
      const fijo = getComputedStyle(el).position === 'fixed';
      if (!fijo && caja.right > ancho + 1) {
        hallazgos.push({
          motivo: 'elemento-fuera-del-viewport',
          detalle: `${el.tagName.toLowerCase()}.${el.className || '(sin clase)'} acaba en ${Math.round(caja.right)}px`,
        });
        // Un solo ejemplo por vista: el primero ya señala el sitio.
        break;
      }
    }
    return hallazgos;
  });
}

/**
 * La cabecera no debe partirse en más de dos filas. En la captura de 800 px
 * del diagnóstico se partía en tres, que es el caso que esto detecta.
 *
 * @param {import('@playwright/test').Page} pagina
 */
export async function cabeceraEnFilas(pagina) {
  return pagina.evaluate(() => {
    const cabecera = document.querySelector('.cabecera');
    if (!cabecera) {
      return [];
    }
    const alto = cabecera.getBoundingClientRect().height;
    // `--cabecera-alto` son 64 px. Dos filas caben en 140; tres no.
    if (alto > 140) {
      return [
        {
          motivo: 'cabecera-en-mas-de-dos-filas',
          detalle: `la cabecera mide ${Math.round(alto)}px (una fila son 64px)`,
        },
      ];
    }
    return [];
  });
}

/**
 * Un diálogo abierto tiene que caber en la pantalla. Un modal cortado por
 * arriba deja el título fuera; cortado por abajo, los botones.
 *
 * @param {import('@playwright/test').Page} pagina
 */
export async function modalesCortados(pagina) {
  return pagina.evaluate(() => {
    const hallazgos = [];
    for (const dialogo of document.querySelectorAll('[role="dialog"], dialog[open]')) {
      const caja = dialogo.getBoundingClientRect();
      if (caja.width === 0) {
        continue;
      }
      if (caja.top < 0 || caja.bottom > window.innerHeight || caja.left < 0) {
        hallazgos.push({
          motivo: 'modal-fuera-de-pantalla',
          detalle: `top ${Math.round(caja.top)} / bottom ${Math.round(caja.bottom)} en ${window.innerHeight}px`,
        });
      }
    }
    return hallazgos;
  });
}

/**
 * En móvil, todo lo que se pulsa necesita 44×44 (WCAG 2.5.5). Se mide solo por
 * debajo de 768 px: en escritorio se apunta con ratón.
 *
 * @param {import('@playwright/test').Page} pagina
 * @param {number} minimo
 */
export async function objetivosTactiles(pagina, minimo = OBJETIVO_TACTIL) {
  return pagina.evaluate((min) => {
    const hallazgos = [];
    const pulsables = document.querySelectorAll(
      'button:not([disabled]), a[href], input[type="checkbox"], input[type="radio"], [role="tab"], [role="menuitem"]',
    );
    for (const el of pulsables) {
      const caja = el.getBoundingClientRect();
      if (caja.width === 0 || caja.height === 0) {
        continue;
      }
      // Un enlace dentro de un párrafo es texto, no un control: se mide solo
      // lo que se presenta como control.
      const estilo = getComputedStyle(el);
      if (el.tagName === 'A' && estilo.display === 'inline') {
        continue;
      }
      if (caja.height < min || caja.width < min) {
        hallazgos.push({
          motivo: 'objetivo-tactil-pequeno',
          detalle: `${el.tagName.toLowerCase()}«${(el.textContent ?? '').trim().slice(0, 24)}» mide ${Math.round(caja.width)}×${Math.round(caja.height)}`,
        });
      }
    }
    return hallazgos;
  }, minimo);
}

/**
 * Texto cortado por accidente: un contenedor con `overflow: hidden` cuyo
 * contenido no cabe y que **no** declara `text-overflow: ellipsis`. Con
 * puntos suspensivos es una decisión; sin ellos es una rotura.
 *
 * @param {import('@playwright/test').Page} pagina
 */
export async function textoCortado(pagina) {
  return pagina.evaluate(() => {
    const hallazgos = [];
    for (const el of document.querySelectorAll('body *')) {
      if (!el.textContent?.trim() || el.children.length > 0) {
        continue;
      }
      const estilo = getComputedStyle(el);
      if (estilo.overflow === 'visible' || estilo.textOverflow === 'ellipsis') {
        continue;
      }
      // Las etiquetas para lector de pantalla (`.solo-lectores` del kit) se
      // recortan a 1px A PROPOSITO. No son texto roto: son texto que no se
      // quiere ver. Sin esta salvedad, «Buscar productos» de la cabecera se
      // denunciaba en las 31 vistas.
      const recortada = estilo.clip === 'rect(0px, 0px, 0px, 0px)' || el.clientWidth <= 4;
      if (recortada) {
        continue;
      }
      if (el.scrollWidth > el.clientWidth + 2 && el.clientWidth > 0) {
        hallazgos.push({
          motivo: 'texto-cortado',
          detalle: `«${el.textContent.trim().slice(0, 32)}» necesita ${el.scrollWidth}px y tiene ${el.clientWidth}px`,
        });
        break;
      }
    }
    return hallazgos;
  });
}

/**
 * Un error técnico como contenido principal. La norma del producto es que el
 * código HTTP puede ir en el detalle, nunca como el mensaje que lee el
 * jugador. Esto lo comprueba sobre el texto visible.
 *
 * @param {import('@playwright/test').Page} pagina
 */
export async function erroresTecnicosVisibles(pagina) {
  return pagina.evaluate(() => {
    const texto = document.body.innerText ?? '';
    // El código HTTP suelto NO sirve como señal: «Hasta 500 caracteres» es una
    // pista de ayuda legítima y salía denunciada en `chat`. Lo que delata un
    // error técnico es el código ACOMPAÑADO de su etiqueta.
    const sospechas = [
      /\b(?:Error|HTTP|[Ss]tatus|[Cc]ódigo|[Cc]odigo)\s*:?\s*\d{3}\b/,
      // «El servicio respondio 404.»: el arnes no lo veia porque la etiqueta
      // no era ninguna de las de arriba, y estaba en la pantalla de Batallas
      // —la entrada al juego— desde siempre (UX-R2.4).
      /\brespondi[oó]\s+\d{3}\b/,
      /\[object Object\]/,
      /\bundefined\b/,
      /\bNaN\b/,
      /TypeError|ReferenceError/,
    ];
    return sospechas
      .filter((patron) => patron.test(texto))
      .map((patron) => ({
        motivo: 'error-tecnico-visible',
        detalle: `el texto de la pantalla contiene ${patron}`,
      }));
  });
}

/**
 * Contraste real de texto contra su fondo real.
 *
 * ## Por qué se mide aquí y no solo en `tokens.css`
 *
 * El kit documenta la razón de contraste de cada pareja de tokens, pero eso
 * solo demuestra que las parejas PREVISTAS cumplen. Lo que rompe el contraste
 * es usar una pareja que nadie previó: texto oscuro sobre `--cromo` porque la
 * vista heredó el color del `body`. Eso no se ve leyendo el CSS; se ve
 * midiendo el píxel calculado.
 *
 * Umbrales WCAG 2.1 AA (1.4.3): 4,5:1 normal · 3:1 texto grande (≥24px, o
 * ≥18,66px en negrita).
 *
 * @param {import('@playwright/test').Page} pagina
 */
export async function contraste(pagina) {
  return pagina.evaluate(() => {
    /** @param {string} css `rgb(a)` calculado */
    const aRgb = (css) => {
      const n = css.match(/[\d.]+/g);
      return n ? { r: +n[0], g: +n[1], b: +n[2], a: n[3] === undefined ? 1 : +n[3] } : null;
    };
    const luminancia = ({ r, g, b }) => {
      const c = [r, g, b].map((v) => {
        const s = v / 255;
        return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
      });
      return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
    };
    const razon = (a, b) => {
      const [x, y] = [luminancia(a), luminancia(b)].sort((p, q) => q - p);
      return (x + 0.05) / (y + 0.05);
    };
    // El fondo efectivo es el del primer ancestro que no sea transparente.
    //
    // Si por el camino aparece un degradado o una imagen, se devuelve `null`:
    // el color real depende del punto de la pantalla y aquí no se puede saber.
    // Denunciarlo igual daría un número inventado — `productos` salía a 1,08:1
    // con texto blanco sobre `linear-gradient(125deg, var(--cromo), #2d3767)`,
    // que en realidad se lee perfectamente. Más vale callar que mentir.
    const fondoDe = (el) => {
      let nodo = el;
      while (nodo && nodo !== document.documentElement.parentNode) {
        const estilo = getComputedStyle(nodo);
        if (estilo.backgroundImage !== 'none') {
          return null;
        }
        const c = aRgb(estilo.backgroundColor);
        if (c && c.a > 0.95) {
          return c;
        }
        nodo = nodo.parentElement;
      }
      return { r: 255, g: 255, b: 255, a: 1 };
    };

    const hallazgos = [];
    const vistos = new Set();
    for (const el of document.querySelectorAll('body *')) {
      // Solo hojas con texto propio: si tiene hijos, el texto es de ellos.
      const propio = [...el.childNodes]
        .filter((n) => n.nodeType === Node.TEXT_NODE)
        .map((n) => n.textContent.trim())
        .join(' ')
        .trim();
      if (!propio) {
        continue;
      }
      const caja = el.getBoundingClientRect();
      if (caja.width === 0 || caja.height === 0) {
        continue;
      }
      const estilo = getComputedStyle(el);
      if (estilo.visibility === 'hidden' || +estilo.opacity < 0.1) {
        continue;
      }
      // WCAG 1.4.3 exceptua explicitamente los controles deshabilitados: un
      // boton apagado DEBE verse apagado. Sin esta linea, el arnes denunciaba
      // el «PAGAR» de la tienda por tener el color de su estado inactivo.
      if (el.closest('[disabled], [aria-disabled="true"], :disabled')) {
        continue;
      }
      const texto = aRgb(estilo.color);
      if (!texto || texto.a < 0.95) {
        continue;
      }
      const fondo = fondoDe(el);
      if (!fondo) {
        continue;
      }
      const tam = parseFloat(estilo.fontSize);
      const grande = tam >= 24 || (tam >= 18.66 && +estilo.fontWeight >= 700);
      const minimo = grande ? 3 : 4.5;
      const medida = razon(texto, fondo);
      if (medida < minimo) {
        // Una firma por combinación color/fondo/tamaño: si la misma pareja
        // falla en veinte celdas de una tabla, el defecto es uno.
        const firma = `${estilo.color}|${estilo.fontSize}|${medida.toFixed(2)}`;
        if (vistos.has(firma)) {
          continue;
        }
        vistos.add(firma);
        hallazgos.push({
          motivo: 'contraste-insuficiente',
          detalle: `«${propio.slice(0, 28)}» ${medida.toFixed(2)}:1 (mínimo ${minimo}:1, ${Math.round(tam)}px)`,
        });
      }
    }
    return hallazgos;
  });
}

/**
 * Pasa todas las auditorías que correspondan a esa anchura.
 *
 * @param {import('@playwright/test').Page} pagina
 * @param {{ancho: number}} pantalla
 * @returns {Promise<Array<{motivo: string, detalle: string}>>}
 */
export async function auditar(pagina, pantalla) {
  const hallazgos = [
    ...(await desbordamientos(pagina)),
    ...(await cabeceraEnFilas(pagina)),
    ...(await modalesCortados(pagina)),
    ...(await textoCortado(pagina)),
    ...(await erroresTecnicosVisibles(pagina)),
    ...(await contraste(pagina)),
  ];
  if (pantalla.ancho < 768) {
    hallazgos.push(...(await objetivosTactiles(pagina)));
  }
  return hallazgos;
}
