/**
 * Canarios del jugador — R16 (Full Platform Recovery).
 *
 * ## Por qué existen
 *
 * El 23-sep-2026 el jugador `demo_grupo6` abrió cinco pantallas en AWS DEV y
 * las cinco mostraron un error de servicio con la sesión perfectamente válida:
 *
 *   - Mi cuenta ............ «No pudimos cargar tu perfil»
 *                            «Tus créditos no están disponibles»
 *   - Historial ............ «No pudimos cargar tu historial»
 *   - Mis cofres ........... «No pudimos cargar tus cofres»
 *   - Tienda ............... «No se pudo cargar la tienda»
 *                            «No se pudo cargar tu carrito»
 *   - Mi inventario ........ cargaba, pero vacío
 *
 * CI estaba verde, el E2E estaba verde y el smoke estaba verde. Ninguna de las
 * tres suites abría las pantallas de un jugador contra el entorno desplegado.
 * Este archivo es exactamente eso: las cinco pantallas, con un jugador recién
 * registrado, contra AWS, mirando lo que ve la persona.
 *
 * ## Qué afirma y qué no
 *
 * Afirma que ninguna petición `/api/v1/*` de la pantalla responde 5xx y que no
 * aparece ninguno de los textos de error de servicio de esa pantalla. **Un
 * estado vacío es válido**: un jugador recién creado no tiene créditos ni
 * inventario todavía, y decirlo no es un fallo. Lo que no se admite es «el
 * servicio no responde».
 *
 * No afirma nada del estado inicial del jugador (créditos, héroes, misiones):
 * eso es la fase siguiente y no se inventa aquí.
 *
 * Cada prueba adjunta la lista de peticiones con su estado: es la evidencia
 * antes/después de R16.28, sin tener que abrir DevTools.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

const AWS = process.env.E2E_AWS ?? 'http://35.168.124.119';
const CLAVE = 'Contrasena-Canario-2026';
const RAIZ = '/frontend/app-web/src';

/** Las pantallas de las capturas, con los textos que delatan un servicio caído. */
const PANTALLAS = [
  {
    nombre: 'Mi cuenta',
    ruta: `${RAIZ}/cuentas/perfil.html`,
    errores: ['No pudimos cargar tu perfil', 'Tus créditos no están disponibles'],
  },
  {
    nombre: 'Historial de transacciones',
    ruta: `${RAIZ}/cuentas/historial-transacciones.html`,
    errores: ['No pudimos cargar tu historial'],
  },
  {
    nombre: 'Mis cofres',
    ruta: `${RAIZ}/cuentas/mis-cofres.html`,
    errores: ['No pudimos cargar tus cofres'],
  },
  {
    nombre: 'Tienda y carrito',
    ruta: `${RAIZ}/cuentas/tienda.html`,
    errores: ['No se pudo cargar la tienda', 'No se pudo cargar tu carrito'],
  },
  {
    nombre: 'Mi inventario',
    ruta: `${RAIZ}/contenido/inventario/inventario.html`,
    errores: ['No se pudo cargar la vitrina del inventario'],
  },
];

function cuerpoDelToken(jwt) {
  const base64 = jwt.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
  return JSON.parse(Buffer.from(base64, 'base64').toString('utf8'));
}

/**
 * Espera a que la pantalla deje de pedir cosas a `/api/v1`: 1,5 s sin
 * peticiones en vuelo, con un tope. `networkidle` no sirve aquí: varias vistas
 * mantienen un canal STOMP o sondean la campana de notificaciones y nunca
 * quedan «quietas».
 */
async function esperarAQueLaApiCalle(enVuelo, topeMs = 25_000) {
  const inicio = Date.now();
  let quietaDesde = Date.now();
  while (Date.now() - inicio < topeMs) {
    if (enVuelo.size > 0) {
      quietaDesde = Date.now();
    } else if (Date.now() - quietaDesde >= 1_500) {
      return;
    }
    await new Promise((resolver) => setTimeout(resolver, 200));
  }
}

