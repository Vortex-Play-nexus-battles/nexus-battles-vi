// @ts-check
/**
 * Ficha administrativa del usuario — HU-USR-010 (RF-USR-010, §7.3.4), por el
 * borde y con los servicios reales del banco: ms-identidad, salas-partidas,
 * ms-finanzas, moderacion-sanciones, comentarios, inventario y
 * ms-cumplimiento.
 *
 *   1. la ruta (ms-identidad-admin.yaml 1.4.0): el administrador lee la ficha
 *      de una jugadora nueva por su uid y por su clave, sin credenciales y sin
 *      cache; la jugadora no puede (403); una cuenta que no existe es un
 *      problem details 404 `cuenta-no-encontrada` (CA-03).
 *   2. la vista (SA-3): cada pestaña pide lo suyo a su dueño por el uid de la
 *      cuenta y todas responden en el banco —rol y permisos, estadísticas y
 *      partidas, saldo y movimientos, sanciones, comentarios, reportes
 *      realizados y recibidos, inventario y la línea de tiempo—, sin
 *      secciones de relleno (SA-2) y con el aviso de auditoría. El
 *      Administrador no ve la auditoría, que es del Super Administrador.
 *   3. el Super Administrador ve, además, las acciones administrativas sobre
 *      la cuenta: también la consulta que acaba de hacer.
 *   4. una advertencia llega al resumen del directorio (canal HISTORIAL de la
 *      cola de salidas): el filtro «Con sanciones» encuentra a la jugadora y
 *      «Sin sanciones» ya no.
 *   5. el directorio de Control integral abre la ficha de cada cuenta. Desde
 *      SA-2 las cuentas de prueba se incluyen con el filtro «Cuentas de
 *      prueba» de la barra común, que viene en «Excluidas».
 *   6. la gestión de usuarios busca por apodo, enseña las cuentas que
 *      coinciden y abre la elegida (SA-3, informe punto 16).
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import { cuentaAdministrativaDeRol, sesionDe as sesionDelBanco } from './ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const ADMIN = process.env.E2E_ADMIN ?? 'admin_e2e';
const SUPER = process.env.E2E_SUPER ?? cuentaAdministrativaDeRol('SUPER_ADMINISTRADOR')?.apodo;
const CLAVE = 'Contrasena-E2E-2026';
const JUGADORA = `ficha_${Date.now().toString(36)}`;
const VISTAS = '/frontend/app-web/src';
const FICHA = `${VISTAS}/plataforma/moderacion-sanciones/ficha-usuario.html`;

function sesionDe(api, apodo) {
  return sesionDelBanco(api, apodo, { clave: CLAVE, base: BORDE });
}

async function conSesion(page, sesion) {
  await page.addInitScript(
    ([token, nombre, uid, rol]) => {
      sessionStorage.setItem('nexus.token', token);
      sessionStorage.setItem('nexus.apodoActual', nombre);
      sessionStorage.setItem('nexus.usuarioId', uid);
      // Lo que deja el login: la gestión de usuarios decide con este rol.
      sessionStorage.setItem('nexus.rolActual', rol);
    },
    [sesion.token, sesion.apodo, sesion.claims.uid, sesion.claims.rol],
  );
}

/** Espera a que un panel de la ficha haya cargado con datos, sin aviso de fallo. */
async function panelCargado(page, id) {
  const panel = page.locator(`[data-panel="${id}"]`);
  await expect(panel).toBeVisible();
  await expect(panel.locator('.panel-admin__estado').first()).toHaveAttribute(
    'data-estado',
    'DATOS',
    { timeout: 20_000 },
  );
  await expect(panel.locator('[data-accion="reintentar"]')).toHaveCount(0);
  return panel;
}

