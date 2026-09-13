/**
 * Cliente de la API de subastas (HU-SUB-004 y HU-SUB-011).
 *
 * Implementa el lado consumidor de contracts/openapi/ms-subastas-pujas.yaml y
 * de ms-subastas-listado.yaml. Vive aparte de subastas.js a proposito: ese
 * modulo es la vista y sus reglas de presentacion, y se puede seguir probando
 * sin red. Aqui esta todo lo que toca el servidor.
 *
 * @module subastas-api
 */

const CLAVE_TOKEN = 'nexus.token';
const CLAVE_APODO = 'nexus.apodoActual';

/** Puerto 8092 + context-path /api/v1, como declara el contrato. */
export const URL_BASE_POR_DEFECTO = 'http://localhost:8092/api/v1';

/**
 * Fallo de una operacion de subastas.
 *
 * @property {number} estado  codigo HTTP.
 * @property {string|null} motivo  codigo estable del problem+json. Es lo que se
 *   debe mirar para decidir el mensaje: el texto libre del servidor puede
 *   cambiar, el motivo no.
 */
export class ErrorDeSubastas extends Error {
  constructor(mensaje, { estado = 0, motivo = null, detalle = null } = {}) {
    super(mensaje);
    this.name = 'ErrorDeSubastas';
    this.estado = estado;
    this.motivo = motivo;
    this.detalle = detalle;
  }
}

/**
 * Mensajes por motivo. Estan aqui y no en el servidor porque el servidor
 * responde a cualquier cliente: el detalle tecnico que devuelve ("La puja de
 * 105 no supera la oferta vigente mas el incremento minimo (110)") sirve para
 * depurar, no para ensenarselo a un jugador en mitad de una puja.
 */
const MENSAJES = {
  SUBASTA_NO_ACTIVA: 'Esta subasta ya se cerro. Actualiza para ver el resultado.',
  PUJA_PROPIA: 'No puedes pujar en tu propia subasta.',
  OFERTA_INSUFICIENTE: 'Alguien se te adelanto: la oferta ya subio. Revisa el nuevo minimo.',
  INTERVALO_MINIMO_NO_CUMPLIDO: 'Espera unos segundos antes de volver a pujar en esta subasta.',
  LIMITE_SUBASTAS_ACTIVAS: 'Ya participas en el maximo de subastas a la vez.',
  LIMITE_PUJAS_ACTIVAS: 'Tienes demasiadas pujas activas al mismo tiempo.',
  LIMITE_AUTOMATICO_INALCANZABLE: 'Ese limite no alcanza ni para la siguiente oferta valida.',
  SALDO_INSUFICIENTE_PARA_LIMITE: 'Tu saldo disponible no cubre el limite que quieres fijar.',
  SALDO_INSUFICIENTE: 'No tienes creditos suficientes para esta operacion.',
  SIN_COMPRA_INMEDIATA: 'Esta subasta no admite compra inmediata.',
  CONFIRMACION_REQUERIDA: 'Hay que confirmar la compra de forma explicita.'
};

/**
 * @param {string|null} motivo
 * @param {string} porDefecto
 * @returns {string} mensaje para el jugador
 */
export function mensajePara(motivo, porDefecto = 'No se pudo completar la operacion.') {
  return MENSAJES[motivo] || porDefecto;
}

/**
 * Clave de idempotencia. La cabecera es obligatoria por contrato y la genera el
 * cliente: si la derivara el servidor del reloj, un reintento tras un timeout
 * traeria otra clave y ms-finanzas reservaria los creditos dos veces.
 *
 * @returns {string}
 */
