// @ts-check
/**
 * Seguridad de las cuentas administrativas — 7.3.1, 7.3.5 y 7.3.9 del
 * documento oficial (SA-1), de punta a punta por el borde, con los servicios
 * reales del banco (ms-identidad, moderacion-sanciones y ms-cumplimiento).
 *
 *   1. ninguna sesión de Moderador, Administrador o Super Administrador se
 *      abre con la contraseña sola: hace falta el código de su aplicación;
 *   2. la consola renueva la sesión sin volver a autenticarse (`auth_time` y
 *      `amr` se conservan) y lo que no vale no se renueva;
 *   3. un jugador ascendido a moderador pierde su sesión anterior, se enrola
 *      al entrar y el cambio queda en la auditoría con su motivo;
 *   4. un moderador no puede quitarse el segundo factor;
 *   5. solo el Super Administrador lo restablece, con motivo, nunca el suyo;
 *   6. y 7. nadie sanciona ni gestiona un rol igual o superior, ni desde el
 *      panel de cuentas ni llamando directo a moderación;
 *   8. el endpoint de demostración que «baneaba» sin banear ya no existe;
 *   9. tres contraseñas equivocadas bloquean la cuenta, queda auditado y la
 *      alerta de cuentas bloqueadas de la consola la nombra (SA-20);
 *  10. la consola avisa antes de cerrar por inactividad, «Seguir conectado»
 *      renueva y, si nadie responde, el login dice que fue por inactividad.
 *      El tiempo del navegador lo adelanta `page.clock`: el token y el
 *      servidor son de verdad.
 *
 * Las cuentas administrativas son las de pruebas del banco (ADMINS_QA, con su
 * secreto TOTP ya enrolado); las que se ascienden, sancionan o bloquean son
 * jugadores creados aquí con sufijo de corrida. Nada se hace sobre las
 * cuentas compartidas que otros specs necesitan.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import {
  CLAVE_DEL_BANCO,
  cuentaAdministrativaDeRol,
  cuerpoDelToken,
  iniciarSesion,
  respetandoElLimite,
  sesionDe as sesionDelBanco,
  tipoDe,
} from './ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const SUFIJO = Date.now().toString(36);
/** Minutos de vida del token administrativo en el banco (compose.yml). */
const MINUTOS = Number(process.env.E2E_MINUTOS_SESION_ADMINISTRATIVA ?? 30);
const ACEPTA = 'application/problem+json, application/json';
const CONSOLA = '/frontend/app-web/src/plataforma/consola/consola.html';

function sesionDe(api, apodo) {
  return sesionDelBanco(api, apodo, { clave: CLAVE_DEL_BANCO, base: BORDE });
}

function como(sesion, extra = {}) {
  return { headers: { Accept: ACEPTA, Authorization: `Bearer ${sesion.token}`, ...extra } };
}

async function leer(respuesta) {
  const texto = await respuesta.text();
  let cuerpo = null;
  try {
    cuerpo = JSON.parse(texto);
  } catch {
    cuerpo = texto;
  }
  return { estado: respuesta.status(), cuerpo, tipo: tipoDe(cuerpo), texto };
}

/** Mañana a esta hora, como lo escribe el campo `datetime-local` del panel. */
function manana() {
  const fecha = new Date(Date.now() + 24 * 3600_000);
  const dos = (n) => String(n).padStart(2, '0');
  return `${fecha.getFullYear()}-${dos(fecha.getMonth() + 1)}-${dos(fecha.getDate())}T${dos(
    fecha.getHours(),
  )}:${dos(fecha.getMinutes())}`;
}

/**
 * Las entradas de la bitácora desde el inicio de la corrida (ms-cumplimiento),
 * consultadas como Super Administrador, la más reciente primero.
 */
async function bitacora(api, superAdmin, filtros) {
  const consulta = new URLSearchParams({ size: '100', sort: 'fechaHora,desc', ...filtros });
  const r = await api.get(`/api/v1/admin/auditoria?${consulta}`, como(superAdmin));
  expect(r.status(), await r.text()).toBe(200);
  return (await r.json()).content ?? [];
}

