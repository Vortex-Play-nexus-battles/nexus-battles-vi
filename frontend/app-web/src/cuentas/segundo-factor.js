/**
 * Verificación en dos pasos — HU-AUT-007 (ms-identidad-auth 2.2.0).
 *
 * Lo que comparten las dos vistas que la usan: el login (segundo paso y
 * enrolamiento obligatorio, `login-segundo-paso.js`) y «Mi cuenta > Seguridad»
 * (`segundo-factor-cuenta.js`). Igual que `portada-tienda.js` o
 * `tienda-producto.css`, vive con las vistas de cuentas que lo usan.
 *
 * El mecanismo es TOTP (RFC 6238): la aplicación de autenticación del teléfono
 * (Google Authenticator, Microsoft Authenticator, Aegis…) guarda una clave y
 * muestra cada 30 segundos un código de 6 dígitos. Quien decide si un código
 * vale es SIEMPRE el servidor; aquí solo se comprueba la forma para no enviar
 * algo que se sabe que va a rechazar, y se explica cada rechazo por su `type`.
 *
 * Nada de esto se guarda en el navegador: el desafío del login vive en una
 * variable de la página y la clave y los códigos solo están en pantalla.
 *
 * @module cuentas/segundo-factor
 */

import { baseDeApi } from '../comun/base-api.js';
import { detalleDelProblema, tipoDelProblema } from '../comun/codigo-de-correo.js';
import { cuerpoDe } from '../comun/entrada.js';
import { fetchWithHttpErrorInterceptor } from '../comun/interceptors/http-error.interceptor.js';
import { tonoPorEstado } from '../comun/ui/aviso.js';
import { h } from '../comun/ui/dom.js';

/** Rutas del contrato. Literales enteros: el guardián de rutas los lee. */
export const RUTAS_SEGUNDO_FACTOR = Object.freeze({
  estado: '/api/v1/auth/segundo-factor',
  enrolamiento: '/api/v1/auth/segundo-factor/enrolamiento',
  activacion: '/api/v1/auth/segundo-factor/activacion',
  desactivacion: '/api/v1/auth/segundo-factor/desactivacion',
  segundoPaso: '/api/v1/auth/login/segundo-factor',
  enrolamientoObligatorio: '/api/v1/auth/login/segundo-factor/enrolamiento',
  activacionObligatoria: '/api/v1/auth/login/segundo-factor/activacion',
});

/** Los `type` de rechazo que usa el contrato 2.2.0 para el segundo factor. */
export const PROBLEMAS_DEL_SEGUNDO_FACTOR = Object.freeze({
  REQUERIDO: 'segundo-factor-requerido',
  ENROLAMIENTO_REQUERIDO: 'segundo-factor-enrolamiento-requerido',
  NO_DISPONIBLE: 'segundo-factor-no-disponible',
  YA_ACTIVO: 'segundo-factor-ya-activo',
  NO_ACTIVO: 'segundo-factor-no-activo',
  SIN_ENROLAMIENTO: 'sin-enrolamiento-pendiente',
  CODIGO_INVALIDO: 'codigo-segundo-factor-invalido',
  DESAFIO_INVALIDO: 'desafio-invalido',
  CONTRASENA_INCORRECTA: 'contrasena-actual-incorrecta',
  CUENTA_BLOQUEADA: 'cuenta-bloqueada',
  DATOS_INVALIDOS: 'datos-invalidos',
});

/** Los 403 del login que dicen que la cuenta no puede entrar (una sanción entre los dos pasos). */
const CUENTA_QUE_NO_PUEDE_ENTRAR = new Set([
  'cuenta-baneada',
  'cuenta-suspendida',
  'cuenta-inactiva',
  'cuenta-no-verificada',
]);

/** Dígitos del código de la aplicación y caracteres de uno de recuperación (contrato 2.2.0). */
export const DIGITOS = 6;
export const LONGITUD_RECUPERACION = 10;
/** Mismo alfabeto que los códigos del servidor: sin 0/O ni 1/I/L. */
const ALFABETO_RECUPERACION = /^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]+$/;

