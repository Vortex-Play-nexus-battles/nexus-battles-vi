/**
 * Parámetros de plataforma — HU-ADM-001 sobre admin-parametros.yaml, con el
 * kit de la consola (SA-8, punto 22 del informe de revisión).
 *
 * Lo que pidió el informe y cómo queda:
 *   - sin la introducción de trabajo ni las referencias internas («Charter»,
 *     «RNF-…», «pendiente del PO», el origen de cada valor): cada parámetro se
 *     lee con su nombre de producto y una línea que dice para qué sirve
 *     (`comun/ui/admin/parametros.js`); los que no se pueden tocar dicen
 *     «Fijo», sin más;
 *   - una tabla por área con lo que hace falta para decidir: valor vigente,
 *     lo que admite, el último cambio y quién lo hizo;
 *   - cambiar es una acción con su propio panel: el valor nuevo con lo que
 *     admite, el motivo obligatorio, cuándo se aplica y, antes de guardar, la
 *     frase exacta de lo que va a pasar («Pasará de 30 días a 45 días, de
 *     inmediato»). El historial, en otro panel, con nombres y sin uid.
 *
 * El servicio decide: un valor fuera de rango, un parámetro fijo o un permiso
 * que falta llegan como problem details y se cuentan por su `motivo`, nunca
 * con el texto del servidor (que nombra la clave técnica).
 *
 * Debajo, los cambios en las políticas de uso (SA-11, §7.3.7), en su panel:
 * `politicas-de-uso.js`.
 */

import { fetchWithHttpErrorInterceptor } from '../../comun/interceptors/http-error.interceptor.js';
import { limpiarAviso, pintarAviso } from '../../comun/ui/aviso.js';
import { barraDeFiltros } from '../../comun/ui/admin/barra-de-filtros.js';
import { distintivoDeEstado } from '../../comun/ui/admin/estado.js';
import { rejillaDeIndicadores, tarjetaDeIndicador } from '../../comun/ui/admin/indicador.js';
import { abrirPanelLateral, listaDeDatos } from '../../comun/ui/admin/panel-lateral.js';
import {
  conUnidad,
  fichaDeParametro,
  GRUPOS_DE_PARAMETROS,
  opcionLegible,
  valorLegible,
} from '../../comun/ui/admin/parametros.js';
import { tablaDeDatos } from '../../comun/ui/admin/tabla-de-datos.js';
import { campo } from '../../comun/ui/campo.js';
import { h, vaciar } from '../../comun/ui/dom.js';
import { lineaDeTiempo } from '../../comun/ui/linea-de-tiempo.js';
import {
  estadoDeCarga,
  estadoDeError,
  estadoVacio,
  pintarEstado,
} from '../../comun/ui/estado-vista.js';
import { fechaHora, numero } from '../../comun/ui/formato.js';
import { encabezadoDePagina } from '../../comun/ui/pagina.js';
import { respaldoPorEstado, tituloDelServidor } from '../../comun/ui/texto-de-fallo.js';
import { consultar } from '../consola/cliente-consola.js';
import { panel } from '../consola/panel.js';
import { directorioDeTitulares } from '../consola/titulares.js';
import { montarPoliticasDeUso } from './politicas-de-uso.js';

export const ROLES_DE_ADMINISTRACION = Object.freeze(['ADMINISTRADOR', 'SUPER_ADMINISTRADOR']);

/** Lo que admite el motivo de un cambio (admin-parametros.yaml, `CambioDeParametro`). */
export const MOTIVO_MINIMO = 3;
export const MOTIVO_MAXIMO = 500;

/** Lo que admite un parámetro de texto libre (el servicio lo rechaza por encima). */
const TEXTO_MAXIMO = 200;

/** Estados de un parámetro, para filtrar y para contar. */
export const ESTADOS_DE_PARAMETRO = Object.freeze({
  EDITABLE: 'editable',
  FIJO: 'fijo',
  SIN_DEFINIR: 'sin-definir',
  PROGRAMADO: 'programado',
});

const OPCIONES_DE_ESTADO = Object.freeze([
  [ESTADOS_DE_PARAMETRO.EDITABLE, 'Se pueden cambiar'],
  [ESTADOS_DE_PARAMETRO.FIJO, 'Fijos'],
  [ESTADOS_DE_PARAMETRO.SIN_DEFINIR, 'Sin definir'],
  [ESTADOS_DE_PARAMETRO.PROGRAMADO, 'Con cambio programado'],
]);

