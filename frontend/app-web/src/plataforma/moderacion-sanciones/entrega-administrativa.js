/**
 * Entregar productos al inventario de una cuenta desde su ficha — SA-10,
 * §7.3.4 «Acciones disponibles: ver y modificar inventario del usuario».
 *
 * `POST /api/v1/admin/usuarios/{usuarioId}/inventario/entregas`
 * (ms-identidad-admin.yaml 1.10.0): ms-identidad pide la entrega al
 * inventario con el token de quien actúa y la deja en la auditoría central con
 * el motivo. El producto se elige del catálogo (`GET /api/v1/productos`,
 * productos.yaml) por su tipo; los suspendidos no se ofrecen porque el
 * inventario no los entrega.
 *
 * **Una clave por intento.** La clave de la operación (`Idempotency-Key`) se
 * crea al abrir el formulario y se repite en cada reintento: si el inventario
 * o la bitácora no respondieron, pulsar otra vez no entrega dos veces. Tras
 * una entrega hecha, el siguiente formulario estrena clave.
 *
 * @module plataforma/moderacion-sanciones/entrega-administrativa
 */

import { rutaDeApi } from '../../comun/base-api.js';
import { fetchWithHttpErrorInterceptor } from '../../comun/interceptors/http-error.interceptor.js';
import { abrirPanelLateral, listaDeDatos } from '../../comun/ui/admin/panel-lateral.js';
import { campo } from '../../comun/ui/campo.js';
import { h } from '../../comun/ui/dom.js';
import { nombreDelTipo } from '../../comun/ui/formato.js';

/** Los tipos que guarda el inventario (inventario.yaml 1.8.0). */
export const TIPOS_ENTREGABLES = Object.freeze([
  'HEROE',
  'ARMA',
  'ARMADURA',
  'ITEM',
  'HABILIDAD',
  'EPICA',
]);

export const CANTIDAD_MAXIMA = 20;
export const MOTIVO_MINIMO = 3;
export const MOTIVO_MAXIMO = 500;
/** Páginas de 50 del catálogo que se leen por tipo, como mucho (200 productos). */
const PAGINAS_DEL_CATALOGO = 4;

const NIVEL_DE_ROL = Object.freeze({
  JUGADOR: 0,
  MODERADOR: 1,
  ADMINISTRADOR: 2,
  SUPER_ADMINISTRADOR: 3,
});

/**
 * Si quien mira puede modificar el inventario de esa cuenta: Administrador o
 * Super Administrador (`GESTIONAR_CUENTAS`), sobre un rol inferior, o un Super
 * Administrador sobre otro (7.3.1). El servidor lo vuelve a decidir; esto solo
 * evita ofrecer un botón que siempre respondería que no.
 *
 * @param {string|null|undefined} rolDeQuienMira
 * @param {string|null|undefined} rolDeLaCuenta
 */
export function puedeEntregarA(rolDeQuienMira, rolDeLaCuenta) {
  const actor = NIVEL_DE_ROL[rolDeQuienMira] ?? -1;
  const objetivo = NIVEL_DE_ROL[rolDeLaCuenta] ?? 0;
  if (actor < NIVEL_DE_ROL.ADMINISTRADOR) {
    return false;
  }
  return actor > objetivo || (actor === NIVEL_DE_ROL.SUPER_ADMINISTRADOR && actor === objetivo);
}

/**
 * La clave de la operación en curso: la misma en cada reintento y una nueva
 * tras una entrega hecha o un rechazo que no se arregla repitiendo.
 */
export function claveDeLaOperacion() {
  let actual = claveNueva();
  return {
    actual: () => actual,
    renovar: () => {
      actual = claveNueva();
      return actual;
    },
  };
}

