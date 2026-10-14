/**
 * Ficha administrativa del usuario — HU-USR-010 (RF-USR-010, §7.3.4
 * «Perfil de usuario (vista administrativa)»).
 *
 * ## Qué junta y de dónde
 *
 * Una cuenta, vista por cada servicio que es dueño de algo suyo, sin que el
 * navegador hable con ninguna base de datos ni ningún servicio lea las tablas
 * de otro. Cada sección pide lo suyo por el `uid` de la cuenta (SA-3):
 *
 * | Pestaña    | Sección                       | Fuente                                                     |
 * |------------|-------------------------------|------------------------------------------------------------|
 * | Resumen    | Datos de la cuenta            | `GET /admin/usuarios/{usuario}/ficha` (ms-identidad-admin 1.4.0) |
 * |            | Rol y permisos                | `GET /rbac/matrix` (rbac 2.0.0)                            |
 * | Juego      | Estadísticas de juego         | `GET /partidas/jugadores/{uid}/estadisticas` (salas-partidas 1.11.0) |
 * |            | Últimas partidas              | `GET /partidas/jugadores/{uid}`                            |
 * | Créditos   | Saldo y movimientos           | `GET /creditos/{uid}/saldo` y `/movimientos` (creditos 1.5.0) |
 * | Sanciones  | Sanciones y advertencias      | `GET /sanciones/usuarios/{uid}` (moderacion-sanciones-admin) |
 * | Comunidad  | Comentarios publicados        | `GET /comentarios/moderacion/autores/{uid}/comentarios`    |
 * |            | Reportes realizados/recibidos | `GET /comentarios/moderacion/usuarios/{uid}/reportes` (comentarios 1.12.0) |
 * | Inventario | Inventario                    | `GET /inventario/usuarios/{uid}/elementos` (inventario 1.8.0) |
 * | Actividad  | Línea de tiempo               | los hechos con fecha de todo lo anterior                   |
 * |            | Acciones administrativas      | `GET /admin/auditoria?afectado=…` (ms-cumplimiento-auditoria 1.2.0), solo Super Administrador |
 *
 * El inventario se ve aquí y, para un Administrador o Super Administrador
 * sobre una cuenta de rol inferior (7.3.1), se modifica con «Entregar
 * productos» (SA-10, §7.3.4): la entrega administrativa auditada de
 * ms-identidad-admin.yaml 1.10.0 (`entrega-administrativa.js`).
 *
 * ## Las reglas de la historia
 *
 * - CA-01/CA-04: se abre con la clave de la cuenta (la del directorio) o con
 *   su `uid` (lo único que la cola de comentarios conoce del autor, CA-02) y
 *   consolida lo que hay, con una línea de tiempo de la actividad.
 * - CA-03: si la cuenta no existe, el problem details de identidad lo dice y
 *   no se consulta nada más; si un servicio no responde, su sección dice que
 *   no pudo cargar y el resto sigue (cada panel carga por su cuenta, como en
 *   Control integral).
 * - Abrir la ficha queda en la auditoría porque son datos personales. Lo
 *   registra ms-identidad al servir la ficha; si no pudo, la pantalla lo
 *   advierte.
 *
 * @module plataforma/moderacion-sanciones/ficha-usuario
 */

import { consultar, filasDe, RESULTADO } from '../consola/cliente-consola.js';
import { panel, pintarDesenlace, tabla } from '../consola/panel.js';
import { CATEGORIAS } from '../comentarios/cliente-moderacion.js';
import { abrirEntregaAdministrativa, puedeEntregarA } from './entrega-administrativa.js';
import { ESTADO_LEGIBLE } from '../comentarios/moderar-comentarios.js';
import { puedeVer, urlDeVista, VEREDICTO } from '../../comun/acceso.js';
import { rutaDeApi } from '../../comun/base-api.js';
import { fetchWithHttpErrorInterceptor } from '../../comun/interceptors/http-error.interceptor.js';
import { leerSesion } from '../../comun/sesion.js';
import { NOMBRE_DE_ROL } from '../../comun/shell.js';
import { aviso } from '../../comun/ui/aviso.js';
import { distintivoDeEstado } from '../../comun/ui/admin/estado.js';
import { rejillaDeIndicadores, tarjetaDeIndicador } from '../../comun/ui/admin/indicador.js';
import { nombreDeAccion } from '../../comun/ui/admin/nombres.js';
import { permisosDelRol } from '../../comun/ui/admin/permisos.js';
import { distintivo } from '../../comun/ui/distintivo.js';
import { h, vaciar } from '../../comun/ui/dom.js';
import {
  estadoDeCarga,
  estadoDeError,
  estadoVacio,
  pintarEstado,
} from '../../comun/ui/estado-vista.js';
import { fechaHora, nombreDelTipo } from '../../comun/ui/formato.js';
import { icono } from '../../comun/ui/icono.js';
import {
  distintivoDeResultado,
  nombreDeModalidad,
  resultadoDePartida,
} from '../../comun/ui/juego/partida.js';
import { lineaDeTiempo } from '../../comun/ui/linea-de-tiempo.js';
import {
  comoImporte,
  importeConUnidad,
  nombreDelConcepto,
} from '../../comun/ui/movimiento-de-creditos.js';
import { encabezadoDePagina } from '../../comun/ui/pagina.js';
import { montarPestanas } from '../../comun/ui/pestanas.js';
import { estadoDeCuenta, hechosDeSanciones, NOMBRE_DE_SANCION } from '../../comun/ui/sancion.js';
import { textoDelServidor } from '../../comun/ui/texto-de-fallo.js';

/** La clave interna que publica el directorio: un entero positivo. */
const CLAVE = /^[1-9]\d{0,17}$/;
const UID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * Lo que se pinta cuando la fuente no trae el dato: el mismo texto que pinta
 * la tabla del kit de la consola cuando una celda llega vacía.
 */
export const SIN_DATO = 'Sin dato';

/** Comentarios que se piden: la primera página del historial del autor. */
export const COMENTARIOS_POR_PAGINA = 20;

/** Partidas, movimientos y reportes de cada lado que se piden: los más recientes. */
export const RECIENTES_POR_SECCION = 10;

/** Acciones administrativas sobre la cuenta que se piden a la auditoría. */
export const ACCIONES_DE_AUDITORIA = 20;

/** Largo del extracto de un comentario en la tabla y en la línea de tiempo. */
const LARGO_DEL_EXTRACTO = 160;

/** Los estados de `EstadoCuenta` (ms-identidad-admin.yaml), como se leen. */
export const ESTADO_DE_CUENTA = Object.freeze({
  ACTIVO: 'Activa',
  PENDIENTE_VERIFICACION: 'Pendiente de confirmar el correo',
  INACTIVO: 'Sin activar (cuenta administrativa)',
  SUSPENDIDO: 'Suspendida',
  BANEADO: 'Baneada',
});

/** Cómo termina la consulta de la ficha a identidad. */
export const DESENLACE_DE_FICHA = Object.freeze({
  DATOS: 'DATOS',
  /** 404 `cuenta-no-encontrada`: la cuenta no existe (CA-03). */
  NO_EXISTE: 'NO_EXISTE',
  SIN_PERMISO: 'SIN_PERMISO',
  /** 404 sin ese tipo: el servicio desplegado aún no tiene la ruta. */
  NO_PUBLICADA: 'NO_PUBLICADA',
  NO_DISPONIBLE: 'NO_DISPONIBLE',
});

/** Las pestañas de la ficha, en el orden de §7.3.4. */
export const PESTANAS_DE_LA_FICHA = Object.freeze([
  { id: 'resumen', etiqueta: 'Resumen' },
  { id: 'juego', etiqueta: 'Juego' },
  { id: 'creditos', etiqueta: 'Créditos' },
  { id: 'sanciones', etiqueta: 'Sanciones' },
  { id: 'comunidad', etiqueta: 'Comunidad' },
  { id: 'inventario', etiqueta: 'Inventario' },
  { id: 'actividad', etiqueta: 'Actividad' },
]);

