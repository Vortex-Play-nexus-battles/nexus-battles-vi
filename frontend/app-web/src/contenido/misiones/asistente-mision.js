/**
 * Preparar una misión, paso a paso — revisión del modo jugador del 6-oct,
 * puntos 22 y 23.
 *
 * «La sección de preparar la misión debería salir cuando uno quiera iniciar
 * la misión y no tener tanta información en una sola página» y «volver más
 * dinámico y sencillo la parte de preparar misión… más guiado entre
 * recuadros como pasos a seguir y no tan landing page».
 *
 * Antes era una columna larga: el héroe, su nivel, la vista previa, las
 * rotaciones, la comprobación y el botón, todo a la vez y debajo de la
 * historia de la misión. Ahora son cinco pasos, uno a la vista:
 *
 *   1. Héroe          — quién sale
 *   2. Estadísticas   — con qué pelea en el nivel elegido
 *   3. Rotaciones     — cómo lo dirige la IA
 *   4. Comprobación   — las reglas del juego dicen si vale
 *   5. Confirmación   — el resumen, lo que queda bloqueado y «Iniciar misión»
 *
 * El configurador de estrategia es el mismo de siempre (RotationBuilder,
 * `constructor-estrategia.js`): el asistente no lo rehace, solo decide qué
 * parte enseña en cada paso (por CSS, con `data-paso`) y cuándo se puede
 * seguir. Nada de lo que valida lo valida aquí: la estrategia la comprueba el
 * servicio de héroes y la matrícula la decide el de misiones.
 *
 * @module contenido/misiones/asistente-mision
 */

import { h } from '../../comun/ui/dom.js';
import { icono } from '../../comun/ui/icono.js';
import { textoDeDuracion } from './modelo-misiones.js';

/** Los cinco pasos, en su orden. `corto` es el del indicador de pasos. */
export const PASOS_DE_PREPARACION = Object.freeze([
  {
    id: 'heroe',
    corto: 'Héroe',
    titulo: 'Elige tu héroe',
    detalle: 'Quién sale a la misión. Mientras dure, no podrá jugar en línea ni en torneos.',
  },
  {
    id: 'estadisticas',
    corto: 'Estadísticas',
    titulo: 'Revisa sus estadísticas',
    detalle: 'Con qué pelea en el nivel elegido: poder, vida, defensa, ataque y habilidades.',
  },
  {
    id: 'rotaciones',
    corto: 'Rotaciones',
    titulo: 'Ordena sus rotaciones',
    detalle: 'En la misión tu héroe pelea solo: la IA lo dirige con este orden.',
  },
  {
    id: 'comprobar',
    corto: 'Comprobación',
    titulo: 'Comprueba la estrategia',
    detalle: 'Las reglas del juego revisan cada paso antes de partir.',
  },
  {
    id: 'confirmar',
    corto: 'Confirmación',
    titulo: 'Confirma y parte',
    detalle: 'Esto es lo que sale de misión.',
  },
]);

/** Por qué no se puede seguir todavía, por paso. */
const MOTIVO_PARA_SEGUIR = Object.freeze({
  heroe: 'Elige un héroe para seguir.',
  comprobar: 'Comprueba la estrategia para seguir: tiene que ser válida.',
});

/**
 * El resumen que se confirma antes de mandar al héroe (RF-MIS-004: «presenta
 * el resumen de la misión, exige confirmación»), con lo que queda bloqueado.
 *
 * @param {{nombre: string, duracionHoras?: number}} mision
 * @param {{heroe: string, rotaciones: Array<{pasos: string[]}>}} envio
 * @returns {HTMLElement}
 */
export function resumenDeMatricula(mision, { heroe, rotaciones }) {
  const duracion = textoDeDuracion(mision.duracionHoras);
  return h('div', {
    clase: 'pila pila--compacta misiones-confirmacion',
    hijos: [
      h('dl', {
        clase: 'misiones-confirmacion__datos',
        hijos: [
          ['Misión', mision.nombre],
          ['Duración', duracion],
          ['Héroe', heroe],
          [
            'Estrategia',
            rotaciones.length > 0
              ? rotaciones.map((r, i) => `${i + 1}. ${r.pasos.join(' → ')}`).join(' · ')
              : 'Ataque básico',
          ],
        ]
          .filter(([, valor]) => valor)
          .map(([etiqueta, valor]) =>
            h('div', { hijos: [h('dt', { texto: etiqueta }), h('dd', { texto: valor })] }),
          ),
      }),
      h('p', {
        clase: 'misiones-confirmacion__advertencia',
        hijos: [
          icono('candado', { etiqueta: null }),
          h('span', {
            texto: `${heroe} queda bloqueado${duracion ? ` ${duracion}` : ''}: no podrá jugar en línea, entrar en torneos ni cambiar su equipamiento hasta que termine o la canceles.`,
          }),
        ],
      }),
    ],
  });
}

