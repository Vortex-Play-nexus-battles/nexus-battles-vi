/**
 * Ficha administrativa del usuario — HU-USR-010 (RF-USR-010, §7.3.4
 * «perfil administrativo completo del usuario»).
 *
 * ## Qué junta y de dónde
 *
 * Una cuenta, vista por cada servicio que es dueño de algo suyo, sin que el
 * navegador hable con ninguna base de datos ni ningún servicio lea las tablas
 * de otro:
 *
 * | Sección                  | Fuente                                                        |
 * |--------------------------|---------------------------------------------------------------|
 * | Datos de la cuenta       | `GET /admin/usuarios/{usuario}/ficha` (ms-identidad-admin 1.4.0) |
 * | Sanciones y advertencias | `GET /sanciones/usuarios/{uid}` (moderacion-sanciones-admin)  |
 * | Comentarios publicados   | `GET /comentarios/moderacion/autores/{uid}/comentarios` (comentarios 1.7.0) |
 *
 * Lo demás que pide la ficha oficial —reportes, transacciones, subastas,
 * misiones, estadísticas y la auditoría sobre la cuenta— no lo publica hoy
 * ningún servicio para administración (cada uno lo explica en `SIN_FUENTE`).
 * La pantalla lo dice: «Sin fuente publicada». Un hueco con su razón es
 * información; una tabla inventada sería una mentira operativa.
 *
 * ## Las reglas de la historia
 *
 * - CA-01/CA-04: se abre con la clave de la cuenta (la del directorio) o con
 *   su `uid` (lo único que la cola de comentarios conoce del autor, CA-02) y
 *   consolida lo que hay, con una línea de tiempo de la actividad.
 * - CA-03: si la cuenta no existe, el problem details de identidad lo dice y
 *   no se consulta nada más; si un servicio no responde, su sección dice
 *   «no disponible» y el resto sigue (cada panel carga por su cuenta, como en
 *   Control integral).
 * - Observación de la ficha: abrirla queda en la auditoría porque son datos
 *   personales. Lo registra ms-identidad al servir la ficha; si no pudo, la
 *   pantalla lo advierte.
 *
 * @module plataforma/moderacion-sanciones/ficha-usuario
 */

import { consultar, RESULTADO } from '../consola/cliente-consola.js';
import { moduloNoImplementado, panel, pintarDesenlace, tabla } from '../consola/panel.js';
import { ESTADO_LEGIBLE } from '../comentarios/moderar-comentarios.js';
import { urlDeVista } from '../../comun/acceso.js';
import { rutaDeApi } from '../../comun/base-api.js';
import { fetchWithHttpErrorInterceptor } from '../../comun/interceptors/http-error.interceptor.js';
import { NOMBRE_DE_ROL } from '../../comun/shell.js';
import { aviso } from '../../comun/ui/aviso.js';
import { distintivo } from '../../comun/ui/distintivo.js';
import { h, vaciar } from '../../comun/ui/dom.js';
import {
  estadoDeCarga,
  estadoDeError,
  estadoVacio,
  pintarEstado,
} from '../../comun/ui/estado-vista.js';
import { fechaHora } from '../../comun/ui/formato.js';
import { lineaDeTiempo } from '../../comun/ui/linea-de-tiempo.js';
import { encabezadoDePagina } from '../../comun/ui/pagina.js';
import { estadoDeCuenta, hechosDeSanciones, NOMBRE_DE_SANCION } from '../../comun/ui/sancion.js';
import { textoDelServidor } from '../../comun/ui/texto-de-fallo.js';

/** La clave interna que publica el directorio: un entero positivo. */
const CLAVE = /^[1-9]\d{0,17}$/;
const UID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Lo que se pinta cuando la fuente no trae el dato. */
export const SIN_DATO = 'sin dato';

/** Comentarios que se piden: la primera página del historial del autor. */
export const COMENTARIOS_POR_PAGINA = 20;

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

/**
 * Lo que la ficha oficial pide y ningún servicio publica hoy para
 * administración, con la razón dicha para quien opera. Revisado contra
 * `contracts/openapi/` (HU-USR-010): si un servicio publica la consulta, la
 * sección sale de esta lista y pasa a tener panel con datos.
 */
