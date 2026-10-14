/**
 * Panel de administración de la lista negra de términos prohibidos
 * (HU-ADM-002; 7.1.1 y 7.3.2 del documento del curso: «lista negra
 * actualizable»).
 *
 * SA-4 (informe de revisión, punto 21): la página pasa al kit de la consola
 * —cifras arriba, alta en su panel, los términos en una tabla con las
 * acciones a un lado, la edición completa (término, categoría y modo) en un
 * panel lateral— y deja de explicar el algoritmo en la cabecera.
 *
 * Habla con `contracts/openapi/moderacion-lista-negra.yaml` 2.0.x por el
 * borde. Desde la 2.0.0 el listado no es una lista de cadenas sino una página
 * de términos con su categoría del 7.1.1, su modo de coincidencia, si está
 * activo y quién lo dio de alta; esta vista los muestra, da de alta con
 * categoría y modo, activa o desactiva sin borrar, edita y elimina.
 *
 * La identidad de un término es su forma normalizada («Spider-Man» y
 * «spiderman» son el mismo): el servicio responde 409 si ya existe y la vista
 * lo cuenta tal cual. Los errores llegan como problem details y se decide por
 * el estado HTTP, nunca comparando textos (`shared/ui-kit/MAPEO-ERRORES.md`).
 */

import { NOMBRE_DE_CATEGORIA_DE_TERMINO } from '../../comun/ui/admin/nombres.js';
import { fetchWithHttpErrorInterceptor } from '../../comun/interceptors/http-error.interceptor.js';
import { construirPaginacion } from '../../comun/paginacion.js';
import { limpiarAviso, pintarAviso, tonoPorEstado } from '../../comun/ui/aviso.js';
import { barraDeFiltros } from '../../comun/ui/admin/barra-de-filtros.js';
import { distintivoDeEstado } from '../../comun/ui/admin/estado.js';
import { rejillaDeIndicadores, tarjetaDeIndicador } from '../../comun/ui/admin/indicador.js';
import { abrirPanelLateral } from '../../comun/ui/admin/panel-lateral.js';
import { tablaDeDatos } from '../../comun/ui/admin/tabla-de-datos.js';
import { confirmarConMotivo } from '../../comun/ui/dialogo.js';
import { h, vaciar } from '../../comun/ui/dom.js';
import { estadoDeCarga, estadoDeError, pintarEstado } from '../../comun/ui/estado-vista.js';
import { fecha as fechaCorta } from '../../comun/ui/formato.js';
import { encabezadoDePagina } from '../../comun/ui/pagina.js';
import { panel } from '../consola/panel.js';

export const BASE_URL = '/api/v1/lista-negra/terminos';

/** RNF-USA-001: paginación de 16 elementos en los listados. */
export const TAMANO_DE_PAGINA = 16;

/** Categorías del contrato, con el texto que ve el moderador (7.1.1). */
export const CATEGORIAS = NOMBRE_DE_CATEGORIA_DE_TERMINO;

/** Modos de coincidencia del contrato. */
export const MODOS = Object.freeze({
  SUBCADENA: 'Dentro de otras palabras',
  PALABRA: 'Palabra entera',
});

/** Error del servicio con la forma del problem details. */
export class ErrorDeListaNegra extends Error {
  /**
   * @param {{title?: string, detail?: string, motivo?: string}|null} problema
   * @param {number} estado
   */
  constructor(problema, estado) {
    super(problema?.detail ?? problema?.title ?? `Error ${estado}`);
    this.name = 'ErrorDeListaNegra';
    this.estado = estado;
    this.titulo = problema?.title ?? 'No se pudo completar';
    this.detalle = problema?.detail ?? '';
    this.motivo = problema?.motivo ?? null;
  }
}

/**
 * @param {string} categoria
 * @returns {string}
 */
export function etiquetaDeCategoria(categoria) {
  return CATEGORIAS[categoria] ?? categoria ?? '—';
}

