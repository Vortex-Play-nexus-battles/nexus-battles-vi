/**
 * UXC-7 — El catálogo para administrarlo: la lista con sus filtros y la ficha
 * de gestión de cada producto (ProductAdminSheet).
 *
 * Hasta aquí la consola podía crear productos y contar cuántos había por tipo
 * y estado, pero no ver cuáles eran ni tocar ninguno: modificar (HU-PRD-003),
 * suspender y reactivar existían en el servicio sin una sola pantalla que los
 * usara. Todo sale de `contracts/openapi/productos.yaml`:
 *
 *   - `GET  /api/v1/productos?page&size&tipo&estado` — la lista, 16 por página
 *     (RNF-USA-001). Sin `estado` salen activos y únicos; los suspendidos solo
 *     se ven pidiéndolos, y el filtro lo hace.
 *   - `PATCH /api/v1/productos/{id}` — solo los campos que cambian; el tipo no
 *     se modifica. El servidor valida el resultado fusionado: su motivo se
 *     enseña tal cual si lo rechaza.
 *   - `PUT /api/v1/productos/{id}/suspender|reactivar` — con confirmación.
 *
 * Aquí no se decide nada del catálogo: la validación de cada campo es la
 * misma del alta (`solicitud-producto.js`) y la última palabra es del servidor.
 *
 * @module contenido/productos/catalogo-admin
 */

import { h } from '../../comun/ui/dom.js';
import { abrirDialogo, confirmar } from '../../comun/ui/dialogo.js';
import { campo } from '../../comun/ui/campo.js';
import { distintivo } from '../../comun/ui/distintivo.js';
import { limpiarAviso, pintarAviso, tonoPorEstado } from '../../comun/ui/aviso.js';
import { conCarga } from '../../comun/ui/boton.js';
import { construirPaginacion } from '../../comun/paginacion.js';
import { NOMBRE_DEL_TIPO, creditos, fechaHora } from '../../comun/ui/formato.js';
import {
  estadoDeCarga,
  estadoDeError,
  estadoVacio,
  pintarEstado,
} from '../../comun/ui/estado-vista.js';
import { cambiarDisponibilidad, listarProductos, modificarProducto } from './cliente-productos.js';
import {
  construirSolicitudProducto,
  PARTES_ARMADURA,
  PROTOTIPOS,
  TIPOS_PRODUCTO,
} from './solicitud-producto.js';

/** RNF-USA-001: dieciséis por página. */
export const PRODUCTOS_POR_PAGINA = 16;

export const ETIQUETA_DE_TIPO = NOMBRE_DEL_TIPO;

export const ETIQUETA_DE_ESTADO = Object.freeze({
  ACTIVO: 'Activo',
  UNICO: 'Único',
  SUSPENDIDO: 'Suspendido',
});

const ETIQUETA_DE_PARTE = Object.freeze({
  CASCO: 'Casco',
  PECHO: 'Pecho',
  GUANTES: 'Guantes',
  BRAZALETES: 'Brazaletes',
  PANTALON: 'Pantalón',
  ZAPATOS: 'Zapatos',
});

/**
 * Lo que se puede editar de cada tipo, con las reglas del contrato. Los
 * comunes van primero; el tipo no está porque no se modifica.
 */
const CAMPOS_COMUNES = [
  { nombre: 'nombre', etiqueta: 'Nombre', atributos: { maxlength: 120 } },
  { nombre: 'descripcion', etiqueta: 'Descripción', multilinea: true },
  {
    nombre: 'imagen',
    etiqueta: 'Referencia de imagen',
    pista: 'La ruta o referencia con la que se guardó la imagen.',
  },
  {
    nombre: 'tiraje',
    etiqueta: 'Tiraje',
    tipo: 'number',
    pista: '-1 es ilimitado; si no, el total de unidades.',
    atributos: { step: 1, min: -1 },
  },
];

