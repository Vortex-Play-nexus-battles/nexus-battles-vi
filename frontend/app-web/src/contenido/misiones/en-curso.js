/**
 * Misiones en curso — UXC-5 (ActiveMissionPanel).
 *
 * §7.8.9, «Panel de misiones activas»: las misiones que se están ejecutando,
 * el tiempo que les queda contado en vivo, el progreso estimado si es
 * calculable, el héroe asignado con acceso a su equipamiento y la opción de
 * cancelar con advertencia de penalización.
 *
 * El contador es el de las subastas (`comun/ui/cuenta-atras.js`): un solo
 * latido para toda la página, que se acelera en la última hora. Cuando llega
 * a cero no se inventa el resultado: se dice que la simulación terminó y que
 * el reporte lo prepara el servidor.
 *
 * @module contenido/misiones/en-curso
 */

import { h, vaciar } from '../../comun/ui/dom.js';
import { icono } from '../../comun/ui/icono.js';
import { aviso } from '../../comun/ui/aviso.js';
import { confirmar } from '../../comun/ui/dialogo.js';
import { cuentaAtras, vigilarCuentasAtras } from '../../comun/ui/cuenta-atras.js';
import { estadoDeCarga, estadoDeError, estadoVacio } from '../../comun/ui/estado-vista.js';
import { fechaHora } from '../../comun/ui/formato.js';
import { retratoDeHeroe } from '../../comun/ui/juego/heroe.js';
import { identidadDePrototipo } from '../../comun/ui/juego/prototipos.js';
import { chipDeCategoria, progresoDeMision } from './tablon-misiones.js';

/**
 * Pone o quita el estado «trabajando» de un botón (fuera del flujo asíncrono,
 * ver require-atomic-updates).
 *
 * @param {HTMLButtonElement} boton
 * @param {boolean} activo
 */
function ocupado(boton, activo) {
  boton.disabled = activo;
  if (activo) {
    boton.setAttribute('aria-busy', 'true');
  } else {
    boton.removeAttribute('aria-busy');
  }
}

/**
 * Una misión en curso.
 *
 * @param {import('./fuente-misiones.js').MisionActiva} activa
 * @param {{hrefDe: Function, hrefEquipamiento: string, alCancelar: (activa: object) => void}} opciones
 * @returns {HTMLElement}
 */
export function tarjetaDeMisionActiva(activa, { hrefDe, hrefEquipamiento, alCancelar }) {
  const identidad = activa.heroe?.prototipo ? identidadDePrototipo(activa.heroe.prototipo) : null;
  const idNombre = `activa-${activa.ejecucionId}-nombre`;
  const cancelar = h('button', {
    clase: 'boton boton--peligro boton--pequeno',
    datos: { accion: 'cancelar-mision' },
    atributos: { type: 'button', 'aria-describedby': idNombre },
    hijos: [icono('cerrar', { etiqueta: null }), h('span', { texto: 'Cancelar misión' })],
  });
  cancelar.addEventListener('click', () => alCancelar(activa, cancelar));

  return h('article', {
    clase: 'tarjeta mision-activa',
    datos: { ejecucion: activa.ejecucionId, mision: activa.misionId },
    atributos: { 'aria-labelledby': idNombre },
    hijos: [
      h('header', {
        clase: 'mision-activa__cabecera',
        hijos: [
          chipDeCategoria(activa.categoria),
          h('h3', {
            clase: 'mision-activa__nombre',
            texto: activa.nombre,
            atributos: { id: idNombre },
          }),
        ],
      }),
      h('div', {
        clase: 'mision-activa__tiempo',
        hijos: [
          h('p', {
            clase: 'contador mision-activa__contador',
            hijos: [
              icono('reloj', { etiqueta: null }),
              h('span', {
                clase: 'mision-activa__prefijo',
                texto: 'Termina en',
                atributos: { 'aria-hidden': 'true' },
              }),
              cuentaAtras(activa.terminaEn, { prefijo: 'Termina en' }),
            ],
          }),
          h('p', {
            clase: 'mision-activa__inicio t-meta',
            texto: `Empezó el ${fechaHora(activa.iniciadaEn)}`,
          }),
        ],
      }),
      Number.isFinite(activa.progreso) ? progresoDeMision(activa.progreso, activa.nombre) : null,
      h('div', {
        clase: 'mision-activa__heroe',
        hijos: [
          retratoDeHeroe(
            { nombre: activa.heroe?.nombre ?? 'Héroe', prototipo: activa.heroe?.prototipo ?? null },
            { conNombre: false },
          ),
          h('div', {
            hijos: [
              h('p', {
                clase: 'mision-activa__heroe-nombre',
                texto: activa.heroe?.nombre ?? 'Héroe',
              }),
              h('p', {
                clase: 't-meta',
                texto: identidad
                  ? `${identidad.nombre} · en misión: no puede jugar ni cambiar su equipo`
                  : 'En misión: no puede jugar ni cambiar su equipo',
              }),
              h('a', {
                clase: 'mision-activa__equipo',
                texto: 'Ver su equipamiento',
                atributos: { href: hrefEquipamiento },
              }),
            ],
          }),
        ],
      }),
      h('div', {
        clase: 'mision-activa__acciones',
        hijos: [
          h('a', {
            clase: 'boton boton--secundario boton--pequeno',
            texto: 'Ver misión',
            atributos: { href: hrefDe({ id: activa.misionId }) },
            datos: { accion: 'ver-mision' },
          }),
          cancelar,
        ],
      }),
    ],
  });
}

