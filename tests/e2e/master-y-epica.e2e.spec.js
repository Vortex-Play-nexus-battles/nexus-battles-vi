// @ts-check
/**
 * Máster y épica de punta a punta — HU-SIM-005 (aparición del Máster) y
 * HU-SIM-006 (su épica), por el borde con misiones, inventario, héroes, motor
 * de combate, productos, ms-finanzas, notificaciones y correo reales.
 *
 * ## Por qué hace falta una misión propia del banco
 *
 * En DEV no se puede ver a mano: el Máster del Templo aparece con un 15 % y la
 * misión dura doce horas reales, y el de la Tabla 20 aparece con un 0,01 a
 * 0,1 %. Ningún E2E ejercía Máster → épica → inventario.
 *
 * `tests/e2e/compose.yml` monta en `srv-misiones` el archivo
 * `tests/e2e/semilla-misiones-banco.json` (`MISIONES_SEMILLA_EXTRA`), que
 * publica «[PROVISIONAL DE DEV] Máster seguro»: una hora de misión (2 s en el
 * banco), un jefe de 1 de vida y un Máster que aparece con probabilidad 1,0 con
 * la épica de la Tabla 20 del Guerrero Tanque, «Golpe de defensa», con su
 * `productoId` del catálogo oficial. El archivo no está en el jar y
 * `docker-compose.contenido.yml` (AWS DEV) no lo monta: allí la misión no
 * existe y ninguna épica se regala.
 *
 * El Máster de prueba pelea con vida 1 y defensa 0 (`MasterDeMision.vida`,
 * opcional). Con las estadísticas del catálogo, el héroe del kit del banco (un
 * Guerrero Tanque de nivel 1) no le gana nunca a un Máster de nivel 3: 0 de
 * 2.000 en la simulación con el motor de combate real. La semilla de azar del
 * banco (`MISIONES_SEMILLA_DE_PRUEBAS=7`) es la misma de todas las ejecuciones.
 *
 * ## Qué se afirma, en orden
 *
 *   1. (HU-SIM-005 C1, configuración) El detalle de la misión dice que su
 *      Máster aparece con probabilidad 1 y lleva «Golpe de defensa».
 *   2. Se envía al héroe del kit con el ataque básico (sin estrategia): 201.
 *   3. (HU-SIM-005 C1, HU-SIM-006 C3) Al vencer el plazo, el reporte da éxito,
 *      el Máster de prueba entre los derrotados con su épica, el jefe, y la
 *      épica entre las recompensas. La vista del reporte lo pinta.
 *   4. (HU-SIM-006 C3) La épica está en el inventario del jugador como
 *      producto EPICA con el `productoId` del catálogo, una sola vez, y el
 *      historial la suma a su colección de épicas.
 *   5. Lo entregado no se duplica: tras varias vueltas más del trabajo en
 *      segundo plano (cada segundo en el banco) sigue habiendo una épica, un
 *      abono de créditos y un aviso de épica en la bandeja.
 *   6. El correo de la épica llega una sola vez al buzón de pruebas.
 *
 * Lo que no se puede ver por HTTP, y por eso no se afirma aquí, sino en las
 * pruebas del servicio: que el Máster está dos niveles por encima del héroe
 * (HU-SIM-006 C2; `CicloDeUnaMisionTest`) y que reintentar la liquidación tras
 * un fallo de un servicio no repite nada (`CicloDeUnaMisionTest`, claves de
 * idempotencia `mision-{id}-epica`). Desde el navegador no hay forma de
 * provocar ese fallo.
 *
 * La jugadora es nueva en cada corrida: su héroe y su inventario son solo
 * suyos.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import { correosPara } from './ayudantes/correo.js';
import { sesionDe as sesionDelBanco } from './ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
// El libro de créditos no está detrás del borde en el banco: puerto publicado.
const FINANZAS = process.env.E2E_FINANZAS ?? 'http://localhost:8093/api/v1';
const CLAVE = 'Contrasena-E2E-2026';
const VISTA = '/frontend/app-web/src/contenido/misiones/misiones.html';

/** La misión del banco (semilla extra): Máster al 100 % y jefe de 1 de vida. */
const MISION = { id: 'dev-master-seguro', nombre: '[PROVISIONAL DE DEV] Máster seguro' };
const MASTER = 'Máster de prueba';
/** La épica de la Tabla 20 del Guerrero Tanque y su producto del catálogo oficial. */
const EPICA = {
  nombre: 'Golpe de defensa',
  productoId: '81af272d-74fb-3dc1-b6ff-01fdc99a1c1d',
};
/** Créditos de la misión (`recompensas.creditos` de su semilla). */
const CREDITOS_DE_LA_MISION = 3;

