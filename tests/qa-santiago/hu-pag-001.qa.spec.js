// HU-PAG-001 · Integración con pasarela de pagos simulada (Juan Diego)
import { test } from '@playwright/test';
import tls from 'node:tls';
import { execSync } from 'node:child_process';
import { correosPara, leerCorreo } from '../e2e/ayudantes/correo.js';
import {
  CORRIDA, FINANZAS_URL, TARJETAS, VISTA, contenedor, datosQa, entrar, esperar, esperarSano, expect, foto, fotoHttp, fotoTexto, llamar, sesion, sql, tokenServicio,
} from './ayudante-qa.js';

const datos = datosQa();
const DOMINIO = 'nexusbattlesvi.com';

async function alCarrito(api, s, productoId) {
  return llamar(api, 'POST', '/api/v1/carrito/items', { token: s.token, data: { productoId, cantidad: 1 } });
}

async function pagarEnLaVista(page, tarjeta) {
  await page.goto(VISTA('cuentas/tienda.html'));
  const pagar = page.locator('#btn-pagar');
  await expect(pagar).toBeEnabled({ timeout: 20_000 });
  await pagar.click();
  const dialogo = page.getByRole('dialog', { name: 'Pagar tu compra' });
  await dialogo.getByLabel('Nombre del titular de la tarjeta').fill(tarjeta.titular);
  await dialogo.getByLabel('Número de tarjeta').fill(tarjeta.numeroTarjeta);
  await dialogo.getByLabel('Fecha de vencimiento (MM/AA)').fill(tarjeta.vencimiento);
  await dialogo.getByLabel('Código de seguridad').fill(tarjeta.codigoSeguridad);
  const cobro = page.waitForResponse((r) => r.url().includes('/api/v1/checkout') && r.request().method() === 'POST');
  await dialogo.locator('[data-accion="confirmar-pago"]').click();
  const r = await cobro;
  await page.waitForTimeout(1200);
  return { dialogo, respuesta: r };
}

function certificado(host) {
  return new Promise((resolver, rechazar) => {
    const s = tls.connect({ host, port: 443, servername: host, timeout: 8000 }, () => {
      const c = s.getPeerCertificate();
      resolver({ protocolo: s.getProtocol(), cifrado: s.getCipher().name, sujeto: c.subject?.CN, emisor: `${c.issuer?.O ?? ''} ${c.issuer?.CN ?? ''}`.trim(), validoDesde: c.valid_from, validoHasta: c.valid_to, autorizado: s.authorized });
      s.end();
    });
    s.on('error', rechazar);
    s.on('timeout', () => rechazar(new Error('timeout')));
  });
}

