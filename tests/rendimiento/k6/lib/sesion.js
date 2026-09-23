/**
 * Sesion contra `ms-identidad` — el camino de entrada de todos los escenarios.
 *
 * El token se consigue UNA vez, en `setup()`, y se reparte a todos los usuarios
 * virtuales. No es por ahorrar: si cada VU iniciara sesion por su cuenta, la
 * primera iteracion de cada uno cargaria con el login y el p95 del escenario
 * medido saldria contaminado por una peticion que no es la que se esta
 * midiendo. El escenario `login` si mide el login, y ahi es el unico trabajo
 * que hace.
 *
 * @module lib/sesion
 */

import http from 'k6/http';

import { CONFIG, cabecerasCon } from './entorno.js';
import { campoJson } from './medicion.js';

/**
 * Inicia sesion con las credenciales de la corrida.
 *
 * @param {Record<string, string>} [etiquetas] etiquetas de k6 para la peticion
 * @returns {import('k6/http').Response}
 */
export function iniciarSesion(etiquetas) {
  return http.post(
    CONFIG.baseUrl + '/api/v1/auth/login',
    JSON.stringify({ email: CONFIG.usuario, password: CONFIG.clave }),
    {
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      tags: etiquetas || {},
    },
  );
}

/**
 * Comprueba el entorno y deja la sesion lista antes de la primera medicion.
 *
 * Las dos comprobaciones existen para que un entorno mal configurado se
 * distinga de un entorno lento. Sin ellas, una URL equivocada o un token que
 * `salas-partidas` no acepta producen una corrida entera con 100 % de error y
 * un informe que hay que interpretar; con ellas, la corrida se detiene en el
 * segundo cero diciendo que paso.
 *
 * @returns {{token: string}} lo que k6 entrega a cada escenario como argumento
 */
export function prepararSesion() {
  const acceso = iniciarSesion({ operacion: 'preparacion' });

  if (acceso.status !== 200) {
    throw new Error(
      'No se pudo iniciar sesion en ' +
        CONFIG.baseUrl +
        ' como ' +
        CONFIG.usuario +
        ': HTTP ' +
        acceso.status +
        (acceso.error ? ' (' + acceso.error + ')' : '') +
        '.\n' +
        '  Revisar BASE_URL, USUARIO y CLAVE, y que el entorno este levantado.\n' +
        '  Para el banco local: docker compose -f tests/e2e/compose.yml up -d --wait',
    );
  }

  const token = campoJson(acceso, 'token');
  if (!token) {
    throw new Error(
      'El login respondio 200 pero sin campo `token`. Respuesta: ' + acceso.body.substring(0, 300),
    );
  }

  // El token lo emite `ms-identidad`; quien tiene que aceptarlo es
  // `salas-partidas`, que es otro servicio con otra cadena de seguridad. Esta
  // sonda comprueba justo esa costura, que ya ha fallado antes (ADR-002).
  const sonda = http.get(CONFIG.baseUrl + '/api/v1/salas?pagina=0&tamano=1', {
    headers: cabecerasCon(token),
    tags: { operacion: 'preparacion' },
  });
  if (sonda.status !== 200) {
    throw new Error(
      'El login funciono, pero GET /api/v1/salas respondio HTTP ' +
        sonda.status +
        '.\n' +
        '  El token de ms-identidad no lo esta aceptando salas-partidas, o el borde\n' +
        '  no enruta /api/v1/salas. Sin esto, tres de los cuatro escenarios medirian\n' +
        '  solo rechazos. Cuerpo: ' +
        String(sonda.body).substring(0, 300),
    );
  }

  return { token: token };
}