const CAMPOS_DE_PRECIO = {
  precioCreditos: {
    nombre: 'precioCreditos',
    etiqueta: 'Precio en créditos',
    tipo: 'number',
    atributos: { step: 1, min: 0 },
  },
  precioMonedaReal: {
    nombre: 'precioMonedaReal',
    etiqueta: 'Precio en moneda real',
    tipo: 'number',
    atributos: { step: '0.01', min: 0 },
  },
};

const TASA_DE_CAIDA = {
  nombre: 'tasaDeCaida',
  etiqueta: 'Tasa de caída (%)',
  tipo: 'number',
  atributos: { step: '0.01', min: 0, max: 100 },
};

const HEROE_ASOCIADO = {
  nombre: 'heroe',
  etiqueta: 'Héroe asociado',
  pista: 'El identificador del producto de tipo héroe.',
};

export const CAMPOS_POR_TIPO = Object.freeze({
  HEROE: [
    {
      nombre: 'prototipo',
      etiqueta: 'Prototipo',
      opciones: PROTOTIPOS.map((p) => ({ valor: p, texto: p })),
    },
  ],
  HABILIDAD: [
    HEROE_ASOCIADO,
    {
      nombre: 'costoPoder',
      etiqueta: 'Costo de poder',
      tipo: 'number',
      atributos: { step: 1, min: 1 },
    },
    {
      nombre: 'multiplicadorNivel',
      etiqueta: 'Multiplicador de nivel',
      tipo: 'number',
      atributos: { step: '0.01', min: '0.01' },
    },
    {
      nombre: 'turnosCarga',
      etiqueta: 'Turnos de carga',
      tipo: 'number',
      atributos: { step: 1, min: 0 },
    },
  ],
  ARMA: [
    {
      nombre: 'poderDeAtaque',
      etiqueta: 'Poder de ataque',
      tipo: 'number',
      atributos: { step: 1, min: 1 },
    },
    TASA_DE_CAIDA,
  ],
  ARMADURA: [
    { nombre: 'defensa', etiqueta: 'Defensa', tipo: 'number', atributos: { step: 1, min: 1 } },
    {
      nombre: 'parte',
      etiqueta: 'Parte',
      opciones: PARTES_ARMADURA.map((p) => ({ valor: p, texto: ETIQUETA_DE_PARTE[p] ?? p })),
    },
    TASA_DE_CAIDA,
  ],
  ITEM: [{ nombre: 'efecto', etiqueta: 'Efecto', multilinea: true }, TASA_DE_CAIDA],
  EPICA: [
    HEROE_ASOCIADO,
    {
      nombre: 'turnosRecarga',
      etiqueta: 'Turnos de recarga',
      tipo: 'number',
      atributos: { step: 1, min: 0 },
    },
    { nombre: 'efectoGeneral', etiqueta: 'Efecto general', multilinea: true },
    { nombre: 'efectoPotenciado', etiqueta: 'Efecto potenciado', multilinea: true },
  ],
});

/**
 * @param {number|null|undefined} tiraje
 * @returns {string}
 */
export function textoDeTiraje(tiraje) {
  if (tiraje === -1) {
    return 'Ilimitado';
  }
  const n = Number(tiraje);
  if (!Number.isFinite(n)) {
    return '—';
  }
  return n === 1 ? '1 unidad' : `${n} unidades`;
}

/**
 * @param {object} producto
 * @returns {string}
 */
export function textoDePrecio(producto) {
  if (producto.premium) {
    const valor = Number(producto.precioMonedaReal);
    return Number.isFinite(valor)
      ? `${valor.toLocaleString('es-CO', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} en moneda real`
      : '—';
  }
  return `${creditos(producto.precioCreditos)} créditos`;
}

/**
 * Solo lo que cambió: el cuerpo de un PATCH (`minProperties: 1`).
 *
 * @param {object} original el producto tal como vino del servidor
 * @param {object} nueva la solicitud completa que arma el formulario
 * @returns {object} los campos distintos, sin `tipo`
 */