function baseDeApi() {
  const meta = globalThis.document?.querySelector?.('meta[name="nexus-api-base"]');
  return String(meta?.content ?? '').replace(/\/+$/, '');
}

/** Un rechazo del servicio, decidido por su `motivo`. */
export class ErrorDeParametros extends Error {
  constructor(problema, estado) {
    super(respaldoPorEstado(estado));
    this.name = 'ErrorDeParametros';
    this.estado = estado;
    this.titulo = tituloDelServidor(problema, 'No se pudo completar');
    this.motivo = problema?.motivo ?? null;
  }
}

async function pedir(ruta, opciones = {}, fetchImpl = fetchWithHttpErrorInterceptor) {
  const respuesta = await fetchImpl(`${baseDeApi()}${ruta}`, {
    ...opciones,
    headers: { Accept: 'application/json', ...(opciones.headers ?? {}) },
  });
  if (respuesta.ok) {
    return respuesta.json();
  }
  let problema = null;
  try {
    problema = await respuesta.json();
  } catch {
    problema = null;
  }
  throw new ErrorDeParametros(problema, respuesta.status);
}

export const api = {
  listar: (f) => pedir('/api/v1/parametros', {}, f),
  cambiar: (clave, cuerpo, f) =>
    pedir(
      `/api/v1/parametros/${encodeURIComponent(clave)}`,
      {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(cuerpo),
      },
      f,
    ),
  historial: (clave, f) =>
    pedir(`/api/v1/parametros/${encodeURIComponent(clave)}/historial`, {}, f),
};

/* =========================================================================
   Presentación (pura, probada)
   ========================================================================= */

const vacio = (valor) => valor === null || valor === undefined || String(valor).trim() === '';

/** @param {{inalterable?: boolean, valor?: string|null, valorProgramado?: string|null}} parametro */
export function estadoDe(parametro) {
  if (parametro?.inalterable) {
    return ESTADOS_DE_PARAMETRO.FIJO;
  }
  if (!vacio(parametro?.valorProgramado)) {
    return ESTADOS_DE_PARAMETRO.PROGRAMADO;
  }
  if (vacio(parametro?.valor)) {
    return ESTADOS_DE_PARAMETRO.SIN_DEFINIR;
  }
  return ESTADOS_DE_PARAMETRO.EDITABLE;
}

/** Se puede dejar sin valor: lo dice su ficha, o ya no tiene ninguno. */
export function admiteVacio(parametro) {
  return Boolean(fichaDeParametro(parametro).admiteVacio) || vacio(parametro?.valor);
}

/** Las opciones cerradas de un parámetro (texto con opciones, o sí/no). */
export function opcionesDe(parametro) {
  if (Array.isArray(parametro?.opciones) && parametro.opciones.length > 0) {
    return parametro.opciones.map(String);
  }
  if (parametro?.tipo === 'BOOLEANO') {
    return ['true', 'false'];
  }
  return null;
}

/**
 * Lo que admite un parámetro, en palabras: «Entre 1 y 365 días», «Se
 * devuelven · Se pierden», «Valor fijo».
 *
 * @param {object} parametro `Parametro` de admin-parametros.yaml
 * @returns {string}
 */
export function rangoLegible(parametro) {
  if (parametro?.inalterable) {
    return 'Valor fijo';
  }
  const opciones = opcionesDe(parametro);
  if (opciones) {
    return opciones.map(opcionLegible).join(' · ');
  }
  if (parametro?.tipo === 'TEXTO') {
    return `Texto de hasta ${TEXTO_MAXIMO} caracteres`;
  }
  const minimo = parametro?.minimo;
  const maximo = parametro?.maximo;
  const con = (cantidad) => conUnidad(numeroLegible(cantidad), parametro?.unidad);
  if (minimo !== null && minimo !== undefined && maximo !== null && maximo !== undefined) {
    return `Entre ${numeroLegible(minimo)} y ${con(maximo)}`;
  }
  if (minimo !== null && minimo !== undefined) {
    return `Desde ${con(minimo)}`;
  }
  if (maximo !== null && maximo !== undefined) {
    return `Hasta ${con(maximo)}`;
  }
  return parametro?.tipo === 'ENTERO' ? 'Un número entero' : 'Un número';
}

/** 1000000 → «1.000.000», 0.01 → «0,01»: las cifras de los rangos, como se leen. */
function numeroLegible(cantidad) {
  const valor = Number(cantidad);
  if (!Number.isFinite(valor)) {
    return String(cantidad);
  }
  return numero(valor, Number.isInteger(valor) ? 0 : 2);
}

