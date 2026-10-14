/**
 * Quién es cada cuenta, para la consola — ms-identidad-admin 1.9.0
 * (`GET /api/v1/admin/titulares`): uid, apodo, estado y rol, sin correo.
 *
 * Un identificador en una tabla no le dice nada a quien opera; aquí se pide
 * el apodo de cada cuenta que aparece, de una vez y sin repetir. Lo usan la
 * consola de sanciones (SA-4), la bitácora de auditoría y la consola de
 * control integral (SA-5).
 *
 * @module plataforma/consola/titulares
 */

import { RESULTADO } from './cliente-consola.js';

/** Hasta cuántas cuentas se nombran de una vez (ms-identidad-admin 1.9.0). */
export const TITULARES_POR_CONSULTA = 100;

/** Un `uid` de cuenta. */
export const UID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * @param {*} valor
 * @returns {boolean}
 */
export function esUid(valor) {
  return UID.test(String(valor ?? '').trim());
}

/**
 * Los titulares de las cuentas, con lo que ya se sabe guardado: cada página
 * pide solo los que faltan, de una vez.
 *
 * @param {{consultarApi: typeof import('./cliente-consola.js').consultar}} opciones
 */
export function directorioDeTitulares({ consultarApi }) {
  /** @type {Map<string, {uid: string, apodo: string, estado: string, rol?: string|null}>} */
  const conocidos = new Map();
  /**
   * Las consultas en curso, por `uid`: el listado y las apelaciones cargan a
   * la vez y piden muchas de las mismas cuentas; la segunda espera a la
   * primera en vez de repetirla.
   *
   * @type {Map<string, Promise<boolean>>}
   */
  const enCurso = new Map();

  const recordar = (titular) => {
    if (titular?.uid) {
      conocidos.set(String(titular.uid).toLowerCase(), titular);
    }
  };

  /** @param {string[]} tanda */
  async function consultarTanda(tanda) {
    const desenlace = await consultarApi(`/admin/titulares?uids=${tanda.join(',')}`);
    if (desenlace.resultado === RESULTADO.VACIO) {
      return true;
    }
    if (desenlace.resultado !== RESULTADO.DATOS || !Array.isArray(desenlace.datos)) {
      return false;
    }
    desenlace.datos.forEach(recordar);
    return true;
  }

  return {
    /** @param {string} uid */
    de(uid) {
      return conocidos.get(String(uid ?? '').toLowerCase()) ?? null;
    },
    recordar,
    /**
     * @param {string[]} uids
     * @returns {Promise<boolean>} false si identidad no respondió
     */
    async nombrar(uids) {
      const pedidos = [...new Set(uids.map((u) => String(u ?? '').toLowerCase()))].filter((u) =>
        UID.test(u),
      );
      const faltan = pedidos.filter((u) => !conocidos.has(u) && !enCurso.has(u));
      for (let i = 0; i < faltan.length; i += TITULARES_POR_CONSULTA) {
        const tanda = faltan.slice(i, i + TITULARES_POR_CONSULTA);
        const promesa = consultarTanda(tanda).finally(() =>
          tanda.forEach((u) => enCurso.delete(u)),
        );
        tanda.forEach((u) => enCurso.set(u, promesa));
      }
      const esperadas = new Set(pedidos.map((u) => enCurso.get(u)).filter(Boolean));
      const resultados = await Promise.all([...esperadas]);
      return resultados.every(Boolean);
    },
    /**
     * @param {string} texto apodo o parte de él, o un `uid`
     * @returns {Promise<import('./cliente-consola.js').Desenlace>}
     */
    async buscar(texto) {
      const desenlace = await consultarApi(`/admin/titulares?buscar=${encodeURIComponent(texto)}`);
      if (desenlace.resultado === RESULTADO.DATOS && Array.isArray(desenlace.datos)) {
        desenlace.datos.forEach(recordar);
      }
      return desenlace;
    },
  };
}
