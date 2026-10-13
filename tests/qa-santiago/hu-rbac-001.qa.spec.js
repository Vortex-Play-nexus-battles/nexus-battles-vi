// HU-RBAC-001 · Modelo y matriz de permisos por rol (Andrés Núñez)
import { test } from '@playwright/test';
import { execSync, spawnSync } from 'node:child_process';
import fs from 'node:fs';
import { RAIZ, VISTA, entrar, expect, foto, fotoHtml, fotoHttp, fotoTexto, llamar, sesion, sql } from './ayudante-qa.js';

const ROLES = ['JUGADOR', 'MODERADOR', 'ADMINISTRADOR', 'SUPER_ADMINISTRADOR'];
// Tabla 24 del documento del cliente (§7.3.1): Sí / No / Temporal.
const TABLA_24 = {
  CREAR_CUENTA_JUGADOR: ['Sí', 'No', 'No', 'No'],
  MODIFICAR_PERFIL_PROPIO: ['Sí', 'Sí', 'Sí', 'Sí'],
  PUBLICAR_COMENTARIOS: ['Sí', 'Sí', 'Sí', 'Sí'],
  ELIMINAR_COMENTARIO_PROPIO: ['Sí', 'Sí', 'Sí', 'Sí'],
  MODERAR_COMENTARIOS: ['No', 'Sí', 'Sí', 'Sí'],
  EMITIR_ADVERTENCIAS: ['No', 'Sí', 'Sí', 'Sí'],
  SUSPENDER_USUARIOS: ['No', 'Temporal', 'Sí', 'Sí'],
  BANEAR_DEFINITIVAMENTE: ['No', 'No', 'Sí', 'Sí'],
  CREAR_ADMIN_MODERADOR: ['No', 'No', 'No', 'Sí'],
  GESTIONAR_PRODUCTOS: ['No', 'No', 'Sí', 'Sí'],
};
const A_SERVIDOR = { Sí: 'GRANTED', No: 'DENIED', Temporal: 'TEMPORARY' };

