/**
 * HU-SAL-001 y HU-SAL-002 — Acceso HTTP a las salas de batalla.
 *
 * Habla con `/api/v1/salas` de `contracts/openapi/salas-partidas.yaml`:
 * crear, listar e ingresar.
 *
 * El anfitrion NO viaja en el cuerpo: lo pone el servidor a partir del token.
 * Si lo mandara el cliente, cualquiera podria crear salas a nombre de otro.
 *
 * Todo error del servicio llega en formato problem details (RFC 7807) y sale de
 * aqui como un `ErrorDeApi`, para que la vista no tenga que leer JSON crudo ni
 * adivinar que paso. La correspondencia con la interfaz esta en
 * `shared/ui-kit/MAPEO-ERRORES.md`.
 */

import { fetchWithHttpErrorInterceptor } from '../../comun/interceptors/http-error.interceptor.js';

/**
 * Base de la API. Vacia por omision, es decir **mismo origen**: asi es como
 * Spring Boot sirve estas vistas en la ejecucion integrada, y por eso no hay
 * ningun `localhost` escrito en el codigo.
 *
 * Para revisar las vistas servidas como HTML estatico contra un backend que
 * corre en otro sitio, la propia pagina lo declara:
 *
 *   <meta name="nexus-api-base" content="http://127.0.0.1:8083" />
 *
 * @returns {string} base sin barra final, o cadena vacia
 */
export function baseDeApi() {
  const meta = globalThis.document?.querySelector?.('meta[name="nexus-api-base"]');
  return String(meta?.content ?? '').replace(/\/+$/, '');
}

/**
 * @param {string} [sufijo]
 * @returns {string} ruta absoluta al recurso de salas
 */
function ruta(sufijo = '') {
  return `${baseDeApi()}/api/v1/salas${sufijo}`;
}

/**
 * Error de negocio devuelto por el servicio, ya interpretado.
 *
 * La vista decide por `tipo` y por `estado`, nunca por el texto: `titulo` y
 * `detalle` son para la persona y pueden cambiar de redaccion.
 */
export class ErrorDeApi extends Error {
  /**
   * @param {{type?: string, title?: string, detail?: string, status?: number,
   *          errores?: Array<{campo: string, mensaje: string}>}} problema
   * @param {number} estado codigo HTTP real de la respuesta
   */
  constructor(problema, estado) {
    super(problema?.detail || problema?.title || 'El servicio no pudo completar la operación.');
    this.name = 'ErrorDeApi';
    this.tipo = problema?.type ?? null;
    this.titulo = problema?.title ?? 'El servicio no pudo completar la operación';
    this.detalle = this.message;
    this.estado = problema?.status ?? estado;
    /** @type {Array<{campo: string, mensaje: string}>} */
    this.errores = Array.isArray(problema?.errores) ? problema.errores : [];
    /**
     * El problem detail entero, tal como llego. Hace falta para las
     * propiedades que no son de la regla 4 base -`seccion`,
     * `reintentarEnSegundos` de HU-DIS-003- y que decide otro componente
     * (`comun/degradacion/aviso-degradacion.js`), no este.
     * @type {object}
     */
    this.problema = problema && typeof problema === 'object' ? problema : {};
  }

  /** True cuando el rechazo se puede corregir campo a campo en el formulario. */
  get esDeFormulario() {
    return this.errores.length > 0;
  }
}

/**
 * Crea una sala de batalla.
 *
 * @param {{maximoParticipantes: number, modalidad: string,
 *          recompensaCreditos: number, incluirHeroeIA: boolean,
 *          privada: boolean, tamanoEquipo?: number|null}} parametros
 * @param {{fetchImpl?: Function}} [opciones] inyeccion para las pruebas
 * @returns {Promise<object>} la sala creada, segun el esquema Sala del contrato
 * @throws {ErrorDeApi} si el servicio rechaza la peticion
 */
export async function crearSala(parametros, { fetchImpl = fetchWithHttpErrorInterceptor } = {}) {
  const respuesta = await fetchImpl(ruta(), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(parametros),
  });

  if (respuesta.ok) {
    return respuesta.json();
  }

  throw new ErrorDeApi(await cuerpoDelProblema(respuesta, 'operacion'), respuesta.status);
}