/**
 * El valor que se escribió, normalizado como lo guarda el servicio (`null`
 * para «sin valor»), o el motivo por el que no vale.
 *
 * @param {object} parametro
 * @param {string} texto lo escrito o elegido
 * @returns {{valor: string|null, error: string|null}}
 */
export function validarValor(parametro, texto) {
  const escrito = String(texto ?? '').trim();
  if (escrito === '') {
    return admiteVacio(parametro)
      ? { valor: null, error: null }
      : { valor: null, error: 'Escribe el valor nuevo.' };
  }
  const opciones = opcionesDe(parametro);
  if (opciones) {
    return opciones.includes(escrito)
      ? { valor: escrito, error: null }
      : { valor: null, error: `Elige una de las opciones: ${rangoLegible(parametro)}.` };
  }
  if (parametro?.tipo === 'TEXTO') {
    return escrito.length <= TEXTO_MAXIMO
      ? { valor: escrito, error: null }
      : { valor: null, error: `Admite hasta ${TEXTO_MAXIMO} caracteres.` };
  }
  // Una coma decimal se acepta: es como se escribe aquí.
  const normalizado = escrito.replace(',', '.');
  const esEntero = parametro?.tipo === 'ENTERO';
  const formato = esEntero ? /^-?\d+$/ : /^-?\d+(\.\d+)?$/;
  if (!formato.test(normalizado)) {
    return {
      valor: null,
      error: esEntero ? 'Escribe un número entero.' : 'Escribe un número.',
    };
  }
  const cifra = Number(normalizado);
  const minimo = parametro?.minimo;
  const maximo = parametro?.maximo;
  if (
    (minimo !== null && minimo !== undefined && cifra < Number(minimo)) ||
    (maximo !== null && maximo !== undefined && cifra > Number(maximo))
  ) {
    return {
      valor: null,
      error: `Tiene que estar en el rango admitido: ${rangoLegible(parametro)}.`,
    };
  }
  return { valor: esEntero ? String(Math.trunc(cifra)) : String(cifra), error: null };
}

/**
 * La frase de lo que va a pasar, antes de guardar: «Pasará de 30 días a
 * 45 días, de inmediato.»
 *
 * @param {object} parametro
 * @param {string|null} valorNuevo ya validado
 * @param {Date|null} desde cuándo se aplica (`null`: ahora)
 * @returns {string}
 */
export function resumenDelCambio(parametro, valorNuevo, desde = null) {
  const antes = valorLegible(parametro);
  const despues = valorLegible({ valor: valorNuevo, unidad: parametro?.unidad });
  const cuando = desde ? `a partir del ${fechaHora(desde)}` : 'de inmediato';
  return `Pasará de ${antes.toLowerCase() === 'sin definir' ? 'no tener valor' : antes} a ${
    despues.toLowerCase() === 'sin definir' ? 'no tener valor' : despues
  }, ${cuando}.`;
}

/**
 * Filtra el catálogo por lo escrito (nombre, ayuda o clave), el área y el
 * estado.
 *
 * @param {object[]} catalogo
 * @param {{buscar?: string, area?: string, estado?: string}} filtros
 */
export function filtrarCatalogo(catalogo, { buscar = '', area = '', estado = '' } = {}) {
  const texto = buscar.trim().toLocaleLowerCase('es');
  return catalogo.filter((parametro) => {
    const ficha = fichaDeParametro(parametro);
    if (area && ficha.grupo !== area) {
      return false;
    }
    if (estado && estadoDe(parametro) !== estado) {
      return false;
    }
    if (!texto) {
      return true;
    }
    return [ficha.nombre, ficha.ayuda, parametro.clave]
      .join(' ')
      .toLocaleLowerCase('es')
      .includes(texto);
  });
}

/** Los parámetros por área, en el orden en que se leen las áreas. */
export function porArea(catalogo) {
  const grupos = new Map(GRUPOS_DE_PARAMETROS.map((grupo) => [grupo, []]));
  for (const parametro of catalogo) {
    const grupo = fichaDeParametro(parametro).grupo;
    if (!grupos.has(grupo)) {
      grupos.set(grupo, []);
    }
    grupos.get(grupo).push(parametro);
  }
  return [...grupos.entries()].filter(([, parametros]) => parametros.length > 0);
}

