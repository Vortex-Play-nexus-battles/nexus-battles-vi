/**
 * Escenario `login` — RNF-REN-001.
 *
 * `POST /api/v1/auth/login` es el camino obligado de todo lo demas: sin sesion
 * no hay listado, ni sala, ni inventario. Si se degrada, el jugador ni siquiera
 * llega a las pantallas donde se notarian las otras latencias, asi que se mide
 * aparte y con su propio umbral.
 *
 * @module escenarios/autenticacion
 */

import { campoJson, medir, pausar } from '../lib/medicion.js';
import { iniciarSesion } from '../lib/sesion.js';

/**
 * Una iteracion: una autenticacion completa contra `ms-identidad` por el borde.
 */
export function medirLogin() {
  const respuesta = iniciarSesion({ escenario: 'login' });

  medir('login', respuesta, {
    'responde 200': (r) => r.status === 200,
    'trae token de sesion': (r) => {
      const token = campoJson(r, 'token');
      return typeof token === 'string' && token.length > 0;
    },
  });

  pausar();
}