/**
 * Lista las salas de batalla — HU-SAL-002, RF-JUE-002.
 *
 * Los filtros vacios no se mandan: el caso de uso del servidor decide los
 * valores por defecto, y mandarle `null` desde aqui seria decidirlos dos veces.
 *
 * @param {{pagina?: number, tamano?: number, modalidad?: string, estado?: string}} [filtros]
 * @param {{fetchImpl?: Function}} [opciones] inyeccion para las pruebas
 * @returns {Promise<object>} pagina de salas, segun el esquema PaginaDeSalas
 * @throws {ErrorDeApi} si el servicio rechaza la peticion
 */
export async function listarSalas(
  filtros = {},
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  const parametros = new URLSearchParams();
  for (const [clave, valor] of Object.entries(filtros)) {
    if (valor !== undefined && valor !== null && valor !== '') {
      parametros.set(clave, String(valor));
    }
  }

  const consulta = parametros.toString();
  const respuesta = await fetchImpl(consulta ? `${ruta()}?${consulta}` : ruta());

  if (respuesta.ok) {
    return respuesta.json();
  }

  throw new ErrorDeApi(await cuerpoDelProblema(respuesta, 'listado'), respuesta.status);
}

/**
 * Ingresa a una sala existente — HU-SAL-002, RF-JUE-002.
 *
 * El jugador no viaja en el cuerpo: lo pone el servidor desde el token, igual
 * que el anfitrion al crear.
 *
 * @param {string} idSala
 * @param {{fetchImpl?: Function}} [opciones] inyeccion para las pruebas
 * @returns {Promise<object>} la sala con el jugador dentro
 * @throws {ErrorDeApi} 404 no existe · 403 privada · 409 llena o ya empezo
 */
export async function ingresarASala(idSala, { fetchImpl = fetchWithHttpErrorInterceptor } = {}) {
  const respuesta = await fetchImpl(ruta(`/${encodeURIComponent(idSala)}/participantes`), {
    method: 'POST',
  });

  if (respuesta.ok) {
    return respuesta.json();
  }

  throw new ErrorDeApi(await cuerpoDelProblema(respuesta, 'sala'), respuesta.status);
}

/**
 * Trae una sala por su identificador — `GET /salas/{idSala}`.
 *
 * La vista de espera lo necesita para saber quien es el anfitrion (y por
 * tanto si ofrece «Cancelar sala» o «Salir de la sala», HU-SAL-006) y cuanta
 * gente hay dentro. Al anfitrion le llega ademas el codigo de invitacion.
 *
 * @param {string} idSala
 * @param {{fetchImpl?: Function}} [opciones] inyeccion para las pruebas
 * @returns {Promise<object>} segun el esquema Sala del contrato
 * @throws {ErrorDeApi} 404 si no existe
 */
export async function obtenerSala(idSala, { fetchImpl = fetchWithHttpErrorInterceptor } = {}) {
  const respuesta = await fetchImpl(ruta(`/${encodeURIComponent(idSala)}`));

  if (respuesta.ok) {
    return respuesta.json();
  }

  throw new ErrorDeApi(await cuerpoDelProblema(respuesta, 'sala'), respuesta.status);
}

/**
 * Sale de una sala antes de que empiece — HU-SAL-006, operacion
 * `abandonarSala` del contrato (`DELETE /salas/{idSala}/participantes`).
 *
 * Quien sale es quien firma el token: no viaja ningun identificador. Si la
 * sala tenia recompensa, el servidor devuelve la reserva (HU-JUE-014).
 *
 * @param {string} idSala
 * @param {{fetchImpl?: Function}} [opciones] inyeccion para las pruebas
 * @returns {Promise<void>}
 * @throws {ErrorDeApi} 404 no existe · 409 no estas dentro, eres el anfitrion o ya empezo
 */
export async function abandonarSala(idSala, { fetchImpl = fetchWithHttpErrorInterceptor } = {}) {
  const respuesta = await fetchImpl(ruta(`/${encodeURIComponent(idSala)}/participantes`), {
    method: 'DELETE',
  });

  if (respuesta.ok) {
    return;
  }

  throw new ErrorDeApi(await cuerpoDelProblema(respuesta, 'sala'), respuesta.status);
}

/**
 * Cancela una sala — HU-SAL-006, operacion `cancelarSala` del contrato
 * (`DELETE /salas/{idSala}`). Solo el anfitrion, y solo antes de empezar; el
 * servidor lo comprueba con el token.
 *
 * @param {string} idSala
 * @param {{fetchImpl?: Function}} [opciones] inyeccion para las pruebas
 * @returns {Promise<void>}
 * @throws {ErrorDeApi} 403 no eres el anfitrion · 404 no existe · 409 ya empezo o ya no esta activa
 */
