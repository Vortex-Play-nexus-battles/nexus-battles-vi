/**
 * La barra de filtros de la consola de operación — SA-2.
 *
 * El informe de revisión lo pidió en el directorio, en la gestión de usuarios
 * y en sanciones: la búsqueda iba detrás de los filtros, cada pantalla los
 * ordenaba a su manera y no había forma de ver de un vistazo qué estaba
 * filtrando ni de quitarlo. Aquí el orden es siempre el mismo:
 *
 *   1. la búsqueda, primero y la más ancha, con su botón;
 *   2. los filtros, cada uno con su etiqueta visible, en el orden que se den;
 *   3. «Limpiar filtros», solo cuando hay alguno puesto;
 *   4. lo que está aplicado, como fichas que se quitan de una en una.
 *
 * Un desplegable o una fecha se aplican al cambiar; la búsqueda, al pulsar
 * «Buscar» o Enter, para no consultar a cada tecla.
 *
 * @module comun/ui/admin/barra-de-filtros
 */

import { h } from '../dom.js';

let contador = 0;

/**
 * @typedef {object} Filtro
 * @property {string} nombre clave del valor
 * @property {string} etiqueta lo que se lee encima del control
 * @property {'opciones'|'fecha'|'texto'} [tipo]
 * @property {Array<[string, string]>} [opciones] pares `[valor, texto]`
 * @property {string} [todos] el texto de «sin filtrar» de un desplegable
 */

/**
 * @param {object} opciones
 * @param {{nombre?: string, etiqueta: string, placeholder?: string}|null} [opciones.busqueda]
 * @param {Filtro[]} [opciones.filtros]
 * @param {Record<string, string>} [opciones.valores] los valores de partida
 * @param {(valores: Record<string, string>) => void} opciones.alAplicar
 * @param {(valores: Record<string, string>) => (string|null)} [opciones.validar]
 *   devuelve el motivo por el que no se puede aplicar, o null
 * @param {string} [opciones.etiqueta] nombre accesible del formulario
 * @returns {{elemento: HTMLFormElement, leer: () => Record<string, string>,
 *   fijar: (valores: Record<string, string>) => void, limpiar: () => void}}
 */
