/**
 * «Registrar mi equipo» sin datos técnicos — RFINAL-04 (revisión de AWS DEV
 * del 4-oct, informes del jugador y del superadministrador).
 *
 * El formulario pedía pegar el «Identificador de tu compañero» (un UUID) y el
 * avatar como texto («Identificador o dirección de la imagen»). Nadie se sabe
 * un UUID de memoria, y una dirección de imagen escrita a mano es un dato
 * técnico. Ahora:
 *
 *   - **Compañero por apodo.** Se busca con la búsqueda que ya existe,
 *     `GET /api/v1/perfiles/publicos?apodo=` (ms-identidad-perfiles.yaml,
 *     B6): datos públicos, mínimo 3 letras, máximo 10 resultados. Se elige
 *     de la lista y lo que viaja a torneos sigue siendo el `uid` del contrato
 *     (`CrearEquipoRequest.companeroUid`); la persona no lo ve nunca.
 *   - **Emblema en vez de texto.** Un selector con los emblemas del juego.
 *     El contrato pide un `avatar` de 1 a 300 caracteres que pasa por la
 *     lista negra; se manda un identificador neutro (`emblema-03`), que no
 *     puede chocar con un término prohibido, y la vista lo pinta con su
 *     imagen.
 *
 * @module plataforma/torneos/equipo-formulario
 */

import { fetchWithHttpErrorInterceptor } from '../../comun/interceptors/http-error.interceptor.js';
import { h } from '../../comun/ui/dom.js';
import { campo } from '../../comun/ui/campo.js';

/** La búsqueda pública de jugadores por apodo (ms-identidad). */
export const RUTA_DE_PERFILES_PUBLICOS = '/api/v1/perfiles/publicos';

/** Letras mínimas de una búsqueda (`apodo.minLength` del contrato). */
export const MINIMO_DE_BUSQUEDA = 3;

/**
 * Los emblemas que se pueden elegir. El `id` es lo que viaja; la imagen es
 * la del juego (`cuentas/avatares`).
 */
export const EMBLEMAS = Object.freeze([
  { id: 'emblema-01', nombre: 'Guerrero', archivo: 'guerrero-berserker.jpg' },
  { id: 'emblema-02', nombre: 'Escudo', archivo: 'guerrero-tanque.jpg' },
  { id: 'emblema-03', nombre: 'Fuego', archivo: 'mago-de-fuego.jpg' },
  { id: 'emblema-04', nombre: 'Hielo', archivo: 'mago-de-hielo.jpg' },
  { id: 'emblema-05', nombre: 'Sabio', archivo: 'gran-mago-sabio.jpg' },
  { id: 'emblema-06', nombre: 'Sombra', archivo: 'picaro-asesino.jpg' },
  { id: 'emblema-07', nombre: 'Arco', archivo: 'arquero-cazador.jpg' },
  { id: 'emblema-08', nombre: 'Sanación', archivo: 'chaman-sanador.jpg' },
  { id: 'emblema-09', nombre: 'Alquimia', archivo: 'alquimista-picaro-veneno.jpg' },
  { id: 'emblema-10', nombre: 'Comandante', archivo: 'comandante-con-casco.jpg' },
]);

/**
 * La dirección de la imagen de un emblema, relativa a este módulo: vale desde
 * cualquier vista que lo importe.
 *
 * @param {{archivo: string}} emblema
 * @returns {string}
 */
export function imagenDeEmblema(emblema) {
  return new URL(`../../cuentas/avatares/${emblema.archivo}`, import.meta.url).href;
}

/**
 * El emblema de un `avatar` de equipo, o null si no es uno de los nuestros
 * (equipos registrados antes, o de la máquina).
 *
 * @param {unknown} avatar
 * @returns {{id: string, nombre: string, archivo: string}|null}
 */
export function emblemaDe(avatar) {
  return EMBLEMAS.find((emblema) => emblema.id === avatar) ?? null;
}

function baseDeApi() {
  const meta = globalThis.document?.querySelector?.('meta[name="nexus-api-base"]');
  return String(meta?.content ?? '').replace(/\/+$/, '');
}

/**
 * Busca jugadores por apodo para elegir compañero. Quien busca no sale en la
 * lista: no puede ser su propio compañero.
 *
 * @param {string} apodo
 * @param {{fetchImpl?: Function, yo?: string|null}} [opciones]
 * @returns {Promise<Array<{uid: string, apodo: string, avatar: string|null}>>}
 * @throws {Error} si el servicio no responde
 */
export async function buscarCompaneros(
  apodo,
  { fetchImpl = fetchWithHttpErrorInterceptor, yo = null } = {},
) {
  const texto = String(apodo ?? '').trim();
  if (texto.length < MINIMO_DE_BUSQUEDA) {
    return [];
  }
  const respuesta = await fetchImpl(
    `${baseDeApi()}${RUTA_DE_PERFILES_PUBLICOS}?${new URLSearchParams({ apodo: texto })}`,
    { headers: { Accept: 'application/json' } },
  );
  if (!respuesta.ok) {
    throw new Error('La búsqueda de jugadores no respondió.');
  }
  const lista = await respuesta.json();
  return (Array.isArray(lista) ? lista : [])
    .filter((perfil) => perfil && typeof perfil.uid === 'string' && perfil.uid !== yo)
    .map((perfil) => ({
      uid: perfil.uid,
      apodo: String(perfil.apodo ?? '').trim() || 'Jugador',
      avatar: typeof perfil.avatar === 'string' ? perfil.avatar : null,
    }));
}

