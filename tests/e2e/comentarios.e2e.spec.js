// @ts-check
/**
 * Comentarios y calificaciones sobre productos — HU-COM-002 (calificación
 * única, D-07), HU-COM-003 (promedio), HU-COM-004 (eliminar el propio) y B3
 * (calificación separada, producto validado, imágenes reales), de punta a
 * punta por el borde, con el servicio real, el catálogo real, la lista negra y
 * las sanciones reales.
 *
 * B3 cambia dos cosas de fondo, y esta prueba las fija:
 *   - solo se comenta y se califica un producto que EXISTE en el catálogo:
 *     el producto de cada corrida lo da de alta el administrador por la API
 *     real (igual que la tienda), y uno inventado es 404;
 *   - la calificación vive aparte (7.1: «solo pueden calificar un producto
 *     una vez, pero podrán agregar o retirar tantos comentarios como sea de
 *     su agrado»): retirar el comentario ya no retira la calificación, y las
 *     estrellas de cada comentario son las de la calificación de su autor.
 *
 *   1. producto nuevo: hilo vacío y «sin calificaciones»; uno inventado, 404
 *   2. anfitriona comenta con 5 -> su calificación; la segunda (3) se descarta
 *      y el comentario enseña sus 5 (D-07)
 *   3. invitado comenta con 3 -> promedio 4.0 (2)
 *   4. moderadora califica SIN comentar (1) -> 3.0 (3); repetir es 409
 *   5. eliminar: ajeno 403; propio 204 y la calificación SIGUE (3.0, 3)
 *   6. imagen: se sube, se adjunta por id y se ve con sus cabeceras; un HTML
 *      con extensión .png es 415
 *   7. la vista: promedio arriba, «Eliminar» solo en los míos y la imagen del
 *      comentario con foto se ve de verdad (B3: el hilo la pinta por su id)
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import { sesionDe as sesionDelBanco } from './ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const ANFITRION = process.env.E2E_ANFITRION ?? 'anfitriona_e2e';
const INVITADO = process.env.E2E_INVITADO ?? 'invitado_e2e';
const MODERADORA = process.env.E2E_MODERADORA ?? 'moderadora_e2e';
const ADMIN = process.env.E2E_ADMIN ?? 'admin_e2e';
const CLAVE = 'Contrasena-E2E-2026';
const VISTA = '/frontend/app-web/src/plataforma/comentarios/publicar-comentario.html';

/** Un PNG de verdad de 3x2 (el mismo que usan las pruebas del servicio). */
const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAMAAAACCAIAAAASFvFNAAAAHElEQVR42mNkYGBQZTBVZTBlYYg2ZWAwZWAwBQAPAgG01W6s7gAAAABJRU5ErkJggg==',
  'base64',
);

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

/**
 * B3 — comentar y calificar exigen un producto del catálogo. Cada corrida da de
 * alta el suyo por la API real: repetir contra el mismo banco no choca con la
 * calificación única de la corrida anterior.
 */