/** Qué decir cuando el servicio rechaza un cambio, por su `motivo`. */
export function textoDelRechazo(error, parametro) {
  switch (error?.motivo) {
    case 'VALOR_INVALIDO':
      return `Ese valor no está en el rango admitido: ${rangoLegible(parametro)}.`;
    case 'INALTERABLE':
      return 'Este parámetro tiene un valor fijo y no se puede cambiar.';
    case 'PERMISO_INSUFICIENTE':
      return 'Tu cuenta no puede cambiar parámetros de la plataforma.';
    case 'SOLICITUD_INVALIDA':
      return 'Revisa el motivo y la fecha de aplicación e inténtalo de nuevo.';
    case 'NO_ENCONTRADO':
      return 'Este parámetro ya no existe. Recarga la página.';
    default:
      return 'No pudimos guardar el cambio. Vuelve a intentarlo en un momento.';
  }
}

/* =========================================================================
   Vista
   ========================================================================= */

/**
 * La cuenta con la que el propio servicio registra los cambios que no hizo
 * una persona (las migraciones que aplican una decisión ya tomada).
 */
export const UID_DEL_SISTEMA = '00000000-0000-0000-0000-000000000000';

/** Quién hizo un cambio: su apodo, «Sistema» si fue automático, o nada si no se sabe. */
function autorDe(uid, titulares) {
  if (!uid) {
    return null;
  }
  if (uid === UID_DEL_SISTEMA) {
    return 'Sistema';
  }
  return titulares?.de(uid)?.apodo ?? null;
}

function celdaDoble(principal, secundaria, datos = {}) {
  return h('span', {
    clase: 'celda-doble',
    datos,
    hijos: [
      typeof principal === 'string' ? h('span', { texto: principal }) : principal,
      secundaria ? h('span', { clase: 't-meta', texto: secundaria }) : null,
    ],
  });
}

/** La celda del valor vigente, con el cambio programado debajo si lo hay. */
function celdaDeValor(parametro) {
  const programado = vacio(parametro.valorProgramado)
    ? null
    : `Pasará a ${valorLegible({ valor: parametro.valorProgramado, unidad: parametro.unidad })} el ${fechaHora(parametro.vigenteDesde)}`;
  const caja = celdaDoble(valorLegible(parametro), programado, { campo: 'valor' });
  if (programado) {
    caja.lastChild.dataset.campo = 'programado';
  }
  return caja;
}

/** La tabla de un área. */
export function tablaDeArea(
  area,
  parametros,
  { administra, titulares, alCambiar, alVerHistorial },
) {
  return tablaDeDatos({
    titulo: area,
    resumen: `${numero(parametros.length)} ${parametros.length === 1 ? 'parámetro' : 'parámetros'}`,
    claveDeFila: (parametro) => parametro.clave,
    columnas: [
      {
        clave: 'nombre',
        titulo: 'Parámetro',
        principal: true,
        valor: (parametro) => {
          const ficha = fichaDeParametro(parametro);
          return celdaDoble(ficha.nombre, ficha.ayuda);
        },
        ordenarPor: (parametro) => fichaDeParametro(parametro).nombre,
      },
      {
        clave: 'valor',
        titulo: 'Valor vigente',
        valor: celdaDeValor,
        ordenarPor: (parametro) => Number(parametro.valor) || 0,
      },
      {
        clave: 'admite',
        titulo: 'Admite',
        valor: (parametro) =>
          parametro.inalterable
            ? h('span', {
                datos: { campo: 'fijo' },
                hijos: [distintivoDeEstado({ texto: 'Fijo', tono: 'neutro' })],
              })
            : rangoLegible(parametro),
      },
      {
        clave: 'actualizadoEn',
        titulo: 'Último cambio',
        tipo: 'fecha',
        ordenable: true,
        valor: (parametro) =>
          parametro.actualizadoEn
            ? celdaDoble(
                fechaHora(parametro.actualizadoEn),
                autorDe(parametro.actualizadoPor, titulares)
                  ? `Por ${autorDe(parametro.actualizadoPor, titulares)}`
                  : null,
              )
            : h('span', { clase: 't-meta', texto: 'Valor inicial' }),
      },
      {
        clave: 'acciones',
        titulo: 'Acciones',
        tipo: 'accion',
        valor: (parametro) => {
          if (!administra) {
            return null;
          }
          const botones = [];
          if (!parametro.inalterable) {
            const cambiar = h('button', {
              clase: 'boton boton--secundario boton--pequeno',
              texto: 'Cambiar',
              atributos: {
                type: 'button',
                'aria-label': `Cambiar ${fichaDeParametro(parametro).nombre}`,
              },
              datos: { accion: 'cambiar' },
            });
            cambiar.addEventListener('click', () => alCambiar?.(parametro));
            botones.push(cambiar);
          }
          if (parametro.version > 1 || parametro.actualizadoEn) {
            const historial = h('button', {
              clase: 'boton boton--contorno boton--pequeno',
              texto: 'Historial',
              atributos: {
                type: 'button',
                'aria-label': `Historial de ${fichaDeParametro(parametro).nombre}`,
              },
              datos: { accion: 'historial' },
            });
            historial.addEventListener('click', () => alVerHistorial?.(parametro));
            botones.push(historial);
          }
          return botones.length ? botones : null;
        },
      },
    ],
    filas: parametros,
  });
}