export const SIN_FUENTE = Object.freeze([
  {
    id: 'reportes',
    titulo: 'Reportes hechos y recibidos',
    razon:
      'El servicio de comentarios no publica los reportes por persona: ni los que hizo ni los ' +
      'que recibieron sus comentarios. Se ven, comentario a comentario, en la cola de moderación.',
  },
  {
    id: 'transacciones',
    titulo: 'Transacciones',
    razon:
      'El servicio de finanzas solo deja consultar los movimientos de un jugador a ese mismo ' +
      'jugador o a otro servicio; no hay una consulta para administración.',
  },
  {
    id: 'subastas',
    titulo: 'Subastas',
    razon:
      'El servicio de subastas publica el mercado y lo de quien tiene la sesión, pero no las ' +
      'subastas o pujas de otra persona.',
  },
  {
    id: 'misiones',
    titulo: 'Misiones',
    razon:
      'El servicio de misiones publica el tablón, las misiones en curso y el historial de quien ' +
      'tiene la sesión, no los de otra persona.',
  },
  {
    id: 'estadisticas',
    titulo: 'Estadísticas de juego',
    razon:
      'Las partidas y su resultado solo se publican para quien las jugó; no hay una consulta ' +
      'por jugador para administración.',
  },
  {
    id: 'auditoria',
    titulo: 'Auditoría sobre esta cuenta',
    razon:
      'La auditoría se consulta por administrador, tipo de acción y fechas, pero no por la ' +
      'persona afectada: no hay forma de traer solo lo que toca a esta cuenta.',
  },
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

/** @param {string|null|undefined} texto */
function extracto(texto) {
  const limpio = String(texto ?? '').trim();
  return limpio.length > LARGO_DEL_EXTRACTO
    ? `${limpio.slice(0, LARGO_DEL_EXTRACTO - 1).trimEnd()}…`
    : limpio;
}

/* -------------------------------------------------------------------------
   Montaje
   ------------------------------------------------------------------------- */

/**
 * Monta la ficha.
 *
 * @param {Document|HTMLElement} raiz con las zonas `encabezado`, `aviso` y `ficha`
 * @param {{clave?: string|null, buscar?: Function, consultarApi?: typeof consultar,
 *          ahora?: () => number}} [opciones] inyectables en pruebas
 * @returns {Promise<object|null>} el desenlace de la ficha de identidad
 */
export function montarFichaDeUsuario(
  raiz,
  {
    clave = claveDeLaDireccion(),
    buscar = fetchWithHttpErrorInterceptor,
    consultarApi = consultar,
    ahora = () => Date.now(),
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
          'La ficha se abre desde una fila del directorio de Control integral o desde el autor ' +
          'de un comentario reportado.',
        accion: {
          texto: 'Ir al directorio',
          href: urlDeVista('control-integral'),
          nombre: 'ir-al-directorio',
        },
      }),
    );
    return Promise.resolve(null);
  }

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
    await pintarSecciones(zonaFicha, desenlace.ficha, { consultarApi, ahora });
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
        'Lo que cada servicio publica de esta cuenta, en un solo sitio y con una línea de ' +
        'tiempo de su actividad. Cada sección dice de dónde sale su dato.',
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
 * Pinta las cuatro secciones y espera a las dos que consultan otros servicios
 * para armar la línea de tiempo. Cada panel carga por su cuenta: que
 * moderación tarde no retrasa a comentarios.
 *
 * @param {HTMLElement} zona
 * @param {object} ficha
 * @param {{consultarApi: typeof consultar, ahora: () => number}} opciones
 */
