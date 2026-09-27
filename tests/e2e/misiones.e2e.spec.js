// @ts-check
/**
 * Misiones y progresión persistida — B9 (§7.8 del documento, M11), de punta a
 * punta por el borde con misiones, inventario, héroes, motor de combate,
 * productos, ms-finanzas y ms-identidad reales.
 *
 * ## Qué se afirma, en orden
 *
 *   1. El tablón lista, para una jugadora recién creada, «El Templo Olvidado»
 *      (el ejemplo del documento, §7.8.14) y la misión técnica del banco
 *      (semilla PROVISIONAL DE DEV, `MISIONES_SEMILLA_PROVISIONAL=true`), cada
 *      una con su origen, y la vista pinta sus tarjetas: hay servicio, no el
 *      estado «no disponible».
 *   2. Desde la vista: elige su héroe (el del alta), comprueba la estrategia
 *      con el servicio de héroes, confirma y la matrícula responde 201 con la
 *      rotación elegida y una `Idempotency-Key`.
 *   3. Al vencer el plazo (en el banco una hora de misión dura 2 s,
 *      `MISIONES_SEGUNDOS_POR_HORA`), el trabajo en segundo plano simula con
 *      el motor y la IA de héroes y liquida: el reporte dice Éxito, el jefe
 *      derrotado, la experiencia y los 5 créditos; la vista lo pinta, y el
 *      historial lo enlaza.
 *   4. La progresión PERSISTE fuera de misiones: el inventario le sumó la
 *      experiencia al héroe y lo liberó; ms-finanzas abonó los créditos (por
 *      `refId`, una sola vez); la estrategia con la que salió quedó guardada.
 *   5. La pestaña Estrategia carga esa estrategia guardada y la vuelve a
 *      guardar (§7.8.12).
 *   6. En curso y cancelación (§7.8.7, «Abandonada»): con «El Templo
 *      Olvidado» (doce horas = 24 s en el banco) el héroe queda bloqueado en
 *      el inventario, la misión se ve en «En curso» y cancelarla lo libera sin
 *      recompensas. La misma `Idempotency-Key` no crea una segunda ejecución.
 *
 * La jugadora es nueva en cada corrida (`Date.now()`): su héroe es solo suyo,
 * así que bloquearlo en una misión no le quita el héroe a ninguna otra prueba
 * del banco.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
// El libro de créditos no está detrás del borde en el banco: puerto publicado.
const FINANZAS = process.env.E2E_FINANZAS ?? 'http://localhost:8093/api/v1';
const CLAVE = 'Contrasena-E2E-2026';
const VISTA = '/frontend/app-web/src/contenido/misiones/misiones.html';

/** La misión técnica de la semilla provisional de dev: un enemigo y un jefe de 1 de vida. */
const PRUEBA = { id: 'dev-prueba-de-humo', nombre: '[PROVISIONAL DE DEV] Prueba de humo' };
/** El ejemplo del documento (§7.8.14): doce horas, 24 s en el banco. */
const TEMPLO = { id: 'templo-olvidado', nombre: 'El Templo Olvidado' };
/** Créditos de la misión técnica (`recompensas.creditos` de su semilla). */
const CREDITOS_DE_PRUEBA = 5;

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

/** El héroe de la jugadora, tal como lo devuelve su inventario. */
async function heroeDe(api, jugadora) {
  const r = await api.get('/api/v1/inventario/elementos?pagina=0', {
    headers: conToken(jugadora.token),
  });
  expect(r.status(), `inventario: ${await r.text()}`).toBe(200);
  const heroe = ((await r.json()).elementos ?? []).find((e) => e.tipo === 'HEROE');
  expect(heroe, 'el alta deja un héroe en el inventario').toBeTruthy();
  return heroe;
}

async function saldoBrutoDe(api, jugadora) {
  const r = await api.get(`${FINANZAS}/creditos/${jugadora.claims.uid}/saldo`, {
    headers: conToken(jugadora.token),
  });
  expect(r.status(), `saldo: ${await r.text()}`).toBe(200);
  return Number((await r.json()).saldoBruto);
}

