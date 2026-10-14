// @ts-check
/**
 * Soporte humano desde el asistente (§7.4.3 y §7.4.6), de punta a punta: lo
 * que el asistente no resuelve llega a una persona, sin ningún secreto.
 *
 * El recorrido de la revisión final del 7.4: una jugadora pregunta algo que
 * el asistente no entiende (y en la pregunta va su contraseña) → la respuesta
 * le ofrece alternativas y, desde ms-chatbot.yaml 1.12.0 (RF-CHA-007), le dice
 * que ya abrió la solicitud con esa conversación, con «Ver solicitud» → la
 * contraseña no llega a ninguna parte (ni a la conversación, ni a la
 * solicitud, ni a su contexto) → la solicitud persiste → desde
 * ms-chatbot.yaml 1.14.0 (§7.4.6) le adjunta una captura de pantalla con el
 * clip de la ventana, que se guarda sin metadatos y nadie más ve → el
 * administrador la ve en el panel del asistente, con su captura, la lee y la
 * responde → la jugadora ve la respuesta en su ventana.
 */

import { Buffer } from 'node:buffer';
import { randomFillSync } from 'node:crypto';
import { deflateSync } from 'node:zlib';

import { test, expect, request as apiRequest } from '@playwright/test';

import { abrirAsistente, preguntar } from './ayudantes/asistente.js';
import { sesionDe as sesionDelBanco } from './ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const CLAVE = 'Contrasena-E2E-2026';
const ADMIN = process.env.E2E_ADMIN ?? 'admin_e2e';
const SUFIJO = Date.now().toString(36);
/** Un secreto que no puede aparecer en ningún sitio. */
const SECRETO = `Zafiro${SUFIJO}!`;
// Algo que el asistente no sabe responder (ningún tema habla de constelaciones).
const PREGUNTA = `mi contraseña es ${SECRETO} y la constelación Quetzal ${SUFIJO} desapareció`;
/** Como queda guardada: la contraseña entera tapada, también su «!» final (1.11.0). */
const PREGUNTA_REDACTADA = `mi contraseña es *** y la constelación Quetzal ${SUFIJO} desapareció`;
const RESPUESTA_DE_SOPORTE = `Revisamos tu cuenta (${SUFIJO}): tu héroe está en «Mi inventario».`;
// SA-7: la consola del asistente por su dirección limpia (la ruta del fichero redirige aquí).
const VISTA_PANEL = '/consola/asistente';

function conToken(token) {
  return { Authorization: `Bearer ${token}` };
}

// --- un PNG de verdad, hecho aquí (1.14.0) -------------------------------

const TABLA_CRC = Array.from({ length: 256 }, (_, n) => {
  let c = n;
  for (let k = 0; k < 8; k += 1) {
    c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  }
  return c >>> 0;
});

function crc32(datos) {
  let c = 0xffffffff;
  for (const byte of datos) {
    c = TABLA_CRC[(c ^ byte) & 0xff] ^ (c >>> 8);
  }
  return (c ^ 0xffffffff) >>> 0;
}

function bloque(tipo, datos) {
  const largo = Buffer.alloc(4);
  largo.writeUInt32BE(datos.length);
  const tipoYDatos = Buffer.concat([Buffer.from(tipo, 'latin1'), datos]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(tipoYDatos));
  return Buffer.concat([largo, tipoYDatos, crc]);
}

/**
 * Un PNG RGB de `ancho`×`alto`: de un color o de ruido (que casi no se
 * comprime, para probar el límite de 2 MB), con un bloque de texto si se pide.
 */
