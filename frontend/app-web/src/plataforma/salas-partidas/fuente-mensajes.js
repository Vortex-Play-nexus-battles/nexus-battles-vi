/**
 * Mensajes privados entre jugadores — el puerto de la interfaz (UXC-6) y su
 * adaptador contra el servicio (B6).
 *
 * FEEDBACK DEL PROFESOR, no requisito del documento: el 7.6 pide chat en las
 * salas y en la vista general; los mensajes privados los pidió el docente en
 * la demostración y van sobre ese mismo chat (salas-partidas, el mismo canal
 * STOMP y las mismas reglas de lista negra, sanción y fallo cerrado).
 *
 * ## El puerto
 *
 * La interfaz (`mensajes-privados.js`) se hizo contra ESTA forma
 * (`FuenteDeMensajes`) cuando todavía no había servicio, y no sabe nada de
 * rutas ni de STOMP. Lo que se exporta ahora es un adaptador que la cumple
 * contra tres contratos:
 *
 *   - `contracts/openapi/salas-partidas.yaml` 1.8.0, `/mensajes-directos/**`:
 *     bandeja, historial paginado hacia atrás, envío de respaldo, leído y
 *     bloqueo;
 *   - `contracts/websocket/mensajes-directos.yaml` 1.1.0: envío por
 *     `/app/mensajes-directos/{uid}` con `idCliente`, y mensajes y rechazos
 *     por la cola de usuario `/usuario/cola/mensajes-directos`;
 *   - `contracts/openapi/ms-identidad-perfiles.yaml` 1.1.0,
 *     `GET /api/v1/perfiles/publicos?apodo=`: a quién se le puede escribir.
 *
 * ## Cómo encaja el servicio en el puerto
 *
 *   - **Una conversación es el otro jugador**: `conversacionId` es su `uid`.
 *     Es lo único que piden las rutas y el destino STOMP; la clave canónica
 *     `dm:{menor}:{mayor}` es cosa del servidor y aquí no hace falta.
 *   - **Enviar**: por el canal, con un `idCliente` para reconocer el eco. Si
 *     el canal no está, o el eco no llega a tiempo, por el `POST` de respaldo
 *     con el MISMO `idCliente`: el servidor no lo guarda dos veces. Un envío
 *     que falló sin saber si llegó se reintenta con ese mismo `idCliente`.
 *   - **Rechazos** (lista negra, sanción, límite de frecuencia…): llegan por
 *     la cola o como problem details, y `enviar` rechaza con un
 *     `FalloDeMensajes` que trae el `motivo` del contrato, el `detalle` en
 *     palabras de jugador —lo que enseña la vista— y si reintentar tiene
 *     sentido (`reintentable`).
 *   - **Tiempo real**: `escuchar` abre el canal del chat (`cliente-chat.js`,
 *     con el JWT en el CONNECT) con su reconexión
 *     (`comun/canal-reconectable.js`) y va contando su estado. Mientras no
 *     hay canal pregunta por REST cada poco (riesgo #7 del Project Charter:
 *     degradación controlada, nunca en silencio) y, al volver, se pone al día
 *     ANTES de decir «reconectado». Tras la primera suscripción también se
 *     pone al día: lo que llegó entre la primera bandeja y la suscripción no
 *     se pierde.
 *
 * ## Bloquear (D-40, provisional)
 *
 *   - `bloquear(uid, true)` hace `PUT …/{uid}/bloqueo` y `bloquear(uid,
 *     false)`, `DELETE`; las dos devuelven la conversación con el `estado`
 *     que dice el servidor (`BLOQUEADA`, `ACTIVA` o, si el otro también
 *     bloqueó, `NO_ADMITE`).
 *   - El `estado` de cada conversación viene en la bandeja. Una conversación
 *     nueva (desde la búsqueda) lo pregunta con `GET …/{uid}/bloqueo`.
 *   - Si un envío se rechaza por un bloqueo (`CONVERSACION_BLOQUEADA`,
 *     `NO_ADMITE`), el fallo trae el `estadoDeConversacion` nuevo para que la
 *     vista deje de ofrecer el campo: pasa si te bloquean con la
 *     conversación abierta.
 *
 * ## Lo que el servicio no tiene, y cómo se dice
 *
 *   - **Acuse de lectura del otro**: el servidor no lo publica (el `leido` de
 *     un mensaje propio es «leído por quien consulta»). Lo tuyo se queda en
 *     «Enviado»: nunca se pinta un «Leído» que nadie confirmó, y no hay
 *     eventos `leido`.
 *   - **Cuenta del otro sancionada** (`CUENTA_SANCIONADA`): el servicio no
 *     la expone en la bandeja; un destinatario que no existe o no está activo
 *     lo dice el rechazo al escribirle.
 *   - **Tu silencio** (`restriccion`): el servicio no lo expone; es `null`, y
 *     si escribes con una sanción activa lo dice el rechazo `SANCIONADO`.
 *
 * ## Si el servicio no responde
 *
 * `fuenteDeMensajes()` pregunta primero por la bandeja y solo si contesta
 * bien devuelve el adaptador; si no (sin red, el borde da 502 o 404, o tarda
 * más de `ESPERA_DE_LA_SONDA_MS`), devuelve la fuente sin servicio
 * (`disponible: false`) y la vista lo dice sin hacer ni una petición más. Esa
 * primera bandeja no se tira: es la que recibe la vista al pedirla.
 *
 * El laboratorio visual sustituye este archivo por
 * `tests/visual/laboratorio/fuente-mensajes.js` para fotografiar los estados.
 *
 * @module plataforma/salas-partidas/fuente-mensajes
 */

import { fetchWithHttpErrorInterceptor } from '../../comun/interceptors/http-error.interceptor.js';
import { baseDeApi } from '../../comun/base-api.js';
import { ESPERAS_POR_OMISION, canalReconectable } from '../../comun/canal-reconectable.js';
import { usuarioIdDeSesion } from '../../comun/identidad.js';
import { textoDelServidor } from '../../comun/ui/texto-de-fallo.js';
import { urlDelCanal } from './canal-sala.js';
import { conectarChat } from './cliente-chat.js';

/**
 * @typedef {object} JugadorDeMensajes
 * @property {string} id `uid` del jugador
 * @property {string} apodo
 */

/**
 * @typedef {'ACTIVA'|'BLOQUEADA'|'NO_ADMITE'|'CUENTA_SANCIONADA'} EstadoDeConversacion
 *   ACTIVA: se puede escribir. BLOQUEADA: bloqueaste tú al otro jugador.
 *   NO_ADMITE: el otro no recibe mensajes tuyos (no se dice por qué).
 *   CUENTA_SANCIONADA: la cuenta del otro está suspendida o baneada.
 *   El adaptador del servicio produce ACTIVA, BLOQUEADA y NO_ADMITE (D-40).
 */

