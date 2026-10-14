// @ts-check
/**
 * SA-6 — recursos gráficos del catálogo (HU-PRD-009, §7.2.3) y motivo de las
 * acciones críticas (§7.2.5), de punta a punta por el borde, con productos,
 * Mongo e identidad reales (productos.yaml 1.10.0).
 *
 *   1. Subir es de administración y el archivo se juzga por sus bytes: un
 *      HTML con nombre de imagen no entra, una imagen fija no pasa por
 *      animación y el mismo archivo no se guarda dos veces.
 *   2. La biblioteca se lista sin los bytes; los bytes son públicos (son la
 *      imagen que ve la tienda) con cabeceras que no dejan ejecutar nada.
 *   3. Un recurso puesto como imagen de un producto no se retira; revertir el
 *      cambio con su motivo lo libera, y retirado se sigue sirviendo.
 *   4. Suspender y reactivar dejan su motivo en el historial.
 *
 * El producto que usa este spec lo crea el propio spec: no toca el catálogo
 * que siembra `sembrar.sh` para los demás.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import { sesionDe as sesionDelBanco } from './ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const ADMIN = process.env.E2E_ADMIN ?? 'admin_e2e';
const CLAVE = 'Contrasena-E2E-2026';

/** Un PNG de 16×16 de verdad (IHDR, IDAT e IEND con sus CRC). */
const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAGUlEQVR4nGOQs+/4TwlmGDVg1IBRA4aLAQDni+QQi6sbngAAAABJRU5ErkJggg==',
  'base64',
);
/** Un GIF89a de dos fotogramas: una animación. */
const GIF_ANIMADO = Buffer.from(
  'R0lGODlhAQABAIAAAAAAAP///yH/C05FVFNDQVBFMi4wAwEAAAAh+QQACgAAACwAAAAAAQABAAACAkQBACH5BAAKAAAALAAAAAABAAEAAAICRAEAOw==',
  'base64',
);
const IMAGEN_INICIAL =
  'data:image/svg+xml,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%2F%3E';

function sesionDe(api, apodo) {
  return sesionDelBanco(api, apodo, { clave: CLAVE, base: BORDE });
}

