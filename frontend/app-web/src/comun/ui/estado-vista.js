/**
 * Estados de una zona de contenido: vacio, cargando y error
 * (`.estado-vista` en `shared/ui-kit/css/componentes.css`).
 *
 * Los tres son el MISMO hueco de la pantalla en tres momentos distintos, y
 * por eso son un solo componente con tres entradas. Antes cada vista resolvia
 * lo que le tocaba: algunas pintaban un `<p>Cargando…</p>`, otras nada, y la
 * de subastas mostraba «Error 502 al consultar subastas» —el numero de la
 * respuesta HTTP -- a un jugador que no tiene por que saber que es un 502.
 *
 * Reglas que el componente impone:
 *
 *   - un estado vacio dice QUE falta y ofrece la accion que lo llena;
 *   - un error dice que paso en lenguaje de persona y ofrece reintentar, sin
 *     codigos ni nombres de servicio;
 *   - cargando muestra la FORMA de lo que viene (esqueletos), no un girador.
 */

import { h, vaciar } from './dom.js';
import { esqueletoDeLista } from './esqueleto.js';

/**
 * @param {{titulo: string, detalle?: string|null, icono?: string|null,
 *          accion?: {texto: string, nombre?: string, alPulsar: Function, href?: string}|null}} opciones
 * @returns {HTMLElement}
 */
export function estadoVacio({ titulo, detalle = null, icono = null, accion = null }) {
  return armar({ variante: 'vacio', titulo, detalle, icono, accion });
}

/**
 * @param {{titulo?: string, detalle?: string|null,
 *          alReintentar?: Function|null, textoDeReintento?: string}} opciones
 * @returns {HTMLElement}
 */
export function estadoDeError({
  titulo = 'No pudimos cargar esta sección',
  detalle = 'Revisa tu conexión e inténtalo de nuevo.',
  alReintentar = null,
  textoDeReintento = 'Reintentar',
} = {}) {
  return armar({
    variante: 'error',
    titulo,
    detalle,
    icono: '!',
    accion: alReintentar
      ? { texto: textoDeReintento, nombre: 'reintentar', alPulsar: alReintentar }
      : null,
  });
}

/**
 * Esqueletos con la forma de lo que se esta pidiendo.
 *
 * @param {{filas?: number, etiqueta?: string}} [opciones]
 * @returns {HTMLElement}
 */
export function estadoDeCarga({ filas = 3, etiqueta = 'Cargando…' } = {}) {
  const caja = h('div', {
    clase: 'estado-vista estado-vista--cargando',
    datos: { estado: 'cargando' },
    // `polite`: avisa al lector de pantalla sin cortar lo que este leyendo.
    atributos: { 'aria-busy': 'true', 'aria-live': 'polite', 'aria-label': etiqueta },
  });
  caja.append(esqueletoDeLista(filas));
  return caja;
}

/**
 * Reemplaza el contenido de una zona por uno de los tres estados.
 *
 * @param {HTMLElement} zona
 * @param {HTMLElement} estado
 * @returns {HTMLElement} la zona
 */
export function pintarEstado(zona, estado) {
  vaciar(zona).append(estado);
  zona.hidden = false;
  return zona;
}

/**
 * @param {{variante: string, titulo: string, detalle: string|null, icono: string|null,
 *          accion: {texto: string, nombre?: string, alPulsar?: Function, href?: string}|null}} opciones
 * @returns {HTMLElement}
 */
function armar({ variante, titulo, detalle, icono, accion }) {
  const caja = h('div', {
    clase: `estado-vista estado-vista--${variante}`,
    datos: { estado: variante },
    atributos: { role: variante === 'error' ? 'alert' : 'status' },
  });
  if (icono) {
    // `aria-hidden`: es decoracion. Lo que se lee es el titulo.
    caja.append(
      h('div', {
        clase: 'estado-vista__icono',
        texto: icono,
        atributos: { 'aria-hidden': 'true' },
      }),
    );
  }
  caja.append(h('p', { clase: 'estado-vista__titulo', texto: titulo }));
  if (detalle) {
    caja.append(h('p', { clase: 'estado-vista__detalle', texto: detalle }));
  }
  if (accion) {
    caja.append(accion.href ? enlaceDeAccion(accion) : botonDeAccion(accion));
  }
  return caja;
}

function botonDeAccion(accion) {
  const boton = h('button', {
    clase: 'boton boton--primario',
    texto: accion.texto,
    atributos: { type: 'button' },
    datos: accion.nombre ? { accion: accion.nombre } : {},
  });
  boton.addEventListener('click', accion.alPulsar);
  return boton;
}

function enlaceDeAccion(accion) {
  return h('a', {
    clase: 'boton boton--primario',
    texto: accion.texto,
    atributos: { href: accion.href },
    datos: accion.nombre ? { accion: accion.nombre } : {},
  });
}
