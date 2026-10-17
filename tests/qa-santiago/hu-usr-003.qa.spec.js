// HU-USR-003 · Gestión de cuentas por administrador (Santiago Sanabria)
import { test } from '@playwright/test';
import { esperarCodigo } from '../e2e/ayudantes/correo.js';
import { CORRIDA, CLAVE_DEL_BANCO, VISTA, entrar, expect, foto, fotoHttp, fotoTexto, llamar, sesion, sql } from './ayudante-qa.js';

const sello = CORRIDA;
const OBJETIVO = `gestion${sello}`;
let id;
let uid;

async function abrirFicha(page) {
  await page.goto(VISTA(`cuentas/gestion-usuarios.html?usuario=${id}`));
  await expect(page.locator('#panel-usuario')).toBeVisible();
  await expect(page.locator('#usuario-apodo-mostrado')).not.toBeEmpty();
  await page.waitForLoadState('networkidle').catch(() => {});
}

async function aceptarDialogo(page) {
  const dialogo = page.getByRole('dialog');
  await expect(dialogo).toBeVisible();
  const boton = dialogo.getByRole('button').filter({ hasNotText: /cancelar|volver|no,/i }).last();
  await boton.click();
}

test.describe('HU-USR-003 · Gestión de cuentas por administrador', () => {
  test.beforeAll(async ({ request }) => {
    const s = await sesion(request, OBJETIVO);
    uid = s.claims.uid;
    id = sql('identidad', `SELECT id FROM usuarios WHERE apodo='${OBJETIVO}';`);
  });

  test('CP-USR-003-01 · El administrador modifica el perfil de cualquier usuario', async ({ page }) => {
    await entrar(page, 'admin_e2e');
    await abrirFicha(page);
    await foto(page, 'CP-USR-003-01', `Ficha del usuario ${OBJETIVO} (ID ${id}) abierta por el ADMINISTRADOR admin_e2e`, { resaltar: page.locator('#panel-usuario'), completa: true });
    await page.locator('#nombres').fill('Camilo');
    await page.locator('#apellidos').fill('Restrepo Gestionado');
    await page.locator('#preferencias').fill('Cuenta revisada por soporte');
    await page.locator('#btn-guardar-perfil').click();
    const dialogo = page.getByRole('dialog');
    if (await dialogo.isVisible().catch(() => false)) {
      await foto(page, 'CP-USR-003-01', 'Confirmación antes de guardar el cambio', { resaltar: dialogo });
      await aceptarDialogo(page);
    }
    const mensaje = page.locator('#mensaje-perfil');
    await expect(mensaje).toBeVisible();
    await foto(page, 'CP-USR-003-01', 'Datos: nombres «Camilo», apellidos «Restrepo Gestionado», preferencias. Resultado: perfil guardado', { resaltar: [mensaje, page.locator('#formulario-perfil-admin')] });
    const fila = sql('identidad', `SELECT p.nombres||' '||p.apellidos||' | '||coalesce(p.preferencias,'') FROM perfiles_usuario p JOIN usuarios u ON u.id=p.usuario_id WHERE u.id=${id};`);
    expect(fila).toContain('Camilo Restrepo Gestionado');
  });

  test('CP-USR-003-02 · El administrador cambia el estado de la cuenta (suspender y reactivar)', async ({ page }) => {
    await entrar(page, 'admin_e2e');
    await abrirFicha(page);
    const manana = new Date(Date.now() + 2 * 864e5);
    const valor = `${manana.toISOString().slice(0, 10)}T18:00`;
    await page.locator('#suspendido-hasta').fill(valor);
    await page.locator('#motivo-sancion').fill('Revisión de actividad sospechosa (prueba QA)');
    await page.locator('#btn-suspender').click();
    await aceptarDialogo(page);
    await expect(page.locator('#usuario-estado-mostrado')).toContainText(/SUSPEND/i);
    await foto(page, 'CP-USR-003-02', `Suspensión hasta ${valor} con motivo: el estado mostrado pasa a SUSPENDIDO`, { resaltar: page.locator('#usuario-estado-mostrado') });
    await page.locator('#btn-reactivar').click();
    await aceptarDialogo(page);
    await expect(page.locator('#usuario-estado-mostrado')).toContainText('ACTIVO');
    await foto(page, 'CP-USR-003-02', 'Reactivar: la cuenta vuelve a ACTIVO', { resaltar: page.locator('#usuario-estado-mostrado') });
  });

  test('CP-USR-003-04 · La modificación del rol queda reservada al Super Administrador', async ({ page, request }) => {
    await entrar(page, 'admin_e2e');
    await abrirFicha(page);
    const boton = page.locator('#btn-cambiar-rol');
    const visible = await boton.isVisible().catch(() => false);
    const habilitado = visible && (await boton.isEnabled().catch(() => false));
    await foto(page, 'CP-USR-003-04', `Para el ADMINISTRADOR el control de rol ${visible ? (habilitado ? 'aparece habilitado' : 'aparece deshabilitado') : 'no aparece'}`, { resaltar: page.locator('#nuevo-rol').locator('..') });
    const admin = await sesion(request, 'admin_e2e');
    const intento = await llamar(request, 'PUT', `/api/v1/rbac/usuarios/${id}/rol`, { token: admin.token, data: { nuevoRol: 'MODERADOR' } });
    await fotoHttp(page, 'CP-USR-003-04', 'Intento directo del ADMINISTRADOR de cambiar el rol por la API: rechazado', intento);
    expect(intento.estado).toBe(403);
    expect(habilitado).toBe(false);
  });

  test('CP-USR-003-05 · Cada modificación guarda valores anterior/nuevo en auditoría y avisa al usuario', async ({ page, request }) => {
    const filas = sql('cumplimiento', `SELECT to_char(fecha_hora,'HH24:MI:SS')||' | admin='||administrador_id||' | '||tipo_accion||' | anterior='||left(coalesce(valor_anterior,'—'),60)||' | nuevo='||left(coalesce(valor_nuevo,'—'),60)||' | motivo='||left(motivo,50) FROM audit_log WHERE afectado IN ('${id}','${uid}') AND administrador_id <> '${uid}' ORDER BY fecha_hora;`);
    await page.goto('about:blank');
    await fotoTexto(page, 'CP-USR-003-05', 'Asientos de auditoría de las acciones del administrador sobre la cuenta', `audit_log · afectado = ${id}`, filas || '(sin filas)');
    expect(filas).toMatch(/anterior=(?!—)/);
    const objetivo = await sesion(request, OBJETIVO, { nueva: true });
    await entrar(page, OBJETIVO);
    await page.goto(VISTA('plataforma/notificaciones/notificaciones.html'));
    await page.waitForLoadState('networkidle');
    const aviso = page.getByText('Tu suspension fue levantada').first();
    await expect(aviso, 'el usuario afectado debe recibir el aviso de la modificación').toBeVisible();
    await foto(page, 'CP-USR-003-05', 'Bandeja del usuario afectado: recibió los avisos de suspensión y de reactivación hechos por el administrador', { resaltar: page.locator('main').getByText(/Tu cuenta esta suspendida|Tu suspension fue levantada/).first().locator('..'), completa: true });
    expect(objetivo.token).toBeTruthy();
  });

  test('CP-USR-003-07 · El usuario recibe aviso cuando el administrador edita su perfil', async ({ page }) => {
    await entrar(page, OBJETIVO);
    await page.goto(VISTA('plataforma/notificaciones/notificaciones.html'));
    await page.waitForLoadState('networkidle');
    await foto(page, 'CP-USR-003-07', 'Bandeja del usuario tras la edición de su perfil hecha por el administrador (CP-USR-003-01)', { completa: true });
    const registro = sql('plataforma', `SELECT usuario_id||' | '||tipo||' | '||titulo FROM notificaciones.notificaciones WHERE titulo='Tu perfil fue actualizado' AND usuario_id IN ('${id}','${uid}');`);
    await fotoTexto(page, 'CP-USR-003-07', 'Aviso emitido por ms-identidad: quedó guardado con el ID numérico interno, no con el uid del usuario', `notificaciones.notificaciones · uid del usuario = ${uid}`, registro || '(sin filas)');
    await expect(page.getByText('Tu perfil fue actualizado'), 'el aviso de edición de perfil debe llegar a la bandeja del usuario').toBeVisible({ timeout: 5000 });
  });

  test('CP-USR-003-06 · Restablecer contraseña: invalida la vigente y envía el mecanismo seguro al titular', async ({ page, request }) => {
    await entrar(page, 'admin_e2e');
    await abrirFicha(page);
    await page.locator('#btn-restablecer-password').click();
    await aceptarDialogo(page);
    await expect(page.getByText(/restablec|correo/i).last()).toBeVisible();
    await foto(page, 'CP-USR-003-06', 'El administrador pulsa «Restablecer contraseña»: el sistema confirma el envío al correo del usuario y no muestra ninguna contraseña', { completa: true });
    const viejo = await llamar(request, 'POST', '/api/v1/auth/login', { data: { email: `${OBJETIVO}@nexus.test`, password: CLAVE_DEL_BANCO } });
    await fotoHttp(page, 'CP-USR-003-06', 'La contraseña anterior deja de funcionar', viejo);
    expect(viejo.estado).toBe(401);
    const correo = await esperarCodigo(`${OBJETIVO}@nexus.test`, { tipo: 'recuperacion', espera: 60_000 });
    await fotoTexto(page, 'CP-USR-003-06', 'El titular recibe en su correo el enlace/código de un solo uso para definir su nueva contraseña', `Correo a ${OBJETIVO}@nexus.test`, `Enlace: ${(correo.enlace ?? '').replace(/codigo=[^&]+/, 'codigo=••••••')}\nCódigo: •••••• (solo lo ve el titular)`);
    expect(correo.codigo).toBeTruthy();
  });
  test('CP-USR-003-03 · El administrador gestiona el inventario y la privacidad/seguridad del usuario', async ({ page }) => {
    await entrar(page, 'admin_e2e');
    await abrirFicha(page);
    const pendiente = page.locator('[data-zona="ficha-pendiente"]');
    await foto(page, 'CP-USR-003-03', 'Se buscan en la ficha los controles para editar el inventario y la configuración de privacidad del usuario', { resaltar: pendiente, completa: true });
    const controles = page.locator('#panel-usuario').getByRole('button', { name: /inventario|privacidad/i });
    await expect(controles, 'la ficha debe permitir modificar inventario y privacidad (CA1)').not.toHaveCount(0, { timeout: 3000 });
  });


});
