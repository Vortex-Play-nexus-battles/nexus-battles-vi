import { solicitarRestablecimiento } from './restablecer-solicitar.js';

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

describe('HU-COR-003 - solicitud de restablecimiento de contraseña', () => {
  beforeEach(() => {
    // solicitar-solicitar.js hace document.getElementById al cargar el
    // modulo -- un DOM minimo evita que el import falle por elementos
    // ausentes, aunque estas pruebas no dependan de el (solo prueban la
    // funcion exportada, no el submit del formulario).
    document.body.innerHTML = `
      <form id="formSolicitud">
        <input id="email" name="email" type="email">
        <button id="botonEnviar" type="submit">Enviar código</button>
      </form>
      <p id="estadoSolicitud" hidden></p>
    `;
  });

  test('envía el correo al endpoint correcto', async () => {
    const llamadas = [];

    const fetchFalso = async (url, opciones) => {
      llamadas.push({ url, opciones });
      return respuesta('Si el correo está registrado, recibirás un mensaje con instrucciones.');
    };

    await solicitarRestablecimiento('cristian@test.com', { fetchImpl: fetchFalso });

    expect(llamadas).toHaveLength(1);
    expect(llamadas[0].url).toBe('/api/v1/auth/restablecer/solicitar');
    expect(llamadas[0].opciones).toEqual({
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: 'cristian@test.com' }),
    });
  });

  test('devuelve el mensaje generico del backend cuando la cuenta existe', async () => {
    const fetchFalso = async () =>
      respuesta('Si el correo está registrado, recibirás un mensaje con instrucciones.');

    const mensaje = await solicitarRestablecimiento('cristian@test.com', { fetchImpl: fetchFalso });

    expect(mensaje).toBe('Si el correo está registrado, recibirás un mensaje con instrucciones.');
  });

  test('devuelve el mismo mensaje generico aunque la cuenta no exista', async () => {
    // Deliberado, por seguridad (ver AuthController): el backend responde
    // exactamente igual sin importar si el correo esta registrado, para
    // no permitir enumerar cuentas. Esta prueba fija ese contrato.
    const fetchFalso = async () =>
      respuesta('Si el correo está registrado, recibirás un mensaje con instrucciones.');

    const mensajeExistente = await solicitarRestablecimiento('existe@test.com', {
      fetchImpl: fetchFalso,
    });
    const mensajeInexistente = await solicitarRestablecimiento('noexiste@test.com', {
      fetchImpl: fetchFalso,
    });

    expect(mensajeExistente).toBe(mensajeInexistente);
  });

  test('propaga el mensaje de error cuando el backend rechaza la solicitud', async () => {
    const fetchFalso = async () => respuesta('El correo no tiene un formato válido.', false, 400);

    await expect(
      solicitarRestablecimiento('correo-invalido', { fetchImpl: fetchFalso }),
    ).rejects.toThrow('El correo no tiene un formato válido.');
  });

  test('usa un mensaje por defecto si el backend no da detalle del error', async () => {
    const fetchFalso = async () => respuesta(null, false, 500);

    await expect(
      solicitarRestablecimiento('cristian@test.com', { fetchImpl: fetchFalso }),
    ).rejects.toThrow('No se pudo procesar la solicitud.');
  });
});
