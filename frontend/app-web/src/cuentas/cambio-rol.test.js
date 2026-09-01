import { cambiarRol, ROLES_DISPONIBLES } from './cambio-rol.js';

function respuesta(cuerpo, ok = true, status = 200) {
  return {
    ok,
    status,
    text: async () => (cuerpo === null ? '' : JSON.stringify(cuerpo)),
  };
}

describe('HU-RBAC-003 - cambio de rol desde frontend', () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  test('envía el cambio de rol usando el JWT de la sesión', async () => {
    sessionStorage.setItem('nexus.token', 'jwt-super-admin');

    const llamadas = [];

    const fetchFalso = async (url, opciones) => {
      llamadas.push({ url, opciones });

      return respuesta({
        mensaje: 'Rol actualizado correctamente',
      });
    };

    await cambiarRol(
      25,
      'MODERADOR',
      {
        fetchImpl: fetchFalso,
      },
    );

    expect(llamadas).toHaveLength(1);

    expect(llamadas[0].url).toBe(
      '/api/v1/rbac/usuarios/25/rol',
    );

    expect(llamadas[0].opciones).toEqual({
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer jwt-super-admin',
      },
      body: JSON.stringify({
        nuevoRol: 'MODERADOR',
      }),
    });
  });

  test('rechaza el cambio si no existe JWT', async () => {
    let llamado = false;

    const fetchFalso = async () => {
      llamado = true;
      return respuesta({});
    };

    await expect(
      cambiarRol(
        25,
        'MODERADOR',
        {
          fetchImpl: fetchFalso,
        },
      ),
    ).rejects.toThrow(/sesión autenticada/i);

    expect(llamado).toBe(false);
  });

  test('rechaza un rol inexistente antes de llamar al backend', async () => {
    sessionStorage.setItem('nexus.token', 'jwt-super-admin');

    let llamado = false;

    const fetchFalso = async () => {
      llamado = true;
      return respuesta({});
    };

    await expect(
      cambiarRol(
        25,
        'DIOS_SUPREMO',
        {
          fetchImpl: fetchFalso,
        },
      ),
    ).rejects.toThrow(/rol seleccionado no es válido/i);

    expect(llamado).toBe(false);
  });

  test('propaga el rechazo del backend', async () => {
    sessionStorage.setItem('nexus.token', 'jwt-admin');

    const fetchFalso = async () =>
      respuesta(
        {
          detail: 'No tienes permiso para esta acción',
        },
        false,
        403,
      );

    await expect(
      cambiarRol(
        25,
        'ADMINISTRADOR',
        {
          fetchImpl: fetchFalso,
        },
      ),
    ).rejects.toThrow(/no tienes permiso/i);
  });

  test('expone únicamente los roles válidos del sistema', () => {
    expect(ROLES_DISPONIBLES).toEqual([
      'JUGADOR',
      'MODERADOR',
      'ADMINISTRADOR',
      'SUPER_ADMINISTRADOR',
    ]);
  });
});