/** Las familias de hechos de la línea de tiempo, para filtrarla. */
export const CATEGORIAS_DE_LA_LINEA = Object.freeze([
  { valor: 'cuenta', etiqueta: 'Cuenta' },
  { valor: 'juego', etiqueta: 'Partidas' },
  { valor: 'creditos', etiqueta: 'Créditos' },
  { valor: 'sanciones', etiqueta: 'Sanciones' },
  { valor: 'comunidad', etiqueta: 'Comentarios y reportes' },
  { valor: 'administracion', etiqueta: 'Acciones administrativas' },
]);

/* -------------------------------------------------------------------------
   Qué cuenta, y dónde está la ficha
   ------------------------------------------------------------------------- */

/**
 * La cuenta que trae la dirección (`?usuario=`): la clave interna del
 * directorio o el `uid`. Cualquier otra cosa no nombra a nadie y no se
 * consulta.
 *
 * @param {string} [busqueda] `location.search`
 * @returns {string|null}
 */
export function claveDeLaDireccion(busqueda = globalThis.location?.search ?? '') {
  const valor = (new URLSearchParams(busqueda).get('usuario') ?? '').trim();
  return CLAVE.test(valor) || UID.test(valor) ? valor : null;
}

/** @param {string} url @param {string|number} usuario */
function conUsuario(url, usuario) {
  const destino = new URL(url);
  destino.searchParams.set('usuario', String(usuario));
  return destino.href;
}

/* -------------------------------------------------------------------------
   La ficha de identidad
   ------------------------------------------------------------------------- */

/**
 * Pide la ficha a identidad. Nunca lanza: devuelve el desenlace.
 *
 * Es la única consulta que no pasa por `consultar()` de la consola, porque
 * aquí un 404 puede significar dos cosas distintas que hay que distinguir
 * leyendo el problem details: que la cuenta no existe (CA-03) o que el
 * servicio desplegado todavía no publica la ruta.
 *
 * @param {string} clave
 * @param {{buscar?: typeof fetchWithHttpErrorInterceptor}} [opciones]
 * @returns {Promise<{resultado: string, ficha: object|null, estado: number|null, problema: object|null}>}
 */
export async function pedirFicha(clave, { buscar = fetchWithHttpErrorInterceptor } = {}) {
  let respuesta;
  try {
    respuesta = await buscar(rutaDeApi(`/admin/usuarios/${encodeURIComponent(clave)}/ficha`), {
      method: 'GET',
      headers: { Accept: 'application/json' },
    });
  } catch {
    return {
      resultado: DESENLACE_DE_FICHA.NO_DISPONIBLE,
      ficha: null,
      estado: null,
      problema: null,
    };
  }
  const cuerpo = await leerJson(respuesta);
  const estado = respuesta.status;
  if (respuesta.ok && cuerpo && typeof cuerpo === 'object') {
    return { resultado: DESENLACE_DE_FICHA.DATOS, ficha: cuerpo, estado, problema: null };
  }
  let resultado = DESENLACE_DE_FICHA.NO_DISPONIBLE;
  if (estado === 404) {
    resultado = String(cuerpo?.type ?? '').endsWith('/cuenta-no-encontrada')
      ? DESENLACE_DE_FICHA.NO_EXISTE
      : DESENLACE_DE_FICHA.NO_PUBLICADA;
  } else if (estado === 401 || estado === 403) {
    resultado = DESENLACE_DE_FICHA.SIN_PERMISO;
  }
  return { resultado, ficha: null, estado, problema: cuerpo };
}

/** @param {{json: () => Promise<*>}} respuesta */
async function leerJson(respuesta) {
  try {
    return await respuesta.json();
  } catch {
    return null;
  }
}

/* -------------------------------------------------------------------------
   Hechos de la línea de tiempo
   ------------------------------------------------------------------------- */

/**
 * Los hechos con fecha que publica identidad: el registro y la última entrada.
 *
 * @param {{creadoEn?: string|null, ultimoAcceso?: string|null}|null} ficha
 * @returns {import('../../comun/ui/linea-de-tiempo.js').HechoDeLaLinea[]}
 */
export function hechosDeLaCuenta(ficha) {
  const hechos = [];
  if (ficha?.creadoEn) {
    hechos.push({ cuando: ficha.creadoEn, titulo: 'Se registró', tono: 'info' });
  }
  if (ficha?.ultimoAcceso) {
    hechos.push({ cuando: ficha.ultimoAcceso, titulo: 'Última entrada', tono: 'neutro' });
  }
  return hechos;
}

/**
 * Un hecho por comentario publicado, con su extracto y, si moderación lo
 * retiró de la vista, su estado.
 *
 * @param {{comentarios?: object[]}|null} historial `HistorialDelAutorResponse`
 * @returns {import('../../comun/ui/linea-de-tiempo.js').HechoDeLaLinea[]}
 */
export function hechosDeComentarios(historial) {
  const lista = Array.isArray(historial?.comentarios) ? historial.comentarios : [];
  return lista
    .filter((comentario) => comentario?.fechaPublicacion)
    .map((comentario) => {
      const retirado = comentario.estado === 'OCULTO' || comentario.estado === 'ELIMINADO';
      const estado =
        comentario.estado && comentario.estado !== 'PUBLICADO'
          ? ` (${ESTADO_LEGIBLE[comentario.estado] ?? comentario.estado})`
          : '';
      return {
        cuando: comentario.fechaPublicacion,
        titulo: 'Publicó un comentario',
        detalle: `${extracto(comentario.texto)}${estado}`,
        tono: retirado ? 'advertencia' : 'neutro',
      };
    });
}

/**
 * Un hecho por partida de la página que se cargó: cuándo terminó (o empezó,
 * si sigue en curso), la modalidad y el resultado para esta cuenta.
 *
 * @param {{contenido?: object[]}|null} pagina `PaginaDePartidas` (salas-partidas 1.11.0)
 * @returns {import('../../comun/ui/linea-de-tiempo.js').HechoDeLaLinea[]}
 */
export function hechosDePartidas(pagina) {
  return filasDe(pagina)
    .filter((partida) => partida?.iniciadaEn)
    .map((partida) => {
      const enCurso = partida.estado === 'EN_CURSO';
      const resultado = resultadoDePartida(partida).texto;
      return {
        cuando: enCurso ? partida.iniciadaEn : (partida.finalizadaEn ?? partida.iniciadaEn),
        titulo: enCurso ? 'Empezó una partida' : 'Jugó una partida',
        detalle: `${nombreDeModalidad(partida.modalidad)} · ${resultado}`,
        tono: partida.resultado === 'VICTORIA' ? 'exito' : 'neutro',
      };
    });
}

/**
 * Un hecho por movimiento de créditos: el concepto y cómo afectó al saldo,
 * con las palabras que decide el libro de créditos (`signo`).
 *
 * @param {{content?: object[]}|null} pagina página de `MovimientoDeCredito` (creditos 1.5.0)
 * @returns {import('../../comun/ui/linea-de-tiempo.js').HechoDeLaLinea[]}
 */
export function hechosDeMovimientos(pagina) {
  return filasDe(pagina)
    .filter((movimiento) => movimiento?.creado)
    .map((movimiento) => ({
      cuando: movimiento.creado,
      titulo: nombreDelConcepto(movimiento.concepto),
      detalle: importeConUnidad(movimiento),
      tono: 'neutro',
    }));
}

/**
 * Un hecho por reporte: los que hizo la cuenta y los que recibieron sus
 * comentarios. De un reporte recibido no se sabe quién lo hizo: el servicio
 * no lo publica.
 *
 * @param {{reportes?: object[]}|null} respuesta `ReportesDelUsuarioResponse` (comentarios 1.12.0)
 * @param {'REALIZADOS'|'RECIBIDOS'} direccion
 * @returns {import('../../comun/ui/linea-de-tiempo.js').HechoDeLaLinea[]}
 */
export function hechosDeReportes(respuesta, direccion) {
  const lista = Array.isArray(respuesta?.reportes) ? respuesta.reportes : [];
  const recibidos = direccion === 'RECIBIDOS';
  return lista
    .filter((reporte) => reporte?.fecha)
    .map((reporte) => ({
      cuando: reporte.fecha,
      titulo: recibidos ? 'Un comentario suyo fue reportado' : 'Reportó un comentario',
      detalle: [
        nombreDeCategoria(reporte.categoria),
        reporte.comentario?.extracto ? `«${extracto(reporte.comentario.extracto)}»` : null,
      ]
        .filter(Boolean)
        .join(' · '),
      tono: recibidos ? 'advertencia' : 'neutro',
    }));
}

