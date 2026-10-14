// @ts-check
/**
 * Administración del asistente (§7.4.7 y §7.4.10), de punta a punta: el panel
 * del asistente con el administrador de pruebas (segundo factor incluido) y
 * ms-chatbot de verdad.
 *
 * El recorrido de la revisión final del 7.4: analíticas → base de
 * conocimiento → un tema nuevo (entrada) con sus variantes de pregunta y su
 * intención, más otra intención a la que también responde → la versión
 * candidata → se evalúa y se publica → un visitante lo pregunta y el
 * asistente responde con lo publicado.
 *
 * El tema lleva un sufijo de la corrida en el título y en sus variantes: no
 * se parece a ninguna pregunta de los casos de evaluación, así que la
 * candidata no rinde peor que producción y se puede desplegar.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import { sesionDe as sesionDelBanco } from './ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const CLAVE = 'Contrasena-E2E-2026';
const ADMIN = process.env.E2E_ADMIN ?? 'admin_e2e';
const SUFIJO = Date.now().toString(36);
// SA-7: la consola del asistente por su dirección limpia (la ruta del fichero redirige aquí).
const VISTA_PANEL = '/consola/asistente';

const TEMA = {
  titulo: `Torneo relámpago Ópalo ${SUFIJO}`,
  variantes: [`torneo relampago opalo ${SUFIJO}`, `que es el torneo relampago opalo ${SUFIJO}`],
  variantesEn: [`opal lightning tournament ${SUFIJO}`],
  respuesta: `El torneo relámpago Ópalo ${SUFIJO} es un evento de prueba del banco: se juega en «Torneo».`,
  respuestaEn: `The Opal lightning tournament ${SUFIJO} is a test event of the bench: it is played in «Torneo».`,
};

function conToken(token) {
  return { Authorization: `Bearer ${token}` };
}

test.describe('Asistente · administración (§7.4.7, §7.4.10)', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let admin;

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    admin = await sesionDelBanco(api, ADMIN, { clave: CLAVE, base: BORDE });
    expect(admin.claims.rol).toBe('ADMINISTRADOR');
  });

  test.afterAll(async () => {
    await api?.dispose();
  });

  test('analíticas → base → tema con variantes e intenciones → candidata → publicar → responde', async ({
    page,
  }) => {
    test.setTimeout(180_000);
    await page.addInitScript(
      ([token, apodo, uid]) => {
        sessionStorage.setItem('nexus.token', token);
        sessionStorage.setItem('nexus.apodoActual', apodo);
        sessionStorage.setItem('nexus.rolActual', 'ADMINISTRADOR');
        sessionStorage.setItem('nexus.usuarioId', uid);
      },
      [admin.token, admin.apodo, admin.claims.uid],
    );

    // 1. Analíticas: las cifras del panel son las del servicio.
    const analiticas = await api.get('/api/v1/chatbot/admin/analiticas', {
      headers: conToken(admin.token),
    });
    expect(analiticas.status(), await analiticas.text()).toBe(200);
    await page.goto(`${BORDE}${VISTA_PANEL}#analiticas`);
    const tablero = page.locator('[data-zona="analiticas"]');
    await expect(
      tablero.locator('.metrica--cifra').filter({ hasText: 'Preguntas' }).first(),
    ).toBeVisible({
      timeout: 30_000,
    });
    await expect(
      tablero.locator('.metrica--cifra').filter({ hasText: 'Tasa de resolución' }),
    ).toBeVisible();

    // 2. Base de conocimiento: la candidata (se crea si no hay una).
    await page.locator('[role="tab"]', { hasText: 'Base de conocimiento' }).click();
    const agregar = page.locator('[data-accion="agregar-tema"]');
    const crearCandidata = page.getByRole('button', { name: 'Crear candidata' });
    await expect(agregar.or(crearCandidata)).toBeVisible({ timeout: 30_000 });
    if (await crearCandidata.isVisible()) {
      await page.locator('input[name="descripcion"]').fill(`E2E del chatbot ${SUFIJO}`);
      await crearCandidata.click();
    }
    await expect(agregar).toBeVisible({ timeout: 30_000 });

    // 3. El tema: título, intención, variantes, respuestas y otra intención.
    await agregar.click();
    const dialogo = page.locator('[role="dialog"]');
    await expect(dialogo).toBeVisible();
    await dialogo.locator('input[name="titulo"]').fill(TEMA.titulo);
    await dialogo.locator('select[name="categoria"]').selectOption('MODALIDAD_JUEGO');
    await dialogo.locator('textarea[name="variantesEs"]').fill(TEMA.variantes.join('\n'));
    await dialogo.locator('textarea[name="variantesEn"]').fill(TEMA.variantesEn.join('\n'));
    await dialogo.locator('textarea[name="respuestaEs"]').fill(TEMA.respuesta);
    await dialogo.locator('textarea[name="respuestaEn"]').fill(TEMA.respuestaEn);
    await dialogo.locator('input[name="intencionesAdicionales"][value="FAQ_GENERAL"]').check();
    const guardado = page.waitForResponse(
      (r) =>
        r.url().includes('/base-conocimiento/borrador/temas') && r.request().method() === 'POST',
    );
    await dialogo.locator('[data-accion="guardar-tema"]').click();
    const respuestaDelTema = await guardado;
    expect(respuestaDelTema.status(), await respuestaDelTema.text()).toBe(201);
    await expect(dialogo).toBeHidden();
    await expect(page.getByText(TEMA.titulo).first()).toBeVisible();

    // Lo guardado: variantes e intención adicional, en la candidata.
    const temas = await api.get('/api/v1/chatbot/admin/base-conocimiento/borrador/temas', {
      headers: conToken(admin.token),
    });
    expect(temas.status()).toBe(200);
    const nuevo = (await temas.json()).find((t) => t.titulo === TEMA.titulo);
    expect(nuevo, 'el tema está en la candidata').toBeTruthy();
    expect(nuevo.categoria).toBe('MODALIDAD_JUEGO');
    expect(nuevo.intencionesAdicionales).toEqual(['FAQ_GENERAL']);
    expect(nuevo.variantesEs).toEqual(TEMA.variantes);

    // Antes de publicar, producción no lo sabe.
    const visitante = `visitante-e2e-adm-${SUFIJO}`;
    const preguntar = async () => {
      const r = await api.post('/api/v1/chat/mensajes', {
        headers: { 'X-Id-Sesion-Anonima': visitante },
        data: { contenido: `¿Qué es el torneo relámpago Ópalo ${SUFIJO}?` },
      });
      expect(r.status(), await r.text()).toBe(200);
      return (await r.json()).contenido;
    };
    expect(await preguntar()).not.toContain(TEMA.respuesta);

    // 4. Reentrenamiento: evaluar y publicar la candidata.
    await page.locator('[role="tab"]', { hasText: 'Reentrenamiento' }).click();
    const evaluar = page.locator('[data-accion="evaluar"]');
    await expect(evaluar).toBeVisible({ timeout: 30_000 });
    await evaluar.click();
    await expect(page.locator('[data-zona="resultado"]')).not.toBeEmpty({ timeout: 30_000 });
    await page.locator('[data-accion="desplegar"]').click();
    const confirmacion = page.locator('[role="dialog"]');
    await expect(confirmacion).toContainText('¿Desplegar la versión');
    const desplegado = page.waitForResponse(
      (r) =>
        r.url().includes('/base-conocimiento/borrador/despliegue') &&
        r.request().method() === 'POST',
    );
    await confirmacion.getByRole('button', { name: 'Desplegar' }).click();
    const respuestaDelDespliegue = await desplegado;
    expect(respuestaDelDespliegue.status(), await respuestaDelDespliegue.text()).toBe(200);
    await expect(
      page.getByText('está en producción: el asistente ya responde con ella'),
    ).toBeVisible();

    // 5. Ya responde con lo publicado, a cualquiera.
    await expect.poll(preguntar, { timeout: 20_000 }).toContain(TEMA.respuesta);
    const enIngles = await api.post('/api/v1/chat/mensajes', {
      headers: { 'X-Id-Sesion-Anonima': `${visitante}-en` },
      data: { contenido: `what is the opal lightning tournament ${SUFIJO}` },
    });
    expect((await enIngles.json()).contenido).toContain(TEMA.respuestaEn);
  });

  test('sin token de administración no hay panel: 401 sin sesión, 403 para un jugador', async () => {
    expect((await api.get('/api/v1/chatbot/admin/analiticas')).status()).toBe(401);
    const jugador = await sesionDelBanco(api, `chat_adm_jug_${SUFIJO}`, {
      clave: CLAVE,
      base: BORDE,
    });
    const prohibido = await api.get('/api/v1/chatbot/admin/analiticas', {
      headers: conToken(jugador.token),
    });
    expect(prohibido.status()).toBe(403);
    const crear = await api.post('/api/v1/chatbot/admin/base-conocimiento/borrador', {
      headers: conToken(jugador.token),
      data: { descripcion: 'no' },
    });
    expect(crear.status()).toBe(403);
  });
});
