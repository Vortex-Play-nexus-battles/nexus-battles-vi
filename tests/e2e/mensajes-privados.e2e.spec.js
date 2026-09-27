/**
 * B6 — Mensajes privados entre jugadores, de punta a punta por el borde.
 *
 * FEEDBACK DEL PROFESOR, no requisito del documento: la sección 7.6 pide chat
 * en las salas y en la vista general. Los mensajes privados van sobre ese
 * mismo chat —el mismo servicio (salas-partidas) y el mismo canal STOMP— y con
 * sus mismas reglas: lista negra, sanción activa y fallo cerrado.
 *
 * La interfaz es UNA: la pestaña «Mensajes privados» del chat
 * (`chat.html#privados`, `mensajes-privados.js`, UXC-6), que habla con el
 * servicio por su fuente (`fuente-mensajes.js`). La vista suelta
 * `mensajes.html` que B6 había hecho aparte ya no existe.
 *
 * Contra los servicios reales del banco (`tests/e2e/compose.yml`):
 *
 *   1. sin sesión: 401 por REST, y la pestaña manda al login
 *   2. a quien no está conectado le llega un aviso a su bandeja, sin el texto;
 *      el sexto mensaje en 10 s se rechaza con 429 y Retry-After
 *   3. A escribe a B desde la pestaña: B, con la suya abierta, lo recibe por
 *      /usuario/cola/mensajes-directos sin pedir nada por REST; C, también
 *      conectada, no recibe nada
 *   4. queda en el historial de los dos, con el remitente del token, y B lo
 *      vuelve a ver al recargar
 *   5. C no puede leer la conversación de A y B: por REST solo ve las suyas, y
 *      por STOMP el broker le niega suscribirse a colas ajenas
 *   6. A no puede hacerse pasar por C ni escribirse a sí misma
 *   7. un término de la lista negra (MENSAJE_PRIVADO → BLOQUEAR) no se entrega,
 *      ni por REST ni desde la pestaña, que dice por qué y devuelve el texto
 *   8. buscar a un jugador por su apodo y escribirle: a él la conversación le
 *      aparece en su lista en ese momento, con su no leído
 *   9. bloquear todavía no existe (decisión del PO pendiente): la pestaña lo
 *      dice y la conversación sigue abierta, sin fingir un bloqueo
 *
 * ## De qué depende
 *
 * - `GET /api/v1/internal/usuarios/{uid}/contacto` de ms-identidad
 *   (`contracts/openapi/ms-identidad-admin.yaml`): salas-partidas lo consulta
 *   con su credencial de servicio para saber si el destinatario existe y está
 *   ACTIVO. Sin él, TODO envío se rechaza —404 destinatario-inexistente o 503
 *   moderacion-no-disponible—, y es a propósito: fallo cerrado. Cada envío que
 *   debía salir dice qué mirar.
 * - `GET /api/v1/perfiles/publicos?apodo=` de ms-identidad
 *   (`contracts/openapi/ms-identidad-perfiles.yaml`), para el caso 8: lo
 *   implementa la rama de identidad de B6. Sin él, el buscador de la pestaña
 *   dice «No pudimos buscar ahora» y el caso lo explica.
 * - Registro (B1): la cuenta nace pendiente de verificar su correo; `sesionDe`
 *   usa el ayudante del banco (`ayudantes/cuentas.js`), que lee el código del
 *   buzón de pruebas y lo confirma antes de entrar.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import { sesionDe as sesionDelBanco } from './ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const ADMIN = process.env.E2E_ADMIN ?? 'admin_e2e';
const CLAVE = 'Contrasena-E2E-2026';

// Jugadores de este spec y de ningún otro: así sus conversaciones, sus no
// leídos y su límite de frecuencia no se cruzan con el resto del banco.
const ANA = process.env.E2E_DM_ANA ?? 'dm_ana_e2e';
const BRUNO = process.env.E2E_DM_BRUNO ?? 'dm_bruno_e2e';
const CARLA = process.env.E2E_DM_CARLA ?? 'dm_carla_e2e';
const RAPIDA = process.env.E2E_DM_RAPIDA ?? 'dm_rapida_e2e';
const DANI = process.env.E2E_DM_DANI ?? 'dm_dani_e2e';

const API = '/api/v1/mensajes-directos/conversaciones';
const CHAT = '/frontend/app-web/src/plataforma/salas-partidas/chat.html';
/** La pestaña «Mensajes privados» del chat, abierta desde la dirección. */
const PRIVADOS = `${CHAT}#privados`;
const COLA = '/usuario/cola/mensajes-directos';

