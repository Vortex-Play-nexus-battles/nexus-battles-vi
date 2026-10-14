// @ts-check
/**
 * El asistente y la subasta (§7.4.4 «Acceso a información en tiempo real:
 * estado de productos en la subasta», ms-chatbot.yaml 1.16.0), de punta a
 * punta por el borde con ms-chatbot y ms-subastas de verdad.
 *
 * Una vendedora publica una subasta con un objeto que le entrega el banco
 * (inventario, con la credencial de servicio del banco) y un postor puja.
 * Entonces:
 *   - un visitante pregunta qué hay ahora en la subasta y por ese producto:
 *     la ve con su oferta vigente, sus pujas y su cierre, y nada de quién la
 *     vende;
 *   - la vendedora pregunta cómo van sus subastas: su tarjeta, con la puja;
 *   - el postor, lo mismo: va ganando;
 *   - y una pregunta de reglas sobre la subasta la sigue respondiendo la base.
 */

import { Buffer } from 'node:buffer';

import { test, expect, request as apiRequest } from '@playwright/test';

import { abrirAsistente, preguntar } from './ayudantes/asistente.js';
import { sesionDe as sesionDelBanco } from './ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const CLAVE = 'Contrasena-E2E-2026';
const BANCO = { id: 'e2e-banco', secreto: 'e2e-secreto-del-banco-de-pruebas' };
const PRODUCTO_SUBASTABLE = 'dddddddd-0000-0000-0000-00000000000a';
const NOMBRE = 'Hacha subastable de prueba';
const SUFIJO = Date.now().toString(36);

