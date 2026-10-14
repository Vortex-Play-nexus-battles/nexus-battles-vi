// @ts-check
/**
 * Cambios en las políticas de uso — SA-11, §7.3.7 («el sistema debe
 * notificar a los usuarios sobre cambios en políticas de uso que afecten al
 * usuario», RF-NOT-005), de punta a punta por el borde y contra los servicios
 * reales del banco (`tests/e2e/compose.yml`):
 *
 *   1. el Super Administrador publica un cambio en la política de privacidad
 *      (admin-parametros.yaml 1.1.0): 201, y la lectura pública lo enseña sin
 *      quién lo publicó, sin el motivo y sin la IP;
 *   2. una jugadora nueva entra y lo encuentra en su bandeja como
 *      `CAMBIO_POLITICA` (notificaciones.yaml 1.5.0), con qué cambió y desde
 *      cuándo rige; entregar otra vez no lo duplica;
 *   3. la publicación queda en la bitácora central con el motivo (§7.3.5);
 *   4. negativos: jugadora y moderadora 403, sin sesión 401, una política que
 *      no existe, sin motivo o con la vigencia en el pasado 400; el
 *      Administrador sí publica;
 *   5. la vista de notificaciones lo pinta con su categoría en palabras
 *      («Políticas de uso»), nunca con el código;
 *   6. el Super Administrador lo publica desde la consola de parámetros
 *      (/consola/parametros): ve antes el aviso tal como lo recibirá cada
 *      jugador y lo que va a pasar, y el cambio queda en la tabla y en la
 *      lectura pública.
 *
 * El banco pone el intervalo mínimo entre consultas a admin-parametros en
 * 1 s (NOTIFICACIONES_POLITICAS_INTERVALO_MINIMO_S, en DEV 60 s).
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import { entrarEnLaPagina } from './ayudantes/consola.js';
import {
  CLAVE_DEL_BANCO,
  cuentaAdministrativaDeRol,
  sesionDe as sesionDelBanco,
} from './ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const SUFIJO = Date.now().toString(36);
const NOTIFICACIONES = '/frontend/app-web/src/plataforma/notificaciones/notificaciones.html';
const POLITICAS = '/api/v1/parametros/politicas/cambios';
/** Algo más que el intervalo mínimo del banco (1 s). */
const PASA_EL_INTERVALO_MS = 1_300;

function sesionDe(api, apodo) {
  return sesionDelBanco(api, apodo, { clave: CLAVE_DEL_BANCO, base: BORDE });
}

const con = (sesion, extra = {}) => ({
  Authorization: `Bearer ${sesion.token}`,
  ...extra,
});

const esperar = (ms) => new Promise((resolver) => setTimeout(resolver, ms));

