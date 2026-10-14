/**
 * Centro de ayuda — ms-chatbot.yaml 1.8.0 (7.4.3 «enlaces a documentación
 * relevante»).
 *
 * Lo mismo que el asistente sabe responder (`GET /chat/ayuda`: los temas
 * activos de la versión en producción), para leerlo con calma: por
 * categoría, con un buscador que filtra aquí mismo y un índice con anclas
 * (`ayuda.html#categoria-cuenta-y-registro`). El asistente enlaza aquí cuando
 * no entiende una pregunta o una respuesta «no le sirvió», abierto en la
 * categoría de la pregunta.
 *
 * Pública: la ven un visitante y un jugador. Los cuatro estados (carga, error,
 * vacío y los temas), y todo se maneja con teclado: cada tema es un
 * `<details>` y el índice son enlaces.
 *
 * El texto llega del servidor y entra con `textContent` (`h()`), nunca como
 * marcado.
 *
 * @module cuentas/ayuda
 */

import { crearClienteChatbot } from '../comun/cliente-chatbot.js';
import { campo } from '../comun/ui/campo.js';
import { h, vaciar } from '../comun/ui/dom.js';
import { introAntesDePasos } from '../comun/ui/enriquecido-chatbot.js';
import {
  estadoDeCarga,
  estadoDeError,
  estadoVacio,
  pintarEstado,
} from '../comun/ui/estado-vista.js';
import { numero } from '../comun/ui/formato.js';
import { encabezadoDePagina } from '../comun/ui/pagina.js';
import { CATEGORIAS } from '../comun/ui/soporte-chatbot.js';

export const TEXTOS_AYUDA = Object.freeze({
  titulo: 'Centro de ayuda',
  descripcion:
    'Lo que el asistente sabe responder, para leerlo con calma. Si no encuentras lo que buscas, pregúntale al asistente con el botón de abajo a la derecha.',
  buscar: 'Buscar en la ayuda',
  pistaBuscar: 'Por ejemplo: subasta, contraseña o torneo.',
  indice: 'Categorías de la ayuda',
  cargando: 'Cargando el centro de ayuda…',
  errorTitulo: 'No se pudo cargar el centro de ayuda',
  errorDetalle: 'El asistente no responde ahora mismo. Vuelve a intentarlo en unos minutos.',
  vacio: 'Todavía no hay temas publicados',
  vacioDetalle: 'Mientras tanto, pregúntale al asistente: te responde o te pasa con soporte.',
  sinCoincidencias: (texto) => `Ningún tema coincide con «${texto}»`,
  sinCoincidenciasDetalle: 'Prueba con otras palabras o pregúntale al asistente.',
  verTodos: 'Ver todos los temas',
  resumen: (temas, categorias) =>
    `${numero(temas)} ${temas === 1 ? 'tema' : 'temas'} en ${numero(categorias)} ${
      categorias === 1 ? 'categoría' : 'categorías'
    }`,
  resumenBusqueda: (temas, texto) =>
    `${numero(temas)} ${temas === 1 ? 'tema coincide' : 'temas coinciden'} con «${texto}»`,
  pasos: 'Pasos',
  enIngles: 'Leer en inglés',
  generales: 'Preguntas generales',
});

/** Mínimo de letras para filtrar: con una sola, casi todo coincide. */
export const MINIMO_DE_BUSQUEDA = 2;

/**
 * Nombre de una categoría como título de la ayuda: el que lee el jugador al
 * abrir una solicitud, salvo «Otra consulta», que aquí son las preguntas
 * generales.
 *
 * @param {string} categoria valor del contrato, p. ej. `CUENTA_Y_REGISTRO`
 * @returns {string}
 */
export function nombreDeCategoria(categoria) {
  if (categoria === 'FAQ_GENERAL') {
    return TEXTOS_AYUDA.generales;
  }
  return CATEGORIAS.find((opcion) => opcion.valor === categoria)?.texto ?? categoria;
}

/**
 * Texto comparable: sin tildes, sin mayúsculas y con los espacios juntos.
 *
 * @param {string|null|undefined} texto
 * @returns {string}
 */
export function normalizar(texto) {
  return String(texto ?? '')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/\s+/g, ' ')
    .trim();
}

