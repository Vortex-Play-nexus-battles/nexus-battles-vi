/**
 * Las entradas de la bitácora central dichas para quien opera — §7.3.5
 * (SA-5), ms-cumplimiento-auditoria 1.3.0.
 *
 * La bitácora guarda identificadores: el `uid` de quien actuó (los servicios
 * de plataforma) o su apodo (ms-identidad), y sobre qué recayó —el `uid` de
 * una cuenta, `comentario:<id>`, `parametro:<clave>`, `lista-negra:<término>`—.
 * Aquí se convierten en lo que se lee: el apodo de la cuenta, el nombre del
 * parámetro, el término entre comillas. Ningún identificador llega a la tabla;
 * el detalle de la entrada los conserva para quien los necesite.
 *
 * Lo usan la bitácora de auditoría (cuentas/auditoria) y el panel de
 * acciones recientes de la consola de control integral.
 *
 * @module plataforma/consola/entradas-de-auditoria
 */

import { NOMBRE_DE_ROL } from '../../comun/shell.js';
import { ESTADOS_DE_CUENTA_ADMIN } from '../../comun/ui/admin/estado.js';
import { CATALOGO_DE_PARAMETROS } from '../../comun/ui/admin/parametros.js';
import { NOMBRE_DEL_PERMISO } from '../../comun/ui/admin/permisos.js';
import { h } from '../../comun/ui/dom.js';
import { esUid } from './titulares.js';

/** Hasta cuántos caracteres de un motivo o de un valor caben en una celda. */
export const LARGO_EN_LA_TABLA = 90;

/** Quien actuó cuando el origen no lo pudo saber (un intento sin credencial). */
const SIN_IDENTIFICAR = new Set(['desconocido', 'anonymous', 'anonimo']);

/**
 * Los motivos que ms-identidad registra como código ante un intento de saltarse
 * un permiso (`SecurityInterceptor`), dichos en palabras.
 */
const MOTIVO_DE_CODIGO = Object.freeze({
  CREDENTIAL_MISSING: 'Sin credencial',
  FORBIDDEN_ROLE: 'Su rol no tiene ese permiso',
  FAIL_CLOSED_ERROR: 'No se pudo comprobar el permiso',
  TOKEN_VERSION_REVOKED: 'Sesión revocada',
  JWT_INVALID_OR_EXPIRED: 'Sesión no válida o caducada',
  HEADER_ROL_DESHABILITADO: 'Intentó acreditar un rol por cabecera',
  INTENTO_NO_AUTORIZADO: 'Intento no autorizado',
});

/** Los valores que llegan como código (un rol, un estado, un 403), en palabras. */
const VALOR_DE_CODIGO = Object.freeze({
  '403_FORBIDDEN': 'Acceso denegado',
  ANONYMOUS: 'Sin sesión',
  INVALID_JWT: 'Sesión no válida',
  UNKNOWN: 'Rol desconocido',
  NONE: 'Ninguno',
  ...NOMBRE_DE_ROL,
  ...Object.fromEntries(
    Object.entries(ESTADOS_DE_CUENTA_ADMIN).map(([codigo, { texto }]) => [codigo, texto]),
  ),
});

/**
 * El momento de la acción: cuando ocurrió en su servicio (`ocurridoEn`, 1.3.0)
 * y, si la entrada no lo trae, cuando quedó registrada.
 *
 * @param {{ocurridoEn?: string|null, fechaHora?: string|null}|null|undefined} entrada
 * @returns {string|null}
 */
export function momentoDe(entrada) {
  return entrada?.ocurridoEn || entrada?.fechaHora || null;
}

/**
 * Los `uid` que aparecen en las entradas, quien actuó y sobre quién, sin
 * repetir: se nombran de una vez.
 *
 * @param {Array<{administrador?: string, afectado?: string}>} entradas
 * @returns {string[]}
 */
export function uidsDe(entradas) {
  const uids = new Set();
  for (const entrada of entradas ?? []) {
    for (const valor of [entrada?.administrador, entrada?.afectado]) {
      if (esUid(valor)) {
        uids.add(String(valor).trim().toLowerCase());
      }
    }
  }
  return [...uids];
}

/**
 * Quién actuó: el apodo de su cuenta. Las entradas de ms-identidad ya traen el
 * apodo; las de los servicios de plataforma, el `uid`, que se nombra.
 *
 * @param {string|null|undefined} administrador
 * @param {{de: (uid: string) => ({apodo?: string}|null)}|null} titulares
 * @returns {{texto: string, identificado: boolean, uid: string|null}}
 */
export function quienDe(administrador, titulares) {
  const valor = String(administrador ?? '').trim();
  if (!valor || SIN_IDENTIFICAR.has(valor.toLowerCase())) {
    return { texto: 'Sin identificar', identificado: false, uid: null };
  }
  if (esUid(valor)) {
    const apodo = titulares?.de(valor)?.apodo;
    return apodo
      ? { texto: apodo, identificado: true, uid: valor }
      : { texto: 'Cuenta sin apodo', identificado: false, uid: valor };
  }
  return { texto: valor, identificado: true, uid: null };
}

/** SA-18: lo que la bitácora importa del catálogo, por el prefijo de lo afectado. */
const DEL_CATALOGO = Object.freeze({
  producto: Object.freeze({ clase: 'Producto', sinNombre: 'Producto del catálogo' }),
  banner: Object.freeze({ clase: 'Banner', sinNombre: 'Banner de inicio' }),
  recurso: Object.freeze({ clase: 'Recurso gráfico', sinNombre: 'Recurso gráfico' }),
});