/**
 * @typedef {object} ResumenDeConversacion
 * @property {string} id el `uid` del otro jugador, en el adaptador del servicio
 * @property {JugadorDeMensajes} con el otro jugador
 * @property {{texto: string, enviadoEn: string, deMi: boolean}|null} ultimo
 * @property {number} noLeidos
 * @property {EstadoDeConversacion} estado
 */

/**
 * @typedef {object} MensajePrivado
 * @property {string} id
 * @property {'mensaje'|'sistema'} tipo
 * @property {JugadorDeMensajes|null} autor null en los del sistema
 * @property {string} texto
 * @property {string} enviadoEn ISO-8601
 * @property {'ENVIADO'|'LEIDO'} [entrega] solo en los tuyos
 */

/**
 * @typedef {object} RestriccionDeMensajes
 * @property {'SILENCIO'} tipo
 * @property {string|null} hasta ISO-8601; null si no vence
 * @property {string|null} motivo
 */

/**
 * @typedef {object} BandejaDeMensajes
 * @property {ResumenDeConversacion[]} conversaciones de la más reciente a la más vieja
 * @property {RestriccionDeMensajes|null} restriccion tu silencio, si lo hay
 */

/**
 * @typedef {{tipo: 'mensaje', conversacionId: string, mensaje: MensajePrivado}
 *   | {tipo: 'leido', conversacionId: string, hasta: string}
 *   | {tipo: 'canal', estado: 'conectado'|'reconectando'|'reconectado'|'sin-conexion',
 *      intento?: number, de?: number}} EventoDeMensajes
 */

/**
 * Lo que rechaza una operación de la fuente. La vista lee `detalle` (lo que
 * se enseña) y `reintentable` (si ofrecer «Reintentar» tiene sentido).
 *
 * Si el rechazo dice que la conversación ya no admite mensajes (un bloqueo),
 * `estadoDeConversacion` trae su estado nuevo.
 *
 * @typedef {Error & {detalle: string, motivo: string|null, estado: number,
 *   reintentable: boolean, reintentarEnSegundos: number|null,
 *   estadoDeConversacion: EstadoDeConversacion|null}} FalloDeLaFuente
 */

/**
 * @typedef {object} FuenteDeMensajes
 * @property {boolean} disponible
 * @property {() => Promise<BandejaDeMensajes>} bandeja
 * @property {(texto: string) => Promise<JugadorDeMensajes[]>} buscarJugadores
 * @property {(jugadorId: string) => Promise<ResumenDeConversacion>} conversacionCon
 *   abre (o crea) la conversación con un jugador
 * @property {(conversacionId: string, pagina?: {antesDe?: string|null, limite?: number}) =>
 *   Promise<{mensajes: MensajePrivado[], hayMasAntiguos?: boolean}>} hilo
 *   sin `pagina`, lo más reciente; con `antesDe`, la página anterior a ese
 *   momento (hacia atrás). Los mensajes van del más antiguo al más reciente.
 * @property {(conversacionId: string, texto: string) => Promise<MensajePrivado>} enviar
 *   rechaza con un `FalloDeLaFuente`
 * @property {(conversacionId: string) => Promise<void>} marcarLeida
 * @property {(jugadorId: string, bloquear: boolean) => Promise<ResumenDeConversacion>} bloquear
 *   bloquea (`true`) o desbloquea (`false`) y devuelve la conversación con su estado
 * @property {(alEvento: (evento: EventoDeMensajes) => void) => () => void} escuchar
 *   devuelve cómo dejar de escuchar
 * @property {() => void} [reintentar] otra ronda de conexión, a mano
 */

/** Lo que responde cada operación mientras no haya servicio. */
export class MensajesSinAbrir extends Error {
  constructor() {
    super('Los mensajes privados no están disponibles ahora.');
    this.name = 'MensajesSinAbrir';
  }
}

const rechazar = async () => {
  throw new MensajesSinAbrir();
};

/**
 * La fuente cuando el servicio no responde. Cada operación rechaza sin tocar
 * la red; la vista no llega a llamarlas porque mira `disponible` primero.
 *
 * @type {FuenteDeMensajes}
 */
export const FUENTE_SIN_SERVICIO = Object.freeze({
  disponible: false,
  bandeja: rechazar,
  buscarJugadores: rechazar,
  conversacionCon: rechazar,
  hilo: rechazar,
  enviar: rechazar,
  marcarLeida: rechazar,
  bloquear: rechazar,
  escuchar: () => () => {},
});

/* ------------------------------------------------------------ el servicio */

/**
 * Rutas de los contratos. Van enteras, con su `/api/v1`, a propósito:
 * `tests/contratos/rutas-contrato-codigo.py` revisa estos literales contra
 * los contratos. La base (`<meta name="nexus-api-base">`) se pone delante.
 */
export const RUTA_DE_CONVERSACIONES = '/api/v1/mensajes-directos/conversaciones';
export const RUTA_DE_PERFILES_PUBLICOS = '/api/v1/perfiles/publicos';

/** Cola de usuario por la que llegan los mensajes y los rechazos. */
export const COLA_DE_MENSAJES = '/usuario/cola/mensajes-directos';

/**
 * Destino STOMP de un envío. Solo dice a quién: el remitente lo pone el
 * servidor a partir del token del CONNECT.
 *
 * @param {string} uid
 * @returns {string}
 */
export function destinoDeEnvio(uid) {
  return `/app/mensajes-directos/${uid}`;
}

/** Largo máximo de un mensaje (`MensajeSaliente.texto.maxLength`). */
export const LARGO_MAXIMO = 500;

/** Mensajes por página del historial (`limite` por omisión del contrato). */
export const TAMANO_DE_PAGINA = 50;

/** Letras mínimas y máximas de una búsqueda por apodo (`apodo` del contrato). */
export const MINIMO_DE_BUSQUEDA = 3;
export const MAXIMO_DE_BUSQUEDA = 50;

/** Lo que se espera a la primera respuesta antes de dar el servicio por caído. */
export const ESPERA_DE_LA_SONDA_MS = 6000;

/** Lo que se espera el eco de un envío por STOMP antes de mandarlo por REST. */
export const ESPERA_DEL_ECO_MS = 8000;

/** Cada cuánto se pregunta por REST mientras no hay canal en tiempo real. */
export const INTERVALO_DE_SONDEO_MS = 10000;

/** La clave con la que el login deja el token (la misma que lee el chat). */
const CLAVE_TOKEN = 'nexus.token';