function sesionDe(api, apodo) {
  return sesionDelBanco(api, apodo, { clave: CLAVE, base: BORDE });
}

function conToken(token) {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

async function conSesion(page, jugadora) {
  await page.addInitScript(
    ([token, nombre, uid]) => {
      sessionStorage.setItem('nexus.token', token);
      sessionStorage.setItem('nexus.apodoActual', nombre);
      sessionStorage.setItem('nexus.rolActual', 'JUGADOR');
      sessionStorage.setItem('nexus.usuarioId', uid);
    },
    [jugadora.token, jugadora.apodo, jugadora.claims.uid],
  );
}

/** Todo el inventario de la jugadora (página a página, de dieciséis en dieciséis). */
async function inventarioDe(api, jugadora) {
  const elementos = [];
  for (let pagina = 0; pagina < 10; pagina++) {
    const r = await api.get(`/api/v1/inventario/elementos?pagina=${pagina}`, {
      headers: conToken(jugadora.token),
    });
    expect(r.status(), `inventario: ${await r.text()}`).toBe(200);
    const cuerpo = await r.json();
    const lote = cuerpo.elementos ?? [];
    elementos.push(...lote);
    if (lote.length < 16) {
      break;
    }
  }
  return elementos;
}

async function saldoBrutoDe(api, jugadora) {
  const r = await api.get(`${FINANZAS}/creditos/${jugadora.claims.uid}/saldo`, {
    headers: conToken(jugadora.token),
  });
  expect(r.status(), `saldo: ${await r.text()}`).toBe(200);
  return Number((await r.json()).saldoBruto);
}

/** Los avisos de la bandeja de la jugadora que cuelgan de una ejecución. */
async function avisosDe(api, jugadora, ejecucionId) {
  const r = await api.get(`/api/v1/users/${jugadora.claims.uid}/notifications`, {
    headers: conToken(jugadora.token),
  });
  expect(r.status(), `bandeja: ${await r.text()}`).toBe(200);
  return ((await r.json()).avisos ?? []).filter((a) => a.id.startsWith(`mision-${ejecucionId}-`));
}

/** El reporte de una ejecución, esperando a que la simulación y la entrega terminen. */
async function reporteTerminado(api, jugadora, ejecucionId) {
  let reporte = null;
  await expect
    .poll(
      async () => {
        const r = await api.get(`/api/v1/misiones/ejecuciones/${ejecucionId}`, {
          headers: conToken(jugadora.token),
        });
        if (r.status() !== 200) {
          return `HTTP ${r.status()}`;
        }
        reporte = await r.json();
        return reporte.recompensas?.entregaPendiente ? 'entrega pendiente' : 'terminada';
      },
      {
        timeout: 60_000,
        intervals: [1_000, 1_000, 2_000],
        message: 'la misión del Máster no terminó en el banco',
      },
    )
    .toBe('terminada');
  return reporte;
}

test.describe('Máster y épica de punta a punta (HU-SIM-005, HU-SIM-006)', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let jugadora;
  let heroe;
  let saldoInicial;
  let ejecucionId;
  let reporte;

  test.beforeAll(async () => {
    // B1: la cuenta espera su código de verificación antes del alta.
    test.setTimeout(180_000);
    api = await apiRequest.newContext({ baseURL: BORDE });
    jugadora = await sesionDe(api, `master_${Date.now()}`);
    // R17: el héroe del kit, con su arma equipada, y los créditos iniciales los
    // pone el alta. Hasta que termine, no hay héroe que enviar.
    await expect
      .poll(
        async () => {
          const r = await api.get('/api/v1/auth/onboarding', {
            headers: conToken(jugadora.token),
          });
          return r.ok() ? (await r.json()).estado : `HTTP ${r.status()}`;
        },
        { timeout: 60_000, message: 'el alta de la jugadora no terminó' },
      )
      .toBe('COMPLETO');
    const inicial = await inventarioDe(api, jugadora);
    heroe = inicial.find((e) => e.tipo === 'HEROE');
    expect(heroe, 'el alta deja un héroe en el inventario').toBeTruthy();
    expect(heroe.nivel).toBe(1);
    expect(heroe.disponible).toBe(true);
    // Sin ninguna épica antes de la misión: lo que aparezca después es de ella.
    expect(inicial.filter((e) => e.tipo === 'EPICA')).toEqual([]);
    saldoInicial = await saldoBrutoDe(api, jugadora);
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  test('1 · HU-SIM-005 C1: el detalle de la misión del banco da al Máster una probabilidad de 1 y su épica', async () => {
    const r = await api.get(`/api/v1/misiones/${MISION.id}`, {
      headers: conToken(jugadora.token),
    });
    expect(r.status(), await r.text()).toBe(200);
    const mision = await r.json();
    expect(mision).toMatchObject({
      id: MISION.id,
      nombre: MISION.nombre,
      origen: 'PROVISIONAL_DEV',
      estado: 'DISPONIBLE',
      probabilidadMaster: 1,
    });
    expect(mision.masters).toHaveLength(1);
    expect(mision.masters[0]).toMatchObject({
      nombre: MASTER,
      probabilidad: 1,
      epica: { nombre: EPICA.nombre },
    });
  });

  test('2 · el héroe del kit sale a la misión con el ataque básico: matrícula 201', async () => {
    const r = await api.post(`/api/v1/misiones/${MISION.id}/ejecuciones`, {
      headers: { ...conToken(jugadora.token), 'Idempotency-Key': `e2e-master-${Date.now()}` },
      data: { heroeId: heroe.id },
    });
    expect(r.status(), await r.text()).toBe(201);
    const activa = await r.json();
    expect(activa).toMatchObject({
      misionId: MISION.id,
      estado: 'EN_PROGRESO',
      heroe: { id: heroe.id },
    });
    ejecucionId = activa.ejecucionId;
  });

  test('3 · HU-SIM-005 C1 y HU-SIM-006 C3: el reporte muestra al Máster derrotado y su épica obtenida', async ({
    page,
  }) => {
    test.setTimeout(120_000);
    reporte = await reporteTerminado(api, jugadora, ejecucionId);

    expect(reporte).toMatchObject({
      ejecucionId,
      mision: { id: MISION.id, nombre: MISION.nombre, categoria: 'HISTORIA' },
      resultado: 'EXITO',
      jefeDerrotado: true,
      heroe: { id: heroe.id, nivel: 1 },
    });
    // Apareció, lo derrotó, y entregó su épica por nombre.
    expect(reporte.mastersDerrotados).toEqual([{ nombre: MASTER, epica: EPICA.nombre }]);
    expect(reporte.recompensas.epicas).toEqual([EPICA.nombre]);
    // Sin regulares: el Máster y el jefe son los dos encuentros.
    expect(reporte.enemigosDerrotados).toEqual([]);
    expect(reporte.combate.encuentros).toBe(2);
    expect(reporte.recompensas.creditos).toBe(CREDITOS_DE_LA_MISION);
    // La épica del catálogo se entregó: no figura entre lo que no se pudo entregar.
    expect(reporte.recompensas.sinEntregar ?? []).toEqual([]);
    expect(reporte.recompensas.entregaPendiente ?? false).toBe(false);

    // La vista del reporte lo cuenta con las mismas palabras.
    await conSesion(page, jugadora);
    await page.goto(`${BORDE}${VISTA}?reporte=${ejecucionId}`);
    await expect(page.locator('.mision-reporte h1')).toHaveText(MISION.nombre, {
      timeout: 20_000,
    });
    await expect(page.locator('.mision-reporte .mision-master__nombre')).toContainText(
      `Máster derrotado: ${MASTER}`,
    );
    await expect(page.locator('.mision-reporte')).toContainText(`Épica obtenida: ${EPICA.nombre}`);
    await expect(page.locator('.mision-reporte__epicas')).toContainText(`Épicas: ${EPICA.nombre}`);
  });

  test('4 · HU-SIM-006 C3: la épica está en el inventario como producto EPICA, una vez, y en la colección de épicas', async () => {
    let epicas = [];
    await expect
      .poll(
        async () => {
          epicas = (await inventarioDe(api, jugadora)).filter((e) => e.tipo === 'EPICA');
          return epicas.length;
        },
        { timeout: 30_000, message: 'la épica no llegó al inventario' },
      )
      .toBe(1);
    expect(epicas[0]).toMatchObject({
      tipo: 'EPICA',
      productoId: EPICA.productoId,
      nombrePropio: EPICA.nombre,
    });

    // La colección de épicas de Máster del historial (§7.8.10 y §7.8.12).
    const r = await api.get('/api/v1/misiones/historial', { headers: conToken(jugadora.token) });
    expect(r.status(), await r.text()).toBe(200);
    const historial = await r.json();
    expect(historial.epicas).toHaveLength(1);
    expect(historial.epicas[0]).toMatchObject({ nombre: EPICA.nombre, master: MASTER });

    // El héroe volvió libre y con su experiencia, igual que tras cualquier misión.
    const despues = (await inventarioDe(api, jugadora)).find((e) => e.tipo === 'HEROE');
    expect(despues.disponible).toBe(true);
    expect(despues.experiencia).toBeCloseTo(reporte.recompensas.experiencia, 6);
  });

  test('5 · lo entregado no se duplica: varias vueltas más del trabajo y sigue una épica, un abono y un aviso', async () => {
    // El trabajo en segundo plano da una vuelta por segundo en el banco
    // (MISIONES_INTERVALO_MS): cinco segundos son varias vueltas completas.
    await expect
      .poll(async () => (await saldoBrutoDe(api, jugadora)) - saldoInicial, {
        timeout: 30_000,
        message: 'el libro de créditos no recibió la recompensa',
      })
      .toBe(CREDITOS_DE_LA_MISION);
    await new Promise((resolver) => setTimeout(resolver, 5_000));

    const epicas = (await inventarioDe(api, jugadora)).filter((e) => e.tipo === 'EPICA');
    expect(epicas).toHaveLength(1);
    expect((await saldoBrutoDe(api, jugadora)) - saldoInicial).toBe(CREDITOS_DE_LA_MISION);

    // RF-NOT-004: la épica tiene su propio aviso, con id estable por ejecución.
    const avisos = await avisosDe(api, jugadora, ejecucionId);
    const deLaEpica = avisos.filter((a) => a.id === `mision-${ejecucionId}-aviso-epica`);
    expect(deLaEpica).toHaveLength(1);
    expect(deLaEpica[0]).toMatchObject({ tipo: 'MISION', leida: false });
    expect(deLaEpica[0].titulo).toBe(`Obtuviste la épica «${EPICA.nombre}»`);
    expect(deLaEpica[0].cuerpo).toContain(MASTER);
    expect(deLaEpica[0].cuerpo).toContain('Ya está en tu inventario.');
    // Y el del fin de misión menciona la épica.
    const deLaMision = avisos.find((a) => a.id === `mision-${ejecucionId}-aviso`);
    expect(deLaMision?.cuerpo).toContain(`Aprendió la épica «${EPICA.nombre}»`);
  });

  test('6 · RF-COR-005: el correo de la épica llega una sola vez al buzón de pruebas', async () => {
    let correos = [];
    await expect
      .poll(
        async () => {
          correos = (await correosPara(jugadora.email, { base: BORDE })).filter((m) =>
            String(m.Subject ?? '').includes(`Obtuviste la épica «${EPICA.nombre}»`),
          );
          return correos.length;
        },
        { timeout: 60_000, message: 'el correo de la épica no llegó al buzón de pruebas' },
      )
      .toBeGreaterThan(0);
    // Idempotency-Key `mision-{id}-correo-epica`: aunque el trabajo dé varias
    // vueltas, el servicio de correo encola uno solo.
    expect(correos).toHaveLength(1);
  });
});