/**
 * El historial de un parámetro como línea de tiempo: cuándo, quién, el cambio
 * en palabras y su motivo. En el panel lateral se lee de arriba abajo, sin
 * columnas que no caben.
 */
export function historialDeParametro(parametro, versiones, titulares, ahora = Date.now()) {
  if (!versiones.length) {
    return estadoVacio({
      titulo: 'Sin cambios',
      detalle: 'Conserva el valor con el que empezó la plataforma.',
    });
  }
  const unidad = parametro?.unidad;
  return lineaDeTiempo(
    versiones.map((version, indice) => {
      const aplicada = Date.parse(version.vigenteDesde);
      const hecha = Date.parse(version.cambiadoEn);
      const programada = Number.isFinite(aplicada) && aplicada > hecha + 1000;
      return {
        cuando: version.cambiadoEn,
        titulo: `${valorLegible({ valor: version.valorAnterior, unidad })} → ${valorLegible({
          valor: version.valorNuevo,
          unidad,
        })}`,
        detalle: [
          version.motivo,
          programada
            ? `${aplicada > ahora ? 'Se aplicará' : 'Se aplicó'} el ${fechaHora(version.vigenteDesde)}`
            : null,
        ]
          .filter(Boolean)
          .map((frase) => (/[.!?]$/.test(frase) ? frase : `${frase}.`))
          .join(' '),
        actor: `Por ${autorDe(version.cambiadoPor, titulares) ?? 'una cuenta sin apodo'}`,
        tono: indice === 0 ? 'info' : 'neutro',
        futuro: programada && aplicada > ahora,
        datos: { version: String(version.version) },
      };
    }),
    { etiqueta: 'Historial de cambios' },
  );
}

