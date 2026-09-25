/**
 * B6 — Mensajes privados entre jugadores, de punta a punta por el borde.
 *
 * FEEDBACK DEL PROFESOR, no requisito del documento: la sección 7.6 pide chat
 * en las salas y en la vista general. Los mensajes privados van sobre ese
 * mismo chat —el mismo servicio (salas-partidas) y el mismo canal STOMP— y con
 * sus mismas reglas: lista negra, sanción activa y fallo cerrado.
 *
 * Contra los servicios reales del banco (`tests/e2e/compose.yml`):
 *
 *   1. sin sesión: 401 por REST, y la vista manda al login
 *   2. a quien no está conectado le llega un aviso a su bandeja, sin el texto;
 *      el sexto mensaje en 10 s se rechaza con 429 y Retry-After
 *   3. A escribe a B desde la vista: B, con la suya abierta, lo recibe por
 *      /usuario/cola/mensajes-directos sin pedir nada por REST; C, también
 *      conectada, no recibe nada
 *   4. queda en el historial de los dos, con el remitente del token, y B lo
 *      vuelve a ver al recargar
 *   5. C no puede leer la conversación de A y B: por REST solo ve las suyas, y
 *      por STOMP el broker le niega suscribirse a colas ajenas
 *   6. A no puede hacerse pasar por C ni escribirse a sí misma
 *   7. un término de la lista negra (MENSAJE_PRIVADO → BLOQUEAR) no se entrega,
 *      ni por REST ni desde la vista
 *   8. «Mensaje privado» junto al apodo, en el chat general, abre la conversación
 *
 * ## De qué depende
 *
 * - `GET /api/v1/internal/usuarios/{uid}/contacto` de ms-identidad
 *   (`contracts/openapi/ms-identidad-admin.yaml`): salas-partidas lo consulta
 *   con su credencial de servicio para saber si el destinatario existe y está
 *   ACTIVO. Lo implementa en paralelo la rama de identidad. Sin él, TODO envío
 *   se rechaza —404 destinatario-inexistente o 503 moderacion-no-disponible—,
 *   y es a propósito: fallo cerrado. Cada envío que debía salir dice qué mirar.
 * - Registro: en esta rama la cuenta queda ACTIVA sin verificar el correo. Si
 *   se exige la verificación antes del login, `sesionDe` tendrá que hacerla
 *   como la haga el resto del banco.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const ADMIN = process.env.E2E_ADMIN ?? 'admin_e2e';
const CLAVE = 'Contrasena-E2E-2026';

// Jugadores de este spec y de ningún otro: así sus conversaciones, sus no
// leídos y su límite de frecuencia no se cruzan con el resto del banco.
const ANA = process.env.E2E_DM_ANA ?? 'dm_ana_e2e';
const BRUNO = process.env.E2E_DM_BRUNO ?? 'dm_bruno_e2e';
const CARLA = process.env.E2E_DM_CARLA ?? 'dm_carla_e2e';
const RAPIDA = process.env.E2E_DM_RAPIDA ?? 'dm_rapida_e2e';

const API = '/api/v1/mensajes-directos/conversaciones';
const VISTA = '/mensajes';
const CHAT = '/frontend/app-web/src/plataforma/salas-partidas/chat.html';
const COLA = '/usuario/cola/mensajes-directos';

/**
 * Límite provisional por remitente (decisión del PO pendiente): 5 mensajes
 * cada 10 s. El banco no fija MENSAJES_DIRECTOS_LIMITE_*, así que vale este.
 */
const LIMITE = 5;

/** Distingue los textos de esta corrida de los de otras sobre el mismo banco. */
const SUFIJO = Date.now().toString(36);
const SALUDO = `hola bruno, soy ana ${SUFIJO}`;

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

/** Deja la sesión en el navegador como la deja login.js. */
async function conSesion(page, jugador) {
  await page.addInitScript(
    ([token, apodo, uid]) => {
      sessionStorage.setItem('nexus.token', token);
      sessionStorage.setItem('nexus.apodoActual', apodo);
      sessionStorage.setItem('nexus.usuarioId', uid);
    },
    [jugador.token, jugador.apodo, jugador.claims.uid],
  );
}