/**
 * Las acciones administrativas sobre la cuenta, de la auditoría. Las
 * sanciones no se repiten: ya las cuenta su dueño (moderación) con su motivo
 * y su vigencia, y la auditoría solo las anota.
 *
 * @param {{content?: object[]}|null} pagina `PaginaDeAuditoria`
 * @returns {import('../../comun/ui/linea-de-tiempo.js').HechoDeLaLinea[]}
 */
export function hechosDeAuditoria(pagina) {
  return filasDe(pagina)
    .filter(
      (registro) =>
        registro?.fechaHora &&
        registro.tipoAccion !== 'SANCION' &&
        registro.tipoAccion !== 'SUSPENSION',
    )
    .map((registro) => ({
      cuando: registro.fechaHora,
      titulo: nombreDeAccion(registro.tipoAccion),
      detalle: registro.motivo || null,
      actor: registro.administrador ? `Por ${registro.administrador}` : null,
      tono: registro.tipoAccion === 'SECURITY_BYPASS_ATTEMPT' ? 'error' : 'info',
    }));
}

/** @param {string|null|undefined} texto */
function extracto(texto) {
  const limpio = String(texto ?? '').trim();
  return limpio.length > LARGO_DEL_EXTRACTO
    ? `${limpio.slice(0, LARGO_DEL_EXTRACTO - 1).trimEnd()}…`
    : limpio;
}

/** @param {string|null|undefined} categoria `CategoriaDeReporte` */
function nombreDeCategoria(categoria) {
  return CATEGORIAS.find((c) => c.valor === categoria)?.etiqueta ?? categoria ?? SIN_DATO;
}

/* -------------------------------------------------------------------------
   Montaje
   ------------------------------------------------------------------------- */

/**
 * Monta la ficha.
 *
 * @param {Document|HTMLElement} raiz con las zonas `encabezado`, `aviso` y `ficha`
 * @param {{clave?: string|null, buscar?: Function, consultarApi?: typeof consultar,
 *          ahora?: () => number, sesion?: {autenticado?: boolean, rol?: string|null}|null}} [opciones]
 *   inyectables en pruebas; `sesion` decide qué secciones de solo Super Administrador se piden
 * @returns {Promise<object|null>} el desenlace de la ficha de identidad
 */
export function montarFichaDeUsuario(
  raiz,
  {
    clave = claveDeLaDireccion(),
    buscar = fetchWithHttpErrorInterceptor,
    consultarApi = consultar,
    ahora = () => Date.now(),
    sesion = leerSesion(),
  } = {},
) {
  const zonaEncabezado = raiz.querySelector('[data-zona="encabezado"]');
  const zonaAviso = raiz.querySelector('[data-zona="aviso"]');
  const zonaFicha = raiz.querySelector('[data-zona="ficha"]');
  if (!zonaFicha) {
    return Promise.resolve(null);
  }
  pintarEncabezado(zonaEncabezado, null);

  if (!clave) {
    pintarEstado(
      zonaFicha,
      estadoVacio({
        titulo: 'Falta la cuenta',
        detalle:
          'La ficha se abre desde una fila del directorio de Control integral, desde la gestión ' +
          'de usuarios o desde el autor de un comentario reportado.',
        accion: {
          texto: 'Ir al directorio',
          href: urlDeVista('control-integral'),
          nombre: 'ir-al-directorio',
        },
      }),
    );
    return Promise.resolve(null);
  }

  // La auditoría es solo del Super Administrador (§7.3.5, `ms-cumplimiento`):
  // a otro rol no se le pide ni se le pinta un panel que siempre negaría.
  const veAuditoria = puedeVer('auditoria', sesion).veredicto === VEREDICTO.VISIBLE;

  const cargar = async () => {
    vaciarAviso(zonaAviso);
    pintarEstado(zonaFicha, estadoDeCarga({ filas: 4, etiqueta: 'Cargando la ficha del usuario' }));
    const desenlace = await pedirFicha(clave, { buscar });
    if (desenlace.resultado !== DESENLACE_DE_FICHA.DATOS) {
      pintarFallo(zonaFicha, desenlace, cargar);
      return desenlace;
    }
    pintarEncabezado(zonaEncabezado, desenlace.ficha);
    pintarAuditoria(zonaAviso, desenlace.ficha);
    await pintarSecciones(zonaFicha, desenlace.ficha, {
      consultarApi,
      ahora,
      veAuditoria,
      rolDeQuienMira: sesion?.rol ?? null,
    });
    return desenlace;
  };
  return cargar();
}

/** @param {HTMLElement|null} zona @param {object|null} ficha */
function pintarEncabezado(zona, ficha) {
  zona?.replaceChildren(
    encabezadoDePagina({
      titulo: ficha?.apodo ? `Ficha de ${ficha.apodo}` : 'Ficha del usuario',
      descripcion:
        'Cuenta, juego, créditos, sanciones, comunidad, inventario y actividad, en una sola ficha.',
    }),
  );
}

/** @param {HTMLElement|null} zona */
function vaciarAviso(zona) {
  if (zona) {
    zona.replaceChildren();
    delete zona.dataset.auditado;
  }
}

/**
 * Que la consulta quedó (o no) en la auditoría. Nunca en silencio: si
 * identidad no pudo registrarla, quien mira lo sabe.
 *
 * @param {HTMLElement|null} zona
 * @param {{accesoAuditado?: boolean}} ficha
 */
function pintarAuditoria(zona, ficha) {
  if (!zona) {
    return;
  }
  const auditado = ficha?.accesoAuditado === true;
  zona.dataset.auditado = auditado ? 'si' : 'no';
  zona.replaceChildren(
    aviso(
      auditado
        ? {
            tono: 'info',
            titulo: 'Esta consulta quedó registrada en la auditoría',
            detalle:
              'La ficha reúne datos personales: cada vez que se abre queda asentado quién la ' +
              'consultó y cuándo.',
          }
        : {
            tono: 'advertencia',
            titulo: 'Esta consulta no quedó registrada en la auditoría',
            detalle:
              'El servicio de auditoría no respondió. La consulta quedó en el registro del ' +
              'servicio de identidad; si vuelve a pasar, avisa a quien opera la plataforma.',
          },
    ),
  );
  zona.hidden = false;
}

/**
 * Lo que se pinta cuando identidad no entrega la ficha. Sin ella no se sabe
 * quién es la cuenta, así que no se consulta ninguna otra sección.
 *
 * @param {HTMLElement} zona
 * @param {{resultado: string, estado: number|null, problema: object|null}} desenlace
 * @param {() => Promise<*>} reintentar
 */
function pintarFallo(zona, desenlace, reintentar) {
  if (desenlace.resultado === DESENLACE_DE_FICHA.NO_EXISTE) {
    const estado = estadoDeError({
      titulo: 'No existe esa cuenta',
      detalle: textoDelServidor(
        desenlace.problema,
        404,
        'No hay ninguna cuenta con ese identificador. Puede que la dirección esté mal copiada.',
      ),
      alReintentar: null,
    });
    estado.append(
      h('a', {
        clase: 'boton boton--secundario',
        texto: 'Ir al directorio',
        atributos: { href: urlDeVista('control-integral') },
        datos: { accion: 'ir-al-directorio' },
      }),
    );
    pintarEstado(zona, estado);
    return;
  }
  if (desenlace.resultado === DESENLACE_DE_FICHA.SIN_PERMISO) {
    pintarEstado(
      zona,
      estadoDeError({
        titulo: 'Tu rol no permite abrir fichas de usuario',
        detalle:
          'La ficha es una herramienta de administración: la abren el Administrador y el Super ' +
          'Administrador.',
        alReintentar: null,
      }),
    );
    return;
  }
  if (desenlace.resultado === DESENLACE_DE_FICHA.NO_PUBLICADA) {
    pintarEstado(
      zona,
      estadoDeError({
        titulo: 'El servicio de identidad no publica todavía la ficha',
        detalle:
          'La versión desplegada del servicio no tiene la consulta de la ficha administrativa. ' +
          'Cuando se despliegue, esta pantalla funcionará sin cambios.',
        alReintentar: reintentar,
      }),
    );
    return;
  }
  pintarEstado(
    zona,
    estadoDeError({
      titulo: 'El servicio de identidad no responde',
      detalle:
        'Sin él no se sabe qué cuenta es, así que no se consulta ninguna sección. Vuelve a ' +
        'intentarlo en unos momentos.',
      alReintentar: reintentar,
    }),
  );
}

