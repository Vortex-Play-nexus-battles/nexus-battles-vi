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
    super(problema?.detail || problema?.title || 'El servicio no pudo completar la operacion.');
    this.name = 'ErrorDeApi';
    this.tipo = problema?.type ?? null;
    this.titulo = problema?.title ?? 'El servicio no pudo completar la operacion';
    this.detalle = this.message;
    this.estado = problema?.status ?? estado;
    /** @type {Array<{campo: string, mensaje: string}>} */
    this.errores = Array.isArray(problema?.errores) ? problema.errores : [];
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

  throw new ErrorDeApi(await cuerpoDelProblema(respuesta), respuesta.status);
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

  throw new ErrorDeApi(await cuerpoDelProblema(respuesta), respuesta.status);
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

  throw new ErrorDeApi(await cuerpoDelProblema(respuesta), respuesta.status);
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

  throw new ErrorDeApi(await cuerpoDelProblema(respuesta), respuesta.status);
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
 * Lee el problem details de una respuesta fallida.
 *
 * Un 401 de Spring Security llega sin cuerpo, y un servidor estatico devuelve
 * HTML. En esos casos se construye un problema minimo en vez de reventar: la
 * persona necesita ver un mensaje, no una excepcion de JSON.
 *
 * @param {Response} respuesta
 * @returns {Promise<object>}
 */
async function cuerpoDelProblema(respuesta) {
  try {
    const problema = await respuesta.json();
    if (problema && typeof problema === 'object') {
      return problema;
    }
  } catch {
    // Cuerpo vacio o no JSON: se cae a los mensajes de abajo.
  }

  if (sinApiDetras(respuesta)) {
    // La URL real de la peticion cuando `fetch` la trae; la base como respaldo.
    const direccion = respuesta.url || ruta();
    return {
      status: respuesta.status,
      title: 'No hay ninguna API detras de esta ruta',
      detail:
        `Estas viendo la vista servida como HTML estatico: nadie atiende ${direccion}. ` +
        'Levanta el servicio de salas, o declara en la pagina ' +
        '<meta name="nexus-api-base"> apuntando a donde este corriendo.',
    };
  }

  return {
    status: respuesta.status,
    title: respuesta.status === 401 ? 'Tu sesion no es valida' : 'El servicio no respondio bien',
    detail:
      respuesta.status === 401
        ? 'Vuelve a iniciar sesion para continuar.'
        : `El servicio respondio ${respuesta.status}.`,
  };
}