export function barraDeFiltros({
  busqueda = null,
  filtros = [],
  valores = {},
  alAplicar,
  validar = null,
  etiqueta = 'Buscar y filtrar',
}) {
  if (typeof alAplicar !== 'function') {
    throw new TypeError('barraDeFiltros: falta alAplicar');
  }
  contador += 1;
  const id = `filtros-admin-${contador}`;
  const formulario = h('form', {
    clase: 'filtros-admin',
    atributos: { role: 'search', 'aria-label': etiqueta, novalidate: true },
    datos: { zona: 'filtros' },
  });

  /** @type {Map<string, {control: HTMLInputElement|HTMLSelectElement, filtro: Filtro|null}>} */
  const controles = new Map();

  if (busqueda) {
    const nombre = busqueda.nombre ?? 'buscar';
    const entrada = h('input', {
      clase: 'campo__control filtros-admin__entrada',
      atributos: {
        id: `${id}-${nombre}`,
        type: 'search',
        name: nombre,
        placeholder: busqueda.placeholder ?? '',
        autocomplete: 'off',
        enterkeyhint: 'search',
      },
    });
    entrada.value = valores[nombre] ?? '';
    const boton = h('button', {
      clase: 'boton boton--primario',
      texto: 'Buscar',
      atributos: { type: 'submit' },
      datos: { accion: 'buscar' },
    });
    formulario.append(
      h('div', {
        clase: 'filtros-admin__busqueda',
        hijos: [
          h('label', {
            clase: 'campo filtros-admin__campo-busqueda',
            atributos: { for: `${id}-${nombre}` },
            hijos: [h('span', { clase: 'campo__etiqueta', texto: busqueda.etiqueta }), entrada],
          }),
          boton,
        ],
      }),
    );
    controles.set(nombre, { control: entrada, filtro: null, etiqueta: busqueda.etiqueta });
  }

  const campos = h('div', { clase: 'filtros-admin__campos' });
  for (const filtro of filtros) {
    const control = controlDe(filtro, `${id}-${filtro.nombre}`);
    control.value = valores[filtro.nombre] ?? '';
    control.addEventListener('change', () => aplicar());
    const esFecha = filtro.tipo === 'fecha' || filtro.tipo === 'texto';
    campos.append(
      h('label', {
        clase: esFecha ? 'campo' : 'desplegable',
        atributos: { for: `${id}-${filtro.nombre}` },
        hijos: [h('span', { clase: 'campo__etiqueta', texto: filtro.etiqueta }), control],
      }),
    );
    controles.set(filtro.nombre, { control, filtro, etiqueta: filtro.etiqueta });
  }

  const limpiarBoton = h('button', {
    clase: 'boton boton--contorno filtros-admin__limpiar',
    texto: 'Limpiar filtros',
    atributos: { type: 'button' },
    datos: { accion: 'limpiar-filtros' },
  });
  limpiarBoton.addEventListener('click', () => limpiar());
  if (filtros.length > 0) {
    campos.append(h('div', { clase: 'filtros-admin__acciones', hijos: [limpiarBoton] }));
    formulario.append(campos);
  }

  const error = h('p', {
    clase: 'filtros-admin__error',
    atributos: { role: 'alert' },
    datos: { zona: 'error-filtros' },
  });
  error.hidden = true;
  const activos = h('div', {
    clase: 'filtros-admin__activos',
    datos: { zona: 'filtros-activos' },
    atributos: { 'aria-live': 'polite' },
  });
  formulario.append(error, activos);

  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    aplicar();
  });

  function leer() {
    const salida = {};
    for (const [nombre, { control }] of controles) {
      salida[nombre] = String(control.value ?? '').trim();
    }
    return salida;
  }

  function fijar(nuevos) {
    for (const [nombre, { control }] of controles) {
      if (Object.prototype.hasOwnProperty.call(nuevos, nombre)) {
        control.value = nuevos[nombre] ?? '';
      }
    }
    pintarActivos();
  }

  function limpiar() {
    for (const { control } of controles.values()) {
      control.value = '';
    }
    aplicar();
  }

  function aplicar() {
    const leidos = leer();
    const motivo = typeof validar === 'function' ? validar(leidos) : null;
    if (motivo) {
      error.textContent = motivo;
      error.hidden = false;
      return;
    }
    error.textContent = '';
    error.hidden = true;
    pintarActivos();
    alAplicar(leidos);
  }

  /** Las fichas de lo aplicado: «Rol: Moderador ✕». */
  function pintarActivos() {
    const fichas = [];
    for (const [nombre, { control, filtro, etiqueta: rotulo }] of controles) {
      const valor = String(control.value ?? '').trim();
      if (!valor) {
        continue;
      }
      const texto = `${rotulo}: ${textoDelValor(filtro, valor)}`;
      const ficha = h('button', {
        clase: 'filtros-admin__ficha',
        atributos: { type: 'button', 'aria-label': `Quitar el filtro ${texto}` },
        datos: { quitar: nombre },
        hijos: [
          h('span', { texto }),
          h('span', {
            clase: 'filtros-admin__quitar',
            texto: '×',
            atributos: { 'aria-hidden': 'true' },
          }),
        ],
      });
      ficha.addEventListener('click', () => {
        control.value = '';
        aplicar();
        // El foco no se pierde con la ficha: vuelve al control que se vació.
        control.focus();
      });
      fichas.push(ficha);
    }
    activos.replaceChildren(...fichas);
    activos.hidden = fichas.length === 0;
    limpiarBoton.hidden = fichas.length === 0;
  }

  pintarActivos();
  return { elemento: formulario, leer, fijar, limpiar };
}

/**
 * @param {Filtro} filtro
 * @param {string} id
 */
function controlDe(filtro, id) {
  if (filtro.tipo === 'fecha' || filtro.tipo === 'texto') {
    return h('input', {
      clase: 'campo__control',
      atributos: { id, type: filtro.tipo === 'fecha' ? 'date' : 'text', name: filtro.nombre },
    });
  }
  return h('select', {
    clase: 'desplegable__control',
    atributos: { id, name: filtro.nombre },
    hijos: [
      h('option', { texto: filtro.todos ?? 'Todos', atributos: { value: '' } }),
      ...(filtro.opciones ?? []).map(([valor, texto]) =>
        h('option', { texto, atributos: { value: valor } }),
      ),
    ],
  });
}

/** El texto con que se lee un valor aplicado: el de su opción, o la fecha legible. */
function textoDelValor(filtro, valor) {
  if (filtro?.opciones) {
    const opcion = filtro.opciones.find(([v]) => v === valor);
    if (opcion) {
      return opcion[1];
    }
  }
  if (filtro?.tipo === 'fecha') {
    const [anio, mes, dia] = valor.split('-');
    if (anio && mes && dia) {
      return `${dia}/${mes}/${anio}`;
    }
  }
  return valor;
}
