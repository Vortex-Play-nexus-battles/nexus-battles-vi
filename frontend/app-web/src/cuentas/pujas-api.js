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

import { leerSesion } from '../comun/sesion.js';

const CLAVE_TOKEN = 'nexus.token';
const CLAVE_APODO = 'nexus.apodoActual';

/**
 * Ruta relativa: el borde del entorno enruta /api/v1/subastas y
 * /api/v1/mis-pujas a ms-subastas dentro del mismo origen
 * (infrastructure/red-balanceo/borde-dev.conf).
 *
 * Antes era http://localhost:8092/api/v1, que desde cualquier navegador que
 * no fuera el de la maquina de desarrollo apuntaba al equipo de quien miraba
 * la pagina, no al servidor.
 */
export const URL_BASE_POR_DEFECTO = '/api/v1';

/**
 * URL del canal en vivo (`/ws-subastas`, registrado por WebSocketConfig bajo el
 * mismo context-path). Se deriva de la base HTTP en vez de declararla aparte:
 * dos direcciones al mismo servicio acaban divergiendo, y con https hay que
 * cambiar a wss sin que nadie se acuerde.
 *
 * @param {string} urlBase
 * @returns {string}
 */
export function urlDelCanal(urlBase = URL_BASE_POR_DEFECTO) {
  return `${urlBase.replace(/^http/, 'ws')}/ws-subastas`;
}

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
  SUBASTA_NO_ACTIVA: 'Esta subasta ya se cerró. Actualiza para ver el resultado.',
  PUJA_PROPIA: 'No puedes pujar en tu propia subasta.',
  OFERTA_INSUFICIENTE: 'Alguien se te adelantó: la oferta ya subió. Revisa el nuevo mínimo.',
  INTERVALO_MINIMO_NO_CUMPLIDO: 'Espera unos segundos antes de volver a pujar en esta subasta.',
  LIMITE_SUBASTAS_ACTIVAS: 'Ya participas en el máximo de subastas a la vez.',
  LIMITE_PUJAS_ACTIVAS: 'Tienes demasiadas pujas activas al mismo tiempo.',
  LIMITE_AUTOMATICO_INALCANZABLE: 'Ese límite no alcanza ni para la siguiente oferta válida.',
  SALDO_INSUFICIENTE_PARA_LIMITE: 'Tu saldo disponible no cubre el límite que quieres fijar.',
  SALDO_INSUFICIENTE: 'No tienes créditos suficientes para esta operación.',
  SIN_COMPRA_INMEDIATA: 'Esta subasta no admite compra inmediata.',
  CONFIRMACION_REQUERIDA: 'Hay que confirmar la compra de forma explícita.',
  // B8 (ms-subastas-pujas.yaml 0.4.0): una puja ya alcanzo el precio de compra
  // inmediata; comprar por debajo le quitaria el objeto al mejor postor.
  COMPRA_INMEDIATA_SUPERADA:
    'La compra inmediata ya no está disponible: una puja alcanzó su precio. Puedes seguir pujando.',
  // B8 (ms-subastas-panel.yaml 1.0.0): cancelar, seguir y recoger.
  NO_ES_EL_VENDEDOR: 'Solo quien publicó la subasta puede cancelarla.',
  CANCELACION_CON_PUJAS: 'Ya hay pujas registradas: la subasta no se puede cancelar.',
  CANCELACION_FUERA_DE_PLAZO: 'No se puede cancelar en las últimas 6 horas de la subasta.',
  PENDIENTE_NO_ENCONTRADO: 'No tienes ningún producto pendiente de recoger de esa subasta.',
  PENDIENTE_YA_RESUELTO: 'El plazo de 7 días para recogerlo ya venció.',
  // No deberia verlo un jugador: significa que el cliente reutilizo una clave
  // de idempotencia. Se traduce igual, porque un mensaje en blanco seria peor
  // que uno generico si alguna vez pasa.
  CLAVE_REUTILIZADA: 'Hubo un problema al enviar la operación. Vuelve a intentarlo.',
};