/**
 * Límite provisional por remitente (decisión del PO pendiente): 5 mensajes
 * cada 10 s. El banco no fija MENSAJES_DIRECTOS_LIMITE_*, así que vale este.
 */
const LIMITE = 5;
/** La ventana de ese límite (10 s) y medio segundo de margen. */
const VENTANA_DEL_LIMITE_MS = 10_500;

/** Distingue los textos de esta corrida de los de otras sobre el mismo banco. */
const SUFIJO = Date.now().toString(36);
const SALUDO = `hola bruno, soy ana ${SUFIJO}`;

/**
 * B1 — una cuenta nueva nace pendiente de verificar su correo: la sesión la
 * da el ayudante del banco, que registra, lee el código del buzón de pruebas,
 * lo confirma y entra (el mismo camino que el resto de specs).
 */
function sesionDe(api, apodo) {
  return sesionDelBanco(api, apodo, { clave: CLAVE, base: BORDE });
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
async function pestanaDe(browser, jugador, ruta = PRIVADOS) {
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
  const privados = pagina.locator('.chat__privados');
  return { contexto, pagina, frames, peticiones, privados };
}

/**
 * La pestaña «Mensajes privados» con su servicio: la lista y el buscador, no
 * el aviso de «no están disponibles ahora».
 */
async function conServicio(pestana) {
  await expect(
    pestana.privados.locator('.mensajes-privados'),
    'la pestaña pregunta primero GET /api/v1/mensajes-directos/conversaciones por el borde: ' +
      'si sale el aviso de «no están disponibles ahora», esa ruta no contesta 200',
  ).toBeVisible({ timeout: 20_000 });
  // Y la lista ya cargó: ni esqueletos, ni el error con «Reintentar».
  const lista = pestana.privados.locator('[data-zona="conversaciones"]');
  await expect(lista.locator('.estado-vista--cargando')).toHaveCount(0, { timeout: 20_000 });
  await expect(lista.locator('.estado-vista--error')).toHaveCount(0);
}

/** Con la cola pedida: desde aquí, lo que le escriban le llega en vivo. */
async function escuchando(pestana) {
  await expect
    .poll(
      () =>
        pestana.frames.enviados.some(
          (frame) => /^\s*SUBSCRIBE/.test(frame) && frame.includes(`destination:${COLA}`),
        ),
      {
        timeout: 20_000,
        message: 'la pestaña se suscribe a /usuario/cola/mensajes-directos por /ws',
      },
    )
    .toBe(true);
}

/** La conversación con ese apodo en la lista de la pestaña. */
function conversacionCon(pestana, apodo) {
  return pestana.privados.locator('.conversaciones__item').filter({
    has: pestana.pagina.locator('.conversaciones__apodo', { hasText: new RegExp(`^${apodo}$`) }),
  });
}

/** Abre, desde la lista, la conversación con ese apodo. */
async function abrirConversacion(pestana, apodo) {
  const item = conversacionCon(pestana, apodo);
  await expect(item, `la conversación con ${apodo} está en la lista`).toBeVisible();
  await item.click();
  await expect(pestana.privados.locator('#mensajes-privados-con')).toHaveText(apodo);
}

/** Escribe y envía en la conversación abierta. */
async function escribir(pestana, texto) {
  const hilo = pestana.privados.locator('.mensajes-privados__hilo');
  await hilo.locator('#mensaje-privado').fill(texto);
  await hilo.locator('.redactor-mensaje__enviar').click();
}

/** Los globos de la conversación abierta con ese texto: `yo` u `otro`. */
function globo(pestana, quien, texto) {
  return pestana.privados.locator(`.mensajes-privados__hilo li.mensaje--${quien}`, {
    hasText: texto,
  });
}

test.describe('Mensajes privados entre jugadores (B6, feedback del profesor)', () => {
  test.describe.configure({ mode: 'serial' });

  let api;
  let ana;
  let bruno;
  let carla;
  let rapida;
  let dani;
  let admin;

  test.beforeAll(async () => {
    // Seis cuentas y, en la primera corrida del banco, cinco de ellas esperan
    // su código de verificación en el buzón de pruebas (B1).
    test.setTimeout(240_000);
    api = await apiRequest.newContext({ baseURL: BORDE });
    ana = await sesionDe(api, ANA);
    bruno = await sesionDe(api, BRUNO);
    carla = await sesionDe(api, CARLA);
    rapida = await sesionDe(api, RAPIDA);
    dani = await sesionDe(api, DANI);
    admin = await sesionDe(api, ADMIN);
    expect(admin.claims.rol, 'sembrar.sh deja a admin_e2e como ADMINISTRADOR').toBe(
      'ADMINISTRADOR',
    );
  });

  test.afterAll(async () => {
    await api?.dispose();
  });

  test('sin sesión no hay mensajes privados: 401 por REST y la pestaña manda al login', async ({
    page,
  }) => {
    expect((await api.get(API)).status()).toBe(401);
    expect(
      (await api.post(`${API}/${bruno.claims.uid}/mensajes`, { data: { texto: 'hola' } })).status(),
    ).toBe(401);

    await page.goto(PRIVADOS);
    await page.waitForURL(/\/login/, { timeout: 20_000 });
    expect(new URL(page.url()).searchParams.get('volver') ?? '').toContain('chat');
  });

  test('a quien no está conectado le llega un aviso sin el texto; el sexto mensaje en 10 s es 429', async () => {
    // B todavía no ha abierto ninguna pestaña en este spec: no escucha. Se
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

  test('A escribe a B desde la pestaña del chat y B lo recibe al instante por su cola; C, conectada, no recibe nada', async ({
    browser,
  }) => {
    // La conversación ya existe en las dos listas: este caso es el vivo, no
    // el buscador (ese es el 8, que depende de otra rama).
    await enviarBien(api, ana, bruno, `para abrir la conversación ${SUFIJO}`);

    const b = await pestanaDe(browser, bruno);
    const c = await pestanaDe(browser, carla);
    let a = null;
    try {
      await conServicio(b);
      await escuchando(b);
      await abrirConversacion(b, ANA);
      await conServicio(c);
      await escuchando(c);
      // La pestaña de A tarda en cargar y conectar lo bastante para que el
      // servidor ya haya registrado las suscripciones de B y C.
      a = await pestanaDe(browser, ana);
      await conServicio(a);
      await escuchando(a);
      await abrirConversacion(a, BRUNO);

      const desde = Date.now();
      await escribir(a, SALUDO);

      // B: aparece en su conversación abierta...
      await expect(globo(b, 'otro', SALUDO)).toBeVisible({ timeout: 15_000 });
      await expect(globo(b, 'otro', SALUDO).locator('.mensaje__autor')).toHaveText(ANA);
      // ...porque llegó por STOMP a su cola, no porque la pestaña lo pidiera.
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

      // A: su globo lo confirma el eco del servidor, y salió por STOMP, no
      // por el POST de respaldo.
      await expect(
        globo(a, 'yo', SALUDO).locator('.mensaje__entrega[data-entrega="ENVIADO"]'),
      ).toBeVisible();
      expect(
        a.peticiones.filter(
          (p) => p.metodo === 'POST' && p.ruta.startsWith(API) && p.ruta.endsWith('/mensajes'),
        ),
        'el envío fue por el canal, sin respaldo REST',
      ).toEqual([]);

      // C: nada. Se le da el mismo margen que tuvo B, y un poco más.
      await c.pagina.waitForTimeout(1500);
      expect(c.frames.recibidos.filter((frame) => frame.includes(SALUDO))).toEqual([]);
      await expect(c.privados.locator('[data-zona="conversaciones"]')).not.toContainText(ANA);
    } finally {
      await a?.contexto.close();
      await b.contexto.close();
      await c.contexto.close();
    }
  });

  test('queda en el historial de los dos, con el remitente del token, y B lo vuelve a ver al recargar', async ({
    browser,
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

    const b = await pestanaDe(browser, bruno);
    try {
      await conServicio(b);
      await abrirConversacion(b, ANA);
      await expect(globo(b, 'otro', SALUDO)).toBeVisible({ timeout: 20_000 });
    } finally {
      await b.contexto.close();
    }
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
    const c = await pestanaDe(browser, carla);
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

  test('un término de la lista negra no se entrega: 422 por REST, y la pestaña dice por qué y devuelve el texto', async ({
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
      const ultimoEnvio = Date.now();
      const cuerpo = await porRest.text();
      expect(porRest.status(), cuerpo).toBe(422);
      expect(JSON.parse(cuerpo).type).toMatch(/\/contenido-bloqueado$/);

      a = await pestanaDe(browser, ana);
      await conServicio(a);
      await escuchando(a);
      await abrirConversacion(a, BRUNO);
      // A lleva varios envíos seguidos entre esta prueba y las anteriores: si
      // el siguiente cayera dentro de la ventana del límite (LIMITE cada 10 s),
      // el rechazo sería «vas demasiado rápido» y no el de la lista negra, que
      // es lo que se prueba aquí. Se deja pasar la ventana entera.
      const esperaDelLimite = ultimoEnvio + VENTANA_DEL_LIMITE_MS - Date.now();
      if (esperaDelLimite > 0) {
        await new Promise((resolver) => setTimeout(resolver, esperaDelLimite));
      }
      const desdeLaVista = `desde la pestaña: ${termino}`;
      await escribir(a, desdeLaVista);

      const hilo = a.privados.locator('.mensajes-privados__hilo');
      const aviso = hilo.locator('.redactor-mensaje [data-zona="aviso"]');
      await expect(aviso).toContainText('Tu mensaje no se envió', { timeout: 15_000 });
      await expect(aviso).toContainText('no están permitidas');
      const fallido = globo(a, 'yo', desdeLaVista);
      await expect(fallido.locator('.mensaje__entrega[data-entrega="FALLIDO"]')).toBeVisible();
      // Repetirlo daría el mismo rechazo: sin «Reintentar», y el texto vuelve
      // al campo para cambiarlo.
      await expect(fallido.locator('[data-accion="reintentar-mensaje"]')).toHaveCount(0);
      await expect(hilo.locator('#mensaje-privado')).toHaveValue(desdeLaVista);
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

  test('buscar a un jugador por su apodo y escribirle: a él la conversación le aparece en su lista en ese momento', async ({
    browser,
  }) => {
    const texto = `hola carla, te encontré buscando ${SUFIJO}`;
    const c = await pestanaDe(browser, carla);
    let d = null;
    try {
      await conServicio(c);
      await escuchando(c);

      d = await pestanaDe(browser, dani);
      await conServicio(d);
      await escuchando(d);
      await d.privados.locator('#buscar-jugador').fill(CARLA.slice(0, 8));
      const resultado = d.privados.getByRole('button', { name: `Escribir a ${CARLA}` });
      await expect(
        resultado,
        'el buscador usa GET /api/v1/perfiles/publicos?apodo= de ms-identidad (rama de ' +
          'identidad de B6): si dice «No pudimos buscar ahora», esa ruta no está desplegada',
      ).toBeVisible({ timeout: 20_000 });
      await resultado.click();
      await expect(d.privados.locator('#mensajes-privados-con')).toHaveText(CARLA);
      await escribir(d, texto);
      await expect(
        globo(d, 'yo', texto).locator('.mensaje__entrega[data-entrega="ENVIADO"]'),
      ).toBeVisible({ timeout: 15_000 });

      // A C le entra la conversación en la lista, en vivo, con su no leído.
      const nueva = conversacionCon(c, DANI);
      await expect(nueva).toBeVisible({ timeout: 15_000 });
      await expect(nueva.locator('.conversaciones__vista')).toHaveText(texto);
      await expect(nueva.locator('.conversaciones__no-leidos')).toBeVisible();
      await nueva.click();
      await expect(globo(c, 'otro', texto)).toBeVisible();
      await expect(conversacionCon(c, DANI).locator('.conversaciones__no-leidos')).toHaveCount(0);
    } finally {
      await d?.contexto.close();
      await c.contexto.close();
    }
  });

  test('bloquear a un jugador todavía no existe: la pestaña lo dice y la conversación sigue abierta', async ({
    browser,
  }) => {
    const a = await pestanaDe(browser, ana);
    try {
      await conServicio(a);
      await abrirConversacion(a, BRUNO);
      await a.privados.locator('[data-accion="bloquear"]').click();
      await a.pagina.locator('[role="dialog"] [data-accion="confirmar"]').click();

      const hilo = a.privados.locator('.mensajes-privados__hilo');
      await expect(hilo.locator('.redactor-mensaje [data-zona="aviso"]')).toContainText(
        'No pudimos bloquear',
      );
      await expect(hilo.locator('.redactor-mensaje [data-zona="aviso"]')).toContainText(
        'todavía no está disponible',
      );
      // No se finge: ni «Bloqueaste a…», ni el campo sustituido.
      await expect(hilo).not.toContainText('Bloqueaste a');
      await expect(hilo.locator('#mensaje-privado')).toBeEnabled();
    } finally {
      await a.contexto.close();
    }
  });
});