const FORMA_UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Lo que se dice cuando el servicio no contesta (sin códigos ni nombres internos). */
const SIN_RESPUESTA =
  'El servicio de mensajes privados no responde ahora. Inténtalo de nuevo en un momento.';
const SIN_ENVIO = 'No pudimos enviarlo. Revisa tu conexión e inténtalo de nuevo.';
const SIN_BUSQUEDA = 'La búsqueda de jugadores no responde. Inténtalo de nuevo en un momento.';
const SESION_NO_VALIDA = 'Tu sesión ya no es válida: vuelve a iniciar sesión.';
const CONVERSACION_INEXISTENTE = 'Esa conversación no existe.';

/**
 * Qué se le dice a quien escribió, por motivo del contrato (`EnvioRechazado`
 * del AsyncAPI, y el `type` de los problem details del `POST`). El servidor
 * manda el motivo; las palabras las pone la interfaz. `reintentable: false`
 * cuando repetir el mismo envío daría el mismo rechazo.
 */
export const RECHAZOS = Object.freeze({
  TEXTO_NO_PERMITIDO: {
    detalle:
      'Tiene palabras que no están permitidas. Te lo devolvemos al campo para que lo cambies.',
    reintentable: false,
  },
  SANCIONADO: {
    detalle:
      'Tienes una sanción activa y, mientras dure, no puedes enviar mensajes privados. Puedes consultarla en «Mis sanciones».',
    reintentable: false,
  },
  DESTINATARIO_INEXISTENTE: {
    detalle: 'Ese jugador no puede recibir mensajes: la cuenta no existe o no está activa.',
    reintentable: false,
  },
  DESTINATARIO_PROPIO: {
    detalle: 'No puedes escribirte a ti mismo.',
    reintentable: false,
  },
  TEXTO_INVALIDO: {
    // Desde la política de texto (#814, D-37) no es solo el largo: un dibujo
    // con símbolos o muchas líneas también se rechazan. Por la cola el rechazo
    // no trae `detail`; por REST se usa el del servidor (`falloDeEnvio`).
    detalle: `Un mensaje lleva entre 1 y ${LARGO_MAXIMO} caracteres y se escribe con palabras: sin dibujos hechos con símbolos ni muchas líneas seguidas.`,
    reintentable: false,
  },
  DEMASIADO_RAPIDO: {
    detalle: 'Vas demasiado rápido: espera unos segundos antes de enviar otro.',
    reintentable: true,
  },
  MODERACION_NO_DISPONIBLE: {
    detalle:
      'No pudimos revisar tu mensaje y no sale sin revisar. Inténtalo de nuevo en un momento.',
    reintentable: true,
  },
  CONVERSACION_BLOQUEADA: {
    detalle: 'Bloqueaste a este jugador: desbloquéalo si quieres volver a escribirle.',
    reintentable: false,
  },
  NO_ADMITE: {
    detalle: 'Este jugador no recibe mensajes tuyos.',
    reintentable: false,
  },
});

/**
 * El estado en que queda la conversación cuando un envío se rechaza por un
 * bloqueo (D-40): la vista deja de ofrecer el campo.
 */
const ESTADO_POR_RECHAZO = Object.freeze({
  CONVERSACION_BLOQUEADA: 'BLOQUEADA',
  NO_ADMITE: 'NO_ADMITE',
});

/** Los `estado` que puede mandar el servicio (`EstadoDeConversacionValor`, 1.8.0). */
const ESTADOS_DEL_SERVICIO = new Set(['ACTIVA', 'BLOQUEADA', 'NO_ADMITE']);

/**
 * Un `estado` del servicio, o null si no es uno del contrato.
 *
 * @param {unknown} valor
 * @returns {EstadoDeConversacion|null}
 */
function estadoDe(valor) {
  return ESTADOS_DEL_SERVICIO.has(valor) ? /** @type {EstadoDeConversacion} */ (valor) : null;
}

/**
 * El motivo de cada `type` de la vía REST (salas-partidas.yaml 1.8.0). Se
 * decide por el `type`, nunca por el texto (MAPEO-ERRORES, regla de oro).
 */
const MOTIVO_POR_TIPO = Object.freeze({
  'mensaje-invalido': 'TEXTO_INVALIDO',
  'destinatario-propio': 'DESTINATARIO_PROPIO',
  'jugador-sancionado': 'SANCIONADO',
  'destinatario-inexistente': 'DESTINATARIO_INEXISTENTE',
  'contenido-bloqueado': 'TEXTO_NO_PERMITIDO',
  'demasiados-mensajes': 'DEMASIADO_RAPIDO',
  'moderacion-no-disponible': 'MODERACION_NO_DISPONIBLE',
  'conversacion-bloqueada': 'CONVERSACION_BLOQUEADA',
  'destinatario-no-admite': 'NO_ADMITE',
});

/**
 * Por qué falló una operación, en la forma que lee la vista (`FalloDeLaFuente`).
 */
export class FalloDeMensajes extends Error {
  /**
   * @param {string} detalle lo que se enseña al jugador
   * @param {{motivo?: string|null, estado?: number, reintentable?: boolean,
   *   reintentarEnSegundos?: number|null,
   *   estadoDeConversacion?: EstadoDeConversacion|null}} [datos]
   */
  constructor(
    detalle,
    {
      motivo = null,
      estado = 0,
      reintentable = true,
      reintentarEnSegundos = null,
      estadoDeConversacion = null,
    } = {},
  ) {
    super(detalle);
    this.name = 'FalloDeMensajes';
    this.detalle = detalle;
    this.motivo = motivo;
    this.estado = estado;
    this.reintentable = reintentable;
    this.reintentarEnSegundos = reintentarEnSegundos;
    this.estadoDeConversacion = estadoDeConversacion;
  }
}

/**
 * El fallo de un envío por su motivo del contrato. Un motivo que no se
 * conoce se trata como un fallo cualquiera, que se puede reintentar.
 *
 * @param {string|null|undefined} motivo
 * @param {{estado?: number, reintentarEnSegundos?: number|null}} [datos]
 * @returns {FalloDeMensajes}
 */
export function falloPorMotivo(
  motivo,
  { estado = 0, reintentarEnSegundos = null, detalleDelServidor = null } = {},
) {
  const rechazo = RECHAZOS[motivo];
  if (!rechazo) {
    return new FalloDeMensajes(SIN_ENVIO, { motivo: motivo ?? null, estado });
  }
  let { detalle } = rechazo;
  if (motivo === 'DEMASIADO_RAPIDO' && reintentarEnSegundos) {
    const cuanto = reintentarEnSegundos === 1 ? '1 segundo' : `${reintentarEnSegundos} segundos`;
    detalle = `Vas demasiado rápido: espera ${cuanto} antes de enviar otro.`;
  }
  // Un texto que no es un mensaje se rechaza por varias reglas; solo el
  // servidor sabe cuál falló («parece un dibujo hecho con símbolos…»). Se
  // decide por el motivo y el `detail` es el cuerpo del aviso (MAPEO-ERRORES §2).
  if (motivo === 'TEXTO_INVALIDO' && detalleDelServidor) {
    detalle = textoDelServidor({ detail: detalleDelServidor }, estado, detalle);
  }
  return new FalloDeMensajes(detalle, {
    motivo,
    estado,
    reintentable: rechazo.reintentable,
    reintentarEnSegundos,
    estadoDeConversacion: ESTADO_POR_RECHAZO[motivo] ?? null,
  });
}