/** Una clave nueva para la operación. */
export function claveNueva() {
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID();
  }
  return `entrega-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

/**
 * Los productos de un tipo que se pueden entregar, por nombre.
 *
 * @param {string} tipo
 * @param {{buscar?: typeof fetchWithHttpErrorInterceptor}} [opciones]
 * @returns {Promise<Array<{id: string, nombre: string}>>}
 */
export async function productosDelTipo(tipo, { buscar = fetchWithHttpErrorInterceptor } = {}) {
  const productos = [];
  for (let pagina = 0; pagina < PAGINAS_DEL_CATALOGO; pagina += 1) {
    const respuesta = await buscar(
      rutaDeApi(`/productos?tipo=${encodeURIComponent(tipo)}&size=50&page=${pagina}`),
      { headers: { Accept: 'application/json' } },
    );
    if (!respuesta.ok) {
      throw new Error(`catalogo ${respuesta.status}`);
    }
    const datos = await respuesta.json();
    const filas = Array.isArray(datos?.content) ? datos.content : [];
    for (const producto of filas) {
      if (producto?.id && producto.estado !== 'SUSPENDIDO') {
        productos.push({ id: String(producto.id), nombre: String(producto.nombre ?? producto.id) });
      }
    }
    const ultima =
      typeof datos?.totalPages === 'number' ? pagina >= datos.totalPages - 1 : filas.length < 50;
    if (ultima) {
      break;
    }
  }
  return productos.sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'));
}

/**
 * Lo que falta o sobra en el formulario, campo por campo.
 *
 * @param {{productoId: string, cantidad: string|number, motivo: string}} valores
 * @returns {Record<string, string>}
 */
export function validarEntrega({ productoId, cantidad, motivo }) {
  const errores = {};
  if (!productoId) {
    errores.producto = 'Elige el producto.';
  }
  const unidades = Number(cantidad);
  if (!Number.isInteger(unidades) || unidades < 1 || unidades > CANTIDAD_MAXIMA) {
    errores.cantidad = `De 1 a ${CANTIDAD_MAXIMA} unidades.`;
  }
  const texto = String(motivo ?? '').trim();
  if (texto.length < MOTIVO_MINIMO) {
    errores.motivo = 'Escribe por qué se entrega: queda en la auditoría.';
  } else if (texto.length > MOTIVO_MAXIMO) {
    errores.motivo = `Como mucho ${MOTIVO_MAXIMO} caracteres.`;
  }
  return errores;
}

/** «Se entregarán 2 × Hacha de Obsidiana a ana_x.» */
export function resumenDeEntrega({ cantidad, nombre, apodo }) {
  const unidades = Number(cantidad);
  if (!nombre || !Number.isInteger(unidades) || unidades < 1) {
    return 'Elige el producto y cuántas unidades.';
  }
  return `Se ${unidades === 1 ? 'entregará' : 'entregarán'} ${unidades} × ${nombre} a ${apodo}. Queda en la auditoría con tu motivo.`;
}

/**
 * El texto de un rechazo, para la persona que pulsó «Entregar».
 *
 * @param {number} estado
 * @param {{type?: string, detail?: string}|null} problema
 * @returns {{texto: string, repetirConLaMismaClave: boolean}}
 */
export function textoDelRechazo(estado, problema) {
  const tipo = String(problema?.type ?? '');
  if (estado === 503 && tipo.endsWith('/auditoria-no-disponible')) {
    return {
      texto:
        'La entrega se hizo, pero no pudo quedar en la auditoría. Pulsa «Entregar» otra vez: ' +
        'no se entregará dos veces y se completará el registro.',
      repetirConLaMismaClave: true,
    };
  }
  if (estado === 503 || estado === 0) {
    return {
      texto:
        'El inventario no responde: no se entregó nada. Vuelve a intentarlo en unos segundos; ' +
        'no se entregará dos veces.',
      repetirConLaMismaClave: true,
    };
  }
  if (estado === 403) {
    return {
      texto: tipo.endsWith('/jerarquia-insuficiente')
        ? 'No puedes modificar el inventario de una cuenta con un rol igual o superior al tuyo.'
        : 'Tu cuenta no puede modificar inventarios.',
      repetirConLaMismaClave: false,
    };
  }
  return {
    texto:
      problema?.detail || 'No se pudo hacer la entrega. Revisa los datos e inténtalo de nuevo.',
    repetirConLaMismaClave: false,
  };
}

/**
 * Pide la entrega. Devuelve la respuesta o el rechazo con su estado.
 *
 * @param {number|string} usuarioId la clave interna de la cuenta (la del directorio)
 * @param {{productoId: string, cantidad: number, motivo: string}} datos
 * @param {string} clave
 * @param {{buscar?: typeof fetchWithHttpErrorInterceptor}} [opciones]
 */
export async function pedirEntrega(
  usuarioId,
  datos,
  clave,
  { buscar = fetchWithHttpErrorInterceptor } = {},
) {
  let respuesta;
  try {
    respuesta = await buscar(
      rutaDeApi(`/admin/usuarios/${encodeURIComponent(usuarioId)}/inventario/entregas`),
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'application/json, application/problem+json',
          'Idempotency-Key': clave,
        },
        body: JSON.stringify(datos),
        rechazoEsperado: true,
      },
    );
  } catch {
    return { ok: false, estado: 0, problema: null };
  }
  let cuerpo = null;
  try {
    cuerpo = await respuesta.json();
  } catch {
    cuerpo = null;
  }
  return respuesta.ok
    ? { ok: true, estado: respuesta.status, entrega: cuerpo }
    : { ok: false, estado: respuesta.status, problema: cuerpo };
}

/**
 * Abre el panel de la entrega para una cuenta.
 *
 * @param {object} opciones
 * @param {{id: number|string, apodo?: string, uid?: string}} opciones.ficha
 * @param {(entrega: object) => void} [opciones.alEntregar] tras una entrega hecha
 * @param {typeof abrirPanelLateral} [opciones.abrirPanel]
 * @param {typeof fetchWithHttpErrorInterceptor} [opciones.buscar]
 */
export function abrirEntregaAdministrativa({
  ficha,
  alEntregar = () => {},
  abrirPanel = abrirPanelLateral,
  buscar = fetchWithHttpErrorInterceptor,
}) {
  const apodo = ficha.apodo || 'esta cuenta';
  const clave = claveDeLaOperacion();
  /** @type {Array<{id: string, nombre: string}>} */
  let productos = [];

  const tipo = campo({
    nombre: 'tipo',
    etiqueta: 'Tipo de producto',
    requerido: true,
    opciones: [
      { valor: '', texto: 'Elige un tipo' },
      ...TIPOS_ENTREGABLES.map((t) => ({ valor: t, texto: nombreDelTipo(t) })),
    ],
  });
  const producto = campo({
    nombre: 'producto',
    etiqueta: 'Producto',
    requerido: true,
    opciones: [{ valor: '', texto: 'Primero elige el tipo' }],
  });
  producto.control.disabled = true;
  const cantidad = campo({
    nombre: 'cantidad',
    etiqueta: 'Unidades',
    tipo: 'number',
    valor: '1',
    requerido: true,
    pista: `De 1 a ${CANTIDAD_MAXIMA}. Cada unidad es un elemento propio en su inventario.`,
    atributos: { min: 1, max: CANTIDAD_MAXIMA, step: 1, inputmode: 'numeric' },
  });
  const motivo = campo({
    nombre: 'motivo',
    etiqueta: 'Motivo',
    multilinea: true,
    requerido: true,
    pista: 'Queda en la auditoría y lo ve el Super Administrador.',
    atributos: { maxlength: MOTIVO_MAXIMO },
  });
  const resumen = h('p', {
    clase: 'aviso aviso--info',
    datos: { zona: 'resumen-de-la-entrega' },
    atributos: { 'aria-live': 'polite' },
  });
  const error = h('p', {
    clase: 'aviso aviso--error',
    datos: { zona: 'error-de-la-entrega' },
    atributos: { role: 'alert' },
  });
  error.hidden = true;
  const formulario = h('form', {
    clase: 'pila pila--compacta',
    datos: { zona: 'formulario-de-entrega' },
    atributos: { novalidate: true },
    hijos: [tipo.elemento, producto.elemento, cantidad.elemento, motivo.elemento, resumen],
  });
  const entregar = h('button', {
    clase: 'boton boton--primario',
    texto: 'Entregar',
    atributos: { type: 'button' },
    datos: { accion: 'confirmar-entrega' },
  });

  const nombreElegido = () =>
    productos.find((p) => p.id === producto.control.value)?.nombre ?? null;
  const actualizarResumen = () => {
    resumen.textContent = resumenDeEntrega({
      cantidad: cantidad.control.value,
      nombre: nombreElegido(),
      apodo,
    });
  };
  actualizarResumen();

  async function cargarProductos() {
    const elegido = tipo.control.value;
    producto.control.replaceChildren(
      h('option', {
        texto: elegido ? 'Cargando el catálogo…' : 'Primero elige el tipo',
        atributos: { value: '' },
      }),
    );
    producto.control.disabled = true;
    productos = [];
    actualizarResumen();
    if (!elegido) {
      return;
    }
    try {
      productos = await productosDelTipo(elegido, { buscar });
    } catch {
      producto.control.replaceChildren(
        h('option', { texto: 'No se pudo cargar el catálogo', atributos: { value: '' } }),
      );
      producto.marcarError('El catálogo no respondió. Vuelve a elegir el tipo para reintentar.');
      return;
    }
    if (tipo.control.value !== elegido) {
      return;
    }
    producto.marcarError(null);
    producto.control.replaceChildren(
      h('option', {
        texto: productos.length
          ? 'Elige el producto'
          : `No hay ${nombreDelTipo(elegido).toLowerCase()}s que entregar`,
        atributos: { value: '' },
      }),
      ...productos.map((p) => h('option', { texto: p.nombre, atributos: { value: p.id } })),
    );
    producto.control.disabled = productos.length === 0;
    actualizarResumen();
  }

  tipo.control.addEventListener('change', cargarProductos);
  producto.control.addEventListener('change', actualizarResumen);
  cantidad.control.addEventListener('input', actualizarResumen);

  const { cerrar } = abrirPanel({
    titulo: `Entregar productos · ${apodo}`,
    subtitulo: 'Se suman a su inventario como entrega de la administración.',
    contenido: [listaDeDatos([['Cuenta', apodo]]), formulario, error],
    acciones: [entregar],
  });

  async function enviar() {
    error.hidden = true;
    const valores = {
      productoId: producto.control.value,
      cantidad: cantidad.control.value,
      motivo: motivo.control.value,
    };
    const errores = validarEntrega(valores);
    producto.marcarError(errores.producto ?? null);
    cantidad.marcarError(errores.cantidad ?? null);
    motivo.marcarError(errores.motivo ?? null);
    if (Object.keys(errores).length > 0) {
      return;
    }
    entregar.disabled = true;
    try {
      const resultado = await pedirEntrega(
        ficha.id,
        {
          productoId: valores.productoId,
          cantidad: Number(valores.cantidad),
          motivo: valores.motivo.trim(),
        },
        clave.actual(),
        { buscar },
      );
      if (resultado.ok) {
        clave.renovar();
        cerrar?.();
        alEntregar({ ...resultado.entrega, nombre: nombreElegido() });
        return;
      }
      const rechazo = textoDelRechazo(resultado.estado, resultado.problema);
      if (!rechazo.repetirConLaMismaClave) {
        clave.renovar();
      }
      error.textContent = rechazo.texto;
      error.hidden = false;
    } finally {
      entregar.disabled = false;
    }
  }

  entregar.addEventListener('click', enviar);
  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    enviar();
  });

  return { cerrar, enviar, cargarProductos, clave };
}