/**
 * @param {string} modo
 * @returns {string}
 */
export function etiquetaDeModo(modo) {
  return MODOS[modo] ?? modo ?? '—';
}

/**
 * La consulta de `GET /terminos` con los filtros que tengan valor.
 *
 * @param {{categoria?: string, activo?: string, buscar?: string}} filtros
 * @param {number} pagina desde cero
 * @returns {string} `?pagina=0&tamano=16&...`
 */
export function consultaDe(filtros = {}, pagina = 0) {
  const parametros = new URLSearchParams({
    pagina: String(pagina),
    tamano: String(TAMANO_DE_PAGINA),
  });
  if (filtros.categoria) {
    parametros.set('categoria', filtros.categoria);
  }
  if (filtros.activo === 'true' || filtros.activo === 'false') {
    parametros.set('activo', filtros.activo);
  }
  const buscar = String(filtros.buscar ?? '').trim();
  if (buscar) {
    parametros.set('buscar', buscar);
  }
  return `?${parametros.toString()}`;
}

/**
 * El cuerpo de un alta a partir del formulario: sin inventar nada; el modo
 * solo va si el moderador eligió uno (si no, el servicio aplica el de omisión
 * según la longitud del término).
 *
 * @param {FormData|Record<string, string>} datos
 * @returns {{termino: string, categoria?: string, modo?: string}}
 */
export function altaDesde(datos) {
  const leer = (clave) =>
    String((datos instanceof FormData ? datos.get(clave) : datos[clave]) ?? '').trim();
  const cuerpo = { termino: leer('termino') };
  if (leer('categoria')) {
    cuerpo.categoria = leer('categoria');
  }
  if (leer('modo')) {
    cuerpo.modo = leer('modo');
  }
  return cuerpo;
}

/** Quien dio de alta el término, como se lee: la carga inicial no es una persona. */
export function autorDe(termino) {
  if (!termino?.creadoPor) {
    return 'Sin autor';
  }
  return termino.creadoPor === 'semilla' ? 'Carga inicial' : termino.creadoPor;
}

/**
 * La celda «Alta»: quién y cuándo.
 *
 * @param {{creadoPor?: string|null, creadoEn?: string|null}} termino
 * @returns {string}
 */
export function altaDe(termino) {
  const cuando = termino?.creadoEn ? fechaCorta(termino.creadoEn) : null;
  return cuando && cuando !== '—' ? `${autorDe(termino)} · ${cuando}` : autorDe(termino);
}

/** Opciones de los desplegables, como pares `[valor, texto]`. */
const OPCIONES_DE_CATEGORIA = Object.freeze(Object.entries(CATEGORIAS));
const OPCIONES_DE_MODO = Object.freeze(Object.entries(MODOS));
const OPCIONES_DE_ESTADO = Object.freeze([
  ['true', 'Activos'],
  ['false', 'Apagados'],
]);

/* ---- API ---- */

function baseDeApi() {
  const meta = globalThis.document?.querySelector?.('meta[name="nexus-api-base"]');
  return String(meta?.content ?? '').replace(/\/+$/, '');
}

async function pedir(ruta, opciones = {}, fetchImpl = fetchWithHttpErrorInterceptor) {
  const respuesta = await fetchImpl(`${baseDeApi()}${ruta}`, {
    ...opciones,
    headers: { Accept: 'application/json', ...(opciones.headers ?? {}) },
  });
  if (respuesta.ok) {
    return respuesta.status === 204 ? null : respuesta.json();
  }
  let problema = null;
  try {
    problema = await respuesta.json();
  } catch {
    problema = null;
  }
  throw new ErrorDeListaNegra(problema, respuesta.status);
}

const conCuerpo = (metodo, cuerpo) => ({
  method: metodo,
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(cuerpo),
});

const caminoDe = (termino) => `${BASE_URL}/${encodeURIComponent(termino)}`;