/**
 * Si un tema coincide con lo buscado: cada palabra buscada está en su
 * título o en su respuesta, en español o en inglés.
 *
 * @param {{titulo: string, respuestaEs: string, respuestaEn?: string|null}} tema
 * @param {string} busqueda ya normalizada
 * @returns {boolean}
 */
export function temaCoincide(tema, busqueda) {
  if (!busqueda) {
    return true;
  }
  const texto = normalizar(`${tema.titulo} ${tema.respuestaEs} ${tema.respuestaEn ?? ''}`);
  return busqueda.split(' ').every((palabra) => texto.includes(palabra));
}

/**
 * Las categorías con los temas que coinciden; sin las que se quedan vacías.
 *
 * @param {Array<{categoria: string, ancla: string, temas: object[]}>} categorias
 * @param {string} busqueda ya normalizada
 * @returns {Array<{categoria: string, ancla: string, temas: object[]}>}
 */
export function filtrar(categorias, busqueda) {
  return categorias
    .map((grupo) => ({
      ...grupo,
      temas: grupo.temas.filter((tema) => temaCoincide(tema, busqueda)),
    }))
    .filter((grupo) => grupo.temas.length > 0);
}

/**
 * @param {HTMLElement} raiz
 * @param {{cliente?: {centroDeAyuda: () => Promise<object>},
 *          ubicacion?: {hash: string}}} [opciones]
 * @returns {{cargar: () => Promise<void>}}
 */
