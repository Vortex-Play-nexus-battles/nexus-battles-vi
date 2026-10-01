/**
 * Los códigos que llegan por correo — B1 (identidad 2.0.0, correo 1.4.0).
 *
 * Tres pantallas piden un código de un solo uso que la persona acaba de
 * recibir: la verificación de una cuenta recién creada (`verificar-cuenta`),
 * la recuperación de la contraseña y la activación de una cuenta
 * administrativa (las dos en `restablecer-confirmar`). Y el login ofrece
 * reenviarlo cuando la cuenta aún no está verificada. Lo que las cuatro
 * comparten vive aquí: cómo se limpia lo que se pega, cómo se lee el enlace
 * del correo, dónde se recuerda el correo entre pantallas y cómo se lee el
 * `type` de un rechazo.
 *
 * ## Por qué el correo viaja en `sessionStorage` y el código en el fragmento
 *
 * El correo no va nunca en la dirección: quedaría en el historial, en la
 * bitácora del borde y en la cabecera `Referer` de cualquier recurso que se
 * pida después. Entre dos pantallas de la misma pestaña basta
 * `sessionStorage`, que se borra al cerrarla.
 *
 * El enlace del correo sí trae el código y el correo, pero en el FRAGMENTO
 * (`/verificar#codigo=...&correo=...`, correo.yaml 1.4.0): el navegador no lo
 * manda a ningún servidor. La vista lo lee una vez y lo borra de la barra con
 * `history.replaceState`, para que no se quede en el historial ni se copie al
 * compartir la dirección.
 *
 * @module comun/codigo-de-correo
 */

import { pareceTextoTecnico } from './ui/texto-de-fallo.js';

/**
 * Longitud de los códigos que manda hoy el servicio de identidad. Solo se usa
 * para decirla en pantalla: quien valida el código es el servidor.
 */
export const LONGITUD_DEL_CODIGO = 8;

/**
 * Límites del contrato (`CodigoDeCorreoRequest.codigo`: de 6 a 16). Fuera de
 * ellos el servidor rechazaría la petición sin mirarla, así que se avisa antes
 * de enviarla. Dentro, decide el servidor.
 */
export const CODIGO_MINIMO = 6;
export const CODIGO_MAXIMO = 16;

/**
 * Lo que la gente pega junto con el código y no forma parte de él: espacios
 * (también los que no se ven, como el no separable), guiones de cualquier
 * tipo, puntos y guiones bajos.
 */
const SEPARADORES = /[\s\-‐-―_.·]/gu;

/** Claves de `sessionStorage`. Un solo sitio las nombra. */
export const CLAVES_DEL_CORREO = Object.freeze({
  /** Correo de una cuenta que acaba de registrarse y aún no se verificó. */
  porVerificar: 'nexus.verificacion.correo',
  /** Cuándo se pidió el último código de verificación (milisegundos). */
  ultimoEnvio: 'nexus.verificacion.ultimoEnvio',
  /** Correo con el que se pidió recuperar la contraseña. */
  recuperacion: 'nexus.recuperacion.correo',
});

/**
 * Deja un código como lo guarda el servidor: sin separadores y en mayúsculas.
 *
 * Si lo que se pega es el enlace entero del correo, se saca el código de él:
 * es lo que haría cualquiera que copie «lo que viene en el correo».
 *
 * @param {unknown} texto lo que se escribió o se pegó
 * @returns {string}
 */
