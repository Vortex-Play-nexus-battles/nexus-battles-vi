// @ts-check
/**
 * Metricas tecnicas (HU-MET-004) y de moderacion (HU-MET-001) por el borde,
 * con metricas-plataforma recolectando de los servicios reales del banco.
 *
 *   1. /tecnicas recolecta cpu, memoria y peticiones de los servicios que SI
 *      corren aqui (torneos, salas, moderacion, comentarios, notificaciones)
 *      y senala como BRECHA a los que no puede observar (correo): CA-03.
 *      Desde B1 correo SI corre en el banco (los codigos de verificacion
 *      viajan por correo), pero a metricas se le da a proposito una direccion
 *      de salud que no responde (compose.yml, SALUD_CORREO): la brecha sigue
 *      siendo real para metricas.
 *   2. /tecnicas/informe/texto exporta el mismo tablero (CA-02)
 *   3. /moderacion agrega lo que moderacion-sanciones publica: se emite una
 *      advertencia y el total del dia sube; sin umbral del PO no hay alertas
 *      (decision D-25) y lo pendiente se dice por su nombre —en la copia
 *      visible, sin citar el identificador de la decision
 *   4. la observabilidad es de administracion (#527): sin token 401, con
 *      token de moderadora 403, con token de administradora 200
 *   5. la vista pinta la tabla con la brecha marcada
 *   6. HU-MET-001 CA-03 con fallos de verdad: si ms-identidad rechaza el rango
 *      o no contesta (contenedor PAUSADO, no reiniciado: conserva su clave de
 *      firma), /moderacion sigue saliendo con las sanciones, las cuentas van
 *      en null con el motivo en «pendientes» —ningun 0 inventado— y al volver
 *      identidad vuelven solas
 */

import { execFileSync } from 'node:child_process';
import path from 'node:path';

import { test, expect, request as apiRequest } from '@playwright/test';

import { sesionDe as sesionDelBanco } from './ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
// Mismo criterio que degradacion.e2e.spec.js: estos specs se transpilan a
// CommonJS, asi que `__dirname` existe; si no, se cae a frontend/app-web.
const AQUI =
  typeof __dirname === 'undefined' ? path.resolve(process.cwd(), '../../tests/e2e') : __dirname;
const COMPOSE = path.join(AQUI, 'compose.yml');

function compose(...args) {
  return execFileSync('docker', ['compose', '-f', COMPOSE, ...args], {
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
  }).trim();
}

/** Pausar un contenedor solo tiene sentido contra el compose local del banco. */
function hayComposeLocal() {
  if (!/localhost|127\.0\.0\.1/.test(BORDE)) {
    return false;
  }
  try {
    compose('ps', '--services');
    return true;
  } catch {
    return false;
  }
}
const MODERADORA = process.env.E2E_MODERADORA ?? 'moderadora_e2e';
const ADMIN = process.env.E2E_ADMIN ?? 'admin_e2e';
const OBJETIVO = process.env.E2E_SANCIONABLE ?? 'medida_e2e';
const CLAVE = 'Contrasena-E2E-2026';
const VISTA = '/frontend/app-web/src/plataforma/metricas-plataforma/tablero-tecnico.html';
const EN_EL_BANCO = [
  'comentarios',
  'torneos',
  'salas-partidas',
  'notificaciones',
  'moderacion-sanciones',
  'admin-parametros',
];
const FUERA_DEL_BANCO = ['correo'];

/**
 * B1 — la cuenta nace pendiente de verificar su correo. Registrar, leer el
 * codigo del buzon, confirmarlo y entrar viven en un solo sitio
 * (`ayudantes/cuentas.js`); aqui solo se fija la contrasena de este spec.
 */
function sesionDe(api, apodo) {
  return sesionDelBanco(api, apodo, { clave: CLAVE, base: BORDE });
}