/** El formulario del panel «Cambiar». */
function formularioDeCambio(parametro) {
  const opciones = opcionesDe(parametro);
  const ficha = fichaDeParametro(parametro);
  const formulario = h('form', {
    clase: 'pila pila--compacta',
    datos: { zona: 'cambio' },
    atributos: { novalidate: true },
  });
  const valor = opciones
    ? campo({
        nombre: 'valor',
        etiqueta: 'Valor nuevo',
        valor: String(parametro.valor ?? opciones[0]),
        opciones: [
          ...(admiteVacio(parametro) ? [{ valor: '', texto: 'Sin valor' }] : []),
          ...opciones.map((opcion) => ({ valor: opcion, texto: opcionLegible(opcion) })),
        ],
      })
    : campo({
        nombre: 'valor',
        etiqueta: parametro.unidad
          ? `Valor nuevo (${conUnidad('', parametro.unidad).trim()})`
          : 'Valor nuevo',
        valor: vacio(parametro.valor) ? '' : String(parametro.valor),
        pista: admiteVacio(parametro)
          ? `${rangoLegible(parametro)}. Déjalo vacío para quitar el valor.`
          : `${rangoLegible(parametro)}.`,
        atributos: {
          inputmode: parametro.tipo === 'ENTERO' ? 'numeric' : 'decimal',
          autocomplete: 'off',
        },
      });
  const motivo = campo({
    nombre: 'motivo',
    etiqueta: 'Motivo del cambio',
    requerido: true,
    multilinea: true,
    pista: `Queda en el historial y en la auditoría. Entre ${MOTIVO_MINIMO} y ${MOTIVO_MAXIMO} caracteres.`,
    atributos: { minlength: MOTIVO_MINIMO, maxlength: MOTIVO_MAXIMO, rows: 3 },
  });
  const desde = campo({
    nombre: 'vigenteDesde',
    etiqueta: 'Aplicar a partir de',
    tipo: 'datetime-local',
    pista: 'Vacío: se aplica en cuanto guardes.',
  });
  const resumen = h('p', {
    clase: 'aviso aviso--info',
    datos: { zona: 'resumen-del-cambio' },
    atributos: { 'aria-live': 'polite' },
  });
  formulario.append(valor.elemento, motivo.elemento, desde.elemento, resumen);

  /** Lee y valida; pinta cada error en su campo y la frase del resumen. */
  function leer() {
    const datos = new FormData(formulario);
    const escrito = validarValor(parametro, String(datos.get('valor') ?? ''));
    const textoMotivo = String(datos.get('motivo') ?? '').trim();
    const textoDesde = String(datos.get('vigenteDesde') ?? '').trim();
    const cuando = textoDesde ? new Date(textoDesde) : null;

    let errorDesde = null;
    if (cuando && (Number.isNaN(cuando.getTime()) || cuando.getTime() <= Date.now())) {
      errorDesde = 'Elige una fecha y hora posteriores a ahora, o déjalo vacío.';
    }
    let errorMotivo = null;
    if (textoMotivo.length < MOTIVO_MINIMO) {
      errorMotivo = `Escribe el motivo del cambio (al menos ${MOTIVO_MINIMO} caracteres).`;
    }
    let errorValor = escrito.error;
    if (
      !errorValor &&
      escrito.valor === (vacio(parametro.valor) ? null : String(parametro.valor)) &&
      !cuando
    ) {
      errorValor = `Ese ya es el valor vigente (${valorLegible(parametro)}).`;
    }
    return {
      cuerpo: {
        valor: escrito.valor,
        motivo: textoMotivo,
        ...(cuando && !errorDesde ? { vigenteDesde: cuando.toISOString() } : {}),
      },
      errores: { valor: errorValor, motivo: errorMotivo, vigenteDesde: errorDesde },
      resumen: errorValor
        ? null
        : resumenDelCambio(parametro, escrito.valor, errorDesde ? null : cuando),
    };
  }

  function pintarResumen() {
    const { resumen: frase } = leer();
    resumen.textContent = frase ?? `${ficha.nombre}: ${valorLegible(parametro)} ahora.`;
  }
  formulario.addEventListener('input', pintarResumen);
  formulario.addEventListener('change', pintarResumen);
  pintarResumen();

  return {
    elemento: formulario,
    leer,
    marcar(errores) {
      valor.marcarError(errores.valor ?? null);
      motivo.marcarError(errores.motivo ?? null);
      desde.marcarError(errores.vigenteDesde ?? null);
      const primero = ['valor', 'motivo', 'vigenteDesde'].find((nombre) => errores[nombre]);
      if (primero) {
        formulario.querySelector(`[name="${primero}"]`)?.focus();
      }
      return Boolean(primero);
    },
  };
}

/**
 * Monta la vista sobre sus zonas: `encabezado`, `aviso`, `indicadores` y
 * `catalogo` (la búsqueda, los filtros y una tabla por área).
 *
 * @param {ParentNode} raiz
 * @param {{rol?: string|null, fetchImpl?: Function, consultarApi?: typeof consultar,
 *          abrirPanel?: typeof abrirPanelLateral}} [opciones]
 */
