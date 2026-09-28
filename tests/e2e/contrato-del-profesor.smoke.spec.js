/**
 * B13 · Lo que el profesor señaló, comprobado en el entorno desplegado (DEV).
 *
 * La prueba del profesor (`profesor.profesor.spec.js`) recorre el ciclo del
 * juego en veinte pasos. Esta suite recorre, contra el mismo AWS DEV y por el
 * mismo borde público, los puntos de su retroalimentación que esos veinte
 * pasos no tocan:
 *
 *   1. lista negra: «SpiderMan» y sus variantes no pasan como apodo, y la
 *      cuenta no se crea (7.1.1);
 *   2. inventario contractual: el catálogo público trae, con su nombre exacto,
 *      los héroes, armas, armaduras, ítems y épicas del documento
 *      (`contracts/esquemas/catalogo-oficial.yaml`, sección 6);
 *   3. detalle, calificación y comentarios: una jugadora califica un producto
 *      real una sola vez y comenta; el hilo lo muestra (7.1);
 *   4. mensajería privada: A escribe a B; B lo ve en su bandeja y en el
 *      historial, con A como remitente; C no lo lee (feedback del profesor);
 *   5. tienda: A compra con la tarjeta de prueba 4242 de la pasarela simulada,
 *      la orden queda COMPLETA y el producto llega a su inventario (7.5).
 *
 * Las cuentas son @nexus.test: su correo va al buzón de pruebas de DEV y se
 * verifican por el mismo camino que la prueba del profesor (B1). Nada se
 * prepara a mano ni se simula: si un servicio no está, la prueba lo dice.
 */

import fs from 'node:fs';
import path from 'node:path';

import { test, expect, request as apiRequest } from '@playwright/test';

import { iniciarSesion, registrar, sesionDe } from './ayudantes/cuentas.js';

const AWS = process.env.E2E_AWS ?? 'http://35.168.124.119';
const CLAVE = process.env.E2E_CLAVE ?? 'Contrasena-E2E-2026';
const MARCA = `${Date.now().toString(36)}${Math.floor(Math.random() * 1e3)}`;

// Los specs corren como CommonJS (no hay "type": "module" en tests/).
const AQUI =
  typeof __dirname === 'undefined' ? path.resolve(process.cwd(), '../../tests/e2e') : __dirname;
const CATALOGO_OFICIAL = path.resolve(AQUI, '../../contracts/esquemas/catalogo-oficial.yaml');

/** Tipo del catálogo de productos por sección del catálogo oficial. */
const SECCIONES = Object.freeze({
  heroes: 'HEROE',
  armas: 'ARMA',
  armaduras: 'ARMADURA',
  items: 'ITEM',
  epicas: 'EPICA',
});

/**
 * Los nombres del catálogo oficial por sección. El fichero es YAML plano con
 * una lista por sección, en bloque (`- nombre: ...`) o en línea
 * (`- {nombre: ..., heroe: ...}`): basta leer eso, sin dependencias.
 */
