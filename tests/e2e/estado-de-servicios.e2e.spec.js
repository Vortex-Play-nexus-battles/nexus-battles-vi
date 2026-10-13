// @ts-check
/**
 * Estado de los servicios de la consola (RFINAL-08), por el borde y contra los
 * servicios reales del banco.
 *
 *   1. cada servicio del catalogo tiene sonda: ninguno sale NO_OBSERVABLE, los
 *      que no estan en el banco (ms-chatbot, ms-cumplimiento) salen
 *      NO_DESPLEGADO y los que si estan contestan su salud de Actuator. Es la
 *      prueba de punta a punta de la sonda estricta: solo cuenta UP si recibe
 *      el JSON de Actuator con status UP en la raiz.
 *   2. la segunda consulta dentro de la vigencia reutiliza la ronda y lo dice
 *      (desdeCache, con la hora de esa ronda).
 *
 * LENTO se admite para los del banco: una maquina de CI cargada puede tardar
 * mas que el plazo de la sonda, y eso es exactamente lo que LENTO dice. Lo que
 * no se admite es CAIDO ni NO_OBSERVABLE para un servicio que esta corriendo.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import { sesionDe as sesionDelBanco } from './ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const ADMIN = process.env.E2E_ADMIN ?? 'admin_e2e';
const CLAVE = 'Contrasena-E2E-2026';
const RUTA = '/api/v1/admin/sistema/servicios';

/** Los del catalogo que corren en el banco (tests/e2e/compose.yml). */
const EN_EL_BANCO = [
  'comentarios',
  'correo',
  'torneos',
  'salas-partidas',
  'notificaciones',
  'moderacion-sanciones',
  'metricas-plataforma',
  'admin-parametros',
  'ms-identidad',
  'ms-ecommerce',
  'ms-finanzas',
  'ms-subastas',
  'heroes',
  'inventario',
  'productos',
  'motor-combate',
  'misiones',
];
const FUERA_DEL_BANCO = ['ms-chatbot', 'ms-cumplimiento'];

test.describe('Estado de los servicios de la consola (RFINAL-08)', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let admin;

  function comoAdmin() {
    return { headers: { Authorization: `Bearer ${admin.token}` } };
  }

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    admin = await sesionDelBanco(api, ADMIN, { clave: CLAVE, base: BORDE });
    expect(admin.claims.rol).toBe('ADMINISTRADOR');
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  test('cada servicio tiene sonda: los del banco contestan su salud, los demas son NO_DESPLEGADO', async () => {
    const r = await api.get(RUTA, comoAdmin());
    expect(r.status(), await r.text()).toBe(200);
    const estado = await r.json();

    expect(estado.total).toBe(EN_EL_BANCO.length + FUERA_DEL_BANCO.length);
    expect(estado.noObservables).toBe(0);
    expect(typeof estado.lentos).toBe('number');
    expect(typeof estado.desdeCache).toBe('boolean');
    const porServicio = Object.fromEntries(estado.servicios.map((s) => [s.servicio, s]));
    for (const nombre of FUERA_DEL_BANCO) {
      expect(porServicio[nombre]?.estado, nombre).toBe('NO_DESPLEGADO');
    }
    for (const nombre of EN_EL_BANCO) {
      const servicio = porServicio[nombre];
      expect(
        ['OPERATIVO', 'LENTO'],
        `${nombre}: ${servicio?.estado} (${servicio?.detalle})`,
      ).toContain(servicio?.estado);
    }
    expect(estado.operativos).toBeGreaterThan(0);
  });

  test('dentro de la vigencia la segunda consulta reutiliza la ronda y lo dice', async () => {
    const primera = await (await api.get(RUTA, comoAdmin())).json();
    const segunda = await (await api.get(RUTA, comoAdmin())).json();

    expect(segunda.desdeCache).toBe(true);
    expect(segunda.instante).toBe(primera.instante);
  });

  test('sin token no hay estado del sistema (401)', async () => {
    expect((await api.get(RUTA)).status()).toBe(401);
  });
});
