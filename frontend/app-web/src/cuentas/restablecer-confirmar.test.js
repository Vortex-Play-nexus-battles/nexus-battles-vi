import { jest } from '@jest/globals';
import { confirmarRestablecimiento } from './restablecer-confirmar.js';

function respuesta(cuerpo, ok = true, status = 200) {
  return {
    ok,
    status,
    text: async () => {
      if (cuerpo === null) {
        return '';
      }
      return typeof cuerpo === 'string' ? cuerpo : JSON.stringify(cuerpo);
    },
  };
}

function construirFormulario() {
  document.body.innerHTML = `
    <form id="formConfirmacion">
      <input id="codigo" name="codigo" type="text" required minlength="10" maxlength="10">
      <input id="nuevaPassword" name="nuevaPassword" type="password" required>
      <input id="confirmarPassword" name="confirmarPassword" type="password" required>
      <button id="botonEnviar" type="submit">Restablecer contraseña</button>
    </form>
    <p id="estadoConfirmacion" hidden></p>
  `;
}

describe('HU-COR-003 - confirmarRestablecimiento (función pura)', () => {
  beforeEach(() => {
    construirFormulario();
  });

  test('envía el token y la nueva contraseña al endpoint correcto', async () => {
    const llamadas = [];

    const fetchFalso = async (url, opciones) => {
      llamadas.push({ url, opciones });
      return respuesta('Contraseña actualizada correctamente. Ya puedes iniciar sesión.');
    };

    await confirmarRestablecimiento('82AEN4P56Z', 'NuevaClave123!', { fetchImpl: fetchFalso });

    expect(llamadas).toHaveLength(1);
    expect(llamadas[0].url).toBe('/api/v1/auth/restablecer/confirmar');
    expect(llamadas[0].opciones).toEqual({
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token: '82AEN4P56Z', nuevaPassword: 'NuevaClave123!' }),
    });
  });

  test('NO envía una confirmación de contraseña por separado: el backend solo recibe token y nuevaPassword', async () => {
    const fetchFalso = async (url, opciones) => {
      const cuerpo = JSON.parse(opciones.body);
      expect(Object.keys(cuerpo).sort()).toEqual(['nuevaPassword', 'token']);
      return respuesta('Contraseña actualizada correctamente. Ya puedes iniciar sesión.');
    };

    await confirmarRestablecimiento('82AEN4P56Z', 'NuevaClave123!', { fetchImpl: fetchFalso });
  });

  test('propaga el mensaje cuando el código es inválido', async () => {
    const fetchFalso = async () => respuesta('El enlace no es válido.', false, 400);

    await expect(
      confirmarRestablecimiento('CODIGOMALO1', 'NuevaClave123!', { fetchImpl: fetchFalso }),
    ).rejects.toThrow('El enlace no es válido.');
  });

  test('propaga el mensaje cuando el código ya fue usado', async () => {
    const fetchFalso = async () => respuesta('Este enlace ya fue utilizado.', false, 400);

    await expect(
      confirmarRestablecimiento('82AEN4P56Z', 'NuevaClave123!', { fetchImpl: fetchFalso }),
    ).rejects.toThrow('Este enlace ya fue utilizado.');
  });

  test('propaga el mensaje cuando el código expiró', async () => {
    const fetchFalso = async () => respuesta('Este enlace ha expirado.', false, 400);

    await expect(
      confirmarRestablecimiento('82AEN4P56Z', 'NuevaClave123!', { fetchImpl: fetchFalso }),
    ).rejects.toThrow('Este enlace ha expirado.');
  });
});

describe('HU-COR-003 - validación de contraseñas coincidentes (formulario)', () => {
  beforeEach(() => {
    construirFormulario();
  });

  test('no llama al backend si las contraseñas no coinciden', async () => {
    let seLlamoAlBackend = false;
    globalThis.fetch = async () => {
      seLlamoAlBackend = true;
      return respuesta('no deberia llegar aqui');
    };

    // jest.resetModules() es necesario: el primer describe ya importo este
    // archivo de forma estatica, y los modulos ES se cachean por ruta --
    // sin esto, este import devolveria el modulo ya cacheado, cuyas
    // referencias a campoCodigo/campoNuevaPassword/campoConfirmarPassword
    // seguirian apuntando al DOM viejo del primer describe.
    jest.resetModules();
    await import('./restablecer-confirmar.js');

    document.getElementById('codigo').value = '82AEN4P56Z';
    document.getElementById('nuevaPassword').value = 'ClaveA123!';
    document.getElementById('confirmarPassword').value = 'ClaveB456!';

    document
      .getElementById('formConfirmacion')
      .dispatchEvent(new Event('submit', { cancelable: true }));

    // Deja correr la cola de microtareas: el listener es async, pero la
    // validacion de coincidencia ocurre antes del primer await real.
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(seLlamoAlBackend).toBe(false);
    expect(document.getElementById('confirmarPassword').validationMessage).toBe(
      'Las contraseñas no coinciden.',
    );
  });
});