export function montarParametros(
  raiz,
  { rol = null, fetchImpl, consultarApi = consultar, abrirPanel = abrirPanelLateral } = {},
) {
  const zonaEncabezado = raiz.querySelector('[data-zona="encabezado"]');
  const zonaAviso = raiz.querySelector('[data-zona="aviso"]');
  const zonaIndicadores = raiz.querySelector('[data-zona="indicadores"]');
  const zonaCatalogo = raiz.querySelector('[data-zona="catalogo"]');
  const administra = ROLES_DE_ADMINISTRACION.includes(rol);
  const titulares = administra ? directorioDeTitulares({ consultarApi }) : null;

  zonaEncabezado?.replaceChildren(
    encabezadoDePagina({
      titulo: 'Parámetros de plataforma',
      descripcion: 'Reglas, plazos y límites con los que funciona el juego.',
    }),
  );

  /** @type {object[]} */
  let catalogo = [];
  let filtros = { buscar: '', area: '', estado: '' };

  const barra = barraDeFiltros({
    etiqueta: 'Buscar y filtrar parámetros',
    busqueda: { nombre: 'buscar', etiqueta: 'Parámetro', placeholder: 'Busca un parámetro' },
    filtros: [
      {
        nombre: 'area',
        etiqueta: 'Área',
        opciones: GRUPOS_DE_PARAMETROS.map((grupo) => [grupo, grupo]),
        todos: 'Todas las áreas',
      },
      { nombre: 'estado', etiqueta: 'Estado', opciones: OPCIONES_DE_ESTADO, todos: 'Todos' },
    ],
    alAplicar: (valores) => {
      filtros = {
        buscar: String(valores.buscar ?? ''),
        area: String(valores.area ?? ''),
        estado: String(valores.estado ?? ''),
      };
      pintarCatalogo();
    },
  });
  const valores = panel({ id: 'parametros', titulo: 'Valores vigentes', ancho: 'completo' });
  valores.sello.dataset.estado = 'datos';
  const zonaTablas = h('div', { clase: 'pila parametros__tablas', datos: { zona: 'tablas' } });
  vaciar(valores.zona).append(barra.elemento, zonaTablas);
  // SA-11 (§7.3.7): los cambios en las políticas de uso, en su propio panel,
  // que se carga por su cuenta.
  const politicas = montarPoliticasDeUso({ administra, zonaAviso, fetchImpl, abrirPanel });
  zonaCatalogo?.replaceChildren(valores.elemento, politicas.elemento);

  function pintarCifras() {
    if (!zonaIndicadores) {
      return;
    }
    const cuenta = (estado) => catalogo.filter((p) => estadoDe(p) === estado).length;
    const sinDefinir = cuenta(ESTADOS_DE_PARAMETRO.SIN_DEFINIR);
    const programados = catalogo.filter((p) => !vacio(p.valorProgramado));
    const proximo = programados
      .map((p) => p.vigenteDesde)
      .filter((fecha) => Number.isFinite(Date.parse(fecha)))
      .sort((a, b) => Date.parse(a) - Date.parse(b))[0];
    zonaIndicadores.replaceChildren(
      rejillaDeIndicadores(
        [
          tarjetaDeIndicador({ etiqueta: 'Parámetros', valor: catalogo.length }),
          tarjetaDeIndicador({
            etiqueta: 'Se pueden cambiar',
            valor: catalogo.filter((p) => !p.inalterable).length,
            detalle: `${numero(cuenta(ESTADOS_DE_PARAMETRO.FIJO))} con valor fijo`,
          }),
          tarjetaDeIndicador({
            etiqueta: 'Sin definir',
            valor: sinDefinir,
            tono: sinDefinir > 0 ? 'advertencia' : 'exito',
            detalle: sinDefinir > 0 ? 'Aplican su comportamiento por omisión' : null,
          }),
          tarjetaDeIndicador({
            etiqueta: 'Cambios programados',
            valor: programados.length,
            detalle: proximo ? `El próximo, el ${fechaHora(proximo)}` : null,
          }),
        ],
        { zona: 'cifras-de-parametros' },
      ),
    );
  }

  function pintarCatalogo() {
    const visibles = filtrarCatalogo(catalogo, filtros);
    if (visibles.length === 0) {
      pintarEstado(
        zonaTablas,
        catalogo.length === 0
          ? estadoVacio({
              titulo: 'Todavía no hay parámetros',
              detalle: 'Aparecerán aquí en cuanto los servicios los publiquen.',
            })
          : estadoVacio({
              titulo: 'Ningún parámetro cumple el filtro',
              detalle: 'Cambia o quita el filtro para ver el resto.',
            }),
      );
      return;
    }
    vaciar(zonaTablas).append(
      ...porArea(visibles).map(([area, parametros]) =>
        h('section', {
          clase: 'pila pila--compacta',
          datos: { area },
          atributos: { 'aria-label': area },
          hijos: [
            h('h4', { clase: 't-subtitulo', texto: area }),
            tablaDeArea(area, parametros, {
              administra,
              titulares,
              alCambiar: cambiar,
              alVerHistorial: verHistorial,
            }),
          ],
        }),
      ),
    );
  }

  async function cargar() {
    pintarEstado(zonaTablas, estadoDeCarga({ filas: 4, etiqueta: 'Cargando parámetros…' }));
    try {
      catalogo = await api.listar(fetchImpl);
    } catch (error) {
      catalogo = [];
      zonaIndicadores?.replaceChildren();
      pintarEstado(
        zonaTablas,
        estadoDeError({
          titulo: 'No pudimos cargar los parámetros',
          detalle:
            error instanceof ErrorDeParametros
              ? error.message
              : 'El servicio de parámetros no respondió. Vuelve a intentarlo.',
          alReintentar: cargar,
        }),
      );
      return;
    }
    if (titulares) {
      await titulares.nombrar(
        catalogo.map((p) => p.actualizadoPor).filter((uid) => uid && uid !== UID_DEL_SISTEMA),
      );
    }
    pintarCifras();
    pintarCatalogo();
  }

  function cambiar(parametro) {
    limpiarAviso(zonaAviso);
    const ficha = fichaDeParametro(parametro);
    const formulario = formularioDeCambio(parametro);
    const error = h('p', {
      clase: 'aviso aviso--error',
      datos: { zona: 'error-del-cambio' },
      atributos: { role: 'alert' },
    });
    error.hidden = true;
    const guardar = h('button', {
      clase: 'boton boton--primario',
      texto: 'Guardar cambio',
      atributos: { type: 'button' },
      datos: { accion: 'guardar-cambio' },
    });
    const { cerrar } = abrirPanel({
      titulo: `Cambiar · ${ficha.nombre}`,
      subtitulo: ficha.ayuda,
      contenido: [
        listaDeDatos([
          ['Valor vigente', valorLegible(parametro)],
          ['Admite', rangoLegible(parametro)],
          [
            'Último cambio',
            parametro.actualizadoEn ? fechaHora(parametro.actualizadoEn) : 'Valor inicial',
          ],
        ]),
        formulario.elemento,
        error,
      ],
      acciones: [guardar],
    });

    async function enviar() {
      error.hidden = true;
      const { cuerpo, errores } = formulario.leer();
      if (formulario.marcar(errores)) {
        return;
      }
      guardar.disabled = true;
      try {
        const actualizado = await api.cambiar(parametro.clave, cuerpo, fetchImpl);
        cerrar?.();
        const programado = !vacio(actualizado?.valorProgramado);
        pintarAviso(zonaAviso, {
          tono: 'exito',
          titulo: programado ? 'Cambio programado' : 'Cambio aplicado',
          detalle: programado
            ? `${ficha.nombre} pasará a ${valorLegible({ valor: actualizado.valorProgramado, unidad: actualizado.unidad })} el ${fechaHora(actualizado.vigenteDesde)}.`
            : `${ficha.nombre}: ahora ${valorLegible(actualizado ?? {}).toLowerCase() === 'sin definir' ? 'sin valor' : valorLegible(actualizado)}.`,
        });
        await cargar();
      } catch (rechazo) {
        error.textContent = textoDelRechazo(rechazo, parametro);
        error.hidden = false;
      } finally {
        guardar.disabled = false;
      }
    }
    guardar.addEventListener('click', enviar);
    formulario.elemento.addEventListener('submit', (evento) => {
      evento.preventDefault();
      enviar();
    });
  }

  async function verHistorial(parametro) {
    limpiarAviso(zonaAviso);
    const ficha = fichaDeParametro(parametro);
    const contenido = h('div', { clase: 'pila pila--compacta', datos: { zona: 'historial' } });
    pintarEstado(contenido, estadoDeCarga({ filas: 3, etiqueta: 'Cargando el historial…' }));
    abrirPanel({
      titulo: `Historial · ${ficha.nombre}`,
      subtitulo: `Valor vigente: ${valorLegible(parametro)}`,
      contenido,
    });
    try {
      const versiones = await api.historial(parametro.clave, fetchImpl);
      if (titulares) {
        await titulares.nombrar(
          versiones.map((v) => v.cambiadoPor).filter((uid) => uid && uid !== UID_DEL_SISTEMA),
        );
      }
      vaciar(contenido).append(historialDeParametro(parametro, versiones, titulares));
    } catch (rechazo) {
      pintarEstado(
        contenido,
        estadoDeError({
          titulo: 'No pudimos cargar el historial',
          detalle:
            rechazo instanceof ErrorDeParametros && rechazo.motivo === 'PERMISO_INSUFICIENTE'
              ? 'Tu cuenta no puede consultar el historial de los parámetros.'
              : 'El servicio de parámetros no respondió. Vuelve a intentarlo.',
          alReintentar: () => verHistorial(parametro),
        }),
      );
    }
  }

  cargar();
  return { cargar, cambiar, verHistorial, politicas };
}