/**
 * Un `uid` en su forma comparable (minúsculas), o null si no es un UUID.
 *
 * @param {unknown} valor
 * @returns {string|null}
 */
function uidDe(valor) {
  return typeof valor === 'string' && FORMA_UUID.test(valor) ? valor.toLowerCase() : null;
}

/**
 * La `fecha` de un mensaje como ISO-8601, o null. El contrato la da en
 * texto (`date-time`); si un conversor de mensajes la mandara como número
 * (segundos, o milisegundos, desde 1970), se entiende igual en vez de perder
 * el mensaje.
 *
 * @param {unknown} valor
 * @returns {{fecha: string, momento: number}|null}
 */
function fechaDe(valor) {
  if (typeof valor === 'string') {
    const momento = Date.parse(valor);
    return Number.isFinite(momento) ? { fecha: valor, momento } : null;
  }
  if (typeof valor === 'number' && Number.isFinite(valor)) {
    const momento = Math.round(valor < 1e11 ? valor * 1000 : valor);
    return { fecha: new Date(momento).toISOString(), momento };
  }
  return null;
}

/**
 * Un mensaje del servicio (`MensajeDirecto` por REST, `MensajeEntregado` por
 * la cola) en una forma interna comparable, o null si no trae lo que el
 * contrato exige: un cuerpo raro no rompe la vista, se descarta.
 *
 * @param {unknown} crudo
 */
function leerMensaje(crudo) {
  if (!crudo || typeof crudo !== 'object') {
    return null;
  }
  const remitente = uidDe(crudo.remitente);
  const destinatario = uidDe(crudo.destinatario);
  const cuando = fechaDe(crudo.fecha);
  if (
    typeof crudo.id !== 'string' ||
    !remitente ||
    !destinatario ||
    typeof crudo.texto !== 'string' ||
    !cuando
  ) {
    return null;
  }
  return {
    id: crudo.id,
    remitente,
    destinatario,
    apodoRemitente:
      typeof crudo.apodoRemitente === 'string' && crudo.apodoRemitente.trim()
        ? crudo.apodoRemitente
        : null,
    texto: crudo.texto,
    fecha: cuando.fecha,
    momento: cuando.momento,
    idCliente: typeof crudo.idCliente === 'string' ? crudo.idCliente : null,
  };
}

/** Segundos de un `Retry-After`, o null. */
function segundosDe(valor) {
  const segundos = Number(valor);
  return Number.isFinite(segundos) && segundos > 0 ? Math.ceil(segundos) : null;
}

/** El problem details de una respuesta, o null si no trae uno. */
async function problemaDe(respuesta) {
  try {
    const cuerpo = await respuesta.json();
    return cuerpo && typeof cuerpo === 'object' ? cuerpo : null;
  } catch {
    // Sin cuerpo (un 401 de Spring Security) o una página del borde.
    return null;
  }
}

/** El motivo del contrato a partir del `type` de un problem details. */
function motivoDeProblema(problema) {
  const tipo = String(problema?.type ?? '');
  return MOTIVO_POR_TIPO[tipo.slice(tipo.lastIndexOf('/') + 1)] ?? null;
}