/* -------------------------------------------------------------------------
   Secciones
   ------------------------------------------------------------------------- */

/**
 * Pinta las pestañas y espera a las secciones que consultan otros servicios
 * para armar la línea de tiempo. Cada panel carga por su cuenta y a la vez:
 * que un servicio tarde no retrasa a los demás, y uno caído no tumba la
 * ficha.
 *
 * @param {HTMLElement} zona
 * @param {object} ficha
 * @param {{consultarApi: typeof consultar, ahora: () => number, veAuditoria: boolean,
 *          rolDeQuienMira?: string|null}} opciones
 */
async function pintarSecciones(
  zona,
  ficha,
  { consultarApi, ahora, veAuditoria, rolDeQuienMira = null },
) {
  const linea = panelDeLaLinea();
  /** @type {Map<string, {datos: *, falta: string|null}>} */
  const cargados = new Map();
  const fuentes = fuentesDeLaFicha(ficha, { veAuditoria });
  const inventario = fuentes.find((fuente) => fuente.id === 'inventario-de-la-cuenta');
  if (inventario && ficha.uid && puedeEntregarA(rolDeQuienMira, ficha.rol)) {
    inventario.panel.acciones = [botonDeEntrega(ficha, () => inventario.controlador)];
  }
  const repintarLinea = () => linea.pintar(armarLinea(ficha, fuentes, cargados, ahora()));
  for (const fuente of fuentes) {
    fuente.controlador = panelPorUid({
      ...fuente.panel,
      ficha,
      consultarApi,
      alCargar: (resultado) => cargados.set(fuente.id, resultado),
    });
    // Un reintento que trae los datos también los suma a la línea.
    fuente.controlador.alReintentar = repintarLinea;
  }
  const elementoDe = (id) => fuentes.find((f) => f.id === id)?.controlador.elemento ?? null;
  const permisos = panelDePermisos(ficha, consultarApi);

  const paneles = {
    resumen: [panelDeLaCuenta(ficha), permisos.elemento, panelDeAcciones(ficha)],
    juego: [elementoDe('estadisticas'), elementoDe('partidas')],
    creditos: [elementoDe('saldo'), elementoDe('movimientos')],
    sanciones: [elementoDe('sanciones')],
    comunidad: [
      elementoDe('comentarios'),
      elementoDe('reportes-realizados'),
      elementoDe('reportes-recibidos'),
    ],
    inventario: [elementoDe('inventario-de-la-cuenta')],
    actividad: [linea.elemento, elementoDe('auditoria')],
  };

  vaciar(zona);
  const contenedor = h('div', { clase: 'ficha-usuario', datos: { zona: 'pestanas' } });
  zona.append(contenedor);
  montarPestanas(
    contenedor,
    PESTANAS_DE_LA_FICHA.map(({ id, etiqueta }) => ({
      id,
      etiqueta,
      panel: seccion(id, etiqueta, paneles[id].filter(Boolean)),
    })),
  );

  await Promise.all([permisos.cargar(), ...fuentes.map((fuente) => fuente.controlador.cargar())]);
  repintarLinea();
}

/** @param {string} id @param {string} etiqueta @param {HTMLElement[]} paneles */
function seccion(id, etiqueta, paneles) {
  return h('section', {
    clase: 'consola-integral__seccion',
    datos: { seccion: id },
    hijos: [
      // La pestaña ya dice dónde se está; el título queda para quien
      // recorre la página por encabezados.
      h('h2', { clase: 'solo-lectores', texto: etiqueta }),
      h('div', { clase: 'consola-integral__rejilla', hijos: paneles }),
    ],
  });
}

/**
 * Las secciones que se piden por el `uid` de la cuenta, con lo que cada una
 * aporta a la línea de tiempo y el nombre con que se dice si faltó.
 *
 * @param {object} ficha
 * @param {{veAuditoria: boolean}} opciones
 */
function fuentesDeLaFicha(ficha, { veAuditoria }) {
  const fuentes = [
    {
      id: 'estadisticas',
      enLaLinea: null,
      panel: {
        id: 'estadisticas',
        titulo: 'Estadísticas de juego',
        descripcion: 'Partidas terminadas, resultados y modalidades.',
        ancho: 'completo',
        servicio: 'partidas',
        recurso: (uid) => `/partidas/jugadores/${uid}/estadisticas`,
        pintar: pintarEstadisticas,
        vacio: null,
      },
    },
    {
      id: 'partidas',
      enLaLinea: { nombre: 'partidas', categoria: 'juego', hechos: hechosDePartidas },
      panel: {
        id: 'partidas',
        titulo: 'Últimas partidas',
        descripcion: 'Con el resultado para esta cuenta.',
        ancho: 'completo',
        servicio: 'partidas',
        recurso: (uid) => `/partidas/jugadores/${uid}?pagina=0&tamano=${RECIENTES_POR_SECCION}`,
        pintar: pintarPartidas,
        vacio: { contenido: [], totalElementos: 0 },
      },
    },
    {
      id: 'saldo',
      enLaLinea: null,
      panel: {
        id: 'saldo',
        titulo: 'Saldo de créditos',
        descripcion: 'Lo que tiene, lo apartado en apuestas y pujas, y lo que puede gastar.',
        ancho: 'completo',
        servicio: 'créditos',
        recurso: (uid) => `/creditos/${uid}/saldo`,
        pintar: pintarSaldo,
        vacio: null,
      },
    },
    {
      id: 'movimientos',
      enLaLinea: { nombre: 'créditos', categoria: 'creditos', hechos: hechosDeMovimientos },
      panel: {
        id: 'movimientos',
        titulo: 'Movimientos de créditos',
        descripcion:
          'Apuestas, recompensas, inscripciones y compras, del más reciente al más antiguo.',
        ancho: 'completo',
        servicio: 'créditos',
        recurso: (uid) => `/creditos/${uid}/movimientos?page=0&size=${RECIENTES_POR_SECCION}`,
        pintar: pintarMovimientos,
        vacio: { content: [], totalElements: 0 },
      },
    },
    {
      id: 'sanciones',
      enLaLinea: {
        nombre: 'sanciones',
        categoria: 'sanciones',
        hechos: (datos, ahora) => (Array.isArray(datos) ? hechosDeSanciones(datos, { ahora }) : []),
      },
      panel: {
        id: 'sanciones',
        titulo: 'Sanciones y advertencias',
        descripcion: 'El historial disciplinario de la cuenta, con el motivo de cada sanción.',
        ancho: 'completo',
        servicio: 'moderación',
        recurso: (uid) => `/sanciones/usuarios/${uid}`,
        pintar: pintarSanciones,
        vacio: [],
      },
    },
    {
      id: 'comentarios',
      enLaLinea: { nombre: 'comentarios', categoria: 'comunidad', hechos: hechosDeComentarios },
      panel: {
        id: 'comentarios',
        titulo: 'Comentarios publicados',
        descripcion:
          'Los más recientes, en cualquier estado: también los que moderación ocultó o eliminó.',
        ancho: 'completo',
        servicio: 'comentarios',
        recurso: (uid) =>
          `/comentarios/moderacion/autores/${uid}/comentarios?pagina=0&tamano=${COMENTARIOS_POR_PAGINA}`,
        pintar: pintarComentarios,
        vacio: { comentarios: [], total: 0 },
      },
    },
    {
      id: 'reportes-realizados',
      enLaLinea: {
        nombre: 'reportes realizados',
        categoria: 'comunidad',
        hechos: (datos) => hechosDeReportes(datos, 'REALIZADOS'),
      },
      panel: {
        id: 'reportes-realizados',
        titulo: 'Reportes realizados',
        descripcion: 'Los comentarios de otras personas que esta cuenta reportó.',
        ancho: 'completo',
        servicio: 'comentarios',
        recurso: (uid) =>
          `/comentarios/moderacion/usuarios/${uid}/reportes?direccion=REALIZADOS&pagina=0&tamano=${RECIENTES_POR_SECCION}`,
        pintar: (datos) => pintarReportes(datos, 'REALIZADOS'),
        vacio: { reportes: [], total: 0 },
      },
    },
    {
      id: 'reportes-recibidos',
      enLaLinea: {
        nombre: 'reportes recibidos',
        categoria: 'comunidad',
        hechos: (datos) => hechosDeReportes(datos, 'RECIBIDOS'),
      },
      panel: {
        id: 'reportes-recibidos',
        titulo: 'Reportes recibidos',
        descripcion: 'Los reportes que otras personas hicieron sobre sus comentarios.',
        ancho: 'completo',
        servicio: 'comentarios',
        recurso: (uid) =>
          `/comentarios/moderacion/usuarios/${uid}/reportes?direccion=RECIBIDOS&pagina=0&tamano=${RECIENTES_POR_SECCION}`,
        pintar: (datos) => pintarReportes(datos, 'RECIBIDOS'),
        vacio: { reportes: [], total: 0 },
      },
    },
    {
      id: 'inventario-de-la-cuenta',
      enLaLinea: null,
      panel: {
        id: 'inventario-de-la-cuenta',
        titulo: 'Inventario',
        descripcion: 'Héroes y objetos de la cuenta, con dónde está cada uno ahora.',
        ancho: 'completo',
        servicio: 'inventario',
        recurso: (uid) => `/inventario/usuarios/${uid}/elementos?pagina=0`,
        pintar: pintarInventario,
        vacio: { elementos: [], totalElementos: 0 },
      },
    },
  ];
  if (veAuditoria) {
    fuentes.push({
      id: 'auditoria',
      enLaLinea: {
        nombre: 'acciones administrativas',
        categoria: 'administracion',
        hechos: hechosDeAuditoria,
      },
      panel: {
        id: 'auditoria',
        titulo: 'Acciones administrativas sobre la cuenta',
        descripcion:
          'Lo que la administración hizo con esta cuenta, según la auditoría: también cada ' +
          'consulta de esta ficha.',
        ancho: 'completo',
        servicio: 'auditoría',
        recurso: (uid) => recursoDeAuditoria(uid, ficha),
        pintar: pintarAuditoriaDeLaCuenta,
        vacio: { content: [], totalElements: 0 },
      },
    });
  }
  return fuentes;
}