/** La clave canónica del contrato: `dm:{uidMenor}:{uidMayor}`. */
function claveEntre(uno, otro) {
  const [menor, mayor] = [uno.claims.uid, otro.claims.uid].sort();
  return `dm:${menor}:${mayor}`;
}

/** Solo letras: la lista negra normaliza los dígitos (0→o, 1→i, 3→e…). */
function soloLetras(numero) {
  return numero.toString(36).replace(/\d/g, (d) => 'abcdefghij'[Number(d)]);
}

/** Qué mirar cuando un envío que debía salir no sale. */
function porQueNoSalio(estado, cuerpo) {
  return (
    `el envío debía salir y respondió ${estado}: ${cuerpo}. Con 404 destinatario-inexistente o ` +
    '503 moderacion-no-disponible, mirar que ms-identidad publique ' +
    'GET /api/v1/internal/usuarios/{uid}/contacto, que acepte la credencial de salas-partidas ' +
    'y que salas-partidas tenga IDENTIDAD_URL.'
  );
}

/** Envío de respaldo por REST: el mismo camino de reglas que el de STOMP. */
function enviar(api, de, para, texto, extra = {}) {
  return api.post(`${API}/${para.claims.uid}/mensajes`, {
    headers: conToken(de.token),
    data: { texto, ...extra },
  });
}

async function enviarBien(api, de, para, texto, extra = {}) {
  const respuesta = await enviar(api, de, para, texto, extra);
  const cuerpo = await respuesta.text();
  expect(respuesta.status(), porQueNoSalio(respuesta.status(), cuerpo)).toBe(201);
  return JSON.parse(cuerpo);
}

/** La conversación de `quien` con `otro`, vista por `quien` (su token decide cuál). */
async function historialEntre(api, quien, otro) {
  const respuesta = await api.get(`${API}/${otro.claims.uid}/mensajes?limite=100`, {
    headers: conToken(quien.token),
  });
  expect(respuesta.status(), await respuesta.text()).toBe(200);
  return respuesta.json();
}

async function conversacionesDe(api, quien) {
  const respuesta = await api.get(API, { headers: conToken(quien.token) });
  expect(respuesta.status(), await respuesta.text()).toBe(200);
  return respuesta.json();
}

/**
 * Una pestaña con la sesión de ese jugador, en su propio contexto (nada
 * compartido con las demás), que apunta los frames STOMP y las peticiones.
 */
async function pestanaDe(browser, jugador, ruta) {
  const contexto = await browser.newContext({ baseURL: BORDE });
  const pagina = await contexto.newPage();
  await conSesion(pagina, jugador);
  const frames = { recibidos: [], enviados: [] };
  pagina.on('websocket', (ws) => {
    ws.on('framereceived', (frame) => frames.recibidos.push(String(frame.payload)));
    ws.on('framesent', (frame) => frames.enviados.push(String(frame.payload)));
  });
  const peticiones = [];
  pagina.on('request', (peticion) =>
    peticiones.push({
      metodo: peticion.method(),
      ruta: new URL(peticion.url()).pathname,
      en: Date.now(),
    }),
  );
  await pagina.goto(ruta);
  return { contexto, pagina, frames, peticiones };
}

/** Conectada y con su cola pedida: desde aquí, lo que le escriban le llega. */
async function escuchando(pestana) {
  await expect(pestana.pagina.locator('[data-zona="conexion"]')).toContainText('Conectado', {
    timeout: 20_000,
  });
  await expect
    .poll(
      () =>
        pestana.frames.enviados.some(
          (frame) => /^\s*SUBSCRIBE/.test(frame) && frame.includes(`destination:${COLA}`),
        ),
      { timeout: 20_000 },
    )
    .toBe(true);
}