/** Un identificador de envío nuevo (máximo 64 caracteres en el contrato). */
function idClienteNuevo() {
  if (typeof globalThis.crypto?.randomUUID === 'function') {
    return globalThis.crypto.randomUUID();
  }
  return `c-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

/**
 * El adaptador contra el servicio. No pregunta nada al crearse: `disponible`
 * es cierto porque quien lo crea ya sabe que el servicio contesta (ver
 * `fuenteDeMensajes`).
 *
 * @param {object} [opciones] todo inyectable en pruebas
 * @param {Function} [opciones.fetchImpl]
 * @param {string|null} [opciones.miId] `uid` de la sesión
 * @param {string} [opciones.base] base de la API (`<meta name="nexus-api-base">`)
 * @param {() => Promise<object>} [opciones.conectar] abre un cliente STOMP; por
 *   omisión el del chat, con el token que haya en ese momento
 * @param {{setTimeout: Function, clearTimeout: Function, setInterval: Function,
 *   clearInterval: Function}} [opciones.reloj]
 * @param {readonly number[]} [opciones.esperas] esperas entre intentos de conexión
 * @param {() => string} [opciones.generarIdCliente]
 * @param {number} [opciones.esperaDelEco]
 * @param {number} [opciones.intervaloDeSondeo]
 * @param {unknown[]|null} [opciones.primeraBandeja] la de la sonda, para no pedirla dos veces
 * @returns {FuenteDeMensajes}
 */
export function fuenteHttpDeMensajes({
  fetchImpl = fetchWithHttpErrorInterceptor,
  miId = usuarioIdDeSesion(),
  base = baseDeApi(),
  conectar = null,
  reloj = globalThis,
  esperas = ESPERAS_POR_OMISION,
  generarIdCliente = idClienteNuevo,
  esperaDelEco = ESPERA_DEL_ECO_MS,
  intervaloDeSondeo = INTERVALO_DE_SONDEO_MS,
  primeraBandeja = null,
} = {}) {
  const yo = uidDe(miId);
  const abrirCliente =
    conectar ??
    (() =>
      conectarChat({
        url: urlDelCanal({ base }),
        token: globalThis.sessionStorage?.getItem(CLAVE_TOKEN) ?? '',
      }));

  /** Quién escucha los eventos de la vista. */
  const oyentes = new Set();
  /** @type {Map<string, string>} uid → apodo, de la bandeja, los mensajes y las búsquedas */
  const apodos = new Map();
  /** @type {Map<string, ResumenDeConversacion>} la última forma conocida de cada conversación */
  const resumenes = new Map();
  /** @type {Map<string, number>} uid → momento (ms) del último mensaje que ya se conoce */
  const ultimoConocido = new Map();
  /** Mensajes que la vista ya tiene (o ya se le contaron): no se cuentan dos veces. */
  const vistos = new Set();
  /** @type {Map<string, {resolver: Function, rechazar: Function, temporizador: unknown}>} */
  const pendientes = new Map();
  /** Envíos que fallaron sin saber si llegaron: su reintento repite `idCliente`. */
  const porReintentar = new Map();
  /**
   * Lo que cambia con el tiempo. Se toca desde funciones pequeñas y no tras
   * un `await` en la misma función (`require-atomic-updates`).
   */
  const control = {
    primera: Array.isArray(primeraBandeja) ? primeraBandeja : null,
    lineaBase: false,
    canal: null,
    abriendo: false,
    cerrado: true,
    caido: false,
    intentoDeApertura: 0,
    temporizadorDeApertura: null,
    sondeo: null,
    poniendoseAlDia: null,
  };

  /* --- Formas del puerto --------------------------------------------- */

  const otroDe = (m) => (m.remitente === yo ? m.destinatario : m.remitente);

  /** @returns {MensajePrivado} */
  function aMensajePrivado(m) {
    const mio = m.remitente === yo;
    return {
      id: m.id,
      tipo: 'mensaje',
      autor: {
        id: mio ? String(miId) : m.remitente,
        apodo: m.apodoRemitente || apodos.get(m.remitente) || 'Jugador',
      },
      texto: m.texto,
      enviadoEn: m.fecha,
      // «Enviado» y no más: el servidor no publica si el otro lo leyó.
      ...(mio ? { entrega: 'ENVIADO' } : {}),
    };
  }

  /** Lo que ya se sabe de un mensaje: su id, el apodo de quien escribe y su momento. */
  function registrar(m) {
    vistos.add(m.id);
    if (m.apodoRemitente && m.remitente !== yo) {
      apodos.set(m.remitente, m.apodoRemitente);
    }
    const otro = otroDe(m);
    if (m.momento > (ultimoConocido.get(otro) ?? Number.NEGATIVE_INFINITY)) {
      ultimoConocido.set(otro, m.momento);
    }
  }

  /**
   * `ResumenDeConversacion` del servicio a la del puerto, aprendiendo de él
   * (apodo, último mensaje).
   *
   * @returns {ResumenDeConversacion|null}
   */
  function aprenderResumen(crudo) {
    const uid = uidDe(crudo?.uidOtro);
    if (!uid) {
      return null;
    }
    if (typeof crudo.apodoOtro === 'string' && crudo.apodoOtro.trim()) {
      apodos.set(uid, crudo.apodoOtro);
    }
    const ultimo = leerMensaje(crudo.ultimoMensaje);
    if (ultimo) {
      registrar(ultimo);
    }
    const resumen = {
      id: uid,
      con: { id: uid, apodo: apodos.get(uid) ?? 'Jugador' },
      ultimo: ultimo
        ? { texto: ultimo.texto, enviadoEn: ultimo.fecha, deMi: ultimo.remitente === yo }
        : null,
      noLeidos: Math.max(0, Math.floor(Number(crudo.noLeidos) || 0)),
      // Un servicio anterior a 1.8.0 no lo manda: sin bloqueos, ACTIVA.
      estado: estadoDe(crudo.estado) ?? 'ACTIVA',
    };
    resumenes.set(uid, resumen);
    return resumen;
  }

  /** Una conversación sin mensajes todavía: existe en cuanto hay uno. */
  function conversacionVacia(uid) {
    return {
      id: uid,
      con: { id: uid, apodo: apodos.get(uid) ?? 'Jugador' },
      ultimo: null,
      noLeidos: 0,
      estado: 'ACTIVA',
    };
  }

  /** Anota el estado nuevo de una conversación y la devuelve. */
  function cambiarEstado(uid, estado) {
    const resumen = { ...(resumenes.get(uid) ?? conversacionVacia(uid)), estado };
    resumenes.set(uid, resumen);
    return resumen;
  }

  /**
   * El estado de una conversación que todavía no está en la bandeja. Si el
   * servicio no lo dice, ACTIVA: escribir dirá el rechazo si lo hay, y no se
   * deja sin abrir una conversación por una consulta que falló.
   */
  async function preguntarEstado(uid) {
    try {
      const respuesta = await fetchImpl(`${rutaDe(uid)}/bloqueo`, {
        headers: { Accept: 'application/json' },
      });
      if (!respuesta.ok) {
        return 'ACTIVA';
      }
      return estadoDe((await respuesta.json())?.estado) ?? 'ACTIVA';
    } catch {
      return 'ACTIVA';
    }
  }

  /** El `uid` del otro de una conversación del puerto, o un fallo claro. */
  function uidDeConversacion(conversacionId) {
    const uid = uidDe(conversacionId);
    if (!uid) {
      throw new FalloDeMensajes(CONVERSACION_INEXISTENTE, { reintentable: false });
    }
    return uid;
  }

  const rutaDe = (uid) => `${base}${RUTA_DE_CONVERSACIONES}/${encodeURIComponent(uid)}`;

  /* --- Red ------------------------------------------------------------- */

  /** Una petición; sin red, el fallo que se le dice al jugador. */
  async function pedir(url, opciones, sinRespuesta) {
    try {
      return await fetchImpl(url, opciones);
    } catch {
      throw new FalloDeMensajes(sinRespuesta);
    }
  }

  /** El fallo de una lectura, por su estado (la sesión, o que no contesta). */
  function falloDeLectura(respuesta, sinRespuesta) {
    if (respuesta.status === 401 || respuesta.status === 403) {
      return new FalloDeMensajes(SESION_NO_VALIDA, {
        estado: respuesta.status,
        reintentable: false,
      });
    }
    return new FalloDeMensajes(sinRespuesta, { estado: respuesta.status });
  }

  /** Una lista JSON, o el fallo que se enseña. */
  async function leerLista(url, sinRespuesta) {
    const respuesta = await pedir(url, { headers: { Accept: 'application/json' } }, sinRespuesta);
    if (!respuesta.ok) {
      throw falloDeLectura(respuesta, sinRespuesta);
    }
    let cuerpo;
    try {
      cuerpo = await respuesta.json();
    } catch {
      cuerpo = null;
    }
    if (!Array.isArray(cuerpo)) {
      // Un cuerpo que no es el del contrato (una página del borde, por
      // ejemplo) no es una lista vacía: es que el servicio no está.
      throw new FalloDeMensajes(sinRespuesta, { estado: respuesta.status });
    }
    return cuerpo;
  }

  const pedirBandeja = () => leerLista(`${base}${RUTA_DE_CONVERSACIONES}`, SIN_RESPUESTA);

  function pedirHistorial(uid, { antesDe = null, limite = TAMANO_DE_PAGINA } = {}) {
    const consulta = new URLSearchParams({ limite: String(limite) });
    if (antesDe) {
      consulta.set('antesDe', antesDe);
    }
    return leerLista(`${rutaDe(uid)}/mensajes?${consulta}`, SIN_RESPUESTA);
  }

  /** La primera bandeja es la de la sonda; las siguientes, del servidor. */
  function tomarBandeja() {
    const { primera } = control;
    if (primera) {
      control.primera = null;
      return Promise.resolve(primera);
    }
    return pedirBandeja();
  }

  function marcarLineaBase() {
    control.lineaBase = true;
  }

  /* --- Enviar ---------------------------------------------------------- */

  /** Saca un envío de los pendientes (y para su espera), o null si ya no estaba. */
  function cerrarPendiente(idCliente) {
    const pendiente = pendientes.get(idCliente);
    if (!pendiente) {
      return null;
    }
    reloj.clearTimeout(pendiente.temporizador);
    pendientes.delete(idCliente);
    return pendiente;
  }

  async function falloDeEnvio(respuesta) {
    const problema = await problemaDe(respuesta);
    const motivo = motivoDeProblema(problema);
    if (motivo) {
      return falloPorMotivo(motivo, {
        estado: respuesta.status,
        reintentarEnSegundos: segundosDe(respuesta.headers?.get?.('Retry-After')),
        detalleDelServidor: problema?.detail ?? null,
      });
    }
    if (respuesta.status === 401) {
      return new FalloDeMensajes(SESION_NO_VALIDA, { estado: 401, reintentable: false });
    }
    return new FalloDeMensajes(SIN_ENVIO, { estado: respuesta.status });
  }

  /** El `POST` de respaldo: mismas reglas que el canal, y no duplica por `idCliente`. */
  async function enviarPorRest(uid, texto, idCliente) {
    const respuesta = await pedir(
      `${rutaDe(uid)}/mensajes`,
      {
        method: 'POST',
        headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
        body: JSON.stringify({ texto, idCliente }),
      },
      SIN_ENVIO,
    );
    if (!respuesta.ok) {
      throw await falloDeEnvio(respuesta);
    }
    let guardado = null;
    try {
      guardado = leerMensaje(await respuesta.json());
    } catch {
      guardado = null;
    }
    if (!guardado) {
      // Se guardó, pero no se puede leer: reintentarlo con el mismo
      // `idCliente` devuelve el mismo mensaje.
      throw new FalloDeMensajes(SIN_ENVIO, { estado: respuesta.status });
    }
    registrar(guardado);
    return aMensajePrivado(guardado);
  }

  /**
   * Espera el eco de un envío por el canal. Si no llega a tiempo, el mismo
   * envío sale por REST; si el eco llega mientras tanto, gana el eco.
   */
  function esperarEco(uid, texto, idCliente) {
    return new Promise((resolver, rechazarEnvio) => {
      const pendiente = { resolver, rechazar: rechazarEnvio, temporizador: null };
      pendiente.temporizador = reloj.setTimeout(() => {
        enviarPorRest(uid, texto, idCliente).then(
          (mensaje) => cerrarPendiente(idCliente)?.resolver(mensaje),
          (error) => cerrarPendiente(idCliente)?.rechazar(error),
        );
      }, esperaDelEco);
      pendientes.set(idCliente, pendiente);
    });
  }

  function enviarConId(uid, texto, idCliente) {
    const { canal } = control;
    if (canal?.conectado) {
      const eco = esperarEco(uid, texto, idCliente);
      try {
        canal.enviar(destinoDeEnvio(uid), { texto, idCliente });
        return eco;
      } catch {
        // El canal se cayó justo ahora: sigue por REST.
        cerrarPendiente(idCliente);
      }
    }
    return enviarPorRest(uid, texto, idCliente);
  }

  /* --- Recibir --------------------------------------------------------- */

  function emitir(evento) {
    for (const oyente of [...oyentes]) {
      try {
        oyente(evento);
      } catch {
        // Un oyente que falla no deja sin eventos a los demás.
      }
    }
  }

  function contarMensaje(m) {
    registrar(m);
    emitir({ tipo: 'mensaje', conversacionId: otroDe(m), mensaje: aMensajePrivado(m) });
  }

  /** Lo que llega por la cola: `MensajeEntregado` o `EnvioRechazado`. */
  function recibir(carga) {
    if (!carga || typeof carga !== 'object') {
      return;
    }
    if (carga.tipo === 'RECHAZO') {
      let idCliente = typeof carga.idCliente === 'string' ? carga.idCliente : null;
      // Sin `idCliente` (un cuerpo que el servidor no pudo leer) solo se sabe
      // de qué envío es si hay uno solo en el aire.
      if (!idCliente && pendientes.size === 1) {
        [idCliente] = pendientes.keys();
      }
      cerrarPendiente(idCliente)?.rechazar(falloPorMotivo(carga.motivo));
      return;
    }
    if (carga.tipo !== 'MENSAJE') {
      return;
    }
    const m = leerMensaje(carga);
    if (!m || (m.remitente !== yo && m.destinatario !== yo)) {
      return;
    }
    const pendiente = m.idCliente ? cerrarPendiente(m.idCliente) : null;
    if (pendiente) {
      // El eco de un envío de esta pestaña: confirma ESE envío, no es un
      // mensaje nuevo que contar.
      registrar(m);
      pendiente.resolver(aMensajePrivado(m));
      return;
    }
    if (vistos.has(m.id)) {
      // Un eco tardío de algo ya confirmado por REST, o repetido.
      return;
    }
    contarMensaje(m);
  }

  /**
   * Pregunta por lo que haya llegado sin canal y se lo cuenta a la vista, en
   * orden. Sin línea base (la vista todavía no tiene su bandeja) solo aprende
   * dónde está cada conversación: no hay con qué comparar. Nunca lanza.
   */
  async function revisarNovedades() {
    let lista;
    try {
      lista = await pedirBandeja();
    } catch {
      // Se vuelve a intentar en la siguiente ronda; el canal ya dice lo que pasa.
      return;
    }
    const conBase = control.lineaBase;
    for (const crudo of lista) {
      const uid = uidDe(crudo?.uidOtro);
      const ultimo = leerMensaje(crudo?.ultimoMensaje);
      if (!uid || !ultimo || vistos.has(ultimo.id)) {
        continue;
      }
      if (typeof crudo.apodoOtro === 'string' && crudo.apodoOtro.trim()) {
        apodos.set(uid, crudo.apodoOtro);
      }
      if (!conBase) {
        registrar(ultimo);
        continue;
      }
      const desde = ultimoConocido.get(uid) ?? Number.NEGATIVE_INFINITY;
      let pagina;
      try {
        pagina = await pedirHistorial(uid);
      } catch {
        continue;
      }
      for (const m of pagina.map(leerMensaje)) {
        if (!m || vistos.has(m.id)) {
          continue;
        }
        if (m.momento < desde) {
          // De antes de lo último que ya se conocía: no es nuevo.
          vistos.add(m.id);
          continue;
        }
        contarMensaje(m);
      }
    }
    marcarLineaBase();
  }

  /** Una sola revisión a la vez: si ya hay una en marcha, se espera a esa. */
  function ponerseAlDia() {
    if (!control.poniendoseAlDia) {
      control.poniendoseAlDia = revisarNovedades().finally(() => {
        control.poniendoseAlDia = null;
      });
    }
    return control.poniendoseAlDia;
  }

  function empezarSondeo() {
    if (control.sondeo !== null || control.cerrado) {
      return;
    }
    control.sondeo = reloj.setInterval(() => {
      ponerseAlDia();
    }, intervaloDeSondeo);
  }

  function pararSondeo() {
    if (control.sondeo !== null) {
      reloj.clearInterval(control.sondeo);
      control.sondeo = null;
    }
  }

  /* --- El canal -------------------------------------------------------- */

  function contarCanal(estado, intento, de) {
    emitir({
      tipo: 'canal',
      estado,
      ...(Number.isInteger(intento) ? { intento } : {}),
      ...(Number.isInteger(de) ? { de } : {}),
    });
  }

  /**
   * El canal vuelve (o está por primera vez). Si estuvo caído, primero se
   * pone al día y DESPUÉS dice «reconectado»: así «la conversación está al
   * día» es verdad cuando se lee.
   */
  function anunciarVuelta() {
    const estabaCaido = control.caido;
    control.caido = false;
    pararSondeo();
    if (!estabaCaido) {
      contarCanal('conectado');
      ponerseAlDia();
      return;
    }
    ponerseAlDia().finally(() => {
      if (!control.cerrado) {
        contarCanal('reconectado');
      }
    });
  }

  function alEstadoDelCanal({ estado, intento, de }) {
    if (control.cerrado) {
      return;
    }
    if (estado === 'reconectando' || estado === 'sin-conexion') {
      control.caido = true;
      empezarSondeo();
      contarCanal(estado, intento, de);
      return;
    }
    // `conectado` de la primera conexión llega antes de tener el canal: lo
    // anuncia `adoptarCanal`, ya suscrito a la cola.
    if (control.canal) {
      anunciarVuelta();
    }
  }

  function adoptarCanal(nuevo) {
    // Cerrado mientras abría, o ya hay otro: este sobra.
    if (control.cerrado || (control.canal && control.canal !== nuevo)) {
      nuevo.cerrar();
      return;
    }
    // Primero la cola; el canal cuenta como abierto cuando ya escucha.
    nuevo.suscribir(COLA_DE_MENSAJES, recibir);
    control.canal = nuevo;
    control.intentoDeApertura = 0;
    anunciarVuelta();
  }

  /**
   * La primera conexión no salió: se reintenta con las mismas esperas que la
   * reconexión, y al acabarse queda «sin conexión» con su «Reintentar».
   */
  function programarApertura(esperaForzada = null) {
    if (control.cerrado) {
      return;
    }
    if (control.intentoDeApertura >= esperas.length) {
      alEstadoDelCanal({ estado: 'sin-conexion', de: esperas.length });
      return;
    }
    const espera = esperaForzada ?? esperas[control.intentoDeApertura];
    control.intentoDeApertura += 1;
    alEstadoDelCanal({
      estado: 'reconectando',
      intento: control.intentoDeApertura,
      de: esperas.length,
    });
    control.temporizadorDeApertura = reloj.setTimeout(() => {
      abrirCanal();
    }, espera);
  }

  function marcarAbriendo(abriendo) {
    control.abriendo = abriendo;
  }

  async function abrirCanal() {
    // Un solo intento a la vez: dos canales abiertos contarían todo dos veces.
    if (control.cerrado || control.canal || control.abriendo) {
      return;
    }
    marcarAbriendo(true);
    let nuevo = null;
    try {
      nuevo = await canalReconectable({
        conectar: abrirCliente,
        alEstado: alEstadoDelCanal,
        esperas,
        reloj,
      });
      marcarAbriendo(false);
      adoptarCanal(nuevo);
    } catch {
      marcarAbriendo(false);
      if (nuevo && control.canal !== nuevo) {
        nuevo.cerrar();
      }
      programarApertura();
    }
  }

  function cerrarCanal() {
    control.cerrado = true;
    reloj.clearTimeout(control.temporizadorDeApertura);
    pararSondeo();
    control.canal?.cerrar();
    control.canal = null;
    control.caido = false;
    control.intentoDeApertura = 0;
  }

  // La primera bandeja es justo la que va a recibir la vista: desde ella se
  // cuenta lo nuevo (la línea base), también si el canal abre antes de que
  // la vista la pida.
  if (control.primera) {
    control.primera.forEach(aprenderResumen);
    marcarLineaBase();
  }

  /* --- El puerto ------------------------------------------------------- */

  return Object.freeze({
    disponible: true,

    async bandeja() {
      const lista = await tomarBandeja();
      const conversaciones = lista.map(aprenderResumen).filter(Boolean);
      marcarLineaBase();
      return { conversaciones, restriccion: null };
    },

    async buscarJugadores(texto) {
      const apodo = String(texto ?? '').trim();
      if (apodo.length < MINIMO_DE_BUSQUEDA) {
        throw new FalloDeMensajes(`Escribe al menos ${MINIMO_DE_BUSQUEDA} letras del apodo.`, {
          reintentable: false,
        });
      }
      if (apodo.length > MAXIMO_DE_BUSQUEDA) {
        // Ningún apodo es tan largo: nadie empieza así.
        return [];
      }
      const lista = await leerLista(
        `${base}${RUTA_DE_PERFILES_PUBLICOS}?${new URLSearchParams({ apodo })}`,
        SIN_BUSQUEDA,
      );
      const jugadores = [];
      for (const perfil of lista) {
        const uid = uidDe(perfil?.uid);
        if (uid && typeof perfil.apodo === 'string' && perfil.apodo.trim()) {
          apodos.set(uid, perfil.apodo);
          jugadores.push({ id: uid, apodo: perfil.apodo });
        }
      }
      return jugadores;
    },

    async conversacionCon(jugadorId) {
      const uid = uidDe(jugadorId);
      if (!uid) {
        throw new FalloDeMensajes('Ese jugador no existe.', { reintentable: false });
      }
      if (uid === yo) {
        throw falloPorMotivo('DESTINATARIO_PROPIO');
      }
      const conocida = resumenes.get(uid);
      if (conocida) {
        return conocida;
      }
      // El servicio no tiene «crear conversación»: existe en cuanto hay un
      // mensaje. Hasta entonces es esta, vacía, con el estado que diga el
      // bloqueo (D-40).
      const estado = await preguntarEstado(uid);
      return resumenes.get(uid) ?? cambiarEstado(uid, estado);
    },

    async hilo(conversacionId, { antesDe = null, limite = TAMANO_DE_PAGINA } = {}) {
      const uid = uidDeConversacion(conversacionId);
      const cuantos = Math.min(100, Math.max(1, Math.floor(Number(limite) || TAMANO_DE_PAGINA)));
      const lista = await pedirHistorial(uid, { antesDe, limite: cuantos });
      const mensajes = lista.map(leerMensaje).filter(Boolean);
      mensajes.forEach(registrar);
      return {
        mensajes: mensajes.map(aMensajePrivado),
        // Una página llena puede tener más detrás; una a medias, no.
        hayMasAntiguos: lista.length >= cuantos,
      };
    },

    async enviar(conversacionId, texto) {
      const uid = uidDeConversacion(conversacionId);
      const limpio = String(texto ?? '').trim();
      if (!limpio || limpio.length > LARGO_MAXIMO) {
        throw falloPorMotivo('TEXTO_INVALIDO');
      }
      if (uid === yo) {
        throw falloPorMotivo('DESTINATARIO_PROPIO');
      }
      const clave = `${uid}\n${limpio}`;
      const idCliente = porReintentar.get(clave) ?? generarIdCliente();
      porReintentar.delete(clave);
      try {
        return await enviarConId(uid, limpio, idCliente);
      } catch (error) {
        if (error?.reintentable !== false) {
          porReintentar.set(clave, idCliente);
        }
        if (error?.estadoDeConversacion) {
          cambiarEstado(uid, error.estadoDeConversacion);
        }
        throw error;
      }
    },

    async marcarLeida(conversacionId) {
      const uid = uidDeConversacion(conversacionId);
      const respuesta = await pedir(`${rutaDe(uid)}/leido`, { method: 'POST' }, SIN_RESPUESTA);
      if (!respuesta.ok) {
        throw falloDeLectura(respuesta, SIN_RESPUESTA);
      }
      const resumen = resumenes.get(uid);
      if (resumen) {
        resumenes.set(uid, { ...resumen, noLeidos: 0 });
      }
    },

    async bloquear(jugadorId, bloquear) {
      const uid = uidDe(jugadorId);
      if (!uid) {
        throw new FalloDeMensajes('Ese jugador no existe.', { reintentable: false });
      }
      if (uid === yo) {
        throw falloPorMotivo('DESTINATARIO_PROPIO');
      }
      const respuesta = await pedir(
        `${rutaDe(uid)}/bloqueo`,
        { method: bloquear ? 'PUT' : 'DELETE', headers: { Accept: 'application/json' } },
        SIN_RESPUESTA,
      );
      if (!respuesta.ok) {
        throw falloDeLectura(respuesta, SIN_RESPUESTA);
      }
      let estado = null;
      try {
        estado = estadoDe((await respuesta.json())?.estado);
      } catch {
        estado = null;
      }
      if (!estado) {
        // Sin el estado del contrato no se sabe en qué quedó: no se pinta un
        // bloqueo que nadie confirmó.
        throw new FalloDeMensajes(SIN_RESPUESTA, { estado: respuesta.status });
      }
      return cambiarEstado(uid, estado);
    },

    escuchar(alEvento) {
      oyentes.add(alEvento);
      if (control.cerrado) {
        control.cerrado = false;
        abrirCanal();
      }
      return () => {
        oyentes.delete(alEvento);
        if (oyentes.size === 0) {
          cerrarCanal();
        }
      };
    },

    reintentar() {
      if (control.cerrado) {
        return;
      }
      if (control.canal) {
        control.canal.reintentar();
        return;
      }
      reloj.clearTimeout(control.temporizadorDeApertura);
      control.intentoDeApertura = 0;
      programarApertura(0);
    },
  });
}

/**
 * La primera pregunta: la bandeja, o null si el servicio no contesta bien a
 * tiempo. Nunca lanza.
 *
 * @returns {Promise<unknown[]|null>}
 */
async function sondear(fetchImpl, base, esperaMs, reloj) {
  const corte = typeof AbortController === 'function' ? new AbortController() : null;
  let temporizador;
  const plazo = new Promise((resolver) => {
    temporizador = reloj.setTimeout(() => {
      corte?.abort();
      resolver(null);
    }, esperaMs);
  });
  const pregunta = (async () => {
    try {
      const respuesta = await fetchImpl(`${base}${RUTA_DE_CONVERSACIONES}`, {
        headers: { Accept: 'application/json' },
        ...(corte ? { signal: corte.signal } : {}),
      });
      if (!respuesta.ok) {
        return null;
      }
      const cuerpo = await respuesta.json();
      return Array.isArray(cuerpo) ? cuerpo : null;
    } catch {
      // Sin red, cortada por el plazo, o un cuerpo que no es JSON (una página
      // del borde en vez del servicio): para la vista es lo mismo.
      return null;
    }
  })();
  try {
    return await Promise.race([pregunta, plazo]);
  } finally {
    reloj.clearTimeout(temporizador);
  }
}

/**
 * La fuente de mensajes privados de este despliegue: el adaptador si el
 * servicio contesta, o la fuente sin servicio si no. Se pide al abrir la
 * pestaña, no al cargar el chat.
 *
 * @param {Parameters<typeof fuenteHttpDeMensajes>[0] & {esperaMs?: number}} [opciones]
 * @returns {Promise<FuenteDeMensajes>}
 */
export async function fuenteDeMensajes(opciones = {}) {
  const {
    fetchImpl = fetchWithHttpErrorInterceptor,
    esperaMs = ESPERA_DE_LA_SONDA_MS,
    miId = usuarioIdDeSesion(),
    base = baseDeApi(),
    reloj = globalThis,
  } = opciones;
  // Sin `fetch` no hay red a la que preguntar (el envoltorio común lo usa por
  // debajo); sin `uid` no se sabe qué mensajes son tuyos. En los dos casos no
  // se intenta.
  if (fetchImpl === fetchWithHttpErrorInterceptor && typeof globalThis.fetch !== 'function') {
    return FUENTE_SIN_SERVICIO;
  }
  if (!uidDe(miId)) {
    return FUENTE_SIN_SERVICIO;
  }
  const primeraBandeja = await sondear(fetchImpl, base, esperaMs, reloj);
  if (primeraBandeja === null) {
    return FUENTE_SIN_SERVICIO;
  }
  return fuenteHttpDeMensajes({ ...opciones, fetchImpl, miId, base, reloj, primeraBandeja });
}