/** Un rechazo del servidor, ya leído. La vista decide por `tipo` y `estado`. */
export class FalloDelSegundoFactor extends Error {
  /**
   * @param {{estado: number, tipo?: string|null, detalle?: string|null}} datos
   */
  constructor({ estado, tipo = null, detalle = null }) {
    super(detalle ?? `HTTP ${estado}`);
    this.name = 'FalloDelSegundoFactor';
    this.estado = estado;
    this.tipo = tipo;
    this.detalle = detalle;
  }
}

/**
 * @param {Response} respuesta
 * @returns {Promise<any>} el cuerpo, o `null` si no lo hay (204)
 */
async function leer(respuesta) {
  const cuerpo = await cuerpoDe(respuesta);
  if (!respuesta.ok) {
    throw new FalloDelSegundoFactor({
      estado: respuesta.status,
      tipo: tipoDelProblema(cuerpo),
      detalle: detalleDelProblema(cuerpo),
    });
  }
  return cuerpo;
}

const ACEPTA = 'application/json, application/problem+json';

/** POST con cuerpo JSON. */
function enviar(fetchImpl, ruta, cuerpo) {
  return fetchImpl(`${baseDeApi()}${ruta}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: ACEPTA },
    ...(cuerpo === undefined ? {} : { body: JSON.stringify(cuerpo) }),
  });
}

// ------------------------------------------------------- con sesión (Mi cuenta)

/**
 * Si la cuenta de esta sesión tiene segundo factor, si su rol lo exige y si el
 * servicio puede activarlo.
 *
 * @param {{fetchImpl?: typeof fetch}} [opciones]
 * @returns {Promise<{activo: boolean, obligatorio: boolean, disponible: boolean,
 *   enrolamientoPendiente: boolean, activadoEn?: string|null, codigosRecuperacionRestantes?: number|null}>}
 */
export async function consultarSegundoFactor({ fetchImpl = fetchWithHttpErrorInterceptor } = {}) {
  return leer(
    await fetchImpl(`${baseDeApi()}${RUTAS_SEGUNDO_FACTOR.estado}`, {
      headers: { Accept: ACEPTA },
      cache: 'no-store',
    }),
  );
}

/**
 * Pide la clave nueva (todavía no protege nada: falta confirmarla).
 *
 * @param {{fetchImpl?: typeof fetch}} [opciones]
 */
export async function iniciarEnrolamiento({ fetchImpl = fetchWithHttpErrorInterceptor } = {}) {
  return leer(await enviar(fetchImpl, RUTAS_SEGUNDO_FACTOR.enrolamiento));
}

/**
 * Confirma la clave con un código de la aplicación: la activa y devuelve los
 * códigos de recuperación (una sola vez).
 *
 * @param {string} codigo
 * @param {{fetchImpl?: typeof fetch}} [opciones]
 */
export async function activarSegundoFactor(
  codigo,
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  return leer(
    await enviar(fetchImpl, RUTAS_SEGUNDO_FACTOR.activacion, { codigo: limpiarCodigo(codigo) }),
  );
}

/**
 * Quita el segundo factor. En `codigo` vale el de la aplicación (seis cifras)
 * o uno de recuperación: se envía en el campo que toca según su forma.
 *
 * @param {{passwordActual: string, codigo: string}} datos
 * @param {{fetchImpl?: typeof fetch}} [opciones]
 * @returns {Promise<null>}
 */
export async function desactivarSegundoFactor(
  { passwordActual, codigo },
  { fetchImpl = fetchWithHttpErrorInterceptor } = {},
) {
  const cuerpo = esCodigoDeAplicacion(codigo)
    ? { passwordActual, codigo: limpiarCodigo(codigo) }
    : { passwordActual, codigoRecuperacion: codigoDeRecuperacionLegible(codigo) };
  return leer(await enviar(fetchImpl, RUTAS_SEGUNDO_FACTOR.desactivacion, cuerpo));
}

// --------------------------------------------- sin sesión (segundo paso del login)
//
// Con `fetch` directo y no con el interceptor, igual que `pedirLogin`: aquí
// todavía no hay sesión que adjuntar, y el aviso flotante del interceptor
// repetiría encima el mismo rechazo que ya dice el formulario.

/**
 * Canjea el desafío del login y el código (o uno de recuperación) por la sesión.
 *
 * @param {{desafio: string, codigo?: string, codigoRecuperacion?: string}} datos
 * @param {{fetchImpl?: typeof fetch}} [opciones]
 */
export async function canjearDesafio(
  { desafio, codigo, codigoRecuperacion },
  { fetchImpl = globalThis.fetch } = {},
) {
  const cuerpo = codigoRecuperacion
    ? { desafio, codigoRecuperacion: codigoDeRecuperacionLegible(codigoRecuperacion) }
    : { desafio, codigo: limpiarCodigo(codigo) };
  return leer(await enviar(fetchImpl, RUTAS_SEGUNDO_FACTOR.segundoPaso, cuerpo));
}

/**
 * Enrolamiento obligatorio: la clave, con el desafío del login.
 *
 * @param {string} desafio
 * @param {{fetchImpl?: typeof fetch}} [opciones]
 */
export async function enrolarConDesafio(desafio, { fetchImpl = globalThis.fetch } = {}) {
  return leer(await enviar(fetchImpl, RUTAS_SEGUNDO_FACTOR.enrolamientoObligatorio, { desafio }));
}

/**
 * Enrolamiento obligatorio: confirma la clave y abre la sesión.
 *
 * @param {{desafio: string, codigo: string}} datos
 * @param {{fetchImpl?: typeof fetch}} [opciones]
 */
export async function activarConDesafio(
  { desafio, codigo },
  { fetchImpl = globalThis.fetch } = {},
) {
  return leer(
    await enviar(fetchImpl, RUTAS_SEGUNDO_FACTOR.activacionObligatoria, {
      desafio,
      codigo: limpiarCodigo(codigo),
    }),
  );
}

// ------------------------------------------------------------------ códigos

/** Lo que se pega junto con un código y no forma parte de él. */
const SEPARADORES = /[\s\-‐-―_.·]/gu;

/**
 * El código como lo espera el servidor: sin espacios ni guiones.
 *
 * @param {unknown} texto
 * @returns {string}
 */
export function limpiarCodigo(texto) {
  return String(texto ?? '').replace(SEPARADORES, '');
}

/**
 * ¿Tiene la forma del código de la aplicación (seis cifras)?
 *
 * @param {unknown} texto
 */
export function esCodigoDeAplicacion(texto) {
  return new RegExp(`^[0-9]{${DIGITOS}}$`).test(limpiarCodigo(texto));
}

/**
 * Por qué no se puede enviar el código de la aplicación, o `null` si se puede.
 *
 * @param {unknown} texto
 * @returns {string|null}
 */
export function motivoDelCodigo(texto) {
  return esCodigoDeAplicacion(texto)
    ? null
    : `Escribe los ${DIGITOS} dígitos que muestra tu aplicación de autenticación.`;
}

/**
 * Un código de recuperación como se entregó: en mayúsculas y partido en dos
 * bloques de cinco (`K7QX2-M9PRT`). El servidor lo compara sin guion ni
 * mayúsculas, así que la forma es solo para leerlo.
 *
 * @param {unknown} texto
 * @returns {string}
 */
function codigoDeRecuperacionLegible(texto) {
  const limpio = limpiarCodigo(texto).toUpperCase();
  return limpio.length === LONGITUD_RECUPERACION
    ? `${limpio.slice(0, 5)}-${limpio.slice(5)}`
    : limpio;
}

/**
 * Por qué no se puede enviar un código de recuperación, o `null` si se puede.
 *
 * @param {unknown} texto
 * @returns {string|null}
 */
export function motivoDelCodigoDeRecuperacion(texto) {
  const limpio = limpiarCodigo(texto).toUpperCase();
  if (!limpio) {
    return 'Escribe un código de recuperación de los que guardaste.';
  }
  if (limpio.length !== LONGITUD_RECUPERACION || !ALFABETO_RECUPERACION.test(limpio)) {
    return `Los códigos de recuperación tienen ${LONGITUD_RECUPERACION} caracteres, como K7QX2-M9PRT.`;
  }
  return null;
}

/**
 * La clave en grupos de cuatro, para copiarla a mano sin perderse.
 *
 * @param {string} secreto en base32
 * @returns {string}
 */
export function secretoLegible(secreto) {
  return (String(secreto ?? '').match(/.{1,4}/g) ?? []).join(' ');
}

// ------------------------------------------------------- guardar los códigos

/**
 * El texto del archivo de códigos de recuperación.
 *
 * @param {string[]} codigos
 * @param {{emisor?: string, cuenta?: string|null, fecha?: Date}} [opciones]
 * @returns {string}
 */
export function textoDeCodigos(
  codigos,
  { emisor = 'Nexus Battles VI', cuenta = null, fecha = new Date() } = {},
) {
  return [
    `${emisor} — códigos de recuperación de la verificación en dos pasos`,
    cuenta ? `Cuenta: ${cuenta}` : null,
    `Generados: ${fecha.toISOString().slice(0, 10)}`,
    '',
    'Cada código sirve una sola vez para entrar si no tienes tu aplicación de autenticación.',
    'Guárdalos en un lugar seguro: quien los tenga y sepa tu contraseña puede entrar en tu cuenta.',
    '',
    ...codigos,
    '',
  ]
    .filter((linea) => linea !== null)
    .join('\n');
}

/**
 * @param {Date} [fecha]
 * @returns {string}
 */
export function nombreDelArchivo(fecha = new Date()) {
  return `nexus-battles-codigos-de-recuperacion-${fecha.toISOString().slice(0, 10)}.txt`;
}

/**
 * Descarga un texto como archivo, con un enlace temporal que se retira.
 *
 * @param {string} texto
 * @param {string} nombre
 * @param {{documento?: Document, url?: typeof URL}} [entorno]
 */
export function descargarTexto(
  texto,
  nombre,
  { documento = globalThis.document, url = globalThis.URL } = {},
) {
  if (!documento?.body || typeof url?.createObjectURL !== 'function') {
    throw new Error('Tu navegador no deja guardar el archivo desde aquí.');
  }
  const direccion = url.createObjectURL(new Blob([texto], { type: 'text/plain;charset=utf-8' }));
  const enlace = documento.createElement('a');
  enlace.href = direccion;
  enlace.download = nombre;
  enlace.hidden = true;
  documento.body.append(enlace);
  enlace.click();
  enlace.remove();
  setTimeout(() => url.revokeObjectURL?.(direccion), 0);
}

/**
 * Copia al portapapeles. Devuelve si se pudo: sin portapapeles (navegador
 * viejo, contexto no seguro, permiso denegado) no se finge.
 *
 * @param {string} texto
 * @param {{portapapeles?: {writeText: (t: string) => Promise<void>}|null}} [entorno]
 * @returns {Promise<boolean>}
 */
export async function copiarTexto(texto, { portapapeles = globalThis.navigator?.clipboard } = {}) {
  if (typeof portapapeles?.writeText !== 'function') {
    return false;
  }
  try {
    await portapapeles.writeText(texto);
    return true;
  } catch {
    return false;
  }
}

// ------------------------------------------------------------------ rechazos

/**
 * Qué decir de un código incorrecto según dónde se escribió: en el acceso y
 * al desactivar cuenta como intento fallido de la cuenta; al confirmar una
 * clave nueva, no (lo normal ahí es un reloj desajustado).
 */
const CODIGO_INVALIDO_SEGUN_CONTEXTO = Object.freeze({
  acceso:
    'Escribe el que muestra ahora tu aplicación (cambia cada 30 segundos) o uno de tus códigos de recuperación. Tras varios intentos fallidos la cuenta se bloquea unos minutos.',
  activacion:
    'Comprueba que la hora de tu teléfono sea automática y escribe el código que se ve ahora en tu aplicación.',
  cuenta:
    'Escribe el código que muestra ahora tu aplicación o uno de tus códigos de recuperación. Tras varios intentos fallidos la cuenta se bloquea unos minutos.',
});

/**
 * Qué decir cuando el servidor no acepta algo del segundo factor, por su
 * `type` (nunca por el texto).
 *
 * @param {unknown} error
 * @param {{contexto?: 'acceso'|'activacion'|'cuenta'}} [opciones] `acceso`: el
 *   segundo paso del login, donde un código incorrecto cuenta como intento
 *   fallido; `activacion`: al confirmar la clave nueva, donde no cuenta.
 * @returns {{tono: 'error'|'advertencia'|'info', titulo: string, detalle: string,
 *   campo: 'codigo'|'passwordActual'|null, volverAEmpezar: boolean}}
 */
export function rechazoDelSegundoFactor(error, { contexto = 'cuenta' } = {}) {
  const base = { campo: null, volverAEmpezar: false };
  if (!(error instanceof FalloDelSegundoFactor)) {
    return {
      ...base,
      tono: 'error',
      titulo: 'No pudimos comprobarlo',
      detalle: 'La conexión falló. Inténtalo de nuevo en unos segundos.',
    };
  }
  const tono = tonoPorEstado(error.estado);
  const P = PROBLEMAS_DEL_SEGUNDO_FACTOR;
  switch (error.tipo) {
    case P.CODIGO_INVALIDO:
      return {
        ...base,
        tono,
        titulo: 'El código no es válido',
        detalle: CODIGO_INVALIDO_SEGUN_CONTEXTO[contexto] ?? CODIGO_INVALIDO_SEGUN_CONTEXTO.cuenta,
        campo: 'codigo',
      };
    case P.DESAFIO_INVALIDO:
      return {
        ...base,
        tono,
        titulo: 'Tu verificación caducó',
        detalle: 'Vuelve a escribir tu contraseña para empezar de nuevo.',
        volverAEmpezar: true,
      };
    case P.CUENTA_BLOQUEADA:
      return {
        ...base,
        tono,
        titulo: 'Cuenta bloqueada temporalmente',
        detalle:
          'Hubo demasiados intentos fallidos. Espera unos minutos antes de volver a intentarlo.',
        volverAEmpezar: contexto === 'acceso',
      };
    case P.NO_DISPONIBLE:
      return {
        ...base,
        tono: 'error',
        titulo: 'La verificación en dos pasos no está disponible ahora mismo',
        detalle:
          contexto === 'acceso'
            ? 'Si tienes a mano un código de recuperación, úsalo; si no, inténtalo más tarde.'
            : 'Inténtalo de nuevo más tarde.',
      };
    case P.CONTRASENA_INCORRECTA:
      return {
        ...base,
        tono,
        titulo: 'La contraseña actual es incorrecta',
        detalle: 'Cuenta como un intento fallido: tras varios, la cuenta se bloquea unos minutos.',
        campo: 'passwordActual',
      };
    case P.YA_ACTIVO:
      return {
        ...base,
        tono,
        titulo: 'La verificación en dos pasos ya está activa',
        detalle: 'Para cambiar de aplicación, desactívala primero y vuelve a activarla.',
      };
    case P.NO_ACTIVO:
      return {
        ...base,
        tono,
        titulo: 'La verificación en dos pasos no está activa',
        detalle: 'No hay nada que desactivar en tu cuenta.',
      };
    case P.SIN_ENROLAMIENTO:
      return {
        ...base,
        tono,
        titulo: 'Primero genera la clave para tu aplicación',
        detalle: 'Pulsa «Activar» de nuevo para obtener una clave nueva.',
      };
    case P.DATOS_INVALIDOS:
      return {
        ...base,
        tono,
        titulo: 'Revisa el código',
        detalle: 'Escribe el código de tu aplicación o uno de tus códigos de recuperación.',
        campo: 'codigo',
      };
    default:
      break;
  }
  if (CUENTA_QUE_NO_PUEDE_ENTRAR.has(error.tipo)) {
    return {
      ...base,
      tono,
      titulo: 'Esta cuenta no puede entrar ahora',
      detalle: error.detalle ?? 'Vuelve a intentarlo más tarde.',
      volverAEmpezar: true,
    };
  }
  if (!error.estado || error.estado >= 500) {
    return {
      ...base,
      tono: 'error',
      titulo: 'No pudimos comprobarlo',
      detalle: 'El servicio no respondió bien. Inténtalo de nuevo en unos minutos.',
    };
  }
  return {
    ...base,
    tono,
    titulo: 'No pudimos completar la verificación',
    detalle: error.detalle ?? 'Revisa los datos e inténtalo de nuevo.',
  };
}

// ------------------------------------------------------------------ interfaz

/**
 * La clave recién generada, lista para añadirla a la aplicación: un enlace
 * `otpauth://` que la abre directamente y la clave para escribirla a mano. No
 * hay código QR: el repositorio no tiene con qué dibujarlo y no se añaden
 * bibliotecas para eso.
 *
 * @param {HTMLElement} contenedor
 * @param {{secreto: string, uriOtpauth: string, cuenta?: string, emisor?: string}} enrolamiento
 * @param {{copiar?: (texto: string) => Promise<boolean>}} [opciones]
 */
export function pintarSecreto(contenedor, enrolamiento, { copiar = copiarTexto } = {}) {
  const pasos = h('ol', {
    clase: 'segundo-factor__pasos',
    hijos: [
      h('li', {
        texto:
          'Abre tu aplicación de autenticación (Google Authenticator, Microsoft Authenticator, Aegis u otra).',
      }),
      h('li', {
        texto:
          'Añade la cuenta con el enlace de abajo o escribiendo la clave a mano, como cuenta basada en el tiempo.',
      }),
      h('li', { texto: 'Escribe aquí el código de 6 dígitos que te muestre.' }),
    ],
  });
  const enlace = h('a', {
    clase: 'boton boton--secundario',
    texto: 'Abrir en mi aplicación de autenticación',
    datos: { zona: 'enlace-otpauth' },
  });
  enlace.setAttribute('href', enrolamiento.uriOtpauth);

  const acuse = h('p', {
    clase: 't-meta',
    datos: { zona: 'acuse-secreto' },
    atributos: { role: 'status', 'aria-live': 'polite' },
  });
  const copiarClave = h('button', {
    clase: 'boton boton--secundario boton--pequeno',
    texto: 'Copiar la clave',
    datos: { accion: 'copiar-secreto' },
    atributos: { type: 'button' },
  });
  copiarClave.addEventListener('click', async () => {
    const copiada = await copiar(enrolamiento.secreto);
    acuse.textContent = copiada
      ? 'Clave copiada.'
      : 'Tu navegador no deja copiar solo. Selecciona la clave y cópiala a mano.';
  });

  contenedor.replaceChildren(
    pasos,
    h('div', { clase: 'fila fila--envuelta', hijos: [enlace] }),
    h('div', {
      clase: 'pila pila--compacta',
      hijos: [
        h('p', { clase: 'campo__etiqueta', texto: 'Clave para escribirla a mano' }),
        h('code', {
          clase: 'codigo-invitacion segundo-factor__secreto',
          texto: secretoLegible(enrolamiento.secreto),
          datos: { zona: 'secreto' },
        }),
        h('p', {
          clase: 't-meta',
          texto: `Cuenta: ${enrolamiento.cuenta ?? ''} · ${enrolamiento.emisor ?? 'Nexus Battles VI'}`,
        }),
        h('div', { clase: 'fila fila--envuelta', hijos: [copiarClave] }),
        acuse,
      ],
    }),
  );
  contenedor.hidden = false;
}

/**
 * Los códigos de recuperación, la única vez que se ven: con copiar,
 * descargar y «Ya los guardé» para seguir.
 *
 * @param {HTMLElement} contenedor
 * @param {string[]} codigos
 * @param {{cuenta?: string|null, copiar?: (texto: string) => Promise<boolean>,
 *   descargar?: (texto: string, nombre: string) => void, alTerminar: () => void,
 *   textoDelBoton?: string}} opciones
 */
export function pintarCodigosDeRecuperacion(
  contenedor,
  codigos,
  {
    cuenta = null,
    copiar = copiarTexto,
    descargar = descargarTexto,
    alTerminar,
    textoDelBoton = 'Ya los guardé',
  },
) {
  const texto = textoDeCodigos(codigos, { cuenta });
  const acuse = h('p', {
    clase: 't-meta',
    datos: { zona: 'acuse-codigos' },
    atributos: { role: 'status', 'aria-live': 'polite' },
  });

  const copiarTodos = h('button', {
    clase: 'boton boton--secundario',
    texto: 'Copiar los códigos',
    datos: { accion: 'copiar-codigos' },
    atributos: { type: 'button' },
  });
  copiarTodos.addEventListener('click', async () => {
    acuse.textContent = (await copiar(texto))
      ? 'Códigos copiados.'
      : 'Tu navegador no deja copiar solo. Descárgalos o cópialos a mano.';
  });

  const bajar = h('button', {
    clase: 'boton boton--secundario',
    texto: 'Descargar en un archivo',
    datos: { accion: 'descargar-codigos' },
    atributos: { type: 'button' },
  });
  bajar.addEventListener('click', () => {
    try {
      descargar(texto, nombreDelArchivo());
      acuse.textContent = 'Archivo descargado.';
    } catch {
      acuse.textContent = 'Tu navegador no deja guardar el archivo desde aquí. Cópialos a mano.';
    }
  });

  const seguir = h('button', {
    clase: 'boton boton--primario',
    texto: textoDelBoton,
    datos: { accion: 'codigos-guardados' },
    atributos: { type: 'button' },
  });
  seguir.addEventListener('click', () => alTerminar());

  contenedor.replaceChildren(
    h('div', {
      clase: 'aviso aviso--advertencia',
      atributos: { role: 'alert' },
      hijos: [
        h('div', {
          clase: 'aviso__cuerpo',
          hijos: [
            h('p', { clase: 'aviso__titulo', texto: 'Guarda tus códigos de recuperación' }),
            h('p', {
              clase: 'aviso__detalle',
              texto:
                'Cada uno sirve una sola vez para entrar si no tienes tu teléfono. No los volveremos a mostrar.',
            }),
          ],
        }),
      ],
    }),
    h('ul', {
      clase: 'segundo-factor__codigos',
      atributos: { 'aria-label': 'Códigos de recuperación' },
      hijos: codigos.map((codigo) =>
        h('li', {
          hijos: [
            h('code', {
              clase: 'codigo-invitacion',
              texto: codigo,
              datos: { zona: 'codigo-recuperacion' },
            }),
          ],
        }),
      ),
    }),
    h('div', { clase: 'fila fila--envuelta', hijos: [copiarTodos, bajar] }),
    acuse,
    h('div', { clase: 'tarjeta__acciones', hijos: [seguir] }),
  );
  contenedor.hidden = false;
  seguir.focus();
}
