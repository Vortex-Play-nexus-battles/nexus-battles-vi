// HU-RBAC-004 · Verificación de autorización en el servidor (Andrés Núñez)
import { test } from '@playwright/test';
import { VISTA, contenedor, datosQa, entrar, esperar, esperarSano, expect, foto, fotoHtml, fotoHttp, fotoTexto, llamar, sesion, sql } from './ayudante-qa.js';

const FINANZAS = 'http://localhost:8093/api/v1';
const datos = datosQa();

/** Mismo token con el rol del cuerpo cambiado a SUPER_ADMINISTRADOR: la firma ya no corresponde. */
function manipulado(token) {
  const [cab, cuerpo, firma] = token.split('.');
  const datos = JSON.parse(Buffer.from(cuerpo.replace(/-/g, '+').replace(/_/g, '/'), 'base64').toString());
  datos.rol = 'SUPER_ADMINISTRADOR';
  datos.roles = ['SUPER_ADMINISTRADOR'];
  return `${cab}.${Buffer.from(JSON.stringify(datos)).toString('base64url')}.${firma}`;
}

const OBJETIVOS = [
  ['ms-identidad', 'GET', '/api/v1/admin/usuarios/1'],
  ['ms-cumplimiento', 'GET', '/api/v1/admin/auditoria'],
  ['ms-finanzas', 'POST', `${FINANZAS}/creditos/acreditar`, { uid: '00000000-0000-0000-0000-000000000000', monto: 999, refId: 'bypass', concepto: 'bypass' }],
  ['ms-subastas', 'POST', `/api/v1/subastas/${datos.subastas[0].id}/cancelacion`, {}],
  ['ms-ecommerce', 'POST', '/api/v1/productos', { nombre: 'x' }],
  ['ms-chatbot', 'GET', '/api/v1/chatbot/admin/analiticas'],
];

