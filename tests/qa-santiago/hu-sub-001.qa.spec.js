// HU-SUB-001 · Publicar un producto en subasta (Edwin Pedraza)
import { test } from '@playwright/test';
import fs from 'node:fs';
import { CORRIDA, RAIZ, VISTA, datosQa, entrar, entregarObjetos, esperar, expect, foto, fotoHttp, fotoTexto, junitGradle, llamar, moverCreditos, saldoDe, sesion, sql } from './ayudante-qa.js';

const datos = datosQa();
const VENDEDOR = `pubqa${CORRIDA}`;

async function abrirFormulario(page) {
  await page.goto(VISTA('cuentas/publicar-subasta.html'));
  await expect(page.locator('#producto')).toBeVisible();
  await page.waitForTimeout(1200);
}

async function publicarEnLaVista(page, { elemento, duracion, inicial, inmediata }) {
  await page.locator('#producto').selectOption(elemento);
  await page.locator(`input[name="duracion"][value="${duracion}"]`).check({ force: true });
  await page.locator('#inicial').fill(String(inicial));
  if (inmediata) await page.locator('#inmediata').fill(String(inmediata));
  await page.locator('#aceptar').check();
  const resp = page.waitForResponse((r) => new URL(r.url()).pathname === '/api/v1/subastas' && r.request().method() === 'POST', { timeout: 15_000 }).catch(() => null);
  await page.getByRole('button', { name: 'Confirmar y publicar' }).click();
  return resp;
}