export function cambiosDe(original, nueva) {
  const cambios = {};
  for (const [clave, valor] of Object.entries(nueva)) {
    if (clave === 'tipo') {
      continue;
    }
    if (original[clave] !== valor) {
      cambios[clave] = valor;
    }
  }
  return cambios;
}

/**
 * Por qué no se pudo, dicho para actuar.
 *
 * @param {Error & {status?: number}} error
 * @param {string} queSeHacia
 */
function avisoDeFallo(error, queSeHacia) {
  const estado = error?.status ?? 0;
  if (estado === 401) {
    return {
      tono: 'advertencia',
      titulo: 'Tu sesión caducó',
      detalle: 'Vuelve a iniciar sesión para gestionar el catálogo.',
    };
  }
  if (estado === 403) {
    return {
      tono: 'advertencia',
      titulo: 'Tu rol no puede hacer esto',
      detalle: 'Gestionar productos es de administración.',
    };
  }
  if (estado === 404) {
    return {
      tono: 'advertencia',
      titulo: 'Este producto ya no existe',
      detalle: 'Cierra la ficha y vuelve a cargar la lista.',
    };
  }
  if (estado === 400) {
    return {
      tono: 'advertencia',
      titulo: `No se pudo ${queSeHacia}`,
      detalle: error.message,
    };
  }
  return {
    tono: tonoPorEstado(estado),
    titulo: `No se pudo ${queSeHacia}`,
    detalle: 'El servicio de productos no respondió. Vuelve a intentarlo en un momento.',
  };
}

/** Distintivo del estado, con palabra (no solo color). */
export function distintivoDeEstado(estado) {
  return distintivo(
    ETIQUETA_DE_ESTADO[estado] ?? estado,
    estado === 'SUSPENDIDO' ? 'suspendido' : 'activo',
  );
}

/**
 * El formulario de edición de un producto (dentro de la ficha).
 *
 * @param {object} producto
 * @returns {{formulario: HTMLFormElement, marcarErrores: (motivo: string|null) => void}}
 */
function formularioDeEdicion(producto) {
  const campos = [];
  const crear = (definicion) => {
    const creado = campo({
      ...definicion,
      valor: producto[definicion.nombre] === undefined ? '' : String(producto[definicion.nombre]),
      requerido: true,
    });
    campos.push(creado);
    return creado.elemento;
  };

  const casillaPremium = h('input', {
    clase: 'casilla__entrada',
    atributos: { type: 'checkbox', name: 'premium' },
  });
  casillaPremium.checked = Boolean(producto.premium);
  const precioCreditos = crear(CAMPOS_DE_PRECIO.precioCreditos);
  const precioMonedaReal = crear(CAMPOS_DE_PRECIO.precioMonedaReal);
  const repasarPrecio = () => {
    precioCreditos.hidden = casillaPremium.checked;
    precioMonedaReal.hidden = !casillaPremium.checked;
    precioCreditos.querySelector('input').disabled = casillaPremium.checked;
    precioMonedaReal.querySelector('input').disabled = !casillaPremium.checked;
  };
  casillaPremium.addEventListener('change', repasarPrecio);

  const formulario = h('form', {
    clase: 'hoja-producto__formulario',
    atributos: { novalidate: true, 'aria-label': 'Datos del producto' },
    hijos: [
      // El tipo va oculto: la validación del alta lo necesita para saber qué
      // campos exigir, y no se modifica (no viaja en el PATCH).
      h('input', { atributos: { type: 'hidden', name: 'tipo', value: producto.tipo } }),
      h('div', {
        clase: 'hoja-producto__rejilla',
        hijos: CAMPOS_COMUNES.map(crear),
      }),
      h('label', {
        clase: 'casilla casilla--caja',
        hijos: [
          casillaPremium,
          h('span', {
            clase: 'casilla__etiqueta',
            hijos: [
              'Premium',
              h('small', {
                clase: 'campo__pista',
                texto: 'Se vende solo en moneda real y no se puede subastar.',
              }),
            ],
          }),
        ],
      }),
      h('div', { clase: 'hoja-producto__rejilla', hijos: [precioCreditos, precioMonedaReal] }),
      h('fieldset', {
        clase: 'hoja-producto__tipo',
        hijos: [
          h('legend', {
            texto: `Atributos de ${ETIQUETA_DE_TIPO[producto.tipo] ?? producto.tipo}`,
          }),
          h('div', {
            clase: 'hoja-producto__rejilla',
            hijos: (CAMPOS_POR_TIPO[producto.tipo] ?? []).map(crear),
          }),
        ],
      }),
    ],
  });
  repasarPrecio();
  return {
    formulario,
    marcarErrores: (motivo) => {
      for (const c of campos) {
        c.marcarError(null);
      }
      if (motivo) {
        // La validación del alta dice el campo por su etiqueta: se marca el
        // que empieza así, si lo hay.
        const culpable = campos.find((c) => {
          const etiqueta = c.elemento
            .querySelector('label')
            .textContent.replace(/\s*\(.*\)\s*$/, '')
            .toLowerCase();
          return motivo.toLowerCase().includes(etiqueta);
        });
        culpable?.marcarError(motivo);
        culpable?.control.focus();
      }
    },
  };
}