test.describe('HU-RBAC-001 · Modelo y matriz de permisos por rol', () => {
  test('CP-RBAC-001-01 · Cada cuenta tiene exactamente uno de los 4 roles; un rol no reconocido recibe el mínimo privilegio', async ({ page, request }) => {
    const admin = await sesion(request, 'superadmin_qa');
    const roles = await llamar(request, 'GET', '/api/v1/rbac/roles', { token: admin.token });
    expect(roles.cuerpo.map((r) => r.role)).toEqual(ROLES);
    const conteo = sql('identidad', `SELECT r.nombre||': '||count(u.id) FROM roles r LEFT JOIN usuarios u ON u.rol_id=r.id GROUP BY r.nombre, r.id ORDER BY r.id;`);
    const sinRol = sql('identidad', `SELECT count(*) FROM usuarios WHERE rol_id IS NULL;`);
    await page.goto('about:blank');
    await fotoHttp(page, 'CP-RBAC-001-01', 'Catálogo de roles del sistema: Jugador, Moderador, Administrador y Super Administrador', roles);
    await fotoTexto(page, 'CP-RBAC-001-01', 'Cada cuenta referencia exactamente un rol (columna rol_id NOT NULL, clave foránea)', 'usuarios por rol (ms-identidad)', `${conteo}\n\ncuentas sin rol: ${sinRol}`);
    expect(sinRol).toBe('0');
    const desconocido = await llamar(request, 'POST', '/api/v1/rbac/authorize', { token: admin.token, data: { role: 'INVITADO', action: 'GESTIONAR_PRODUCTOS' } });
    await fotoHttp(page, 'CP-RBAC-001-01', 'Rol no reconocido «INVITADO»: no se le concede ningún permiso (mínimo privilegio)', desconocido);
    expect(desconocido.cuerpo?.permitted).not.toBe(true);
    expect(desconocido.estado).toBeGreaterThanOrEqual(400);
    await entrar(page, 'superadmin_qa');
    await page.goto(VISTA('cuentas/crear-cuenta-admin.html'));
    await page.locator('#rol').selectOption('MODERADOR');
    await expect(page.locator('#permisos')).not.toContainText('Selecciona un rol');
    await foto(page, 'CP-RBAC-001-01', 'Selector de rol con la descripción y los permisos de cada rol (pantalla de creación de cuentas)', { resaltar: [page.locator('#rol'), page.locator('#permisos')] });
  });

  test('CP-RBAC-001-02 · Las 40 combinaciones rol × acción de la Tabla 24 se autorizan exactamente según la matriz', async ({ page, request }) => {
    const admin = await sesion(request, 'superadmin_qa');
    const filas = [];
    let fallos = 0;
    for (const [accion, valores] of Object.entries(TABLA_24)) {
      const celdas = [];
      for (const [i, rol] of ROLES.entries()) {
        const r = await llamar(request, 'POST', '/api/v1/rbac/authorize', { token: admin.token, data: { role: rol, action: accion } });
        const obtenido = r.cuerpo?.permissionType ?? 'ERROR';
        const esperado = A_SERVIDOR[valores[i]];
        const ok = obtenido === esperado;
        if (!ok) fallos += 1;
        celdas.push(`<td class="${ok ? 'ok' : 'ko'}">${valores[i]} → ${obtenido} ${ok ? '✔' : '✘'}</td>`);
      }
      filas.push(`<tr><td>${accion}</td>${celdas.join('')}</tr>`);
    }
    await page.goto('about:blank');
    await fotoHtml(page, 'CP-RBAC-001-02', `40 combinaciones evaluadas por POST /api/v1/rbac/authorize: ${40 - fallos} correctas, ${fallos} incorrectas`,
      `<table><tr><th>Acción (Tabla 24)</th>${ROLES.map((r) => `<th>${r}</th>`).join('')}</tr>${filas.join('')}</table>`,
      { nota: 'Cada celda: valor de la Tabla 24 del cliente → respuesta real del servidor. «Temporal» del Moderador en «Suspender usuarios» debe responder TEMPORARY.' });
    expect(fallos).toBe(0);
  });

  test('CP-RBAC-001-03 · La interfaz solo habilita las acciones del rol vigente', async ({ page }) => {
    const casos = [
      ['moderadora_e2e', 'MODERADOR: la consola solo ofrece herramientas de moderación (comentarios, sanciones, lista negra)'],
      ['admin_e2e', 'ADMINISTRADOR: gestiona usuarios y productos, pero no «Crear cuenta administrativa»'],
      ['superadmin_qa', 'SUPER ADMINISTRADOR: acceso total, incluida la creación de cuentas administrativas y la auditoría'],
    ];
    for (const [apodo, descripcion] of casos) {
      await entrar(page, apodo);
      await page.goto(VISTA('plataforma/consola/consola.html'));
      await page.waitForTimeout(1500);
      await foto(page, 'CP-RBAC-001-03', descripcion, { completa: true });
      await page.evaluate(() => sessionStorage.clear());
    }
    await entrar(page, 'moderadora_e2e');
    await page.goto(VISTA('plataforma/consola/consola.html'));
    await page.waitForTimeout(1500);
    await expect(page.getByRole('link', { name: /Crear cuenta administrativa/ })).toHaveCount(0);
  });

  test('CP-RBAC-001-04 · Un intento directo a una acción no permitida muestra «acceso denegado»', async ({ page }) => {
    await entrar(page, 'jugador_qa');
    await page.goto(VISTA('cuentas/gestion-usuarios.html'));
    const aviso = page.getByText(/No tienes acceso a esta sección/);
    await expect(aviso).toBeVisible();
    await foto(page, 'CP-RBAC-001-04', 'Un JUGADOR escribe la URL de «Gestión de usuarios»: la vista se bloquea con «No tienes acceso a esta sección»', { resaltar: aviso.locator('..') });
    await page.goto(VISTA('cuentas/auditoria.html'));
    await expect(page.getByText(/No tienes acceso|acceso denegado/i).first()).toBeVisible();
    await foto(page, 'CP-RBAC-001-04', 'Lo mismo al intentar abrir directamente el registro de auditoría', { resaltar: page.getByText(/No tienes acceso|acceso denegado/i).first() });
  });

  test('CP-RBAC-001-05 · Una combinación no contemplada se deniega por defecto y genera alerta', async ({ page, request }) => {
    test.setTimeout(300_000);
    const admin = await sesion(request, 'superadmin_qa');
    const r = await llamar(request, 'POST', '/api/v1/rbac/authorize', { token: admin.token, data: { role: 'MODERADOR', action: 'PUBLICAR_SUBASTA_OFICIAL' } });
    await page.goto('about:blank');
    await fotoHttp(page, 'CP-RBAC-001-05', 'Acción fuera de la matriz («PUBLICAR_SUBASTA_OFICIAL»): el servidor no la autoriza', r);
    expect(r.cuerpo?.permitted).not.toBe(true);
    // La rama de alerta (rol/acción sin entrada en la matriz) no es alcanzable por HTTP: la cubre la prueba unitaria del servicio.
    const modulo = `${RAIZ}/services/cuentas/ms-identidad`;
    const r2 = spawnSync(process.env.ComSpec ?? 'C:/Windows/System32/cmd.exe', ['/c', 'C:\\Users\\chapa\\nbqa\\repo\\services\\cuentas\\ms-identidad\\mvnw.cmd', '-q', '-B', 'test', '-Dtest=RbacMatrix48CombinationsTest', '-Djacoco.skip=true'], {
      cwd: modulo, encoding: 'utf8', maxBuffer: 50e6,
    });
    const salida = `${r2.stdout ?? ''}${r2.stderr ?? ''}`;
    const reporte = fs.readFileSync(`${RAIZ}/services/cuentas/ms-identidad/target/surefire-reports/com.nexusbattles.ms_identidad.rbac.RbacMatrix48CombinationsTest.txt`, 'utf8');
    const alertas = salida.split(/\r?\n/).filter((l) => /RBAC_INCONSISTENCY_DETECTED/.test(l)).slice(0, 3).map((l) => l.replace(/^.*?(RBAC_)/, '$1')).join('\n');
    await fotoTexto(page, 'CP-RBAC-001-05', 'Prueba unitaria JUnit «Regla Default-Deny» del servicio RBAC: deniega y emite la alerta', 'mvnw test -Dtest=RbacMatrix48CombinationsTest', `${reporte.trim()}\n\nAlertas emitidas durante la prueba:\n${alertas}`);
    expect(reporte).toMatch(/Failures: 0, Errors: 0/);
    expect(alertas).toContain('RBAC_INCONSISTENCY_DETECTED');
  });
});