test.describe('HU-RBAC-004 · Verificación de autorización en el servidor', () => {
  test('CP-RBAC-004-01 · Cada microservicio valida la credencial y el permiso antes de ejecutar', async ({ page, request }) => {
    const jugador = await sesion(request, 'jugador_qa');
    const filas = [];
    let fallos = 0;
    for (const [servicio, metodo, url, data] of OBJETIVOS) {
      const sin = await llamar(request, metodo, url, { data });
      const falso = await llamar(request, metodo, url, { data, token: manipulado(jugador.token) });
      const ajeno = await llamar(request, metodo, url, { data, token: jugador.token });
      const ok = [sin, falso].every((r) => r.estado === 401 || r.estado === 403) && [401, 403].includes(ajeno.estado);
      if (!ok) fallos += 1;
      const c = (r) => `<td class="${r.estado === 401 || r.estado === 403 ? 'ok' : 'ko'}">${r.estado}</td>`;
      filas.push(`<tr><td>${servicio}</td><td>${metodo} ${url.replace(FINANZAS, '/api/v1')}</td>${c(sin)}${c(falso)}${c(ajeno)}</tr>`);
    }
    await page.goto('about:blank');
    await fotoHtml(page, 'CP-RBAC-004-01', `Peticiones directas a operaciones protegidas de 6 microservicios: ${OBJETIVOS.length - fallos}/${OBJETIVOS.length} rechazadas en el servidor`,
      `<table><tr><th>Servicio</th><th>Operación</th><th>Sin token</th><th>Token manipulado (rol cambiado)</th><th>Token válido de JUGADOR</th></tr>${filas.join('')}</table>`,
      { nota: 'Se omite la interfaz y se llama a cada API como lo haría alguien que manipula el navegador. Esperado: 401 (sin credencial o firma inválida) o 403 (sin permiso).' });
    expect(fallos).toBe(0);
  });

  test('CP-RBAC-004-02 · Manipular la interfaz no salta la autorización: el servidor rechaza con mensaje genérico', async ({ page, request }) => {
    await entrar(page, 'admin_e2e');
    const id = sql('identidad', `SELECT id FROM usuarios WHERE apodo='jugador_qa';`);
    await page.goto(VISTA(`cuentas/gestion-usuarios.html?usuario=${id}`));
    await expect(page.locator('#panel-usuario')).toBeVisible();
    const boton = page.locator('#btn-cambiar-rol');
    await foto(page, 'CP-RBAC-004-02', 'ADMINISTRADOR: el control «Cambiar rol» está bloqueado en la interfaz (rol reservado al Super Administrador)', { resaltar: page.locator('#nuevo-rol').locator('..') });
    await page.evaluate(() => {
      for (const el of document.querySelectorAll('#btn-cambiar-rol, #nuevo-rol, [data-has-permission="ASIGNAR_ROL"], [data-has-permission="ASIGNAR_ROL"] *')) {
        el.hidden = false; el.disabled = false; el.removeAttribute('disabled'); el.removeAttribute('aria-disabled'); el.style.display = '';
      }
    });
    await page.locator('#nuevo-rol').selectOption('SUPER_ADMINISTRADOR').catch(() => {});
    const respuesta = page.waitForResponse((r) => /\/rol$/.test(r.url()), { timeout: 15_000 }).catch(() => null);
    await boton.click({ force: true });
    const dialogo = page.getByRole('dialog');
    if (await dialogo.isVisible().catch(() => false)) await dialogo.getByRole('button').last().click();
    const r = await respuesta;
    const mensaje = page.locator('#mensaje-cambio-rol, .aviso--error, [role="alert"]').filter({ hasText: /permiso|denegad|no autoriz/i }).first();
    await expect(mensaje).toBeVisible();
    await foto(page, 'CP-RBAC-004-02', 'Tras habilitar el botón con las herramientas del navegador, la vista responde «No tienes permiso para cambiar roles.»', { resaltar: mensaje });
    // El manipulador va más allá: llama a la API desde la consola del navegador con su propia sesión.
    const directo = await page.evaluate(async (usuario) => {
      const token = sessionStorage.getItem('nexus.token');
      const resp = await fetch(`/api/v1/rbac/usuarios/${usuario}/rol`, { method: 'PUT', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` }, body: JSON.stringify({ nuevoRol: 'SUPER_ADMINISTRADOR' }) });
      return { estado: resp.status, cuerpo: await resp.text() };
    }, id);
    let cuerpo = directo.cuerpo; try { cuerpo = JSON.parse(cuerpo); } catch { /* texto */ }
    await fotoHttp(page, 'CP-RBAC-004-02', 'Llamada directa desde la consola del navegador con la sesión del ADMINISTRADOR: el servidor la rechaza', { metodo: 'PUT', url: `/api/v1/rbac/usuarios/${id}/rol`, estado: directo.estado, cuerpo, enviado: { cabeceras: { Authorization: 'Bearer (sesión del navegador)' }, cuerpo: { nuevoRol: 'SUPER_ADMINISTRADOR' } } });
    expect(directo.estado).toBe(403);
    expect(r === null || r.status() === 403).toBe(true);
    const rol = sql('identidad', `SELECT r.nombre FROM usuarios u JOIN roles r ON r.id=u.rol_id WHERE u.id=${id};`);
    expect(rol).toBe('JUGADOR');
  });

  test('CP-RBAC-004-03 · Si la autorización no se puede comprobar, la operación se deniega (falla cerrado)', async ({ page, request }) => {
    test.setTimeout(300_000);
    await entrar(page, 'admin_e2e');
    await page.route('**/api/v1/rbac/matrix', (ruta) => ruta.fulfill({ status: 503, body: '' }));
    await page.goto(VISTA('cuentas/gestion-usuarios.html'));
    const bloqueo = page.locator('[data-zona="titulo-bloqueo"], [data-zona="detalle-bloqueo"]').first();
    await expect(bloqueo).toBeVisible();
    await foto(page, 'CP-RBAC-004-03', 'Con el servicio de la matriz RBAC respondiendo 503, la vista NO se abre: falla cerrada', { resaltar: bloqueo });
    await page.unroute('**/api/v1/rbac/matrix');

    const superadmin = await sesion(request, 'superadmin_qa', { nueva: true });
    contenedor('stop', 'srv-ms-identidad');
    contenedor('restart', 'srv-ms-cumplimiento');
    await esperar(25_000);
    let r;
    try {
      r = await llamar(request, 'GET', '/api/v1/admin/auditoria', { token: superadmin.token });
      await fotoHttp(page, 'CP-RBAC-004-03', 'ms-identidad (emisor de llaves) caído y ms-cumplimiento sin llaves en caché: un token válido NO entra', r, { nota: 'Se detuvo srv-ms-identidad y se reinició srv-ms-cumplimiento: no puede verificar la firma del token, así que deniega en lugar de ejecutar.' });
    } finally {
      contenedor('start', 'srv-ms-identidad');
      await esperarSano(request, '/api/v1/auth/jwks');
      contenedor('restart', 'srv-ms-cumplimiento');
      await esperar(20_000);
    }
    expect(r.estado).toBeGreaterThanOrEqual(401);
    expect(r.estado).not.toBe(200);
  });

  test('CP-RBAC-004-04 · Un intento de acceso no autorizado queda registrado', async ({ page, request }) => {
    const jugador = await sesion(request, 'jugador_qa', { nueva: true });
    const r = await llamar(request, 'POST', '/api/v1/admin/ban', { token: jugador.token, data: { usuarioId: 1, motivo: 'intento indebido QA' } });
    await page.goto('about:blank');
    await fotoHttp(page, 'CP-RBAC-004-04', 'Un JUGADOR intenta banear a otra cuenta llamando directo a la API: 403', r);
    expect(r.estado).toBe(403);
    let filas = '';
    for (let i = 0; i < 10 && !filas; i += 1) {
      await esperar(1000);
      filas = sql('cumplimiento', `SELECT to_char(fecha_hora,'HH24:MI:SS')||' | '||tipo_accion||' | admin='||administrador_id||' | afectado='||afectado||' | motivo='||motivo||' | ip='||ip_origen FROM audit_log WHERE tipo_accion ILIKE '%BYPASS%' OR motivo ILIKE '%NO_AUTORIZ%' OR motivo ILIKE '%BANEAR%' ORDER BY fecha_hora DESC LIMIT 3;`);
    }
    await fotoTexto(page, 'CP-RBAC-004-04', 'Asiento del intento no autorizado en el registro de auditoría', 'audit_log (ms-cumplimiento)', filas || '(sin filas)');
    expect(filas, 'el intento no autorizado debe quedar en la auditoría').toMatch(/BYPASS|NO_AUTORIZ/i);
  });
});