/**
 * El campo «Tu compañero»: se escribe parte del apodo, se busca y se elige.
 *
 * No es un `<form>`: vive dentro del formulario del equipo, y un formulario
 * dentro de otro no existe en HTML. Enter en la búsqueda busca; no envía el
 * equipo.
 *
 * @param {{fetchImpl?: Function, yo?: string|null}} [opciones]
 * @returns {{elemento: HTMLElement, elegido: () => {uid: string, apodo: string}|null,
 *   marcarError: (motivo: string|null) => void}}
 */
export function selectorDeCompanero({ fetchImpl, yo = null } = {}) {
  const busqueda = campo({
    nombre: 'apodoCompanero',
    etiqueta: 'Tu compañero',
    pista: 'Escribe al menos 3 letras de su apodo y elígelo de la lista.',
    autocompletar: 'off',
    atributos: { maxlength: 50, spellcheck: false },
  });
  const buscar = h('button', {
    clase: 'boton boton--secundario',
    texto: 'Buscar',
    atributos: { type: 'button' },
    datos: { accion: 'buscar-companero' },
  });
  const resultados = h('div', {
    clase: 'buscador-jugador__resultados',
    datos: { zona: 'companeros' },
    atributos: { 'aria-live': 'polite' },
  });
  const elegidoTexto = h('p', {
    clase: 't-meta',
    datos: { zona: 'companero-elegido' },
    atributos: { hidden: true },
  });

  /** @type {{uid: string, apodo: string}|null} */
  let elegido = null;
  let consulta = 0;

  function decir(texto, error = false) {
    resultados.replaceChildren(
      h('p', {
        clase: `buscador-jugador__estado${error ? ' buscador-jugador__estado--error' : ' t-meta'}`,
        texto,
      }),
    );
  }

  function elegir(jugador) {
    elegido = { uid: jugador.uid, apodo: jugador.apodo };
    busqueda.marcarError(null);
    resultados.replaceChildren();
    elegidoTexto.textContent = `Compañero elegido: ${jugador.apodo}`;
    elegidoTexto.hidden = false;
  }

  async function ejecutarBusqueda() {
    const texto = String(busqueda.control.value ?? '').trim();
    elegido = null;
    elegidoTexto.hidden = true;
    if (texto.length < MINIMO_DE_BUSQUEDA) {
      decir('Escribe al menos 3 letras del apodo.');
      return;
    }
    const esta = ++consulta;
    decir('Buscando…');
    let jugadores;
    try {
      jugadores = await buscarCompaneros(texto, { fetchImpl, yo });
    } catch {
      if (esta === consulta) {
        decir('No pudimos buscar ahora. Vuelve a intentarlo en un momento.', true);
      }
      return;
    }
    if (esta !== consulta) {
      return;
    }
    if (jugadores.length === 0) {
      decir(`Ningún jugador tiene un apodo que empiece por «${texto}».`);
      return;
    }
    const lista = h('ul', {
      clase: 'buscador-jugador__lista',
      atributos: { 'aria-label': 'Jugadores encontrados' },
    });
    for (const jugador of jugadores) {
      const boton = h('button', {
        clase: 'buscador-jugador__resultado',
        atributos: { type: 'button', 'aria-label': `Elegir a ${jugador.apodo} como compañero` },
        datos: { jugador: jugador.uid },
        hijos: [
          h('span', { clase: 'buscador-jugador__apodo', texto: jugador.apodo }),
          h('span', {
            clase: 'buscador-jugador__accion',
            texto: 'Elegir',
            atributos: { 'aria-hidden': 'true' },
          }),
        ],
      });
      boton.addEventListener('click', () => elegir(jugador));
      lista.append(h('li', { hijos: [boton] }));
    }
    resultados.replaceChildren(lista);
  }

  buscar.addEventListener('click', () => ejecutarBusqueda());
  busqueda.control.addEventListener('keydown', (evento) => {
    if (evento.key === 'Enter') {
      evento.preventDefault();
      ejecutarBusqueda();
    }
  });
  // Cambiar el texto deshace la elección: lo elegido tiene que ser lo que se ve.
  busqueda.control.addEventListener('input', () => {
    if (elegido) {
      elegido = null;
      elegidoTexto.hidden = true;
    }
  });

  const elemento = h('div', {
    clase: 'buscador-jugador',
    datos: { zona: 'selector-companero' },
    hijos: [
      busqueda.elemento,
      h('div', { clase: 'fila fila--acciones', hijos: [buscar] }),
      resultados,
      elegidoTexto,
    ],
  });

  return {
    elemento,
    elegido: () => elegido,
    marcarError: (motivo) => busqueda.marcarError(motivo),
  };
}

/**
 * El campo «Emblema del equipo»: un selector con los emblemas del juego y su
 * imagen al lado.
 *
 * @returns {{elemento: HTMLElement, elegido: () => string}}
 */
export function selectorDeEmblema() {
  const selector = campo({
    nombre: 'emblema',
    etiqueta: 'Emblema del equipo',
    requerido: true,
    valor: EMBLEMAS[0].id,
    opciones: EMBLEMAS.map((emblema) => ({ valor: emblema.id, texto: emblema.nombre })),
    pista: 'Es la imagen con la que se reconoce a tu equipo en el torneo.',
  });
  const vista = h('img', {
    clase: 'avatar-vista-previa',
    atributos: { alt: '', width: 96, height: 96, loading: 'lazy' },
    datos: { zona: 'emblema-previo' },
  });
  function pintar() {
    const emblema = emblemaDe(selector.control.value) ?? EMBLEMAS[0];
    vista.src = imagenDeEmblema(emblema);
    vista.alt = `Emblema ${emblema.nombre}`;
  }
  selector.control.addEventListener('change', pintar);
  pintar();

  return {
    elemento: h('div', { clase: 'pila pila--ajustada', hijos: [selector.elemento, vista] }),
    elegido: () => selector.control.value,
  };
}