function claveDeIdempotencia() {
  if (globalThis.crypto && typeof globalThis.crypto.randomUUID === 'function') {
    return globalThis.crypto.randomUUID();
  }
  return `idem-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;
}

/**
 * Convierte una SubastaResumen del listado en lo que espera la vista.
 *
 * Los campos que el backend todavia no expone quedan en valores neutros a
 * proposito, no inventados: el historial de pujas y el rival no estan en el
 * contrato del listado, y rellenarlos con datos falsos seria mentirle al
 * jugador sobre quien esta pujando.
 *
 * @param {object} resumen  SubastaResumen de ms-subastas-listado.yaml
 * @param {string|null} apodoPropio
 */
export function aVistaDeSubasta(resumen, apodoPropio = null) {
  const finMs = resumen.fechaFin ? new Date(resumen.fechaFin).getTime() : 0;
  const restantes = finMs ? Math.max(0, Math.round((finMs - Date.now()) / 1000)) : 0;

  return {
    id: resumen.id,
    nombre: resumen.nombreProducto || 'Objeto sin nombre',
    tipo: resumen.tipoProducto || '',
    descripcion: resumen.descripcionCorta || '',
    rareza: (resumen.rareza || 'comun').toLowerCase(),
    nivel: 0,
    vendedor: resumen.vendedorId || '',
    oferta: Number(resumen.ofertaVigente || 0),
    // ?? y no ||: un precio de 0 es un dato, aunque sea raro, y || lo
    // confundiria con "no hay precio de compra inmediata".
    compraInmediata: Number(resumen.precioCompraInmediata ?? 0),
    mediaMercado: 0,
    segundosRestantes: restantes,
    ganando: false,
    superado: false,
    autoLimite: 0,
    esperaSegundos: 0,
    retenido: 0,
    rival: null,
    rivales: Number(resumen.cantidadPujas || 0),
    aporte: { poder: 0, vida: 0, defensa: 0 },
    historial: [],
    esMaestroDeJuego: Boolean(resumen.esMaestroDeJuego),
    apodoPropio
  };
}

/**
 * @param {Response} respuesta
 * @returns {Promise<never>} siempre lanza
 */
async function lanzarDesde(respuesta) {
  let motivo = null;
  let detalle = null;
  try {
    const problema = await respuesta.json();
    motivo = problema.motivo || null;
    detalle = problema.detail || null;
  } catch {
    // Una respuesta sin cuerpo JSON (un 502 de un proxy, por ejemplo) no es
    // motivo para romper: se reporta con el codigo y ya.
  }

  if (respuesta.status === 401) {
    throw new ErrorDeSubastas('Tu sesion no es valida. Vuelve a iniciar sesion.',
      { estado: 401, motivo, detalle });
  }
  if (respuesta.status === 404) {
    throw new ErrorDeSubastas('Esa subasta ya no existe.', { estado: 404, motivo, detalle });
  }
  throw new ErrorDeSubastas(mensajePara(motivo), { estado: respuesta.status, motivo, detalle });
}

/**
 * @param {object} [opciones]
 * @param {string} [opciones.urlBase]
 * @param {function} [opciones.fetch]  inyectable para probar sin red
 * @param {function} [opciones.leerToken]
 */
export function crearApiSubastas({
  urlBase = URL_BASE_POR_DEFECTO,
  fetch: hacerPeticion = globalThis.fetch?.bind(globalThis),
  leerToken = () => globalThis.sessionStorage?.getItem(CLAVE_TOKEN) || null,
  leerApodo = () => globalThis.sessionStorage?.getItem(CLAVE_APODO) || null
} = {}) {

  async function pedir(ruta, { metodo = 'GET', cuerpo = null, conIdempotencia = false } = {}) {
    const token = leerToken();
    if (!token) {
      throw new ErrorDeSubastas('Inicia sesion para participar en las subastas.', { estado: 401 });
    }

    const cabeceras = {
      Accept: 'application/json',
      Authorization: `Bearer ${token}`
    };
    if (cuerpo !== null) {cabeceras['Content-Type'] = 'application/json';}
    if (conIdempotencia) {cabeceras['Idempotency-Key'] = claveDeIdempotencia();}

    let respuesta;
    try {
      respuesta = await hacerPeticion(`${urlBase}${ruta}`, {
        method: metodo,
        headers: cabeceras,
        body: cuerpo === null ? undefined : JSON.stringify(cuerpo)
      });
    } catch (fallo) {
      throw new ErrorDeSubastas('No se pudo contactar al servidor de subastas.',
        { estado: 0, detalle: fallo?.message || null });
    }

    if (!respuesta.ok) {await lanzarDesde(respuesta);}
    if (respuesta.status === 204) {return null;}
    return respuesta.json();
  }

  return {
    /**
     * El listado es publico y no necesita token, pero se pide igual con el que
     * haya: asi el servidor puede, mas adelante, marcar en que subastas
     * participa quien mira.
     */
    async listar({ page = 0, size = 16 } = {}) {
      const token = leerToken();
      const cabeceras = { Accept: 'application/json' };
      if (token) {cabeceras.Authorization = `Bearer ${token}`;}

      let respuesta;
      try {
        respuesta = await hacerPeticion(`${urlBase}/subastas?page=${page}&size=${size}`,
          { method: 'GET', headers: cabeceras });
      } catch (fallo) {
        throw new ErrorDeSubastas('No se pudo cargar el listado de subastas.',
          { estado: 0, detalle: fallo?.message || null });
      }
      if (!respuesta.ok) {await lanzarDesde(respuesta);}

      const pagina = await respuesta.json();
      const apodo = leerApodo();
      // 'contenido' es el nombre exacto del contrato de listado: ese envoltorio
      // se escribio a mano justamente para no serializar el Page de Spring
      // Data, cuyo JSON usa 'content' y no coincidiria.
      return (pagina.contenido || []).map((resumen) => aVistaDeSubasta(resumen, apodo));
    },

    /** POST /subastas/{id}/pujas */
    pujar(subastaId, monto) {
      return pedir(`/subastas/${subastaId}/pujas`, {
        metodo: 'POST',
        cuerpo: { monto: String(monto) },
        conIdempotencia: true
      });
    },

    /** POST /subastas/{id}/compra-inmediata */
    comprarAhora(subastaId) {
      return pedir(`/subastas/${subastaId}/compra-inmediata`, {
        metodo: 'POST',
        cuerpo: { confirmado: true },
        conIdempotencia: true
      });
    },

    /** PUT /subastas/{id}/puja-automatica */
    configurarAutomatica(subastaId, limite) {
      return pedir(`/subastas/${subastaId}/puja-automatica`, {
        metodo: 'PUT',
        cuerpo: { limite: String(limite) }
      });
    },

    /** DELETE /subastas/{id}/puja-automatica */
    desactivarAutomatica(subastaId) {
      return pedir(`/subastas/${subastaId}/puja-automatica`, { metodo: 'DELETE' });
    }
  };
}