test.describe('7.3.9 · seguridad de las cuentas administrativas', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let moderadora;
  let admin;
  let superAdmin;
  /** El jugador que se asciende a moderador (pruebas 3 a 6). */
  let ascendido;
  const desde = new Date(Date.now() - 60_000).toISOString();

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    moderadora = await sesionDe(api, cuentaAdministrativaDeRol('MODERADOR').apodo);
    admin = await sesionDe(api, cuentaAdministrativaDeRol('ADMINISTRADOR').apodo);
    superAdmin = await sesionDe(api, cuentaAdministrativaDeRol('SUPER_ADMINISTRADOR').apodo);
    expect([moderadora.claims.rol, admin.claims.rol, superAdmin.claims.rol]).toEqual([
      'MODERADOR',
      'ADMINISTRADOR',
      'SUPER_ADMINISTRADOR',
    ]);
  });

  test.afterAll(async () => {
    await api?.dispose();
  });

  test('1 · la contraseña sola no abre una sesión administrativa: hace falta el código', async () => {
    for (const rol of ['MODERADOR', 'ADMINISTRADOR', 'SUPER_ADMINISTRADOR']) {
      const cuenta = cuentaAdministrativaDeRol(rol);
      const login = await iniciarSesion(api, cuenta.email, cuenta.clave);
      expect(login.estado, `${rol}: ${login.texto}`).toBe(403);
      expect(login.tipo).toBe('segundo-factor-requerido');
      expect(typeof login.cuerpo.desafio).toBe('string');
      expect(login.cuerpo.token, 'un 403 nunca trae sesión').toBeUndefined();
    }
    for (const sesion of [moderadora, admin, superAdmin]) {
      expect(sesion.claims.amr).toEqual(['pwd', 'otp']);
      expect(typeof sesion.claims.auth_time).toBe('number');
      // 15 min en DEV y producción; en el banco, los del compose.
      expect(sesion.claims.exp - sesion.claims.iat).toBe(MINUTOS * 60);
    }
  });

  test('2 · la renovación conserva la autenticación; lo que no vale no se renueva', async () => {
    const r = await respetandoElLimite(() =>
      api.post('/api/v1/auth/sesion/renovacion', como(admin)),
    );
    expect(r.status(), await r.text()).toBe(200);
    expect(r.headers()['cache-control']).toContain('no-store');
    const { token, expiraEn } = await r.json();
    const antes = admin.claims;
    const despues = cuerpoDelToken(token);
    expect(despues.auth_time).toBe(antes.auth_time);
    expect(despues.amr).toEqual(antes.amr);
    expect(despues.ver).toBe(antes.ver);
    expect(despues.uid).toBe(antes.uid);
    expect(despues.exp).toBeGreaterThanOrEqual(antes.exp);
    expect(Date.parse(expiraEn)).toBe(despues.exp * 1000);
    admin = { ...admin, token, claims: despues };

    for (const autorizacion of [null, 'Bearer no-es-un-token']) {
      const rechazo = await leer(
        await api.post('/api/v1/auth/sesion/renovacion', {
          headers: autorizacion
            ? { Accept: ACEPTA, Authorization: autorizacion }
            : { Accept: ACEPTA },
        }),
      );
      expect(rechazo.estado).toBe(401);
      expect(rechazo.tipo).toBe('sesion-no-renovable');
    }
  });

  test('3 · un jugador ascendido a moderador pierde su sesión, se enrola al entrar y queda auditado', async () => {
    const apodo = `asc_${SUFIJO}`;
    const jugador = await sesionDe(api, apodo);
    expect(jugador.claims.rol).toBe('JUGADOR');

    const cambio = await leer(
      await api.put(`/api/v1/rbac/usuarios/${jugador.usuarioId}/rol`, {
        ...como(superAdmin, { 'Content-Type': 'application/json' }),
        data: { nuevoRol: 'MODERADOR', motivo: `Refuerzo del equipo de moderación ${SUFIJO}` },
      }),
    );
    expect(cambio.estado, cambio.texto).toBe(200);

    // La sesión de jugador ya no vale: la versión del token subió.
    const vieja = await api.get('/api/v1/auth/onboarding', como(jugador));
    expect(vieja.status()).toBe(403);

    // Con la contraseña sola, ahora pide enrolar el segundo factor.
    const login = await iniciarSesion(api, jugador.email, CLAVE_DEL_BANCO);
    expect(login.estado).toBe(403);
    expect(login.tipo).toBe('segundo-factor-enrolamiento-requerido');

    ascendido = await sesionDe(api, apodo);
    expect(ascendido.claims.rol).toBe('MODERADOR');
    expect(ascendido.claims.amr).toEqual(['pwd', 'otp']);

    const entradas = await bitacora(api, superAdmin, {
      tipoAccion: 'CAMBIO_ROL',
      administradorId: superAdmin.apodo,
      desde,
    });
    const asiento = entradas.find((e) => e.afectado === jugador.claims.uid);
    expect(asiento, 'el cambio de rol está en la bitácora por el uid de la cuenta').toBeTruthy();
    expect(asiento.valorAnterior).toBe('JUGADOR');
    expect(asiento.valorNuevo).toBe('MODERADOR');
    expect(asiento.motivo).toContain(`Refuerzo del equipo de moderación ${SUFIJO}`);
  });

  test('4 · un moderador no puede quitarse la verificación en dos pasos', async () => {
    const estado = await api.get('/api/v1/auth/segundo-factor', como(ascendido));
    expect(estado.status()).toBe(200);
    expect(await estado.json()).toMatchObject({ activo: true, obligatorio: true });

    // Se rechaza antes de comparar nada: ni la clave ni el código cuentan
    // como intento fallido.
    const quitar = await leer(
      await api.post('/api/v1/auth/segundo-factor/desactivacion', {
        ...como(ascendido, { 'Content-Type': 'application/json' }),
        data: { passwordActual: CLAVE_DEL_BANCO, codigo: '000000' },
      }),
    );
    expect(quitar.estado, quitar.texto).toBe(409);
    expect(quitar.tipo).toBe('segundo-factor-obligatorio');
  });

  test('5 · solo el Super Administrador restablece el segundo factor de otra cuenta, nunca el suyo', async () => {
    const ruta = (id) => `/api/v1/admin/usuarios/${id}/segundo-factor/restablecimiento`;
    const motivo = { motivo: `Perdió el teléfono y los códigos ${SUFIJO}` };
    const json = { 'Content-Type': 'application/json' };

    for (const quien of [admin, moderadora]) {
      const r = await api.post(ruta(ascendido.usuarioId), { ...como(quien, json), data: motivo });
      expect(r.status(), `${quien.apodo} no puede`).toBe(403);
    }
    const propio = await leer(
      await api.post(ruta(superAdmin.usuarioId), { ...como(superAdmin, json), data: motivo }),
    );
    expect(propio.estado).toBe(403);
    expect(propio.tipo).toBe('jerarquia-insuficiente');
    const sinMotivo = await api.post(ruta(ascendido.usuarioId), {
      ...como(superAdmin, json),
      data: { motivo: '' },
    });
    expect(sinMotivo.status()).toBe(400);

    const hecho = await api.post(ruta(ascendido.usuarioId), {
      ...como(superAdmin, json),
      data: motivo,
    });
    expect(hecho.status(), await hecho.text()).toBe(204);

    // Sus sesiones abiertas se cierran y el siguiente login le pide enrolarse.
    expect((await api.get('/api/v1/auth/onboarding', como(ascendido))).status()).toBe(403);
    const login = await iniciarSesion(api, ascendido.email, CLAVE_DEL_BANCO);
    expect(login.tipo).toBe('segundo-factor-enrolamiento-requerido');

    const entradas = await bitacora(api, superAdmin, {
      tipoAccion: 'ACTUALIZACION',
      administradorId: superAdmin.apodo,
      desde,
    });
    const asiento = entradas.find(
      (e) => e.afectado === ascendido.claims.uid && e.valorNuevo === 'segundo-factor=RESTABLECIDO',
    );
    expect(asiento, 'el restablecimiento está en la bitácora').toBeTruthy();
    expect(asiento.valorAnterior).toBe('segundo-factor=ACTIVO');
    expect(asiento.motivo).toBe(motivo.motivo);

    // Vuelve a enrolarse para las pruebas siguientes.
    ascendido = await sesionDe(api, ascendido.apodo);
    expect(ascendido.claims.amr).toEqual(['pwd', 'otp']);
  });

  test('6 · en el panel de cuentas nadie actúa sobre un rol igual o superior', async () => {
    const json = { 'Content-Type': 'application/json' };
    const suspender = (quien, id) =>
      api.put(`/api/v1/admin/usuarios/${id}/suspender`, {
        ...como(quien, json),
        data: { suspendidoHasta: manana(), motivo: `Prueba de jerarquía ${SUFIJO}` },
      });

    // Un administrador sobre el Super Administrador.
    const sobreSuper = await leer(await suspender(admin, superAdmin.usuarioId));
    expect(sobreSuper.estado, sobreSuper.texto).toBe(403);
    expect(sobreSuper.tipo).toBe('jerarquia-insuficiente');
    const clave = await leer(
      await api.post(
        `/api/v1/admin/usuarios/${superAdmin.usuarioId}/restablecer-password`,
        como(admin),
      ),
    );
    expect(clave.estado).toBe(403);
    expect(clave.tipo).toBe('jerarquia-insuficiente');

    // Un administrador sobre otro administrador (uno ascendido aquí).
    const otro = await sesionDe(api, `adm_${SUFIJO}`);
    const ascenso = await api.put(`/api/v1/rbac/usuarios/${otro.usuarioId}/rol`, {
      ...como(superAdmin, json),
      data: { nuevoRol: 'ADMINISTRADOR', motivo: `Segundo administrador ${SUFIJO}` },
    });
    expect(ascenso.status(), await ascenso.text()).toBe(200);
    const sobreIgual = await leer(await suspender(admin, otro.usuarioId));
    expect(sobreIgual.estado).toBe(403);
    expect(sobreIgual.tipo).toBe('jerarquia-insuficiente');

    // Sobre un rol inferior sí: el administrador suspende al moderador
    // ascendido. Un moderador no levanta suspensiones; el administrador sí.
    const suspension = await suspender(admin, ascendido.usuarioId);
    expect(suspension.status(), await suspension.text()).toBe(204);
    const porModeradora = await leer(
      await api.put(`/api/v1/admin/usuarios/${ascendido.usuarioId}/reactivar`, como(moderadora)),
    );
    expect(porModeradora.estado).toBe(403);
    const reactivacion = await api.put(
      `/api/v1/admin/usuarios/${ascendido.usuarioId}/reactivar`,
      como(admin),
    );
    expect(reactivacion.status(), await reactivacion.text()).toBe(204);

    // Nadie cambia su propio rol.
    const propio = await leer(
      await api.put(`/api/v1/rbac/usuarios/${superAdmin.usuarioId}/rol`, {
        ...como(superAdmin, json),
        data: { nuevoRol: 'ADMINISTRADOR', motivo: 'Me equivoqué de cuenta' },
      }),
    );
    expect(propio.estado).toBe(403);
    expect(propio.tipo).toBe('jerarquia-insuficiente');
  });

  test('7 · la misma jerarquía en moderación, llamando directo a POST /sanciones', async () => {
    const json = { 'Content-Type': 'application/json' };
    const advertencia = await leer(
      await api.post('/api/v1/sanciones', {
        ...como(moderadora, json),
        data: { usuarioId: admin.claims.uid, tipo: 'ADVERTENCIA', motivo: `Jerarquía ${SUFIJO}` },
      }),
    );
    expect(advertencia.estado, advertencia.texto).toBe(403);
    expect(advertencia.cuerpo.motivo).toBe('JERARQUIA_INSUFICIENTE');

    const baneo = await leer(
      await api.post('/api/v1/sanciones', {
        ...como(admin, json),
        data: {
          usuarioId: superAdmin.claims.uid,
          tipo: 'BANEO',
          motivo: `Jerarquía ${SUFIJO}`,
          confirmacion: true,
        },
      }),
    );
    expect(baneo.estado, baneo.texto).toBe(403);
    expect(baneo.cuerpo.motivo).toBe('JERARQUIA_INSUFICIENTE');

    // Y no quedó nada registrado contra ellos.
    for (const cuenta of [admin, superAdmin]) {
      const historial = await api.get(
        `/api/v1/sanciones/usuarios/${cuenta.claims.uid}`,
        como(superAdmin),
      );
      expect(historial.status()).toBe(200);
      const sanciones = await historial.json();
      expect(JSON.stringify(sanciones)).not.toContain(`Jerarquía ${SUFIJO}`);
    }
  });

  test('8 · el endpoint de demostración que respondía «baneado» sin banear ya no existe', async () => {
    const r = await api.post('/api/v1/admin/ban', {
      ...como(superAdmin, { 'Content-Type': 'application/json' }),
      data: { userId: 'nadie' },
    });
    expect(r.status()).toBe(404);
  });

  test('9 · tres contraseñas equivocadas bloquean la cuenta, y queda en la auditoría', async () => {
    const jugador = await sesionDe(api, `bloq_${SUFIJO}`);
    for (let i = 0; i < 3; i += 1) {
      const mala = await iniciarSesion(api, jugador.email, 'No-Es-La-Clave-2026');
      expect(mala.estado).toBe(401);
    }
    // Bloqueada: ni con la contraseña buena se compara nada.
    const buena = await iniciarSesion(api, jugador.email, CLAVE_DEL_BANCO);
    expect(buena.estado, buena.texto).toBe(423);
    expect(buena.tipo).toBe('cuenta-bloqueada');

    await expect
      .poll(
        async () => {
          const entradas = await bitacora(api, superAdmin, {
            tipoAccion: 'OTRO',
            administradorId: 'ms-identidad',
            desde,
          });
          return entradas.find(
            (e) => e.afectado === jugador.claims.uid && e.valorNuevo === 'BLOQUEADA_TEMPORALMENTE',
          )?.motivo;
        },
        { timeout: 20_000, message: 'el bloqueo está en la bitácora' },
      )
      .toBe('BLOQUEO_POR_INTENTOS_FALLIDOS');

    // SA-20 (§7.3.4, D-45 señal 1): la alerta de comportamiento sospechoso de
    // la consola la nombra, bloqueada ahora y sin su correo.
    const r = await api.get('/api/v1/admin/jugadores/bloqueos?ocultarPruebas=false', como(admin));
    expect(r.status(), await r.text()).toBe(200);
    const alerta = await r.json();
    const bloqueada = alerta.cuentas.find((c) => c.uid === jugador.claims.uid);
    expect(bloqueada, 'la cuenta recién bloqueada está en la alerta').toMatchObject({
      apodo: jugador.apodo,
      rol: 'JUGADOR',
      vigente: true,
    });
    expect(Date.parse(bloqueada.bloqueadaHasta)).toBeGreaterThan(Date.parse(bloqueada.bloqueadaEn));
    expect(alerta.total).toBeGreaterThanOrEqual(1);
    expect(JSON.stringify(bloqueada)).not.toContain(jugador.email);

    // Solo quien gestiona cuentas la lee.
    for (const sesion of [moderadora, jugador]) {
      const prohibida = await api.get('/api/v1/admin/jugadores/bloqueos', como(sesion));
      expect(prohibida.status(), sesion.apodo).toBe(403);
    }
  });

  test('10 · la consola avisa antes de cerrar por inactividad; sin respuesta, cierra y el login lo dice', async ({
    page,
  }) => {
    test.setTimeout(150_000);
    // El reloj del navegador, no el del servidor: el token es de verdad.
    await page.clock.install({ time: new Date() });
    const sesion = await sesionDe(api, cuentaAdministrativaDeRol('ADMINISTRADOR').apodo);
    await page.addInitScript(
      ([token, apodo, rol, uid]) => {
        // Una sola vez por pestaña: tras el cierre, el login no la recupera.
        if (sessionStorage.getItem('nexus.e2e.inyectada')) {
          return;
        }
        sessionStorage.setItem('nexus.e2e.inyectada', '1');
        sessionStorage.setItem('nexus.token', token);
        sessionStorage.setItem('nexus.apodoActual', apodo);
        sessionStorage.setItem('nexus.rolActual', rol);
        sessionStorage.setItem('nexus.usuarioId', uid);
      },
      [sesion.token, sesion.apodo, sesion.claims.rol, sesion.claims.uid],
    );
    // Lo que le queda al token en la hora del SERVIDOR, como la mide la página.
    // `page.clock` adelanta el navegador y no el servidor: para la consola es
    // un reloj descuadrado, y lo corrige con el desfase que mide al recibir
    // cada token (`nexus.desfaseDelReloj`, atado a su `iat`).
    const restante = () =>
      page.evaluate(() => {
        const token = sessionStorage.getItem('nexus.token');
        const claims = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
        let desfase = 0;
        try {
          const medido = JSON.parse(sessionStorage.getItem('nexus.desfaseDelReloj') ?? 'null');
          if (medido && medido.iat === claims.iat) {
            desfase = medido.ms;
          }
        } catch {
          desfase = 0;
        }
        return claims.exp * 1000 - (Date.now() - desfase);
      });
    const esRenovacion = (r) =>
      r.request().method() === 'POST' && r.url().includes('/api/v1/auth/sesion/renovacion');

    await page.goto(CONSOLA);
    await expect(page.locator('header[data-cabecera-app]')).not.toBeEmpty();

    // a) Abrir la página es actividad: al entrar en los últimos 5 minutos,
    //    la consola renueva sola.
    let renovada = page.waitForResponse(esRenovacion);
    await page.clock.runFor(Math.max(0, (await restante()) - 5 * 60_000) + 20_000);
    const primeraRenovacion = await renovada;
    expect(primeraRenovacion.status()).toBe(200);
    // La consola guarda el token que le dio la renovación. No basta con
    // «otro distinto del inicial»: `iat` y `exp` van al segundo y la firma
    // RS256 es determinista, así que una renovación en el mismo segundo que
    // el inicio de sesión devuelve un token idéntico.
    const { token: renovado } = await primeraRenovacion.json();
    expect(renovado).toBeTruthy();
    await expect
      .poll(() => page.evaluate(() => sessionStorage.getItem('nexus.token')))
      .toBe(renovado);

    // b) Sin tocar nada, un minuto antes del fin sale el aviso con la cuenta atrás.
    const aviso = page.locator('[data-zona="aviso-de-inactividad"]');
    await page.clock.runFor((await restante()) - 60_000 + 2_000);
    await expect(aviso).toBeVisible();
    await expect(aviso).toContainText('Tu sesión está por cerrarse');
    await expect(aviso).toContainText(/0:[0-5]\d/);
    await expect(page.locator('[data-accion="seguir-conectado"]')).toBeFocused();

    // c) «Seguir conectado» renueva y cierra el aviso.
    renovada = page.waitForResponse(esRenovacion);
    await page.locator('[data-accion="seguir-conectado"]').click();
    expect((await renovada).status()).toBe(200);
    await expect(aviso).toBeHidden();

    // d) Otra vez sin actividad: aviso y, al acabarse, cierre por inactividad.
    await page.clock.runFor((await restante()) - 60_000 + 2_000);
    await expect(aviso).toBeVisible();
    await page.clock.runFor(60_000);
    await page.waitForURL(/[?&]motivo=inactividad/, { timeout: 30_000 });
    await expect(page.locator('body')).toContainText('no hubo actividad');
    expect(await page.evaluate(() => sessionStorage.getItem('nexus.token'))).toBeNull();
  });
});
