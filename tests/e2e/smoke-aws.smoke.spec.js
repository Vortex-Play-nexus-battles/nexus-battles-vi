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
    expect(r.headers().location).toContain('login.html');
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

    const claims = cuerpoDelToken(jugador.token);
    expect(claims.uid).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i);
    expect(claims.preferred_username ?? claims.apodo).toBe(apodo);
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
    // que el frontend pagina.
    expect(pagina).toHaveProperty('salas');
    expect(Array.isArray(pagina.salas)).toBe(true);
    expect(typeof pagina.total === 'number' || typeof pagina.totalElementos === 'number').toBe(
      true,
    );
  });

  test('LIMITACION DE DEV: crear sala falla porque inventario no esta desplegado', async () => {
    // Esta prueba afirma la limitacion a proposito (#435). El dia que se
    // despliegue `inventario`, se pondra roja y habra que borrarla: es la
    // forma de que la limitacion no se quede solo escrita en un issue.
    const r = await api.post('/api/v1/salas', {
      headers: { Authorization: `Bearer ${jugador.token}`, 'Content-Type': 'application/json' },
      data: { maximoParticipantes: 2, modalidad: 'UNO_VS_UNO', recompensaCreditos: 0 },
    });

    expect(r.status(), 'si esto ya no es 503, inventario esta desplegado: borra esta prueba').toBe(
      503,
    );
    const problema = await r.json();
    expect(problema.detail ?? '').toMatch(/inventario|vitrina/i);
  });

  // ===================================================================
  // Notificaciones, correo y metricas
  // ===================================================================

  test('la bandeja de notificaciones responde con su forma y cuenta las no leidas', async () => {
    const r = await api.get(`/api/v1/users/${cuerpoDelToken(jugador.token).uid}/notifications`);
    expect(r.status()).toBe(200);

    const bandeja = await r.json();
    expect(bandeja).toHaveProperty('usuarioId');
    expect(bandeja).toHaveProperty('noLeidas');
    expect(Array.isArray(bandeja.avisos)).toBe(true);
    expect(typeof bandeja.noLeidas).toBe('number');
  });

  test('un correo enviado llega de verdad a la bandeja de pruebas', async () => {
    // De punta a punta: correo -> SMTP -> Mailpit. Un 202 del servicio no
    // prueba que el mensaje saliera.
    const destinatario = `${apodo}@nexus.test`;

    const envio = await api.post('/api/v1/correos/bienvenida', {
      data: { email: destinatario, apodo, nombres: 'Smoke', apellidos: 'De Prueba' },
    });
    expect([200, 201, 202], `envio: ${await envio.text()}`).toContain(envio.status());

    await expect
      .poll(
        async () => {
          const bandeja = await api.get('/mailpit/api/v1/search', {
            params: { query: `to:${destinatario}` },
          });
          if (!bandeja.ok()) return 0;
          return (await bandeja.json()).messages_count ?? 0;
        },
        { timeout: 30000, message: 'el correo no llego a Mailpit' },
      )
      .toBeGreaterThan(0);
  });

  test('metricas conoce a los servicios de plataforma y los ve disponibles', async () => {
    const r = await api.get('/api/v1/disponibilidad');
    expect(r.status()).toBe(200);

    const { servicios } = await r.json();
    expect(Array.isArray(servicios)).toBe(true);

    const porNombre = Object.fromEntries(servicios.map((s) => [s.servicio, s.estado]));
    // No se comprueba "alguno esta arriba": se comprueba que estos, que son
    // los del bloque, lo estan.
    for (const esperado of [
      'comentarios',
      'correo',
      'salas-partidas',
      'notificaciones',
      'moderacion-sanciones',
      'admin-parametros',
      'torneos',
    ]) {
      expect(porNombre[esperado], `${esperado} no aparece o no esta disponible`).toBe('DISPONIBLE');
    }

    // Y cada comprobacion trae su momento: una foto vieja no vale.
    for (const s of servicios) {
      expect(new Date(s.comprobadoEn).getTime()).toBeGreaterThan(Date.now() - 10 * 60 * 1000);
    }
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
