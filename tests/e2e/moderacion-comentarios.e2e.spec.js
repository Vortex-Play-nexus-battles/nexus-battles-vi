// @ts-check
/**
 * Moderacion de comentarios de punta a punta — RF-COM-005, RF-COM-006 y
 * RF-COM-008 (R10.1), por el borde, con servicios reales.
 *
 * ## Que defecto de producto vigila esta prueba
 *
 * El estado EN_REVISION existia desde la primera migracion y el filtro
 * automatico metia comentarios ahi. Lo que no existia era la SALIDA: ni cola
 * donde aparecieran, ni accion que los resolviera. Un comentario retenido se
 * quedaba invisible para siempre y su autor no sabia por que.
 *
 * Una prueba que solo mirase «reportar devuelve 201» no habria visto nunca ese
 * agujero. Por eso esta recorre el camino entero y, sobre todo, comprueba que
 * el comentario SALE del estado en el que entro:
 *
 *   1. la anfitriona publica  -> PUBLICADO, visible en el hilo
 *   2. el invitado reporta    -> 201, pasa a EN_REVISION y DESAPARECE del hilo
 *   3. el invitado repite     -> 409 REPORTE_DUPLICADO (no un 500)
 *   4. el invitado pide cola  -> 403: quien reporta no decide
 *   5. la moderadora ve cola  -> ahi esta, con su reporte y su categoria
 *   6. la moderadora oculta   -> 200, OCULTO, asiento con SU uid (del token)
 *   7. el asiento queda       -> el historial del detalle lo trae: auditoria
 *   8. el autor se entera     -> el aviso esta en su bandeja de notificaciones
 *   9. otra decision igual    -> 409 TRANSICION_INVALIDA (otro se adelanto)
 *  10. la moderadora restaura -> PUBLICADO y VUELVE a verse en el hilo
 *  11. (B3, 7.3.3) la moderadora lo MARCA -> sale en la lista de seguimiento
 *      (cola con marcado=true) aunque este publicado; DESMARCAR lo saca
 *  12. (B3, 7.3.3) la moderadora lo EDITA -> el hilo ensena el texto nuevo,
 *      marcado como editado, y el asiento guarda el anterior
 *  13. (B3) lo mismo desde la pantalla: la consola lleva a «Comentarios», el
 *      comentario reportado de nuevo esta en la cola, se MARCA desde su
 *      detalle (y la pantalla dice que es una nota interna), sale en
 *      «Marcados para seguimiento» y se EDITA escribiendo el texto nuevo
 *
 * El paso 10 es el que cierra el defecto: sin el, todo lo anterior seria un
 * camino de ida a otro agujero.
 *
 * La moderadora y el administrador los deja sembrar.sh con su rol en la base
 * de identidad (crear un MODERADOR exige ser administrador, y ahi se rompe el
 * huevo-gallina insertando directo). Desde B3 el producto tiene que existir en
 * el catalogo: lo da de alta el administrador en cada corrida.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import { sesionDe as sesionDelBanco } from './ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const ANFITRION = process.env.E2E_ANFITRION ?? 'anfitriona_e2e';
const INVITADO = process.env.E2E_INVITADO ?? 'invitado_e2e';
const MODERADORA = process.env.E2E_MODERADORA ?? 'moderadora_e2e';
const ADMIN = process.env.E2E_ADMIN ?? 'admin_e2e';
const CLAVE = 'Contrasena-E2E-2026';

/**
 * B1 — la cuenta nace pendiente de verificar su correo. Registrar, leer el
 * codigo del buzon, confirmarlo y entrar viven en un solo sitio
 * (`ayudantes/cuentas.js`); aqui solo se fija la contrasena de este spec.
 */
function sesionDe(api, apodo) {
  return sesionDelBanco(api, apodo, { clave: CLAVE, base: BORDE });
}