/**
 * Monta el panel: pide las activas y las pinta, con sus cuatro estados.
 *
 * @param {HTMLElement} zona
 * @param {object} opciones
 * @param {import('./fuente-misiones.js').FuenteDeMisiones} opciones.fuente
 * @param {Function} opciones.hrefDe
 * @param {string} opciones.hrefEquipamiento
 * @param {string} opciones.hrefTablon
 * @param {(cuantas: number) => void} [opciones.alContar] para el número de la pestaña
 * @returns {{recargar: () => Promise<void>, detener: () => void}}
 */
export function montarEnCurso(
  zona,
  { fuente, hrefDe, hrefEquipamiento, hrefTablon, alContar = () => {} },
) {
  const avisos = h('div', { clase: 'misiones-en-curso__aviso', datos: { zona: 'aviso' } });
  const lista = h('div', { clase: 'misiones-en-curso__lista' });
  zona.replaceChildren(avisos, lista);
  let detenerLatido = () => {};

  async function cancelarMision(activa, boton) {
    const cuerpo = h('div', {
      clase: 'pila pila--compacta',
      hijos: [
        h('p', {
          texto: `${activa.heroe?.nombre ?? 'Tu héroe'} vuelve al inventario y la misión queda como abandonada.`,
        }),
        h('p', {
          clase: 'mision-activa__penalizacion',
          hijos: [
            icono('alerta', { etiqueta: null }),
            h('span', {
              texto: activa.penalizacion
                ? `Penalización: ${activa.penalizacion}`
                : 'Cancelar tiene penalización.',
            }),
          ],
        }),
      ],
    });
    const seguro = await confirmar({
      titulo: `¿Cancelar «${activa.nombre}»?`,
      cuerpo,
      textoConfirmar: 'Cancelar misión',
      textoCancelar: 'Seguir en la misión',
      peligro: true,
    });
    if (!seguro) {
      return;
    }
    ocupado(boton, true);
    try {
      await fuente.cancelar(activa.ejecucionId);
      vaciar(avisos).append(
        aviso({
          tono: 'exito',
          titulo: `Cancelaste «${activa.nombre}»`,
          detalle: `${activa.heroe?.nombre ?? 'Tu héroe'} ya está libre.`,
        }),
      );
      await cargar();
    } catch (fallo) {
      console.error('No se pudo cancelar la misión', fallo);
      ocupado(boton, false);
      vaciar(avisos).append(
        aviso({
          tono: 'error',
          titulo: 'No pudimos cancelar la misión',
          detalle: fallo?.detalle ?? 'Sigue en curso. Inténtalo de nuevo en un momento.',
        }),
      );
    }
  }

  async function cargar() {
    detenerLatido();
    vaciar(lista).append(estadoDeCarga({ filas: 2, etiqueta: 'Cargando tus misiones en curso…' }));
    let activas;
    try {
      activas = await fuente.activas();
    } catch (fallo) {
      console.error('No se pudieron leer las misiones en curso', fallo);
      vaciar(lista).append(
        estadoDeError({
          titulo: 'No pudimos cargar tus misiones en curso',
          detalle: 'Tus héroes siguen donde estaban. Inténtalo de nuevo.',
          alReintentar: cargar,
        }),
      );
      return;
    }
    const enCurso = Array.isArray(activas) ? activas : [];
    alContar(enCurso.length);
    vaciar(lista);
    if (enCurso.length === 0) {
      lista.append(
        estadoVacio({
          titulo: 'Ningún héroe está en misión',
          detalle:
            'Cuando envíes a uno, aquí verás cuánto le queda y podrás cancelarla si hace falta.',
          accion: { texto: 'Elegir una misión', href: hrefTablon },
        }),
      );
      return;
    }
    lista.append(
      h('ul', {
        clase: 'misiones-en-curso__rejilla',
        atributos: { 'aria-label': 'Misiones en curso' },
        hijos: enCurso.map((activa) =>
          h('li', {
            hijos: [
              tarjetaDeMisionActiva(activa, {
                hrefDe,
                hrefEquipamiento,
                alCancelar: cancelarMision,
              }),
            ],
          }),
        ),
      }),
    );
    guardarLatido(vigilarCuentasAtras(lista, { prefijo: 'Termina en' }));
  }

  function guardarLatido(detener) {
    detenerLatido = detener;
  }

  cargar();
  return { recargar: cargar, detener: () => detenerLatido() };
}