export const api = {
  listar: (filtros, pagina, f) => pedir(`${BASE_URL}${consultaDe(filtros, pagina)}`, {}, f),
  agregar: (cuerpo, f) => pedir(BASE_URL, conCuerpo('POST', cuerpo), f),
  editar: (termino, cuerpo, f) => pedir(caminoDe(termino), conCuerpo('PUT', cuerpo), f),
  // 2.2.0: el motivo va a la bitácora central (§7.3.5).
  eliminar: (termino, motivo, f) =>
    pedir(
      motivo ? `${caminoDe(termino)}?motivo=${encodeURIComponent(motivo)}` : caminoDe(termino),
      { method: 'DELETE' },
      f,
    ),
};

/** Mínimo y máximo del motivo de un cambio (la bitácora central admite 500). */
export const MOTIVO_MINIMO = 5;
export const MOTIVO_MAXIMO = 500;

/**
 * Lo que dice el diálogo de cada acción sobre un término; las tres piden el
 * motivo, que queda en el registro de auditoría (§7.3.5).
 *
 * @param {object} termino
 * @param {'quitar'|'apagar'|'activar'} accion
 */
export function dialogoDeAccion(termino, accion) {
  const nombre = `«${termino.termino}»`;
  if (accion === 'quitar') {
    return {
      titulo: `¿Quitar ${nombre} de la lista negra?`,
      mensaje:
        'Deja de filtrarse desde este momento. Si solo quieres dejar de aplicarlo un tiempo, apágalo.',
      textoConfirmar: 'Quitar término',
      peligro: true,
    };
  }
  if (accion === 'apagar') {
    return {
      titulo: `¿Apagar ${nombre}?`,
      mensaje: 'Deja de aplicarse sin borrarse; lo puedes volver a activar cuando quieras.',
      textoConfirmar: 'Apagar',
      peligro: false,
    };
  }
  return {
    titulo: `¿Activar ${nombre}?`,
    mensaje: 'Vuelve a filtrarse en apodos, nombres, comentarios y chat desde este momento.',
    textoConfirmar: 'Activar',
    peligro: false,
  };
}

/* ---- DOM ---- */

let secuencia = 0;

/** Un desplegable del kit con su etiqueta. */
function desplegable({ id, nombre, etiqueta, opciones, vacio = null, valor = '', pista = null }) {
  const control = h('select', {
    clase: 'desplegable__control',
    atributos: { id, name: nombre },
    hijos: [
      ...(vacio !== null ? [h('option', { texto: vacio, atributos: { value: '' } })] : []),
      ...opciones.map(([v, texto]) => h('option', { texto, atributos: { value: v } })),
    ],
  });
  control.value = valor;
  const pistaId = pista ? `${id}-pista` : null;
  if (pistaId) {
    control.setAttribute('aria-describedby', pistaId);
  }
  return h('div', {
    clase: 'desplegable',
    hijos: [
      h('label', { clase: 'campo__etiqueta', texto: etiqueta, atributos: { for: id } }),
      control,
      ...(pista
        ? [h('p', { clase: 'campo__pista', texto: pista, atributos: { id: pistaId } })]
        : []),
    ],
  });
}

/** El campo del término. */
function campoDeTermino({ id, valor = '' }) {
  const entrada = h('input', {
    clase: 'campo__control',
    atributos: {
      id,
      name: 'termino',
      type: 'text',
      maxlength: 120,
      required: true,
      autocomplete: 'off',
      placeholder: 'Por ejemplo, spiderman',
    },
  });
  entrada.value = valor;
  return h('div', {
    clase: 'campo',
    hijos: [
      h('label', { clase: 'campo__etiqueta', texto: 'Término', atributos: { for: id } }),
      entrada,
    ],
  });
}

const PISTA_DEL_MODO =
  'Automático: dentro de otras palabras desde 5 letras; si es más corto, solo como palabra entera.';

/**
 * El formulario de alta: término, categoría y cómo se busca.
 *
 * @returns {HTMLFormElement}
 */