/**
 * La auditoría guarda la cuenta afectada por su `uid` y, en registros
 * anteriores, por su clave: se piden las dos formas (filtro repetible,
 * ms-cumplimiento-auditoria 1.2.0), lo más reciente primero.
 *
 * @param {string} uid ya escapado
 * @param {{id?: number|string}} ficha
 */
function recursoDeAuditoria(uid, ficha) {
  const pares = [`afectado=${uid}`];
  const clave = Number(ficha?.id);
  if (Number.isInteger(clave) && clave > 0) {
    pares.push(`afectado=${clave}`, `afectado=${encodeURIComponent(`usuario-${clave}`)}`);
  }
  pares.push('sort=fechaHora,desc', 'page=0', `size=${ACCIONES_DE_AUDITORIA}`);
  return `/admin/auditoria?${pares.join('&')}`;
}

/** Un desenlace con datos que ya se tienen (no hace falta pedirlos). */
const yaCargado = (datos) => ({
  resultado: RESULTADO.DATOS,
  datos,
  estado: 200,
  motivo: '',
  recurso: '',
  detalle: null,
});

/** @param {object} ficha */
function panelDeLaCuenta(ficha) {
  const partes = panel({
    id: 'cuenta',
    titulo: 'Datos de la cuenta',
    descripcion: 'Identidad, contacto, rol y estado.',
  });
  pintarDesenlace(partes, yaCargado(ficha), datosDeLaCuenta);
  return partes.elemento;
}

/** @param {object} ficha */
function datosDeLaCuenta(ficha) {
  const nombre = [ficha.nombres, ficha.apellidos]
    .filter((parte) => typeof parte === 'string' && parte.trim())
    .join(' ');
  const pares = [
    ['Apodo', ficha.apodo || SIN_DATO],
    ['Nombre', nombre || SIN_DATO],
    ['Correo', ficha.email || SIN_DATO],
    ['Rol', NOMBRE_DE_ROL[ficha.rol] ?? (ficha.rol || SIN_DATO)],
    ['Estado', estadoLegible(ficha)],
    ['Registro', ficha.creadoEn ? fechaHora(ficha.creadoEn) : SIN_DATO],
    ['Última entrada', ficha.ultimoAcceso ? fechaHora(ficha.ultimoAcceso) : 'Nunca ha entrado'],
  ];
  const lista = h('dl', {
    clase: 'ficha-usuario__datos',
    hijos: pares.map(([etiqueta, valor]) =>
      h('div', {
        clase: 'ficha-usuario__dato',
        hijos: [h('dt', { texto: etiqueta }), h('dd', { texto: valor })],
      }),
    ),
  });
  if (!ficha.bloqueada) {
    return lista;
  }
  // El bloqueo por intentos no es una sanción, y por eso no va en el estado.
  return [
    lista,
    h('p', {
      clase: 'fila fila--envuelta',
      hijos: [distintivo('Bloqueada por intentos fallidos', 'bloqueada')],
    }),
  ];
}

/** @param {{estado?: string, suspendidoHasta?: string|null}} ficha */
function estadoLegible(ficha) {
  const nombre = ESTADO_DE_CUENTA[ficha.estado] ?? (ficha.estado || SIN_DATO);
  if (ficha.estado === 'SUSPENDIDO' && ficha.suspendidoHasta) {
    return `${nombre} hasta ${fechaHora(ficha.suspendidoHasta)}`;
  }
  return nombre;
}

/**
 * §7.3.4 «rol y permisos asignados»: lo que la matriz que publica el
 * servidor le da al rol de la cuenta, en palabras.
 *
 * @param {object} ficha
 * @param {typeof consultar} consultarApi
 */
function panelDePermisos(ficha, consultarApi) {
  const partes = panel({
    id: 'permisos',
    titulo: 'Rol y permisos',
    descripcion: 'Lo que el rol de la cuenta le permite hacer en la plataforma.',
  });
  const rol = ficha.rol ?? null;
  const pintar = (respuesta) => {
    const nombreDelRol = NOMBRE_DE_ROL[rol] ?? (rol || 'Sin rol asignado');
    const cabeza = h('p', {
      clase: 'fila fila--envuelta',
      hijos: [distintivo(nombreDelRol, rol ? String(rol).toLowerCase() : null)],
    });
    const permisos = permisosDelRol(respuesta?.matrix, rol);
    if (permisos.length === 0) {
      return [
        cabeza,
        h('p', {
          clase: 't-meta',
          texto: rol
            ? 'La matriz de permisos no le da ninguna acción a este rol.'
            : 'Sin rol, la cuenta no tiene permisos.',
        }),
      ];
    }
    return [
      cabeza,
      h('ul', {
        clase: 'ficha-usuario__permisos',
        atributos: { 'aria-label': `Permisos del rol ${nombreDelRol}` },
        hijos: permisos.map((permiso) =>
          h('li', {
            hijos: [
              icono('check', { etiqueta: null, clase: 'icono ficha-usuario__permiso-icono' }),
              h('span', { texto: permiso }),
            ],
          }),
        ),
      }),
    ];
  };
  const cargar = async () => {
    const desenlace = await consultarApi('/rbac/matrix');
    pintarDesenlace(partes, desenlace, pintar, { alReintentar: cargar });
  };
  return { elemento: partes.elemento, cargar };
}

/**
 * Las acciones que ya existen sobre esta cuenta, en sus pantallas. Aquí no
 * se ejecuta ninguna: cada pantalla aplica su propio permiso y pide su motivo.
 *
 * @param {object} ficha
 */