/**
 * ProductAdminSheet: la ficha de gestión de un producto.
 *
 * @param {object} producto `ProductoCreado`
 * @param {{alCambiar?: (producto: object) => void, fetchImpl?: Function,
 *   confirmarAccion?: typeof confirmar}} [opciones]
 *   `alCambiar` recibe el producto como quedó tras guardar, suspender o reactivar.
 * @returns {{cerrar: () => void, elemento: HTMLElement}}
 */
export function abrirHojaDeProducto(
  producto,
  { alCambiar = () => {}, fetchImpl, confirmarAccion = confirmar } = {},
) {
  let actual = { ...producto };
  const zonaAviso = h('div', { datos: { zona: 'aviso-hoja' }, atributos: { hidden: true } });
  const resumen = h('div', { clase: 'hoja-producto__resumen' });
  const disponibilidad = h('section', {
    clase: 'hoja-producto__disponibilidad',
    atributos: { 'aria-label': 'Disponibilidad' },
  });

  function pintarResumen() {
    resumen.replaceChildren(
      h('p', {
        clase: 'hoja-producto__distintivos',
        hijos: [
          distintivo(ETIQUETA_DE_TIPO[actual.tipo] ?? actual.tipo),
          distintivoDeEstado(actual.estado),
          actual.premium ? distintivo('Premium', 'promocion') : null,
        ],
      }),
      h('p', {
        clase: 't-meta',
        texto: `Versión ${actual.version ?? '—'} · modificado el ${fechaHora(actual.modificadoEn)} · creado el ${fechaHora(actual.creadoEn)}`,
      }),
      h('p', { clase: 't-meta hoja-producto__id', texto: `Identificador: ${actual.id}` }),
    );
  }

  function pintarDisponibilidad() {
    const suspendido = actual.estado === 'SUSPENDIDO';
    const boton = h('button', {
      clase: `boton ${suspendido ? 'boton--primario' : 'boton--peligro'} boton--pequeno`,
      texto: suspendido ? 'Reactivar' : 'Suspender',
      atributos: { type: 'button' },
      datos: { accion: suspendido ? 'reactivar' : 'suspender' },
    });
    boton.addEventListener('click', () =>
      cambiarEstado(suspendido ? 'reactivar' : 'suspender', boton),
    );
    disponibilidad.replaceChildren(
      h('h3', { texto: 'Disponibilidad' }),
      h('p', {
        texto: suspendido
          ? 'Suspendido: nadie puede adquirirlo. Al reactivarlo vuelve al estado que tenía (activo o único) con el mismo tiraje.'
          : `${ETIQUETA_DE_ESTADO[actual.estado] ?? actual.estado}: se puede adquirir. Tiraje: ${textoDeTiraje(actual.tiraje)}.`,
      }),
      boton,
    );
  }

  async function cambiarEstado(accion, boton) {
    const suspender = accion === 'suspender';
    const seguro = await confirmarAccion({
      titulo: suspender ? `¿Suspender «${actual.nombre}»?` : `¿Reactivar «${actual.nombre}»?`,
      mensaje: suspender
        ? 'Nadie podrá adquirirlo mientras esté suspendido. No se borra y su tiraje no cambia.'
        : 'Vuelve al estado que tenía antes de suspenderse y conserva su tiraje.',
      textoConfirmar: suspender ? 'Suspender' : 'Reactivar',
      peligro: suspender,
    });
    if (!seguro) {
      return;
    }
    limpiarAviso(zonaAviso);
    conCarga(boton, true, suspender ? 'Suspendiendo…' : 'Reactivando…');
    try {
      const disponible = await cambiarDisponibilidad(actual.id, accion, { fetchImpl });
      aplicar({ ...actual, estado: disponible.estado, tiraje: disponible.tiraje ?? actual.tiraje });
      pintarAviso(zonaAviso, {
        tono: 'exito',
        titulo: suspender ? 'Producto suspendido' : 'Producto reactivado',
        detalle: suspender
          ? 'Ya no se puede adquirir. Puedes reactivarlo cuando quieras.'
          : 'Ya se puede adquirir de nuevo.',
      });
    } catch (error) {
      conCarga(boton, false);
      pintarAviso(zonaAviso, avisoDeFallo(error, suspender ? 'suspender' : 'reactivar'));
    }
  }

  function aplicar(nuevo) {
    actual = nuevo;
    pintarResumen();
    pintarDisponibilidad();
    alCambiar(actual);
  }

  const { formulario, marcarErrores } = formularioDeEdicion(actual);
  const guardar = h('button', {
    clase: 'boton boton--primario',
    texto: 'Guardar cambios',
    atributos: { type: 'submit' },
    datos: { accion: 'guardar-producto' },
  });
  formulario.append(h('div', { clase: 'hoja-producto__acciones', hijos: [guardar] }));
  formulario.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    limpiarAviso(zonaAviso);
    let solicitud;
    try {
      solicitud = construirSolicitudProducto(formulario);
    } catch (error) {
      marcarErrores(error.message);
      pintarAviso(zonaAviso, {
        tono: 'advertencia',
        titulo: 'Revisa el formulario',
        detalle: error.message,
      });
      return;
    }
    marcarErrores(null);
    const cambios = cambiosDe(actual, solicitud);
    if (Object.keys(cambios).length === 0) {
      pintarAviso(zonaAviso, {
        tono: 'info',
        titulo: 'No hay nada que guardar',
        detalle: 'Los datos son los mismos que ya tiene el producto.',
      });
      return;
    }
    conCarga(guardar, true, 'Guardando…');
    try {
      const guardado = await modificarProducto(actual.id, cambios, { fetchImpl });
      aplicar(guardado);
      pintarAviso(zonaAviso, {
        tono: 'exito',
        titulo: 'Cambios guardados',
        detalle: `Queda en la versión ${guardado.version ?? '—'}; la anterior se conserva como respaldo.`,
      });
    } catch (error) {
      pintarAviso(zonaAviso, avisoDeFallo(error, 'guardar los cambios'));
    } finally {
      conCarga(guardar, false);
    }
  });

  pintarResumen();
  pintarDisponibilidad();
  const cuerpo = h('div', {
    clase: 'hoja-producto',
    hijos: [resumen, zonaAviso, formulario, disponibilidad],
  });
  const dialogo = abrirDialogo({ titulo: actual.nombre, cuerpo, cerrableFuera: false });
  dialogo.elemento.classList.add('dialogo--hoja');
  return dialogo;
}

