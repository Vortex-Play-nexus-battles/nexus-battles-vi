/**
 * Panel de administración de la lista negra de términos prohibidos
 * (HU-ADM-002; 7.1.1 y 7.3.2 del documento del curso: «lista negra
 * actualizable»).
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

import { fetchWithHttpErrorInterceptor } from '../../comun/interceptors/http-error.interceptor.js';
import { construirPaginacion } from '../../comun/paginacion.js';
import { limpiarAviso, pintarAviso, tonoPorEstado } from '../../comun/ui/aviso.js';
import { confirmar as confirmarConDialogo, pedirTexto } from '../../comun/ui/dialogo.js';
import { h, nodo, vaciar } from '../../comun/ui/dom.js';
import { distintivo } from '../../comun/ui/distintivo.js';
import {
  estadoDeCarga,
  estadoDeError,
  estadoVacio,
  pintarEstado,
} from '../../comun/ui/estado-vista.js';

export const BASE_URL = '/api/v1/lista-negra/terminos';

/** RNF-USA-001: paginación de 16 elementos en los listados. */
export const TAMANO_DE_PAGINA = 16;

/** Categorías del contrato, con el texto que ve el moderador (7.1.1). */
export const CATEGORIAS = Object.freeze({
  OFENSIVO: 'Ofensivo',
  MARCA: 'Marca registrada',
  CELEBRIDAD: 'Celebridad',
  POLITICO: 'Político',
  DIRIGENTE: 'Dirigente',
  OTRO: 'Otro',
});

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

/** Fecha corta legible, o vacío si no hay. */
function fecha(valor) {
  if (!valor) {
    return '';
  }
  const instante = new Date(valor);
  return Number.isNaN(instante.getTime()) ? '' : instante.toLocaleDateString('es-CO');
}

/**
 * La línea de metadatos de un término: forma normalizada, autor y fecha.
 *
 * @param {{normalizado?: string, creadoPor?: string|null, creadoEn?: string}} termino
 * @returns {string}
 */
export function descripcionDe(termino) {
  const partes = [`Se compara como «${termino.normalizado ?? ''}»`];
  partes.push(termino.creadoPor ? `alta de ${termino.creadoPor}` : 'autor desconocido');
  const cuando = fecha(termino.creadoEn);
  if (cuando) {
    partes.push(cuando);
  }
  return partes.join(' · ');
}

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
  eliminar: (termino, f) => pedir(caminoDe(termino), { method: 'DELETE' }, f),
};

/* ---- DOM ---- */

/**
 * Fila de un término con sus acciones.
 *
 * @param {object} termino `TerminoResponse` del contrato
 * @param {{editar?: Function, alternar?: Function, eliminar?: Function}} [acciones]
 * @returns {HTMLLIElement}
 */
export function filaDeTermino(termino, { editar, alternar, eliminar } = {}) {
  const fila = h('li', {
    clase: 'lista-terminos__fila',
    datos: { termino: termino.termino, activo: String(Boolean(termino.activo)) },
  });

  const datos = h('div', { clase: 'pila pila--ajustada' });
  datos.append(h('span', { clase: 'lista-terminos__termino', texto: termino.termino }));
  const etiquetas = h('div', { clase: 'fila fila--acciones' });
  etiquetas.append(
    distintivo(etiquetaDeCategoria(termino.categoria)),
    distintivo(etiquetaDeModo(termino.modo)),
    distintivo(termino.activo ? 'Activo' : 'Inactivo', termino.activo ? 'activo' : 'no-disponible'),
  );
  datos.append(etiquetas, nodo('p', 't-meta', descripcionDe(termino)));
  fila.append(datos);

  const botones = h('div', { clase: 'lista-terminos__acciones' });
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
  botones.append(
    boton('Editar', 'boton--secundario', 'editar', `Editar el término ${termino.termino}`, editar),
    boton(
      termino.activo ? 'Desactivar' : 'Activar',
      'boton--secundario',
      'alternar',
      `${termino.activo ? 'Desactivar' : 'Activar'} el término ${termino.termino}`,
      alternar,
    ),
    boton(
      'Eliminar',
      'boton--peligro',
      'eliminar',
      `Eliminar el término ${termino.termino}`,
      eliminar,
    ),
  );
  fila.append(botones);
  return fila;
}

/**
 * Monta la vista sobre un documento con las zonas `[data-zona=...]` de
 * `lista-negra-admin.html`.
 *
 * UXC-7 — confirmar y preguntar son los diálogos del kit (con su etiqueta, se
 * leen con lector de pantalla y devuelven el foco), no `window.confirm()` ni
 * `window.prompt()`. Se pueden inyectar; lo que devuelvan se espera con
 * `await`, así que sirven tanto una función síncrona como una promesa.
 *
 * @param {ParentNode} raiz
 * @param {{fetchImpl?: Function,
 *          confirmar?: (texto: string) => boolean|Promise<boolean>,
 *          preguntar?: (texto: string, valor: string) => string|null|Promise<string|null>}} [opciones]
 * @returns {{cargar: (pagina?: number) => Promise<void>}}
 */
