/**
 * Quién es el jugador de esta sesión — ADR-002.
 *
 * El defecto original: tres vistas leían `nexus.usuarioId` y nadie lo escribía,
 * así que en producción siempre valía `null`.
 *
 * El segundo, que este archivo llegó a fijar por error: sí lo escriben, y con
 * dos valores que no son el identificador de ADR-002 —la clave primaria que
 * guarda el login, y el usuario que el administrador consulta en el panel—.
 * Ahora manda el token.
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
  return {
    getItem: (clave) => datos[clave] ?? null,
    setItem: (clave, valor) => {
      datos[clave] = String(valor);
    },
  };
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

  test('el token gana a nexus.usuarioId, porque esa clave esta contaminada', () => {
    // Esta prueba decia lo contrario. Se cambia porque fijaba un defecto:
    //
    //   - login.js guarda ahi `body.usuarioId`, un Long de la tabla ("7"),
    //     no el UUID de ADR-002;
    //   - gestion-usuarios.js guarda ahi el usuario que el ADMINISTRADOR
    //     acaba de seleccionar en el panel.
    //
    // Con el orden anterior, la sesion de un administrador que consultaba a
    // otra persona quedaba con la identidad ajena. El token lo firma el
    // servidor y ninguna vista lo pisa por descuido.
    const sesion = almacen({
      'nexus.usuarioId': '7',
      'nexus.token': token({ uid: UID }),
    });

    expect(usuarioIdDeSesion(sesion)).toBe(UID);
  });

  test('sin token legible, lo guardado es mejor que nada', () => {
    const sesion = almacen({ 'nexus.usuarioId': 'jugador-7' });

    expect(usuarioIdDeSesion(sesion)).toBe('jugador-7');
  });

  test('un administrador que consulta a otro usuario conserva SU identidad', () => {
    const sesion = almacen({ 'nexus.token': token({ uid: UID }) });

    // Lo que hace gestion-usuarios.js al seleccionar una ficha ajena.
    sesion.setItem('nexus.usuarioId', '99');

    expect(usuarioIdDeSesion(sesion)).toBe(UID);
  });

  test('sin sesion devuelve null, no una cadena vacia', () => {
    expect(usuarioIdDeSesion(almacen({}))).toBeNull();
    expect(usuarioIdDeSesion(undefined)).toBeNull();
  });
});