/**
 * La lista del catálogo con filtros y paginación, en la consola.
 *
 * @param {HTMLElement} zona
 * @param {{fetchImpl?: Function, alCambiarCatalogo?: () => void, hrefCrear?: string}} [opciones]
 *   `alCambiarCatalogo` avisa de que algo cambió (para repintar los contadores).
 */
export function montarCatalogoAdmin(
  zona,
  { fetchImpl, alCambiarCatalogo = () => {}, hrefCrear = './productos.html' } = {},
) {
  const criterios = { tipo: '', estado: '', pagina: 0 };
  let peticion = 0;

  const selectorTipo = campo({
    nombre: 'tipo',
    etiqueta: 'Tipo',
    opciones: [
      { valor: '', texto: 'Todos los tipos' },
      ...TIPOS_PRODUCTO.map((t) => ({ valor: t, texto: ETIQUETA_DE_TIPO[t] })),
    ],
  });
  const selectorEstado = campo({
    nombre: 'estado',
    etiqueta: 'Estado',
    opciones: [
      { valor: '', texto: 'Activos y únicos' },
      { valor: 'ACTIVO', texto: 'Solo activos' },
      { valor: 'UNICO', texto: 'Solo únicos' },
      { valor: 'SUSPENDIDO', texto: 'Suspendidos' },
    ],
  });
  const filtros = h('form', {
    clase: 'catalogo-admin__filtros',
    atributos: { role: 'search', 'aria-label': 'Filtrar el catálogo' },
    hijos: [selectorTipo.elemento, selectorEstado.elemento],
  });
  const cuenta = h('p', {
    clase: 'catalogo-admin__cuenta t-meta',
    atributos: { tabindex: '-1', 'aria-live': 'polite' },
  });
  const contenido = h('div', { datos: { zona: 'catalogo' } });
  const paginacion = h('div', { clase: 'catalogo-admin__paginacion' });
  zona.replaceChildren(
    h('div', {
      clase: 'catalogo-admin__encabezado',
      hijos: [
        h('h2', { texto: 'Productos del catálogo', atributos: { id: 'titulo-catalogo-admin' } }),
      ],
    }),
    filtros,
    cuenta,
    contenido,
    paginacion,
  );
  zona.setAttribute('aria-labelledby', 'titulo-catalogo-admin');

  filtros.addEventListener('change', () => {
    criterios.tipo = selectorTipo.control.value;
    criterios.estado = selectorEstado.control.value;
    criterios.pagina = 0;
    cargar();
  });
  filtros.addEventListener('submit', (evento) => evento.preventDefault());

  function hayFiltros() {
    return Boolean(criterios.tipo || criterios.estado);
  }

  function quitarFiltros() {
    selectorTipo.control.value = '';
    selectorEstado.control.value = '';
    criterios.tipo = '';
    criterios.estado = '';
    criterios.pagina = 0;
    cargar();
  }

  function filaDe(producto) {
    const gestionar = h('button', {
      clase: 'boton boton--secundario boton--pequeno',
      texto: 'Gestionar',
      atributos: { type: 'button', 'aria-label': `Gestionar ${producto.nombre}` },
      datos: { accion: 'gestionar', producto: producto.id },
    });
    gestionar.addEventListener('click', () =>
      abrirHojaDeProducto(producto, {
        fetchImpl,
        alCambiar: () => {
          alCambiarCatalogo();
          cargar({ conservarFoco: true });
        },
      }),
    );
    return h('tr', {
      datos: { producto: producto.id, estado: producto.estado },
      hijos: [
        h('th', {
          atributos: { scope: 'row' },
          hijos: [
            h('span', { clase: 'catalogo-admin__nombre', texto: producto.nombre }),
            h('span', {
              clase: 'catalogo-admin__tipo t-meta',
              texto: ETIQUETA_DE_TIPO[producto.tipo] ?? producto.tipo,
            }),
          ],
        }),
        h('td', { hijos: [distintivoDeEstado(producto.estado)] }),
        h('td', { texto: textoDeTiraje(producto.tiraje) }),
        h('td', { texto: textoDePrecio(producto) }),
        h('td', { clase: 't-meta', texto: fechaHora(producto.modificadoEn) }),
        h('td', { clase: 'catalogo-admin__accion', hijos: [gestionar] }),
      ],
    });
  }

  function pintarPagina(pagina) {
    const productos = pagina?.content ?? [];
    const total = Number(pagina?.totalElements) || 0;
    cuenta.textContent = total === 1 ? '1 producto' : `${total} productos`;
    if (productos.length === 0) {
      paginacion.replaceChildren();
      pintarEstado(
        contenido,
        hayFiltros()
          ? estadoVacio({
              titulo: 'Ningún producto cumple estos filtros',
              detalle: 'Prueba con otro tipo o estado.',
              accion: {
                texto: 'Quitar filtros',
                nombre: 'quitar-filtros',
                alPulsar: quitarFiltros,
              },
            })
          : estadoVacio({
              titulo: 'El catálogo está vacío',
              detalle: 'Todavía no hay productos activos. Crea el primero.',
              accion: { texto: 'Crear producto', href: hrefCrear },
            }),
      );
      return;
    }
    const tabla = h('table', {
      clase: 'catalogo-admin__tabla',
      hijos: [
        h('caption', { clase: 'solo-lectores', texto: 'Productos del catálogo' }),
        h('thead', {
          hijos: [
            h('tr', {
              hijos: ['Producto', 'Estado', 'Tiraje', 'Precio', 'Modificado', 'Acción'].map((t) =>
                h('th', { texto: t, atributos: { scope: 'col' } }),
              ),
            }),
          ],
        }),
        h('tbody', { hijos: productos.map(filaDe) }),
      ],
    });
    contenido.replaceChildren(
      h('div', {
        clase: 'catalogo-admin__desplazable',
        atributos: {
          tabindex: '0',
          role: 'region',
          'aria-label': 'Tabla de productos, desplazable',
        },
        hijos: [tabla],
      }),
    );
    const totalPaginas = Number(pagina.totalPages) || 0;
    if (totalPaginas > 1) {
      const control = construirPaginacion(
        { paginaActual: Number(pagina.page) || 0, totalPaginas },
        (nueva) => {
          criterios.pagina = nueva;
          cargar().then(() => cuenta.focus());
        },
      );
      control.setAttribute('aria-label', 'Páginas del catálogo');
      paginacion.replaceChildren(control);
    } else {
      paginacion.replaceChildren();
    }
  }

  async function cargar({ conservarFoco = false } = {}) {
    peticion += 1;
    const esta = peticion;
    if (!conservarFoco) {
      pintarEstado(contenido, estadoDeCarga({ filas: 4, etiqueta: 'Cargando el catálogo…' }));
    }
    let pagina;
    try {
      pagina = await listarProductos(
        {
          pagina: criterios.pagina,
          tamano: PRODUCTOS_POR_PAGINA,
          tipo: criterios.tipo || null,
          estado: criterios.estado || null,
        },
        { fetchImpl },
      );
    } catch {
      if (esta === peticion) {
        cuenta.textContent = '';
        paginacion.replaceChildren();
        pintarEstado(
          contenido,
          estadoDeError({
            titulo: 'No pudimos cargar el catálogo',
            detalle: 'El servicio de productos no respondió. Vuelve a intentarlo.',
            alReintentar: () => cargar(),
          }),
        );
      }
      return;
    }
    if (esta === peticion) {
      pintarPagina(pagina);
    }
  }

  cargar();
  return { recargar: cargar };
}