test.describe('Metricas tecnicas y de moderacion (HU-MET-004 / HU-MET-001)', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let moderadora;
  let objetivo;
  let admin;

  /** HU-MET-001 (#527): la observabilidad solo responde a administracion. */
  function comoAdmin() {
    return { headers: { Authorization: `Bearer ${admin.token}` } };
  }

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    moderadora = await sesionDe(api, MODERADORA);
    objetivo = await sesionDe(api, OBJETIVO);
    admin = await sesionDe(api, ADMIN);
    expect(moderadora.claims.rol).toBe('MODERADOR');
    expect(admin.claims.rol).toBe('ADMINISTRADOR');
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  test('/tecnicas recolecta de los servicios del banco y senala la brecha de los que no estan (CA-03)', async () => {
    const r = await api.get('/api/v1/tecnicas', comoAdmin());
    expect(r.status(), await r.text()).toBe(200);
    const tablero = await r.json();
    expect(tablero.umbrales).toEqual({
      cpu: 0.75,
      latenciaMs: 500,
      disponibilidadPorcentaje: 99.95,
    });
    const porServicio = Object.fromEntries(tablero.servicios.map((s) => [s.servicio, s]));
    for (const nombre of EN_EL_BANCO) {
      const s = porServicio[nombre];
      expect(s, `${nombre} en el tablero`).toBeTruthy();
      expect(s.brecha, `${nombre} recolectado`).toBeNull();
      expect(typeof s.cpu).toBe('number');
      expect(typeof s.memoriaMb).toBe('number');
      expect(s.peticiones).toBeGreaterThanOrEqual(0);
    }
    for (const nombre of FUERA_DEL_BANCO) {
      expect(porServicio[nombre].brecha, `${nombre} es brecha`).toBeTruthy();
      expect(tablero.brechas.some((b) => b.startsWith(`${nombre}:`))).toBe(true);
    }
    // Un servicio caido tambien sale como alerta de disponibilidad, con su nombre.
    expect(
      tablero.alertas.filter((a) => a.metrica === 'disponibilidad').map((a) => a.servicio),
    ).toEqual(expect.arrayContaining(FUERA_DEL_BANCO));
  });

  test('/tecnicas/informe/texto exporta el tablero redactado (CA-02)', async () => {
    const r = await api.get('/api/v1/tecnicas/informe/texto', comoAdmin());
    expect(r.status()).toBe(200);
    const texto = await r.text();
    expect(texto).toMatch(/Metricas tecnicas de la plataforma/);
    expect(texto).toMatch(/correo: BRECHA DE OBSERVABILIDAD/);
    expect(texto).toMatch(/- torneos: cpu \d+ %/);
  });

  test('/moderacion agrega las sanciones reales; una advertencia nueva sube el total del dia; sin umbral no hay alertas', async () => {
    const antes = await api.get('/api/v1/moderacion', comoAdmin());
    expect(antes.status(), await antes.text()).toBe(200);
    const previo = await antes.json();
    expect(previo.alertasConfiguradas).toBe(false);
    expect(previo.alertas).toEqual([]);
    // HU-MET-001 1.10.0: el banco tiene ms-identidad y el tablero le reenvia el
    // token del administrador, asi que las cuentas SI llegan y «nuevos
    // usuarios» deja de ser pendiente. Lo unico que sigue pendiente es la
    // frecuencia de reportes (HU-COM-006 sin lectura agregada).
    expect(previo.pendientes).toEqual(['frecuencia de reportes: HU-COM-006 #523 sin implementar']);
    expect(previo.registroDeUsuarios, 'identidad no dio las cuentas').not.toBeNull();
    expect(previo.registroDeUsuarios.total).toBeGreaterThanOrEqual(1);
    expect(typeof previo.registroDeUsuarios.porEstado.ACTIVO).toBe('number');
    expect(Array.isArray(previo.registroDeUsuarios.registros.porDia)).toBe(true);

    const emitida = await api.post('/api/v1/sanciones', {
      headers: { Authorization: `Bearer ${moderadora.token}`, 'Content-Type': 'application/json' },
      data: {
        usuarioId: objetivo.claims.uid,
        tipo: 'ADVERTENCIA',
        motivo: 'Para la metrica (E2E)',
      },
    });
    expect(emitida.status(), await emitida.text()).toBe(201);

    const despues = await api.get('/api/v1/moderacion', comoAdmin());
    const actual = await despues.json();
    expect(actual.sanciones.total).toBe(previo.sanciones.total + 1);
    expect(actual.sanciones.porTipo.ADVERTENCIA).toBe(previo.sanciones.porTipo.ADVERTENCIA + 1);
    expect(actual.sanciones.moderadoresActivos).toBeGreaterThanOrEqual(1);
    const hoy = new Date().toISOString().slice(0, 10);
    expect(actual.sanciones.porDia.find((d) => d.fecha === hoy)?.emitidas).toBeGreaterThanOrEqual(
      1,
    );

    const invertido = await api.get(
      '/api/v1/moderacion?desde=2026-10-02T00:00:00Z&hasta=2026-10-01T00:00:00Z',
      comoAdmin(),
    );
    expect(invertido.status()).toBe(400);
  });

  test('la observabilidad del bloque no es publica: sin token 401, moderadora 403, administradora 200 (#527)', async () => {
    // Hasta el 22-sep-2026 estas cinco rutas respondian a cualquiera que
    // llegara por el borde: consumo, errores 5xx y caidas de los siete
    // servicios, mas los agregados de moderacion.
    const rutas = [
      '/api/v1/tecnicas',
      '/api/v1/tecnicas/informe/texto',
      '/api/v1/moderacion',
      '/api/v1/disponibilidad',
      '/api/v1/degradacion',
    ];
    for (const ruta of rutas) {
      const anonimo = await api.get(ruta);
      expect(anonimo.status(), `${ruta} sin token`).toBe(401);

      const comoModeradora = await api.get(ruta, {
        headers: { Authorization: `Bearer ${moderadora.token}` },
      });
      expect(comoModeradora.status(), `${ruta} con rol MODERADOR`).toBe(403);

      const comoAdministradora = await api.get(ruta, comoAdmin());
      expect(comoAdministradora.status(), `${ruta} con rol ADMINISTRADOR`).toBe(200);
    }
  });

  test('la vista pinta la tabla tecnica con la brecha marcada y el resumen de moderacion (como administradora)', async ({
    page,
  }) => {
    await page.addInitScript(
      ([token, nombre, uid]) => {
        sessionStorage.setItem('nexus.token', token);
        sessionStorage.setItem('nexus.apodoActual', nombre);
        sessionStorage.setItem('nexus.usuarioId', uid);
      },
      [admin.token, ADMIN, admin.claims.uid],
    );
    await page.goto(`${BORDE}${VISTA}`);
    const tabla = page.locator('[data-zona="tabla-tecnica"]');
    await expect(tabla).toBeVisible({ timeout: 30000 });
    await expect(tabla.locator('tr[data-servicio="torneos"]')).not.toHaveAttribute(
      'data-brecha',
      'true',
    );
    await expect(tabla.locator('tr[data-servicio="correo"]')).toHaveAttribute(
      'data-brecha',
      'true',
    );
    await expect(page.locator('[data-zona="brechas"]')).toContainText('correo');
    await expect(page.locator('[data-zona="resumen-moderacion"]')).toContainText(/sanciones/, {
      timeout: 20000,
    });
    // Se afirma el SIGNIFICADO —que no hay umbral y por eso no se evalua
    // ninguna alerta—, no el identificador interno de la decision. La prueba
    // exigia el literal «D-25» y el bloque UX lo quito de la copia visible,
    // con razon: «D-25» no le dice nada a una administradora. Desde entonces
    // el E2E de develop estaba rojo, y la unica afirmacion que fallaba era
    // esta. Un codigo de decision interno no es contrato de interfaz.
    await expect(page.locator('[data-zona="moderacion"] [data-zona="alertas"]')).toContainText(
      /sin umbral/i,
    );
    await expect(page.locator('[data-zona="moderacion"] [data-zona="alertas"]')).toContainText(
      /no se evalua|no se evalúa/i,
    );
  });

  test('CA-03 con fallos de verdad: identidad rechaza el rango o no contesta y /moderacion sale igual, sin cuentas inventadas', async () => {
    test.skip(!hayComposeLocal(), 'pausar ms-identidad necesita el compose local de tests/e2e');
    test.setTimeout(120_000);
    const DIA = 24 * 60 * 60 * 1000;

    // Identidad sana: las cuentas llegan (y metricas tiene fresca la clave publica del emisor).
    const sana = await api.get('/api/v1/moderacion', comoAdmin());
    expect(sana.status(), await sana.text()).toBe(200);
    expect((await sana.json()).registroDeUsuarios, 'con identidad sana').not.toBeNull();

    // 1) Identidad rechaza el rango: su tope tecnico es de 366 dias y aqui se piden 400.
    const desde = new Date(Date.now() - 400 * DIA).toISOString();
    const largo = await api.get(
      `/api/v1/moderacion?desde=${encodeURIComponent(desde)}&hasta=${encodeURIComponent(new Date().toISOString())}`,
      comoAdmin(),
    );
    expect(largo.status(), await largo.text()).toBe(200);
    const conRangoRechazado = await largo.json();
    expect(conRangoRechazado.sanciones.total).toBeGreaterThanOrEqual(1);
    expect(conRangoRechazado.registroDeUsuarios).toBeNull();
    expect(conRangoRechazado.pendientes[0]).toMatch(
      /^registro de nuevos usuarios: ms-identidad no acepto el rango/,
    );

    // 2) Identidad acepta la conexion y no contesta: su contenedor se PAUSA.
    compose('pause', 'srv-ms-identidad');
    try {
      const inicio = Date.now();
      const sinIdentidad = await api.get('/api/v1/moderacion', comoAdmin());
      const espera = Date.now() - inicio;
      expect(sinIdentidad.status(), await sinIdentidad.text()).toBe(200);
      const tablero = await sinIdentidad.json();
      expect(tablero.sanciones.total, 'las sanciones siguen').toBeGreaterThanOrEqual(1);
      expect(tablero.registroDeUsuarios, 'sin cuentas, nunca ceros').toBeNull();
      expect(tablero.pendientes[0]).toMatch(
        /^registro de nuevos usuarios: ms-identidad no responde/,
      );
      expect(espera, 'el plazo de 3 s del cliente corta la espera').toBeLessThan(15_000);
    } finally {
      compose('unpause', 'srv-ms-identidad');
    }

    // 3) Al volver identidad, las cuentas vuelven sin reiniciar nada.
    await expect
      .poll(
        async () =>
          (await (await api.get('/api/v1/moderacion', comoAdmin())).json()).registroDeUsuarios,
        { timeout: 30_000, intervals: [1_000], message: 'las cuentas no volvieron con identidad' },
      )
      .not.toBeNull();
  });
});