export function montarCentroDeAyuda(
  raiz,
  { cliente = crearClienteChatbot(), ubicacion = globalThis.location } = {},
) {
  let categorias = [];

  const buscador = campo({
    nombre: 'buscar',
    etiqueta: TEXTOS_AYUDA.buscar,
    tipo: 'search',
    pista: TEXTOS_AYUDA.pistaBuscar,
    autocompletar: 'off',
  });
  buscador.elemento.hidden = true;
  const resumen = h('p', {
    clase: 't-meta ayuda__resumen',
    atributos: { role: 'status', 'aria-live': 'polite' },
    datos: { zona: 'resumen-ayuda' },
  });
  const indice = h('nav', {
    clase: 'ayuda__indice',
    atributos: { 'aria-label': TEXTOS_AYUDA.indice, hidden: true },
    datos: { zona: 'indice-ayuda' },
  });
  const zona = h('div', { clase: 'pila pila--amplia ayuda__temas', datos: { zona: 'ayuda' } });

  raiz.append(
    encabezadoDePagina({ titulo: TEXTOS_AYUDA.titulo, descripcion: TEXTOS_AYUDA.descripcion }),
    buscador.elemento,
    resumen,
    indice,
    zona,
  );

  async function cargar() {
    pintarEstado(zona, estadoDeCarga({ filas: 5, etiqueta: TEXTOS_AYUDA.cargando }));
    try {
      const centro = await cliente.centroDeAyuda();
      categorias = Array.isArray(centro?.categorias) ? centro.categorias : [];
      pintar();
      irAlAncla();
    } catch {
      categorias = [];
      buscador.elemento.hidden = true;
      indice.hidden = true;
      resumen.textContent = '';
      pintarEstado(
        zona,
        estadoDeError({
          titulo: TEXTOS_AYUDA.errorTitulo,
          detalle: TEXTOS_AYUDA.errorDetalle,
          alReintentar: () => cargar(),
        }),
      );
    }
  }

  function pintar() {
    if (categorias.length === 0) {
      buscador.elemento.hidden = true;
      indice.hidden = true;
      resumen.textContent = '';
      pintarEstado(
        zona,
        estadoVacio({ titulo: TEXTOS_AYUDA.vacio, detalle: TEXTOS_AYUDA.vacioDetalle }),
      );
      return;
    }
    buscador.elemento.hidden = false;
    const escrito = buscador.control.value.trim();
    const busqueda = normalizar(escrito).length >= MINIMO_DE_BUSQUEDA ? normalizar(escrito) : '';
    const visibles = filtrar(categorias, busqueda);
    const cuantos = visibles.reduce((suma, grupo) => suma + grupo.temas.length, 0);
    resumen.textContent = busqueda
      ? TEXTOS_AYUDA.resumenBusqueda(cuantos, escrito)
      : TEXTOS_AYUDA.resumen(cuantos, visibles.length);
    pintarIndice(visibles);
    if (visibles.length === 0) {
      pintarEstado(
        zona,
        estadoVacio({
          titulo: TEXTOS_AYUDA.sinCoincidencias(escrito),
          detalle: TEXTOS_AYUDA.sinCoincidenciasDetalle,
          accion: {
            texto: TEXTOS_AYUDA.verTodos,
            nombre: 'ver-todos',
            alPulsar: () => {
              buscador.control.value = '';
              pintar();
              buscador.control.focus();
            },
          },
        }),
      );
      return;
    }
    vaciar(zona).append(...visibles.map((grupo) => seccion(grupo, Boolean(busqueda))));
  }

  function pintarIndice(visibles) {
    vaciar(indice);
    indice.hidden = visibles.length === 0;
    indice.append(
      h('ul', {
        clase: 'ayuda__indice-lista',
        hijos: visibles.map((grupo) =>
          h('li', {
            hijos: [
              h('a', {
                clase: 'ayuda__indice-enlace',
                texto: `${nombreDeCategoria(grupo.categoria)} (${numero(grupo.temas.length)})`,
                atributos: { href: `#${grupo.ancla}` },
                datos: { categoria: grupo.categoria },
              }),
            ],
          }),
        ),
      }),
    );
  }

  function seccion(grupo, abiertos) {
    const idTitulo = `titulo-${grupo.ancla}`;
    return h('section', {
      clase: 'ayuda__categoria pila',
      atributos: { id: grupo.ancla, 'aria-labelledby': idTitulo },
      datos: { categoria: grupo.categoria },
      hijos: [
        h('h2', {
          clase: 'ayuda__titulo-categoria',
          texto: nombreDeCategoria(grupo.categoria),
          atributos: { id: idTitulo, tabindex: '-1' },
        }),
        ...grupo.temas.map((tema) => articuloDeTema(tema, abiertos)),
      ],
    });
  }

  // Abiertos al buscar: lo que coincide se lee sin un clic más.
  function articuloDeTema(tema, abierto) {
    const pasos = tema.pasosEs ?? [];
    const cuerpo = [];
    if (pasos.length > 0) {
      const intro = introAntesDePasos(tema.respuestaEs);
      if (intro) {
        cuerpo.push(h('p', { clase: 'ayuda__respuesta', texto: intro }));
      }
      cuerpo.push(
        h('ol', {
          clase: 'ayuda__pasos',
          atributos: { 'aria-label': TEXTOS_AYUDA.pasos },
          hijos: pasos.map((paso) => h('li', { texto: paso })),
        }),
      );
    } else {
      cuerpo.push(h('p', { clase: 'ayuda__respuesta', texto: tema.respuestaEs }));
    }
    if (tema.respuestaEn) {
      cuerpo.push(
        h('details', {
          clase: 'ayuda__ingles',
          hijos: [
            h('summary', { texto: TEXTOS_AYUDA.enIngles }),
            h('p', {
              clase: 'ayuda__respuesta',
              texto: tema.respuestaEn,
              atributos: { lang: 'en' },
            }),
          ],
        }),
      );
    }
    return h('details', {
      clase: 'ayuda__tema tarjeta',
      atributos: { open: abierto },
      datos: { tema: '' },
      hijos: [
        h('summary', { clase: 'ayuda__pregunta', texto: tema.titulo }),
        h('div', { clase: 'ayuda__cuerpo', hijos: cuerpo }),
      ],
    });
  }

  // `ayuda.html#categoria-...`: el asistente abre la ayuda en la categoría de
  // la pregunta. Se lleva el foco a su título para que el teclado y el lector
  // de pantalla empiecen ahí.
  function irAlAncla() {
    const ancla = String(ubicacion?.hash ?? '').replace(/^#/, '');
    if (!/^[a-z0-9-]{1,60}$/.test(ancla)) {
      return;
    }
    const destino = zona.querySelector(`section[id="${ancla}"]`);
    if (!destino) {
      return;
    }
    destino.scrollIntoView?.({ block: 'start' });
    destino.querySelector('h2')?.focus({ preventScroll: true });
  }

  buscador.control.addEventListener('input', () => pintar());
  // Un enlace del asistente con la página ya abierta solo cambia el ancla.
  globalThis.addEventListener?.('hashchange', () => irAlAncla());

  return { cargar };
}
