// HU-SUB-004 · Pujar o comprar de forma inmediata en una subasta (Andrés Núñez)
import { test } from '@playwright/test';
import fs from 'node:fs';
import { CORRIDA, SALIDA, VISTA, aceptarDialogo, datosQa, entrar, esperar, expect, foto, fotoHttp, fotoTexto, llamar, sesion, sql, tokenServicio } from './ayudante-qa.js';

const datos = datosQa();
const FINANZAS = 'http://localhost:8093/api/v1';
const ESTADO = `${SALIDA}/estado-sub004-${CORRIDA}.json`;
const VENDEDOR = `vendsub${CORRIDA}`;

async function abrirDetalle(page, id) {
  await page.goto(VISTA('cuentas/pujas.html'));
  const abrir = page.locator(`[data-abrir="${id}"]`).first();
  await expect(abrir).toBeVisible();
  await abrir.click();
  await expect(page.locator('#input-monto-puja')).toBeVisible();
  await page.waitForTimeout(800);
}

async function pujarEnLaVista(page, monto) {
  await page.locator('#input-monto-puja').fill(String(monto));
  const resp = page.waitForResponse((r) => /\/pujas$/.test(new URL(r.url()).pathname) && r.request().method() === 'POST', { timeout: 15_000 }).catch(() => null);
  await page.locator('#btn-pujar-manual').click();
  const dialogo = page.getByRole('dialog');
  if (await dialogo.isVisible({ timeout: 1500 }).catch(() => false)) await aceptarDialogo(page);
  return resp;
}

const ficha = async (api, id) => (await llamar(api, 'GET', `/api/v1/subastas/${id}`)).cuerpo;
const reservado = async (api, s) => (await llamar(api, 'GET', `${FINANZAS}/creditos/${s.claims.uid}/saldo`, { token: s.token })).cuerpo;

