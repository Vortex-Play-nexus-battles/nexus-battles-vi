// @ts-check
/**
 * Modificar el inventario de una cuenta desde la consola — SA-10, §7.3.4
 * («Acciones disponibles: ver y modificar inventario del usuario»), de punta
 * a punta por el borde con ms-identidad, inventario, productos y
 * ms-cumplimiento reales (ms-identidad-admin.yaml 1.10.0).
 *
 *   1. el Administrador entrega 2 unidades de un producto del catálogo a una
 *      jugadora: 201, llegan a su inventario con origen ADMINISTRACION y la
 *      misma clave otra vez es 200 sin duplicar nada;
 *   2. la entrega queda en la bitácora central con quién, a quién, qué y el
 *      motivo, una sola vez aunque se repita (§7.3.5);
 *   3. negativos: jugador y moderador 403, una cuenta de Administrador 403
 *      jerarquia-insuficiente, sin clave o con cantidad fuera de rango 400,
 *      producto inexistente 422;
 *   4. desde la ficha de la cuenta: «Entregar productos», elegir del catálogo,
 *      motivo, y el inventario de la ficha ya lo enseña.
 *
 * La jugadora se crea aquí con el sufijo de la corrida; las cuentas
 * administrativas son las de pruebas del banco.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import {
  CLAVE_DEL_BANCO,
  cuentaAdministrativaDeRol,
  sesionDe as sesionDelBanco,
} from './ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const SUFIJO = Date.now().toString(36);
const ERRORES = 'https://nexusbattles.upb.edu.co/errors/';
/** El tipo del producto que se entrega: el banco siembra armas (sembrar.sh), no ítems. */
const TIPO = 'ARMA';

function sesionDe(api, apodo) {
  return sesionDelBanco(api, apodo, { clave: CLAVE_DEL_BANCO, base: BORDE });
}

const con = (sesion, extra = {}) => ({
  headers: { Authorization: `Bearer ${sesion.token}`, ...extra },
});

