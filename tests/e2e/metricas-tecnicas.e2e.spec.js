// @ts-check
/**
 * Metricas tecnicas (HU-MET-004) y de moderacion (HU-MET-001) por el borde,
 * con metricas-plataforma recolectando de los servicios reales del banco.
 *
 *   1. /tecnicas recolecta cpu, memoria y peticiones de los servicios que SI
 *      corren aqui (torneos, salas, moderacion, comentarios, notificaciones)
 *      y senala como BRECHA a los que no (correo, admin-parametros): CA-03
 *   2. /tecnicas/informe/texto exporta el mismo tablero (CA-02)
 *   3. /moderacion agrega lo que moderacion-sanciones publica: se emite una
 *      advertencia y el total del dia sube; sin umbral del PO no hay alertas
 *      (D-25) y lo pendiente se dice por su nombre
 *   4. la vista pinta la tabla con la brecha marcada
 */

import { test, expect, request as apiRequest } from '@playwright/test';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const MODERADORA = process.env.E2E_MODERADORA ?? 'moderadora_e2e';
const OBJETIVO = process.env.E2E_SANCIONABLE ?? 'medida_e2e';
const CLAVE = 'Contrasena-E2E-2026';
const VISTA = '/frontend/app-web/src/plataforma/metricas-plataforma/tablero-tecnico.html';
const EN_EL_BANCO = ['comentarios', 'torneos', 'salas-partidas', 'notificaciones', 'moderacion-sanciones'];
const FUERA_DEL_BANCO = ['correo', 'admin-parametros'];

function cuerpoDelToken(jwt) {
  const base64 = jwt.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
  return JSON.parse(Buffer.from(base64, 'base64').toString('utf8'));
}

async function sesionDe(api, apodo) {
  const email = `${apodo}@nexus.test`;
  const registro = await api.post('/api/v1/auth/registro', {
    multipart: { nombres: 'Jugadora', apellidos: 'De Prueba', email, password: CLAVE, apodo },
  });
  expect([200, 201, 400, 409]).toContain(registro.status());
  const login = await api.post('/api/v1/auth/login', { data: { email, password: CLAVE } });
  expect(login.status(), `login de ${apodo}: ${await login.text()}`).toBe(200);
  const cuerpo = await login.json();
  return { ...cuerpo, apodo, claims: cuerpoDelToken(cuerpo.token) };
}

test.describe('Metricas tecnicas y de moderacion (HU-MET-004 / HU-MET-001)', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let moderadora;
  let objetivo;

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    moderadora = await sesionDe(api, MODERADORA);
    objetivo = await sesionDe(api, OBJETIVO);
    expect(moderadora.claims.rol).toBe('MODERADOR');
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  test('/tecnicas recolecta de los servicios del banco y senala la brecha de los que no estan (CA-03)', async () => {
    const r = await api.get('/api/v1/tecnicas');
    expect(r.status(), await r.text()).toBe(200);
    const tablero = await r.json();
    expect(tablero.umbrales).toEqual({ cpu: 0.75, latenciaMs: 500, disponibilidadPorcentaje: 99.95 });
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
    expect(tablero.alertas.filter((a) => a.metrica === 'disponibilidad').map((a) => a.servicio)).toEqual(
      expect.arrayContaining(FUERA_DEL_BANCO),
    );
  });

  test('/tecnicas/informe/texto exporta el tablero redactado (CA-02)', async () => {
    const r = await api.get('/api/v1/tecnicas/informe/texto');
    expect(r.status()).toBe(200);
    const texto = await r.text();
    expect(texto).toMatch(/Metricas tecnicas de la plataforma/);
    expect(texto).toMatch(/correo: BRECHA DE OBSERVABILIDAD/);
    expect(texto).toMatch(/- torneos: cpu \d+ %/);
  });

  test('/moderacion agrega las sanciones reales; una advertencia nueva sube el total del dia; sin umbral no hay alertas', async () => {
    const antes = await api.get('/api/v1/moderacion');
    expect(antes.status(), await antes.text()).toBe(200);
    const previo = await antes.json();
    expect(previo.alertasConfiguradas).toBe(false);
    expect(previo.alertas).toEqual([]);
    expect(previo.pendientes).toHaveLength(2);

    const emitida = await api.post('/api/v1/sanciones', {
      headers: { Authorization: `Bearer ${moderadora.token}`, 'Content-Type': 'application/json' },
      data: { usuarioId: objetivo.claims.uid, tipo: 'ADVERTENCIA', motivo: 'Para la metrica (E2E)' },
    });
    expect(emitida.status(), await emitida.text()).toBe(201);

    const despues = await api.get('/api/v1/moderacion');
    const actual = await despues.json();
    expect(actual.sanciones.total).toBe(previo.sanciones.total + 1);
    expect(actual.sanciones.porTipo.ADVERTENCIA).toBe(previo.sanciones.porTipo.ADVERTENCIA + 1);
    expect(actual.sanciones.moderadoresActivos).toBeGreaterThanOrEqual(1);
    const hoy = new Date().toISOString().slice(0, 10);
    expect(actual.sanciones.porDia.find((d) => d.fecha === hoy)?.emitidas).toBeGreaterThanOrEqual(1);

    const invertido = await api.get('/api/v1/moderacion?desde=2026-10-02T00:00:00Z&hasta=2026-10-01T00:00:00Z');
    expect(invertido.status()).toBe(400);
  });

  test('la vista pinta la tabla tecnica con la brecha marcada y el resumen de moderacion', async ({ page }) => {
    await page.addInitScript(
      ([token, nombre, uid]) => {
        sessionStorage.setItem('nexus.token', token);
        sessionStorage.setItem('nexus.apodoActual', nombre);
        sessionStorage.setItem('nexus.usuarioId', uid);
      },
      [moderadora.token, MODERADORA, moderadora.claims.uid],
    );
    await page.goto(`${BORDE}${VISTA}`);
    const tabla = page.locator('[data-zona="tabla-tecnica"]');
    await expect(tabla).toBeVisible({ timeout: 30000 });
    await expect(tabla.locator('tr[data-servicio="torneos"]')).not.toHaveAttribute('data-brecha', 'true');
    await expect(tabla.locator('tr[data-servicio="correo"]')).toHaveAttribute('data-brecha', 'true');
    await expect(page.locator('[data-zona="brechas"]')).toContainText('correo');
    await expect(page.locator('[data-zona="resumen-moderacion"]')).toContainText(/sanciones/, { timeout: 20000 });
    await expect(page.locator('[data-zona="moderacion"] [data-zona="alertas"]')).toContainText('D-25');
  });
});