function panelDeAcciones(ficha) {
  const partes = panel({
    id: 'acciones',
    titulo: 'Acciones',
    descripcion: 'Las que ya existen, con esta cuenta abierta.',
  });
  const pintar = () => {
    const lista = h('div', { clase: 'pila pila--compacta' });
    const clave = Number(ficha.id);
    if (Number.isInteger(clave) && clave > 0) {
      lista.append(
        accion({
          nombre: 'gestionar',
          texto: 'Gestionar la cuenta',
          href: conUsuario(urlDeVista('gestion-usuarios'), clave),
          detalle:
            'Editar el perfil, el rol y el estado; suspender, banear o reactivar; restablecer la ' +
            'contraseña.',
        }),
      );
    }
    if (ficha.uid) {
      lista.append(
        accion({
          nombre: 'sancionar',
          texto: 'Sancionar o revisar sanciones',
          href: conUsuario(urlDeVista('sanciones-admin'), ficha.uid),
          detalle: 'Advertencia, suspensión o baneo con su motivo, y las apelaciones.',
        }),
      );
    } else {
      lista.append(
        h('p', {
          clase: 't-meta',
          texto:
            'Sin identificador público, moderación no conoce esta cuenta: se suspende o se banea ' +
            'desde su gestión.',
        }),
      );
    }
    return lista;
  };
  pintarDesenlace(partes, yaCargado(ficha), pintar);
  return partes.elemento;
}

/** @param {{nombre: string, texto: string, href: string, detalle: string}} opciones */
function accion({ nombre, texto, href, detalle }) {
  return h('div', {
    clase: 'pila pila--ajustada',
    hijos: [
      h('a', {
        clase: 'boton boton--secundario boton--pequeno',
        texto,
        atributos: { href },
        datos: { accion: nombre },
      }),
      h('p', { clase: 't-meta', texto: detalle }),
    ],
  });
}

/**
 * Un panel que consulta un servicio por el `uid` de la cuenta. El
 * identificador no se pinta ni en el detalle técnico de un fallo: la
 * consulta se enseña con su plantilla.
 *
 * @param {{id: string, titulo: string, descripcion: string, servicio: string,
 *          recurso: (uid: string) => string, ancho?: 'completo'|null,
 *          pintar: (datos: *) => HTMLElement|HTMLElement[], vacio: *,
 *          alCargar: (resultado: {datos: *, falta: string|null}) => void,
 *          ficha: object, consultarApi: typeof consultar}} opciones
 */
function panelPorUid({
  id,
  titulo,
  descripcion,
  servicio,
  recurso,
  ancho = null,
  acciones = [],
  pintar,
  vacio,
  alCargar,
  ficha,
  consultarApi,
}) {
  const partes = panel({ id, titulo, descripcion, ancho, acciones });
  const controlador = {
    elemento: partes.elemento,
    zona: partes.zona,
    alReintentar: null,
    cargar: null,
  };

  controlador.cargar = async () => {
    if (!ficha.uid) {
      vaciar(partes.zona).append(
        aviso({
          tono: 'info',
          titulo: 'Sin identificador público',
          detalle:
            `La cuenta es anterior al identificador público y el servicio de ${servicio} solo ` +
            'conoce a las personas por él.',
        }),
      );
      partes.sello.replaceChildren(distintivoDeEstado({ texto: 'Sin dato', tono: 'neutro' }));
      alCargar({ datos: vacio, falta: null });
      return;
    }
    let desenlace = await consultarApi(recurso(encodeURIComponent(ficha.uid)));
    if (desenlace.resultado === RESULTADO.VACIO) {
      // Para esta sección, nada que enseñar es una respuesta: «no tiene».
      desenlace = { ...desenlace, resultado: RESULTADO.DATOS, datos: desenlace.datos ?? vacio };
    }
    pintarDesenlace(partes, desenlace, pintar, {
      alReintentar: async () => {
        await controlador.cargar();
        controlador.alReintentar?.();
      },
    });
    const conDatos = desenlace.resultado === RESULTADO.DATOS;
    alCargar({ datos: conDatos ? desenlace.datos : null, falta: conDatos ? null : id });
  };
  return controlador;
}

/** Un número que llegó, o null (el indicador pinta un guion), nunca un cero inventado. */
function cifra(valor) {
  return typeof valor === 'number' && Number.isFinite(valor) ? valor : null;
}

/**
 * «13 de oct»: una fecha que cabe en la cifra grande de un indicador; la fecha
 * entera va en su línea de detalle.
 *
 * @param {string} valor ISO-8601
 */
function diaYMes(valor) {
  const momento = new Date(valor);
  if (Number.isNaN(momento.getTime())) {
    return null;
  }
  return momento.toLocaleDateString('es-CO', { day: 'numeric', month: 'short' }).replace('.', '');
}

/**
 * @param {string} singular
 * @param {string} plural
 * @param {number} cuantos
 */
function contados(cuantos, singular, plural) {
  return `${cuantos} ${cuantos === 1 ? singular : plural}`;
}

/**
 * El resumen de una tabla con los más recientes de un total.
 *
 * @param {number} vistos
 * @param {number|null} total
 * @param {[string, string]} nombres singular y plural
 * @param {string} orden «del más reciente al más antiguo» o en femenino
 */
function resumenDeRecientes(vistos, total, [singular, plural], orden) {
  if (Number.isFinite(total) && total > vistos) {
    return `Los ${vistos} más recientes de ${total} ${plural}`;
  }
  return `${contados(Number.isFinite(total) ? total : vistos, singular, plural)}, ${orden}`;
}

/** @param {object|null} estadisticas `EstadisticasDeJugador` (salas-partidas 1.11.0) */
function pintarEstadisticas(estadisticas) {
  const ultima = estadisticas?.ultimaPartidaEn ?? null;
  const partes = [
    rejillaDeIndicadores(
      [
        tarjetaDeIndicador({
          etiqueta: 'Partidas terminadas',
          valor: cifra(estadisticas?.jugadas),
        }),
        tarjetaDeIndicador({ etiqueta: 'Victorias', valor: cifra(estadisticas?.victorias) }),
        tarjetaDeIndicador({ etiqueta: 'Derrotas', valor: cifra(estadisticas?.derrotas) }),
        tarjetaDeIndicador({ etiqueta: 'Empates', valor: cifra(estadisticas?.empates) }),
        tarjetaDeIndicador({ etiqueta: 'En curso', valor: cifra(estadisticas?.enCurso) }),
        // Sin línea de detalle, como las demás: así las seis cifras quedan a
        // la misma altura. El servicio dice «nulo si nunca jugó».
        tarjetaDeIndicador({
          etiqueta: 'Última partida',
          valor: ultima ? diaYMes(ultima) : 'Nunca',
        }),
      ],
      { zona: 'estadisticas' },
    ),
  ];
  if (estadisticas?.parcial) {
    partes.push(
      aviso({
        tono: 'info',
        titulo: 'Cuenta con más de mil partidas',
        detalle: 'Estas cifras salen de sus mil partidas más recientes.',
      }),
    );
  }
  const porModalidad = Object.entries(estadisticas?.porModalidad ?? {});
  if (porModalidad.length === 0) {
    partes.push(h('p', { clase: 't-meta', texto: 'Todavía no ha terminado ninguna partida.' }));
    return partes;
  }
  partes.push(
    tabla({
      columnas: [
        'Modalidad',
        { titulo: 'Terminadas', tipo: 'numero' },
        { titulo: 'Victorias', tipo: 'numero' },
        { titulo: 'Derrotas', tipo: 'numero' },
        { titulo: 'Empates', tipo: 'numero' },
      ],
      filas: porModalidad.map(([modalidad, cuenta]) => [
        nombreDeModalidad(modalidad, modalidad),
        cifra(cuenta?.jugadas),
        cifra(cuenta?.victorias),
        cifra(cuenta?.derrotas),
        cifra(cuenta?.empates),
      ]),
      resumen: 'Partidas terminadas por modalidad',
    }),
  );
  return partes;
}