async function productoNuevo(api, admin) {
  const r = await api.post('/api/v1/productos', {
    headers: conToken(admin.token),
    data: {
      nombre: `Producto de comentarios E2E ${Date.now()}`,
      imagen: '/frontend/app-web/src/cuentas/avatares/arquero-cazador.jpg',
      descripcion: 'Producto de prueba del E2E de comentarios y calificaciones.',
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

test.describe('Comentarios y calificaciones: única, promedio, eliminar e imágenes (HU-COM-002/003/004, B3)', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let anfitriona;
  let invitado;
  let moderadora;
  let producto;
  const ruta = () => `/api/v1/products/${producto}/comments`;
  const rutaCalificacion = () => `/api/v1/products/${producto}/rating`;
  let comentarioDeAnfitriona;

  async function hilo() {
    const r = await api.get(ruta());
    expect(r.status(), await r.text()).toBe(200);
    return r.json();
  }

  async function resumen() {
    const r = await api.get(rutaCalificacion());
    expect(r.status(), await r.text()).toBe(200);
    return r.json();
  }

  async function comentar(quien, texto, estrellas, imagenes) {
    const data = { texto };
    if (estrellas !== undefined) {
      data.estrellas = estrellas;
    }
    if (imagenes) {
      data.imagenes = imagenes;
    }
    return api.post(ruta(), { headers: conToken(quien.token), data });
  }

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    anfitriona = await sesionDe(api, ANFITRION);
    invitado = await sesionDe(api, INVITADO);
    moderadora = await sesionDe(api, MODERADORA);
    const admin = await sesionDe(api, ADMIN);
    producto = await productoNuevo(api, admin);
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  test('un producto nuevo tiene el hilo vacío y «sin calificaciones»; uno inventado es 404', async () => {
    const h = await hilo();
    expect(h.comentarios).toEqual([]);
    expect(h.calificacionPromedio).toBeNull();
    expect(h.totalCalificaciones).toBe(0);
    expect(h.total).toBe(0);
    expect(h.tamano).toBe(16);

    const r = await resumen();
    expect(r.promedio).toBeNull();
    expect(r.total).toBe(0);

    const inventado = `producto-inventado-${Date.now()}`;
    const comentario = await api.post(`/api/v1/products/${inventado}/comments`, {
      headers: conToken(anfitriona.token),
      data: { texto: 'Este producto no existe' },
    });
    expect(comentario.status()).toBe(404);
    expect((await comentario.json()).type).toMatch(/producto-inexistente$/);
    expect((await api.get(`/api/v1/products/${inventado}/rating`)).status()).toBe(404);
  });

  test('la primera calificación entra y hace promedio; la segunda del mismo jugador se descarta (D-07)', async () => {
    const primera = await comentar(anfitriona, 'Excelente espada, aguanta toda la temporada', 5);
    expect(primera.status(), await primera.text()).toBe(201);
    comentarioDeAnfitriona = await primera.json();
    expect(comentarioDeAnfitriona.autorId).toBe(anfitriona.claims.uid);
    expect(comentarioDeAnfitriona.estrellas).toBe(5);
    expect(comentarioDeAnfitriona.calificacionDescartada).toBe(false);

    let h = await hilo();
    expect(h.calificacionPromedio).toBe(5);
    expect(h.totalCalificaciones).toBe(1);

    const segunda = await comentar(anfitriona, 'Segunda opinion, sigue igual de buena', 3);
    expect(segunda.status(), 'no es 409: entra, sin cambiar su calificación').toBe(201);
    const cuerpo = await segunda.json();
    expect(cuerpo.calificacionDescartada).toBe(true);
    expect(cuerpo.estrellas, 'enseña las de su calificación, no las que mandó').toBe(5);

    h = await hilo();
    expect(h.total).toBe(2);
    expect(h.comentarios[0].id, 'del más reciente al más antiguo').toBe(cuerpo.id);
    expect(h.calificacionPromedio, 'la segunda no movió el promedio').toBe(5);
    expect(h.totalCalificaciones).toBe(1);
  });

  test('otra jugadora califica al comentar y el promedio se recalcula con las dos', async () => {
    const r = await comentar(invitado, 'Correcta, sin mas', 3);
    expect(r.status(), await r.text()).toBe(201);
    const h = await hilo();
    expect(h.calificacionPromedio).toBe(4);
    expect(h.totalCalificaciones).toBe(2);
  });

  test('G4 (1.9.0): el hilo público no publica el uid de nadie; `propio` lo dice el servidor', async () => {
    // Sin token: apodos sí, uid no, en ningún campo.
    const anonimo = await api.get(ruta());
    expect(anonimo.status()).toBe(200);
    const texto = await anonimo.text();
    expect(texto).not.toContain(anfitriona.claims.uid);
    expect(texto).not.toContain(invitado.claims.uid);
    const h = JSON.parse(texto);
    expect(h.comentarios.length).toBeGreaterThan(0);
    for (const c of h.comentarios) {
      expect(c).not.toHaveProperty('autorId');
      expect(typeof c.apodoAutor).toBe('string');
      expect(c.propio).toBe(false);
    }

    // Con su token, cada una ve propios solo los suyos, y tampoco recibe uids.
    const deLaAnfitriona = await api.get(ruta(), { headers: conToken(anfitriona.token) });
    const suyo = await deLaAnfitriona.text();
    expect(suyo).not.toContain(anfitriona.claims.uid);
    expect(suyo).not.toContain(invitado.claims.uid);
    const propios = JSON.parse(suyo).comentarios.filter((c) => c.propio);
    expect(propios.map((c) => c.apodoAutor)).toEqual(
      propios.map(() => comentarioDeAnfitriona.apodoAutor),
    );
    expect(propios.length).toBe(2);
  });

  test('se califica sin comentar, una sola vez: 201, luego 409, y la propia se puede leer', async () => {
    const r = await api.post(rutaCalificacion(), {
      headers: conToken(moderadora.token),
      data: { estrellas: 1 },
    });
    expect(r.status(), await r.text()).toBe(201);
    const calificada = await r.json();
    expect(calificada.estrellas).toBe(1);
    expect(calificada.resumen.promedio).toBe(3);
    expect(calificada.resumen.total).toBe(3);

    const otra = await api.post(rutaCalificacion(), {
      headers: conToken(moderadora.token),
      data: { estrellas: 5 },
    });
    expect(otra.status()).toBe(409);
    expect((await otra.json()).type).toMatch(/ya-calificado$/);

    const mia = await api.get(`${rutaCalificacion()}/mia`, { headers: conToken(moderadora.token) });
    expect(mia.status()).toBe(200);
    expect((await mia.json()).estrellas, 'no se sustituyó').toBe(1);

    const r2 = await resumen();
    expect(r2.distribucion).toEqual({ 1: 1, 2: 0, 3: 1, 4: 0, 5: 1 });
    expect((await hilo()).calificacionPromedio, 'el hilo y el resumen son el mismo número').toBe(3);
  });

  test('eliminar: ajeno 403, propio 204 y la calificación se queda; repetir 204; inexistente 404', async () => {
    const ajeno = await api.delete(`${ruta()}/${comentarioDeAnfitriona.id}`, {
      headers: conToken(invitado.token),
    });
    expect(ajeno.status()).toBe(403);
    expect((await ajeno.json()).type).toMatch(/comentario-ajeno$/);

    const propio = await api.delete(`${ruta()}/${comentarioDeAnfitriona.id}`, {
      headers: conToken(anfitriona.token),
    });
    expect(propio.status(), await propio.text()).toBe(204);

    const h = await hilo();
    expect(h.comentarios.map((c) => c.id)).not.toContain(comentarioDeAnfitriona.id);
    expect(h.calificacionPromedio, 'retirar el comentario no retira la calificación (7.1)').toBe(3);
    expect(h.totalCalificaciones).toBe(3);

    const otraVez = await api.delete(`${ruta()}/${comentarioDeAnfitriona.id}`, {
      headers: conToken(anfitriona.token),
    });
    expect(otraVez.status(), 'idempotente').toBe(204);

    const inventado = await api.delete(`${ruta()}/no-existe`, {
      headers: conToken(anfitriona.token),
    });
    expect(inventado.status()).toBe(404);
    expect((await inventado.json()).type).toMatch(/comentario-no-encontrado$/);

    const sinToken = await api.delete(`${ruta()}/${comentarioDeAnfitriona.id}`);
    expect(sinToken.status()).toBe(401);
  });

  test('imágenes reales: se suben, se adjuntan por id y se sirven con sus cabeceras; un HTML no pasa', async () => {
    const subida = await api.post('/api/v1/comentarios/imagenes', {
      headers: { Authorization: `Bearer ${anfitriona.token}` },
      multipart: { archivo: { name: 'captura.png', mimeType: 'image/png', buffer: PNG } },
    });
    expect(subida.status(), await subida.text()).toBe(201);
    const imagen = await subida.json();
    expect(imagen.tipo).toBe('image/png');
    expect(imagen.url).toBe(`/api/v1/comentarios/imagenes/${imagen.id}`);

    const falsa = await api.post('/api/v1/comentarios/imagenes', {
      headers: { Authorization: `Bearer ${anfitriona.token}` },
      multipart: {
        archivo: {
          name: 'foto.png',
          mimeType: 'image/png',
          buffer: Buffer.from('<html><script>alert(1)</script></html>'),
        },
      },
    });
    expect(falsa.status()).toBe(415);

    const pendiente = await api.get(imagen.url);
    expect(pendiente.status(), 'sin comentario todavía no es pública').toBe(404);

    const conFoto = await comentar(anfitriona, 'Mirad la foto', undefined, [imagen.id]);
    expect(conFoto.status(), await conFoto.text()).toBe(201);
    expect((await conFoto.json()).imagenes).toEqual([imagen.id]);

    const publica = await api.get(imagen.url);
    expect(publica.status()).toBe(200);
    expect(publica.headers()['content-type']).toBe('image/png');
    expect(publica.headers()['x-content-type-options']).toContain('nosniff');
    expect(publica.headers()['cache-control']).toBe('public, max-age=31536000, immutable');
    expect(Buffer.compare(await publica.body(), PNG)).toBe(0);

    const ajena = await comentar(invitado, 'Me la llevo', undefined, [imagen.id]);
    expect(ajena.status(), 'una imagen ajena no se adjunta').toBe(400);
  });

  test('la vista muestra el promedio y solo pone «Eliminar» en mis comentarios', async ({ page }) => {
    await page.addInitScript(
      ([token, nombre, uid]) => {
        sessionStorage.setItem('nexus.token', token);
        sessionStorage.setItem('nexus.apodoActual', nombre);
        sessionStorage.setItem('nexus.usuarioId', uid);
      },
      [anfitriona.token, ANFITRION, anfitriona.claims.uid],
    );
    await page.goto(`${BORDE}${VISTA}?producto=${producto}`);

    await expect(page.locator('[data-zona="promedio"]')).toHaveText(
      /3\.00 de 5 \(3 calificaciones\)/,
      {
        timeout: 20000,
      },
    );
    // Quedan: los dos de la anfitriona que no retiró (el de la segunda opinión
    // y el de la foto) y el de invitado.
    const articulos = page.locator('[data-zona="hilo-lista"] article');
    await expect(articulos).toHaveCount(3);
    const mios = page.locator('[data-zona="hilo-lista"] article:has([data-accion="eliminar"])');
    await expect(mios).toHaveCount(2);
    await expect(mios.first().locator('[data-campo="apodo"]')).toHaveText(ANFITRION);

    // B3: el comentario con foto enseña la imagen de verdad, servida por su id
    // (antes el hilo solo sabía pintar un nombre de archivo sin imagen detrás).
    const foto = page.locator('[data-zona="hilo-lista"] article', { hasText: 'Mirad la foto' });
    const imagen = foto.locator('img.comentario__imagen');
    await expect(imagen).toBeVisible();
    await expect(imagen).toHaveAttribute('src', /\/api\/v1\/comentarios\/imagenes\/[0-9a-f-]{36}$/);
    await expect
      .poll(() => imagen.evaluate((img) => (img.complete ? img.naturalWidth : 0)))
      .toBeGreaterThan(0);
  });
});