/**
 * @param {string|null} motivo
 * @param {string} porDefecto
 * @returns {string} mensaje para el jugador
 */
export function mensajePara(motivo, porDefecto = 'No se pudo completar la operación.') {
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
 * @param {string|null} [uidPropio] el `uid` de quien mira (ADR-002), para
 *   reconocer sus propias subastas por `vendedorId`
 */
export function aVistaDeSubasta(resumen, apodoPropio = null, uidPropio = null) {
  // G5 (ms-subastas-listado 1.2.0): el servidor dice `esPropia` con el token
  // de quien pide el listado; el `uid` del vendedor ya no viaja (llega null).
  // Sin `esPropia` (un servicio anterior), la comparación de siempre.
  const esPropia =
    typeof resumen.esPropia === 'boolean'
      ? resumen.esPropia
      : Boolean(
          uidPropio && resumen.vendedorId && String(resumen.vendedorId) === String(uidPropio),
        );
  const finMs = resumen.fechaFin ? new Date(resumen.fechaFin).getTime() : 0;
  const restantes = finMs ? Math.max(0, Math.round((finMs - Date.now()) / 1000)) : 0;

  return {
    id: resumen.id,
    nombre: resumen.nombreProducto || 'Objeto sin nombre',
    tipo: resumen.tipoProducto || '',
    descripcion: resumen.descripcionCorta || '',
    // FI-R1 — `rareza` esta en `SubastaResumen` pero NO es obligatoria y no
    // trae enumeracion. Aqui se ponia 'comun' cuando faltaba, y «comun» es un
    // escalon real del juego: eso es inventarse la rareza de un objeto, no
    // decir que no se sabe. Null significa que no vino.
    rareza: typeof resumen.rareza === 'string' ? resumen.rareza.toLowerCase() : null,
    // FI-R1 — `nivel` NO existe en el contrato de subastas. Era un cero fijo
    // que alimentaba el veredicto «Nivel insuficiente / Compatible» citando
    // RN-INV-004: una regla de negocio real aplicada a un dato inventado.
    // Null es «no se sabe»; el cero decia «no pide nivel», que es distinto.
    nivel: null,
    // El contrato no trae el apodo del vendedor: sin nombre no se escribe.
    vendedor: null,
    // Se guarda para reconocer la subasta propia (cancelar, pujas
    // bloqueadas) aunque el uid de quien mira llegue después; no se pinta.
    vendedorId: resumen.vendedorId || null,
    esPropia,
    oferta: Number(resumen.ofertaVigente || 0),
    // UXC-8 — `precioCompraInmediata` es nullable en el contrato: null es
    // «esta subasta no admite compra inmediata». Convertirlo en 0 pintaba
    // «Comprar ya: 0 cr» y un botón de comprar que el servidor rechazaba
    // (SIN_COMPRA_INMEDIATA). Un 0 que sí venga se respeta: es un dato.
    compraInmediata:
      resumen.precioCompraInmediata === null || resumen.precioCompraInmediata === undefined
        ? null
        : Number(resumen.precioCompraInmediata),
    // `miniaturaUrl` si esta en el contrato y hasta ahora no se traia; la
    // vitrina del listado ya la pinta (`subastas-vitrina.js`).
    miniaturaUrl: resumen.miniaturaUrl || null,
    // FI-R1 — tampoco esta en el contrato. Un cero aqui se leia como «este
    // objeto vale cero de media», que es una afirmacion sobre el mercado.
    mediaMercado: null,
    segundosRestantes: restantes,
    ganando: false,
    superado: false,
    autoLimite: 0,
    esperaSegundos: 0,
    retenido: 0,
    rival: null,
    rivales: Number(resumen.cantidadPujas || 0),
    // B8 — `estado` llega en SubastaResumen desde ms-subastas-listado.yaml
    // 1.1.0. Lo que el listado NO trae (la puja minima exacta, el incremento
    // de ESTA subasta, si la compra inmediata sigue disponible, si la sigues)
    // queda en null hasta que el detalle lo pida a GET /subastas/{id}: null es
    // «todavia no se sabe», no un valor.
    estado: resumen.estado || 'ACTIVA',
    // G5 (7.7.9): «fecha y hora de finalización», además de la cuenta atrás.
    fechaFin: resumen.fechaFin || null,
    precioInicial:
      resumen.precioInicial === null || resumen.precioInicial === undefined
        ? null
        : Number(resumen.precioInicial),
    pujaMinimaSiguiente: null,
    incrementoMinimo: null,
    compraInmediataDisponible: null,
    siguiendo: false,
    // FI-R1 — el contrato no dice que aporta un objeto a un heroe. Los tres
    // ceros llenaban la columna «Con <objeto>» y toda la de «Diferencia».
    aporte: null,
    historial: [],
    esMaestroDeJuego: Boolean(resumen.esMaestroDeJuego),
    apodoPropio,
  };
}

/**
 * Un 404 no significa lo mismo en un listado que en una subasta concreta.
 *
 * UX-R3.11 — antes CUALQUIER 404 se traducia por «Esa subasta ya no existe.»,
 * un texto escrito para la ficha. Cuando lo que fallaba era el listado
 * (`/subastas?page=…`, `/mis-pujas/resumen`), la pantalla quedaba diciendo a
 * la vez «El mercado no responde» y «Esa subasta ya no existe»: dos cosas que
 * se contradicen, y ninguna de las dos cierta.
 *
 * @param {string} ruta la que se pidio
 * @returns {string}
 */
function textoDe404(ruta) {
  const esFichaDeSubasta = /\/subastas\/[^/?]+/.test(ruta);
  return esFichaDeSubasta
    ? 'Esa subasta ya no existe.'
    : 'El mercado no respondió. Vuelve a intentarlo en un momento.';
}

/**
 * @param {Response} respuesta
 * @param {string} [ruta] la ruta pedida, para que el 404 hable del recurso
 * @returns {Promise<never>} siempre lanza
 */
async function lanzarDesde(respuesta, ruta = '') {
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
    throw new ErrorDeSubastas('Tu sesión no es válida. Vuelve a iniciar sesión.', {
      estado: 401,
      motivo,
      detalle,
    });
  }
  if (respuesta.status === 404) {
    throw new ErrorDeSubastas(textoDe404(ruta || respuesta.url || ''), {
      estado: 404,
      motivo,
      detalle,
    });
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
  leerApodo = () => globalThis.sessionStorage?.getItem(CLAVE_APODO) || null,
  // UXC-8 — el uid de la sesion (claim `uid` del token; si no, el guardado
  // por el login), para reconocer las subastas propias.
  leerUid = () => {
    const sesion = leerSesion();
    return sesion.autenticado ? sesion.uid : null;
  },
} = {}) {
  async function pedir(
    ruta,
    {
      metodo = 'GET',
      cuerpo = null,
      conIdempotencia = false,
      exigeSesion = true,
      // UXC-8 — el historial exportado llega en `text/csv`, no en JSON.
      comoTexto = false,
    } = {},
  ) {
    const token = leerToken();
    if (!token && exigeSesion) {
      throw new ErrorDeSubastas('Inicia sesión para participar en las subastas.', { estado: 401 });
    }

    const cabeceras = {
      Accept: comoTexto ? 'text/csv, application/problem+json' : 'application/json',
    };
    // Sin sesion se manda igual la peticion cuando el endpoint es publico: el
    // token solo enriquece la respuesta (marcar las pujas propias).
    if (token) {
      cabeceras.Authorization = `Bearer ${token}`;
    }
    if (cuerpo !== null) {
      cabeceras['Content-Type'] = 'application/json';
    }
    // La clave se genera UNA vez, fuera del bucle de reintento: reintentar con
    // una clave nueva seria pujar otra vez, que es justo lo contrario.
    if (conIdempotencia) {
      cabeceras['Idempotency-Key'] = claveDeIdempotencia();
    }

    const opciones = {
      method: metodo,
      headers: cabeceras,
      body: cuerpo === null ? undefined : JSON.stringify(cuerpo),
    };

    let respuesta;
    try {
      respuesta = await hacerPeticion(`${urlBase}${ruta}`, opciones);
    } catch (fallo) {
      // Se reintenta SOLO cuando la peticion no llego a tener respuesta y solo
      // si lleva clave de idempotencia. Sin ella no se sabe si el servidor
      // llego a procesarla, y repetir una puja a ciegas seria pujar dos veces;
      // con ella, el servidor devuelve la puja original si la primera si entro.
      // Un error HTTP no se reintenta: el servidor ya respondio.
      if (!conIdempotencia) {
        throw new ErrorDeSubastas('No se pudo contactar al servidor de subastas.', {
          estado: 0,
          detalle: fallo?.message || null,
        });
      }
      try {
        respuesta = await hacerPeticion(`${urlBase}${ruta}`, opciones);
      } catch (segundoFallo) {
        throw new ErrorDeSubastas('No se pudo contactar al servidor de subastas.', {
          estado: 0,
          detalle: segundoFallo?.message || null,
        });
      }
    }

    if (!respuesta.ok) {
      await lanzarDesde(respuesta, ruta);
    }
    if (respuesta.status === 204) {
      return null;
    }
    return comoTexto ? respuesta.text() : respuesta.json();
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
      if (token) {
        cabeceras.Authorization = `Bearer ${token}`;
      }

      let respuesta;
      try {
        respuesta = await hacerPeticion(`${urlBase}/subastas?page=${page}&size=${size}`, {
          method: 'GET',
          headers: cabeceras,
        });
      } catch (fallo) {
        throw new ErrorDeSubastas('No se pudo cargar el listado de subastas.', {
          estado: 0,
          detalle: fallo?.message || null,
        });
      }
      if (!respuesta.ok) {
        await lanzarDesde(respuesta, `/subastas?page=${page}&size=${size}`);
      }

      const pagina = await respuesta.json();
      const apodo = leerApodo();
      const uid = leerUid();
      // 'contenido' es el nombre exacto del contrato de listado: ese envoltorio
      // se escribio a mano justamente para no serializar el Page de Spring
      // Data, cuyo JSON usa 'content' y no coincidiria.
      return (pagina.contenido || []).map((resumen) => aVistaDeSubasta(resumen, apodo, uid));
    },

    /**
     * GET /mis-pujas/resumen — lo que tienes en juego sumando todas las
     * subastas. No trae saldo total ni disponible: eso lo sabe ms-finanzas, que
     * todavia no existe, y este servicio no se lo inventa.
     */
    async miResumen() {
      return pedir('/mis-pujas/resumen');
    },

    /**
     * GET /subastas/{id}/pujas — historial, de la mas reciente a la mas
     * antigua. Publico, pero se manda el token si lo hay para que el servidor
     * marque cuales son propias.
     */
    async historial(subastaId) {
      return pedir(`/subastas/${subastaId}/pujas`, { exigeSesion: false });
    },

    /**
     * GET /subastas/{id}/mi-participacion — si vas ganando, tu oferta vigente,
     * tus creditos retenidos, tu limite automatico y cuanto falta para poder
     * volver a pujar. Sin esto la pantalla tenia que suponerlo.
     */
    async miParticipacion(subastaId) {
      return pedir(`/subastas/${subastaId}/mi-participacion`);
    },

    /** POST /subastas/{id}/pujas */
    pujar(subastaId, monto) {
      return pedir(`/subastas/${subastaId}/pujas`, {
        metodo: 'POST',
        cuerpo: { monto: String(monto) },
        conIdempotencia: true,
      });
    },

    /** POST /subastas/{id}/compra-inmediata */
    comprarAhora(subastaId) {
      return pedir(`/subastas/${subastaId}/compra-inmediata`, {
        metodo: 'POST',
        cuerpo: { confirmado: true },
        conIdempotencia: true,
      });
    },

    /** PUT /subastas/{id}/puja-automatica */
    configurarAutomatica(subastaId, limite) {
      return pedir(`/subastas/${subastaId}/puja-automatica`, {
        metodo: 'PUT',
        cuerpo: { limite: String(limite) },
      });
    },

    /** DELETE /subastas/{id}/puja-automatica */
    desactivarAutomatica(subastaId) {
      return pedir(`/subastas/${subastaId}/puja-automatica`, { metodo: 'DELETE' });
    },

    // ------------------------------------------------------------------ B8

    /**
     * GET /subastas/reglas — las reglas de 7.7 tal como estan (duraciones y
     * comisiones, incremento minimo de admin-parametros, limites). Publica.
     * Sustituye a las cifras escritas a mano en CONFIG_REGLAS.
     */
    reglas() {
      return pedir('/subastas/reglas', { exigeSesion: false });
    },

    /**
     * GET /subastas/{id} — la ficha: puja minima exacta, incremento de esta
     * subasta, si la compra inmediata sigue disponible y la reputacion del
     * vendedor. Publica; con sesion cuenta la visita.
     */
    ficha(subastaId) {
      return pedir(`/subastas/${subastaId}`, { exigeSesion: false });
    },

    /** POST /subastas/{id}/cancelacion — 7.7.10: sin pujas, fuera de las ultimas 6 h, 50 % de la comision. */
    cancelar(subastaId) {
      return pedir(`/subastas/${subastaId}/cancelacion`, { metodo: 'POST' });
    },

    /** PUT /subastas/{id}/seguimiento — lista de seguimiento (7.7.9). Idempotente. */
    seguir(subastaId) {
      return pedir(`/subastas/${subastaId}/seguimiento`, { metodo: 'PUT' });
    },

    /** DELETE /subastas/{id}/seguimiento */
    dejarDeSeguir(subastaId) {
      return pedir(`/subastas/${subastaId}/seguimiento`, { metodo: 'DELETE' });
    },

    /** GET /mis-subastas/pendientes — lo ganado al vencer, pendiente de recoger 7 dias (7.7.9). */
    pendientes() {
      return pedir('/mis-subastas/pendientes');
    },

    /** POST /mis-subastas/pendientes/{id}/recogida — recoger uno. Idempotente. */
    recoger(subastaId) {
      return pedir(`/mis-subastas/pendientes/${subastaId}/recogida`, { metodo: 'POST' });
    },

    /** POST /mis-subastas/pendientes/recogida — «Recoger todo». */
    recogerTodo() {
      return pedir('/mis-subastas/pendientes/recogida', { metodo: 'POST' });
    },

    // ----------------------------------------------- UXC-8 · panel personal

    /**
     * GET /mis-pujas — «Mis pujas» (7.7.9, ms-subastas-pujas.yaml 0.4.0):
     * cada subasta en la que pujaste o dejaste una automática, también las
     * cerradas, con el estado de tu participación.
     */
    misPujas() {
      return pedir('/mis-pujas');
    },

    /**
     * GET /mis-subastas/publicadas — lo que publicaste, en cualquier estado:
     * pujas recibidas, visitas, lo cobrado y si todavía se puede cancelar
     * (con lo que costaría). De la más reciente a la más antigua (7.7.9).
     */
    misPublicaciones() {
      return pedir('/mis-subastas/publicadas');
    },

    /** GET /mis-subastas/seguimiento — tu lista de seguimiento, las activas primero. */
    misSeguidas() {
      return pedir('/mis-subastas/seguimiento');
    },

    /**
     * GET /mis-subastas/historial — compras, ventas, comisiones y
     * penalizaciones, con lo ganado, lo gastado y el balance.
     */
    miHistorial() {
      return pedir('/mis-subastas/historial');
    },

    /** GET /mis-subastas/historial?formato=csv — el mismo historial, para guardarlo. */
    exportarHistorial() {
      return pedir('/mis-subastas/historial?formato=csv', { comoTexto: true });
    },
  };
}