function conToken(token) {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

async function tokenDeServicio(api) {
  const r = await api.post('/api/v1/auth/token', {
    headers: {
      Authorization: `Basic ${Buffer.from(`${BANCO.id}:${BANCO.secreto}`).toString('base64')}`,
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    data: 'grant_type=client_credentials',
  });
  expect(r.status(), `token de servicio: ${await r.text()}`).toBe(200);
  return (await r.json()).access_token;
}

/** El alta (500 créditos, héroe y arma) terminó: con eso paga la comisión o la puja. */
async function esperarElAlta(api, quien) {
  await expect
    .poll(
      async () => {
        const r = await api.get('/api/v1/auth/onboarding', { headers: conToken(quien.token) });
        return r.ok() ? (await r.json()).estado : `HTTP ${r.status()}`;
      },
      { timeout: 60_000, intervals: [1_000], message: 'el alta no terminó' },
    )
    .toBe('COMPLETO');
}

/** Lo que hace una persona: correo, contraseña y «Entrar». */
async function entrarPorLaVista(page, quien) {
  await page.goto(`${BORDE}/login`);
  await page.fill('#email', quien.email);
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

/** Las tarjetas de una respuesta del asistente: título y texto. */
async function tarjetasDe(respuesta) {
  return respuesta.locator('.chatbot-enriquecido__tarjeta').evaluateAll((tarjetas) =>
    tarjetas.map((t) => ({
      titulo: t.querySelector('.chatbot-enriquecido__tarjeta-titulo')?.textContent?.trim() ?? '',
      texto: t.textContent?.trim() ?? '',
    })),
  );
}

test.describe('Asistente · la subasta (§7.4.4)', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let vendedora;
  let postor;
  let subastaId;

  test.beforeAll(async () => {
    test.setTimeout(240_000);
    api = await apiRequest.newContext({ baseURL: BORDE });
    const servicio = await tokenDeServicio(api);
    vendedora = await sesionDelBanco(api, `chat_vende_${SUFIJO}`, { clave: CLAVE, base: BORDE });
    postor = await sesionDelBanco(api, `chat_puja_${SUFIJO}`, { clave: CLAVE, base: BORDE });
    await esperarElAlta(api, vendedora);
    await esperarElAlta(api, postor);

    const clave = `chat-subasta-entrega-${SUFIJO}`;
    const entrega = await api.post('/api/v1/inventario/entregas', {
      headers: { ...conToken(servicio), 'Idempotency-Key': clave },
      data: {
        uid: vendedora.claims.uid,
        origen: 'ADMINISTRACION',
        referencia: clave,
        productos: [{ productoId: PRODUCTO_SUBASTABLE, cantidad: 1 }],
      },
    });
    expect(entrega.status(), await entrega.text()).toBe(201);
    const publicar = await api.post('/api/v1/subastas', {
      headers: {
        ...conToken(vendedora.token),
        'Idempotency-Key': `chat-subasta-publicar-${SUFIJO}`,
      },
      data: {
        elementoInventarioId: (await entrega.json()).elementos[0].id,
        productoId: PRODUCTO_SUBASTABLE,
        duracion: '24H',
        precioInicial: 100,
        precioCompraInmediata: 1000,
      },
    });
    expect(publicar.status(), await publicar.text()).toBe(201);
    subastaId = (await publicar.json()).id;
    const puja = await api.post(`/api/v1/subastas/${subastaId}/pujas`, {
      headers: { ...conToken(postor.token), 'Idempotency-Key': `chat-subasta-puja-${SUFIJO}` },
      data: { monto: 100 },
    });
    expect(puja.status(), await puja.text()).toBe(201);
  });

  test.afterAll(async () => {
    await api?.dispose();
  });

  test('un visitante ve lo que hay ahora en la subasta y lo de un producto, sin nada del vendedor', async ({
    page,
  }) => {
    test.setTimeout(120_000);
    await page.goto(`${BORDE}/login`);
    await abrirAsistente(page);

    const ahora = await preguntar(page, '¿Qué hay ahora en la subasta?');
    await expect(ahora).toContainText(
      /Hay \d+ subasta\(s\) abierta\(s\) ahora\. Las que cierran antes:/,
    );
    expect((await tarjetasDe(ahora)).length).toBeGreaterThan(0);
    await expect(
      ahora.locator('a.chatbot-enriquecido__enlace[data-destino="subastas"]'),
    ).toBeVisible();

    const hachas = await preguntar(page, '¿Hay hachas en subasta?');
    await expect(hachas).toContainText('subasta(s) abierta(s) de «hachas»');
    const deLaHacha = (await tarjetasDe(hachas)).filter((t) => t.titulo === NOMBRE);
    expect(deLaHacha.length, 'la subasta de la vendedora sale por su producto').toBeGreaterThan(0);
    expect(
      deLaHacha.some((t) =>
        t.texto.includes('Oferta vigente: 100 créditos · 1 puja(s) · cierra el'),
      ),
    ).toBe(true);
    expect(deLaHacha.some((t) => t.texto.includes('compra inmediata: 1000 créditos'))).toBe(true);
    // Del listado público: ni el apodo ni el uid de quien vende.
    await expect(hachas).not.toContainText(vendedora.apodo);
    await expect(hachas).not.toContainText(vendedora.claims.uid);

    // Una pregunta de reglas sobre la subasta la sigue respondiendo la base.
    const reglas = await preguntar(page, '¿Cuánto cuesta publicar una subasta?');
    await expect(reglas).not.toContainText('subasta(s) abierta(s)');
    await expect(reglas).toContainText('crédito');
  });

  test('la vendedora y el postor ven cada una de sus subastas abiertas', async ({ page }) => {
    test.setTimeout(150_000);
    await entrarPorLaVista(page, vendedora);
    await abrirAsistente(page);
    const suyas = await preguntar(page, '¿Cómo van mis subastas?');
    await expect(suyas).toContainText('Vas ganando 0 subasta(s)');
    await expect(suyas).toContainText('tienes 1 publicada(s)');
    const publicada = (await tarjetasDe(suyas)).find((t) => t.titulo === NOMBRE);
    expect(publicada?.texto).toContain(
      'Tu subasta: oferta vigente 100 créditos · 1 puja(s) · cierra el',
    );

    await page.context().clearCookies();
    await page.evaluate(() => {
      sessionStorage.clear();
      localStorage.clear();
    });
    await entrarPorLaVista(page, postor);
    await abrirAsistente(page);
    const delPostor = await preguntar(page, '¿Cómo van mis subastas?');
    await expect(delPostor).toContainText(
      'Vas ganando 1 subasta(s) en este momento, con 100 créditos retenidos',
    );
    const ganando = (await tarjetasDe(delPostor)).find((t) => t.titulo === NOMBRE);
    expect(ganando?.texto).toContain('Vas ganando con 100 créditos · 1 puja(s) · cierra el');
  });
});
