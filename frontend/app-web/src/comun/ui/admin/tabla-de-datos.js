/**
 * La tabla de la consola de operación — SA-2.
 *
 * ## El defecto que la hizo necesaria
 *
 * Las tablas de la consola se dibujaban con el patrón de filas del kit
 * (`.tabla__fila`, que es `display: flex`) colgado de un `<table>`. Una fila
 * flexible deja de ser una fila de tabla: cada celda medía lo que su texto y
 * las columnas no casaban con sus encabezados («Elemento · Precio actual ·
 * Pujas · Cierra» arriba y los valores corridos debajo). Lo vio el informe de
 * revisión en subastas, torneos, bitácora, sanciones, parámetros y servicios.
 *
 * ## Lo que garantiza
 *
 *   - una `<table>` de verdad, con `<caption>`, `<th scope="col">` y la celda
 *     que identifica la fila como `<th scope="row">`;
 *   - columnas alineadas por tipo: el texto a la izquierda, las cifras a la
 *     derecha con números tabulares, las acciones a la derecha;
 *   - orden por columna con un botón en el encabezado y `aria-sort`, local o
 *     pedido al servidor (`alOrdenar`), y un selector de orden en el teléfono;
 *   - en un teléfono cada fila se apila como una ficha con la etiqueta de cada
 *     dato (los roles ARIA se escriben explícitos para que la tabla siga
 *     siendo una tabla para el lector de pantalla aunque el CSS la apile);
 *   - un dato que falta se dice («Sin dato») y nunca se rellena con un cero;
 *   - sin filas, un estado vacío que dice qué falta.
 *
 * Todo con `textContent`: las celdas llevan apodos, motivos y nombres que
 * escribió gente de fuera.
 *
 * @module comun/ui/admin/tabla-de-datos
 */

import { h } from '../dom.js';
import { estadoVacio } from '../estado-vista.js';

/** Lo que se lee donde la fuente no trae el dato. */
export const SIN_DATO = 'Sin dato';

/** Tipos de columna: deciden la alineación y el orden por omisión. */
export const TIPOS_DE_COLUMNA = Object.freeze(['texto', 'numero', 'fecha', 'estado', 'accion']);

let contador = 0;

/**
 * @typedef {object} Columna
 * @property {string} clave nombre del dato en la fila
 * @property {string} titulo encabezado visible
 * @property {'texto'|'numero'|'fecha'|'estado'|'accion'} [tipo]
 * @property {boolean} [ordenable] si se puede ordenar por ella
 * @property {boolean} [principal] la celda que identifica la fila (`<th scope="row">`)
 * @property {(fila: object) => (string|number|Node|Array<string|Node>|null|undefined)} [valor]
 *   lo que se pinta; por omisión, `fila[clave]`
 * @property {(fila: object) => (string|number|null|undefined)} [ordenarPor]
 *   con qué se compara al ordenar; por omisión, el dato crudo
 */

/**
 * @typedef {{clave: string, direccion: 'asc'|'desc'}} Orden
 */

/**
 * Monta la tabla.
 *
 * @param {object} opciones
 * @param {Columna[]} opciones.columnas
 * @param {object[]} opciones.filas
 * @param {string} opciones.titulo nombre accesible (el `<caption>`)
 * @param {boolean} [opciones.leyendaVisible] enseña el `<caption>`; por omisión
 *   lo lee el lector de pantalla y el título visible es el del panel
 * @param {string|null} [opciones.resumen] la línea de recuento («6 de 6»)
 * @param {Orden|null} [opciones.orden] el orden vigente
 * @param {((orden: Orden) => void)|null} [opciones.alOrdenar] si el orden lo
 *   aplica el servidor; sin él, ordena la propia tabla
 * @param {{titulo: string, detalle?: string|null}|null} [opciones.vacio]
 * @param {((fila: object) => string)|null} [opciones.claveDeFila] `data-fila`
 * @returns {HTMLElement}
 */