test.describe('Ficha administrativa del usuario (HU-USR-010)', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let admin;
  let jugadora;
  let ficha;

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    admin = await sesionDe(api, ADMIN);
    expect(admin.claims.rol).toBe('ADMINISTRADOR');
    jugadora = await sesionDe(api, JUGADORA);
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  test('la ruta: el administrador la lee por uid y por clave; la jugadora no; lo inexistente es 404', async () => {
    const porUid = await api.get(`/api/v1/admin/usuarios/${jugadora.claims.uid}/ficha`, {
      headers: { Authorization: `Bearer ${admin.token}` },
    });
    expect(porUid.status(), await porUid.text()).toBe(200);
    expect(porUid.headers()['cache-control']).toContain('no-store');
    ficha = await porUid.json();
    expect(ficha.apodo).toBe(JUGADORA);
    expect(ficha.uid).toBe(jugadora.claims.uid);
    expect(ficha.email).toBe(`${JUGADORA}@nexus.test`);
    expect(ficha.rol).toBe('JUGADOR');
    expect(typeof ficha.accesoAuditado).toBe('boolean');
    expect(JSON.stringify(ficha)).not.toMatch(/password|versionToken|\$2a\$/i);

    const porClave = await api.get(`/api/v1/admin/usuarios/${ficha.id}/ficha`, {
      headers: { Authorization: `Bearer ${admin.token}` },
    });
    expect(porClave.status()).toBe(200);
    expect((await porClave.json()).uid).toBe(jugadora.claims.uid);

    const propia = await api.get(`/api/v1/admin/usuarios/${ficha.id}/ficha`, {
      headers: { Authorization: `Bearer ${jugadora.token}` },
    });
    expect(propia.status()).toBe(403);

    const nadie = await api.get(
      '/api/v1/admin/usuarios/00000000-0000-0000-0000-000000000000/ficha',
      {
        headers: { Authorization: `Bearer ${admin.token}`, Accept: 'application/problem+json' },
      },
    );
    expect(nadie.status()).toBe(404);
    expect((await nadie.json()).type).toBe(
      'https://nexusbattles.upb.edu.co/errors/cuenta-no-encontrada',
    );
  });

  test('la vista: cada pestaña responde desde su dueño, sin relleno y con la consulta auditada', async ({
    page,
  }) => {
    await conSesion(page, admin);
    await page.goto(`${BORDE}${FICHA}?usuario=${ficha.id}`);

    await expect(page.locator('h1')).toHaveText(`Ficha de ${JUGADORA}`, { timeout: 20_000 });
    // Resumen: la cuenta, su rol con los permisos de la matriz y las acciones.
    const cuenta = page.locator('[data-panel="cuenta"]');
    await expect(cuenta).toContainText(`${JUGADORA}@nexus.test`);
    await expect(cuenta).toContainText('Jugador');
    const permisos = await panelCargado(page, 'permisos');
    await expect(permisos).toContainText('Publicar comentarios');
    await expect(permisos).not.toContainText('Moderar comentarios');

    // Juego: una cuenta nueva no ha jugado, y se dice sin inventar fechas.
    await page.locator('#pestana-juego').click();
    const estadisticas = await panelCargado(page, 'estadisticas');
    await expect(
      estadisticas.locator('[data-indicador="Partidas terminadas"] .indicador-admin__valor'),
    ).toHaveText('0');
    await expect(
      estadisticas.locator('[data-indicador="Última partida"] .indicador-admin__valor'),
    ).toHaveText('Nunca');
    await expect(await panelCargado(page, 'partidas')).toContainText(
      'No ha jugado ninguna partida.',
    );

    // Créditos: el libro responde a la administración.
    await page.locator('#pestana-creditos').click();
    await expect((await panelCargado(page, 'saldo')).locator('.indicador-admin')).toHaveCount(3);
    await panelCargado(page, 'movimientos');

    // Sanciones y comunidad: nada todavía, y cada sección lo dice.
    await page.locator('#pestana-sanciones').click();
    await expect(await panelCargado(page, 'sanciones')).toContainText(
      'No tiene sanciones ni advertencias.',
    );
    await page.locator('#pestana-comunidad').click();
    await expect(await panelCargado(page, 'comentarios')).toContainText(
      'No ha publicado comentarios.',
    );
    await expect(await panelCargado(page, 'reportes-realizados')).toContainText(
      'No ha reportado ningún comentario.',
    );
    await expect(await panelCargado(page, 'reportes-recibidos')).toContainText(
      'Nadie ha reportado sus comentarios.',
    );

    // Inventario: el inventario responde a la administración.
    await page.locator('#pestana-inventario').click();
    await panelCargado(page, 'inventario-de-la-cuenta');

    // Actividad: la línea de tiempo; la auditoría no es del Administrador.
    await page.locator('#pestana-actividad').click();
    await expect(page.locator('[data-panel="linea-de-tiempo"] .linea-tiempo')).toContainText(
      'Se registró',
    );
    await expect(page.locator('[data-panel="auditoria"]')).toHaveCount(0);

    // SA-2: ninguna sección vacía para aparentar ni copy de andamio.
    await expect(page.locator('[data-seccion="sin-fuente"]')).toHaveCount(0);
    await expect(page.locator('main')).not.toContainText(/Sin fuente publicada|Fuente:|GET \/api/);
    const aviso = page.locator('[data-zona="aviso"]');
    await expect(aviso).toHaveAttribute('data-auditado', /^(si|no)$/);
    await expect(aviso).toContainText(/quedó registrada en la auditoría/);
  });

  test('el Super Administrador ve las acciones administrativas sobre la cuenta, también su consulta', async ({
    page,
  }) => {
    test.skip(!SUPER, 'el banco no tiene cuenta de Super Administrador (ADMINS_QA)');
    const superAdmin = await sesionDe(api, SUPER);
    expect(superAdmin.claims.rol).toBe('SUPER_ADMINISTRADOR');
    await conSesion(page, superAdmin);
    await page.goto(`${BORDE}${FICHA}?usuario=${ficha.id}#actividad`);

    await expect(page.locator('h1')).toHaveText(`Ficha de ${JUGADORA}`, { timeout: 20_000 });
    const auditoria = await panelCargado(page, 'auditoria');
    await expect(auditoria.locator('tbody tr').first()).toBeVisible();
    await expect(auditoria).toContainText('Consulta de la ficha administrativa');
  });

  test('una advertencia llega al directorio: «Con sanciones» la encuentra y «Sin sanciones» ya no', async () => {
    const emitida = await api.post('/api/v1/sanciones', {
      headers: { Authorization: `Bearer ${admin.token}` },
      data: {
        usuarioId: jugadora.claims.uid,
        tipo: 'ADVERTENCIA',
        motivo: 'Lenguaje ofensivo en el chat (prueba de la ficha)',
      },
    });
    expect(emitida.status(), await emitida.text()).toBe(201);

    const directorio = async (historial) => {
      const r = await api.get(
        `/api/v1/admin/jugadores?buscar=${encodeURIComponent(JUGADORA)}&historialSanciones=${historial}`,
        { headers: { Authorization: `Bearer ${admin.token}` } },
      );
      expect(r.status(), await r.text()).toBe(200);
      return (await r.json()).contenido.filter((c) => c.apodo === JUGADORA);
    };
    // La salida HISTORIAL se entrega con reintentos (cada 2 s en el banco).
    await expect
      .poll(async () => (await directorio('CON'))[0]?.sancionesRegistradas ?? 0, {
        timeout: 30_000,
        intervals: [500, 1000, 2000],
      })
      .toBeGreaterThanOrEqual(1);
    expect(await directorio('SIN')).toEqual([]);
  });

  test('el directorio de Control integral abre la ficha de la cuenta', async ({ page }) => {
    await conSesion(page, admin);
    await page.goto(`${BORDE}${VISTAS}/plataforma/consola/control-integral.html`);
    const directorio = page.locator('[data-panel="directorio"]');
    // Las cuentas del banco son @nexus.test, un dominio de pruebas: el
    // directorio las excluye por omisión con un filtro visible (RFINAL-06,
    // SA-2). Se incluyen para buscarla.
    await directorio.locator('select[name="cuentas"]').selectOption({ label: 'Incluidas' });
    await directorio.locator('input[name="buscar"]').fill(JUGADORA);
    await directorio.locator('button[data-accion="buscar"]').click();

    const enlace = directorio.locator('tbody a[data-accion="ver-ficha"]').first();
    await expect(enlace).toHaveAttribute(
      'href',
      new RegExp(`ficha-usuario\\.html\\?usuario=${ficha.id}$`),
      {
        timeout: 20_000,
      },
    );
    await enlace.click();
    await expect(page.locator('h1')).toHaveText(`Ficha de ${JUGADORA}`, { timeout: 20_000 });
  });

  test('la gestión de usuarios busca por apodo y abre la cuenta elegida', async ({ page }) => {
    await conSesion(page, admin);
    await page.goto(`${BORDE}${VISTAS}/cuentas/gestion-usuarios.html`);

    await expect(page.locator('#gestion-contenedor')).toBeVisible({ timeout: 20_000 });
    await page.locator('#usuario-id').fill(JUGADORA);
    await page.locator('#btn-buscar').click();

    const resultados = page.locator('#resultados-busqueda');
    const fila = resultados.locator('tbody tr', { hasText: JUGADORA });
    await expect(fila).toBeVisible({ timeout: 20_000 });
    await expect(fila).toContainText(`${JUGADORA}@nexus.test`);
    // Hasta elegir, no se gestiona nada.
    await expect(page.locator('#panel-usuario')).toBeHidden();

    await fila.locator('button[data-accion="seleccionar-cuenta"]').click();
    await expect(page.locator('#panel-usuario')).toBeVisible({ timeout: 20_000 });
    await expect(page.locator('#usuario-apodo-mostrado')).toHaveText(JUGADORA);
    await expect(page.locator('#usuario-id-mostrado')).toHaveText(String(ficha.id));
    await expect(page.locator('#enlace-ficha-usuario')).toHaveAttribute(
      'href',
      new RegExp(`ficha-usuario\\.html\\?usuario=${ficha.id}$`),
    );
  });
});