test.describe('Mensajes privados entre jugadores (B6, feedback del profesor)', () => {
  test.describe.configure({ mode: 'serial' });

  let api;
  let ana;
  let bruno;
  let carla;
  let rapida;
  let admin;

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    ana = await sesionDe(api, ANA);
    bruno = await sesionDe(api, BRUNO);
    carla = await sesionDe(api, CARLA);
    rapida = await sesionDe(api, RAPIDA);
    admin = await sesionDe(api, ADMIN);
    expect(admin.claims.rol, 'sembrar.sh deja a admin_e2e como ADMINISTRADOR').toBe(
      'ADMINISTRADOR',
    );
  });

  test.afterAll(async () => {
    await api?.dispose();
  });

  test('sin sesión no hay mensajes privados: 401 por REST y la vista manda al login', async ({
    page,
  }) => {
    expect((await api.get(API)).status()).toBe(401);
    expect(
      (await api.post(`${API}/${bruno.claims.uid}/mensajes`, { data: { texto: 'hola' } })).status(),
    ).toBe(401);

    await page.goto(VISTA);
    await page.waitForURL(/\/login/, { timeout: 20_000 });
    expect(new URL(page.url()).searchParams.get('volver') ?? '').toContain('mensajes');
  });

  test('a quien no está conectado le llega un aviso sin el texto; el sexto mensaje en 10 s es 429', async () => {
    // B todavía no ha abierto ninguna vista en este spec: no escucha. Se
    // parte de cero no leídos de la rápida para que este mensaje abra la
    // racha —el aviso lleva el id del primer no leído— también cuando el
    // banco se reutiliza entre corridas.
    const leido = await api.post(`${API}/${rapida.claims.uid}/leido`, {
      headers: conToken(bruno.token),
    });
    expect(leido.status()).toBe(204);

    const texto = `primero, sin nadie escuchando ${SUFIJO}`;
    const primero = await enviarBien(api, rapida, bruno, texto, { idCliente: `rapida-${SUFIJO}` });
    expect(primero).toMatchObject({
      remitente: rapida.claims.uid,
      destinatario: bruno.claims.uid,
      texto,
    });

    // El aviso lo manda salas-partidas en segundo plano, con su credencial.
    const bandeja = `/api/v1/users/${bruno.claims.uid}/notifications`;
    const avisoDelPrimero = async () => {
      const respuesta = await api.get(bandeja, { headers: conToken(bruno.token) });
      if (respuesta.status() !== 200) {
        return null;
      }
      const { avisos } = await respuesta.json();
      return avisos.find((aviso) => aviso.id === `mensaje-directo-${primero.id}`) ?? null;
    };
    await expect
      .poll(avisoDelPrimero, {
        timeout: 20_000,
        message:
          'salas-partidas avisa por POST /internal/notifications, con su credencial de servicio, ' +
          'a quien no escucha: mirar NOTIFICACIONES_URL y que notificaciones acepte ROLE_SERVICIO',
      })
      .toMatchObject({ tipo: 'MENSAJE_PRIVADO', titulo: 'Nuevo mensaje privado' });
    const aviso = await avisoDelPrimero();
    expect(aviso.cuerpo).toContain(RAPIDA);
    expect(aviso.cuerpo, 'el aviso no lleva el texto del mensaje').not.toContain(texto);

    // Límite: el primero ya cuenta. Los cinco siguientes salen a la vez —así
    // llegan todos dentro de la ventana aunque el banco vaya lento— y
    // exactamente uno sobra.
    const rafaga = await Promise.all(
      Array.from({ length: LIMITE }, (_, i) => enviar(api, rapida, bruno, `ráfaga ${i} ${SUFIJO}`)),
    );
    const estados = rafaga.map((respuesta) => respuesta.status()).sort((x, y) => x - y);
    expect(estados).toEqual([...Array(LIMITE - 1).fill(201), 429]);

    const rechazado = rafaga.find((respuesta) => respuesta.status() === 429);
    const reintentar = Number(rechazado.headers()['retry-after']);
    expect(reintentar).toBeGreaterThanOrEqual(1);
    expect(reintentar).toBeLessThanOrEqual(10);
    expect((await rechazado.json()).type).toMatch(/\/demasiados-mensajes$/);

    const guardados = (await historialEntre(api, bruno, rapida)).filter(
      (mensaje) => mensaje.texto.startsWith('ráfaga ') && mensaje.texto.endsWith(SUFIJO),
    );
    expect(guardados, 'el rechazado no se guardó').toHaveLength(LIMITE - 1);
  });

  test('A escribe a B desde la vista y B lo recibe al instante por su cola; C, conectada, no recibe nada', async ({
    browser,
  }) => {
    const b = await pestanaDe(browser, bruno, `${VISTA}?con=${ana.claims.uid}`);
    const c = await pestanaDe(browser, carla, VISTA);
    let a = null;
    try {
      await escuchando(b);
      await escuchando(c);
      // La pestaña de A tarda en cargar y conectar lo bastante para que el
      // servidor ya haya registrado las suscripciones de B y C.
      a = await pestanaDe(browser, ana, `${VISTA}?con=${bruno.claims.uid}`);
      await escuchando(a);
      await expect(a.pagina.locator('[data-zona="redactor"]')).toBeVisible();

      const desde = Date.now();
      await a.pagina.locator('[data-zona="redactor"] [name="texto"]').fill(SALUDO);
      await a.pagina.locator('[data-zona="redactor"] button[type="submit"]').click();

      // B: aparece en su hilo abierto...
      await expect(b.pagina.locator('[data-zona="lineas"] li', { hasText: SALUDO })).toBeVisible({
        timeout: 15_000,
      });
      await expect(b.pagina.locator('[data-zona="titulo-hilo"]')).toHaveText(
        `Conversación con ${ANA}`,
      );
      // ...porque llegó por STOMP a su cola, no porque la vista lo pidiera.
      expect(
        b.frames.recibidos.some(
          (frame) =>
            /^\s*MESSAGE/.test(frame) &&
            frame.includes('mensajes-directos') &&
            frame.includes(SALUDO),
        ),
      ).toBe(true);
      expect(
        b.peticiones.filter(
          (p) => p.en >= desde && p.metodo === 'GET' && p.ruta.includes('/mensajes-directos/'),
        ),
        'B no consultó nada: le llegó solo',
      ).toEqual([]);

      // A: su línea la confirma el eco del servidor, y salió por STOMP, no por
      // el POST de respaldo.
      await expect(
        a.pagina.locator('[data-zona="lineas"] li[data-estado="entregado"]', { hasText: SALUDO }),
      ).toBeVisible();
      await expect(a.pagina.locator('[data-zona="titulo-hilo"]')).toHaveText(
        `Conversación con ${BRUNO}`,
      );
      expect(
        a.peticiones.filter(
          (p) => p.metodo === 'POST' && p.ruta.startsWith(API) && p.ruta.endsWith('/mensajes'),
        ),
        'el envío fue por el canal, sin respaldo REST',
      ).toEqual([]);

      // C: nada. Se le da el mismo margen que tuvo B, y un poco más.
      await c.pagina.waitForTimeout(1500);
      expect(c.frames.recibidos.filter((frame) => frame.includes(SALUDO))).toEqual([]);
      await expect(c.pagina.locator('[data-zona="conversaciones"]')).not.toContainText(ANA);
    } finally {
      await a?.contexto.close();
      await b.contexto.close();
      await c.contexto.close();
    }
  });

  test('queda en el historial de los dos, con el remitente del token, y B lo vuelve a ver al recargar', async ({
    page,
  }) => {
    const enB = (await historialEntre(api, bruno, ana)).find((m) => m.texto === SALUDO);
    expect(enB, 'B lo tiene en su historial').toBeTruthy();
    expect(enB).toMatchObject({
      conversacion: claveEntre(ana, bruno),
      remitente: ana.claims.uid,
      apodoRemitente: ANA,
      destinatario: bruno.claims.uid,
    });
    expect(enB.idCliente ?? null, 'el idCliente solo lo ve quien escribió').toBeNull();

    const enA = (await historialEntre(api, ana, bruno)).find((m) => m.texto === SALUDO);
    expect(enA?.id).toBe(enB.id);
    expect(enA.idCliente, 'A sí ve el suyo').toBeTruthy();

    const deB = await conversacionesDe(api, bruno);
    expect(deB.find((r) => r.uidOtro === ana.claims.uid)).toMatchObject({ apodoOtro: ANA });

    await conSesion(page, bruno);
    await page.goto(`${VISTA}?con=${ana.claims.uid}`);
    await expect(page.locator('[data-zona="lineas"] li', { hasText: SALUDO })).toBeVisible({
      timeout: 20_000,
    });
  });

  test('C no puede leer la conversación de A y B: por REST solo ve las suyas y el broker le niega las colas ajenas', async ({
    browser,
  }) => {
    // La clave de la conversación sale del token de quien pregunta: C, pida
    // por A o por B, solo obtiene SU conversación con cada uno.
    for (const otro of [ana, bruno]) {
      const suya = await historialEntre(api, carla, otro);
      expect(suya.filter((m) => m.texto === SALUDO)).toEqual([]);
      for (const mensaje of suya) {
        expect(mensaje.conversacion).toContain(carla.claims.uid);
      }
    }
    // Y la clave dm:... de A y B en la ruta no es un uid: 400, sin datos.
    const porClave = await api.get(
      `${API}/${encodeURIComponent(claveEntre(ana, bruno))}/mensajes`,
      {
        headers: conToken(carla.token),
      },
    );
    expect(porClave.status()).toBe(400);
    expect(await porClave.text()).not.toContain(SALUDO);

    const deC = (await conversacionesDe(api, carla)).map((r) => r.uidOtro);
    expect(deC).not.toContain(ana.claims.uid);
    expect(deC).not.toContain(bruno.claims.uid);

    // Por STOMP: C abre un cliente propio y pide TODAS las colas con un
    // comodín. En un broker simple sin autorización eso recibiría lo de todo
    // el mundo; aquí el broker lo rechaza.
    const c = await pestanaDe(browser, carla, VISTA);
    try {
      await escuchando(c);
      const intento = c.pagina.evaluate(async (destino) => {
        const { conectarChat } =
          await import('/frontend/app-web/src/plataforma/salas-partidas/cliente-chat.js');
        const { urlDelCanal } =
          await import('/frontend/app-web/src/plataforma/salas-partidas/canal-sala.js');
        const cliente = await conectarChat({
          url: urlDelCanal(),
          token: sessionStorage.getItem('nexus.token'),
        });
        return new Promise((resolver) => {
          const recibidos = [];
          cliente.alError = (error) =>
            resolver({ rechazado: String(error?.message ?? error), recibidos });
          cliente.alCerrar = () => resolver({ rechazado: 'cerrado', recibidos });
          cliente.suscribir(destino, (cuerpo) => recibidos.push(JSON.stringify(cuerpo)));
          setTimeout(() => resolver({ rechazado: null, recibidos }), 8000);
        });
      }, '/cola/**');

      // Mientras tanto, A le escribe a B.
      await c.pagina.waitForTimeout(1000);
      const otroTexto = `otro para bruno ${SUFIJO}`;
      await enviarBien(api, ana, bruno, otroTexto);

      const resultado = await intento;
      expect(resultado.rechazado, 'el broker rechaza la suscripción con comodín').toBeTruthy();
      expect(resultado.recibidos.filter((cuerpo) => cuerpo.includes(otroTexto))).toEqual([]);
      expect(c.frames.recibidos.filter((frame) => frame.includes(otroTexto))).toEqual([]);
    } finally {
      await c.contexto.close();
    }
  });

  test('A no puede hacerse pasar por C ni escribirse a sí misma: el remitente sale del token', async () => {
    const texto = `firmado por carla, dice ana ${SUFIJO}`;
    const guardado = await enviarBien(api, ana, bruno, texto, {
      remitente: carla.claims.uid,
      idRemitente: carla.claims.uid,
      apodoRemitente: CARLA,
    });
    expect(guardado).toMatchObject({
      remitente: ana.claims.uid,
      apodoRemitente: ANA,
      destinatario: bruno.claims.uid,
    });

    const enB = (await historialEntre(api, bruno, ana)).find((m) => m.texto === texto);
    expect(enB).toMatchObject({ remitente: ana.claims.uid, apodoRemitente: ANA });
    expect(
      (await historialEntre(api, bruno, carla)).find((m) => m.texto === texto),
    ).toBeUndefined();

    const aSiMisma = await enviar(api, ana, ana, `a mí misma ${SUFIJO}`);
    expect(aSiMisma.status()).toBe(400);
    expect((await aSiMisma.json()).type).toMatch(/\/destinatario-propio$/);
  });

  test('un término de la lista negra no se entrega: 422 por REST y rechazo explicado en la vista', async ({
    browser,
  }) => {
    // La lista negra del banco trae términos de otras suites; se prueba con
    // uno que el administrador acaba de añadir y que se retira al final.
    const termino = `vetadodm${soloLetras(Date.now())}`;
    const alta = await api.post('/api/v1/lista-negra/terminos', {
      headers: conToken(admin.token),
      data: { termino },
    });
    expect([200, 201], await alta.text()).toContain(alta.status());

    let a = null;
    try {
      const porRest = await enviar(api, ana, bruno, `esto lleva ${termino} dentro`);
      const cuerpo = await porRest.text();
      expect(porRest.status(), cuerpo).toBe(422);
      expect(JSON.parse(cuerpo).type).toMatch(/\/contenido-bloqueado$/);

      a = await pestanaDe(browser, ana, `${VISTA}?con=${bruno.claims.uid}`);
      await escuchando(a);
      const desdeLaVista = `desde la vista: ${termino}`;
      await a.pagina.locator('[data-zona="redactor"] [name="texto"]').fill(desdeLaVista);
      await a.pagina.locator('[data-zona="redactor"] button[type="submit"]').click();

      await expect(a.pagina.locator('[data-zona="aviso-redactor"]')).toContainText(
        'Tu mensaje no se envió',
        { timeout: 15_000 },
      );
      await expect(
        a.pagina.locator('[data-zona="lineas"] li[data-estado="fallido"]', {
          hasText: desdeLaVista,
        }),
      ).toBeVisible();
      // El rechazo llegó por la cola, con su motivo.
      expect(
        a.frames.recibidos.some(
          (frame) => frame.includes('"RECHAZO"') && frame.includes('TEXTO_NO_PERMITIDO'),
        ),
      ).toBe(true);

      const deB = await historialEntre(api, bruno, ana);
      expect(
        deB.filter((m) => m.texto.includes(termino)),
        'a B no le llegó ninguno',
      ).toEqual([]);
    } finally {
      await a?.contexto.close();
      const baja = await api.delete(`/api/v1/lista-negra/terminos/${encodeURIComponent(termino)}`, {
        headers: conToken(admin.token),
      });
      expect([200, 204, 404]).toContain(baja.status());
    }
  });

  test('«Mensaje privado» junto al apodo, en el chat general, abre la conversación', async ({
    browser,
  }) => {
    const a = await pestanaDe(browser, ana, CHAT);
    let b = null;
    try {
      await expect(a.pagina.locator('[data-zona="conexion"]')).toHaveText(/conectado/i, {
        timeout: 20_000,
      });
      const texto = `quien quiera una 1v1, que me escriba ${SUFIJO}`;
      await a.pagina.fill('#formulario-chat [name="texto"]', texto);
      await a.pagina.click('#formulario-chat button[type="submit"]');
      const propio = a.pagina.locator('[data-zona="mensajes"] li', { hasText: texto });
      await expect(propio).toBeVisible({ timeout: 20_000 });
      await expect(propio.locator('[data-accion="mensaje-privado"]')).toHaveCount(0);

      b = await pestanaDe(browser, bruno, CHAT);
      const suyo = b.pagina.locator('[data-zona="mensajes"] li', { hasText: texto });
      await expect(suyo).toBeVisible({ timeout: 20_000 });
      const boton = suyo.locator('[data-accion="mensaje-privado"]');
      await expect(boton).toHaveAttribute('aria-label', `Enviar un mensaje privado a ${ANA}`);
      await boton.click();

      await b.pagina.waitForURL(/mensajes(\.html)?\?con=/, { timeout: 20_000 });
      expect(new URL(b.pagina.url()).searchParams.get('con')).toBe(ana.claims.uid);
      await expect(b.pagina.locator('[data-zona="titulo-hilo"]')).toHaveText(
        `Conversación con ${ANA}`,
      );
      await expect(b.pagina.locator('[data-zona="redactor"]')).toBeVisible();
    } finally {
      await b?.contexto.close();
      await a.contexto.close();
    }
  });
});