export async function cancelarSala(idSala, { fetchImpl = fetchWithHttpErrorInterceptor } = {}) {
  const respuesta = await fetchImpl(ruta(`/${encodeURIComponent(idSala)}`), {
    method: 'DELETE',
  });

  if (respuesta.ok) {
    return;
  }

  throw new ErrorDeApi(await cuerpoDelProblema(respuesta, 'sala'), respuesta.status);
}

/**
 * Verifica el heroe antes de intentar entrar — HU-SAL-003, RF-JUE-003.
 *
 * Habla con `GET /salas/{idSala}/verificacion-heroe`, que ya esta publicado en
 * `contracts/openapi/salas-partidas.yaml`. La ruta existe en el contrato pero
 * **todavia no en el servicio**: depende de que el modulo de contenido publique
 * cual es el heroe activo del jugador. Por eso este cliente se escribe contra
 * el contrato y se inyecta en la vista, que se prueba con datos de ejemplo.
 *
 * No comprueba ni decide nada: solo trae el veredicto. Quien decide es el
 * servidor, y la vista solo lo pinta.
 *
 * @param {string} idSala
 * @param {{fetchImpl?: Function}} [opciones] inyeccion para las pruebas
 * @returns {Promise<object>} segun el esquema VerificacionHeroe del contrato
 * @throws {ErrorDeApi} si el servicio rechaza la peticion
 */
export async function verificarHeroe(idSala, { fetchImpl = fetchWithHttpErrorInterceptor } = {}) {
  const respuesta = await fetchImpl(ruta(`/${encodeURIComponent(idSala)}/verificacion-heroe`));

  if (respuesta.ok) {
    return respuesta.json();
  }

  throw new ErrorDeApi(await cuerpoDelProblema(respuesta, 'sala'), respuesta.status);
}

/**
 * Arranca el combate de una sala — HU-SAL-004, RF-JUE-017.
 *
 * Solo el anfitrion puede, y el servidor lo comprueba con el token: por eso
 * aqui no viaja ningun identificador de jugador, igual que al crear y al
 * entrar.
 *
 * Pulsar dos veces no es un error ni crea dos partidas: la segunda llamada
 * devuelve la misma. La vista puede reintentar sin comprobar nada antes.
 *
 * @param {string} idSala
 * @param {{fetchImpl?: Function}} [opciones] inyeccion para las pruebas
 * @returns {Promise<object>} la partida iniciada, segun el esquema Partida
 * @throws {ErrorDeApi} 403 no eres el anfitrion · 404 no existe · 409 la sala no puede empezar
 */
export async function iniciarPartida(idSala, { fetchImpl = fetchWithHttpErrorInterceptor } = {}) {
  const respuesta = await fetchImpl(ruta(`/${encodeURIComponent(idSala)}/partida`), {
    method: 'POST',
  });

  if (respuesta.ok) {
    return respuesta.json();
  }

  throw new ErrorDeApi(await cuerpoDelProblema(respuesta, 'sala'), respuesta.status);
}

/**
 * Trae el estado de una partida — RF-JUE-017.
 *
 * Es para pintar la vista de combate la primera vez y para ponerse al dia tras
 * una caida del canal. El avance turno a turno llega por WebSocket: si la vista
 * llamara a esto en bucle, el canal sobraria.
 *
 * Cuelga de `/api/v1/partidas`, no de `/api/v1/salas`, asi que no usa `ruta()`.
 *
 * @param {string} idPartida
 * @param {{fetchImpl?: Function}} [opciones] inyeccion para las pruebas
 * @returns {Promise<object>} segun el esquema Partida del contrato
 * @throws {ErrorDeApi} 404 si la partida no existe
 */
export async function obtenerPartida(
  idPartida,
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  const respuesta = await fetchImpl(
    `${baseDeApi()}/api/v1/partidas/${encodeURIComponent(idPartida)}`,
  );

  if (respuesta.ok) {
    return respuesta.json();
  }

  throw new ErrorDeApi(await cuerpoDelProblema(respuesta, 'sala'), respuesta.status);
}

/**
 * True cuando detras de la ruta no hay ninguna API, sino un servidor de
 * ficheros. Un servidor estatico responde 405 a un POST sobre una ruta que
 * para el es un fichero (`http-server` lo hace con `text/plain`), y devuelve
 * HTML cuando la ruta no existe. Distinguirlo importa: es la diferencia entre
 * «el servicio fallo» y «no has levantado el servicio».
 *
 * Solo se consulta cuando el cuerpo NO era JSON. Un servicio de la Empresa A
 * responde siempre con problem details (regla 4 de plataforma), tambien en
 * un 405 real, asi que ese 405 nunca llega aqui: lo atrapa `cuerpoDelProblema`
 * antes. Por eso no se reduce la comprobacion a `text/html`: dejaria de
 * reconocerse el caso real del servidor estatico.
 *
 * @param {Response} respuesta
 * @returns {boolean}
 */