/** El reporte de una ejecución, esperando a que la simulación termine (409 mientras tanto). */
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
        // Hasta que no quede nada reintentándose, la entrega no ha terminado.
        return reporte.recompensas?.entregaPendiente ? 'entrega pendiente' : 'terminada';
      },
      {
        timeout: 60_000,
        intervals: [1_000, 1_000, 2_000],
        message: 'la misión no terminó en el banco',
      },
    )
    .toBe('terminada');
  return reporte;
}

test.describe('Misiones y progresión persistida (B9, §7.8)', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let jugadora;
  let heroeInicial;
  let saldoInicial;
  let habilidad;
  let ejecucionId;
  let reporte;

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    jugadora = await sesionDe(api, `misiones_${Date.now()}`);
    // R17: el héroe con su arma equipada y los créditos iniciales los pone el
    // alta. Hasta que termine, no hay héroe que enviar.
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
    heroeInicial = await heroeDe(api, jugadora);
    saldoInicial = await saldoBrutoDe(api, jugadora);
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  test('1 · el tablón lista la misión del documento y la del banco, cada una con su origen', async ({
    page,
  }) => {
    // El héroe recién dado de alta: nivel 1, sin experiencia y libre.
    expect(heroeInicial.nivel).toBe(1);
    expect(heroeInicial.experiencia).toBe(0);
    expect(heroeInicial.disponible).toBe(true);
    expect(heroeInicial.ejecucionMisionId ?? null).toBeNull();

    const r = await api.get('/api/v1/misiones?categoria=HISTORIA', {
      headers: conToken(jugadora.token),
    });
    expect(r.status(), await r.text()).toBe(200);
    const pagina = await r.json();
    expect(pagina.tamanio).toBe(16);
    const porId = new Map(pagina.misiones.map((m) => [m.id, m]));
    expect(porId.get(TEMPLO.id)).toMatchObject({
      nombre: TEMPLO.nombre,
      origen: 'DOCUMENTO',
      estado: 'DISPONIBLE',
      duracionHoras: 12,
    });
    expect(porId.get(PRUEBA.id)).toMatchObject({
      nombre: PRUEBA.nombre,
      origen: 'PROVISIONAL_DEV',
      estado: 'DISPONIBLE',
    });
    // Sin token no hay tablón: todas las rutas son del jugador autenticado.
    expect((await api.get('/api/v1/misiones?categoria=HISTORIA')).status()).toBe(401);

    await conSesion(page, jugadora);
    await page.goto(`${BORDE}${VISTA}`);
    await expect(page.locator(`.mision-card[data-mision="${TEMPLO.id}"]`)).toBeVisible({
      timeout: 20_000,
    });
    await expect(page.locator(`.mision-card[data-mision="${PRUEBA.id}"]`)).toBeVisible();
    // Hay servicio: ni rastro del estado «no disponible».
    await expect(page.locator('.misiones-estado[data-estado="sin-abrir"]')).toHaveCount(0);
  });

  test('2 · iniciar desde la vista: héroe, estrategia comprobada, confirmación y matrícula 201', async ({
    page,
  }) => {
    await conSesion(page, jugadora);
    await page.goto(`${BORDE}${VISTA}`);
    const tarjeta = page.locator(`.mision-card[data-mision="${PRUEBA.id}"]`);
    await expect(tarjeta).toBeVisible({ timeout: 20_000 });
    await tarjeta.locator('[data-accion="iniciar"]').click();

    // El configurador: el héroe del alta ya elegido y sus habilidades, que
    // dice el servicio de héroes para su prototipo y su nivel.
    await expect(page.locator('.mision-detalle__nombre')).toHaveText(PRUEBA.nombre, {
      timeout: 20_000,
    });
    const paso = page.locator('.estrategia__paso select').first();
    await expect(paso).toBeVisible({ timeout: 30_000 });
    await expect(page.locator('select[name="nivel"]')).toHaveValue('1');
    habilidad = await paso.locator('option:not([value=""])').first().getAttribute('value');
    expect(habilidad).toBeTruthy();
    await paso.selectOption(habilidad);
    await page.locator('[data-accion="comprobar-estrategia"]').click();
    await expect(page.locator('.estrategia__veredicto .aviso--exito')).toBeVisible({
      timeout: 30_000,
    });
    await expect(page.locator('.estrategia__veredicto')).toContainText(
      'quedará guardada para la próxima',
    );

    const iniciar = page.locator('[data-accion="iniciar-mision"]');
    await expect(iniciar).toHaveAttribute('aria-disabled', 'false');
    await iniciar.click();
    const dialogo = page.locator('[role="dialog"]');
    await expect(dialogo).toContainText(`«${PRUEBA.nombre}»`);
    await expect(dialogo).toContainText('queda bloqueado');

    // La vista navega en cuanto la matrícula contesta, y con la navegación el
    // navegador suelta el cuerpo de la respuesta: se lee al pasar por la ruta.
    let matricula = null;
    await page.route(`**/api/v1/misiones/${PRUEBA.id}/ejecuciones`, async (ruta) => {
      const respuesta = await ruta.fetch();
      matricula = {
        status: respuesta.status(),
        cuerpo: await respuesta.json().catch(() => null),
        peticion: ruta.request(),
      };
      await ruta.fulfill({ response: respuesta });
    });
    await dialogo.locator('[data-accion="confirmar"]').click();
    await expect.poll(() => matricula?.status, { timeout: 20_000 }).toBeTruthy();
    expect(matricula.status, JSON.stringify(matricula.cuerpo)).toBe(201);
    expect(matricula.peticion.method()).toBe('POST');
    expect(matricula.peticion.headers()['idempotency-key']).toBeTruthy();
    expect(matricula.peticion.postDataJSON()).toEqual({
      heroeId: heroeInicial.id,
      rotaciones: [{ pasos: [habilidad] }],
    });
    const activa = matricula.cuerpo;
    expect(activa).toMatchObject({
      misionId: PRUEBA.id,
      estado: 'EN_PROGRESO',
      heroe: { id: heroeInicial.id },
    });
    ejecucionId = activa.ejecucionId;

    // Vuelve al tablón, en «En curso», con el aviso de que salió.
    await expect(page).toHaveURL(/misiones\.html\?iniciada=dev-prueba-de-humo#en-curso$/);
    await expect(page.locator('.misiones__aviso')).toContainText('Tu héroe salió a la misión');
  });

  test('3 · al vencer el plazo se simula en segundo plano: el reporte trae experiencia y créditos', async ({
    page,
  }) => {
    test.setTimeout(120_000);
    reporte = await reporteTerminado(api, jugadora, ejecucionId);

    expect(reporte).toMatchObject({
      ejecucionId,
      mision: { id: PRUEBA.id, nombre: PRUEBA.nombre, categoria: 'HISTORIA' },
      resultado: 'EXITO',
      jefeDerrotado: true,
      heroe: { id: heroeInicial.id, nivel: 1 },
    });
    // Los regulares van en la lista; el jefe, en `jefeDerrotado`.
    expect(reporte.enemigosDerrotados).toEqual([{ nombre: 'Enemigo de prueba', cantidad: 1 }]);
    expect(reporte.combate.encuentros).toBe(2);
    expect(reporte.combate.danoInfligido).toBeGreaterThan(0);
    expect(reporte.recompensas.creditos).toBe(CREDITOS_DE_PRUEBA);
    // 10 × 1,2^(1d8) por cada enemigo derrotado, jefe incluido (§6.1.1): entre
    // 12 y 43 cada uno, así que entre 24 y 86 por los dos.
    expect(reporte.recompensas.experiencia).toBeGreaterThanOrEqual(24);
    expect(reporte.recompensas.experiencia).toBeLessThanOrEqual(86);
    expect(reporte.recompensas.entregaPendiente ?? false).toBe(false);

    // La vista del reporte: resultado, experiencia y créditos.
    await conSesion(page, jugadora);
    await page.goto(`${BORDE}${VISTA}?reporte=${ejecucionId}`);
    await expect(page.locator('.mision-reporte h1')).toHaveText(PRUEBA.nombre, {
      timeout: 20_000,
    });
    await expect(page.locator('.mision-reporte .mision-estado').first()).toHaveText('Éxito');
    const recompensas = page.locator('[data-bloque="recompensas"]');
    await expect(recompensas.locator('.metrica', { hasText: 'Créditos' })).toContainText(
      String(CREDITOS_DE_PRUEBA),
    );
    await expect(recompensas.locator('.metrica', { hasText: 'Experiencia' })).toContainText(
      String(Math.round(reporte.recompensas.experiencia)),
    );
    await expect(page.locator('.mision-reporte')).toContainText('Derrotó al jefe final.');

    // Y el historial la enlaza.
    await page.goto(`${BORDE}${VISTA}#historial`);
    const fila = page.locator('.mision-historial__tabla tbody tr', { hasText: PRUEBA.nombre });
    await expect(fila).toBeVisible({ timeout: 20_000 });
    await expect(fila.getByRole('link', { name: /Ver el reporte/ })).toHaveAttribute(
      'href',
      `misiones.html?reporte=${ejecucionId}`,
    );
  });

  test('4 · la progresión persiste: experiencia en el inventario, créditos en el libro, estrategia guardada', async () => {
    // El inventario liberó al héroe sumándole la experiencia en la misma
    // escritura (inventario.yaml 1.6.0).
    let heroe;
    await expect
      .poll(
        async () => {
          heroe = await heroeDe(api, jugadora);
          return heroe.disponible === true && !heroe.ejecucionMisionId;
        },
        { timeout: 30_000, message: 'el inventario no liberó al héroe' },
      )
      .toBe(true);
    // Con dos enemigos no llega a 100 (lo que pide el nivel 2): sigue en el 1
    // con toda la experiencia de la misión.
    expect(heroe.nivel).toBe(reporte.heroe.nivelAlcanzado ?? 1);
    expect(heroe.experiencia).toBeCloseTo(
      heroeInicial.experiencia + reporte.recompensas.experiencia,
      6,
    );

    // ms-finanzas abonó los créditos de la misión, una vez (refId por misión).
    await expect
      .poll(async () => (await saldoBrutoDe(api, jugadora)) - saldoInicial, {
        timeout: 30_000,
        message: 'el libro de créditos no recibió la recompensa',
      })
      .toBe(CREDITOS_DE_PRUEBA);

    // La estrategia con que salió quedó guardada para el héroe (§7.8.12).
    const r = await api.get(`/api/v1/misiones/estrategias/${heroeInicial.id}`, {
      headers: conToken(jugadora.token),
    });
    expect(r.status(), await r.text()).toBe(200);
    expect(await r.json()).toMatchObject({
      heroeId: heroeInicial.id,
      nivel: 1,
      rotaciones: [{ prioridad: 'Alta', pasos: [habilidad] }],
    });

    // Y la misión ya no está disponible sin más: el tablón la da por completada.
    const tablon = await api.get('/api/v1/misiones?categoria=HISTORIA', {
      headers: conToken(jugadora.token),
    });
    const prueba = (await tablon.json()).misiones.find((m) => m.id === PRUEBA.id);
    expect(prueba).toMatchObject({ estado: 'COMPLETADA', ultimaEjecucionId: ejecucionId });
  });

  test('5 · la pestaña Estrategia carga la guardada del héroe y la vuelve a guardar', async ({
    page,
  }) => {
    await conSesion(page, jugadora);
    await page.goto(`${BORDE}${VISTA}#estrategia`);
    const nota = page.locator('[data-seccion="estrategia"] [data-zona="guardada"]');
    await expect(nota).toBeVisible({ timeout: 30_000 });
    await expect(nota).toContainText('Es la estrategia que guardaste');
    await expect(page.locator('[data-seccion="estrategia"] .estrategia__paso select').first()).toHaveValue(
      habilidad,
    );

    await page.locator('[data-seccion="estrategia"] [data-accion="comprobar-estrategia"]').click();
    const guardar = page.locator('[data-accion="guardar-estrategia"]');
    await expect(guardar).toBeVisible({ timeout: 30_000 });
    const guardado = page.waitForResponse(
      (r) =>
        r.url().includes(`/api/v1/misiones/estrategias/${heroeInicial.id}`) &&
        r.request().method() === 'PUT',
    );
    await guardar.click();
    const respuesta = await guardado;
    expect(respuesta.status(), await respuesta.text()).toBe(200);
    expect(respuesta.request().postDataJSON()).toEqual({ rotaciones: [{ pasos: [habilidad] }] });
    await expect(page.locator('[data-zona="guardado"] .aviso--exito')).toContainText(
      'Estrategia guardada',
    );
  });

  test('6 · en curso y cancelación: el héroe queda bloqueado y cancelar lo libera sin recompensas', async ({
    page,
  }) => {
    test.setTimeout(120_000);
    const antes = await heroeDe(api, jugadora);
    const clave = `e2e-templo-${Date.now()}`;
    const matricular = () =>
      api.post(`/api/v1/misiones/${TEMPLO.id}/ejecuciones`, {
        headers: { ...conToken(jugadora.token), 'Idempotency-Key': clave },
        data: { heroeId: heroeInicial.id, rotaciones: [{ pasos: [habilidad] }] },
      });
    const creada = await matricular();
    expect(creada.status(), await creada.text()).toBe(201);
    const activa = await creada.json();
    // La misma clave no crea otra: devuelve la misma ejecución (200).
    const repetida = await matricular();
    expect(repetida.status(), await repetida.text()).toBe(200);
    expect((await repetida.json()).ejecucionId).toBe(activa.ejecucionId);

    // §7.8.10: bloqueado en el inventario mientras dure.
    const bloqueado = await heroeDe(api, jugadora);
    expect(bloqueado.disponible).toBe(false);
    expect(bloqueado.ejecucionMisionId).toBe(activa.ejecucionId);

    await conSesion(page, jugadora);
    await page.goto(`${BORDE}${VISTA}#en-curso`);
    const tarjeta = page.locator(`.mision-activa[data-ejecucion="${activa.ejecucionId}"]`);
    await expect(tarjeta).toBeVisible({ timeout: 20_000 });
    await expect(tarjeta).toContainText(TEMPLO.nombre);
    await expect(tarjeta).toContainText('Termina en');
    await tarjeta.locator('[data-accion="cancelar-mision"]').click();
    const dialogo = page.locator('[role="dialog"]');
    await expect(dialogo).toContainText('Penalización');
    const cancelacion = page.waitForResponse(
      (r) =>
        r.url().includes(`/api/v1/misiones/ejecuciones/${activa.ejecucionId}/cancelacion`) &&
        r.request().method() === 'POST',
    );
    await dialogo.locator('[data-accion="confirmar"]').click();
    const respuesta = await cancelacion;
    expect(respuesta.status(), await respuesta.text()).toBe(200);
    expect(await respuesta.json()).toMatchObject({
      ejecucionId: activa.ejecucionId,
      estado: 'ABANDONADA',
    });
    await expect(page.locator('.misiones-en-curso__aviso')).toContainText(
      `Cancelaste «${TEMPLO.nombre}»`,
    );

    // Abandonada no tiene reporte (se genera al completarse el tiempo).
    const sinReporte = await api.get(`/api/v1/misiones/ejecuciones/${activa.ejecucionId}`, {
      headers: conToken(jugadora.token),
    });
    expect(sinReporte.status()).toBe(409);
    // Y el héroe vuelve sin nada nuevo (penalización provisional: se pierde todo).
    await expect
      .poll(
        async () => {
          const heroe = await heroeDe(api, jugadora);
          return heroe.disponible === true && !heroe.ejecucionMisionId;
        },
        { timeout: 30_000, message: 'cancelar no liberó al héroe' },
      )
      .toBe(true);
    const despues = await heroeDe(api, jugadora);
    expect(despues.nivel).toBe(antes.nivel);
    expect(despues.experiencia).toBeCloseTo(antes.experiencia, 6);
  });
});
