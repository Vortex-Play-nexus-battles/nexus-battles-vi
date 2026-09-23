import { rutaDeApi } from '../comun/base-api.js';
import { fetchWithHttpErrorInterceptor } from '../comun/interceptors/http-error.interceptor.js';

const GENERICO = 'No se pudo publicar la subasta. Inténtalo de nuevo más tarde.';
const INCIERTO =
  'No se pudo confirmar el resultado. Reintenta esta misma publicación; no inicies otra mientras se resuelve.';

// ManejadorDeErroresPublicacion usa about:blank. No expone códigos funcionales:
// solo traducimos detalles conocidos, nunca mostramos texto arbitrario del servidor.
const MENSAJES = new Map([
  [
    '422|Creditos insuficientes para publicar la subasta',
    'No tienes créditos suficientes para pagar la comisión.',
  ],
  [
    '403|El usuario tiene una sanción activa',
    'Tienes una sanción activa que impide publicar subastas.',
  ],
  ['403|El elemento no pertenece al usuario', 'El producto no pertenece a tu inventario.'],
  [
    '422|El producto está en uso',
    'El producto está en uso. Debes dejar de usarlo antes de publicarlo.',
  ],
  [
    '422|El producto no es subastable',
    'Este producto no se puede subastar (por ejemplo, si es premium o está suspendido).',
  ],
  [
    '422|El producto no coincide con el elemento',
    'El producto ya no coincide con el inventario. Vuelve a seleccionarlo.',
  ],
  [
    '422|El precio de compra inmediata debe ser mayor o igual al precio inicial',
    'La compra inmediata debe ser mayor o igual al precio inicial.',
  ],
  ['404|Elemento de inventario inexistente', 'El elemento ya no existe en el inventario.'],
  ['404|Producto inexistente', 'El producto ya no está disponible.'],
  [
    '409|El elemento ya tiene una subasta activa',
    'Este producto ya tiene una subasta activa y no está disponible.',
  ],
  [
    '409|La clave de idempotencia fue usada con otra solicitud',
    'Existe un conflicto con este intento. Revisa tus subastas antes de volver a publicar.',
  ],
  [
    '409|Publicacion con esta clave en curso; reintente tras su finalizacion',
    'Tu publicación sigue en curso. Espera y reintenta esta misma operación.',
  ],
  [
    '503|Resultado transaccional desconocido; requiere conciliacion',
    'El resultado de la publicación requiere conciliación. Conserva este intento y consulta tus subastas; no publiques de nuevo con otra clave.',
  ],
]);

export class ErrorPublicacion extends Error {
  constructor(message, { status = 0, incierto = false } = {}) {
    super(message);
    this.name = 'ErrorPublicacion';
    this.status = status;
    this.incierto = incierto;
  }
}

export function interpretarProblema(status, problema = {}) {
  const conocido = !problema?.type || problema.type === 'about:blank';
  const detalle = conocido ? problema?.detail : '';
  let message = MENSAJES.get(`${status}|${detalle}`) ?? GENERICO;
  if (status === 401) {
    message = 'Tu sesión no es válida. Inicia sesión para continuar.';
  } else if (status === 503 && !MENSAJES.has(`${status}|${detalle}`)) {
    message = `Un servicio necesario no está disponible temporalmente. ${INCIERTO}`;
  } else if (status >= 500) {
    message = MENSAJES.get(`${status}|${detalle}`) ?? INCIERTO;
  }
  return new ErrorPublicacion(message, {
    status,
    incierto:
      status >= 500 || (status === 409 && detalle !== 'El elemento ya tiene una subasta activa'),
  });
}

/** El llamador conserva esta clave (1..100 caracteres) durante todo el intento. */
export function crearClavePublicacion() {
  const bytes = globalThis.crypto.getRandomValues(new Uint8Array(16));
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
}

export async function publicarSubasta(
  solicitud,
  clave,
  { fetchImpl = fetchWithHttpErrorInterceptor, timeoutMs = 20000 } = {},
) {
  if (typeof clave !== 'string' || !clave.trim() || clave.length > 100) {
    throw new ErrorPublicacion('No se pudo preparar la publicación.');
  }
  const controller = new AbortController();
  const temporizador = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const respuesta = await fetchImpl(rutaDeApi('/subastas'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': clave },
      body: JSON.stringify(solicitud),
      signal: controller.signal,
    });
    let cuerpo;
    try {
      cuerpo = await respuesta.json();
    } catch {
      cuerpo = null;
    }
    if (!respuesta.ok) {
      throw interpretarProblema(respuesta.status, cuerpo);
    }
    if (
      respuesta.status !== 201 ||
      !cuerpo?.id ||
      cuerpo.estado !== 'ACTIVA' ||
      !Number.isFinite(cuerpo.comisionCobrado)
    ) {
      throw new ErrorPublicacion(INCIERTO, { incierto: true });
    }
    return cuerpo;
  } catch (error) {
    if (error instanceof ErrorPublicacion) {
      throw error;
    }
    throw new ErrorPublicacion(INCIERTO, { incierto: true });
  } finally {
    clearTimeout(temporizador);
  }
}