export function montarListaNegra(
  raiz,
  {
    fetchImpl,
    confirmar = (texto) =>
      confirmarConDialogo({
        titulo: texto,
        mensaje: 'Dejará de filtrarse desde este momento. Puedes volver a añadirlo cuando quieras.',
        textoConfirmar: 'Quitar término',
      }),
    preguntar = (texto, valor) =>
      pedirTexto({
        titulo: texto,
        etiqueta: 'Término',
        valor,
        pista: 'La lista negra filtra apodos, comentarios, chat y mensajes privados.',
        textoConfirmar: 'Guardar término',
      }),
  } = {},
) {
  const zonaAviso = raiz.querySelector('[data-zona="aviso"]');
  const zonaLista = raiz.querySelector('[data-zona="terminos"]');
  const zonaPaginacion = raiz.querySelector('[data-zona="paginacion"]');
  const formAlta = raiz.querySelector('[data-zona="alta"]');
  const formFiltros = raiz.querySelector('[data-zona="filtros"]');

  let filtros = {};
  let paginaActual = 0;

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

  async function cargar(pagina = 0) {
    pintarEstado(zonaLista, estadoDeCarga({ filas: 3, etiqueta: 'Cargando términos…' }));
    vaciar(zonaPaginacion);
    let respuesta;
    try {
      respuesta = await api.listar(filtros, pagina, fetchImpl);
    } catch (error) {
      pintarEstado(
        zonaLista,
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
    if (terminos.length === 0) {
      const filtrada = Object.keys(filtros).length > 0;
      pintarEstado(
        zonaLista,
        estadoVacio({
          titulo: filtrada ? 'Ningún término cumple el filtro' : 'La lista negra está vacía',
          detalle: filtrada
            ? 'Cambia o quita el filtro para ver el resto de la lista.'
            : 'Mientras esté vacía, el registro solo aplica sus validaciones de formato. Añade un término arriba.',
        }),
      );
      return;
    }
    const lista = h('ul', {
      clase: 'lista-terminos',
      atributos: { 'aria-label': 'Términos vetados' },
    });
    for (const termino of terminos) {
      lista.append(filaDeTermino(termino, { editar, alternar, eliminar }));
    }
    vaciar(zonaLista).append(lista);
    zonaLista.hidden = false;
    const totalPaginas = Math.ceil((respuesta.total ?? terminos.length) / TAMANO_DE_PAGINA);
    const control = construirPaginacion(
      { paginaActual: Math.min(paginaActual, Math.max(totalPaginas - 1, 0)), totalPaginas },
      (siguiente) => cargar(siguiente),
    );
    control.setAttribute('aria-label', 'Paginación de la lista negra');
    zonaPaginacion.append(control);
  }

  async function editar(termino) {
    limpiarAviso(zonaAviso);
    const nuevo = await preguntar(`Editar «${termino.termino}»`, termino.termino);
    if (!nuevo || !nuevo.trim() || nuevo.trim() === termino.termino) {
      return;
    }
    try {
      const editado = await api.editar(termino.termino, { termino: nuevo.trim() }, fetchImpl);
      await cargar(paginaActual);
      contarExito('Término actualizado', `Ahora es «${editado?.termino ?? nuevo.trim()}».`);
    } catch (error) {
      contarError(error, 'editar el término');
    }
  }

  async function alternar(termino) {
    limpiarAviso(zonaAviso);
    try {
      await api.editar(
        termino.termino,
        { termino: termino.termino, activo: !termino.activo },
        fetchImpl,
      );
      await cargar(paginaActual);
      contarExito(
        termino.activo ? 'Término desactivado' : 'Término activado',
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
    if (!(await confirmar(`¿Quitar «${termino.termino}» de la lista negra?`))) {
      return;
    }
    try {
      await api.eliminar(termino.termino, fetchImpl);
      await cargar(paginaActual);
      contarExito('Término eliminado', `«${termino.termino}» ya no está en la lista negra.`);
    } catch (error) {
      contarError(error, 'eliminar el término');
    }
  }

  formAlta?.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    limpiarAviso(zonaAviso);
    const cuerpo = altaDesde(new FormData(formAlta));
    if (!cuerpo.termino) {
      pintarAviso(zonaAviso, {
        tono: 'advertencia',
        titulo: 'Escribe el término que quieres prohibir',
      });
      return;
    }
    const botonAlta = formAlta.querySelector('[type="submit"]');
    botonAlta.disabled = true;
    try {
      const creado = await api.agregar(cuerpo, fetchImpl);
      formAlta.querySelector('[name="termino"]').value = '';
      await cargar(0);
      contarExito(
        'Término agregado',
        `«${creado?.termino ?? cuerpo.termino}» (${etiquetaDeCategoria(creado?.categoria)}) ya se aplica.`,
      );
    } catch (error) {
      contarError(error, 'agregar el término');
    } finally {
      botonAlta.disabled = false;
    }
  });

  formFiltros?.addEventListener('submit', (evento) => {
    evento.preventDefault();
    const datos = new FormData(formFiltros);
    filtros = {};
    for (const clave of ['categoria', 'activo', 'buscar']) {
      const valor = String(datos.get(clave) ?? '').trim();
      if (valor) {
        filtros[clave] = valor;
      }
    }
    cargar(0);
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
  return { cargar };
}