/** @param {object|null} pagina `PaginaDePartidas` */
function pintarPartidas(pagina) {
  const lista = filasDe(pagina);
  if (lista.length === 0) {
    return h('p', { clase: 't-meta', texto: 'No ha jugado ninguna partida.' });
  }
  return tabla({
    columnas: [
      { titulo: 'Empezó', tipo: 'fecha' },
      'Modalidad',
      'Héroe',
      { titulo: 'Jugadores', tipo: 'numero' },
      { titulo: 'Resultado', tipo: 'estado' },
    ],
    filas: lista.map((partida) => [
      partida.iniciadaEn ? fechaHora(partida.iniciadaEn) : null,
      nombreDeModalidad(partida.modalidad, null),
      partida.heroe || null,
      cifra(partida.participantes),
      distintivoDeResultado(partida),
    ]),
    resumen: resumenDeRecientes(
      lista.length,
      cifra(pagina?.totalElementos),
      ['partida', 'partidas'],
      'de la más reciente a la más antigua',
    ),
  });
}

/** @param {object|null} saldo `Saldo` (creditos 1.5.0) */
function pintarSaldo(saldo) {
  return rejillaDeIndicadores(
    [
      tarjetaDeIndicador({
        etiqueta: 'Disponible',
        valor: cifra(saldo?.saldoDisponible),
        unidad: 'créditos',
        detalle: 'Lo que puede gastar o apostar.',
      }),
      tarjetaDeIndicador({
        etiqueta: 'Apartado',
        valor: cifra(saldo?.saldoReservado),
        unidad: 'créditos',
        detalle: 'Reservado en apuestas y pujas abiertas.',
      }),
      tarjetaDeIndicador({
        etiqueta: 'Total',
        valor: cifra(saldo?.saldoBruto),
        unidad: 'créditos',
        detalle: 'Lo disponible más lo apartado.',
      }),
    ],
    { zona: 'saldo' },
  );
}

/** @param {object|null} pagina página de `MovimientoDeCredito` */
function pintarMovimientos(pagina) {
  const lista = filasDe(pagina);
  if (lista.length === 0) {
    return h('p', { clase: 't-meta', texto: 'Su saldo no se ha movido todavía.' });
  }
  return tabla({
    columnas: [
      { titulo: 'Fecha', tipo: 'fecha' },
      'Concepto',
      { titulo: 'Importe', tipo: 'numero' },
    ],
    filas: lista.map((movimiento) => {
      const importe = comoImporte(movimiento);
      return [
        movimiento.creado ? fechaHora(movimiento.creado) : null,
        nombreDelConcepto(movimiento.concepto),
        h('span', {
          clase: 'movimiento__importe',
          texto: importe.texto,
          datos: { tono: importe.tono },
        }),
      ];
    }),
    resumen: resumenDeRecientes(
      lista.length,
      cifra(pagina?.totalElements),
      ['movimiento', 'movimientos'],
      'del más reciente al más antiguo',
    ),
  });
}

/** @param {object[]} sanciones `Sancion` de moderacion-sanciones-admin.yaml */
function pintarSanciones(sanciones) {
  const lista = Array.isArray(sanciones) ? sanciones : [];
  const estado = estadoDeCuenta(lista, {
    persona: 'tercera',
    titulo: 'Estado disciplinario de la cuenta',
  });
  if (lista.length === 0) {
    return [estado, h('p', { clase: 't-meta', texto: 'No tiene sanciones ni advertencias.' })];
  }
  return [
    estado,
    tabla({
      columnas: ['Tipo', 'Emitida', 'Situación', 'Motivo'],
      filas: lista.map((s) => [
        NOMBRE_DE_SANCION[s.tipo] ?? s.tipo ?? null,
        s.emitidaEn ? fechaHora(s.emitidaEn) : null,
        situacionDe(s),
        s.motivo || null,
      ]),
      resumen: `${lista.length} ${lista.length === 1 ? 'registro' : 'registros'}, del más reciente al más antiguo`,
    }),
  ];
}

/**
 * Lo que la sanción significa hoy, según `vigente` del servidor: aquí no se
 * recalcula ninguna regla.
 *
 * @param {{tipo?: string, vigente?: boolean, revertidaEn?: string|null, vigenteHasta?: string|null}} s
 */
function situacionDe(s) {
  if (s.revertidaEn) {
    return 'Revertida';
  }
  if (s.tipo === 'ADVERTENCIA') {
    return 'No restringe el acceso';
  }
  if (s.tipo === 'BANEO') {
    return s.vigente ? 'Vigente, sin fecha de fin' : 'Sin efecto';
  }
  if (s.tipo === 'SUSPENSION') {
    if (!s.vigente) {
      return 'Cumplida';
    }
    return s.vigenteHasta ? `Vigente hasta ${fechaHora(s.vigenteHasta)}` : 'Vigente';
  }
  return s.vigente ? 'Vigente' : 'Sin efecto';
}

/** @param {{comentarios?: object[], total?: number}} historial `HistorialDelAutorResponse` */
function pintarComentarios(historial) {
  const lista = Array.isArray(historial?.comentarios) ? historial.comentarios : [];
  if (lista.length === 0) {
    return h('p', { clase: 't-meta', texto: 'No ha publicado comentarios.' });
  }
  const total = Number.isFinite(historial?.total) ? historial.total : lista.length;
  return tabla({
    columnas: ['Publicado', 'Estado', 'Comentario'],
    filas: lista.map((c) => [
      c.fechaPublicacion ? fechaHora(c.fechaPublicacion) : null,
      `${ESTADO_LEGIBLE[c.estado] ?? (c.estado || SIN_DATO)}${c.editado ? ' · editado por moderación' : ''}`,
      extracto(c.texto) || null,
    ]),
    resumen:
      total > lista.length
        ? `Los ${lista.length} más recientes de ${total} comentarios`
        : `${total} ${total === 1 ? 'comentario' : 'comentarios'}, del más reciente al más antiguo`,
  });
}

/**
 * Los reportes de una dirección. De uno recibido no se pinta quién lo hizo:
 * el servicio no lo publica, y la ficha no lo deduce.
 *
 * @param {object|null} respuesta `ReportesDelUsuarioResponse` (comentarios 1.12.0)
 * @param {'REALIZADOS'|'RECIBIDOS'} direccion
 */
function pintarReportes(respuesta, direccion) {
  const lista = Array.isArray(respuesta?.reportes) ? respuesta.reportes : [];
  const recibidos = direccion === 'RECIBIDOS';
  if (lista.length === 0) {
    return h('p', {
      clase: 't-meta',
      texto: recibidos
        ? 'Nadie ha reportado sus comentarios.'
        : 'No ha reportado ningún comentario.',
    });
  }
  const columnas = [
    { titulo: 'Fecha', tipo: 'fecha' },
    'Categoría',
    ...(recibidos ? [] : ['Autor del comentario']),
    'Comentario',
    'Estado del comentario',
    'Descripción del reporte',
  ];
  return tabla({
    columnas,
    filas: lista.map((reporte) => {
      const comentario = reporte.comentario ?? null;
      return [
        reporte.fecha ? fechaHora(reporte.fecha) : null,
        nombreDeCategoria(reporte.categoria),
        ...(recibidos ? [] : [comentario?.apodoAutor || null]),
        comentario ? extracto(comentario.extracto) || null : 'El comentario ya no existe',
        comentario ? (ESTADO_LEGIBLE[comentario.estado] ?? (comentario.estado || SIN_DATO)) : null,
        reporte.descripcion?.trim() || 'Sin descripción',
      ];
    }),
    resumen: resumenDeRecientes(
      lista.length,
      cifra(respuesta?.total),
      ['reporte', 'reportes'],
      'del más reciente al más antiguo',
    ),
  });
}

/**
 * Dónde está hoy cada producto: en una subasta, en una misión, puesto en un
 * héroe o libre. Lo dice el inventario, aquí solo se lee.
 *
 * @param {object} elemento `ElementoInventario` (inventario 1.8.0)
 */
