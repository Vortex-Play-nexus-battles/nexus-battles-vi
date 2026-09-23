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
 *
 * El paso 10 es el que cierra el defecto: sin el, todo lo anterior seria un
 * camino de ida a otro agujero.
 *
 * La moderadora y el administrador los deja sembrar.sh con su rol en la base
 * de identidad (crear un MODERADOR exige ser administrador, y ahi se rompe el
 * huevo-gallina insertando directo).
 */

import { test, expect, request as apiRequest } from '@playwright/test';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const ANFITRION = process.env.E2E_ANFITRION ?? 'anfitriona_e2e';
const INVITADO = process.env.E2E_INVITADO ?? 'invitado_e2e';
const MODERADORA = process.env.E2E_MODERADORA ?? 'moderadora_e2e';
const CLAVE = 'Contrasena-E2E-2026';

function cuerpoDelToken(jwt) {
  const base64 = jwt.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
  return JSON.parse(Buffer.from(base64, 'base64').toString('utf8'));
}

async function sesionDe(api, apodo) {
  const email = `${apodo}@nexus.test`;
  const registro = await api.post('/api/v1/auth/registro', {
    multipart: { nombres: 'Jugadora', apellidos: 'De Prueba', email, password: CLAVE, apodo },
  });
  expect([200, 201, 400, 409]).toContain(registro.status());
  const login = await api.post('/api/v1/auth/login', { data: { email, password: CLAVE } });
  expect(login.status(), `login de ${apodo}: ${await login.text()}`).toBe(200);
  const cuerpo = await login.json();
  return { ...cuerpo, apodo, claims: cuerpoDelToken(cuerpo.token) };
}

function conToken(token) {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

test.describe('Moderacion de comentarios: el comentario en revision tiene salida (RF-COM-005/006/008)', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let anfitriona;
  let invitado;
  let moderadora;

  const producto = `producto-moderacion-${Date.now()}`;
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
});