test.describe('HU-SUB-001 · Publicar un producto en subasta', () => {
  let s;
  let objetos;

  test.beforeAll(async ({ request }) => {
    s = await sesion(request, VENDEDOR);
    const ya = sql('subastas', `SELECT count(*) FROM subastas WHERE apodo_vendedor='${VENDEDOR}';`);
    objetos = await entregarObjetos(request, s, [datos.subastables[5].id, datos.subastables[6].id, datos.subastables[7].id], `qa-pub-${CORRIDA}`);
    if (ya === '0') await moverCreditos(request, s, 20, `qa-pub-cr-${CORRIDA}`);
  });

  test('CP-SUB-001-01 · Modalidad extendida (48 h): la comisión de 3 créditos se cobra al publicar y el objeto queda bloqueado', async ({ page, request }) => {
    const antes = await saldoDe(request, s);
    await entrar(page, VENDEDOR);
    await abrirFormulario(page);
    await page.locator('#producto').selectOption(objetos[0]);
    await page.locator('input[name="duracion"][value="48H"]').check({ force: true });
    await page.locator('#inicial').fill('50');
    await page.locator('#inmediata').fill('150');
    await page.locator('#aceptar').check();
    await foto(page, 'CP-SUB-001-01', 'Datos: producto de su inventario, duración 48 h (comisión 3 créditos), precio mínimo 50, compra inmediata 150', { completa: true });
    const resp = page.waitForResponse((r) => new URL(r.url()).pathname === '/api/v1/subastas' && r.request().method() === 'POST');
    await page.getByRole('button', { name: 'Confirmar y publicar' }).click();
    const r = await resp;
    expect(r.status()).toBe(201);
    await page.waitForTimeout(1500);
    await foto(page, 'CP-SUB-001-01', 'Resultado: subasta publicada');
    const despues = await saldoDe(request, s);
    const sub = (await r.json()).id;
    const fila = sql('subastas', `SELECT 'duracion='||round(extract(epoch from (fecha_fin - fecha_publicacion at time zone 'UTC'))/3600)||'h | comision='||comision_cobrada||' | estado='||estado FROM subastas WHERE id='${sub}';`);
    const inv = await llamar(request, 'GET', '/api/v1/inventario/elementos?pagina=0', { token: s.token });
    const el = (inv.cuerpo.elementos ?? []).find((e) => e.id === objetos[0]);
    await fotoTexto(page, 'CP-SUB-001-01', 'Comisión cobrada al publicar y objeto bloqueado en el inventario mientras dura la subasta', 'ms-subastas / ms-finanzas / inventario',
      `subasta ${sub}: ${fila}\nsaldo disponible: ${antes.saldoDisponible} → ${despues.saldoDisponible}\nobjeto ${objetos[0]}: disponible=${el?.disponible} subastaId=${el?.subastaId}`);
    expect(Number(antes.saldoDisponible) - Number(despues.saldoDisponible)).toBe(3);
    expect(el?.disponible).toBe(false);
    await page.goto('/inventario');
    await page.waitForTimeout(2500);
    await foto(page, 'CP-SUB-001-01', 'Mi inventario: el objeto en subasta aparece bloqueado / no disponible', { completa: true });
  });

  test('CP-SUB-001-02 · Modalidad corta (24 h) con comisión de 1 crédito', async ({ page, request }) => {
    const antes = await saldoDe(request, s);
    await entrar(page, VENDEDOR);
    await abrirFormulario(page);
    const opciones = page.locator('input[name="duracion"]').locator('xpath=ancestor::label[1]');
    await foto(page, 'CP-SUB-001-02', 'Las 2 modalidades del formulario: 24 horas (comisión 1 crédito) y 48 horas (comisión 3 créditos)', { resaltar: opciones });
    const r = await publicarEnLaVista(page, { elemento: objetos[1], duracion: '24H', inicial: 30 });
    expect(r?.status()).toBe(201);
    const despues = await saldoDe(request, s);
    await page.waitForTimeout(1200);
    await foto(page, 'CP-SUB-001-02', `Publicada a 24 h: saldo ${antes.saldoDisponible} → ${despues.saldoDisponible} (comisión de 1 crédito)`);
    expect(Number(antes.saldoDisponible) - Number(despues.saldoDisponible)).toBe(1);
  });

  test('CP-SUB-001-03 · Condiciones de publicación: autenticado, dueño del objeto, objeto no en uso, créditos suficientes y sin sanciones', async ({ page, request }) => {
    const sinSesion = await llamar(request, 'POST', '/api/v1/subastas', { data: { elementoInventarioId: objetos[2], productoId: datos.subastables[7].id, duracion: '24H', precioInicial: 10 } });
    const ajeno = await sesion(request, 'comprador_qa');
    const noEsSuyo = await llamar(request, 'POST', '/api/v1/subastas', { token: ajeno.token, headers: { 'Idempotency-Key': `qa-ajeno-${Date.now()}` }, data: { elementoInventarioId: objetos[2], productoId: datos.subastables[7].id, duracion: '24H', precioInicial: 10 } });
    await page.goto('about:blank');
    await fotoHttp(page, 'CP-SUB-001-03', 'Sin sesión (401) y publicando un objeto que no es suyo: rechazadas', [sinSesion, noEsSuyo]);
    expect(sinSesion.estado).toBe(401);
    expect(noEsSuyo.estado).toBeGreaterThanOrEqual(400);

    const inv = await llamar(request, 'GET', '/api/v1/inventario/elementos?pagina=0', { token: s.token });
    const enUso = (inv.cuerpo.elementos ?? []).find((e) => e.tipo === 'ARMA' && e.productoId === 'p-arma-e2e');
    const usado = await llamar(request, 'POST', '/api/v1/subastas', { token: s.token, headers: { 'Idempotency-Key': `qa-uso-${Date.now()}` }, data: { elementoInventarioId: enUso.id, productoId: enUso.productoId, duracion: '24H', precioInicial: 10 } });
    await entrar(page, VENDEDOR);
    await abrirFormulario(page);
    await foto(page, 'CP-SUB-001-03', 'En el formulario, los objetos en uso (héroe y arma equipada) aparecen deshabilitados', { resaltar: page.locator('#producto') });
    await fotoHttp(page, 'CP-SUB-001-03', 'Forzando por la API la publicación del arma equipada: rechazada por estar en uso', usado);
    expect(usado.estado).toBeGreaterThanOrEqual(400);

    const pobre = await sesion(request, `sinsaldo${CORRIDA}`);
    const [objPobre] = await entregarObjetos(request, pobre, datos.subastables[8].id, `qa-pobre-${CORRIDA}`);
    const saldo = await saldoDe(request, pobre);
    if (Number(saldo.saldoDisponible) > 0) await moverCreditos(request, pobre, Number(saldo.saldoDisponible), `qa-vaciar-${CORRIDA}`, 'debitar');
    await entrar(page, pobre.apodo);
    await abrirFormulario(page);
    const r = await publicarEnLaVista(page, { elemento: objPobre, duracion: '48H', inicial: 20 });
    await page.waitForTimeout(1200);
    await foto(page, 'CP-SUB-001-03', `Jugador con 0 créditos intenta publicar (comisión 3): se rechaza${r ? ` (HTTP ${r.status()})` : ''} con el motivo`, { completa: true });
    expect(r === null || r.status() >= 400).toBe(true);

    const sancionado = await sesion(request, `sancpub${CORRIDA}`);
    const [objSanc] = await entregarObjetos(request, sancionado, datos.subastables[9].id, `qa-sanc-${CORRIDA}`);
    await moverCreditos(request, sancionado, 10, `qa-sanc-cr-${CORRIDA}`);
    const admin = await sesion(request, 'admin_e2e');
    const sancion = await llamar(request, 'POST', '/api/v1/sanciones', { token: admin.token, data: { usuarioId: sancionado.claims.uid, tipo: 'SUSPENSION', motivo: 'Conducta antideportiva (prueba QA)', duracionHoras: 2 } });
    const conSancion = await llamar(request, 'POST', '/api/v1/subastas', { token: sancionado.token, headers: { 'Idempotency-Key': `qa-sanc-${Date.now()}` }, data: { elementoInventarioId: objSanc, productoId: datos.subastables[9].id, duracion: '24H', precioInicial: 10 } });
    await fotoHttp(page, 'CP-SUB-001-03', 'Cuenta con una sanción activa (suspensión emitida por el administrador): no puede publicar', [sancion, conSancion]);
    expect(sancion.estado).toBeLessThan(300);
    expect(conSancion.estado).toBeGreaterThanOrEqual(400);
  });

  test('CP-SUB-001-04 · La comisión no se reembolsa aunque la subasta termine sin venderse', async ({ page, request }) => {
    test.setTimeout(240_000);
    const sub = sql('subastas', `SELECT id FROM subastas WHERE apodo_vendedor='${VENDEDOR}' AND estado='ACTIVA' AND cantidad_pujas=0 ORDER BY fecha_publicacion DESC LIMIT 1;`);
    const antes = await saldoDe(request, s);
    // Precondición acelerada: la subasta vence ya (el job de cierre corre cada 30 s).
    sql('subastas', `UPDATE subastas SET fecha_fin = now() - interval '1 minute' WHERE id='${sub}';`);
    let estado = 'ACTIVA';
    for (let i = 0; i < 40 && estado === 'ACTIVA'; i += 1) {
      await esperar(3000);
      estado = sql('subastas', `SELECT estado FROM subastas WHERE id='${sub}';`);
    }
    const despues = await saldoDe(request, s);
    const comision = sql('subastas', `SELECT comision_cobrada FROM subastas WHERE id='${sub}';`);
    await page.goto('about:blank');
    await fotoTexto(page, 'CP-SUB-001-04', 'Subasta vencida sin pujas: cierra SIN_ADJUDICACION y la comisión no se devuelve', 'ms-subastas / ms-finanzas', `subasta ${sub}: estado=${estado} · comision_cobrada=${comision}\nsaldo disponible del vendedor: ${antes.saldoDisponible} → ${despues.saldoDisponible} (sin reembolso)`);
    expect(estado).toBe('SIN_ADJUDICACION');
    expect(despues.saldoDisponible).toBe(antes.saldoDisponible);
  });

  test('CP-SUB-001-05 · El Maestro de Juego está exento de comisión', async ({ page }) => {
    test.setTimeout(600_000);
    const r = junitGradle(':services:cuentas:ms-subastas', 'PublicarSubastaApplicationServiceTest');
    const dir = `${RAIZ}/services/cuentas/ms-subastas/build.nosync/test-results/test`;
    const f = fs.readdirSync(dir).find((x) => x.includes('PublicarSubastaApplicationServiceTest'));
    const reporte = f ? fs.readFileSync(`${dir}/${f}`, 'utf8') : '(sin reporte)';
    const resumen = (reporte.match(/<testsuite [^>]+>/) ?? [''])[0].replace(/ timestamp="[^"]+"| hostname="[^"]+"/g, '');
    const casos = [...reporte.matchAll(/<testcase name="([^"]+)"/g)].map((m) => `✔ ${m[1]}`).join('\n');
    await page.goto('about:blank');
    await fotoTexto(page, 'CP-SUB-001-05', 'Pruebas unitarias del servicio de publicación (incluye «maestroExento»: comisión 0 para el Maestro de Juego)', 'gradlew :services:cuentas:ms-subastas:test --tests *PublicarSubastaApplicationServiceTest', `${resumen}\n\n${casos}`,
      { nota: 'ms-identidad aún no emite el rol «Maestro de Juego» (HU-SUB-010, fuera del alcance), por lo que la exención se verifica en la regla de negocio con su prueba unitaria.' });
    expect(r.estado, r.salida.slice(-2000)).toBe(0);
    expect(resumen).toMatch(/failures="0"/);
    expect(casos).toContain('maestroExento');
  });
});