test.describe('Modificar el inventario de una cuenta (SA-10, §7.3.4)', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let admin;
  let superAdmin;
  let moderador;
  let jugadora;
  let idDeLaJugadora;
  let producto;

  /** La clave interna (la del directorio) de una cuenta, por su apodo. */
  async function claveDe(apodo) {
    const r = await api.get(
      `/api/v1/admin/jugadores?buscar=${encodeURIComponent(apodo)}&size=5`,
      con(admin),
    );
    expect(r.status(), await r.text()).toBe(200);
    const fila = (await r.json()).contenido.find((c) => c.apodo === apodo);
    expect(fila, `${apodo} está en el directorio`).toBeTruthy();
    return fila.id;
  }

  /** Cuántos elementos de ese producto tiene la jugadora, y con qué origen. */
  async function delProducto(productoId) {
    const r = await api.get('/api/v1/inventario/elementos?pagina=0', con(jugadora));
    expect(r.status(), await r.text()).toBe(200);
    return (await r.json()).elementos.filter((e) => e.productoId === productoId);
  }

  const entregar = (sesion, cuenta, cuerpo, clave) =>
    api.post(`/api/v1/admin/usuarios/${cuenta}/inventario/entregas`, {
      headers: {
        Authorization: `Bearer ${sesion.token}`,
        'Content-Type': 'application/json',
        ...(clave ? { 'Idempotency-Key': clave } : {}),
      },
      data: cuerpo,
    });

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    admin = await sesionDe(api, cuentaAdministrativaDeRol('ADMINISTRADOR').apodo);
    superAdmin = await sesionDe(api, cuentaAdministrativaDeRol('SUPER_ADMINISTRADOR').apodo);
    moderador = await sesionDe(api, cuentaAdministrativaDeRol('MODERADOR').apodo);
    jugadora = await sesionDe(api, `inv_sa10_${SUFIJO}`);
    idDeLaJugadora = await claveDe(jugadora.apodo);

    // Un producto del catálogo que se pueda entregar: un arma activa. El banco
    // siembra «Espada de prueba» (sembrar.sh); si no estuviera, cualquier otra.
    const catalogo = await api.get(`/api/v1/productos?tipo=${TIPO}&size=50&page=0`);
    expect(catalogo.status()).toBe(200);
    const activos = (await catalogo.json()).content.filter((p) => p.estado !== 'SUSPENDIDO');
    producto = activos.find((p) => p.id === 'p-arma-e2e') ?? activos[0];
    expect(producto, 'el banco tiene algún arma activa').toBeTruthy();
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  test('1 · entrega 2 unidades, llegan con origen ADMINISTRACION y repetir no duplica', async () => {
    const antes = (await delProducto(producto.id)).length;
    const clave = `e2e-sa10-${SUFIJO}-api`;
    const cuerpo = { productoId: producto.id, cantidad: 2, motivo: 'Compensación del E2E' };

    const primera = await entregar(admin, idDeLaJugadora, cuerpo, clave);
    expect(primera.status(), await primera.text()).toBe(201);
    const hecha = await primera.json();
    expect(hecha.nueva).toBe(true);
    expect(hecha.cantidad).toBe(2);
    expect(hecha.uid).toBe(jugadora.claims.uid);

    const recibidos = await delProducto(producto.id);
    expect(recibidos).toHaveLength(antes + 2);
    expect(recibidos.filter((e) => e.origen === 'ADMINISTRACION').length).toBeGreaterThanOrEqual(2);

    const repetida = await entregar(admin, idDeLaJugadora, cuerpo, clave);
    expect(repetida.status(), await repetida.text()).toBe(200);
    expect((await repetida.json()).nueva).toBe(false);
    expect(await delProducto(producto.id)).toHaveLength(antes + 2);
  });

  test('2 · queda en la bitácora central con el motivo, una sola vez', async () => {
    await expect
      .poll(
        async () => {
          const r = await api.get(
            `/api/v1/admin/auditoria?afectado=${jugadora.claims.uid}&page=0&size=50`,
            con(superAdmin),
          );
          if (r.status() !== 200) {
            return -1;
          }
          const entradas = (await r.json()).content ?? [];
          return entradas.filter(
            (e) =>
              e.tipoAccion === 'ACTUALIZACION' &&
              e.motivo === 'Compensación del E2E' &&
              String(e.valorNuevo ?? '').startsWith('Inventario: 2 × '),
          ).length;
        },
        { timeout: 20_000, message: 'la entrega está en la bitácora una vez' },
      )
      .toBe(1);
  });

  test('3 · negativos: roles, jerarquía, datos y producto inexistente', async () => {
    const cuerpo = { productoId: producto.id, cantidad: 1, motivo: 'No debería entrar' };

    for (const sinPermiso of [jugadora, moderador]) {
      const r = await entregar(sinPermiso, idDeLaJugadora, cuerpo, `e2e-sa10-${SUFIJO}-no`);
      expect(r.status(), `${sinPermiso.claims.rol} no entrega`).toBe(403);
    }

    // Una cuenta de rol igual (la del propio Administrador): 7.3.1 no lo deja.
    const cuentaDeAdministrador = await claveDe(admin.apodo);
    const jerarquia = await entregar(
      admin,
      cuentaDeAdministrador,
      cuerpo,
      `e2e-sa10-${SUFIJO}-jer`,
    );
    expect(jerarquia.status()).toBe(403);
    expect((await jerarquia.json()).type).toBe(`${ERRORES}jerarquia-insuficiente`);

    const sinClave = await entregar(admin, idDeLaJugadora, cuerpo, null);
    expect(sinClave.status()).toBe(400);
    expect((await sinClave.json()).type).toBe(`${ERRORES}datos-invalidos`);

    const demasiadas = await entregar(
      admin,
      idDeLaJugadora,
      { ...cuerpo, cantidad: 21 },
      `e2e-sa10-${SUFIJO}-21`,
    );
    expect(demasiadas.status()).toBe(400);

    const inexistente = await entregar(
      admin,
      idDeLaJugadora,
      { ...cuerpo, productoId: `no-existe-${SUFIJO}` },
      `e2e-sa10-${SUFIJO}-422`,
    );
    expect(inexistente.status(), await inexistente.text()).toBe(422);
  });

  test('4 · desde la ficha: «Entregar productos», elegir, motivo y el inventario lo enseña', async ({
    page,
  }) => {
    await page.addInitScript(
      ([token, apodo, rol, uid]) => {
        if (sessionStorage.getItem('nexus.e2e.inyectada')) {
          return;
        }
        sessionStorage.setItem('nexus.e2e.inyectada', '1');
        sessionStorage.setItem('nexus.token', token);
        sessionStorage.setItem('nexus.apodoActual', apodo);
        sessionStorage.setItem('nexus.rolActual', rol);
        sessionStorage.setItem('nexus.usuarioId', uid);
      },
      [admin.token, admin.apodo, admin.claims.rol, admin.claims.uid],
    );
    const antes = (await delProducto(producto.id)).length;

    await page.goto(`/consola/usuarios/ficha?usuario=${idDeLaJugadora}`);
    await page.getByRole('tab', { name: 'Inventario' }).click();
    const panel = page.locator('[data-panel="inventario-de-la-cuenta"]');
    await panel.locator('[data-accion="entregar-productos"]').click();

    const dialogo = page.locator('[role="dialog"]');
    await expect(dialogo).toContainText(`Entregar productos · ${jugadora.apodo}`);
    await dialogo.locator('select[name="tipo"]').selectOption(TIPO);
    await expect(dialogo.locator('select[name="producto"]')).toBeEnabled({ timeout: 20_000 });
    await dialogo.locator('select[name="producto"]').selectOption(producto.id);
    await dialogo.locator('input[name="cantidad"]').fill('1');
    await dialogo.locator('textarea[name="motivo"]').fill('Entrega desde la ficha (E2E)');
    await expect(dialogo.locator('[data-zona="resumen-de-la-entrega"]')).toContainText(
      `Se entregará 1 × ${producto.nombre} a ${jugadora.apodo}`,
    );
    const alta = page.waitForResponse(
      (r) => r.request().method() === 'POST' && r.url().includes('/inventario/entregas'),
    );
    await dialogo.locator('[data-accion="confirmar-entrega"]').click();
    expect((await alta).status()).toBe(201);

    await expect(panel).toContainText('Entrega hecha', { timeout: 20_000 });
    await expect(panel).toContainText(producto.nombre);
    expect(await delProducto(producto.id)).toHaveLength(antes + 1);
  });
});