function nombresOficiales() {
  const lineas = fs.readFileSync(CATALOGO_OFICIAL, 'utf8').split(/\r?\n/);
  const porSeccion = {};
  let actual = null;
  for (const linea of lineas) {
    const seccion = /^([a-z]+):\s*$/.exec(linea);
    if (seccion) {
      actual = SECCIONES[seccion[1]] ? seccion[1] : null;
      continue;
    }
    const nombre = /^\s*-\s*\{?\s*nombre:\s*([^,}]+?)\s*(?:,|\}|$)/.exec(linea);
    if (actual && nombre) {
      (porSeccion[SECCIONES[actual]] ??= []).push(nombre[1].replace(/^["']|["']$/g, ''));
    }
  }
  return porSeccion;
}

function conToken(token) {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

/** Todos los productos públicos de un tipo, página a página (máximo 50 por página). */
async function productosDelTipo(api, tipo) {
  const todos = [];
  for (let pagina = 0; pagina < 10; pagina++) {
    const r = await api.get(`/api/v1/productos?tipo=${tipo}&page=${pagina}&size=50`);
    expect(r.status(), `catálogo ${tipo}, página ${pagina}: ${await r.text()}`).toBe(200);
    const cuerpo = await r.json();
    todos.push(...cuerpo.content);
    if (pagina + 1 >= cuerpo.totalPages) {
      break;
    }
  }
  return todos;
}

test.describe('B13 · lo que pidió el profesor, en DEV', () => {
  // La lista negra y el catálogo no dependen de nada: si una falla, las
  // demás se siguen comprobando. Solo 3-5 van en serie (comparten a A).

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let ana;
  let bruno;
  let carla;
  let producto;

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: AWS });
  });

  test.afterAll(async () => {
    await api?.dispose();
  });

  test('1 · la lista negra no deja registrar «SpiderMan» ni sus variantes, y no crea la cuenta', async () => {
    for (const [indice, apodo] of ['SpiderMan', 'sp1derman', 'spider_man'].entries()) {
      const email = `negra_${MARCA}_${indice}@nexus.test`;
      const registro = await registrar(api, { apodo, email, clave: CLAVE });
      expect(registro.estado, `${apodo}: ${registro.texto}`).toBe(400);
      expect(registro.tipo, `${apodo}: ${registro.texto}`).toBe('apodo-no-permitido');
      // No quedó ninguna cuenta con ese correo: el login no la conoce.
      const login = await iniciarSesion(api, email, CLAVE);
      expect(login.estado, `${apodo}: ${login.texto}`).toBe(401);
    }
  });

  test('2 · el catálogo público trae los productos del documento con su nombre exacto', async () => {
    const oficiales = nombresOficiales();
    expect(Object.keys(oficiales).sort()).toEqual(Object.values(SECCIONES).sort());
    for (const [tipo, nombres] of Object.entries(oficiales)) {
      const publicados = new Set((await productosDelTipo(api, tipo)).map((p) => p.nombre));
      const faltan = nombres.filter((n) => !publicados.has(n));
      expect(faltan, `${tipo}: faltan en el catálogo público`).toEqual([]);
    }
    // 8 héroes, 16 armas, 16 armaduras, 8 ítems y 8 épicas (sección 6).
    expect(oficiales.HEROE).toHaveLength(8);
    expect(oficiales.ARMA).toHaveLength(16);
    expect(oficiales.ARMADURA).toHaveLength(16);
    expect(oficiales.ITEM).toHaveLength(8);
    expect(oficiales.EPICA).toHaveLength(8);
  });

  test.describe('con cuentas propias (A, B y C)', () => {
    test.describe.configure({ mode: 'serial' });

    test('3 · una jugadora califica un producto real una sola vez y comenta; el hilo lo muestra', async () => {
      test.setTimeout(180_000);
      ana = await sesionDe(api, `ana_${MARCA}`, { clave: CLAVE, base: AWS });
      const armas = await productosDelTipo(api, 'ARMA');
      producto = armas.find((p) => nombresOficiales().ARMA.includes(p.nombre));
      expect(producto, 'un arma del catálogo oficial').toBeTruthy();

      const calificacion = await api.post(`/api/v1/products/${producto.id}/rating`, {
        headers: conToken(ana.token),
        data: { estrellas: 5 },
      });
      expect(calificacion.status(), await calificacion.text()).toBe(201);
      const otra = await api.post(`/api/v1/products/${producto.id}/rating`, {
        headers: conToken(ana.token),
        data: { estrellas: 1 },
      });
      expect(otra.status()).toBe(409);
      expect((await otra.json()).type).toMatch(/ya-calificado$/);

      const resumen = await api.get(`/api/v1/products/${producto.id}/rating`);
      expect(resumen.status()).toBe(200);
      expect((await resumen.json()).total).toBeGreaterThanOrEqual(1);

      const texto = `Buen equilibrio entre daño y peso para la primera partida (${MARCA})`;
      const comentario = await api.post(`/api/v1/products/${producto.id}/comments`, {
        headers: conToken(ana.token),
        data: { texto },
      });
      expect(comentario.status(), await comentario.text()).toBe(201);

      const hilo = await api.get(`/api/v1/products/${producto.id}/comments?pagina=0&tamano=10`);
      expect(hilo.status()).toBe(200);
      expect(JSON.stringify(await hilo.json())).toContain(texto);
    });

    test('4 · A escribe a B por mensaje privado; B lo recibe con A como remitente y C no lo lee', async () => {
      test.setTimeout(240_000);
      bruno = await sesionDe(api, `bruno_${MARCA}`, { clave: CLAVE, base: AWS });
      carla = await sesionDe(api, `carla_${MARCA}`, { clave: CLAVE, base: AWS });
      const texto = `hola bruno, ¿jugamos una partida? (${MARCA})`;

      const envio = await api.post(
        `/api/v1/mensajes-directos/conversaciones/${bruno.claims.uid}/mensajes`,
        { headers: conToken(ana.token), data: { texto, idCliente: `dm-${MARCA}` } },
      );
      expect(envio.status(), await envio.text()).toBe(201);

      const bandeja = await api.get('/api/v1/mensajes-directos/conversaciones', {
        headers: conToken(bruno.token),
      });
      expect(bandeja.status()).toBe(200);
      const conAna = (await bandeja.json()).find((c) => c.uidOtro === ana.claims.uid);
      expect(conAna, 'la conversación con A en la bandeja de B').toBeTruthy();
      expect(conAna.noLeidos).toBeGreaterThanOrEqual(1);

      const historial = await api.get(
        `/api/v1/mensajes-directos/conversaciones/${ana.claims.uid}/mensajes?limite=20`,
        { headers: conToken(bruno.token) },
      );
      expect(historial.status()).toBe(200);
      const recibido = (await historial.json()).find((m) => m.texto === texto);
      expect(recibido, 'el mensaje en el historial de B').toBeTruthy();
      expect(recibido.remitente).toBe(ana.claims.uid);
      expect(recibido.destinatario).toBe(bruno.claims.uid);

      // C solo ve sus conversaciones: con A no tiene ninguna, y el texto no le llega.
      const deCarla = await api.get(
        `/api/v1/mensajes-directos/conversaciones/${ana.claims.uid}/mensajes?limite=20`,
        { headers: conToken(carla.token) },
      );
      expect(deCarla.status()).toBe(200);
      expect(JSON.stringify(await deCarla.json())).not.toContain(texto);
    });

    test('5 · A compra con la tarjeta de prueba 4242 y el producto llega a su inventario', async () => {
      test.setTimeout(180_000);
      const vitrina = await api.get('/api/v1/vitrina?page=0&size=50', {
        headers: conToken(ana.token),
      });
      expect(vitrina.status(), await vitrina.text()).toBe(200);
      const candidatos = (await vitrina.json()).content.filter(
        (p) => Number(p.precioFinal) > 0 && !p.esPropio,
      );
      expect(candidatos.length, 'productos a la venta en COP').toBeGreaterThan(0);

      let elegido = null;
      for (const candidato of candidatos) {
        const alta = await api.post('/api/v1/carrito/items', {
          headers: conToken(ana.token),
          data: { productoId: candidato.id, cantidad: 1 },
        });
        if (alta.status() === 200) {
          elegido = candidato;
          break;
        }
        // Uno agotado o sin precio en moneda real se salta; cualquier otra cosa es un fallo.
        expect([409, 422], `al carrito ${candidato.nombre}: ${await alta.text()}`).toContain(
          alta.status(),
        );
      }
      expect(elegido, 'un producto que se pueda comprar').toBeTruthy();

      const pago = await api.post('/api/v1/checkout', {
        headers: { ...conToken(ana.token), 'Idempotency-Key': `dev-compra-${MARCA}` },
        data: {
          titular: 'Ana De Prueba',
          numeroTarjeta: '4242 4242 4242 4242',
          vencimiento: '12/39',
          codigoSeguridad: '123',
          moneda: 'COP',
        },
      });
      expect(pago.status(), await pago.text()).toBe(201);
      const orden = await pago.json();
      expect(orden.estado).toBe('COMPLETA');
      expect(orden.medioDePago).toEqual({ marca: 'VISA', ultimos4: '4242' });

      const inventario = await api.get('/api/v1/inventario/elementos', {
        headers: conToken(ana.token),
      });
      expect(inventario.status()).toBe(200);
      const propios = (await inventario.json()).elementos.filter(
        (e) => e.productoId === elegido.id,
      );
      expect(propios.length, `${elegido.nombre} en el inventario de A`).toBeGreaterThanOrEqual(1);
    });
  });
});