export function formularioDeAlta() {
  secuencia += 1;
  const id = `alta-${secuencia}`;
  return h('form', {
    clase: 'formulario-termino',
    atributos: { novalidate: true, 'aria-label': 'Añadir un término a la lista negra' },
    datos: { zona: 'alta' },
    hijos: [
      campoDeTermino({ id: `${id}-termino` }),
      desplegable({
        id: `${id}-categoria`,
        nombre: 'categoria',
        etiqueta: 'Categoría',
        opciones: OPCIONES_DE_CATEGORIA,
        vacio: 'Elige la categoría',
      }),
      desplegable({
        id: `${id}-modo`,
        nombre: 'modo',
        etiqueta: 'Cómo se busca',
        opciones: OPCIONES_DE_MODO,
        vacio: 'Automático',
        pista: PISTA_DEL_MODO,
      }),
      h('div', {
        clase: 'formulario-termino__accion',
        hijos: [
          h('button', {
            clase: 'boton boton--primario',
            texto: 'Agregar término',
            atributos: { type: 'submit' },
          }),
        ],
      }),
    ],
  });
}

/**
 * Los botones de una fila: editar, apagar o activar, y quitar.
 *
 * @param {object} termino `TerminoResponse`
 * @param {{editar?: Function, alternar?: Function, eliminar?: Function}} acciones
 * @returns {HTMLElement}
 */
export function accionesDeTermino(termino, { editar, alternar, eliminar } = {}) {
  const boton = (texto, clase, accion, etiqueta, alPulsar) => {
    const elemento = h('button', {
      clase: `boton ${clase} boton--pequeno`,
      texto,
      atributos: { type: 'button', 'aria-label': etiqueta },
      datos: { accion },
    });
    elemento.addEventListener('click', () => alPulsar?.(termino));
    return elemento;
  };
  return h('div', {
    clase: 'tabla-admin__acciones',
    hijos: [
      boton(
        'Editar',
        'boton--secundario',
        'editar',
        `Editar el término ${termino.termino}`,
        editar,
      ),
      boton(
        termino.activo ? 'Apagar' : 'Activar',
        'boton--secundario',
        'alternar',
        `${termino.activo ? 'Apagar' : 'Activar'} el término ${termino.termino}`,
        alternar,
      ),
      boton(
        'Quitar',
        'boton--peligro',
        'eliminar',
        `Quitar el término ${termino.termino}`,
        eliminar,
      ),
    ],
  });
}

/**
 * La tabla de términos: cada uno con su categoría, su modo, su estado, su
 * alta y sus acciones a la derecha.
 *
 * @param {object[]} terminos
 * @param {{editar?: Function, alternar?: Function, eliminar?: Function, resumen?: string|null}} acciones
 * @returns {HTMLElement}
 */
export function tablaDeTerminos(terminos, { resumen = null, ...acciones } = {}) {
  return tablaDeDatos({
    titulo: 'Términos vetados',
    resumen,
    claveDeFila: (t) => String(t.id ?? t.termino),
    columnas: [
      { clave: 'termino', titulo: 'Término', principal: true },
      {
        clave: 'categoria',
        titulo: 'Categoría',
        valor: (t) => etiquetaDeCategoria(t.categoria),
      },
      { clave: 'modo', titulo: 'Cómo se busca', valor: (t) => etiquetaDeModo(t.modo) },
      {
        clave: 'activo',
        titulo: 'Estado',
        valor: (t) =>
          distintivoDeEstado(
            t.activo ? { texto: 'Activo', tono: 'exito' } : { texto: 'Apagado', tono: 'neutro' },
          ),
      },
      { clave: 'alta', titulo: 'Alta', valor: (t) => altaDe(t) },
      {
        clave: 'acciones',
        titulo: 'Acciones',
        tipo: 'accion',
        valor: (t) => accionesDeTermino(t, acciones),
      },
    ],
    filas: terminos,
  });
}

