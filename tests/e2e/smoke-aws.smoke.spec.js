/**
 * Smoke del entorno desplegado — solo de lo que de verdad vive allí.
 *
 * ## Qué es y qué no
 *
 * No es el corte vertical: el flujo de sala **no se puede correr en AWS**
 * (#435), porque la puerta de héroe consulta a `inventario`, que no está
 * desplegado, y no cabe en el `t3.small` (#430). Eso se demuestra en
 * `sala-de-batalla.e2e.spec.js`, contra el compose de `tests/e2e/`.
 *
 * Esto comprueba que lo que SÍ está desplegado hace su trabajo. Y lo
 * comprueba mirando **estado y datos**, no códigos 200: un servicio caído
 * puede devolver 200 desde el borde, y un 404 puede ser la respuesta correcta.
 *
 * ## Una prueba aquí es distinta de las demás
 *
 * `la sala no se puede crear en DEV` afirma la **limitación**, a propósito. Si
 * algún día se despliega `inventario`, esa prueba se pondrá roja y nos
 * obligará a borrarla — que es exactamente lo que queremos que pase, en vez de
 * que la limitación se quede escrita en un issue que nadie relee.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

const AWS = process.env.E2E_AWS ?? 'http://35.168.124.119';
const CLAVE = 'Contrasena-Smoke-2026';

function cuerpoDelToken(jwt) {
  const base64 = jwt.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
  return JSON.parse(Buffer.from(base64, 'base64').toString('utf8'));
}

test.describe('Smoke del entorno desplegado', () => {
  test.describe.configure({ mode: 'serial' });

  let api;
  let jugador;
  const apodo = `smoke_${Date.now()}`;

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: AWS, ignoreHTTPSErrors: true });
  });

  test.afterAll(async () => {
    await api?.dispose();
  });

  // ===================================================================
  // Borde y frontend
  // ===================================================================

  test('el borde responde y reparte: un prefijo sin dueño da problem details, no el login', async () => {
    const salud = await api.get('/salud-borde');
    expect((await salud.text()).trim()).toBe('UP');

    // Lo que importa no es el 404: es que sea un problem details del borde y
    // no el HTML del login, que es lo que pasaba antes de registrar la regla.
    const huerfano = await api.get('/api/v1/prefijo-que-no-existe');
    expect(huerfano.status()).toBe(404);
    expect(huerfano.headers()['content-type']).toContain('problem+json');
    const problema = await huerfano.json();
    expect(problema.title).toMatch(/sin servicio/i);
  });

  test('el login se sirve con su formulario, no con una página en blanco', async ({ page }) => {
    await page.goto(`${AWS}/frontend/app-web/src/cuentas/login.html`);

    // Campos de verdad: un 200 con el HTML roto pasaría un smoke de códigos.
    await expect(page.locator('input[type="email"], input[name="email"]').first()).toBeVisible();
    await expect(page.locator('input[type="password"]').first()).toBeVisible();

    // Y el sistema de diseño cargó: si el CSS diera 404, el fondo sería el
    // del navegador. Esto detecta las rutas relativas rotas de #425.
    const fondo = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    expect(fondo).not.toBe('rgba(0, 0, 0, 0)');
  });

  test('la raíz lleva al login', async () => {
    const r = await api.get('/', { maxRedirects: 0 });
    expect([301, 302]).toContain(r.status());
    // R17.3 — la raíz lleva a la dirección limpia del login.
    expect(r.headers().location).toMatch(/\/login(?:\.html)?$/);
  });

  // ===================================================================
  // Identidad — ms-identidad
  // ===================================================================

  test('registrarse y entrar devuelve un token con la identidad de ADR-002', async () => {
    const email = `${apodo}@nexus.test`;

    const registro = await api.post('/api/v1/auth/registro', {
      multipart: {
        nombres: 'Smoke',
        apellidos: 'De Prueba',
        email,
        password: CLAVE,
        apodo,
      },
    });
    expect([200, 201], `registro: ${await registro.text()}`).toContain(registro.status());

    const login = await api.post('/api/v1/auth/login', {
      data: { email, password: CLAVE },
    });
    expect(login.status(), `login: ${await login.text()}`).toBe(200);
    jugador = await login.json();

    // ADR-002: el apodo va en `sub` y el identificador estable en `uid`.
    // `JwtService` emite subject(apodo) + los claims `uid`, `rol` y `ver`.
    const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    const claims = cuerpoDelToken(jugador.token);
    expect(claims.uid, JSON.stringify(claims)).toMatch(uuid);
    expect(claims.sub, JSON.stringify(claims)).toBe(apodo);
    expect(claims.sub).not.toMatch(uuid);
    expect(jugador.apodo).toBe(apodo);
  });

  test('el JWKS publica una clave utilizable, no un objeto vacío', async () => {
    const r = await api.get('/api/v1/auth/jwks');
    expect(r.status()).toBe(200);

    const jwks = await r.json();
    expect(Array.isArray(jwks.keys)).toBe(true);
    expect(jwks.keys.length).toBeGreaterThan(0);
    // Sin `n` y `kty` la clave no sirve para verificar nada, y plataforma
    // entera dejaria de aceptar tokens.
    expect(jwks.keys[0].kty).toBe('RSA');
    expect(jwks.keys[0].n).toBeTruthy();
  });

  test('una clave equivocada no entra', async () => {
    const r = await api.post('/api/v1/auth/login', {
      data: { email: `${apodo}@nexus.test`, password: 'no-es-esta' },
    });
    expect([400, 401, 403]).toContain(r.status());
  });

  // ===================================================================
  // Salas — hasta donde llega DEV
  // ===================================================================

  test('el listado de salas exige token y responde una página con su forma', async () => {
    const sinToken = await api.get('/api/v1/salas');
    expect(sinToken.status()).toBe(401);

    const conToken = await api.get('/api/v1/salas', {
      headers: { Authorization: `Bearer ${jugador.token}` },
    });
    expect(conToken.status()).toBe(200);

    const pagina = await conToken.json();
    // Forma de página, no un array pelado: es lo que el contrato declara y lo
    // que el frontend pagina. Los nombres son los de
    // `PaginaDeSalasResponse` —`contenido`, no `salas`—, comprobados contra
    // el DTO y no supuestos.
    expect(Array.isArray(pagina.contenido), JSON.stringify(pagina)).toBe(true);
    expect(typeof pagina.pagina).toBe('number');
    expect(typeof pagina.tamano).toBe('number');
    expect(typeof pagina.totalElementos).toBe('number');
    expect(typeof pagina.totalPaginas).toBe('number');

    // Y si hay salas, cada una trae su forma: el listado de RF-JUE-002 se
    // pinta con estos campos.
    for (const sala of pagina.contenido) {
      expect(sala.id, JSON.stringify(sala)).toBeTruthy();
      expect(sala.estado).toBeTruthy();
      expect(sala.modalidad).toBeTruthy();
      expect(typeof sala.ocupacion).toBe('number');
      expect(sala.ocupacion).toBeLessThanOrEqual(sala.maximoParticipantes);
      // El codigo de invitacion NO viaja en el listado: es de su anfitrion.
      expect(sala.codigoInvitacion).toBeUndefined();
    }
  });

  test('la puerta de heroe CONTESTA: salas-partidas alcanza al inventario del host de contenido', async () => {
    // Esta prueba sustituye a la que afirmaba la limitacion de #435 ("crear
    // sala falla porque inventario no esta desplegado"). Aquella se escribio
    // para ponerse roja el dia que inventario estuviera desplegado, y ese dia
    // llego con una vuelta de tuerca: inventario llevaba tiempo corriendo y
    // sano en el host de contenido (34.193.90.11:8102), pero
    // `docker-compose.deploy.yml` no fijaba INVENTARIO_BASE_URL para
    // salas-partidas, asi que el servicio se lo preguntaba a si mismo
    // (`localhost:8080`) y contestaba 503. El 503 no decia "no desplegado":
    // decia "no configurado", y nadie podia distinguirlo desde fuera.
    //
    // Lo que se comprueba ahora es justo esa diferencia: la puerta de heroe
    // RESPONDE. Un jugador recien registrado no tiene heroe equipado, asi que
    // la respuesta correcta es 422 `heroe-no-equipado` (RF-JUE-003). Si
    // volviera el 503 de seccion degradada, la cadena hacia el host de
    // contenido esta rota otra vez - que es exactamente lo que esta prueba
    // tiene que gritar.
    const r = await api.post('/api/v1/salas', {
      headers: { Authorization: `Bearer ${jugador.token}`, 'Content-Type': 'application/json' },
      data: { maximoParticipantes: 2, modalidad: 'UNO_CONTRA_UNO', recompensaCreditos: 0 },
    });

    const problema = await r.json();
    expect(
      r.status(),
      `si esto es 503, salas-partidas no alcanza al inventario: ${JSON.stringify(problema)}`,
    ).toBe(422);
    expect(problema.type).toBe('https://nexusbattles.local/errores/heroe-no-equipado');
  });

  // ===================================================================
  // Notificaciones, correo y metricas
  // ===================================================================

  test('el canal en tiempo real acepta el CONNECT con el JWT desplegado', async ({ page }) => {
    // HU-JUE-015 y HU-SAL-005 viajan los dos por aqui. Un 200 de
    // `/actuator/health` no dice nada del canal: el handshake de WebSocket
    // pasa por el borde con cabeceras de upgrade y el CONNECT de STOMP lleva
    // el token. Se prueba lo que de verdad puede romperse.
    // Se abre desde una pagina del propio host: el canal comprueba el Origin
    // (`setAllowedOriginPatterns`), y desde `about:blank` viajaria como
    // `null` y lo rechazaria por un motivo que no es el que se quiere probar.
    await page.goto(`${AWS}/frontend/app-web/src/cuentas/login.html`);

    const url = `${AWS.replace(/^http/, 'ws')}/ws`;
    const resultado = await page.evaluate(
      ([destino, token]) =>
        new Promise((resolver) => {
          const NUL = ' ';
          const socket = new WebSocket(destino);
          const cortar = setTimeout(() => resolver('sin respuesta en 15 s'), 15000);
          socket.onopen = () =>
            socket.send(
              `CONNECT\naccept-version:1.2\nheart-beat:0,0\n` +
                `Authorization:Bearer ${token}\n\n${NUL}`,
            );
          socket.onmessage = (evento) => {
            clearTimeout(cortar);
            resolver(String(evento.data).split('\n')[0]);
          };
          socket.onerror = () => {
            clearTimeout(cortar);
            resolver('error de transporte');
          };
          socket.onclose = () => {
            clearTimeout(cortar);
            resolver('cerrado sin CONNECTED');
          };
        }),
      [url, jugador.token],
    );

    expect(resultado, `respuesta del canal en ${url}`).toBe('CONNECTED');
  });

  test('el hilo de comentarios de un producto se puede leer, aunque este vacio', async () => {
    // HU-COM-001 (#34) y lado proveedor de HU-INV-014 (#233). Hasta #438 solo
    // existia el POST: lo publicado no lo veia nadie. Un producto sin
    // comentarios responde 200 con el hilo vacio y promedio nulo, no 404: no
    // tener comentarios es un estado normal, y la ficha lo pinta como vacio.
    const lectura = await api.get('/api/v1/products/smoke-inexistente/comments');
    expect(lectura.status(), await lectura.text()).toBe(200);

    const hilo = await lectura.json();
    expect(hilo.productoId).toBe('smoke-inexistente');
    expect(Array.isArray(hilo.comentarios)).toBe(true);
    expect(hilo.total).toBe(hilo.comentarios.length);
    expect(typeof hilo.totalCalificaciones).toBe('number');
    // Sin calificaciones el promedio es nulo, nunca un cero que parezca nota.
    if (hilo.totalCalificaciones === 0) {
      expect(hilo.calificacionPromedio).toBeNull();
    } else {
      expect(hilo.calificacionPromedio).toBeGreaterThanOrEqual(1);
      expect(hilo.calificacionPromedio).toBeLessThanOrEqual(5);
    }

    // No se afirma aqui la postura de seguridad del POST: el servicio valida
    // el cuerpo antes que el token, asi que desde fuera no se distingue
    // «rechazado por el cuerpo» de «rechazado por el token». Eso lo prueba su
    // dueno con el DTO delante.
  });

  test('la bandeja de notificaciones es del dueno del token: 200 con el suyo, 401 sin token', async () => {
    const ruta = `/api/v1/users/${cuerpoDelToken(jugador.token).uid}/notifications`;

    const sinToken = await api.get(ruta);
    expect(sinToken.status(), 'la bandeja ya no se lee sin token (contrato 1.1.0)').toBe(401);

    const r = await api.get(ruta, { headers: { Authorization: `Bearer ${jugador.token}` } });
    expect(r.status()).toBe(200);

    const bandeja = await r.json();
    expect(bandeja).toHaveProperty('usuarioId');
    expect(bandeja).toHaveProperty('noLeidas');
    expect(Array.isArray(bandeja.avisos)).toBe(true);
    expect(typeof bandeja.noLeidas).toBe('number');
  });

  test('el correo del registro llega de verdad a la bandeja de pruebas, y correo no se alcanza desde fuera', async () => {
    // De punta a punta por el camino real: ms-identidad (registro de arriba)
    // -> correo con su credencial de servicio (ADR-005) -> SMTP -> Mailpit.
    // Ya no se puede POSTear a correo desde fuera: el borde no lo expone y
    // el servicio exige ROLE_SERVICIO. Eso tambien se afirma.
    const destinatario = `${apodo}@nexus.test`;

    const desdeFuera = await api.post('/api/v1/correos/bienvenida', {
      data: { email: destinatario, apodo, nombres: 'Smoke', apellidos: 'De Prueba' },
    });
    expect(desdeFuera.status(), 'correo no debe ser alcanzable desde el borde').toBe(404);

    await expect
      .poll(
        async () => {
          const bandeja = await api.get('/mailpit/api/v1/search', {
            params: { query: `to:${destinatario}` },
          });
          if (!bandeja.ok()) return 0;
          return (await bandeja.json()).messages_count ?? 0;
        },
        { timeout: 30000, message: 'el correo del registro no llego a Mailpit' },
      )
      .toBeGreaterThan(0);
  });

  test('metricas esta desplegado y su observabilidad NO es publica (#527)', async () => {
    // Antes del 22-sep-2026 este smoke leia aqui el estado de los siete
    // servicios sin token, porque metricas-plataforma no tenia cadena de
    // seguridad. Eso era el defecto, no la prueba: el tablero publica consumo,
    // errores 5xx y caidas del bloque, y su ficha (RF-MET-001) dice
    // «Como administrador».
    //
    // Sin una cuenta administrativa sembrada en el host de dev, lo que este
    // smoke puede afirmar desde fuera es que el servicio esta ARRIBA y que
    // pide credenciales: un 401 solo lo contesta el propio servicio; si
    // estuviera caido, el borde devolveria 502 (asi se vio en #571).
    for (const ruta of ['/api/v1/disponibilidad', '/api/v1/tecnicas', '/api/v1/moderacion']) {
      const anonimo = await api.get(ruta);
      expect(anonimo.status(), `${ruta} deberia pedir credenciales, no responder`).toBe(401);
    }

    // Un jugador autenticado tampoco ve la observabilidad del bloque.
    const conJugador = await api.get('/api/v1/disponibilidad', {
      headers: { Authorization: `Bearer ${jugador.token}` },
    });
    expect(conJugador.status()).toBe(403);
  });

  test('la lista negra exige rol: no basta con estar autenticado', async () => {
    const sinToken = await api.get('/api/v1/lista-negra/terminos');
    expect(sinToken.status()).toBe(401);

    const conTokenDeJugador = await api.get('/api/v1/lista-negra/terminos', {
      headers: { Authorization: `Bearer ${jugador.token}` },
    });
    // Un jugador normal no administra la lista negra.
    expect([401, 403]).toContain(conTokenDeJugador.status());
  });
});