/**
 * Sobre qué recayó la acción, con su clase («Cuenta», «Comentario»…). Las
 * operaciones del catálogo (SA-18) llegan con el nombre que tenía lo afectado
 * en ese momento (`nombreAfectado`); sin él, se dice qué era.
 *
 * @param {string|null|undefined} afectado
 * @param {{de: (uid: string) => ({apodo?: string}|null)}|null} titulares
 * @param {string|null} [nombre] el `nombreAfectado` de la entrada
 * @returns {{texto: string|null, clase: string|null, uid: string|null, referencia: string|null}}
 */
export function sobreDe(afectado, titulares, nombre = null) {
  const valor = String(afectado ?? '').trim();
  if (!valor) {
    return { texto: null, clase: null, uid: null, referencia: null };
  }
  if (esUid(valor)) {
    const apodo = titulares?.de(valor)?.apodo;
    return { texto: apodo ?? 'Cuenta sin apodo', clase: 'Cuenta', uid: valor, referencia: valor };
  }
  if (/^\d+$/.test(valor)) {
    return { texto: `Cuenta n.º ${valor}`, clase: 'Cuenta', uid: null, referencia: valor };
  }
  const [prefijo, ...resto] = valor.split(':');
  const dato = resto.join(':').trim();
  if (dato) {
    if (prefijo === 'comentario') {
      return { texto: 'Comentario', clase: null, uid: null, referencia: dato };
    }
    if (prefijo === 'parametro') {
      return {
        texto: CATALOGO_DE_PARAMETROS[dato]?.nombre ?? dato,
        clase: 'Parámetro',
        uid: null,
        referencia: dato,
      };
    }
    if (prefijo === 'lista-negra') {
      return { texto: `«${dato}»`, clase: 'Lista negra', uid: null, referencia: dato };
    }
    const delCatalogo = DEL_CATALOGO[prefijo];
    if (delCatalogo) {
      const conNombre = String(nombre ?? '').trim();
      return {
        texto: conNombre ? `«${conNombre}»` : delCatalogo.sinNombre,
        clase: delCatalogo.clase,
        uid: null,
        referencia: dato,
      };
    }
  }
  if (valor === 'registro-de-auditoria') {
    return { texto: 'Registro de auditoría', clase: 'Auditoría', uid: null, referencia: null };
  }
  if (NOMBRE_DEL_PERMISO[valor]) {
    return { texto: NOMBRE_DEL_PERMISO[valor], clase: 'Permiso', uid: null, referencia: valor };
  }
  return { texto: valor, clase: null, uid: null, referencia: null };
}

/**
 * Un texto largo recortado para la celda; el completo queda en el detalle.
 *
 * @param {string|null|undefined} texto
 * @param {number} [largo]
 * @returns {string|null}
 */
export function recortado(texto, largo = LARGO_EN_LA_TABLA) {
  const limpio = String(texto ?? '')
    .replace(/\s+/g, ' ')
    .trim();
  if (!limpio) {
    return null;
  }
  return limpio.length <= largo ? limpio : `${limpio.slice(0, largo - 1).trimEnd()}…`;
}

/**
 * Un motivo registrado como código (los intentos de acceso de ms-identidad)
 * en palabras; cualquier otro, tal cual.
 *
 * @param {string|null|undefined} motivo
 * @returns {string|null}
 */
export function motivoLegible(motivo) {
  const texto = String(motivo ?? '').trim();
  if (!texto) {
    return null;
  }
  const codigo = texto.split(':')[0].trim();
  return MOTIVO_DE_CODIGO[codigo] ?? texto;
}

/**
 * Un valor que llegó como código (un rol, un estado, «403_FORBIDDEN») en
 * palabras; una frase, tal cual.
 *
 * @param {string|null|undefined} valor
 * @returns {string|null}
 */
export function valorLegible(valor) {
  const texto = String(valor ?? '').trim();
  if (!texto) {
    return null;
  }
  return VALOR_DE_CODIGO[texto] ?? texto;
}

/**
 * El cambio en una línea: «Activa → SUSPENDIDO hasta…», solo «→ nuevo» o
 * solo lo que había. Sin ninguno de los dos, nada.
 *
 * @param {{valorAnterior?: string|null, valorNuevo?: string|null}} entrada
 * @returns {string|null}
 */
export function cambioDe(entrada) {
  const antes = recortado(valorLegible(entrada?.valorAnterior), 40);
  const despues = recortado(valorLegible(entrada?.valorNuevo), 60);
  if (antes && despues) {
    return `${antes} → ${despues}`;
  }
  if (despues) {
    return despues;
  }
  return antes ? `Antes: ${antes}` : null;
}

/**
 * El contenido de la celda «Sobre»: lo que es y, debajo y en pequeño, qué
 * clase de cosa es.
 *
 * @param {ReturnType<typeof sobreDe>} sobre
 * @param {{enlaceDeCuenta?: ((uid: string) => string|null)|null}} [opciones]
 * @returns {Node|null}
 */
export function celdaDeSobre(sobre, { enlaceDeCuenta = null } = {}) {
  if (!sobre?.texto) {
    return null;
  }
  const destino = sobre.uid && enlaceDeCuenta ? enlaceDeCuenta(sobre.uid) : null;
  const principal = destino
    ? h('a', { texto: sobre.texto, atributos: { href: destino } })
    : h('span', { texto: sobre.texto });
  return h('span', {
    clase: 'celda-doble',
    hijos: [principal, sobre.clase ? h('span', { clase: 't-meta', texto: sobre.clase }) : null],
  });
}