function sinApiDetras(respuesta) {
  const tipo = String(respuesta.headers?.get?.('content-type') ?? '');
  return respuesta.status === 405 || tipo.includes('text/html');
}

/**
 * Que decirle a quien mira, segun por que fallo. Nunca el codigo.
 *
 * ## Por que hace falta saber QUE se pidio (UX-R3.4)
 *
 * El 404 devolvia siempre «Esa sala ya no existe. Vuelve al listado para ver
 * las que siguen abiertas», y eso vale cuando se pidio UNA sala. Cuando lo
 * que fallaba era el LISTADO —que es lo que pasa sin backend detras— el
 * jugador leia, en la pantalla del listado, que vuelva al listado para ver
 * una sala que nadie habia abierto. Un mensaje que se contradice con la
 * pantalla en la que esta enseña a no leer los mensajes.
 *
 * @param {number} estado
 * @param {'listado'|'sala'|'operacion'} recurso que se estaba pidiendo
 * @returns {string}
 */
function detalleDelFallo(estado, recurso = 'operacion') {
  if (estado === 401) {
    return 'Vuelve a iniciar sesión para continuar.';
  }
  if (estado === 403) {
    return 'Tu cuenta no tiene permiso para ver esto.';
  }
  if (estado === 404) {
    if (recurso === 'listado') {
      return 'El servicio de batallas no está disponible en este momento.';
    }
    return 'Esa sala ya no existe. Vuelve al listado para ver las que siguen abiertas.';
  }
  if (estado === 409) {
    return 'Alguien se te adelantó: el estado de la sala cambió mientras mirabas.';
  }
  if (estado >= 500 || estado === 0) {
    return 'El servicio de batallas no responde ahora mismo. Vuelve a intentarlo en un momento.';
  }
  return 'No pudimos completar la operación. Vuelve a intentarlo.';
}

/**
 * Lee el problem details de una respuesta fallida.
 *
 * Un 401 de Spring Security llega sin cuerpo, y un servidor estatico devuelve
 * HTML. En esos casos se construye un problema minimo en vez de reventar: la
 * persona necesita ver un mensaje, no una excepcion de JSON.
 *
 * @param {Response} respuesta
 * @returns {Promise<object>}
 */
async function cuerpoDelProblema(respuesta, recurso = 'operacion') {
  try {
    const problema = await respuesta.json();
    if (problema && typeof problema === 'object') {
      return problema;
    }
  } catch {
    // Cuerpo vacio o no JSON: se cae a los mensajes de abajo.
  }

  if (sinApiDetras(respuesta)) {
    // UX-R3.4 — esto se pintaba EN LA PANTALLA: «Estas viendo la vista servida
    // como HTML estatico: nadie atiende /api/v1/salas. Levanta el servicio de
    // salas, o declara en la pagina <meta name="nexus-api-base">…».
    //
    // Es el mensaje correcto y va a la persona equivocada. A quien programa le
    // dice exactamente que hacer; a un jugador le enseña una etiqueta HTML y
    // le pide que levante un servicio. El diagnostico se queda donde lo mira
    // quien puede actuar sobre el, y la pantalla dice lo que dice siempre que
    // un servicio no esta.
    const direccion = respuesta.url || ruta();
    console.warn(
      `[salas] No hay API detras de ${direccion}: la vista se esta sirviendo como HTML ` +
        'estatico. Levanta el servicio de salas, o declara <meta name="nexus-api-base"> ' +
        'apuntando a donde este corriendo.',
    );
    return {
      status: respuesta.status,
      title: 'Las batallas no están disponibles',
      detail: 'El servicio de batallas no responde ahora mismo. Vuelve a intentarlo en un momento.',
    };
  }

  // UX-R2.4 — el detalle era `El servicio respondio ${status}.`, o sea el
  // codigo HTTP como el mensaje que lee el jugador. La norma del producto es
  // que el codigo puede ir en la traza, nunca en la pantalla: «404» no le dice
  // a nadie si esperar, reintentar o irse. El estado sigue en `status` para
  // quien programa.
  return {
    status: respuesta.status,
    title: respuesta.status === 401 ? 'Tu sesión terminó' : 'Las batallas no están disponibles',
    detail: detalleDelFallo(respuesta.status, recurso),
  };
}