export function tablaDeDatos({
  columnas,
  filas,
  titulo,
  leyendaVisible = false,
  resumen = null,
  orden = null,
  alOrdenar = null,
  vacio = null,
  claveDeFila = null,
}) {
  if (!Array.isArray(columnas) || columnas.length === 0) {
    throw new TypeError('tablaDeDatos: hace falta al menos una columna');
  }
  if (!titulo) {
    throw new TypeError('tablaDeDatos: la tabla necesita un título (su nombre accesible)');
  }
  const lista = Array.isArray(filas) ? filas : [];
  contador += 1;
  const id = `tabla-admin-${contador}`;

  const raiz = h('div', { clase: 'tabla-admin', datos: { zona: 'tabla' } });
  const anuncio = h('p', {
    clase: 'solo-lectores',
    atributos: { role: 'status', 'aria-live': 'polite' },
  });

  const ordenables = columnas.filter((c) => c.ordenable && tipoDe(c) !== 'accion');
  let ordenVigente = orden && ordenables.some((c) => c.clave === orden.clave) ? orden : null;

  if (resumen || ordenables.length > 0) {
    const cabecera = h('div', { clase: 'tabla-admin__cabecera' });
    if (resumen) {
      cabecera.append(h('p', { clase: 'tabla-admin__resumen', texto: resumen }));
    }
    if (ordenables.length > 0 && lista.length > 1) {
      cabecera.append(selectorDeOrden(ordenables, () => ordenVigente, ordenar, id));
    }
    raiz.append(cabecera);
  }

  if (lista.length === 0) {
    raiz.append(
      estadoVacio({
        titulo: vacio?.titulo ?? 'Sin registros',
        detalle: vacio?.detalle ?? null,
      }),
    );
    return raiz;
  }

  const tabla = h('table', {
    clase: 'tabla-admin__tabla',
    atributos: { id, role: 'table' },
  });
  tabla.append(
    h('caption', {
      clase: leyendaVisible ? 'tabla-admin__leyenda' : 'solo-lectores',
      texto: titulo,
      atributos: { id: `${id}-nombre` },
    }),
  );

  const filaDeEncabezados = h('tr', { atributos: { role: 'row' } });
  const encabezados = new Map();
  for (const columna of columnas) {
    const th = h('th', {
      atributos: { scope: 'col', role: 'columnheader' },
      datos: { tipo: tipoDe(columna), columna: columna.clave },
    });
    if (columna.ordenable && tipoDe(columna) !== 'accion') {
      const boton = h('button', {
        clase: 'tabla-admin__ordenar',
        atributos: { type: 'button' },
        datos: { orden: columna.clave },
        hijos: [
          h('span', { texto: columna.titulo }),
          h('span', { clase: 'tabla-admin__flecha', atributos: { 'aria-hidden': 'true' } }),
        ],
      });
      boton.addEventListener('click', () => {
        const direccion =
          ordenVigente?.clave === columna.clave && ordenVigente.direccion === 'asc'
            ? 'desc'
            : 'asc';
        ordenar({ clave: columna.clave, direccion });
      });
      th.append(boton);
    } else if (tipoDe(columna) === 'accion' && !columna.titulo) {
      th.append(h('span', { clase: 'solo-lectores', texto: 'Acciones' }));
    } else {
      th.textContent = columna.titulo;
    }
    encabezados.set(columna.clave, th);
    filaDeEncabezados.append(th);
  }
  tabla.append(h('thead', { atributos: { role: 'rowgroup' }, hijos: [filaDeEncabezados] }));

  const cuerpo = h('tbody', { atributos: { role: 'rowgroup' } });
  tabla.append(cuerpo);

  const marco = h('div', {
    clase: 'tabla-admin__marco',
    // Si la tabla no cabe, se desplaza aquí dentro y no la página: el marco
    // entra en el orden del teclado para poder desplazarlo con las flechas.
    atributos: { tabindex: '0', role: 'region', 'aria-labelledby': `${id}-nombre` },
  });
  marco.append(tabla);
  raiz.append(marco, anuncio);
  vigilarDesborde(marco);

  function pintarFilas() {
    const ordenadas =
      alOrdenar || !ordenVigente ? lista : ordenarLocal(lista, columnas, ordenVigente);
    cuerpo.replaceChildren(...ordenadas.map((fila) => filaDeDatos(fila, columnas, claveDeFila)));
    for (const [clave, th] of encabezados) {
      if (ordenVigente?.clave === clave) {
        th.setAttribute('aria-sort', ordenVigente.direccion === 'asc' ? 'ascending' : 'descending');
      } else if (th.querySelector('.tabla-admin__ordenar')) {
        th.setAttribute('aria-sort', 'none');
      }
    }
  }

  /** @param {Orden} nuevo */
  function ordenar(nuevo) {
    ordenVigente = nuevo;
    const columna = columnas.find((c) => c.clave === nuevo.clave);
    anuncio.textContent = `Ordenado por ${columna?.titulo ?? nuevo.clave}, ${
      nuevo.direccion === 'asc' ? 'de menor a mayor' : 'de mayor a menor'
    }.`;
    const selector = raiz.querySelector('[data-zona="orden-movil"]');
    if (selector) {
      selector.value = `${nuevo.clave}:${nuevo.direccion}`;
    }
    if (alOrdenar) {
      alOrdenar(nuevo);
      return;
    }
    pintarFilas();
  }

  pintarFilas();
  return raiz;
}

