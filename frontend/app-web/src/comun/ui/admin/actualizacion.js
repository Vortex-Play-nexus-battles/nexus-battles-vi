/**
 * Actualización en vivo de una sección de la consola — SA-2.
 *
 * El informe de revisión pidió que las tablas «en vivo» lo fueran de verdad
 * (punto 24). Una tabla en vivo no es la que se redibuja sin avisar: es la
 * que dice **de cuándo es** lo que se ve, se actualiza sola a un ritmo
 * razonable, deja actualizar a mano y se detiene cuando nadie mira (pestaña
 * oculta), para no cargar los servicios por nada.
 *
 *   - «Actualizado a las 14:32:05» después de cada carga correcta;
 *   - si una carga falla se dice y se conserva lo último bueno;
 *   - nunca dos cargas a la vez;
 *   - nunca más a menudo que cada 15 segundos.
 *
 * @module comun/ui/admin/actualizacion
 */

import { h } from '../dom.js';

/** Lo más a menudo que se pregunta sola una sección. */
export const PERIODO_MINIMO_MS = 15_000;

/**
 * @param {object} opciones
 * @param {() => Promise<boolean|void>} opciones.cargar consulta y pinta; un
 *   `false` (o un rechazo) es «no se pudo»
 * @param {number} [opciones.cadaMs] cada cuánto, con la pestaña a la vista
 * @param {boolean} [opciones.automatica] si arranca actualizándose sola
 * @param {string} [opciones.nombre] qué se actualiza, para el lector de pantalla
 * @returns {{elemento: HTMLElement, actualizar: () => Promise<void>, detener: () => void}}
 */
export function actualizacionPeriodica({
  cargar,
  cadaMs = 30_000,
  automatica = true,
  nombre = 'esta sección',
  documento = globalThis.document,
  reloj = globalThis,
  ahora = () => new Date(),
}) {
  if (typeof cargar !== 'function') {
    throw new TypeError('actualizacionPeriodica: falta cargar');
  }
  const periodo = Math.max(PERIODO_MINIMO_MS, Number(cadaMs) || PERIODO_MINIMO_MS);
  let temporizador = null;
  let enCurso = null;
  let detenida = false;
  let ultimaBuena = null;

  const marca = h('span', { clase: 'actualizacion-admin__marca', datos: { zona: 'marca' } });
  const boton = h('button', {
    clase: 'boton boton--contorno boton--pequeno',
    texto: 'Actualizar',
    atributos: { type: 'button', 'aria-label': `Actualizar ${nombre}` },
    datos: { accion: 'actualizar' },
  });
  const casilla = h('input', {
    clase: 'casilla__entrada',
    atributos: { type: 'checkbox', name: 'actualizacion-automatica' },
  });
  casilla.checked = Boolean(automatica);
  const elemento = h('div', {
    clase: 'actualizacion-admin',
    datos: { zona: 'actualizacion' },
    hijos: [
      marca,
      boton,
      h('label', {
        clase: 'casilla actualizacion-admin__automatica',
        hijos: [casilla, h('span', { clase: 'casilla__etiqueta', texto: 'Actualizar sola' })],
      }),
    ],
  });

  function programar() {
    reloj.clearTimeout(temporizador);
    temporizador = null;
    if (detenida || !casilla.checked || documento?.visibilityState === 'hidden') {
      return;
    }
    temporizador = reloj.setTimeout(() => {
      actualizar();
    }, periodo);
  }

  function actualizar() {
    if (detenida) {
      return Promise.resolve();
    }
    if (enCurso) {
      return enCurso;
    }
    boton.disabled = true;
    elemento.setAttribute('aria-busy', 'true');
    // `cargar` se llama en el acto; si lanza, la promesa lo recoge.
    enCurso = new Promise((resolver) => {
      resolver(cargar());
    })
      .then(
        (resultado) => resultado !== false,
        () => false,
      )
      .then((bien) => {
        if (detenida) {
          return;
        }
        if (bien) {
          ultimaBuena = ahora();
          marca.textContent = `Actualizado a las ${hora(ultimaBuena)}`;
          marca.dataset.estado = 'al-dia';
        } else {
          marca.textContent = ultimaBuena
            ? `No se pudo actualizar. Lo que ves es de las ${hora(ultimaBuena)}.`
            : 'No se pudo actualizar.';
          marca.dataset.estado = 'fallo';
        }
      })
      .finally(() => {
        enCurso = null;
        boton.disabled = false;
        elemento.removeAttribute('aria-busy');
        programar();
      });
    return enCurso;
  }

  function detener() {
    detenida = true;
    reloj.clearTimeout(temporizador);
    temporizador = null;
    documento?.removeEventListener?.('visibilitychange', alCambiarVisibilidad);
  }

  function alCambiarVisibilidad() {
    if (documento.visibilityState === 'visible' && casilla.checked) {
      // Al volver a la pestaña, lo primero es ponerse al día.
      actualizar();
    } else {
      programar();
    }
  }

  boton.addEventListener('click', () => {
    actualizar();
  });
  casilla.addEventListener('change', () => {
    if (casilla.checked) {
      actualizar();
    } else {
      programar();
    }
  });
  documento?.addEventListener?.('visibilitychange', alCambiarVisibilidad);

  return { elemento, actualizar, detener };
}

/** @param {Date} momento */
function hora(momento) {
  return momento.toLocaleTimeString('es-CO', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}
