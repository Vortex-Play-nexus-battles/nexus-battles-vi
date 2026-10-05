// @ts-check
/**
 * Panel de usuarios (HU-USR-008 #561, §7.3.4), por el borde y contra el
 * ms-identidad real del banco (ms-identidad-admin.yaml 1.3.0).
 *
 *   1. el administrador del banco ve cuántas cuentas hay en cada estado y la
 *      serie de registros por día, continua y con el rango por omisión;
 *   2. filtra el directorio por estado y por rol, y todas las filas cumplen;
 *   3. encuentra una cuenta por el nombre y los apellidos de su perfil;
 *   4. exporta el listado filtrado en CSV, con las columnas del directorio;
 *   5. un filtro que no se puede aplicar es 400 `datos-invalidos`, y un
 *      jugador no ve nada de esto (403).
 *
 * Las cuentas del banco son todas `@nexus.test`, o sea «de pruebas
 * automáticas»: por eso aquí se consulta SIN `ocultarPruebas` (con él, el
 * banco entero queda fuera y los conteos son cero, que es lo correcto).
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import { sesionDe as sesionDelBanco } from './ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const ADMIN = process.env.E2E_ADMIN ?? 'admin_e2e';
const JUGADOR = process.env.E2E_ANFITRION ?? 'anfitriona_e2e';
const CLAVE = 'Contrasena-E2E-2026';
const MARCA = `${Date.now().toString(36)}${Math.floor(Math.random() * 1e3)}`;
const ERRORES = 'https://nexusbattles.upb.edu.co/errors/';
const ESTADOS = ['ACTIVO', 'PENDIENTE_VERIFICACION', 'INACTIVO', 'SUSPENDIDO', 'BANEADO'];

test.describe('Panel de usuarios de la consola (HU-USR-008)', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let admin;
  let jugador;
  let conNombre;

  const como = (sesion) => ({ headers: { Authorization: `Bearer ${sesion.token}` } });

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    admin = await sesionDelBanco(api, ADMIN, { clave: CLAVE, base: BORDE });
    expect(admin.claims.rol).toBe('ADMINISTRADOR');
    jugador = await sesionDelBanco(api, JUGADOR, { clave: CLAVE, base: BORDE });
    conNombre = await sesionDelBanco(api, `u8_${MARCA}`, {
      clave: CLAVE,
      base: BORDE,
      nombres: `Zoe${MARCA}`,
      apellidos: 'Quintero Rangel',
    });
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  test('1 · conteos por estado y registros por día del último mes', async () => {
    const r = await api.get('/api/v1/admin/jugadores/indicadores', como(admin));
    expect(r.status(), await r.text()).toBe(200);
    const indicadores = await r.json();

    expect(Object.keys(indicadores.porEstado)).toEqual(expect.arrayContaining(ESTADOS));
    const suma = Object.values(indicadores.porEstado).reduce((a, b) => a + Number(b), 0);
    expect(indicadores.total).toBe(suma);
    expect(indicadores.porEstado.ACTIVO).toBeGreaterThanOrEqual(3);
    expect(indicadores.registros.porDia).toHaveLength(30);
    expect(indicadores.registros.porDia.at(-1).fecha).toBe(indicadores.registros.hasta);
    // El banco se siembra en cada corrida: sus cuentas son de hoy.
    expect(indicadores.registros.total).toBeGreaterThanOrEqual(1);
  });

  test('2 · el directorio filtra por estado y por rol en el servidor', async () => {
    const activas = await api.get('/api/v1/admin/jugadores?estado=ACTIVO&size=100', como(admin));
    expect(activas.status(), await activas.text()).toBe(200);
    const pagina = await activas.json();
    expect(pagina.contenido.length).toBeGreaterThan(0);
    expect(pagina.contenido.every((c) => c.estado === 'ACTIVO')).toBe(true);

    const administradores = await (
      await api.get('/api/v1/admin/jugadores?rol=ADMINISTRADOR&size=100', como(admin))
    ).json();
    expect(administradores.contenido.map((c) => c.apodo)).toContain(ADMIN);
    expect(administradores.contenido.every((c) => c.rol === 'ADMINISTRADOR')).toBe(true);
  });

  test('3 · la búsqueda encuentra por nombre y apellidos del perfil', async () => {
    const r = await api.get(
      `/api/v1/admin/jugadores?buscar=${encodeURIComponent(`zoe${MARCA} quintero`)}`,
      como(admin),
    );
    expect(r.status(), await r.text()).toBe(200);
    const pagina = await r.json();
    expect(pagina.contenido.map((c) => c.apodo)).toEqual([conNombre.apodo]);
  });

  test('4 · exporta el listado filtrado en CSV, con las columnas del directorio', async () => {
    const r = await api.get('/api/v1/admin/jugadores/exportacion?estado=ACTIVO', como(admin));
    expect(r.status(), await r.text()).toBe(200);
    expect(r.headers()['content-type']).toContain('text/csv');
    expect(r.headers()['content-disposition']).toMatch(
      /attachment; filename="directorio-de-cuentas-/,
    );
    const csv = (await r.body()).toString('utf8');
    expect(csv.charCodeAt(0)).toBe(0xfeff);
    const lineas = csv.slice(1).split('\r\n');
    expect(lineas[0]).toBe('Apodo,Correo,Rol,Estado,Registro,Última entrada');
    expect(csv).toContain(`"${ADMIN}"`);
    expect(csv).not.toMatch(/\$2[aby]\$/);
  });

  test('5 · un filtro imposible es 400 con motivo; un jugador no ve nada', async () => {
    const malo = await api.get('/api/v1/admin/jugadores/indicadores?desde=ayer', como(admin));
    expect(malo.status()).toBe(400);
    expect((await malo.json()).type).toBe(`${ERRORES}datos-invalidos`);

    for (const ruta of ['/indicadores', '/exportacion', '']) {
      const r = await api.get(`/api/v1/admin/jugadores${ruta}`, como(jugador));
      expect(r.status(), ruta).toBe(403);
    }
  });
});