/**
 * El formulario de edición del panel lateral: el término, su categoría y
 * cómo se busca, con los valores de ahora.
 *
 * @param {object} termino
 * @returns {{elemento: HTMLFormElement,
 *   leer: () => {termino: string, categoria: string, modo: string, motivo: string}}}
 */
export function formularioDeEdicion(termino) {
  secuencia += 1;
  const id = `edicion-${secuencia}`;
  const elemento = h('form', {
    clase: 'pila pila--compacta',
    atributos: { novalidate: true, 'aria-label': `Editar el término ${termino.termino}` },
    datos: { zona: 'edicion' },
    hijos: [
      campoDeTermino({ id: `${id}-termino`, valor: termino.termino }),
      desplegable({
        id: `${id}-categoria`,
        nombre: 'categoria',
        etiqueta: 'Categoría',
        opciones: OPCIONES_DE_CATEGORIA,
        valor: termino.categoria ?? 'OTRO',
      }),
      desplegable({
        id: `${id}-modo`,
        nombre: 'modo',
        etiqueta: 'Cómo se busca',
        opciones: OPCIONES_DE_MODO,
        valor: termino.modo ?? 'SUBCADENA',
      }),
      h('div', {
        clase: 'campo',
        hijos: [
          h('label', {
            clase: 'campo__etiqueta',
            texto: 'Motivo del cambio',
            atributos: { for: `${id}-motivo` },
          }),
          h('textarea', {
            clase: 'campo__control',
            atributos: {
              id: `${id}-motivo`,
              name: 'motivo',
              rows: 3,
              minlength: MOTIVO_MINIMO,
              maxlength: MOTIVO_MAXIMO,
              required: true,
              'aria-describedby': `${id}-motivo-pista`,
            },
          }),
          h('p', {
            clase: 'campo__pista',
            texto: 'Queda en el registro de auditoría con tu nombre y la fecha.',
            atributos: { id: `${id}-motivo-pista` },
          }),
        ],
      }),
      h('p', {
        clase: 'campo__error',
        atributos: { role: 'alert' },
        datos: { zona: 'error-edicion' },
      }),
    ],
  });
  elemento.querySelector('[data-zona="error-edicion"]').hidden = true;
  const leer = () => {
    const datos = new FormData(elemento);
    return {
      termino: String(datos.get('termino') ?? '').trim(),
      categoria: String(datos.get('categoria') ?? ''),
      modo: String(datos.get('modo') ?? ''),
      motivo: String(datos.get('motivo') ?? '').trim(),
    };
  };
  return { elemento, leer };
}

/**
 * Monta la vista sobre las zonas `[data-zona=encabezado|aviso|indicadores|consola]`
 * de `lista-negra-admin.html`.
 *
 * Confirmar es el diálogo del kit (con su etiqueta, se lee con lector de
 * pantalla y devuelve el foco), no `window.confirm()`; se puede inyectar, y lo
 * que devuelva se espera con `await`.
 *
 * @param {ParentNode} raiz
 * @param {{fetchImpl?: Function,
 *          confirmar?: (termino: object, accion: 'quitar'|'apagar'|'activar') =>
 *            string|boolean|null|Promise<string|boolean|null>,
 *          abrirEdicion?: typeof abrirPanelLateral}} [opciones]
 * @returns {{cargar: (pagina?: number) => Promise<void>, cargarCifras: () => Promise<void>}}
 */
