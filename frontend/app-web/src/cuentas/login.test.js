/**
 * HU-RBAC-005 - Separación de credenciales por ambiente.
 *
 * Verifica que el frontend presente un rechazo explícito cuando
 * las credenciales no son válidas para el ambiente actual.
 */

describe('Login - aislamiento de credenciales por ambiente', () => {
  beforeAll(() => {
    document.body.innerHTML = `
      <form id="formLogin">
        <input id="email" name="email" type="email">
        <input id="password" name="password" type="password">
        <button id="botonEnviar" type="submit">Iniciar sesión</button>
      </form>

      <p id="estadoLogin" hidden></p>
      <p id="avisoDispositivo" hidden></p>
    `;
  });

  test('muestra un rechazo explícito cuando las credenciales no pertenecen al ambiente', async () => {
    const { mensajeDeError } = await import('./login.js');

    const mensaje = mensajeDeError(401, 'Correo o contraseña incorrectos.');

    expect(mensaje).toContain('Acceso rechazado');
    expect(mensaje).toContain('estas credenciales no están registradas en este ambiente');
  });

  test('no expone información que permita saber si el correo existe', async () => {
    const { mensajeDeError } = await import('./login.js');

    const mensaje = mensajeDeError(401);

    expect(mensaje).toContain('correo o la contraseña son incorrectos');
    expect(mensaje).not.toContain('usuario inexistente');
    expect(mensaje).not.toContain('correo registrado');
  });

  test('mantiene el mensaje específico enviado por el backend para un 403', async () => {
    const { mensajeDeError } = await import('./login.js');

    const mensaje = mensajeDeError(403, 'Esta cuenta ha sido suspendida.');

    expect(mensaje).toBe('Esta cuenta ha sido suspendida.');
  });

  test('mantiene el mensaje de bloqueo temporal para un 423', async () => {
    const { mensajeDeError } = await import('./login.js');

    const mensaje = mensajeDeError(423, 'Cuenta bloqueada temporalmente.');

    expect(mensaje).toBe('Cuenta bloqueada temporalmente.');
  });
});

describe('Login - por qué se llega aquí (R17)', () => {
  test('cada motivo tiene su aviso, y uno desconocido no pinta nada', async () => {
    const { avisoDelMotivo } = await import('./login.js');

    expect(avisoDelMotivo('caducada')).toEqual({
      tipo: 'advertencia',
      texto: 'Tu sesión terminó. Vuelve a entrar y te llevamos a donde estabas.',
    });
    expect(avisoDelMotivo('cerrada').texto).toContain('Cerraste sesión');
    expect(avisoDelMotivo('registrada').tipo).toBe('exito');
    expect(avisoDelMotivo('<script>')).toBeNull();
    expect(avisoDelMotivo(null)).toBeNull();
  });
});

describe('Login - identidad de la sesion (#426, ADR-002)', () => {
  const token = (claims) =>
    `x.${btoa(JSON.stringify(claims)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')}.y`;

  test('guarda el uid del token y no la clave primaria', async () => {
    const { identificadorDeSesion } = await import('./login.js');
    expect(
      identificadorDeSesion(token({ uid: '11111111-1111-1111-1111-111111111111', sub: 'lyra' }), 7),
    ).toBe('11111111-1111-1111-1111-111111111111');
  });

  test('sin uid legible cae a la clave primaria, sin romper', async () => {
    const { identificadorDeSesion } = await import('./login.js');
    expect(identificadorDeSesion(token({ sub: 'lyra' }), 7)).toBe('7');
    expect(identificadorDeSesion('no-es-un-jwt', 7)).toBe('7');
    expect(identificadorDeSesion(undefined, undefined)).toBe('');
  });
});
