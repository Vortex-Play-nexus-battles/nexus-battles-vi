import { jest } from '@jest/globals';
import { setCurrentRole, setPermissionMatrix } from './directives/has-permission.directive.js';
import { cargarMatrizYVerificarAcceso } from './gestion-usuarios.js';

const MATRIZ_BACKEND = {
  ADMINISTRADOR: {
    GESTIONAR_CUENTAS: 'GRANTED',
    ASIGNAR_ROL: 'DENIED',
  },
  SUPER_ADMINISTRADOR: {
    GESTIONAR_CUENTAS: 'GRANTED',
    ASIGNAR_ROL: 'GRANTED',
  },
};

function prepararDom() {
  document.body.innerHTML = `
    <section id="acceso-denegado" hidden>
      <h2 data-zona="titulo-bloqueo"></h2>
      <p data-zona="detalle-bloqueo"></p>
      <button data-zona="reintentar-bloqueo" hidden></button>
    </section>
    <section id="gestion-contenedor" hidden>
      <button id="btn-cambiar-rol" data-has-permission="ASIGNAR_ROL"></button>
    </section>
  `;
}

const bloqueo = () => ({
  titulo: document.querySelector('[data-zona="titulo-bloqueo"]').textContent,
  detalle: document.querySelector('[data-zona="detalle-bloqueo"]').textContent,
  reintentar: document.querySelector('[data-zona="reintentar-bloqueo"]'),
});

function respuestaMatriz(matrix = MATRIZ_BACKEND) {
  return {
    ok: true,
    status: 200,
    json: async () => ({ version: '1.1.0', matrix }),
  };
}

describe('Gestión de usuarios - carga de matriz RBAC', () => {
  beforeEach(() => {
    sessionStorage.clear();
    setPermissionMatrix({});
    prepararDom();
  });

  test('ADMINISTRADOR entra a la pantalla pero no ve Cambiar rol', async () => {
    setCurrentRole('ADMINISTRADOR');
    sessionStorage.setItem('nexus.token', 'jwt-admin');
    const fetchImpl = jest.fn().mockResolvedValue(respuestaMatriz());

    await cargarMatrizYVerificarAcceso({ fetchImpl });

    expect(fetchImpl).toHaveBeenCalledWith('/api/v1/rbac/matrix', {
      headers: { Authorization: 'Bearer jwt-admin' },
    });
    expect(document.getElementById('gestion-contenedor').hidden).toBe(false);
    expect(document.getElementById('acceso-denegado').hidden).toBe(true);
    expect(document.getElementById('btn-cambiar-rol').style.display).toBe('none');
  });

  test('SUPER_ADMINISTRADOR entra y ve Cambiar rol', async () => {
    setCurrentRole('SUPER_ADMINISTRADOR');

    await cargarMatrizYVerificarAcceso({
      fetchImpl: jest.fn().mockResolvedValue(respuestaMatriz()),
    });

    expect(document.getElementById('gestion-contenedor').hidden).toBe(false);
    expect(document.getElementById('acceso-denegado').hidden).toBe(true);
    expect(document.getElementById('btn-cambiar-rol').style.display).toBe('');
  });

  test('mantiene acceso denegado si no puede cargar la matriz', async () => {
    setCurrentRole('SUPER_ADMINISTRADOR');
    const errorSpy = jest.spyOn(console, 'error').mockImplementation(() => {});

    await cargarMatrizYVerificarAcceso({
      fetchImpl: jest.fn().mockRejectedValue(new Error('backend caído')),
    });

    expect(document.getElementById('gestion-contenedor').hidden).toBe(true);
    expect(document.getElementById('acceso-denegado').hidden).toBe(false);
    expect(document.getElementById('btn-cambiar-rol').style.display).toBe('none');
    errorSpy.mockRestore();
  });
});

/**
 * UX-R3.3 — «no puedes» y «no pude comprobarlo» no son el mismo mensaje.
 *
 * La UI falla cerrada contra la matriz del servidor, y eso no cambia. Lo que
 * cambia es lo que lee la persona: cuando `ms-identidad` no contestaba —en
 * dev no corre— un administrador legítimo leía «No tienes permisos
 * suficientes». Es falso, y es la clase de mensaje que hace perder una tarde:
 * quien lo lee va a pedir que le revisen el rol, no a mirar si el servicio
 * está caído.
 */
describe('§17 — por qué está bloqueado', () => {
  beforeEach(() => {
    prepararDom();
    setPermissionMatrix({});
  });

  test('con la matriz cargada y sin permiso: es tu rol, y no se ofrece reintentar', async () => {
    setCurrentRole('JUGADOR');

    await cargarMatrizYVerificarAcceso({
      fetchImpl: jest.fn().mockResolvedValue(respuestaMatriz()),
    });

    expect(document.getElementById('acceso-denegado').hidden).toBe(false);
    const visto = bloqueo();
    expect(visto.titulo).toBe('No tienes acceso a esta sección.');
    expect(visto.detalle).toContain('no tiene habilitada');
    // Reintentar no arreglaría nada: el rol seguiría siendo el mismo.
    expect(visto.reintentar.hidden).toBe(true);
  });

  test('sin matriz: es el servicio, se dice, y se ofrece reintentar', async () => {
    setCurrentRole('SUPER_ADMINISTRADOR');
    const errorSpy = jest.spyOn(console, 'error').mockImplementation(() => {});

    await cargarMatrizYVerificarAcceso({
      fetchImpl: jest.fn().mockRejectedValue(new Error('backend caído')),
    });

    expect(document.getElementById('gestion-contenedor').hidden).toBe(true);
    const visto = bloqueo();
    expect(visto.titulo).toBe('Esta función no está disponible temporalmente.');
    expect(visto.detalle).toContain('No es un problema de tu cuenta');
    expect(visto.reintentar.hidden).toBe(false);
    // Ni un código crudo a la vista (§17).
    expect(`${visto.titulo} ${visto.detalle}`).not.toMatch(/\b(401|403|404|500|502)\b/);
    errorSpy.mockRestore();
  });

  test('una matriz vacía del servidor cuenta como «no pude comprobarlo»', async () => {
    // Un 200 con `{ matrix: {} }` no es una respuesta válida: sin matriz no se
    // puede decidir nada, y decirle a un super administrador que no tiene
    // permiso sería inventarse una razón.
    setCurrentRole('SUPER_ADMINISTRADOR');
    const errorSpy = jest.spyOn(console, 'error').mockImplementation(() => {});

    await cargarMatrizYVerificarAcceso({
      fetchImpl: jest.fn().mockResolvedValue(respuestaMatriz({})),
    });

    expect(bloqueo().titulo).toBe('Esta función no está disponible temporalmente.');
    errorSpy.mockRestore();
  });
});
