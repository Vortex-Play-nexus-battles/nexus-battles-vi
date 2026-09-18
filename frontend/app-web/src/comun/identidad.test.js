/**
 * Quién es el jugador de esta sesión — ADR-002.
 *
 * El defecto que esto corrige: tres vistas leían `nexus.usuarioId` y nadie lo
 * escribía nunca, así que en producción siempre valía `null`.
 */

import { cuerpoDelToken, usuarioIdDeSesion } from './identidad.js';

const UID = '44444444-4444-4444-4444-444444444444';

/** Arma un JWT de mentira: cabecera y firma dan igual, solo se lee el cuerpo. */
function token(cuerpo) {
  const base64url = (o) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${base64url({ alg: 'none' })}.${base64url(cuerpo)}.firma`;
}

function almacen(datos) {
  return { getItem: (clave) => datos[clave] ?? null };
}

describe('cuerpoDelToken', () => {
  test('lee el cuerpo de un JWT', () => {
    expect(cuerpoDelToken(token({ uid: UID, sub: 'demo_grupo6' }))).toEqual({
      uid: UID,
      sub: 'demo_grupo6',
    });
  });

  test('un token ilegible no rompe la vista: se trata como sin sesion', () => {
    expect(cuerpoDelToken('esto-no-es-un-jwt')).toBeNull();
    expect(cuerpoDelToken('a.b')).toBeNull();
    expect(cuerpoDelToken(null)).toBeNull();
    expect(cuerpoDelToken(undefined)).toBeNull();
  });
});

describe('usuarioIdDeSesion', () => {
  test('sale de uid, que es donde lo pone ms-identidad tras ADR-002', () => {
    const sesion = almacen({ 'nexus.token': token({ uid: UID, sub: 'demo_grupo6' }) });

    expect(usuarioIdDeSesion(sesion)).toBe(UID);
  });

  test('NO devuelve el sub cuando es el apodo: seria mentir', () => {
    // Es el mismo error que provoco el 500 del PR #404 en el backend.
    const sesion = almacen({ 'nexus.token': token({ sub: 'demo_grupo6' }) });

    expect(usuarioIdDeSesion(sesion)).toBeNull();
  });

  test('con un sub que si es UUID vale de respaldo, para los tokens antiguos', () => {
    const sesion = almacen({ 'nexus.token': token({ sub: UID }) });

    expect(usuarioIdDeSesion(sesion)).toBe(UID);
  });

  test('si alguien ya guardo nexus.usuarioId, ese manda', () => {
    // No se rompe a quien lo escriba hoy ni a las pruebas que lo fijan.
    const sesion = almacen({
      'nexus.usuarioId': 'jugador-7',
      'nexus.token': token({ uid: UID }),
    });

    expect(usuarioIdDeSesion(sesion)).toBe('jugador-7');
  });

  test('sin sesion devuelve null, no una cadena vacia', () => {
    expect(usuarioIdDeSesion(almacen({}))).toBeNull();
    expect(usuarioIdDeSesion(undefined)).toBeNull();
  });
});