function pngDePrueba({ ancho, alto, ruido = false, texto = null }) {
  const cabecera = Buffer.alloc(13);
  cabecera.writeUInt32BE(ancho, 0);
  cabecera.writeUInt32BE(alto, 4);
  cabecera[8] = 8;
  cabecera[9] = 2;
  const filas = [];
  for (let y = 0; y < alto; y += 1) {
    const fila = Buffer.alloc(1 + ancho * 3, 0x3f);
    fila[0] = 0;
    if (ruido) {
      randomFillSync(fila, 1);
    }
    filas.push(fila);
  }
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    bloque('IHDR', cabecera),
    ...(texto ? [bloque('tEXt', Buffer.from(`Comment\0${texto}`, 'latin1'))] : []),
    bloque('IDAT', deflateSync(Buffer.concat(filas))),
    bloque('IEND', Buffer.alloc(0)),
  ]);
}

function comoArchivo(buffer) {
  return { archivo: { name: 'captura', mimeType: 'image/png', buffer } };
}

/** La sesión que deja login.js (comun/sesion.js) en una página nueva. */
async function conSesion(page, sesion, rol) {
  await page.addInitScript(
    ([token, apodo, uid, elRol]) => {
      sessionStorage.setItem('nexus.token', token);
      sessionStorage.setItem('nexus.apodoActual', apodo);
      sessionStorage.setItem('nexus.rolActual', elRol);
      sessionStorage.setItem('nexus.usuarioId', uid);
    },
    [sesion.token, sesion.apodo, sesion.claims.uid, rol],
  );
}

