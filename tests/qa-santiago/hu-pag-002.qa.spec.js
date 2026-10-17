// HU-PAG-002 · Registro de transacciones en moneda real (Santiago Sanabria)
import { test } from '@playwright/test';
import {
  CORRIDA, TARJETAS, VISTA, aceptarDialogo, comprar, datosQa, entrar, esperar, expect, foto, fotoHttp, fotoTexto, llamar, sesion, sql, tokenServicio,
} from './ayudante-qa.js';

const datos = datosQa();
const COMPRADOR = `txqa${CORRIDA}`;
const FINANZAS = 'http://localhost:8093/api/v1';

test.describe('HU-PAG-002 · Registro de transacciones en moneda real', () => {
  let s;
  let aprobada;
  let rechazada;

  test.beforeAll(async ({ request }) => {
    s = await sesion(request, COMPRADOR);
    const previas = sql('finanzas', `SELECT count(*) FROM finanzas.transacciones WHERE uid_usuario='${s.claims.uid}';`);
    if (previas === '0') {
      aprobada = (await comprar(request, s, datos.productosTienda[6].id)).cuerpo;
      rechazada = (await comprar(request, s, datos.productosTienda[7].id, TARJETAS.rechazada)).cuerpo;
      await llamar(request, 'DELETE', `/api/v1/carrito/items/${(await llamar(request, 'GET', '/api/v1/carrito', { token: s.token })).cuerpo.items?.[0]?.id}`, { token: s.token });
    }
  });

  test('CP-PAG-002-01 · Cada transacción se persiste con referencia, monto, moneda, usuario, concepto y resultado', async ({ page, request }) => {
    const filas = sql('finanzas', `SELECT ref_id||' | '||monto||' '||moneda||' | usuario='||uid_usuario||' | '||concepto||' | '||resultado||' | ref.pasarela='||coalesce(pasarela_ref_externa,'—') FROM finanzas.transacciones WHERE uid_usuario='${s.claims.uid}' ORDER BY creado;`);
    await page.goto('about:blank');
    await fotoTexto(page, 'CP-PAG-002-01', 'Registro persistido de las dos compras (aprobada y rechazada) del jugador', 'finanzas.transacciones', filas);
    expect(filas).toMatch(/APROBADO/);
    expect(filas).toMatch(/RECHAZADO/);
    const ordenes = await llamar(request, 'GET', '/api/v1/ordenes', { token: s.token });
    await fotoHttp(page, 'CP-PAG-002-01', 'El comprobante (orden) al que apunta la referencia de cada transacción', ordenes, { nota: 'La referencia (ref_id) de la transacción es el id de la orden de compra: es el comprobante que el jugador ve en «Mis compras».' });
    const refs = filas.split('\n').map((l) => l.split(' | ')[0]);
    for (const o of ordenes.cuerpo) expect(refs).toContain(o.id);
  });

  test('CP-PAG-002-02 · El usuario consulta su historial de transacciones desde Mi Cuenta', async ({ page, request }) => {
    await entrar(page, COMPRADOR);
    await page.goto('/cuenta#historial');
    const enlace = page.getByRole('link', { name: /pagos en dinero real/i });
    await expect(enlace).toBeVisible();
    await foto(page, 'CP-PAG-002-02', 'Mi Cuenta › Historial: enlace «Ver pagos en dinero real»', { resaltar: enlace });
    await enlace.click();
    const tabla = page.locator('#historial-tbody');
    await expect(tabla.locator('tr')).toHaveCount(2);
    await expect(tabla).toContainText(/Aprobad/i);
    await expect(tabla).toContainText(/Rechazad/i);
    await foto(page, 'CP-PAG-002-02', 'Historial de transacciones: monto en COP, concepto, resultado y fecha de cada pago', { resaltar: tabla });
    const otro = await sesion(request, 'jugador_qa');
    const ajeno = await llamar(request, 'GET', '/api/v1/transacciones/mi-historial', { token: otro.token });
    await fotoHttp(page, 'CP-PAG-002-02', 'Otro jugador consulta SU historial: no ve las transacciones del comprador', ajeno);
    expect(JSON.stringify(ajeno.cuerpo)).not.toContain(s.claims.uid);
  });

  test('CP-PAG-002-03 · Una transacción indeterminada queda marcada para conciliación manual', async ({ page, request }) => {
    const servicio = await tokenServicio(request);
    const ref = `FALLO-PASARELA-${CORRIDA}-${Date.now()}`;
    const r = await llamar(request, 'POST', `${FINANZAS}/pagos/procesar`, {
      token: servicio,
      data: { uidUsuario: s.claims.uid, monto: 54000, moneda: 'COP', concepto: 'Inscripción al torneo de otoño', refId: ref },
    });
    await page.goto('about:blank');
    await fotoHttp(page, 'CP-PAG-002-03', 'La pasarela simulada no responde (3 intentos): el pago queda INDETERMINADO', r, { nota: 'Se fuerza la caída de la pasarela con la referencia de prueba FALLO-PASARELA-*. El servicio reintenta 3 veces y registra la transacción como indeterminada.' });
    expect(r.cuerpo.estado).toBe('INDETERMINADO');
    expect(r.cuerpo.mensaje).toMatch(/conciliaci/i);
    await entrar(page, COMPRADOR);
    await page.goto(VISTA('cuentas/historial-transacciones.html'));
    const fila = page.locator('#historial-tbody tr').filter({ hasText: 'torneo de otoño' });
    await expect(fila).toBeVisible();
    await foto(page, 'CP-PAG-002-03', 'En el historial del jugador la transacción aparece como indeterminada / en conciliación', { resaltar: fila });
  });

  test('CP-PAG-002-04 · Los registros financieros se conservan aunque el usuario ejerza el derecho al olvido', async ({ page, request }) => {
    const apodo = `olvido${CORRIDA}`;
    const o = await sesion(request, apodo);
    await comprar(request, o, datos.productosTienda[8].id);
    await entrar(page, apodo);
    await page.goto('/cuenta#privacidad');
    await page.locator('[data-accion="solicitar-cierre"]').click();
    await page.locator('#passwordActualCierre').fill('Contrasena-E2E-2026');
    await page.locator('[data-accion="confirmar-cierre"]').click();
    const dialogo = page.getByRole('dialog');
    if (await dialogo.isVisible().catch(() => false)) await aceptarDialogo(page);
    await expect(page.locator('[data-zona="estado-cierre"]')).toContainText(/programad|30 días/i);
    await foto(page, 'CP-PAG-002-04', 'El jugador pide cerrar su cuenta (derecho al olvido): queda programado a 30 días', { resaltar: page.locator('[data-zona="cierre-cuenta"]') });
    // Precondición acelerada: la solicitud se adelanta 31 días para que el ejecutor la procese ya.
    sql('identidad', `UPDATE solicitudes_cierre_cuenta SET solicitada_en = now() - interval '31 days', programada_para = now() - interval '1 day' WHERE usuario_uid='${o.claims.uid}' AND estado='PROGRAMADA';`);
    let estado = '';
    for (let i = 0; i < 30 && estado !== 'EJECUTADA'; i += 1) {
      await esperar(2000);
      estado = sql('identidad', `SELECT estado FROM solicitudes_cierre_cuenta WHERE usuario_uid='${o.claims.uid}' ORDER BY solicitada_en DESC LIMIT 1;`);
    }
    const cuenta = sql('identidad', `SELECT 'apodo='||apodo||' | email='||email||' | estado='||estado FROM usuarios WHERE public_id='${o.claims.uid}';`);
    const finanzas = sql('finanzas', `SELECT ref_id||' | '||monto||' '||moneda||' | '||concepto||' | '||resultado FROM finanzas.transacciones WHERE uid_usuario='${o.claims.uid}';`);
    await fotoTexto(page, 'CP-PAG-002-04', 'Tras ejecutarse el cierre: la cuenta queda anonimizada pero la transacción financiera se conserva', `solicitud=${estado}`, `ms-identidad · usuarios:\n${cuenta}\n\nms-finanzas · transacciones del mismo uid:\n${finanzas}`);
    expect(estado).toBe('EJECUTADA');
    expect(finanzas).toMatch(/APROBADO/);
  });

  test('CP-PAG-002-05 · Cada transacción queda asociada a su comprobante en el historial', async ({ page }) => {
    await entrar(page, COMPRADOR);
    await page.goto(VISTA('cuentas/historial-transacciones.html'));
    const aprobada = page.locator('#historial-tbody tr').filter({ hasText: /APROBADO/i }).first();
    await expect(aprobada).toBeVisible();
    const celda = aprobada.locator('td').last();
    await foto(page, 'CP-PAG-002-05', 'Columna «Comprobante» de la transacción aprobada', { resaltar: celda });
    const comprobante = sql('finanzas', `SELECT coalesce(comprobante_url,'(null)') FROM finanzas.transacciones WHERE uid_usuario='${s.claims.uid}' AND resultado='APROBADO' LIMIT 1;`);
    await fotoTexto(page, 'CP-PAG-002-05', 'Valor persistido del comprobante de la transacción', 'finanzas.transacciones.comprobante_url', comprobante);
    await expect(celda.getByRole('link'), 'la transacción debe enlazar su comprobante').toBeVisible({ timeout: 3000 });
  });
});