test.describe('HU-PAG-001 · Integración con pasarela de pagos simulada', () => {
  test('CP-PAG-001-01 · El pago se procesa por la pasarela simulada y la compra solo se consuma si se aprueba', async ({ page, request }) => {
    const apodo = `pasarela${CORRIDA}`;
    const s = await sesion(request, apodo);
    const producto = datos.productosTienda[11];
    await alCarrito(request, s, producto.id);
    await entrar(page, apodo);
    const rechazo = await pagarEnLaVista(page, TARJETAS.rechazada);
    const avisoRechazo = rechazo.dialogo.getByText('Pago rechazado').locator('..');
    await expect(avisoRechazo).toBeVisible();
    await foto(page, 'CP-PAG-001-01', `Tarjeta de prueba •••• 0002: la pasarela RECHAZA (HTTP ${rechazo.respuesta.status()}); la compra no se consuma y se borran número y CVV`, { resaltar: avisoRechazo });
    expect(rechazo.respuesta.status()).toBe(402);
    const inv1 = await llamar(request, 'GET', '/api/v1/inventario/elementos?pagina=0', { token: s.token });
    expect(JSON.stringify(inv1.cuerpo)).not.toContain(producto.id);
    await page.keyboard.press('Escape');
    const aprobado = await pagarEnLaVista(page, TARJETAS.aprobada);
    await expect(aprobado.dialogo.locator('.pago__resultado')).toContainText('Compra completada');
    await foto(page, 'CP-PAG-001-01', `Tarjeta de prueba •••• 4242: pasarela APRUEBA (HTTP ${aprobado.respuesta.status()}) → «Compra completada»`, { resaltar: aprobado.dialogo.locator('.pago__resultado') });
    const inv2 = await llamar(request, 'GET', '/api/v1/inventario/elementos?pagina=0', { token: s.token });
    expect(JSON.stringify(inv2.cuerpo)).toContain(producto.id);
    const ordenes = sql('ecommerce', `SELECT estado||' | '||total||' '||moneda||' | ref_pasarela='||coalesce(referencia_pasarela,'—')||' | tarjeta='||coalesce(medio_marca,'—') FROM ordenes WHERE usuario_id='${s.claims.uid}' ORDER BY reservada_en NULLS FIRST;`);
    await fotoTexto(page, 'CP-PAG-001-01', 'Órdenes del jugador: la rechazada no entregó nada; la aprobada lleva la referencia de la pasarela simulada', 'ms-ecommerce.ordenes', ordenes);
  });

  test('CP-PAG-001-02 · La transacción viaja por un canal cifrado (HTTPS en el entorno desplegado)', async ({ page }) => {
    let cert;
    let redireccion = '';
    try {
      cert = await certificado(DOMINIO);
      const r = await fetch(`http://${DOMINIO}/cuenta`, { redirect: 'manual' });
      redireccion = `${r.status} -> ${r.headers.get('location')}`;
    } catch (e) {
      test.skip(true, `sin acceso a ${DOMINIO}: ${e.message}`);
    }
    await page.goto('about:blank');
    await fotoTexto(page, 'CP-PAG-001-02', `TLS del borde desplegado (${DOMINIO}): todo el tráfico del navegador, incluido el formulario de pago, va cifrado`, `openssl/tls · ${DOMINIO}:443`,
      `Protocolo: ${cert.protocolo}\nCifrado: ${cert.cifrado}\nCertificado para: ${cert.sujeto}\nEmitido por: ${cert.emisor}\nVigencia: ${cert.validoDesde} → ${cert.validoHasta}\nCadena válida: ${cert.autorizado}\n\nhttp://${DOMINIO}/cuenta  →  ${redireccion}`);
    expect(cert.autorizado).toBe(true);
    expect(redireccion).toMatch(/^30[18] -> https:/);
  });

  test('CP-PAG-001-03 · Ante falla transitoria se reintenta y, si la pasarela no responde, queda para conciliación', async ({ page, request }) => {
    const servicio = await tokenServicio(request);
    const ref = `FALLO-PASARELA-reintento-${CORRIDA}-${Date.now()}`;
    const s = await sesion(request, 'comprador_qa');
    const r = await llamar(request, 'POST', `${FINANZAS_URL}/pagos/procesar`, { token: servicio, data: { uidUsuario: s.claims.uid, monto: 75000, moneda: 'COP', concepto: 'Inscripción a torneo (prueba de pasarela caída)', refId: ref } });
    await esperar(1000);
    const bitacora = execSync('docker compose -p nexus-e2e logs --since 2m srv-ms-finanzas', { encoding: 'utf8' }).split('\n').filter((l) => l.includes(ref)).map((l) => l.replace(/^.*"message":"([^"]+)".*$/, '$1')).join('\n');
    await page.goto('about:blank');
    await fotoHttp(page, 'CP-PAG-001-03', 'Pasarela simulada sin respuesta: el servicio devuelve INDETERMINADO y lo marca para conciliación manual', r);
    await fotoTexto(page, 'CP-PAG-001-03', 'Bitácora de ms-finanzas: 3 intentos controlados antes de marcar la transacción', 'docker logs srv-ms-finanzas', bitacora || '(sin líneas)');
    expect(r.cuerpo.estado).toBe('INDETERMINADO');
    expect((bitacora.match(/Intento \d\/3/g) ?? []).length).toBeGreaterThanOrEqual(2);

    const apodo = `caida${CORRIDA}`;
    const c = await sesion(request, apodo);
    await alCarrito(request, c, datos.productosTienda[12].id);
    await entrar(page, apodo);
    const caida = await pagarEnLaVista(page, TARJETAS.caida);
    await foto(page, 'CP-PAG-001-03', `En la tienda, con la pasarela caída (tarjeta de prueba •••• 0069): HTTP ${caida.respuesta.status()}, «no se cobró nada», la orden queda PENDIENTE`, { resaltar: caida.dialogo });
    expect(caida.respuesta.status()).toBe(503);
  });

  test('CP-PAG-001-04 · Al aprobarse se envía el correo de confirmación; si el correo falla se encola sin afectar la compra', async ({ page, request }) => {
    test.setTimeout(300_000);
    const apodo = `confirma${CORRIDA}`;
    const s = await sesion(request, apodo);
    await alCarrito(request, s, datos.productosTienda[13].id);
    contenedor('stop', 'srv-correo');
    let orden;
    try {
      const pago = await llamar(request, 'POST', '/api/v1/checkout', { token: s.token, headers: { 'Idempotency-Key': `qa-conf-${CORRIDA}` }, data: { ...TARJETAS.aprobada, moneda: 'COP' } });
      orden = pago.cuerpo;
      await page.goto('about:blank');
      await fotoHttp(page, 'CP-PAG-001-04', 'Compra aprobada con el servicio de correo DETENIDO: el producto se entrega y el correo queda pendiente en la cola', pago);
      expect(pago.estado).toBe(201);
      expect(['COMPLETA', 'ENTREGADA']).toContain(orden.estado);
    } finally {
      contenedor('start', 'srv-correo');
      await esperarSano(request, 'http://localhost:8099/salud-borde');
    }
    let correo = null;
    for (let i = 0; i < 60 && !correo; i += 1) {
      await esperar(2000);
      for (const m of await correosPara(`${apodo}@nexus.test`)) {
        const d = await leerCorreo(m.ID);
        if (/compra|pedido|orden/i.test(d.Subject ?? '')) correo = d;
      }
    }
    const estado = sql('ecommerce', `SELECT 'correo='||correo||' | enviado_en='||coalesce(to_char(correo_en,'HH24:MI:SS'),'—') FROM ordenes WHERE id='${orden.id}';`);
    await fotoTexto(page, 'CP-PAG-001-04', 'Al volver el servicio de correo, la cola reintenta y el jugador recibe la confirmación con el detalle', `Asunto: ${correo?.Subject ?? '(no llegó)'} · ${estado}`, (correo?.Text ?? '').slice(0, 1500));
    expect(correo).not.toBeNull();
  });

  test('CP-PAG-001-05 · Si el monto supera el umbral de alto valor se exige verificación de doble factor', async ({ page, request }) => {
    const apodo = `altovalor${CORRIDA}`;
    const s = await sesion(request, apodo);
    for (const p of datos.productosTienda.slice(14, 18)) await alCarrito(request, s, p.id);
    await entrar(page, apodo);
    await page.goto(VISTA('cuentas/tienda.html'));
    const pagar = page.locator('#btn-pagar');
    await expect(pagar).toBeEnabled({ timeout: 20_000 });
    await pagar.click();
    const dialogo = page.getByRole('dialog', { name: 'Pagar tu compra' });
    await expect(dialogo).toBeVisible();
    await foto(page, 'CP-PAG-001-05', 'Compra de alto valor (4 productos): el formulario de pago no pide segundo factor', { resaltar: dialogo });
    await expect(page.getByText(/segundo factor|verificaci[oó]n en dos pasos|c[oó]digo de 6 d[ií]gitos/i), 'debe exigirse el segundo factor antes de pagar un monto de alto valor').toBeVisible({ timeout: 3000 });
  });
});