test.describe('Asistente · soporte humano (§7.4.3, §7.4.6)', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let jugadora;
  let ticket;

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    jugadora = await sesionDelBanco(api, `chat_sop_${SUFIJO}`, { clave: CLAVE, base: BORDE });
  });

  test.afterAll(async () => {
    await api?.dispose();
  });

  test('sin resolver: alternativas, la solicitud abierta sola y ningún secreto guardado', async ({
    page,
  }) => {
    test.setTimeout(120_000);
    await conSesion(page, jugadora, 'JUGADOR');
    await page.goto(`${BORDE}/inicio`);
    await abrirAsistente(page);

    const respuesta = await preguntar(page, PREGUNTA);
    // El dato sensible se tapó y se lo dice.
    await expect(respuesta).toContainText('Por tu seguridad tapé un dato sensible');
    // No lo entendió: alternativas y soporte humano.
    await expect(respuesta).toContainText('No estoy seguro de haber entendido');
    await expect(respuesta.locator('[data-accion="respuesta-rapida"]').first()).toBeVisible();
    // En la conversación, la pregunta se ve ya tapada al recargar.
    await page.reload();
    const recargado = await abrirAsistente(page);
    await expect(recargado.preguntas.last()).toContainText(PREGUNTA_REDACTADA);
    await expect(recargado.ventana).not.toContainText(SECRETO);

    // 1.12.0: la solicitud ya está abierta; la respuesta lo dice y lleva a ella.
    const ultima = recargado.respuestas.last();
    await expect(ultima).toContainText('Abrí una solicitud de soporte con esta conversación');
    const aviso = ultima.locator('.chatbot-enriquecido__solicitud');
    await expect(aviso).toContainText('Sin respuesta del asistente');
    await expect(aviso).not.toContainText(SECRETO);
    await expect(ultima.locator('[data-accion="ofrecer-soporte"]')).toHaveCount(0);
    const id = await aviso.getAttribute('data-solicitud-id');
    expect(id).toMatch(/^[0-9a-f-]{36}$/);
    ticket = { ticketId: id };

    await aviso.locator('[data-accion="ver-solicitud"]').click();
    const panel = page.locator('.chatbot-soporte');
    await expect(panel).toBeVisible();
    // Ya abierta: no se ofrece crear otra con la conversación.
    await expect(panel.locator('[data-accion="crear-solicitud-desde-conversacion"]')).toHaveCount(
      0,
    );
    const fila = panel.locator(`.chatbot-soporte__ticket[data-ticket-id="${id}"]`);
    await expect(fila).toContainText('Sin respuesta del asistente');
    await expect(fila).toContainText('Abierta');

    // Persistida, y redactada en todas sus partes.
    const mias = await api.get('/api/v1/chat/tickets', { headers: conToken(jugadora.token) });
    expect(mias.status()).toBe(200);
    const texto = JSON.stringify(await mias.json());
    expect(texto).toContain('Sin respuesta del asistente');
    expect(texto).toContain(PREGUNTA_REDACTADA);
    expect(texto).not.toContain(SECRETO);
  });

  // ms-chatbot.yaml 1.14.0 (§7.4.6, RF-CHA-002): el clip de la ventana lleva a
  // su solicitud abierta; la captura llega sin metadatos y nadie más la ve.
  test('adjunta una captura con el clip: sin metadatos, hasta 2 MB y solo para ella', async ({
    page,
  }) => {
    test.setTimeout(120_000);
    const id = ticket?.ticketId;
    expect(id, 'la solicitud de la prueba anterior').toBeTruthy();
    await conSesion(page, jugadora, 'JUGADOR');
    await page.goto(`${BORDE}/inicio`);
    const asistente = await abrirAsistente(page);

    await asistente.captura.click();
    const solicitud = page.locator(`.chatbot-soporte__ticket[data-ticket-id="${id}"]`);
    const entrada = solicitud.locator('input[type="file"]');
    await expect(entrada).toBeFocused({ timeout: 20_000 });
    // El aviso, antes de elegir.
    await expect(solicitud).toContainText('no se vean contraseñas, códigos ni datos de tarjetas');
    await entrada.setInputFiles({
      name: `pantallazo de ${SUFIJO}.png`,
      mimeType: 'image/png',
      buffer: pngDePrueba({ ancho: 320, alto: 180, texto: SECRETO }),
    });

    const ver = solicitud.getByRole('button', { name: 'Ver captura 1' });
    await expect(ver).toBeFocused({ timeout: 20_000 });
    await ver.click();
    const imagen = solicitud.locator('img');
    await expect(imagen).toBeVisible();
    expect(await imagen.evaluate((img) => img.naturalWidth)).toBe(320);

    // Lo guardado: sin el texto que llevaba, servido como imagen y sin caché.
    const mias = await (
      await api.get('/api/v1/chat/tickets', { headers: conToken(jugadora.token) })
    ).json();
    const [captura] = mias.find((t) => t.ticketId === id).capturas;
    expect(captura).toMatchObject({ tipo: 'image/png', ancho: 320, alto: 180 });
    const ruta = `/api/v1/chat/tickets/${id}/capturas/${captura.capturaId}`;
    const contenido = await api.get(ruta, { headers: conToken(jugadora.token) });
    expect(contenido.status()).toBe(200);
    expect(contenido.headers()['content-type']).toBe('image/png');
    expect(contenido.headers()['cache-control']).toBe('no-store, private');
    const bytes = (await contenido.body()).toString('latin1');
    expect(bytes).not.toContain(SECRETO);
    expect(bytes).not.toContain('tEXt');

    // Por el borde hasta 2 MB: una de casi 1,5 MB entra y una de 2,5 MB no.
    const mediana = await api.post(`/api/v1/chat/tickets/${id}/capturas`, {
      headers: conToken(jugadora.token),
      multipart: comoArchivo(pngDePrueba({ ancho: 700, alto: 700, ruido: true })),
    });
    expect(mediana.status(), await mediana.text()).toBe(201);
    const grande = await api.post(`/api/v1/chat/tickets/${id}/capturas`, {
      headers: conToken(jugadora.token),
      multipart: comoArchivo(pngDePrueba({ ancho: 950, alto: 900, ruido: true })),
    });
    expect(grande.status()).toBe(413);
    expect((await grande.json()).motivo).toBe('CAPTURA_DEMASIADO_GRANDE');

    // Otra jugadora: ni la ve ni adjunta a la solicitud ajena; como si no existiera.
    const otra = await sesionDelBanco(api, `chat_sop_otra_${SUFIJO}`, {
      clave: CLAVE,
      base: BORDE,
    });
    const ajena = await api.get(ruta, { headers: conToken(otra.token) });
    expect(ajena.status()).toBe(404);
    const subirAjena = await api.post(`/api/v1/chat/tickets/${id}/capturas`, {
      headers: conToken(otra.token),
      multipart: comoArchivo(pngDePrueba({ ancho: 8, alto: 8 })),
    });
    expect(subirAjena.status()).toBe(404);
    expect((await subirAjena.json()).motivo).toBe('SOLICITUD_NO_ENCONTRADA');
  });

  test('el administrador la ve en el panel, sin el secreto, y la responde', async ({ page }) => {
    test.setTimeout(120_000);
    expect(ticket?.ticketId ?? ticket?.id, 'la solicitud de la prueba anterior').toBeTruthy();
    const id = ticket.ticketId ?? ticket.id;
    const admin = await sesionDelBanco(api, ADMIN, { clave: CLAVE, base: BORDE });
    expect(admin.claims.rol).toBe('ADMINISTRADOR');

    // Por la API: la solicitud y su conversación, sin el secreto.
    const detalle = await api.get(`/api/v1/chatbot/admin/tickets/${id}`, {
      headers: conToken(admin.token),
    });
    expect(detalle.status(), await detalle.text()).toBe(200);
    const cuerpo = JSON.stringify(await detalle.json());
    expect(cuerpo).toContain('***');
    expect(cuerpo).not.toContain(SECRETO);

    // Por el panel.
    await conSesion(page, admin, 'ADMINISTRADOR');
    await page.goto(`${BORDE}${VISTA_PANEL}#soporte`);
    const abrir = page.locator(`[data-accion="abrir-ticket"][data-ticket="${id}"]`);
    await expect(abrir).toBeVisible({ timeout: 30_000 });
    await abrir.click();
    const dialogo = page.locator('[role="dialog"]');
    await expect(dialogo).toContainText(PREGUNTA_REDACTADA);
    await expect(dialogo).not.toContainText(SECRETO);
    // 1.14.0: las capturas que adjuntó, para leerlas al atenderla.
    await expect(dialogo).toContainText('Capturas de pantalla');
    await expect(dialogo.locator('[data-accion="ver-captura"]')).toHaveCount(2);
    await dialogo.getByRole('button', { name: 'Ver captura 1' }).click();
    const captura = dialogo.locator('.capturas-chatbot__imagen img');
    await expect(captura).toBeVisible();
    expect(await captura.evaluate((img) => img.naturalWidth)).toBe(320);
    await dialogo.locator('select[name="estado"]').selectOption('RESUELTO');
    await dialogo.locator('textarea[name="respuesta"]').fill(RESPUESTA_DE_SOPORTE);
    const guardado = page.waitForResponse(
      (r) =>
        r.url().includes(`/api/v1/chatbot/admin/tickets/${id}`) && r.request().method() === 'PATCH',
    );
    await dialogo.locator('[data-accion="guardar-ticket"]').click();
    expect((await guardado).status()).toBe(200);
    await expect(dialogo).toBeHidden();
  });

  test('la jugadora ve la respuesta de soporte en su ventana', async ({ page }) => {
    await conSesion(page, jugadora, 'JUGADOR');
    await page.goto(`${BORDE}/inicio`);
    const asistente = await abrirAsistente(page);
    await asistente.soporte.click();
    const solicitud = page
      .locator('.chatbot-soporte .chatbot-soporte__ticket')
      .filter({ hasText: 'Sin respuesta del asistente' })
      .first();
    await expect(solicitud).toContainText('Respondida', { timeout: 20_000 });
    await expect(solicitud.locator('.chatbot-soporte__respuesta')).toContainText(
      RESPUESTA_DE_SOPORTE,
    );
  });
});