async function pintarSecciones(zona, ficha, { consultarApi, ahora }) {
  const linea = panelDeLaLinea();
  const cargados = { sanciones: null, comentarios: null };
  const repintarLinea = () => linea.pintar(armarLinea(ficha, cargados, ahora()));

  const sanciones = panelDeSanciones(ficha, consultarApi, (resultado) => {
    cargados.sanciones = resultado;
  });
  const comentarios = panelDeComentarios(ficha, consultarApi, (resultado) => {
    cargados.comentarios = resultado;
  });
  // Un reintento que trae los datos tambien los suma a la linea.
  sanciones.alReintentar = repintarLinea;
  comentarios.alReintentar = repintarLinea;

  vaciar(zona);
  zona.append(
    seccion('cuenta', 'Cuenta', [panelDeLaCuenta(ficha), panelDeAcciones(ficha)]),
    seccion('historial', 'Sanciones y comentarios', [sanciones.elemento, comentarios.elemento]),
    seccion('actividad', 'Línea de tiempo', [linea.elemento]),
    seccion('sin-fuente', 'Sin fuente publicada', SIN_FUENTE.map(panelSinFuente)),
  );

  await Promise.all([sanciones.cargar(), comentarios.cargar()]);
  repintarLinea();
}

/** @param {string} id @param {string} etiqueta @param {HTMLElement[]} paneles */
function seccion(id, etiqueta, paneles) {
  return h('section', {
    clase: 'consola-integral__seccion',
    datos: { seccion: id },
    atributos: { 'aria-label': etiqueta },
    hijos: [
      h('h2', { clase: 't-subtitulo', texto: etiqueta }),
      h('div', { clase: 'consola-integral__rejilla', hijos: paneles }),
    ],
  });
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
    fuente: 'GET /api/v1/admin/usuarios/{usuario}/ficha',
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
    lista.append(
      h('p', {
        clase: 't-meta',
        texto: 'Ver o modificar su inventario: ningún servicio lo publica para administración.',
      }),
    );
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
 * @param {{id: string, titulo: string, descripcion: string, plantilla: string,
 *          servicio: string, recurso: (uid: string) => string,
 *          pintar: (datos: *) => HTMLElement|HTMLElement[], vacio: *,
 *          alCargar: (resultado: {datos: *, falta: string|null}) => void,
 *          ficha: object, consultarApi: typeof consultar}} opciones
 */
function panelPorUid({
  id,
  titulo,
  descripcion,
  plantilla,
  servicio,
  recurso,
  pintar,
  vacio,
  alCargar,
  ficha,
  consultarApi,
}) {
  const partes = panel({ id, titulo, descripcion, fuente: `GET /api/v1${plantilla}` });
  const controlador = { elemento: partes.elemento, alReintentar: null, cargar: null };

  controlador.cargar = async () => {
    if (!ficha.uid) {
      vaciar(partes.zona).append(
        moduloNoImplementado({
          titulo: 'Sin identificador público',
          razon:
            `La cuenta no tiene identificador público (es anterior a él) y el servicio de ` +
            `${servicio} solo conoce a las personas por ese identificador.`,
        }),
      );
      partes.sello.dataset.estado = 'neutro';
      partes.sello.textContent = 'SIN DATO';
      alCargar({ datos: vacio, falta: null });
      return;
    }
    let desenlace = await consultarApi(recurso(encodeURIComponent(ficha.uid)));
    if (desenlace.resultado === RESULTADO.VACIO) {
      // Para esta sección, nada que enseñar es una respuesta: «no tiene».
      desenlace = { ...desenlace, resultado: RESULTADO.DATOS, datos: desenlace.datos ?? vacio };
    }
    pintarDesenlace(partes, { ...desenlace, recurso: plantilla }, pintar, {
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

/** @param {object} ficha @param {typeof consultar} consultarApi @param {Function} alCargar */
function panelDeSanciones(ficha, consultarApi, alCargar) {
  return panelPorUid({
    id: 'sanciones',
    titulo: 'Sanciones y advertencias',
    descripcion: 'El historial disciplinario que publica el servicio de moderación.',
    plantilla: '/sanciones/usuarios/{uid}',
    servicio: 'moderación',
    recurso: (uid) => `/sanciones/usuarios/${uid}`,
    pintar: pintarSanciones,
    vacio: [],
    alCargar,
    ficha,
    consultarApi,
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
        NOMBRE_DE_SANCION[s.tipo] ?? s.tipo ?? SIN_DATO,
        s.emitidaEn ? fechaHora(s.emitidaEn) : SIN_DATO,
        situacionDe(s),
        s.motivo || SIN_DATO,
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

/** @param {object} ficha @param {typeof consultar} consultarApi @param {Function} alCargar */
function panelDeComentarios(ficha, consultarApi, alCargar) {
  return panelPorUid({
    id: 'comentarios',
    titulo: 'Comentarios publicados',
    descripcion:
      'Los más recientes, en cualquier estado: también los que moderación ocultó o eliminó.',
    plantilla: '/comentarios/moderacion/autores/{uid}/comentarios',
    servicio: 'comentarios',
    recurso: (uid) =>
      `/comentarios/moderacion/autores/${uid}/comentarios?pagina=0&tamano=${COMENTARIOS_POR_PAGINA}`,
    pintar: pintarComentarios,
    vacio: { comentarios: [], total: 0 },
    alCargar,
    ficha,
    consultarApi,
  });
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
      c.fechaPublicacion ? fechaHora(c.fechaPublicacion) : SIN_DATO,
      `${ESTADO_LEGIBLE[c.estado] ?? (c.estado || SIN_DATO)}${c.editado ? ' · editado por moderación' : ''}`,
      extracto(c.texto) || SIN_DATO,
    ]),
    resumen:
      total > lista.length
        ? `Los ${lista.length} más recientes de ${total} comentarios`
        : `${total} ${total === 1 ? 'comentario' : 'comentarios'}, del más reciente al más antiguo`,
  });
}

/** @param {{id: string, titulo: string, razon: string}} seccionSinFuente */
function panelSinFuente({ id, titulo, razon }) {
  const partes = panel({ id, titulo });
  vaciar(partes.zona).append(moduloNoImplementado({ titulo: 'Sin fuente publicada', razon }));
  partes.sello.dataset.estado = 'neutro';
  partes.sello.textContent = 'SIN FUENTE';
  return partes.elemento;
}

/* -------------------------------------------------------------------------
   Línea de tiempo
   ------------------------------------------------------------------------- */

/** Nombre de cada sección en la nota de lo que falta. */
const NOMBRE_EN_LA_LINEA = Object.freeze({ sanciones: 'sanciones', comentarios: 'comentarios' });

/**
 * Los hechos de lo que se cargó y la lista de lo que no respondió.
 *
 * @param {object} ficha
 * @param {{sanciones: {datos: *, falta: string|null}|null, comentarios: {datos: *, falta: string|null}|null}} cargados
 * @param {number} ahora
 */
function armarLinea(ficha, cargados, ahora) {
  const hechos = [...hechosDeLaCuenta(ficha)];
  const faltan = [];
  const { sanciones, comentarios } = cargados;
  if (sanciones?.falta) {
    faltan.push(NOMBRE_EN_LA_LINEA.sanciones);
  } else if (Array.isArray(sanciones?.datos)) {
    hechos.push(...hechosDeSanciones(sanciones.datos, { ahora }));
  }
  if (comentarios?.falta) {
    faltan.push(NOMBRE_EN_LA_LINEA.comentarios);
  } else if (comentarios?.datos) {
    hechos.push(...hechosDeComentarios(comentarios.datos));
  }
  return { hechos, faltan };
}

function panelDeLaLinea() {
  const partes = panel({
    id: 'linea-de-tiempo',
    titulo: 'Actividad de la cuenta',
    descripcion: 'Los hechos con fecha de las secciones de arriba, el más reciente primero.',
  });
  /** @param {{hechos: object[], faltan: string[]}} linea */
  const pintar = ({ hechos, faltan }) => {
    vaciar(partes.zona);
    partes.sello.dataset.estado = faltan.length > 0 ? 'aviso' : 'ok';
    partes.sello.textContent = faltan.length > 0 ? 'PARCIAL' : 'EN LINEA';
    partes.zona.append(
      hechos.length > 0
        ? lineaDeTiempo(hechos, {
            etiqueta: 'Actividad de la cuenta, de lo más reciente a lo más antiguo',
          })
        : h('p', { clase: 't-meta', texto: 'No hay hechos con fecha que mostrar.' }),
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
    partes.zona.append(
      h('p', {
        clase: 't-meta',
        texto:
          'Entran la cuenta, las sanciones y los comentarios: el resto de secciones no tiene ' +
          'fuente publicada.',
      }),
    );
  };
  return { elemento: partes.elemento, pintar };
}
