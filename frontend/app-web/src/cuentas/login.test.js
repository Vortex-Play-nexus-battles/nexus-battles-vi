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

    const mensaje = mensajeDeError(
      401,
      'Correo o contraseña incorrectos.',
    );

    expect(mensaje).toContain('Acceso rechazado');
    expect(mensaje).toContain(
      'estas credenciales no están registradas en este ambiente',
    );
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

    const mensaje = mensajeDeError(
      403,
      'Esta cuenta ha sido suspendida.',
    );

    expect(mensaje).toBe('Esta cuenta ha sido suspendida.');
  });

  test('mantiene el mensaje de bloqueo temporal para un 423', async () => {
    const { mensajeDeError } = await import('./login.js');

    const mensaje = mensajeDeError(
      423,
      'Cuenta bloqueada temporalmente.',
    );

    expect(mensaje).toBe('Cuenta bloqueada temporalmente.');
  });
});