function conToken(token) {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

function soloToken(token) {
  return { Authorization: `Bearer ${token}` };
}

test.describe('Recursos gráficos y acciones críticas del catálogo (SA-6)', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let admin;
  let jugador;
  let idProducto;
  let recursoPng;
  const sufijo = Date.now();

  async function subir(token, archivo, clase, { productoId = idProducto } = {}) {
    return api.post('/api/v1/productos/recursos', {
      headers: soloToken(token),
      multipart: {
        archivo,
        ...(clase ? { clase } : {}),
        ...(productoId ? { productoId } : {}),
      },
    });
  }

  async function historial() {
    const respuesta = await api.get(`/api/v1/productos/${idProducto}/historial`, {
      headers: conToken(admin.token),
    });
    expect(respuesta.status()).toBe(200);
    return respuesta.json();
  }

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    admin = await sesionDe(api, ADMIN);
    jugador = await sesionDe(api, `recursos_${sufijo}`);
    expect(admin.claims.rol, 'sembrar.sh deja a admin_e2e como ADMINISTRADOR').toBe(
      'ADMINISTRADOR',
    );
    const alta = await api.post('/api/v1/productos', {
      headers: conToken(admin.token),
      data: {
        nombre: `Escudo de los recursos E2E ${sufijo}`,
        imagen: IMAGEN_INICIAL,
        descripcion: 'Producto del E2E de recursos gráficos.',
        tipo: 'ARMADURA',
        tiraje: -1,
        premium: false,
        precioCreditos: 90,
        defensa: 3,
        parte: 'PECHO',
        tasaDeCaida: 10,
      },
    });
    expect(alta.status(), await alta.text()).toBe(201);
    idProducto = (await alta.json()).id;
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  test('subir es de administración y el archivo se juzga por sus bytes', async () => {
    const png = { name: 'escudo.png', mimeType: 'image/png', buffer: PNG };

    const sinToken = await api.post('/api/v1/productos/recursos', {
      multipart: { archivo: png, clase: 'IMAGEN' },
    });
    expect(sinToken.status()).toBe(401);
    expect((await subir(jugador.token, png, 'IMAGEN')).status()).toBe(403);

    const nuevo = await subir(admin.token, png, 'IMAGEN');
    expect(nuevo.status(), await nuevo.text()).toBe(201);
    recursoPng = await nuevo.json();
    expect(recursoPng).toMatchObject({
      clase: 'IMAGEN',
      formato: 'PNG',
      tipoContenido: 'image/png',
      ancho: 16,
      alto: 16,
      bytes: PNG.length,
      nombre: 'escudo.png',
      productoId: idProducto,
      subidoPor: admin.claims.uid,
      usadoEn: [],
    });
    expect(recursoPng.url).toBe(`/api/v1/productos/recursos/${recursoPng.id}`);
    expect(nuevo.headers().location).toBe(recursoPng.url);
    expect(recursoPng).not.toHaveProperty('contenido');

    const repetido = await subir(admin.token, { ...png, name: 'otra.png' }, 'IMAGEN');
    expect(repetido.status()).toBe(200);
    expect((await repetido.json()).id).toBe(recursoPng.id);

    const html = await subir(
      admin.token,
      { name: 'foto.png', mimeType: 'image/png', buffer: Buffer.from('<html></html>') },
      'IMAGEN',
    );
    expect(html.status()).toBe(415);
    expect(await html.json()).toMatchObject({ motivo: 'FORMATO_NO_ADMITIDO' });

    const fija = await subir(admin.token, png, 'ANIMACION');
    expect(fija.status()).toBe(415);
    expect(await fija.json()).toMatchObject({ motivo: 'SIN_ANIMACION' });

    const sinClase = await subir(admin.token, png, null);
    expect(sinClase.status()).toBe(400);
    expect(await sinClase.json()).toMatchObject({ motivo: 'CLASE_INVALIDA' });

    const animacion = await subir(
      admin.token,
      { name: 'brillo.gif', mimeType: 'image/gif', buffer: GIF_ANIMADO },
      'ANIMACION',
    );
    expect(animacion.status(), await animacion.text()).toBe(201);
    expect(await animacion.json()).toMatchObject({ clase: 'ANIMACION', formato: 'GIF' });
  });

  test('la biblioteca se lista sin bytes; los bytes son públicos y no ejecutan nada', async () => {
    const biblioteca = await api.get(
      `/api/v1/productos/recursos?productoId=${idProducto}&size=16`,
      { headers: conToken(admin.token) },
    );
    expect(biblioteca.status()).toBe(200);
    const pagina = await biblioteca.json();
    expect(pagina.totalElements).toBe(2);
    expect(pagina.content.map((r) => r.clase).sort()).toEqual(['ANIMACION', 'IMAGEN']);
    expect(JSON.stringify(pagina)).not.toContain('contenido');

    const delJugador = await api.get('/api/v1/productos/recursos', {
      headers: conToken(jugador.token),
    });
    expect(delJugador.status()).toBe(403);

    const bytes = await api.get(recursoPng.url);
    expect(bytes.status()).toBe(200);
    expect(bytes.headers()['content-type']).toBe('image/png');
    expect(bytes.headers()['x-content-type-options']).toBe('nosniff');
    expect(bytes.headers()['content-security-policy']).toContain("default-src 'none'");
    expect(bytes.headers()['cache-control']).toContain('immutable');
    expect(Buffer.compare(await bytes.body(), PNG)).toBe(0);
  });

  test('la imagen en uso no se retira; revertir con motivo la libera y retirada se sigue sirviendo', async () => {
    const poner = await api.patch(`/api/v1/productos/${idProducto}`, {
      headers: conToken(admin.token),
      data: { imagen: recursoPng.url },
    });
    expect(poner.status(), await poner.text()).toBe(200);
    expect((await poner.json()).imagen).toBe(recursoPng.url);

    const enUso = await api.delete(`${recursoPng.url}?motivo=Ya%20no%20sirve`, {
      headers: conToken(admin.token),
    });
    expect(enUso.status()).toBe(409);

    const cambio = (await historial()).find((c) => c.revertible);
    expect(
      cambio,
      'la modificación de la imagen es el último cambio y se puede revertir',
    ).toBeTruthy();
    const motivo = `Imagen equivocada ${sufijo}`;
    const revertir = await api.post(`/api/v1/productos/${idProducto}/reversiones`, {
      headers: conToken(admin.token),
      data: { respaldoId: cambio.id, motivo },
    });
    expect(revertir.status(), await revertir.text()).toBe(200);
    expect((await revertir.json()).imagen).toBe(IMAGEN_INICIAL);
    const reversion = (await historial()).find((c) => c.tipo === 'REVERSION');
    expect(reversion).toMatchObject({ motivo, autor: admin.claims.uid });

    const retirar = await api.delete(`${recursoPng.url}?motivo=Duplicada`, {
      headers: conToken(admin.token),
    });
    expect(retirar.status(), await retirar.text()).toBe(200);
    expect(await retirar.json()).toMatchObject({
      motivoRetiro: 'Duplicada',
      retiradoPor: admin.claims.uid,
    });
    const sinRetirados = await api.get(
      `/api/v1/productos/recursos?productoId=${idProducto}&clase=IMAGEN`,
      { headers: conToken(admin.token) },
    );
    expect((await sinRetirados.json()).totalElements).toBe(0);
    const conRetirados = await api.get(
      `/api/v1/productos/recursos?productoId=${idProducto}&clase=IMAGEN&incluirRetirados=true`,
      { headers: conToken(admin.token) },
    );
    expect((await conRetirados.json()).totalElements).toBe(1);
    expect((await api.get(recursoPng.url)).status()).toBe(200);
  });

  test('suspender y reactivar dejan su motivo en el historial', async () => {
    const motivoSuspension = `Revisión de balance ${sufijo}`;
    const suspender = await api.put(
      `/api/v1/productos/${idProducto}/suspender?motivo=${encodeURIComponent(motivoSuspension)}`,
      { headers: conToken(admin.token) },
    );
    expect(suspender.status(), await suspender.text()).toBe(200);
    expect((await suspender.json()).estado).toBe('SUSPENDIDO');

    const largo = await api.put(
      `/api/v1/productos/${idProducto}/reactivar?motivo=${'x'.repeat(501)}`,
      { headers: conToken(admin.token) },
    );
    expect(largo.status()).toBe(400);

    const reactivar = await api.put(
      `/api/v1/productos/${idProducto}/reactivar?motivo=${encodeURIComponent('Balance corregido')}`,
      { headers: conToken(admin.token) },
    );
    expect(reactivar.status(), await reactivar.text()).toBe(200);

    const cambios = await historial();
    expect(cambios.find((c) => c.tipo === 'SUSPENSION')).toMatchObject({
      motivo: motivoSuspension,
    });
    expect(cambios.find((c) => c.tipo === 'REACTIVACION')).toMatchObject({
      motivo: 'Balance corregido',
    });

    const delJugador = await api.put(`/api/v1/productos/${idProducto}/suspender?motivo=x`, {
      headers: conToken(jugador.token),
    });
    expect(delJugador.status()).toBe(403);
  });
});