export function normalizarCodigo(texto) {
  const valor = String(texto ?? '');
  const delEnlace = /[#&?]codigo=([^&#\s]+)/i.exec(valor);
  const crudo = delEnlace ? decodificar(delEnlace[1]) : valor;
  return crudo.replace(SEPARADORES, '').toUpperCase();
}

/**
 * Lo que se puede rechazar sin preguntar al servidor.
 *
 * @param {string} codigo ya normalizado
 * @returns {string|null} el motivo, o `null` si se puede enviar
 */
export function motivoDelCodigo(codigo) {
  if (!codigo) {
    return 'Escribe el código que te llegó por correo.';
  }
  if (codigo.length < CODIGO_MINIMO || codigo.length > CODIGO_MAXIMO) {
    return `El código tiene ${LONGITUD_DEL_CODIGO} caracteres. Revisa que esté completo y sin nada más.`;
  }
  return null;
}

/**
 * Lee el fragmento del enlace del correo: `#codigo=...&correo=...`.
 *
 * No usa `URLSearchParams` a propósito: convierte cada `+` en un espacio, y un
 * `+` es válido en un correo (`ana+nexus@ejemplo.test`).
 *
 * @param {string|null|undefined} hash `location.hash`
 * @returns {{codigo: string, correo: string}|null} `null` si no trae ninguno de los dos
 */
export function leerEnlaceDelCorreo(hash) {
  const texto = String(hash ?? '').replace(/^#/, '');
  if (!texto) {
    return null;
  }
  const valores = {};
  for (const par of texto.split('&')) {
    const [clave, ...resto] = par.split('=');
    if (clave) {
      valores[decodificar(clave)] = decodificar(resto.join('='));
    }
  }
  const codigo = valores.codigo ? normalizarCodigo(valores.codigo) : '';
  const correo = String(valores.correo ?? '').trim();
  if (!codigo && !correo) {
    return null;
  }
  return { codigo, correo };
}

/**
 * Borra el fragmento de la barra de direcciones sin recargar la página.
 *
 * La ruta se escribe absoluta (desde `/`): en una dirección limpia el borde
 * pone un `<base>` hacia la carpeta del fichero, y una ruta relativa se
 * resolvería contra esa carpeta en vez de contra `/verificar`.
 *
 * @param {{historial?: History, ubicacion?: Location}} [entorno]
 */
export function limpiarFragmento({
  historial = globalThis.history,
  ubicacion = globalThis.location,
} = {}) {
  if (!ubicacion?.hash || typeof historial?.replaceState !== 'function') {
    return;
  }
  historial.replaceState(historial.state ?? null, '', `${ubicacion.pathname}${ubicacion.search}`);
}

/**
 * Guarda (o borra, con un valor vacío) un dato de esta pestaña. Nunca falla:
 * sin almacenamiento (navegación privada estricta) la persona escribe el
 * correo otra vez, que es lo peor que puede pasar.
 *
 * @param {string} clave
 * @param {string|null|undefined} valor
 * @param {Storage} [almacen]
 */
export function recordar(clave, valor, almacen = globalThis.sessionStorage) {
  try {
    if (valor === null || valor === undefined || valor === '') {
      almacen?.removeItem(clave);
    } else {
      almacen?.setItem(clave, String(valor));
    }
  } catch {
    // Sin almacenamiento: no hay nada que recordar.
  }
}

/**
 * @param {string} clave
 * @param {Storage} [almacen]
 * @returns {string|null}
 */
export function recordado(clave, almacen = globalThis.sessionStorage) {
  try {
    return almacen?.getItem(clave) ?? null;
  } catch {
    return null;
  }
}

/** Espacio de nombres de los `type` de error del servicio de identidad. */
const ESPACIO_DE_ERRORES = 'https://nexusbattles.upb.edu.co/errors/';

/**
 * El motivo de un rechazo, leído de su `type` (problem details, RFC 7807):
 * `https://nexusbattles.upb.edu.co/errors/codigo-invalido` → `codigo-invalido`.
 * La interfaz decide por esto y nunca por el texto (MAPEO-ERRORES §2).
 *
 * @param {unknown} cuerpo el cuerpo ya leído de la respuesta
 * @returns {string|null}
 */
export function tipoDelProblema(cuerpo) {
  const tipo = cuerpo && typeof cuerpo === 'object' ? cuerpo.type : null;
  if (typeof tipo !== 'string' || !tipo.startsWith(ESPACIO_DE_ERRORES)) {
    return null;
  }
  return tipo.slice(ESPACIO_DE_ERRORES.length) || null;
}

/**
 * El `detail` de un problem details, o el texto plano de un servicio que aún
 * no los manda. Solo para enseñarlo: nunca para decidir.
 *
 * @param {unknown} cuerpo
 * @returns {string|null}
 */
export function detalleDelProblema(cuerpo) {
  // UXC-9 — solo lo que se puede leer: un texto plano puede ser la página de
  // un proxy («502 Bad Gateway») y un `detail`, una excepción. Eso no se
  // enseña; la vista pone entonces su propio texto.
  let detalle = null;
  if (typeof cuerpo === 'string') {
    detalle = cuerpo;
  } else if (cuerpo && typeof cuerpo === 'object') {
    detalle = cuerpo.detail;
  }
  return typeof detalle === 'string' && !pareceTextoTecnico(detalle) ? detalle.trim() : null;
}

/** Los motivos de rechazo de un código, tal como los nombra el contrato. */
export const PROBLEMAS_DEL_CODIGO = Object.freeze({
  CODIGO_INVALIDO: 'codigo-invalido',
  DEMASIADOS_INTENTOS: 'demasiados-intentos',
});

/**
 * Qué decir cuando el servidor no acepta un código. Sirve igual para la
 * verificación que para la recuperación: el contrato usa los mismos dos
 * `type` en las dos, y a propósito no distingue «no existe», «caducó» y «ya
 * se usó» (distinguirlos diría qué correos están registrados).
 *
 * @param {string|null} tipo el de `tipoDelProblema`
 * @param {number} estado código HTTP
 * @returns {{titulo: string, detalle: string, pedirOtro: boolean}|null} `null` si no es un rechazo del código
 */
export function rechazoDelCodigo(tipo, estado) {
  if (tipo === PROBLEMAS_DEL_CODIGO.CODIGO_INVALIDO) {
    return {
      titulo: 'El código no es válido',
      detalle:
        'Puede que esté mal escrito, que haya caducado o que ya se haya usado. Revisa que sea el último que te enviamos o pide uno nuevo.',
      pedirOtro: true,
    };
  }
  if (tipo === PROBLEMAS_DEL_CODIGO.DEMASIADOS_INTENTOS) {
    return {
      titulo: 'Ese código ya no sirve',
      detalle: 'Se anuló tras varios intentos fallidos. Pide un código nuevo para seguir.',
      pedirOtro: true,
    };
  }
  if (estado === 429) {
    // Sin `type`: es el límite por dirección del borde, no el del código.
    return {
      titulo: 'Demasiadas solicitudes seguidas',
      detalle: 'Espera un momento y vuelve a intentarlo.',
      pedirOtro: false,
    };
  }
  return null;
}

/**
 * @param {string} texto
 * @returns {string}
 */
function decodificar(texto) {
  try {
    return decodeURIComponent(texto);
  } catch {
    return texto;
  }
}