/**
 * El asistente.
 *
 * @param {object} opciones
 * @param {{nombre: string, duracionHoras?: number}} opciones.mision
 * @param {{elemento: HTMLElement, estrategia: () => object|null,
 *   heroe?: () => object|null}} opciones.configurador
 * @param {HTMLButtonElement} opciones.botonIniciar «Iniciar misión» (lo crea el detalle)
 * @param {HTMLElement} opciones.motivoInicio por qué no se puede todavía
 * @param {string} [opciones.hrefEquipamiento] para cambiar el equipo del héroe
 * @param {() => void} [opciones.alVerDetalles] vuelve a la historia de la misión
 * @returns {{elemento: HTMLElement, irA: (id: string, opciones?: {foco?: boolean}) => void,
 *   paso: () => string, actualizar: () => void}}
 */
export function asistenteDeMision({
  mision,
  configurador,
  botonIniciar,
  motivoInicio,
  hrefEquipamiento = null,
  alVerDetalles = () => {},
}) {
  let actual = 0;

  const marcadores = PASOS_DE_PREPARACION.map((paso, indice) =>
    h('li', {
      clase: 'mision-asistente__marca',
      datos: { paso: paso.id },
      hijos: [
        h('span', {
          clase: 'mision-asistente__numero',
          texto: String(indice + 1),
          atributos: { 'aria-hidden': 'true' },
        }),
        h('span', { clase: 'mision-asistente__etiqueta', texto: paso.corto }),
      ],
    }),
  );
  const indicador = h('ol', {
    clase: 'mision-asistente__pasos',
    atributos: { 'aria-label': 'Pasos para preparar la misión' },
    hijos: marcadores,
  });

  const contador = h('p', { clase: 'mision-asistente__contador' });
  const tituloPaso = h('h3', {
    clase: 'mision-asistente__titulo',
    atributos: { id: 'mision-asistente-titulo', tabindex: '-1' },
  });
  const detallePaso = h('p', { clase: 'mision-asistente__detalle' });

  const enlaceEquipo = hrefEquipamiento
    ? h('a', {
        clase: 'mision-asistente__equipo',
        texto: 'Cambiar su equipamiento en Mi inventario',
        atributos: { href: hrefEquipamiento },
        datos: { accion: 'cambiar-equipamiento' },
      })
    : null;

  const zonaResumen = h('div', {
    clase: 'mision-asistente__resumen',
    datos: { zona: 'resumen' },
  });
  const confirmar = h('div', {
    clase: 'tarjeta mision-asistente__confirmar',
    datos: { zona: 'confirmar' },
    hijos: [zonaResumen, botonIniciar, motivoInicio],
  });

  const motivoParaSeguir = h('p', {
    clase: 'mision-asistente__motivo',
    datos: { zona: 'motivo-seguir' },
    atributos: { id: 'mision-asistente-motivo', role: 'status' },
  });
  const anterior = h('button', {
    clase: 'boton boton--secundario',
    datos: { accion: 'paso-anterior' },
    atributos: { type: 'button' },
    hijos: [
      icono('chevron', { etiqueta: null, clase: 'icono mision-asistente__atras' }),
      h('span', { texto: 'Anterior' }),
    ],
  });
  const siguiente = h('button', {
    clase: 'boton boton--primario',
    datos: { accion: 'paso-siguiente' },
    atributos: { type: 'button', 'aria-describedby': 'mision-asistente-motivo' },
  });
  const navegacion = h('div', {
    clase: 'mision-asistente__navegacion',
    hijos: [anterior, motivoParaSeguir, siguiente],
  });

  const verDetalles = h('button', {
    clase: 'boton boton--contorno boton--pequeno',
    datos: { accion: 'ver-detalles' },
    atributos: { type: 'button' },
    hijos: [icono('mapa', { etiqueta: null }), h('span', { texto: 'Ver la misión' })],
  });
  verDetalles.addEventListener('click', () => alVerDetalles());

  const elemento = h('section', {
    clase: 'mision-asistente',
    datos: { paso: PASOS_DE_PREPARACION[0].id },
    atributos: { id: 'configurar', 'aria-labelledby': 'mision-seccion-configurar' },
    hijos: [
      h('div', {
        clase: 'mision-asistente__cabecera',
        hijos: [
          h('h2', {
            clase: 'mision-detalle__subtitulo mision-detalle__subtitulo--atmosfera',
            texto: 'Prepara la misión',
            atributos: { id: 'mision-seccion-configurar', tabindex: '-1' },
          }),
          verDetalles,
        ],
      }),
      indicador,
      h('div', {
        clase: 'tarjeta mision-asistente__paso',
        hijos: [
          h('div', {
            clase: 'mision-asistente__encabezado',
            hijos: [contador, tituloPaso, detallePaso, enlaceEquipo],
          }),
          configurador.elemento,
          confirmar,
          navegacion,
        ],
      }),
    ],
  });

  /** ¿Se puede pasar del paso en curso al siguiente? */
  function puedeSeguir() {
    const id = PASOS_DE_PREPARACION[actual].id;
    if (id === 'heroe') {
      return Boolean(configurador.heroe?.());
    }
    if (id === 'comprobar') {
      return (configurador.estrategia?.() ?? null) !== null;
    }
    return true;
  }

  function pintarResumen() {
    const envio = configurador.estrategia?.() ?? null;
    if (!envio) {
      zonaResumen.replaceChildren();
      return;
    }
    zonaResumen.replaceChildren(
      resumenDeMatricula(mision, {
        heroe: envio.heroeNombre ?? 'Tu héroe',
        rotaciones: envio.rotaciones ?? [],
      }),
    );
  }

  /** Hecho, el de ahora o pendiente: lo que dice cada marca del indicador. */
  function estadoDeLaMarca(indice) {
    if (indice < actual) {
      return 'hecho';
    }
    return indice === actual ? 'actual' : 'pendiente';
  }

  /** Repinta el estado de los pasos y de los botones; no mueve el foco. */
  function actualizar() {
    const paso = PASOS_DE_PREPARACION[actual];
    elemento.dataset.paso = paso.id;
    marcadores.forEach((marca, indice) => {
      marca.dataset.estado = estadoDeLaMarca(indice);
      if (indice === actual) {
        marca.setAttribute('aria-current', 'step');
      } else {
        marca.removeAttribute('aria-current');
      }
    });
    contador.textContent = `Paso ${actual + 1} de ${PASOS_DE_PREPARACION.length}`;
    tituloPaso.textContent = paso.titulo;
    detallePaso.textContent = paso.detalle;
    if (enlaceEquipo) {
      enlaceEquipo.hidden = paso.id !== 'estadisticas';
    }

    anterior.hidden = actual === 0;
    const ultimo = actual === PASOS_DE_PREPARACION.length - 1;
    siguiente.hidden = ultimo;
    if (!ultimo) {
      const proximo = PASOS_DE_PREPARACION[actual + 1];
      siguiente.replaceChildren(
        h('span', { texto: `Siguiente: ${proximo.corto.toLowerCase()}` }),
        icono('chevron', { etiqueta: null }),
      );
      const listo = puedeSeguir();
      siguiente.setAttribute('aria-disabled', String(!listo));
      motivoParaSeguir.textContent = listo ? '' : (MOTIVO_PARA_SEGUIR[paso.id] ?? '');
    } else {
      motivoParaSeguir.textContent = '';
      pintarResumen();
    }
  }

  /**
   * Va a un paso. Con `foco`, el foco va al título del paso (quien navega con
   * teclado o lector de pantalla oye dónde está).
   *
   * @param {string} id
   * @param {{foco?: boolean}} [opciones]
   */
  function irA(id, { foco = true } = {}) {
    const indice = PASOS_DE_PREPARACION.findIndex((paso) => paso.id === id);
    if (indice < 0) {
      return;
    }
    actual = indice;
    actualizar();
    if (foco) {
      tituloPaso.focus();
    }
  }

  siguiente.addEventListener('click', () => {
    if (!puedeSeguir()) {
      // `aria-disabled` y no `disabled`: sigue en el orden del teclado y dice
      // por qué no se puede todavía.
      actualizar();
      return;
    }
    irA(PASOS_DE_PREPARACION[actual + 1].id);
  });
  anterior.addEventListener('click', () => {
    if (actual > 0) {
      irA(PASOS_DE_PREPARACION[actual - 1].id);
    }
  });

  actualizar();

  return {
    elemento,
    irA,
    paso: () => PASOS_DE_PREPARACION[actual].id,
    actualizar,
  };
}