test.describe('HU-SUB-004 · Pujar o comprar de forma inmediata en una subasta', () => {
  let A; // subasta para pujas manuales y automáticas
  let B; // subasta para compra inmediata
  let EB; // objeto de la subasta B

  test.beforeAll(async ({ request }) => {
    if (fs.existsSync(ESTADO)) {
      ({ A, B, EB } = JSON.parse(fs.readFileSync(ESTADO, 'utf8')));
      return;
    }
    const vendedor = await sesion(request, VENDEDOR);
    const servicio = await tokenServicio(request);
    await llamar(request, 'POST', `${FINANZAS}/creditos/acreditar`, { token: servicio, data: { uid: vendedor.claims.uid, monto: 50, refId: `qa-s4-com-${CORRIDA}`, concepto: 'semilla-qa' } });
    const objetos = [];
    for (const [k, prod] of [datos.subastables[3], datos.subastables[4]].entries()) {
      const e = await llamar(request, 'POST', '/api/v1/inventario/entregas', { token: servicio, headers: { 'Idempotency-Key': `qa-s4-${CORRIDA}-${k}` }, data: { uid: vendedor.claims.uid, origen: 'ADMINISTRACION', referencia: `qa-s4-${CORRIDA}-${k}`, productos: [{ productoId: prod.id, cantidad: 1 }] } });
      objetos.push({ elementoId: e.cuerpo.elementos[0].id, productoId: prod.id });
    }
    const [ea, eb] = objetos;
    const pa = await llamar(request, 'POST', '/api/v1/subastas', { token: vendedor.token, headers: { 'Idempotency-Key': `qa-s4a-${CORRIDA}` }, data: { elementoInventarioId: ea.elementoId, productoId: ea.productoId, duracion: '24H', precioInicial: 40, precioCompraInmediata: 400 } });
    const pb = await llamar(request, 'POST', '/api/v1/subastas', { token: vendedor.token, headers: { 'Idempotency-Key': `qa-s4b-${CORRIDA}` }, data: { elementoInventarioId: eb.elementoId, productoId: eb.productoId, duracion: '24H', precioInicial: 30, precioCompraInmediata: 90 } });
    expect(pa.estado, pa.texto).toBe(201);
    expect(pb.estado, pb.texto).toBe(201);
    A = pa.cuerpo.id;
    B = pb.cuerpo.id;
    EB = eb.elementoId;
    // Precondición: que cierren antes que el resto, para que salgan primeras en «Subastas en curso».
    sql('subastas', `UPDATE subastas SET fecha_fin = now() + interval '2 hours' WHERE id IN ('${A}','${B}');`);
    fs.writeFileSync(ESTADO, JSON.stringify({ A, B, EB }));
  });

  test('CP-SUB-004-01 · Una puja por debajo del mínimo se rechaza; la válida se acepta y reserva los créditos', async ({ page, request }) => {
    const c1 = await sesion(request, 'comprador_qa');
    await entrar(page, 'comprador_qa');
    await abrirDetalle(page, A);
    await foto(page, 'CP-SUB-004-01', 'Detalle de la subasta sin pujas: precio mínimo 40 cr (la primera puja debe ser ≥ 40)', { resaltar: page.locator('#input-monto-puja') });
    const baja = await pujarEnLaVista(page, 35);
    await page.waitForTimeout(1200);
    await foto(page, 'CP-SUB-004-01', `Dato 35 cr (< 40): la puja no se acepta ${baja ? `(HTTP ${baja.status()})` : '(la vista la bloquea antes de enviarla)'}`, { resaltar: page.locator('#input-monto-puja').locator('xpath=ancestor::div[1]') });
    expect(Number((await ficha(request, A)).cantidadPujas)).toBe(0);
    const antes = await reservado(request, c1);
    await page.waitForTimeout(5500);
    const buena = await pujarEnLaVista(page, 40);
    expect(buena?.status()).toBeLessThan(300);
    await expect.poll(async () => Number((await ficha(request, A)).ofertaVigente)).toBe(40);
    const despues = await reservado(request, c1);
    await page.reload();
    await abrirDetalle(page, A);
    await foto(page, 'CP-SUB-004-01', `Dato 40 cr: puja aceptada, «Vas ganando tú» y 40 cr retenidos (disponible ${antes.saldoDisponible} → ${despues.saldoDisponible})`, { completa: true });
    expect(Number(antes.saldoDisponible) - Number(despues.saldoDisponible)).toBe(40);
  });

  test('CP-SUB-004-02 · Incremento mínimo, intervalo de 5 s entre pujas del mismo jugador y prohibido pujar en la propia subasta', async ({ page, request }) => {
    const c2 = await sesion(request, 'comprador2_qa');
    const corta = await llamar(request, 'POST', `/api/v1/subastas/${A}/pujas`, { token: c2.token, data: { monto: 43 }, headers: { 'Idempotency-Key': `qa-inc-${Date.now()}` } });
    await page.goto('about:blank');
    await fotoHttp(page, 'CP-SUB-004-02', 'Oferta vigente 40 cr e incremento 5: una puja de 43 cr se rechaza', corta);
    expect(corta.estado).toBeGreaterThanOrEqual(400);
    await esperar(5500);
    const primera = await llamar(request, 'POST', `/api/v1/subastas/${A}/pujas`, { token: c2.token, data: { monto: 45 }, headers: { 'Idempotency-Key': `qa-i1-${Date.now()}` } });
    const segunda = await llamar(request, 'POST', `/api/v1/subastas/${A}/pujas`, { token: c2.token, data: { monto: 60 }, headers: { 'Idempotency-Key': `qa-i2-${Date.now()}` } });
    await fotoHttp(page, 'CP-SUB-004-02', 'Puja de 45 cr aceptada; otra del mismo jugador sin esperar 5 s se rechaza', [primera, segunda]);
    expect(primera.estado).toBeLessThan(300);
    expect(segunda.estado).toBeGreaterThanOrEqual(400);
    const vendedor = await sesion(request, VENDEDOR);
    const propia = await llamar(request, 'POST', `/api/v1/subastas/${A}/pujas`, { token: vendedor.token, data: { monto: 999 }, headers: { 'Idempotency-Key': `qa-p-${Date.now()}` } });
    await fotoHttp(page, 'CP-SUB-004-02', 'El vendedor intenta pujar en su propia subasta: rechazado', propia);
    expect(propia.estado).toBeGreaterThanOrEqual(400);
  });

  test('CP-SUB-004-03 · Los créditos del ofertante superado se restituyen automáticamente', async ({ page, request }) => {
    const c1 = await sesion(request, 'comprador_qa');
    const movimientos = await llamar(request, 'GET', `${FINANZAS}/creditos/${c1.claims.uid}/movimientos`, { token: c1.token });
    const lista = (movimientos.cuerpo.content ?? movimientos.cuerpo ?? []).filter((m) => JSON.stringify(m).includes(A));
    await page.goto('about:blank');
    await fotoHttp(page, 'CP-SUB-004-03', 'Movimientos de créditos de comprador_qa en la subasta: su reserva de 40 cr quedó LIBERADA al ser superado (puja de 45)', { ...movimientos, cuerpo: lista });
    expect(JSON.stringify(lista)).toMatch(/LIBERAD/);
    await entrar(page, 'comprador_qa');
    await page.goto('/cuenta#historial');
    const fila = page.getByRole('row').filter({ hasText: A.slice(0, 8) }).filter({ hasText: /devuelto|LIBERADA/i }).first();
    await expect(fila).toBeVisible();
    await foto(page, 'CP-SUB-004-03', 'Mi cuenta › Historial: «Puja en la subasta … 40 devueltos · LIBERADA»', { resaltar: fila });
  });

  test('CP-SUB-004-04 · La puja automática emite ofertas hasta el límite y se detiene avisando', async ({ page, request }) => {
    test.setTimeout(240_000);
    const c1 = await sesion(request, 'comprador_qa');
    const c2 = await sesion(request, 'comprador2_qa');
    await entrar(page, 'comprador_qa');
    await abrirDetalle(page, A);
    const vigente = Number((await ficha(request, A)).ofertaVigente);
    const limite = vigente + 30;
    const panel = page.locator('#input-limite-auto').locator('xpath=ancestor::div[2]');
    const campoLimite = page.locator('#input-limite-auto');
    await campoLimite.fill(String(limite));
    await page.locator('#btn-activar-auto').click();
    const dialogo = page.getByRole('dialog');
    if (await dialogo.isVisible({ timeout: 1500 }).catch(() => false)) await aceptarDialogo(page);
    await esperar(1500);
    await foto(page, 'CP-SUB-004-04', `Puja automática activada con límite ${limite} cr (oferta vigente ${vigente} cr)`, { resaltar: panel });
    await expect.poll(async () => (await ficha(request, A)).ofertaVigente, { timeout: 30_000 }).not.toBe(String(vigente));
    let rondas = 0;
    while (rondas < 6) {
      const f = await ficha(request, A);
      const minimo = Number(f.pujaMinimaSiguiente);
      if (minimo > limite + 10) break;
      await esperar(5600);
      await llamar(request, 'POST', `/api/v1/subastas/${A}/pujas`, { token: c2.token, data: { monto: minimo }, headers: { 'Idempotency-Key': `qa-auto-${Date.now()}` } });
      await esperar(4000);
      rondas += 1;
    }
    const final = await ficha(request, A);
    const historial = await llamar(request, 'GET', `/api/v1/subastas/${A}/pujas`, { token: c1.token });
    await page.goto('about:blank');
    await fotoHttp(page, 'CP-SUB-004-04', `Historial de pujas: las automáticas de comprador_qa suben hasta ${limite} cr y no lo superan; oferta final ${final.ofertaVigente} cr (otro jugador)`, historial);
    expect(Number(final.ofertaVigente)).toBeGreaterThan(limite);
    await entrar(page, 'comprador_qa');
    await page.goto(VISTA('plataforma/notificaciones/notificaciones.html'));
    await page.waitForTimeout(2000);
    const aviso = page.getByText('Tu puja automática se detuvo').first();
    await expect(aviso).toBeVisible();
    await foto(page, 'CP-SUB-004-04', 'Al alcanzarse el límite, la puja automática se detiene y el jugador recibe el aviso', { resaltar: aviso.locator('..') });
  });

  test('CP-SUB-004-05 · Compra inmediata con confirmación: transfiere el producto, cierra la subasta y avisa a quienes pujaron', async ({ page, request }) => {
    const c1 = await sesion(request, 'comprador_qa');
    await esperar(5500);
    const puja = await llamar(request, 'POST', `/api/v1/subastas/${B}/pujas`, { token: c1.token, data: { monto: 35 }, headers: { 'Idempotency-Key': `qa-b1-${CORRIDA}` } });
    expect(puja.estado).toBeLessThan(300);
    await entrar(page, 'comprador2_qa');
    await abrirDetalle(page, B);
    await page.locator('#btn-solicitar-compra').click();
    const dialogo = page.getByRole('dialog');
    await expect(dialogo).toBeVisible();
    await foto(page, 'CP-SUB-004-05', 'Al pulsar «Comprarla ya» se pide confirmación con el precio de compra inmediata (90 cr)', { resaltar: dialogo });
    const resp = page.waitForResponse((r) => r.url().includes('/compra-inmediata'));
    await aceptarDialogo(page);
    expect((await resp).status()).toBeLessThan(300);
    await page.waitForTimeout(1500);
    await foto(page, 'CP-SUB-004-05', 'Compra completada: la subasta se cierra', { completa: true });
    const estado = await ficha(request, B);
    expect(estado.estado).toBe('ADJUDICADA');
    const c2 = await sesion(request, 'comprador2_qa');
    // El inventario es paginado (16 por página): se recorren todas las páginas del comprador.
    let todo = '';
    for (let pagina = 0; pagina < 20; pagina += 1) {
      const inventario = await llamar(request, 'GET', `/api/v1/inventario/elementos?pagina=${pagina}`, { token: c2.token });
      todo += JSON.stringify(inventario.cuerpo);
      if (inventario.cuerpo?.ultima !== false) break;
    }
    const elemento = EB;
    expect(todo).toContain(elemento);
    await fotoTexto(page, 'CP-SUB-004-05', 'Estado de la subasta y del objeto tras la compra', 'ms-subastas / inventario', `subasta ${B}: estado=${estado.estado}, cerradaEn=${estado.cerradaEn}\nEl elemento ${elemento} ahora está en el inventario de comprador2_qa.`);
    await entrar(page, 'comprador_qa');
    await page.goto(VISTA('plataforma/notificaciones/notificaciones.html'));
    await page.waitForTimeout(2500);
    const aviso = page.getByText(/La subasta se cerró por una compra inmediata/).first();
    await expect(aviso).toBeVisible();
    await foto(page, 'CP-SUB-004-05', 'comprador_qa, que había pujado, recibe el aviso de que la subasta se cerró por compra inmediata', { resaltar: aviso.locator('..') });
  });

  test('CP-SUB-004-06 · Límites de participación configurables: máx. 10 subastas activas y máx. pujas activas', async ({ page, request }) => {
    const vendedor1 = await sesion(request, 'vendedor1_qa');
    const activas = sql('subastas', `SELECT count(*) FROM subastas WHERE apodo_vendedor='vendedor1_qa' AND estado='ACTIVA';`);
    const libre = datos.elementos.vendedor1_qa.at(-1);
    const once = await llamar(request, 'POST', '/api/v1/subastas', { token: vendedor1.token, headers: { 'Idempotency-Key': `qa-11-${Date.now()}` }, data: { elementoInventarioId: libre.elementoId, productoId: libre.productoId, duracion: '24H', precioInicial: 10 } });
    await page.goto('about:blank');
    await fotoHttp(page, 'CP-SUB-004-06', `vendedor1_qa ya tiene ${activas} subastas activas: la publicación número 11 se rechaza`, once);
    expect(once.estado).toBeGreaterThanOrEqual(400);

    await entrar(page, 'superadmin_qa');
    await page.goto(VISTA('plataforma/admin-parametros/parametros-admin.html'));
    const fila = page.locator('[data-zona="catalogo"] > *').filter({ hasText: 'subastas.max-pujas-activas-por-jugador' }).first();
    await expect(fila).toBeVisible();
    await fila.locator('[name="valor"]').fill('1');
    await fila.locator('[name="motivo"]').fill('Prueba QA del límite de pujas activas');
    await fila.locator('[data-accion="guardar"]').click();
    await expect(fila).toContainText(/Vigente: 1 /);
    await foto(page, 'CP-SUB-004-06', 'Administración › Parámetros: el Super Administrador cambia «máximo de pujas activas por jugador» de 50 a 1', { resaltar: fila });
    try {
      await esperar(2500);
      const c1 = await sesion(request, 'comprador_qa');
      const otra = datos.subastas.find((s) => s.vendedor === 'vendedor1_qa');
      const f = await ficha(request, otra.id);
      const r = await llamar(request, 'POST', `/api/v1/subastas/${otra.id}/pujas`, { token: c1.token, data: { monto: Number(f.pujaMinimaSiguiente) }, headers: { 'Idempotency-Key': `qa-lim-${Date.now()}` } });
      await fotoHttp(page, 'CP-SUB-004-06', 'Con el límite en 1 y pujas activas en otras subastas, una nueva puja se rechaza por el límite configurado', r);
      expect(r.estado).toBeGreaterThanOrEqual(400);
      const reglas = await llamar(request, 'GET', '/api/v1/subastas/reglas');
      await fotoHttp(page, 'CP-SUB-004-06', 'Las reglas vigentes publicadas por ms-subastas leen los 4 límites desde administración', reglas);
    } finally {
      await fila.locator('[name="valor"]').fill('50');
      await fila.locator('[name="motivo"]').fill('Restablecer el valor del documento tras la prueba QA');
      await fila.locator('[data-accion="guardar"]').click();
      await expect(fila).toContainText(/Vigente: 50 /);
    }
  });
});
