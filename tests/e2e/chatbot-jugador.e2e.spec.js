// @ts-check
/**
 * El asistente para un jugador (§7.4.4), de punta a punta por el borde con
 * ms-chatbot y los servicios de verdad que consulta con el token del propio
 * jugador: inventario, misiones, ms-subastas, notificaciones y ms-finanzas.
 *
 * El recorrido es el de la revisión final del 7.4: entra por /login →
 * «¿qué tengo en mi inventario?» → «¿cómo equipo a mi héroe?» → su misión
 * activa → sus subastas → sus notificaciones → sus transacciones → cambia el
 * idioma de las respuestas → sale y vuelve a entrar → la conversación y las
 * preferencias siguen ahí, y mientras estuvo fuera nadie las vio.
 *
 * ms-chatbot.yaml 1.15.0: al volver, el asistente le propone lo que sigue de
 * lo que preguntó (§7.4.3 «sugerencias proactivas») y su nivel de detalle
 * automático sigue su experiencia (§7.4.5); elegido «Breve», la respuesta
 * recortada trae «Más detalle», que la da entera.
 *
 * ms-chatbot.yaml 1.18.0 (observación de RF-CHA-008, «quedar auditado»): lo
 * que el asistente leyó de su cuenta queda en la bitácora central, que el
 * superadministrador consulta, sin la pregunta ni lo que respondió cada
 * servicio.
 *
 * La jugadora es nueva en cada corrida: el alta (R17) le deja 500 créditos,
 * el héroe y el arma del banco. Antes de preguntar, una misión técnica de
 * dos segundos le deja un aviso en la bandeja y 5 créditos más en el libro;
 * la misión «activa» se matricula justo antes de preguntar por ella («El
 * Templo Olvidado», doce horas = 24 s en el banco).
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import {
  ESPERA_DE_RESPUESTA_MS,
  abrirAsistente,
  asistenteDe,
  destinosDe,
  preguntar,
} from './ayudantes/asistente.js';
import { cuentaAdministrativaDeRol, sesionDe as sesionDelBanco } from './ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const CLAVE = 'Contrasena-E2E-2026';
const SUFIJO = Date.now().toString(36);
const PRUEBA = { id: 'dev-prueba-de-humo', nombre: '[PROVISIONAL DE DEV] Prueba de humo' };
const TEMPLO = { id: 'templo-olvidado', nombre: 'El Templo Olvidado' };

function conToken(token) {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

/** Lo que hace una persona: correo, contraseña y «Entrar». */
async function entrarPorLaVista(page, jugadora) {
  await page.goto(`${BORDE}/login`);
  await page.fill('#email', jugadora.email);
  const clave = page.locator('#password');
  await clave.focus();
  await clave.evaluate((el, v) => {
    el.value = v;
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }, CLAVE);
  await page.click('#botonEnviar');
  await page.waitForURL(/\/inicio(?:[?#]|$)/, { timeout: 30_000 });
}

/** El alta de la jugadora (créditos, héroe y arma) terminó. */
async function esperarElAlta(api, jugadora) {
  await expect
    .poll(
      async () => {
        const r = await api.get('/api/v1/auth/onboarding', { headers: conToken(jugadora.token) });
        return r.ok() ? (await r.json()).estado : `HTTP ${r.status()}`;
      },
      { timeout: 60_000, intervals: [1_000], message: 'el alta de la jugadora no terminó' },
    )
    .toBe('COMPLETO');
}

/** El héroe de la jugadora y una habilidad que el servicio de héroes acepta para él. */
async function heroeYHabilidad(api, jugadora) {
  const r = await api.get('/api/v1/inventario/elementos?pagina=0', {
    headers: conToken(jugadora.token),
  });
  expect(r.status(), `inventario: ${await r.text()}`).toBe(200);
  const heroe = ((await r.json()).elementos ?? []).find((e) => e.tipo === 'HEROE');
  expect(heroe, 'el alta deja un héroe en el inventario').toBeTruthy();
  // El prototipo (la clase de héroe) es del producto, no del elemento.
  const producto = await api.get(`/api/v1/productos/${heroe.productoId}`);
  expect(producto.status(), await producto.text()).toBe(200);
  const validacion = await api.post('/api/v1/estrategias/validacion', {
    headers: conToken(jugadora.token),
    data: { heroe: (await producto.json()).prototipo },
  });
  expect(validacion.status(), await validacion.text()).toBe(200);
  const habilidades = (await validacion.json()).habilidadesValidas ?? [];
  expect(habilidades.length, 'el héroe tiene alguna habilidad válida').toBeGreaterThan(0);
  return { heroe, habilidad: habilidades[0] };
}

async function matricular(api, jugadora, mision, heroe, habilidad) {
  const r = await api.post(`/api/v1/misiones/${mision.id}/ejecuciones`, {
    headers: {
      ...conToken(jugadora.token),
      'Idempotency-Key': `e2e-chatbot-${mision.id}-${SUFIJO}`,
    },
    data: { heroeId: heroe.id, rotaciones: [{ pasos: [habilidad] }] },
  });
  expect(r.status(), `matrícula en ${mision.id}: ${await r.text()}`).toBe(201);
  return r.json();
}

test.describe('Asistente · jugador (§7.4.4)', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let jugadora;
  let heroe;
  let habilidad;

  test.beforeAll(async () => {
    test.setTimeout(180_000);
    api = await apiRequest.newContext({ baseURL: BORDE });
    jugadora = await sesionDelBanco(api, `chat_jug_${SUFIJO}`, { clave: CLAVE, base: BORDE });
    await esperarElAlta(api, jugadora);
    ({ heroe, habilidad } = await heroeYHabilidad(api, jugadora));

    // La misión técnica (1 h = 2 s en el banco): al liquidarse deja el aviso
    // en la bandeja y los créditos en el libro.
    const prueba = await matricular(api, jugadora, PRUEBA, heroe, habilidad);
    await expect
      .poll(
        async () => {
          const r = await api.get(`/api/v1/misiones/ejecuciones/${prueba.ejecucionId}`, {
            headers: conToken(jugadora.token),
          });
          if (r.status() !== 200) {
            return `HTTP ${r.status()}`;
          }
          return (await r.json()).recompensas?.entregaPendiente ? 'entrega pendiente' : 'terminada';
        },
        {
          timeout: 60_000,
          intervals: [1_000, 1_000, 2_000],
          message: 'la misión técnica no terminó',
        },
      )
      .toBe('terminada');
    await expect
      .poll(
        async () => {
          const r = await api.get(`/api/v1/users/${jugadora.claims.uid}/notifications`, {
            headers: conToken(jugadora.token),
          });
          return r.ok() ? ((await r.json()).avisos ?? []).length : 0;
        },
        { timeout: 30_000, message: 'el aviso de la misión no llegó a la bandeja' },
      )
      .toBeGreaterThan(0);
  });

  test.afterAll(async () => {
    await api?.dispose();
  });

  test('entra y pregunta por lo suyo: inventario, equipo, misión, subastas, avisos y transacciones', async ({
    page,
  }) => {
    test.setTimeout(150_000);
    await entrarPorLaVista(page, jugadora);
    await abrirAsistente(page);

    // 1. Su inventario, en vivo, con su token.
    const inventario = await preguntar(page, '¿Qué tengo en mi inventario?');
    await expect(inventario).toContainText('elemento(s) en tu inventario');
    await expect(inventario).toContainText(heroe.nombrePropio);
    expect(await destinosDe(inventario)).toContain('inventario');

    // 2. Cómo equiparlo: es una guía de la base, no una consulta.
    const equipar = await preguntar(page, '¿Cómo equipo a mi héroe?');
    await expect(equipar).toContainText('Mi inventario');
    await expect(equipar).not.toContainText('elemento(s) en tu inventario');

    // 3. Su misión activa: se matricula ahora («El Templo Olvidado», 24 s).
    await matricular(api, jugadora, TEMPLO, heroe, habilidad);
    const mision = await preguntar(page, '¿Tengo alguna misión activa?');
    await expect(mision).toContainText('misión(es) en curso');
    await expect(mision).toContainText(TEMPLO.nombre);
    await expect(mision.locator('.chatbot-enriquecido__tarjeta')).toContainText(TEMPLO.nombre);

    // 4. Sus subastas y pujas (ms-subastas, con su token).
    const subastas = await preguntar(page, '¿Cómo van mis subastas?');
    await expect(subastas).toContainText('Vas ganando 0 subasta(s)');
    expect(await destinosDe(subastas)).toContain('subastas');

    // 5. Sus notificaciones: el aviso de la misión técnica.
    const avisos = await preguntar(page, '¿Tengo notificaciones?');
    await expect(avisos).toContainText('notificación(es) sin leer');
    await expect(avisos).toContainText(PRUEBA.nombre);

    // 6. Sus transacciones: los créditos del alta y los de la misión.
    const transacciones = await preguntar(page, '¿Cuáles son mis últimas transacciones?');
    await expect(transacciones).toContainText('Tus últimos movimientos de créditos');
    await expect(transacciones).toContainText('500');
  });

  test('cambia el idioma, sale y vuelve: la conversación y las preferencias siguen, y nadie las vio', async ({
    page,
  }) => {
    test.setTimeout(150_000);
    await entrarPorLaVista(page, jugadora);
    const asistente = await abrirAsistente(page);
    // Lo de la prueba anterior ya está: el historial es de la cuenta, no del navegador.
    await expect(asistente.preguntas.first()).toContainText('¿Qué tengo en mi inventario?');

    // Preferencias: respuestas en inglés.
    await asistente.preferencias.click();
    const panel = page.locator('.chatbot-preferencias');
    await expect(panel).toBeVisible();
    await panel.locator('select[name="idioma"]').selectOption('EN');
    await panel.locator('[data-accion="guardar-preferencias"]').click();
    await expect(panel).toContainText('Listo: guardamos tus preferencias.');
    await panel.locator('[data-accion="volver-al-chat"]').click();

    const enIngles = await preguntar(page, '¿Qué tengo en mi inventario?');
    await expect(enIngles).toContainText('item(s) in your inventory');

    // Sale.
    await page.locator('header[data-cabecera-app] [data-zona="cuenta"]').click();
    await page.locator('[data-zona="menu-cuenta"] [data-zona="cerrar-sesion"]').click();
    await page.waitForURL(/\/login\?.*motivo=cerrada/, { timeout: 20_000 });

    // Como visitante, en el mismo navegador, su conversación no está.
    const comoVisitante = await abrirAsistente(page);
    await expect(
      comoVisitante.preguntas.filter({ hasText: '¿Qué tengo en mi inventario?' }),
    ).toHaveCount(0);

    // Vuelve a entrar: la conversación y el inglés siguen.
    await entrarPorLaVista(page, jugadora);
    const otraVez = await abrirAsistente(page);
    await expect(otraVez.preguntas.filter({ hasText: '¿Qué tengo en mi inventario?' })).toHaveCount(
      2,
    );
    await otraVez.preferencias.click();
    await expect(page.locator('.chatbot-preferencias select[name="idioma"]')).toHaveValue('EN');
    await page.locator('.chatbot-preferencias [data-accion="volver-al-chat"]').click();
    const sigueEnIngles = await preguntar(page, '¿Cómo van mis subastas?');
    await expect(sigueEnIngles).toContainText("You're winning 0 auction(s)");

    // Lo deja como estaba para que una repetición empiece igual (1.15.0: el
    // nivel de quien nunca eligió es el automático).
    const r = await api.put('/api/v1/chat/preferencias', {
      headers: conToken(jugadora.token),
      data: { idioma: 'AUTOMATICO', nivelDetalle: 'AUTOMATICO' },
    });
    expect(r.status()).toBe(200);
  });

  test('al volver le propone lo que sigue de lo que preguntó, y el detalle sigue su experiencia', async ({
    page,
  }) => {
    test.setTimeout(150_000);
    await entrarPorLaVista(page, jugadora);
    const asistente = await abrirAsistente(page);

    // §7.4.3: preguntó cómo equipar a su héroe (un tema de la base); le
    // propone otros de ese tema y dice por qué.
    const propuesta = asistente.ventana.locator('[data-proactiva]');
    const grupo = propuesta.getByRole('group', { name: /^Porque preguntaste por «/ }).first();
    await expect(grupo).toBeVisible({ timeout: ESPERA_DE_RESPUESTA_MS });
    const sugerencia = grupo.getByRole('button').first();
    const elegida = ((await sugerencia.textContent()) ?? '').trim();
    const antes = await asistente.respuestas.count();
    await sugerencia.click();
    await expect(asistente.respuestas).toHaveCount(antes + 1, { timeout: ESPERA_DE_RESPUESTA_MS });
    await expect(asistente.preguntas.last()).toContainText(elegida);
    // La responde la base: no es una pregunta sin respuesta.
    await expect(asistente.respuestas.last()).not.toContainText(
      'No estoy seguro de haber entendido',
    );
    await expect(propuesta).toHaveCount(0);

    // §7.4.5: nunca eligió nivel; con lo que ya le resolvió (más de cinco,
    // menos de treinta), la respuesta completa.
    await asistente.preferencias.click();
    const panel = page.locator('.chatbot-preferencias');
    await expect(panel.locator('select[name="nivelDetalle"]')).toHaveValue('AUTOMATICO');
    await expect(panel.locator('[data-nivel-efectivo]')).toHaveText(
      'Ahora te respondo con la respuesta completa.',
    );

    // Elige «Breve»: lo esencial, con «Más detalle» a un clic.
    await panel.locator('select[name="nivelDetalle"]').selectOption('BREVE');
    await panel.locator('[data-accion="guardar-preferencias"]').click();
    await expect(panel).toContainText('Listo: guardamos tus preferencias.');
    await panel.locator('[data-accion="volver-al-chat"]').click();
    const breve = await preguntar(page, '¿Cómo funciona el Torneo?');
    const textoBreve = (
      (await breve.locator('.chatbot-ventana__texto').first().textContent()) ?? ''
    ).trim();
    const masDetalle = breve.getByRole('button', { name: 'Más detalle' });
    await expect(masDetalle).toBeVisible();
    await masDetalle.click();
    const entera = asistenteDe(page).respuestas.nth(antes + 2);
    await expect(entera).toBeVisible({ timeout: ESPERA_DE_RESPUESTA_MS });
    const textoEntero = (
      (await entera.locator('.chatbot-ventana__texto').first().textContent()) ?? ''
    ).trim();
    expect(textoEntero.startsWith(textoBreve)).toBe(true);
    expect(textoEntero.length).toBeGreaterThan(textoBreve.length);

    // «Restablecer» (RF-CHA-010) lo devuelve al automático.
    await asistente.preferencias.click();
    await panel.locator('[data-accion="restablecer-preferencias"]').click();
    await expect(panel).toContainText('Listo: guardamos tus preferencias.');
    await expect(panel.locator('select[name="nivelDetalle"]')).toHaveValue('AUTOMATICO');

    // Lo que le propone es suyo: otra jugadora no recibe nada de lo que preguntó.
    const otra = await sesionDelBanco(api, `chat_prop_${SUFIJO}`, { clave: CLAVE, base: BORDE });
    const ajenas = await api.get('/api/v1/chat/sugerencias/para-ti?limite=5', {
      headers: conToken(otra.token),
    });
    expect(ajenas.status()).toBe(200);
    for (const s of await ajenas.json()) {
      expect(s.motivo).not.toBe('POR_LO_QUE_PREGUNTASTE');
    }
  });

  test('su conversación no la lee nadie más: ni otra jugadora ni un visitante', async () => {
    const otra = await sesionDelBanco(api, `chat_otra_${SUFIJO}`, { clave: CLAVE, base: BORDE });
    const ajeno = await api.get('/api/v1/chat/historial', { headers: conToken(otra.token) });
    expect(ajeno.status()).toBe(200);
    expect(JSON.stringify(await ajeno.json())).not.toContain('¿Qué tengo en mi inventario?');

    // Con el uid de la jugadora en la consulta tampoco.
    const conUid = await api.get(`/api/v1/chat/historial?uid=${jugadora.claims.uid}`, {
      headers: conToken(otra.token),
    });
    expect([200, 400]).toContain(conUid.status());
    expect(JSON.stringify(await conUid.json())).not.toContain('¿Qué tengo en mi inventario?');
  });

  test('lo que el asistente leyó de su cuenta queda en la bitácora central, sin el contenido', async () => {
    const superAdmin = await sesionDelBanco(
      api,
      cuentaAdministrativaDeRol('SUPER_ADMINISTRADOR').apodo,
      {
        clave: CLAVE,
        base: BORDE,
      },
    );
    let asientos = [];
    await expect
      .poll(
        async () => {
          const consulta = new URLSearchParams({
            afectado: jugadora.claims.uid,
            size: '100',
            sort: 'fechaHora,desc',
          });
          const r = await api.get(`/api/v1/admin/auditoria?${consulta}`, {
            headers: conToken(superAdmin.token),
          });
          expect(r.status(), await r.text()).toBe(200);
          asientos = ((await r.json()).content ?? []).filter(
            (a) => a.motivo === 'CONSULTA_DEL_ASISTENTE',
          );
          return asientos.map((a) => a.valorNuevo);
        },
        { timeout: 30_000, message: 'los asientos del asistente no llegaron a la bitácora' },
      )
      .toEqual(
        expect.arrayContaining([
          'dato=inventario;resultado=consultado',
          'dato=misiones;resultado=consultado',
          'dato=subastas;resultado=consultado',
          'dato=notificaciones;resultado=consultado',
          'dato=movimientos-de-creditos;resultado=consultado',
        ]),
      );
    for (const asiento of asientos) {
      // Ella lo pidió y los datos son suyos; nada de lo que se respondió.
      expect(asiento.tipoAccion).toBe('OTRO');
      expect(asiento.administrador).toBe(jugadora.claims.uid);
      expect(asiento.afectado).toBe(jugadora.claims.uid);
      expect(asiento.referencia).toMatch(/^ms-chatbot:consulta-/);
      expect(JSON.stringify(asiento)).not.toContain(heroe.nombrePropio);
      expect(JSON.stringify(asiento)).not.toContain(TEMPLO.nombre);
    }

    // La jugadora no lee la bitácora.
    const propia = await api.get(`/api/v1/admin/auditoria?afectado=${jugadora.claims.uid}`, {
      headers: conToken(jugadora.token),
    });
    expect(propia.status()).toBe(403);
  });
});