test.describe('Cambios en las políticas de uso (SA-11, §7.3.7)', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let superAdmin;
  let admin;
  let moderadora;
  let jugadora;
  let publicado;

  const cambio = {
    politica: 'POLITICA_DE_PRIVACIDAD',
    titulo: `Datos del chat ${SUFIJO}`,
    descripcion: 'Los mensajes del chat de las salas se guardan noventa días y después se borran.',
    motivo: `Ajuste acordado en el comité (E2E ${SUFIJO})`,
  };

  const publicar = (sesion, cuerpo) =>
    api.post(POLITICAS, {
      headers: sesion ? con(sesion, { 'Content-Type': 'application/json' }) : undefined,
      data: cuerpo,
    });

  async function entregarPendientes(sesion) {
    const respuesta = await api.post(
      `/api/v1/users/${jugadora.claims.uid}/sessions/${sesion}/pending`,
      { headers: con(jugadora) },
    );
    expect(respuesta.status(), await respuesta.text()).toBe(200);
    return respuesta.json();
  }

  async function bandeja() {
    const respuesta = await api.get(`/api/v1/users/${jugadora.claims.uid}/notifications`, {
      headers: con(jugadora),
    });
    expect(respuesta.status(), await respuesta.text()).toBe(200);
    return (await respuesta.json()).avisos ?? [];
  }

  const delCambio = (avisos) => avisos.filter((a) => a.id === `politica:${publicado.id}`);

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    superAdmin = await sesionDe(api, cuentaAdministrativaDeRol('SUPER_ADMINISTRADOR').apodo);
    admin = await sesionDe(api, cuentaAdministrativaDeRol('ADMINISTRADOR').apodo);
    moderadora = await sesionDe(api, cuentaAdministrativaDeRol('MODERADOR').apodo);
  });

  test.afterAll(async () => {
    await api?.dispose();
  });

  test('1 · el Super Administrador publica; la lectura pública no enseña quién, ni el motivo, ni la IP', async () => {
    const vigencia = new Date(Date.now() + 6 * 24 * 3600 * 1000).toISOString();
    const respuesta = await publicar(superAdmin, { ...cambio, vigenteDesde: vigencia });
    expect(respuesta.status(), await respuesta.text()).toBe(201);
    publicado = await respuesta.json();
    expect(publicado).toMatchObject({
      politica: 'POLITICA_DE_PRIVACIDAD',
      nombreDeLaPolitica: 'Política de privacidad',
      titulo: cambio.titulo,
      descripcion: cambio.descripcion,
    });
    expect(Date.parse(publicado.vigenteDesde)).toBe(Date.parse(vigencia));

    const lectura = await api.get(POLITICAS);
    expect(lectura.status(), 'leer es público').toBe(200);
    const enLaLista = (await lectura.json()).find((c) => c.id === publicado.id);
    expect(enLaLista).toBeTruthy();
    for (const interno of ['publicadoPor', 'motivo', 'ipOrigen']) {
      expect(enLaLista, `la lectura pública no lleva ${interno}`).not.toHaveProperty(interno);
    }
  });

  test('2 · una jugadora nueva lo encuentra en su bandeja al entrar, una sola vez', async () => {
    // Nueva: sin punto de lectura, lee lo publicado en la ventana de 30 días.
    jugadora = await sesionDe(api, `politicas_${SUFIJO}`);
    expect(jugadora.claims.rol).toBe('JUGADOR');

    let aviso = null;
    await expect
      .poll(
        async () => {
          await entregarPendientes(`entrada-${Date.now()}`);
          aviso = delCambio(await bandeja())[0] ?? null;
          if (!aviso) {
            await esperar(PASA_EL_INTERVALO_MS);
          }
          return aviso ? 'en la bandeja' : 'todavía no';
        },
        {
          timeout: 30_000,
          message: 'notificaciones no trajo el cambio: mirar PARAMETROS_URL de srv-notificaciones',
        },
      )
      .toBe('en la bandeja');

    expect(aviso).toMatchObject({
      tipo: 'CAMBIO_POLITICA',
      titulo: 'Actualizamos la política de privacidad',
      leida: false,
    });
    expect(aviso.cuerpo).toContain(`${cambio.titulo}. ${cambio.descripcion}`);
    expect(aviso.cuerpo).toMatch(/Rige desde el \d{1,2} de [a-z]+ de \d{4}, \d{2}:\d{2}\.$/);
    expect(Date.parse(aviso.creadaEn)).toBe(Date.parse(publicado.publicadoEn));

    for (let vuelta = 0; vuelta < 2; vuelta++) {
      await esperar(PASA_EL_INTERVALO_MS);
      await entregarPendientes(`repite-${vuelta}-${SUFIJO}`);
    }
    expect(delCambio(await bandeja()), 'entregar otra vez no lo duplica').toHaveLength(1);
  });

  test('3 · la publicación queda en la bitácora central con el motivo, una vez', async () => {
    await expect
      .poll(
        async () => {
          const r = await api.get(
            '/api/v1/admin/auditoria?afectado=politica:POLITICA_DE_PRIVACIDAD&page=0&size=50',
            { headers: con(superAdmin) },
          );
          if (r.status() !== 200) {
            return -1;
          }
          return ((await r.json()).content ?? []).filter(
            (e) =>
              e.tipoAccion === 'CREACION' &&
              e.motivo === cambio.motivo &&
              String(e.valorNuevo ?? '').startsWith(`${cambio.titulo} (vigente desde `),
          ).length;
        },
        { timeout: 30_000, message: 'la publicación está en la bitácora una vez' },
      )
      .toBe(1);
  });

  test('4 · negativos por rol y por datos; el Administrador sí publica', async () => {
    for (const sinPermiso of [jugadora, moderadora]) {
      const r = await publicar(sinPermiso, cambio);
      expect(r.status(), `${sinPermiso.claims.rol} no publica`).toBe(403);
      expect((await r.json()).motivo).toBe('PERMISO_INSUFICIENTE');
    }
    expect((await publicar(null, cambio)).status(), 'sin sesión').toBe(401);

    const invalidos = [
      { ...cambio, politica: 'NORMAS_INVENTADAS' },
      { ...cambio, motivo: '' },
      { ...cambio, titulo: 'x' },
      { ...cambio, vigenteDesde: new Date(Date.now() - 24 * 3600 * 1000).toISOString() },
    ];
    for (const cuerpo of invalidos) {
      const r = await publicar(admin, cuerpo);
      expect(r.status(), JSON.stringify(cuerpo)).toBe(400);
      expect((await r.json()).motivo).toBe('SOLICITUD_INVALIDA');
    }

    const delAdministrador = await publicar(admin, {
      politica: 'TERMINOS_Y_CONDICIONES',
      titulo: `Reglas del chat ${SUFIJO}`,
      descripcion: 'Los enlaces externos en el chat se ocultan hasta que un moderador los revise.',
      motivo: `Acuerdo del comité (E2E ${SUFIJO})`,
    });
    expect(delAdministrador.status(), await delAdministrador.text()).toBe(201);
  });

  test('5 · la vista de notificaciones lo pinta con su categoría en palabras', async ({ page }) => {
    await page.addInitScript(
      ([token, apodo, uid]) => {
        sessionStorage.setItem('nexus.token', token);
        sessionStorage.setItem('nexus.apodoActual', apodo);
        sessionStorage.setItem('nexus.usuarioId', uid);
      },
      [jugadora.token, jugadora.apodo, jugadora.claims.uid],
    );
    await page.goto(`${BORDE}${NOTIFICACIONES}`);

    const item = page.locator(`[data-zona="lista"] li[data-aviso-id="politica:${publicado.id}"]`);
    await expect(item).toBeVisible({ timeout: 20_000 });
    await expect(item.locator('.tarjeta__titulo')).toHaveText(
      'Actualizamos la política de privacidad',
    );
    await expect(item.locator('.distintivo')).toHaveText('Políticas de uso');
    await expect(item).toContainText(cambio.descripcion);
    await expect(item).not.toContainText('CAMBIO_POLITICA');
  });

  test('6 · el Super Administrador lo publica desde la consola, viendo antes el aviso', async ({
    page,
  }) => {
    const titulo = `Uso de los apodos ${SUFIJO}`;
    const descripcion = `Los apodos que suplanten a otra persona se cambiarán sin aviso (${SUFIJO}).`;
    await entrarEnLaPagina(page, superAdmin);
    await page.goto(`${BORDE}/consola/parametros`);

    const panel = page.locator('#politicas-de-uso');
    await expect(panel).toBeVisible({ timeout: 20_000 });
    await panel.locator('[data-accion="publicar-cambio-de-politica"]').click();

    const formulario = page.locator('[data-zona="publicacion"]');
    await expect(formulario).toBeVisible();
    await formulario.locator('[name="politica"]').selectOption('TERMINOS_Y_CONDICIONES');
    await formulario.locator('[name="titulo"]').fill(titulo);
    await formulario.locator('[name="descripcion"]').fill(descripcion);
    await formulario
      .locator('[name="motivo"]')
      .fill(`Acuerdo con el equipo de comunidad (E2E ${SUFIJO}).`);

    // Antes de publicar: el aviso como lo verá cada jugador y lo que va a pasar.
    const previa = page.locator('[data-zona="vista-previa-del-aviso"]');
    await expect(previa).toContainText('Actualizamos los términos y condiciones del servicio');
    await expect(previa).toContainText(titulo);
    await expect(page.locator('[data-zona="consecuencia"]')).toContainText('no se puede retirar');

    await page.locator('[data-accion="confirmar-publicacion"]').click();
    await expect(page.getByText('Cambio publicado')).toBeVisible({ timeout: 20_000 });
    await expect(panel).toContainText(titulo, { timeout: 20_000 });

    const lectura = await api.get(POLITICAS);
    expect(lectura.status()).toBe(200);
    const cambios = await lectura.json();
    const delPanel = cambios.find((c) => c.titulo === titulo);
    expect(delPanel, 'lo publicado desde la consola está en la lectura pública').toBeTruthy();
    expect(delPanel.politica).toBe('TERMINOS_Y_CONDICIONES');
    expect(delPanel).not.toHaveProperty('motivo');
  });
});