test.describe('Canarios del jugador (R16)', () => {
  // Sin modo serie: cada pantalla es independiente y un fallo no debe esconder
  // el estado de las siguientes. Si una falla, el worker se reinicia y
  // beforeAll registra otro jugador; no importa, todos son de usar y tirar.
  let sesion;

  test.beforeAll(async () => {
    const api = await apiRequest.newContext({ baseURL: AWS, ignoreHTTPSErrors: true });
    const apodo = `canario_${Date.now()}`;
    const email = `${apodo}@nexus.test`;
    try {
      const registro = await api.post('/api/v1/auth/registro', {
        multipart: { nombres: 'Canario', apellidos: 'De Prueba', email, password: CLAVE, apodo },
      });
      expect([200, 201], `registro: ${await registro.text()}`).toContain(registro.status());

      const login = await api.post('/api/v1/auth/login', { data: { email, password: CLAVE } });
      expect(login.status(), `login: ${await login.text()}`).toBe(200);
      const cuerpo = await login.json();
      const claims = cuerpoDelToken(cuerpo.token);
      sesion = { token: cuerpo.token, apodo: claims.sub, rol: claims.rol, uid: claims.uid };
    } finally {
      await api.dispose();
    }
  });

  // Informativo, no falla: cuánto hay en el catálogo maestro (servicio
  // `productos`) frente a lo que muestra la vitrina (ms-ecommerce). Si el
  // maestro tiene productos y la vitrina sale vacía, la tienda está
  // desconectada del catálogo real, que es justo lo que pide revisar R16.13.
  //
  // R16 — la vitrina ya no es `GET /api/v1/productos`: ese prefijo es entero
  // del catálogo maestro (#421) y la vitrina vive en `/api/v1/vitrina`
  // (ecommerce-carrito.yaml 1.2.0). Con la ruta vieja, esta línea imprimiría
  // el listado del catálogo con la etiqueta «vitrina».
  test('catálogo maestro frente a vitrina (informativo)', async ({ request }) => {
    const cabeceras = { Authorization: `Bearer ${sesion.token}` };
    const maestro = await request.get(`${AWS}/api/v1/productos/estadisticas`, {
      headers: cabeceras,
    });
    const vitrina = await request.get(`${AWS}/api/v1/vitrina?page=0&size=16`, {
      headers: cabeceras,
    });
    const resumen = async (r) =>
      `${r.status()} ${(await r.text()).slice(0, 300).replace(/\s+/g, ' ')}`;
    console.log(`CANARIO-INFO|catalogo-maestro|${await resumen(maestro)}`);
    console.log(`CANARIO-INFO|vitrina|${await resumen(vitrina)}`);
  });

  for (const pantalla of PANTALLAS) {
    test(`${pantalla.nombre}: sin errores de servicio`, async ({ page }) => {
      const peticiones = [];
      const enVuelo = new Set();

      page.on('request', (peticion) => {
        if (new URL(peticion.url()).pathname.startsWith('/api/v1/')) enVuelo.add(peticion);
      });
      const terminar = (peticion) => enVuelo.delete(peticion);
      page.on('requestfailed', terminar);
      page.on('response', (respuesta) => {
        const peticion = respuesta.request();
        const ruta = new URL(respuesta.url()).pathname;
        if (!ruta.startsWith('/api/v1/')) return;
        terminar(peticion);
        peticiones.push({ metodo: peticion.method(), ruta, estado: respuesta.status() });
      });

      // La misma sesión que deja login.js (comun/sesion.js, CLAVES).
      await page.addInitScript((s) => {
        sessionStorage.setItem('nexus.token', s.token);
        sessionStorage.setItem('nexus.apodoActual', s.apodo);
        sessionStorage.setItem('nexus.rolActual', s.rol ?? 'JUGADOR');
        if (s.uid) sessionStorage.setItem('nexus.usuarioId', s.uid);
      }, sesion);

      await page.goto(`${AWS}${pantalla.ruta}`, { waitUntil: 'domcontentloaded' });
      await esperarAQueLaApiCalle(enVuelo);

      await test.info().attach('peticiones-api', {
        body: JSON.stringify(peticiones, null, 2),
        contentType: 'application/json',
      });
      // Una línea legible en la bitácora del workflow: la evidencia antes/después.
      for (const p of peticiones) {
        console.log(`CANARIO|${pantalla.nombre}|${p.metodo}|${p.ruta}|${p.estado}`);
      }

      // R17.3 — el login es /login detrás del borde; login.html redirige allí.
      expect(page.url(), 'la sesión no sirvió y la vista mandó al login').not.toMatch(
        /\/login(?:\.html)?(?:[?#]|$)/,
      );

      const conFallo = peticiones.filter((p) => p.estado >= 500);
      expect(conFallo, 'peticiones /api/v1 que respondieron 5xx').toEqual([]);

      for (const texto of pantalla.errores) {
        await expect(page.getByText(texto, { exact: false }), `se ve «${texto}»`).toHaveCount(0);
      }
    });
  }
});