function conToken(token) {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

/** B3 — solo se comenta un producto del catalogo: cada corrida da de alta el suyo. */
async function productoNuevo(api, admin) {
  const r = await api.post('/api/v1/productos', {
    headers: conToken(admin.token),
    data: {
      nombre: `Producto de moderacion E2E ${Date.now()}`,
      imagen: '/frontend/app-web/src/cuentas/avatares/arquero-cazador.jpg',
      descripcion: 'Producto de prueba del E2E de moderacion de comentarios.',
      tipo: 'ARMA',
      tiraje: -1,
      premium: false,
      precioCreditos: 10,
      poderDeAtaque: 5,
      tasaDeCaida: 10,
    },
  });
  expect(r.status(), await r.text()).toBe(201);
  return (await r.json()).id;
}

test.describe('Moderacion de comentarios: el comentario en revision tiene salida (RF-COM-005/006/008)', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let anfitriona;
  let invitado;
  let moderadora;

  let producto;
  const hiloDe = () => `/api/v1/products/${producto}/comments`;
  const COLA = '/api/v1/comentarios/moderacion';

  let comentario;

  async function hilo() {
    const r = await api.get(hiloDe());
    expect(r.status(), await r.text()).toBe(200);
    return r.json();
  }

  async function detalle() {
    const r = await api.get(`${COLA}/${comentario.id}`, { headers: conToken(moderadora.token) });
    expect(r.status(), await r.text()).toBe(200);
    return r.json();
  }

  async function decidir(quien, accion, motivo) {
    return api.post(`${COLA}/${comentario.id}/decision`, {
      headers: conToken(quien.token),
      data: { accion, motivo },
    });
  }

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    anfitriona = await sesionDe(api, ANFITRION);
    invitado = await sesionDe(api, INVITADO);
    moderadora = await sesionDe(api, MODERADORA);
    expect(moderadora.claims.rol, 'sembrar.sh deja a la moderadora con su rol').toBe('MODERADOR');
    expect(invitado.claims.rol).toBe('JUGADOR');
    producto = await productoNuevo(api, await sesionDe(api, ADMIN));
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  test('1-2: se publica, se reporta y el comentario desaparece del hilo publico', async () => {
    const publicado = await api.post(hiloDe(), {
      headers: conToken(anfitriona.token),
      data: { texto: 'Esta espada esta claramente rota, es injugable', estrellas: 1 },
    });
    expect(publicado.status(), await publicado.text()).toBe(201);
    comentario = await publicado.json();
    expect(comentario.estado).toBe('PUBLICADO');
    expect((await hilo()).comentarios.map((c) => c.id)).toContain(comentario.id);

    const reporte = await api.post(`${hiloDe()}/${comentario.id}/reportes`, {
      headers: conToken(invitado.token),
      // Se manda un reportanteId ajeno a proposito: el contrato no lo declara
      // y el servicio tiene que ignorarlo y quedarse con el uid del token.
      data: {
        reportanteId: anfitriona.claims.uid,
        categoria: 'INFORMACION_FALSA',
        descripcion: 'Dice que la espada esta rota y no lo esta',
      },
    });
    expect(reporte.status(), await reporte.text()).toBe(201);
    const cuerpo = await reporte.json();
    expect(cuerpo.estadoDelComentario, 'el primer reporte encola').toBe('EN_REVISION');
    expect(cuerpo.reportesTotales).toBe(1);

    const h = await hilo();
    expect(
      h.comentarios.map((c) => c.id),
      'un comentario en revision no se le pinta a nadie',
    ).not.toContain(comentario.id);
  });

  test('3-4: reportar dos veces es 409, y quien reporta no puede ver la cola', async () => {
    const repetido = await api.post(`${hiloDe()}/${comentario.id}/reportes`, {
      headers: conToken(invitado.token),
      data: { categoria: 'SPAM', descripcion: 'Otra vez' },
    });
    expect(repetido.status()).toBe(409);
    expect((await repetido.json()).motivo).toBe('REPORTE_DUPLICADO');

    const colaDeJugador = await api.get(COLA, { headers: conToken(invitado.token) });
    expect(colaDeJugador.status(), 'un JUGADOR reporta pero no resuelve').toBe(403);

    const decisionDeJugador = await decidir(invitado, 'APROBAR', 'Me parece bien');
    expect(decisionDeJugador.status()).toBe(403);

    const sinToken = await api.get(COLA);
    expect(sinToken.status()).toBe(401);
  });

  test('5: la moderadora lo encuentra en la cola con su reporte y su categoria', async () => {
    const r = await api.get(`${COLA}?productoId=${producto}`, {
      headers: conToken(moderadora.token),
    });
    expect(r.status(), await r.text()).toBe(200);
    const cola = await r.json();

    const entrada = cola.entradas.find((e) => e.comentario.id === comentario.id);
    expect(entrada, `el comentario no aparecio en la cola: ${JSON.stringify(cola)}`).toBeTruthy();
    expect(entrada.comentario.estado).toBe('EN_REVISION');
    expect(entrada.reportes).toBe(1);
    expect(entrada.porCategoria.INFORMACION_FALSA).toBe(1);

    const d = await detalle();
    expect(d.reportes).toHaveLength(1);
    expect(d.reportes[0].categoria).toBe('INFORMACION_FALSA');
    expect(d.historial, 'todavia no se ha decidido nada: el historial esta vacio').toHaveLength(0);
  });

  test('6-7: la moderadora oculta, el asiento lleva SU uid y queda en el historial', async () => {
    const r = await decidir(moderadora, 'OCULTAR', 'Afirmacion no verificable sobre el producto');
    expect(r.status(), await r.text()).toBe(200);
    const resuelto = await r.json();

    expect(resuelto.comentario.estado).toBe('OCULTO');
    expect(resuelto.asiento.accion).toBe('OCULTAR');
    expect(resuelto.asiento.estadoAnterior).toBe('EN_REVISION');
    expect(resuelto.asiento.estadoNuevo).toBe('OCULTO');
    expect(resuelto.asiento.moderadorId, 'quien firma sale del token').toBe(moderadora.claims.uid);
    expect(resuelto.asiento.apodoModerador).toBe(MODERADORA);
    expect(resuelto.asiento.motivo).toBe('Afirmacion no verificable sobre el producto');

    const d = await detalle();
    expect(d.comentario.estado).toBe('OCULTO');
    expect(d.historial, 'la decision deja rastro').toHaveLength(1);
    expect(d.historial[0].id).toBe(resuelto.asiento.id);

    const cola = await api.get(COLA, { headers: conToken(moderadora.token) });
    expect(
      (await cola.json()).entradas.map((e) => e.comentario.id),
      'resuelto es fuera de la cola: si no, se revisaria dos veces',
    ).not.toContain(comentario.id);
  });

  test('8: el autor se entera — el aviso esta en su bandeja', async () => {
    const r = await api.get(`/api/v1/users/${anfitriona.claims.uid}/notifications`, {
      headers: conToken(anfitriona.token),
    });
    expect(r.status(), await r.text()).toBe(200);
    const bandeja = await r.json();

    const aviso = bandeja.avisos.find((a) => a.tipo === 'MODERACION_COMENTARIO');
    expect(
      aviso,
      `sin aviso de moderacion en la bandeja: ${JSON.stringify(bandeja.avisos)}`,
    ).toBeTruthy();
    expect(aviso.titulo).toBe('Tu comentario se oculto');
    expect(aviso.cuerpo, 'el motivo viaja al autor: no se le oculta por que').toBe(
      'Afirmacion no verificable sobre el producto',
    );
  });

  test('9-10: repetir la decision es 409, y restaurar lo devuelve al hilo', async () => {
    const otraVez = await decidir(moderadora, 'OCULTAR', 'Lo vuelvo a ocultar');
    expect(otraVez.status(), 'ya no esta en revision: otro pudo adelantarse').toBe(409);
    expect((await otraVez.json()).motivo).toBe('TRANSICION_INVALIDA');

    const sinMotivo = await api.post(`${COLA}/${comentario.id}/decision`, {
      headers: conToken(moderadora.token),
      data: { accion: 'RESTAURAR' },
    });
    expect(sinMotivo.status(), 'no se archiva nada sin decir por que').toBe(400);

    const restaurado = await decidir(moderadora, 'RESTAURAR', 'Revisado: el comentario es opinion');
    expect(restaurado.status(), await restaurado.text()).toBe(200);
    expect((await restaurado.json()).comentario.estado).toBe('PUBLICADO');

    const h = await hilo();
    expect(
      h.comentarios.map((c) => c.id),
      'el comentario en revision TIENE salida: vuelve a verse',
    ).toContain(comentario.id);

    const d = await detalle();
    expect(d.historial, 'las dos decisiones, en orden').toHaveLength(2);
    expect(d.historial.map((a) => a.accion)).toEqual(['OCULTAR', 'RESTAURAR']);
  });

  test('11: marcarlo lo pone en seguimiento aunque este publicado; desmarcarlo lo saca (7.3.3)', async () => {
    const marcado = await decidir(moderadora, 'MARCAR', 'Seguimiento especial de este autor');
    expect(marcado.status(), await marcado.text()).toBe(200);
    const resuelto = await marcado.json();
    expect(resuelto.comentario.estado, 'marcar no cambia el estado').toBe('PUBLICADO');
    expect(resuelto.comentario.marcado).toBe(true);
    expect(resuelto.autorNotificado, 'es una nota interna: el autor no se entera').toBe(false);

    const seguimiento = await api.get(`${COLA}?marcado=true&productoId=${producto}`, {
      headers: conToken(moderadora.token),
    });
    expect(seguimiento.status()).toBe(200);
    expect((await seguimiento.json()).entradas.map((e) => e.comentario.id)).toContain(comentario.id);

    const h = await hilo();
    expect(
      h.comentarios.find((c) => c.id === comentario.id).marcado,
      'la marca no se le ensena a los jugadores',
    ).toBeUndefined();

    const otraVez = await decidir(moderadora, 'MARCAR', 'Otra vez');
    expect(otraVez.status(), 'ya estaba marcado').toBe(409);

    const desmarcado = await decidir(moderadora, 'DESMARCAR', 'Ya no hace falta vigilarlo');
    expect(desmarcado.status()).toBe(200);
    const despues = await api.get(`${COLA}?marcado=true&productoId=${producto}`, {
      headers: conToken(moderadora.token),
    });
    expect((await despues.json()).entradas.map((e) => e.comentario.id)).not.toContain(comentario.id);
  });

  test('12: editarlo cambia el texto visible, lo marca como editado y guarda el anterior (7.3.3)', async () => {
    const sinTexto = await decidir(moderadora, 'EDITAR', 'Falta el texto nuevo');
    expect(sinTexto.status(), 'EDITAR exige textoNuevo').toBe(400);

    const editado = await api.post(`${COLA}/${comentario.id}/decision`, {
      headers: conToken(moderadora.token),
      data: {
        accion: 'EDITAR',
        motivo: 'Se quita una afirmacion no verificable',
        textoNuevo: 'Esta espada no me convencio',
      },
    });
    expect(editado.status(), await editado.text()).toBe(200);
    const resuelto = await editado.json();
    expect(resuelto.asiento.textoAnterior).toBe('Esta espada esta claramente rota, es injugable');
    expect(resuelto.asiento.textoNuevo).toBe('Esta espada no me convencio');

    const enElHilo = (await hilo()).comentarios.find((c) => c.id === comentario.id);
    expect(enElHilo.texto).toBe('Esta espada no me convencio');
    expect(enElHilo.editado, 'quien lo lee sabe que moderacion lo cambio').toBe(true);

    const d = await detalle();
    expect(d.historial.map((a) => a.accion)).toEqual([
      'OCULTAR',
      'RESTAURAR',
      'MARCAR',
      'DESMARCAR',
      'EDITAR',
    ]);
  });

  test('13: desde la pantalla, la consola lleva a la cola; se marca, se filtra y se edita (B3)', async ({
    page,
  }) => {
    // Otro jugador lo reporta: vuelve a la cola de revision.
    const reportero = await sesionDe(api, `reportero_${Date.now().toString(36)}`);
    const reporte = await api.post(`${hiloDe()}/${comentario.id}/reportes`, {
      headers: conToken(reportero.token),
      data: { categoria: 'CONTENIDO_OFENSIVO', descripcion: 'Sigue sin gustarme' },
    });
    expect(reporte.status(), await reporte.text()).toBe(201);

    await page.addInitScript(
      ([token, nombre, uid]) => {
        sessionStorage.setItem('nexus.token', token);
        sessionStorage.setItem('nexus.apodoActual', nombre);
        sessionStorage.setItem('nexus.rolActual', 'MODERADOR');
        sessionStorage.setItem('nexus.usuarioId', uid);
      },
      [moderadora.token, MODERADORA, moderadora.claims.uid],
    );

    // La consola lleva a la cola de comentarios: antes solo se llegaba
    // escribiendo la direccion.
    await page.goto(`${BORDE}/frontend/app-web/src/plataforma/consola/consola.html`);
    const herramienta = page.locator('[data-herramienta="comentarios"]');
    await expect(herramienta).toBeVisible({ timeout: 20_000 });
    await expect(herramienta).toHaveAttribute('href', /moderar-comentarios\.html$/);

    await page.goto(
      `${BORDE}/frontend/app-web/src/plataforma/comentarios/moderar-comentarios.html?producto=${producto}`,
    );
    const tarjeta = page.locator(`[data-zona="cola"] [data-comentario-id="${comentario.id}"]`);
    await expect(tarjeta).toBeVisible({ timeout: 20_000 });
    await tarjeta.locator('[data-accion="revisar"]').click();

    // MARCAR con su motivo: nota interna, y el detalle se queda delante.
    const panel = page.locator('[data-zona="detalle"]');
    await panel.locator('#accion').selectOption('MARCAR');
    await expect(panel.locator('#motivo-pista')).toContainText('nota interna');
    await panel.locator('#motivo').fill('Seguimiento especial desde la pantalla');
    const marcado = page.waitForResponse((r) =>
      r.url().endsWith(`/comentarios/moderacion/${comentario.id}/decision`),
    );
    await panel.locator('[data-accion="decidir"]').click();
    const respuesta = await marcado;
    expect(respuesta.status()).toBe(200);
    expect((await respuesta.json()).autorNotificado).toBe(false);
    const aviso = page.locator('[data-zona="aviso"]');
    await expect(aviso).toContainText('Comentario marcado para seguimiento');
    await expect(aviso).toContainText('nota interna');
    await expect(panel.locator('[data-campo="marcado"]')).toBeVisible();

    // La lista de seguimiento lo trae.
    await page.locator('[data-zona="filtro"]').selectOption('marcados');
    await expect(
      page.locator(`[data-zona="cola"] [data-comentario-id="${comentario.id}"]`),
    ).toBeVisible();

    // EDITAR: el texto nuevo, con su motivo.
    await page
      .locator(`[data-zona="cola"] [data-comentario-id="${comentario.id}"] [data-accion="revisar"]`)
      .click();
    await panel.locator('#accion').selectOption('EDITAR');
    await panel.locator('#texto-nuevo').fill('No me convencio, pero es cuestion de gustos');
    await panel.locator('#motivo').fill('Se suaviza el tono');
    const editado = page.waitForResponse((r) =>
      r.url().endsWith(`/comentarios/moderacion/${comentario.id}/decision`),
    );
    await panel.locator('[data-accion="decidir"]').click();
    const cuerpo = (await editado).request().postDataJSON();
    expect(cuerpo).toEqual({
      accion: 'EDITAR',
      motivo: 'Se suaviza el tono',
      textoNuevo: 'No me convencio, pero es cuestion de gustos',
    });
    await expect(aviso).toContainText('Texto del comentario editado');
    await expect(panel.locator('[data-zona="historial"]')).toContainText(
      'Ahora: «No me convencio, pero es cuestion de gustos»',
    );

    const d = await detalle();
    expect(d.comentario.texto).toBe('No me convencio, pero es cuestion de gustos');
    expect(d.comentario.marcado).toBe(true);
  });
});