/**
 * El orden en un teléfono, donde los encabezados no se ven: un desplegable
 * con cada columna ordenable en los dos sentidos.
 */
function selectorDeOrden(ordenables, vigente, ordenar, id) {
  const control = h('select', {
    clase: 'desplegable__control',
    atributos: { id: `${id}-orden`, name: 'orden' },
    datos: { zona: 'orden-movil' },
    hijos: [
      h('option', { texto: 'Sin ordenar', atributos: { value: '' } }),
      ...ordenables.flatMap((c) => [
        h('option', {
          texto: `${c.titulo} (menor a mayor)`,
          atributos: { value: `${c.clave}:asc` },
        }),
        h('option', {
          texto: `${c.titulo} (mayor a menor)`,
          atributos: { value: `${c.clave}:desc` },
        }),
      ]),
    ],
  });
  const actual = vigente();
  control.value = actual ? `${actual.clave}:${actual.direccion}` : '';
  control.addEventListener('change', () => {
    const [clave, direccion] = control.value.split(':');
    if (clave) {
      ordenar({ clave, direccion: direccion === 'desc' ? 'desc' : 'asc' });
    }
  });
  return h('label', {
    clase: 'desplegable tabla-admin__orden-movil',
    atributos: { for: `${id}-orden` },
    hijos: [h('span', { clase: 'campo__etiqueta', texto: 'Ordenar por' }), control],
  });
}

/** @param {Columna} columna */
function tipoDe(columna) {
  return TIPOS_DE_COLUMNA.includes(columna.tipo) ? columna.tipo : 'texto';
}

/**
 * @param {object} fila
 * @param {Columna[]} columnas
 * @param {((fila: object) => string)|null} claveDeFila
 */
function filaDeDatos(fila, columnas, claveDeFila) {
  const tr = h('tr', {
    atributos: { role: 'row' },
    datos: claveDeFila ? { fila: claveDeFila(fila) } : {},
  });
  for (const columna of columnas) {
    const tipo = tipoDe(columna);
    const principal = Boolean(columna.principal);
    const celda = h(principal ? 'th' : 'td', {
      clase: principal ? 'tabla-admin__principal' : null,
      atributos: principal ? { scope: 'row', role: 'rowheader' } : { role: 'cell' },
      datos: {
        tipo,
        columna: columna.clave,
        // En el teléfono, cada dato lleva su etiqueta delante (CSS).
        ...(tipo === 'accion' || principal ? {} : { etiqueta: columna.titulo }),
      },
    });
    const valor = typeof columna.valor === 'function' ? columna.valor(fila) : fila?.[columna.clave];
    pintarCelda(celda, valor, tipo);
    tr.append(celda);
  }
  return tr;
}