export function montarListaNegra(
  raiz,
  {
    fetchImpl,
    confirmar = (termino, accion) =>
      confirmarConMotivo({
        ...dialogoDeAccion(termino, accion),
        minimo: MOTIVO_MINIMO,
        maximo: MOTIVO_MAXIMO,
      }),
    abrirEdicion = abrirPanelLateral,
  } = {},
) {
  const zonaEncabezado = raiz.querySelector('[data-zona="encabezado"]');
  const zonaAviso = raiz.querySelector('[data-zona="aviso"]');
  const zonaIndicadores = raiz.querySelector('[data-zona="indicadores"]');
  const zonaConsola = raiz.querySelector('[data-zona="consola"]');

  zonaEncabezado?.replaceChildren(
    encabezadoDePagina({
      titulo: 'Lista negra',
      descripcion: 'Vocabulario vetado en apodos, nombres, comentarios y chat.',
    }),
  );

  const alta = panel({ id: 'alta-de-termino', titulo: 'Añadir un término', ancho: 'completo' });
  const formAlta = formularioDeAlta();
  // Un formulario no «carga»: el sello queda como el de un panel con datos.
  alta.sello.dataset.estado = 'datos';
  vaciar(alta.zona).append(formAlta);

  const listado = panel({ id: 'terminos', titulo: 'Términos vetados', ancho: 'completo' });
  const zonaTabla = h('div', { clase: 'pila pila--compacta', datos: { zona: 'terminos' } });
  const zonaPaginacion = h('div', { datos: { zona: 'paginacion' } });

  let filtros = {};
  let paginaActual = 0;

  const barra = barraDeFiltros({
    etiqueta: 'Buscar y filtrar términos',
    busqueda: { nombre: 'buscar', etiqueta: 'Término', placeholder: 'Busca un término' },
    filtros: [
      {
        nombre: 'categoria',
        etiqueta: 'Categoría',
        opciones: OPCIONES_DE_CATEGORIA,
        todos: 'Todas las categorías',
      },
      { nombre: 'activo', etiqueta: 'Estado', opciones: OPCIONES_DE_ESTADO, todos: 'Todos' },
    ],
    alAplicar: (valores) => {
      filtros = {};
      for (const clave of ['categoria', 'activo', 'buscar']) {
        const valor = String(valores[clave] ?? '').trim();
        if (valor) {
          filtros[clave] = valor;
        }
      }
      cargar(0);
    },
  });
  listado.sello.dataset.estado = 'datos';
  vaciar(listado.zona).append(barra.elemento, zonaTabla, zonaPaginacion);

  if (zonaConsola) {
    vaciar(zonaConsola).append(
      h('div', { clase: 'consola-integral__rejilla', hijos: [alta.elemento, listado.elemento] }),
    );
  }

  function contarError(error, accion) {
    const deServicio = error instanceof ErrorDeListaNegra;
    pintarAviso(zonaAviso, {
      tono: deServicio ? tonoPorEstado(error.estado) : 'error',
      titulo: deServicio ? error.titulo : `No se pudo ${accion}`,
      detalle: deServicio ? error.detalle : 'No se pudo conectar con el servicio de moderación.',
    });
  }

  function contarExito(titulo, detalle) {
    pintarAviso(zonaAviso, { tono: 'exito', titulo, detalle });
  }

  /** Las tres cifras de arriba; la que no responda se dice «sin dato», nunca 0. */
  async function cargarCifras() {
    if (!zonaIndicadores) {
      return;
    }
    const total = async (consulta) => {
      try {
        const respuesta = await api.listar(consulta, 0, fetchImpl);
        return Number.isFinite(respuesta?.total) ? respuesta.total : null;
      } catch {
        return null;
      }
    };
    const [todos, activos, apagados] = await Promise.all([
      total({}),
      total({ activo: 'true' }),
      total({ activo: 'false' }),
    ]);
    zonaIndicadores.replaceChildren(
      rejillaDeIndicadores(
        [
          tarjetaDeIndicador({ etiqueta: 'Términos en la lista', valor: todos }),
          tarjetaDeIndicador({
            etiqueta: 'Activos',
            valor: activos,
            tono: activos ? 'exito' : null,
          }),
          tarjetaDeIndicador({ etiqueta: 'Apagados', valor: apagados }),
        ],
        { zona: 'cifras-de-la-lista' },
      ),
    );
  }

  async function cargar(pagina = 0) {
    pintarEstado(zonaTabla, estadoDeCarga({ filas: 3, etiqueta: 'Cargando términos…' }));
    vaciar(zonaPaginacion);
    let respuesta;
    try {
      respuesta = await api.listar(filtros, pagina, fetchImpl);
    } catch (error) {
      pintarEstado(
        zonaTabla,
        estadoDeError({
          titulo: 'No pudimos cargar la lista negra',
          detalle:
            error instanceof ErrorDeListaNegra && error.detalle
              ? error.detalle
              : 'El servicio de moderación no respondió. Vuelve a intentarlo.',
          alReintentar: () => cargar(pagina),
        }),
      );
      return;
    }
    paginaActual = respuesta.pagina ?? pagina;
    const terminos = respuesta.contenido ?? [];
    const total = respuesta.total ?? terminos.length;
    const filtrada = Object.keys(filtros).length > 0;
    vaciar(zonaTabla).append(
      terminos.length === 0
        ? tablaDeDatos({
            titulo: 'Términos vetados',
            columnas: [{ clave: 'termino', titulo: 'Término', principal: true }],
            filas: [],
            vacio: filtrada
              ? {
                  titulo: 'Ningún término cumple el filtro',
                  detalle: 'Cambia o quita el filtro para ver el resto de la lista.',
                }
              : {
                  titulo: 'La lista negra está vacía',
                  detalle: 'Añade el primer término en el panel de arriba.',
                },
          })
        : tablaDeTerminos(terminos, {
            resumen: `${terminos.length} de ${total}`,
            editar,
            alternar,
            eliminar,
          }),
    );
    const totalPaginas = Math.ceil(total / TAMANO_DE_PAGINA);
    if (totalPaginas > 1) {
      const control = construirPaginacion(
        { paginaActual: Math.min(paginaActual, totalPaginas - 1), totalPaginas },
        (siguiente) => cargar(siguiente),
      );
      control.setAttribute('aria-label', 'Paginación de la lista negra');
      zonaPaginacion.append(control);
    }
  }

  function recargarTodo(pagina = paginaActual) {
    return Promise.all([cargar(pagina), cargarCifras()]);
  }

  function editar(termino) {
    limpiarAviso(zonaAviso);
    const formulario = formularioDeEdicion(termino);
    const error = formulario.elemento.querySelector('[data-zona="error-edicion"]');
    const guardar = h('button', {
      clase: 'boton boton--primario',
      texto: 'Guardar cambios',
      atributos: { type: 'button' },
      datos: { accion: 'guardar-termino' },
    });
    const { cerrar } = abrirEdicion({
      titulo: `Editar «${termino.termino}»`,
      subtitulo: etiquetaDeCategoria(termino.categoria),
      contenido: formulario.elemento,
      acciones: [guardar],
    });
    const enviar = async () => {
      error.hidden = true;
      const cuerpo = formulario.leer();
      if (!cuerpo.termino) {
        error.textContent = 'Escribe el término.';
        error.hidden = false;
        return;
      }
      if (cuerpo.motivo.length < MOTIVO_MINIMO) {
        error.textContent = `Escribe el motivo del cambio (al menos ${MOTIVO_MINIMO} caracteres).`;
        error.hidden = false;
        formulario.elemento.querySelector('[name="motivo"]')?.focus();
        return;
      }
      guardar.disabled = true;
      try {
        const editado = await api.editar(termino.termino, cuerpo, fetchImpl);
        cerrar?.();
        await recargarTodo();
        contarExito(
          'Término actualizado',
          `«${editado?.termino ?? cuerpo.termino}» · ${etiquetaDeCategoria(editado?.categoria ?? cuerpo.categoria)} · ${etiquetaDeModo(editado?.modo ?? cuerpo.modo).toLowerCase()}.`,
        );
      } catch (problema) {
        error.textContent =
          problema instanceof ErrorDeListaNegra && problema.detalle
            ? problema.detalle
            : 'No pudimos contactar con el servicio de moderación. Vuelve a intentarlo.';
        error.hidden = false;
      } finally {
        guardar.disabled = false;
      }
    };
    guardar.addEventListener('click', enviar);
    formulario.elemento.addEventListener('submit', (evento) => {
      evento.preventDefault();
      enviar();
    });
  }

  /**
   * El motivo de una acción, o null si se canceló. Un `confirmar` que solo
   * dice sí o no (sin motivo) deja que el servicio ponga uno descriptivo.
   */
  async function motivoPara(termino, accion) {
    const respuesta = await confirmar(termino, accion);
    if (typeof respuesta === 'string') {
      return respuesta.trim() || null;
    }
    return respuesta ? '' : null;
  }

  async function alternar(termino) {
    limpiarAviso(zonaAviso);
    const motivo = await motivoPara(termino, termino.activo ? 'apagar' : 'activar');
    if (motivo === null) {
      return;
    }
    try {
      await api.editar(
        termino.termino,
        {
          termino: termino.termino,
          activo: !termino.activo,
          ...(motivo ? { motivo } : {}),
        },
        fetchImpl,
      );
      await recargarTodo();
      contarExito(
        termino.activo ? 'Término apagado' : 'Término activado',
        termino.activo
          ? `«${termino.termino}» deja de aplicarse, sin borrarse.`
          : `«${termino.termino}» vuelve a aplicarse.`,
      );
    } catch (error) {
      contarError(error, 'cambiar el estado del término');
    }
  }

  async function eliminar(termino) {
    limpiarAviso(zonaAviso);
    const motivo = await motivoPara(termino, 'quitar');
    if (motivo === null) {
      return;
    }
    try {
      await api.eliminar(termino.termino, motivo || null, fetchImpl);
      await recargarTodo();
      contarExito('Término quitado', `«${termino.termino}» ya no está en la lista negra.`);
    } catch (error) {
      contarError(error, 'quitar el término');
    }
  }

  formAlta.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    limpiarAviso(zonaAviso);
    const cuerpo = altaDesde(new FormData(formAlta));
    if (!cuerpo.termino) {
      pintarAviso(zonaAviso, {
        tono: 'advertencia',
        titulo: 'Escribe el término que quieres vetar',
      });
      formAlta.querySelector('[name="termino"]')?.focus();
      return;
    }
    if (!cuerpo.categoria) {
      pintarAviso(zonaAviso, { tono: 'advertencia', titulo: 'Elige la categoría del término' });
      formAlta.querySelector('[name="categoria"]')?.focus();
      return;
    }
    const botonAlta = formAlta.querySelector('[type="submit"]');
    botonAlta.disabled = true;
    try {
      const creado = await api.agregar(cuerpo, fetchImpl);
      formAlta.querySelector('[name="termino"]').value = '';
      await recargarTodo(0);
      contarExito(
        'Término agregado',
        `«${creado?.termino ?? cuerpo.termino}» (${etiquetaDeCategoria(creado?.categoria ?? cuerpo.categoria)}) ya se aplica.`,
      );
    } catch (error) {
      contarError(error, 'agregar el término');
    } finally {
      botonAlta.disabled = false;
    }
  });

  /*
   * CA-03: un usuario sin permisos que intente entrar a esta ruta debe ser
   * redirigido a la pantalla principal. El interceptor compartido (comun/) ya
   * dispara este evento en cada 403; aquí solo se escucha. El destino es el
   * menú (no `/`, que el borde manda al login) y se resuelve contra la URL de
   * este módulo para acertar servido por el borde o por `npm run dev`.
   */
  globalThis.window?.addEventListener('nexus:rbac-forbidden', () => {
    window.location.href = new URL('../../cuentas/index.html', import.meta.url).href;
  });

  cargar(0);
  cargarCifras();
  return { cargar, cargarCifras };
}