function situacionDelElemento(elemento) {
  if (elemento?.subastaId) {
    return { texto: 'En subasta', tono: 'advertencia' };
  }
  if (elemento?.ejecucionMisionId) {
    return { texto: 'En una misión', tono: 'info' };
  }
  if (elemento?.equipadoEn?.heroeId) {
    const heroe = elemento.equipadoEn.nombreHeroe;
    return { texto: heroe ? `Equipado en ${heroe}` : 'Equipado', tono: 'neutro' };
  }
  return elemento?.disponible === false
    ? { texto: 'No disponible', tono: 'neutro' }
    : { texto: 'Disponible', tono: 'exito' };
}

/**
 * «Entregar productos» (SA-10, §7.3.4): abre el panel de la entrega y, hecha,
 * vuelve a pedir el inventario y dice qué se entregó.
 *
 * @param {object} ficha
 * @param {() => {cargar: () => Promise<void>, zona: HTMLElement}|undefined} controladorDelPanel
 */
function botonDeEntrega(ficha, controladorDelPanel) {
  const boton = h('button', {
    clase: 'boton boton--secundario boton--pequeno',
    texto: 'Entregar productos',
    atributos: { type: 'button' },
    datos: { accion: 'entregar-productos' },
  });
  boton.addEventListener('click', () => {
    abrirEntregaAdministrativa({
      ficha,
      alEntregar: async (entrega) => {
        const controlador = controladorDelPanel();
        if (!controlador) {
          return;
        }
        await controlador.cargar();
        const unidades = Number(entrega?.cantidad) || 0;
        controlador.zona.prepend(
          aviso({
            tono: 'exito',
            titulo: entrega?.nueva === false ? 'Esa entrega ya estaba hecha' : 'Entrega hecha',
            detalle:
              entrega?.nueva === false
                ? 'No se repitió: sus productos siguen como estaban.'
                : `${unidades} × ${entrega?.nombre ?? 'producto'} ya ${unidades === 1 ? 'está' : 'están'} en su inventario. Queda en la auditoría.`,
          }),
        );
        boton.focus();
      },
    });
  });
  return boton;
}

/** @param {object|null} pagina `PaginaInventario` (inventario 1.8.0) */
function pintarInventario(pagina) {
  const lista = Array.isArray(pagina?.elementos) ? pagina.elementos : [];
  if (lista.length === 0) {
    return h('p', { clase: 't-meta', texto: 'No tiene héroes ni objetos.' });
  }
  const total = cifra(pagina?.totalElementos);
  return tabla({
    columnas: [
      'Nombre',
      'Tipo',
      { titulo: 'Nivel', tipo: 'numero' },
      { titulo: 'Situación', tipo: 'estado' },
    ],
    filas: lista.map((elemento) => [
      elemento.nombrePropio || null,
      nombreDelTipo(elemento.tipo),
      // El nivel es solo de los héroes (§6.1.1): en un objeto no falta, no aplica.
      elemento.tipo === 'HEROE' ? cifra(elemento.nivel) : 'No aplica',
      distintivoDeEstado(situacionDelElemento(elemento)),
    ]),
    resumen:
      Number.isFinite(total) && total > lista.length
        ? `Los primeros ${lista.length} de ${total} productos`
        : contados(Number.isFinite(total) ? total : lista.length, 'producto', 'productos'),
  });
}

/** @param {object|null} pagina `PaginaDeAuditoria` */
function pintarAuditoriaDeLaCuenta(pagina) {
  const lista = filasDe(pagina);
  if (lista.length === 0) {
    return h('p', {
      clase: 't-meta',
      texto: 'La auditoría no tiene acciones administrativas sobre esta cuenta.',
    });
  }
  return tabla({
    columnas: [{ titulo: 'Fecha', tipo: 'fecha' }, 'Acción', 'Quién', 'Cambio', 'Motivo'],
    filas: lista.map((registro) => [
      registro.fechaHora ? fechaHora(registro.fechaHora) : null,
      nombreDeAccion(registro.tipoAccion),
      registro.administrador || null,
      cambioDe(registro),
      registro.motivo || null,
    ]),
    resumen: resumenDeRecientes(
      lista.length,
      cifra(pagina?.totalElements),
      ['acción', 'acciones'],
      'de la más reciente a la más antigua',
    ),
  });
}

/** «antes → después», lo que haya de los dos, o null (la tabla dice «Sin dato»). */
function cambioDe(registro) {
  const antes = registro?.valorAnterior ? String(registro.valorAnterior) : '';
  const despues = registro?.valorNuevo ? String(registro.valorNuevo) : '';
  if (antes && despues) {
    return `${antes} → ${despues}`;
  }
  return despues || antes || null;
}

/* -------------------------------------------------------------------------
   Línea de tiempo
   ------------------------------------------------------------------------- */

/**
 * Los hechos de lo que se cargó, con su familia, y la lista de lo que no
 * respondió.
 *
 * @param {object} ficha
 * @param {Array<{id: string, enLaLinea: {nombre: string, categoria: string,
 *          hechos: (datos: *, ahora: number) => object[]}|null}>} fuentes
 * @param {Map<string, {datos: *, falta: string|null}>} cargados
 * @param {number} ahora
 */
function armarLinea(ficha, fuentes, cargados, ahora) {
  const hechos = hechosDeLaCuenta(ficha).map((hecho) => conCategoria(hecho, 'cuenta'));
  const faltan = [];
  for (const fuente of fuentes) {
    if (!fuente.enLaLinea) {
      continue;
    }
    const cargado = cargados.get(fuente.id);
    if (cargado?.falta) {
      faltan.push(fuente.enLaLinea.nombre);
    } else if (cargado?.datos) {
      hechos.push(
        ...fuente.enLaLinea
          .hechos(cargado.datos, ahora)
          .map((hecho) => conCategoria(hecho, fuente.enLaLinea.categoria)),
      );
    }
  }
  return { hechos, faltan };
}

/** @param {object} hecho @param {string} categoria */
function conCategoria(hecho, categoria) {
  return { ...hecho, datos: { ...(hecho.datos ?? {}), categoria } };
}

function panelDeLaLinea() {
  const filtro = h('select', {
    clase: 'campo__control',
    atributos: { id: 'ficha-linea-filtro', name: 'familia' },
    datos: { zona: 'filtro-linea' },
    hijos: [
      h('option', { texto: 'Todo', atributos: { value: '' } }),
      ...CATEGORIAS_DE_LA_LINEA.map(({ valor, etiqueta }) =>
        h('option', { texto: etiqueta, atributos: { value: valor } }),
      ),
    ],
  });
  const partes = panel({
    id: 'linea-de-tiempo',
    titulo: 'Actividad de la cuenta',
    descripcion: 'Los hechos con fecha de todas las secciones, el más reciente primero.',
    ancho: 'completo',
    acciones: [
      h('label', {
        clase: 'desplegable',
        atributos: { for: 'ficha-linea-filtro' },
        hijos: [h('span', { clase: 'campo__etiqueta', texto: 'Mostrar' }), filtro],
      }),
    ],
  });
  /** @type {{hechos: object[], faltan: string[]}} */
  let vigente = { hechos: [], faltan: [] };

  const pintar = (linea = vigente) => {
    vigente = linea;
    const { hechos, faltan } = linea;
    const familia = filtro.value;
    const visibles = familia
      ? hechos.filter((hecho) => hecho.datos?.categoria === familia)
      : hechos;
    vaciar(partes.zona);
    partes.sello.replaceChildren(
      ...(faltan.length > 0
        ? [distintivoDeEstado({ texto: 'Incompleta', tono: 'advertencia' })]
        : []),
    );
    partes.zona.append(
      visibles.length > 0
        ? lineaDeTiempo(visibles, {
            etiqueta: 'Actividad de la cuenta, de lo más reciente a lo más antiguo',
          })
        : h('p', {
            clase: 't-meta',
            texto: familia
              ? 'No hay hechos de este tipo con fecha.'
              : 'No hay hechos con fecha que mostrar.',
          }),
    );
    if (faltan.length > 0) {
      partes.zona.append(
        h('p', {
          clase: 't-meta',
          datos: { zona: 'faltan' },
          texto: `Faltan los hechos de: ${faltan.join(', ')} (su servicio no respondió).`,
        }),
      );
    }
  };
  filtro.addEventListener('change', () => pintar());
  return { elemento: partes.elemento, pintar };
}
