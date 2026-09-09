import { jest } from '@jest/globals';
import {
  setCurrentRole,
  setPermissionMatrix,
} from './directives/has-permission.directive.js';
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
    <section id="acceso-denegado" hidden></section>
    <section id="gestion-contenedor" hidden>
      <button id="btn-cambiar-rol" data-has-permission="ASIGNAR_ROL"></button>
    </section>
  `;
}

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