/**
 * @param {HTMLElement} celda
 * @param {*} valor
 * @param {string} tipo
 */
function pintarCelda(celda, valor, tipo) {
  if (valor === null || valor === undefined || valor === '') {
    if (tipo === 'accion') {
      return;
    }
    celda.append(h('span', { clase: 'tabla-admin__sin-dato', texto: SIN_DATO }));
    return;
  }
  const piezas = Array.isArray(valor) ? valor : [valor];
  const destino = tipo === 'accion' ? h('div', { clase: 'tabla-admin__acciones' }) : celda;
  for (const pieza of piezas) {
    if (pieza === null || pieza === undefined || pieza === false) {
      continue;
    }
    destino.append(pieza instanceof globalThis.Node ? pieza : String(pieza));
  }
  if (destino !== celda) {
    celda.append(destino);
  }
}

/**
 * Dice en el marco hacia qué lado sigue la tabla cuando no cabe
 * (`data-desborde="inicio fin"`): con eso el CSS sombrea el borde de la
 * columna fija que tapa lo que se desplaza. Sin desbordar no hay atributo.
 *
 * @param {HTMLElement} marco
 */
export function vigilarDesborde(marco) {
  const medir = () => {
    const { scrollLeft, scrollWidth, clientWidth } = marco;
    const lados = [];
    if (scrollLeft > 1) {
      lados.push('inicio');
    }
    if (scrollLeft + clientWidth < scrollWidth - 1) {
      lados.push('fin');
    }
    if (lados.length > 0) {
      marco.dataset.desborde = lados.join(' ');
    } else {
      delete marco.dataset.desborde;
    }
  };
  marco.addEventListener('scroll', medir, { passive: true });
  // Al pintarse y cada vez que cambia de ancho (girar la tableta, abrir el
  // asistente). Donde no hay ResizeObserver, basta con el desplazamiento.
  if (typeof ResizeObserver === 'function') {
    new ResizeObserver(medir).observe(marco);
  }
  return medir;
}

/**
 * Orden local, estable: a igualdad conserva el orden en que llegaron.
 *
 * @param {object[]} filas
 * @param {Columna[]} columnas
 * @param {Orden} orden
 */
export function ordenarLocal(filas, columnas, orden) {
  const columna = columnas.find((c) => c.clave === orden.clave);
  if (!columna) {
    return filas;
  }
  const clave = (fila) => claveDeOrden(fila, columna);
  const sentido = orden.direccion === 'desc' ? -1 : 1;
  return filas
    .map((fila, indice) => ({ fila, indice, valor: clave(fila) }))
    .sort((a, b) => {
      // Lo que no tiene dato va siempre al final, ordene como ordene.
      const aVacio = a.valor === null || a.valor === undefined || a.valor === '';
      const bVacio = b.valor === null || b.valor === undefined || b.valor === '';
      if (aVacio && bVacio) {
        return a.indice - b.indice;
      }
      if (aVacio || bVacio) {
        return aVacio ? 1 : -1;
      }
      const comparacion =
        typeof a.valor === 'number' && typeof b.valor === 'number'
          ? a.valor - b.valor
          : String(a.valor).localeCompare(String(b.valor), 'es', {
              sensitivity: 'base',
              numeric: true,
            });
      return comparacion === 0 ? a.indice - b.indice : comparacion * sentido;
    })
    .map(({ fila }) => fila);
}

/** @param {object} fila @param {Columna} columna */
function claveDeOrden(fila, columna) {
  if (typeof columna.ordenarPor === 'function') {
    return columna.ordenarPor(fila);
  }
  const crudo = fila?.[columna.clave];
  if (crudo === null || crudo === undefined || crudo === '') {
    return null;
  }
  if (tipoDe(columna) === 'numero') {
    const cifra = Number(crudo);
    return Number.isFinite(cifra) ? cifra : null;
  }
  if (tipoDe(columna) === 'fecha') {
    const instante = new Date(crudo).getTime();
    return Number.isFinite(instante) ? instante : null;
  }
  return typeof crudo === 'object' ? null : crudo;
}
