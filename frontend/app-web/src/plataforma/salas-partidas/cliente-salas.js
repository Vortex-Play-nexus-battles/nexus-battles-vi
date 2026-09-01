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

const RUTA = '/api/v1/salas';

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
    this.titulo = problema?.title ?? 'No se pudo crear la sala';
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
  const respuesta = await fetchImpl(RUTA, {
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
  const respuesta = await fetchImpl(consulta ? `${RUTA}?${consulta}` : RUTA);

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
  const respuesta = await fetchImpl(`${RUTA}/${encodeURIComponent(idSala)}/participantes`, {
    method: 'POST',
  });

  if (respuesta.ok) {
    return respuesta.json();
  }

  throw new ErrorDeApi(await cuerpoDelProblema(respuesta), respuesta.status);
}

/**
 * Lee el problem details de una respuesta fallida.
 *
 * Un 401 de Spring Security llega sin cuerpo, y un fallo de red puede devolver
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
    // Cuerpo vacio o no JSON: se cae al mensaje por defecto de abajo.
  }
  return {
    status: respuesta.status,
    title: respuesta.status === 401 ? 'Tu sesion no es valida' : 'No se pudo crear la sala',
    detail:
      respuesta.status === 401
        ? 'Vuelve a iniciar sesion para crear una sala.'
        : `El servicio respondio ${respuesta.status}.`,
  };
}
