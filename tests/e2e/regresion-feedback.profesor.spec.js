/**
 * PR-F · Regresión del feedback de producto del 4-oct, con cuentas NUEVAS.
 *
 * Es el chequeo «con una cuenta nueva» de la auditoría del 4-oct, de punta a
 * punta y sin atajos. Por el sufijo `.profesor.spec.js` corre donde corre la
 * prueba del profesor:
 *
 *   - en el banco (`e2e.yml`, «La prueba del profesor en el banco»), en cada PR;
 *   - contra AWS DEV después de cada despliegue (`prueba-del-profesor.yml`),
 *     con el buzón por el túnel SSH. Es la evidencia en AWS de los cinco
 *     cambios autorizados.
 *
 * El recorrido, en el orden de un jugador nuevo:
 *
 *   1. Portada pública `/` (F6): productos reales con su precio en créditos y
 *      sin «Añadir»; la ficha se abre sin cuenta; «Entra para comprar» lleva a
 *      `/login` con la vuelta a la tienda.
 *   2. Entrar por la interfaz y volver a la tienda.
 *   3. Comprar con los créditos del Nexo (D-44): saldo actual, precio y saldo
 *      después que calcula el servidor; DOBLE CLIC en «Confirmar»: una orden,
 *      un débito de exactamente el precio y el producto una vez en el inventario.
 *   4. Subastar ese producto (D-43): «Incremento mínimo: 5 créditos» en pantalla
 *      y en el servidor; con 20 vigente, 24 se rechaza y 25 entra.
 *   5. Batalla contra la IA (PR-A): ningún golpe le quita vida a quien lo lanza
 *      —ni al jugador ni a la máquina— salvo un REFLEJO declarado, y el daño que
 *      recibe cada uno lo causa otro; la máquina se nombra «(IA)».
 *   6. Misiones (D-42): ninguna recomienda más del nivel 8; la de nivel 1 está
 *      disponible para el héroe nuevo y se juega con el motor real; la
 *      experiencia queda en el inventario y, según el resultado, se desbloquea
 *      la siguiente (éxito) o se puede volver a intentar (fallo): sin callejón
 *      sin salida. Que no «siempre falle» lo mide BalanceDeMisionesTest con el
 *      motor real y miles de tiradas; aquí se exige el recorrido, no la suerte.
 *
 * Reglas que se impone, las mismas que la prueba del profesor:
 *
 *   - Tres cuentas desechables de QA (`qa_fb…@nexus.test`): la jugadora y dos
 *     postores. La contraseña es aleatoria y no se imprime ni va a ningún título
 *     de paso (la configuración apaga la traza y el vídeo).
 *   - Nada de credenciales de servicio ni de semillas: en DEV no las hay, y un
 *     jugador nuevo tampoco. Todo sale del alta (créditos de bienvenida y héroe).
 *   - Una línea `PROFESOR|…` por paso, que el flujo de DEV pasa al resumen de la
 *     corrida, y ningún error de página ni 5xx en todo el recorrido.
 *
 * Solo en escritorio: lo que se prueba no depende de la anchura.
 */

import { randomBytes } from 'node:crypto';

import { test, expect, request as apiRequest } from '@playwright/test';

import { sesionDe } from './ayudantes/cuentas.js';
import { servicioNoDesplegadoDe } from './ayudantes/no-desplegados.js';

const BASE = process.env.PROFESOR_URL ?? process.env.E2E_AWS ?? 'http://localhost:8099';
const ESCRITORIO = 'escritorio-1440';
/** Lo que sale en la columna «Anchura» del resumen de DEV. */
const ETIQUETA = 'escritorio-1440 · feedback 4-oct';
const VISTAS = '/frontend/app-web/src';
const RUTA = {
  sala: `${VISTAS}/plataforma/salas-partidas/sala-batalla.html`,
  misiones: `${VISTAS}/contenido/misiones/misiones.html`,
  publicar: `${VISTAS}/cuentas/publicar-subasta.html`,
};
/** D-43: el incremento mínimo entre pujas que publica admin-parametros. */
const INCREMENTO_MINIMO = 5;
const PRECIO_INICIAL_DE_LA_SUBASTA = 20;
/** §6.1.1: el héroe «puede incrementarse hasta el nivel 8». */
const NIVEL_MAXIMO = 8;
/** D-42: la primera misión de la progresión y la que desbloquea. */
const PRIMERA_MISION = 'sendero-de-los-aprendices';
const SEGUNDA_MISION = 'mina-abandonada';
const CATEGORIAS = ['HISTORIA', 'DESAFIO', 'EXPLORACION'];

// ------------------------------------------------------------- utilidades

/** Apodo, correo y contraseña desechables, reconocibles como de QA. */
function cuentaDesechable(rol) {
  const sufijo = `${Date.now().toString(36).slice(-6)}${randomBytes(2).toString('hex')}`;
  const apodo = `qa_fb${rol}_${sufijo}`.slice(0, 24);
  return {
    apodo,
    email: `${apodo}@nexus.test`,
    // La política de RF-AUT-002: más de 8, mayúscula, minúscula, cifra y símbolo.
    clave: `Qa-${randomBytes(12).toString('base64url')}-7z`,
  };
}

/**
 * Escribe un secreto en un campo sin que su valor quede en el informe: `fill`
 * lo pondría en el título del paso.
 */
async function escribirSecreto(campo, valor) {
  await campo.focus();
  await campo.evaluate((el, v) => {
    el.value = v;
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }, valor);
}

function conToken(token, extra = {}) {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', ...extra };
}

/** «1 crédito», «150 créditos», «1.250 créditos»: como lo escribe la tienda. */
function enCreditos(n) {
  return `${n.toLocaleString('es-CO')} ${n === 1 ? 'crédito' : 'créditos'}`;
}

async function altaTerminada(api, quien) {
  await expect
    .poll(
      async () => {
        const r = await api.get('/api/v1/auth/onboarding', { headers: conToken(quien.token) });
        return r.ok() ? (await r.json()).estado : `HTTP ${r.status()}`;
      },
      { timeout: 90_000, intervals: [1_000, 2_000, 5_000], message: `el alta de ${quien.apodo}` },
    )
    .toBe('COMPLETO');
}

async function saldoDe(api, quien) {
  const r = await api.get(`/api/v1/creditos/${quien.claims.uid}/saldo`, {
    headers: conToken(quien.token),
  });
  expect(r.status(), `saldo: ${await r.text()}`).toBe(200);
  return Number((await r.json()).saldoDisponible);
}

async function inventarioDe(api, quien) {
  const r = await api.get('/api/v1/inventario/elementos?pagina=0', {
    headers: conToken(quien.token),
  });
  expect(r.status(), `inventario: ${await r.text()}`).toBe(200);
  return (await r.json()).elementos ?? [];
}

async function heroeDe(api, quien) {
  const heroe = (await inventarioDe(api, quien)).find((e) => e.tipo === 'HEROE');
  expect(heroe, 'el alta deja un héroe en el inventario').toBeTruthy();
  return heroe;
}

/** La vitrina entera vista por quien pregunta (marca lo que ya tiene). */
async function vitrinaDe(api, quien) {
  const productos = [];
  for (let pagina = 0; pagina < 20; pagina += 1) {
    const r = await api.get(`/api/v1/vitrina?page=${pagina}&size=50`, {
      headers: conToken(quien.token),
    });
    expect(r.status(), `vitrina: ${await r.text()}`).toBe(200);
    const cuerpo = await r.json();
    productos.push(...cuerpo.content);
    if (cuerpo.last || cuerpo.content.length === 0) {
      break;
    }
  }
  return productos;
}

/** Todas las misiones del tablón de una categoría, página a página. */
async function tablonDe(api, quien, categoria) {
  const misiones = [];
  for (let pagina = 0; pagina < 20; pagina += 1) {
    const r = await api.get(`/api/v1/misiones?categoria=${categoria}&pagina=${pagina}`, {
      headers: conToken(quien.token),
    });
    expect(r.status(), `tablón ${categoria}: ${await r.text()}`).toBe(200);
    const cuerpo = await r.json();
    misiones.push(...cuerpo.misiones);
    if (pagina + 1 >= cuerpo.totalPaginas) {
      break;
    }
  }
  return misiones;
}

/** El reporte de una ejecución, cuando la simulación y la entrega terminaron. */
async function reporteTerminado(api, quien, ejecucionId, limite) {
  let reporte = null;
  await expect
    .poll(
      async () => {
        const r = await api.get(`/api/v1/misiones/ejecuciones/${ejecucionId}`, {
          headers: conToken(quien.token),
        });
        if (r.status() !== 200) {
          return `HTTP ${r.status()}`;
        }
        reporte = await r.json();
        return reporte.recompensas?.entregaPendiente ? 'entrega pendiente' : 'terminada';
      },
      {
        timeout: limite,
        intervals: [1_000, 2_000, 5_000],
        message: 'la misión no terminó (DEV: 3 minutos de misión más la vuelta del trabajo)',
      },
    )
    .toBe('terminada');
  return reporte;
}

/** Los cuerpos JSON de los MESSAGE de STOMP que llegan por un WebSocket. */
function cuerposStomp(texto) {
  const cuerpos = [];
  for (const marco of String(texto).split('\0')) {
    if (!marco.startsWith('MESSAGE')) {
      continue;
    }
    const inicio = marco.indexOf('\n\n');
    if (inicio < 0) {
      continue;
    }
    try {
      cuerpos.push(JSON.parse(marco.slice(inicio + 2)));
    } catch {
      // Un marco que no es JSON no es un aviso de combate.
    }
  }
  return cuerpos;
}

/**
 * Entra por el formulario y espera a estar en `destino` (se mira la ruta, no la
 * dirección entera: la vuelta `?volver=…tienda.html` ya va en la del login).
 *
 * Antes de escribir se espera a que la página de entrada termine de cargar:
 * se llega a ella navegando desde la portada, y `toHaveURL` vuelve en cuanto
 * cambia la dirección, no cuando `login.js` ya escucha el envío. Sin esa
 * espera, en DEV el clic llegó antes que el guion (4-oct, corrida
 * 37194290879): el formulario se envió solo, por GET, y la página volvió a
 * empezar en blanco.
 *
 * El borde admite 30 entradas por minuto y dirección; en DEV, la prueba del
 * profesor acaba de usar unas cuantas desde el mismo runner. Si lo que frena es
 * ese límite, se espera y se vuelve a intentar, como lo haría una persona;
 * cualquier otro rechazo, falla diciendo dónde se quedó.
 */
async function entrarPorElFormulario(page, cuenta, destino) {
  for (let intento = 1; intento <= 3; intento += 1) {
    await page.waitForLoadState('load');
    await expect(page.locator('#formLogin')).toBeVisible();
    await page.fill('#email', cuenta.email);
    await escribirSecreto(page.locator('#password'), cuenta.clave);
    await page.click('#botonEnviar');
    const llego = await page
      .waitForURL((url) => destino.test(url.pathname), { timeout: 30_000 })
      .then(() => true)
      .catch(() => false);
    if (llego) {
      return intento;
    }
    const estado = (await page.locator('#estadoLogin').textContent()) ?? '';
    const donde = new URL(page.url()).pathname;
    expect(estado, `la entrada no avanzó (en ${donde}) y no fue por el límite del borde`).toMatch(
      /demasiad|espera/i,
    );
    await page.waitForTimeout(30_000);
  }
  throw new Error('no se pudo entrar en tres intentos');
}

async function capturar(page, testInfo, nombre) {
  const ruta = testInfo.outputPath(`${nombre}.png`);
  await page.screenshot({ path: ruta });
  await testInfo.attach(nombre, { path: ruta, contentType: 'image/png' });
}

// ------------------------------------------------------------- bitácora

/**
 * Cada paso con su resultado, y lo que el navegador dijo mientras tanto: el
 * mismo formato que la prueba del profesor, para que el resumen de DEV lo
 * pinte en su tabla.
 */
function abrirBitacora(page, context) {
  const pasos = [];
  const incidencias = [];
  let pasoEnCurso = 0;

  context.on('response', (respuesta) => {
    const url = new URL(respuesta.url());
    if (
      url.pathname.startsWith('/api/') &&
      respuesta.status() >= 500 &&
      servicioNoDesplegadoDe(url.pathname) === null
    ) {
      incidencias.push({
        paso: pasoEnCurso,
        tipo: 'http',
        detalle: `${respuesta.request().method()} ${url.pathname} → ${respuesta.status()}`,
      });
    }
  });
  context.on('weberror', (error) => {
    const mensaje = String(error.error()?.message ?? error.error());
    if (!/sin sesi[oó]n/i.test(mensaje)) {
      incidencias.push({ paso: pasoEnCurso, tipo: 'pagina', detalle: mensaje.slice(0, 200) });
    }
  });

  async function paso(numero, titulo, cuerpo) {
    pasoEnCurso = numero;
    const registro = { paso: numero, titulo, estado: 'OK', detalle: '', ms: 0 };
    pasos.push(registro);
    const inicio = Date.now();
    try {
      await test.step(`${numero}. ${titulo}`, async () => {
        const detalle = await cuerpo();
        if (detalle) {
          registro.detalle = String(detalle);
        }
      });
    } catch (error) {
      registro.estado = 'FALLO';
      registro.detalle = String(error?.message ?? error)
        .split('\n')[0]
        .slice(0, 300);
      throw error;
    } finally {
      registro.ms = Date.now() - inicio;
    }
  }

  async function cerrar(testInfo) {
    await testInfo.attach('bitacora-feedback-4-oct.json', {
      body: JSON.stringify({ pasos, incidencias }, null, 2),
      contentType: 'application/json',
    });
    const limpio = (texto) => String(texto).replaceAll('|', '/').replaceAll('\n', ' ');
    for (const p of pasos) {
      console.log(
        `PROFESOR|${ETIQUETA}|${p.paso}|${limpio(p.titulo)}|${p.estado}|${limpio(p.detalle)}|${p.ms}`,
      );
    }
    for (const i of incidencias) {
      console.log(`PROFESOR-INCIDENCIA|${ETIQUETA}|${i.paso}|${i.tipo}|${i.detalle}`);
    }
  }

  return { paso, cerrar, incidencias };
}

// ------------------------------------------------------------------ prueba

test.describe('PR-F · el feedback del 4-oct, con cuentas nuevas', () => {
  test('portada → compra con créditos → subasta +5 → IA sin auto-daño → misión de nivel 1', async ({
    page,
    context,
  }, testInfo) => {
    test.skip(testInfo.project.name !== ESCRITORIO, 'lo que se prueba no depende de la anchura');
    // La misión de nivel 1 dura 3 minutos de verdad en DEV (0,05 h).
    test.setTimeout(16 * 60_000);

    const api = await apiRequest.newContext({ baseURL: BASE });
    const bitacora = abrirBitacora(page, context);
    const { paso } = bitacora;
    const cuentas = {
      jugadora: cuentaDesechable(''),
      postor1: cuentaDesechable('p1'),
      postor2: cuentaDesechable('p2'),
    };
    const sesiones = {};
    let producto = null;
    let elementoComprado = null;

    try {
      await paso(0, 'Tres cuentas nuevas con su alta real', async () => {
        for (const [rol, cuenta] of Object.entries(cuentas)) {
          sesiones[rol] = await sesionDe(api, cuenta.apodo, {
            clave: cuenta.clave,
            email: cuenta.email,
            base: BASE,
          });
        }
        for (const sesion of Object.values(sesiones)) {
          await altaTerminada(api, sesion);
        }
        const saldo = await saldoDe(api, sesiones.jugadora);
        expect(saldo, 'el alta acredita los créditos de bienvenida').toBeGreaterThan(0);
        return `${Object.keys(sesiones).length} cuentas qa_fb…; ${saldo} créditos de bienvenida`;
      });
      const jugadora = sesiones.jugadora;

      await paso(1, 'Portada pública con la tienda, antes de entrar (F6)', async () => {
        const respuesta = await page.goto('/');
        expect(respuesta?.status(), 'la raíz se sirve, no redirige').toBe(200);
        expect(new URL(page.url()).pathname).toBe('/');
        await expect(page.locator('body[data-vista="portada"]')).toBeVisible();
        const tarjetas = page.locator('.vitrina-publica .product-card');
        await expect(tarjetas.first()).toBeVisible({ timeout: 20_000 });
        await expect(page.locator('.vitrina-publica .btn-add')).toHaveCount(0);
        // D-44 a la vista de quien todavía no tiene cuenta: el precio en créditos.
        await expect(page.locator('.vitrina-publica [data-precio-creditos]').first()).toBeVisible();
        const total = await tarjetas.count();

        await tarjetas.first().locator('[data-ver-producto]').click();
        const ficha = page.locator('[role="dialog"].ficha');
        await expect(ficha).toBeVisible({ timeout: 20_000 });
        await capturar(page, testInfo, 'fb-01-portada-ficha');
        await ficha.locator('[data-accion="entrar-para-comprar"]').click();
        await expect(page).toHaveURL(/\/login\?/);
        const destino = new URL(page.url());
        expect(destino.pathname).toBe('/login');
        expect(destino.searchParams.get('volver')).toMatch(/tienda\.html$/);
        return (
          `/ → 200 sin redirección; ${total} productos reales con precio en créditos y sin ` +
          '«Añadir»; ficha pública; «Entra para comprar» → /login?volver=…tienda.html'
        );
      });

      await paso(2, 'Entrar y volver a la tienda', async () => {
        await expect(page.locator('#formLogin')).toBeVisible();
        const intentos = await entrarPorElFormulario(page, cuentas.jugadora, /tienda/);
        await expect(page.locator('#productos-grid .product-card').first()).toBeVisible({
          timeout: 20_000,
        });
        const conSesion = await page.evaluate(() => Boolean(sessionStorage.getItem('nexus.token')));
        expect(conSesion, 'la sesión quedó abierta').toBe(true);
        return `/login → ${new URL(page.url()).pathname}, con sesión (${intentos} intento/s)`;
      });

      await paso(3, 'Comprar con créditos del Nexo, con doble clic (D-44)', async () => {
        const saldoAntes = await saldoDe(api, jugadora);
        // Lo más barato que se puede pagar con lo que da el alta y que todavía
        // no se tiene (RF-CAR-004). El precio en créditos es el del catálogo.
        const candidatos = (await vitrinaDe(api, jugadora))
          .filter(
            (p) =>
              Number.isInteger(p.precioCreditos) &&
              p.precioCreditos > 0 &&
              p.precioCreditos <= saldoAntes &&
              !p.esPropio &&
              p.tipo !== 'HEROE',
          )
          .sort((a, b) => a.precioCreditos - b.precioCreditos || a.nombre.localeCompare(b.nombre));
        expect(
          candidatos.length,
          'algo de la tienda se paga con los créditos del alta',
        ).toBeGreaterThan(0);
        producto = candidatos[0];
        const precio = producto.precioCreditos;

        await page.locator('#busqueda-tienda').fill(producto.nombre);
        const tarjeta = page.locator(
          `#productos-grid .product-card[data-id-producto="${producto.id}"]`,
        );
        await expect(tarjeta).toBeVisible({ timeout: 20_000 });
        // G3 (1.7.0): si solo se vende en créditos, ese ES su precio; si tiene
        // los dos, el de créditos va como «o N créditos».
        await expect(tarjeta.locator('[data-precio-creditos]')).toHaveText(
          producto.precioFinal === null ? enCreditos(precio) : `o ${enCreditos(precio)}`,
        );
        const alta = page.waitForResponse(
          (r) => r.url().includes('/api/v1/carrito/items') && r.request().method() === 'POST',
        );
        await tarjeta.locator('.btn-add').click();
        expect((await alta).status()).toBe(200);

        // El precio y el saldo los calcula el servidor, no la pantalla.
        const cotizacion = await api.get('/api/v1/checkout/creditos', {
          headers: conToken(jugadora.token),
        });
        expect(cotizacion.status(), await cotizacion.text()).toBe(200);
        expect(await cotizacion.json()).toMatchObject({
          pagable: true,
          totalCreditos: precio,
          saldoDisponible: saldoAntes,
          saldoDespues: saldoAntes - precio,
          alcanza: true,
        });

        await page.locator('#btn-pagar').click();
        const dialogo = page.getByRole('dialog', { name: 'Pagar tu compra' });
        await expect(dialogo.getByText('¿Cómo quieres pagar?')).toBeVisible();
        await dialogo.getByLabel(/Créditos del Nexo/).check();
        const zona = dialogo.locator('[data-zona="creditos"]');
        await expect(zona.locator('[data-zona="saldo-actual"] dd')).toHaveText(
          enCreditos(saldoAntes),
        );
        await expect(zona.locator('[data-zona="precio"] dd')).toHaveText(enCreditos(precio));
        await expect(zona.locator('[data-zona="saldo-despues"] dd')).toHaveText(
          enCreditos(saldoAntes - precio),
        );
        const confirmar = dialogo.locator('[data-accion="confirmar-pago"]');
        await expect(confirmar).toHaveText(`Confirmar compra por ${enCreditos(precio)}`);

        // El doble clic de la auditoría: todas las peticiones con la MISMA clave.
        const claves = [];
        const anotar = (peticion) => {
          if (
            peticion.method() === 'POST' &&
            new URL(peticion.url()).pathname === '/api/v1/checkout/creditos'
          ) {
            claves.push(peticion.headers()['idempotency-key']);
          }
        };
        page.on('request', anotar);
        await confirmar.dblclick();
        await expect(dialogo.locator('.pago__resultado')).toContainText('Compra realizada', {
          timeout: 30_000,
        });
        page.off('request', anotar);
        expect(claves.length, 'el pago salió').toBeGreaterThan(0);
        expect(new Set(claves).size, 'un doble clic es la misma compra').toBe(1);
        await capturar(page, testInfo, 'fb-03-compra-realizada');

        // Una orden pagada con créditos, un débito por exactamente el precio.
        const ordenes = await (
          await api.get('/api/v1/ordenes', { headers: conToken(jugadora.token) })
        ).json();
        const conCreditos = ordenes.filter((o) => o.formaDePago === 'CREDITOS');
        expect(conCreditos, 'una sola orden pagada con créditos').toHaveLength(1);
        const [orden] = conCreditos;
        expect(Number(orden.total)).toBe(precio);
        expect(await saldoDe(api, jugadora), 'el saldo baja una vez, exactamente el precio').toBe(
          saldoAntes - precio,
        );
        const movimientos = await api.get(
          `/api/v1/creditos/${jugadora.claims.uid}/movimientos?size=50`,
          { headers: conToken(jugadora.token) },
        );
        expect(movimientos.status(), await movimientos.text()).toBe(200);
        const deLaOrden = (await movimientos.json()).content.filter(
          (m) => m.referenciaId === `tienda-orden-${orden.id}`,
        );
        expect(deLaOrden, 'un solo débito con el refId de la orden').toHaveLength(1);
        expect(deLaOrden[0]).toMatchObject({ tipo: 'DEBITO' });
        expect(Number(deLaOrden[0].monto)).toBe(precio);

        // «Ver inventario»: el producto, una vez.
        await dialogo.locator('[data-accion="ver-inventario"]').click();
        await expect(page).toHaveURL(/inventario/);
        await page.locator('#pestana-objetos').click();
        await expect(
          page.locator('.vitrina__nombre', { hasText: producto.nombre }).first(),
        ).toBeVisible({ timeout: 20_000 });
        const comprados = (await inventarioDe(api, jugadora)).filter(
          (e) => e.productoId === producto.id,
        );
        expect(comprados, 'entregado una vez').toHaveLength(1);
        [elementoComprado] = comprados;
        return (
          `«${producto.nombre}» por ${enCreditos(precio)}: saldo ${saldoAntes} → ` +
          `${saldoAntes - precio}; ${claves.length} petición/es con una sola clave; 1 orden ` +
          `${orden.estado}, 1 débito tienda-orden-…; en el inventario`
        );
      });

      await paso(4, 'Subastar lo comprado: incremento mínimo de 5 créditos (D-43)', async () => {
        const reglas = await (await api.get('/api/v1/subastas/reglas')).json();
        expect(reglas.incrementoMinimoConfigurado, 'el incremento sale de admin-parametros').toBe(
          true,
        );
        expect(Number(reglas.incrementoMinimo)).toBe(INCREMENTO_MINIMO);

        await page.goto(RUTA.publicar);
        await expect(page.locator('[data-incremento-minimo]')).toHaveText(
          `Incremento mínimo: ${INCREMENTO_MINIMO} créditos`,
          { timeout: 20_000 },
        );
        await expect(page.locator('body')).not.toContainText('DECISIÓN PO');
        await capturar(page, testInfo, 'fb-04-publicar-subasta');

        const publicar = await api.post('/api/v1/subastas', {
          headers: conToken(jugadora.token, {
            'Idempotency-Key': `qa-fb-publicar-${elementoComprado.id}`,
          }),
          data: {
            elementoInventarioId: elementoComprado.id,
            productoId: producto.id,
            duracion: '24H',
            precioInicial: PRECIO_INICIAL_DE_LA_SUBASTA,
          },
        });
        expect(publicar.status(), await publicar.text()).toBe(201);
        const subasta = await publicar.json();
        expect(Number(subasta.incrementoMinimo)).toBe(INCREMENTO_MINIMO);

        const pujar = (quien, monto) =>
          api.post(`/api/v1/subastas/${subasta.id}/pujas`, {
            headers: conToken(quien.token, {
              'Idempotency-Key': `qa-fb-${quien.claims.uid}-${monto}-${Date.now()}`,
            }),
            data: { monto: String(monto) },
          });
        const base = PRECIO_INICIAL_DE_LA_SUBASTA;
        const primera = await pujar(sesiones.postor1, base);
        expect(primera.status(), await primera.text()).toBe(201);
        const corta = await pujar(sesiones.postor2, base + INCREMENTO_MINIMO - 1);
        expect(corta.status(), await corta.text()).toBe(409);
        expect((await corta.json()).motivo).toBe('OFERTA_INSUFICIENTE');
        const justa = await pujar(sesiones.postor2, base + INCREMENTO_MINIMO);
        expect(justa.status(), await justa.text()).toBe(201);

        const ficha = await (await api.get(`/api/v1/subastas/${subasta.id}`)).json();
        expect(Number(ficha.ofertaVigente)).toBe(base + INCREMENTO_MINIMO);
        expect(Number(ficha.pujaMinimaSiguiente)).toBe(base + 2 * INCREMENTO_MINIMO);
        return (
          `reglas: incremento ${reglas.incrementoMinimo}; pantalla «Incremento mínimo: 5 créditos»; ` +
          `${base} ✓ → ${base + INCREMENTO_MINIMO - 1} ✗ OFERTA_INSUFICIENTE → ` +
          `${base + INCREMENTO_MINIMO} ✓; siguiente mínima ${ficha.pujaMinimaSiguiente}`
        );
      });

      await paso(5, 'Batalla contra la IA: nadie se hace daño a sí mismo (PR-A)', async () => {
        const yo = jugadora.claims.uid;
        const creada = await api.post('/api/v1/salas', {
          headers: conToken(jugadora.token),
          data: {
            modalidad: 'CONTRA_IA',
            maximoParticipantes: 2,
            recompensaCreditos: 0,
            heroesIA: 1,
          },
        });
        expect(creada.status(), await creada.text()).toBe(201);
        const sala = await creada.json();
        const inicio = await api.post(`/api/v1/salas/${sala.id}/partida`, {
          headers: conToken(jugadora.token),
        });
        expect(inicio.status(), await inicio.text()).toBe(201);
        const partida = await inicio.json();

        // Lo que llega por el canal, tal cual lo recibe el navegador.
        const avisos = [];
        page.on('websocket', (ws) => {
          ws.on('framereceived', (marco) => {
            for (const cuerpo of cuerposStomp(marco.payload)) {
              if (cuerpo?.tipo === 'partida.accion.resuelta' && cuerpo.idPartida === partida.id) {
                avisos.push(cuerpo);
              }
            }
          });
        });
        await page.goto(`${RUTA.sala}?sala=${sala.id}&partida=${partida.id}`);

        const resultado = page.locator('[data-zona="resultado"]');
        const ataque = page.locator('[data-zona="acciones"] [data-atacar]').first();
        const entrar = page.locator('[data-accion="entrar-al-combate"]');
        const limite = Date.now() + 6 * 60_000;
        let golpes = 0;
        while (Date.now() < limite && !(await resultado.isVisible())) {
          if (await entrar.isVisible()) {
            await entrar.click({ timeout: 5_000 }).catch(() => {});
          } else if ((await ataque.isVisible()) && (await ataque.isEnabled())) {
            const golpeo = await ataque
              .click({ timeout: 5_000 })
              .then(() => true)
              .catch(() => false);
            golpes += golpeo ? 1 : 0;
          } else {
            await page.waitForTimeout(500);
          }
        }
        await expect(resultado).toContainText(/has ganado|has perdido|empate/i, {
          timeout: 60_000,
        });
        await capturar(page, testInfo, 'fb-05-batalla');

        const acciones = avisos.filter((a) => a.accion?.codigo !== 'EFECTO_POR_TURNO');
        expect(acciones.length, 'el canal contó el combate').toBeGreaterThan(0);
        const ilegales = [];
        for (const aviso of acciones) {
          const quien = aviso.idEjecutor === yo ? 'la jugadora' : 'la IA';
          const propio = (aviso.afectados ?? []).find((x) => x.idJugador === aviso.idEjecutor);
          const reflejo = (propio?.causas ?? []).some((c) => c.tipo === 'REFLEJO');
          if (propio && propio.diferencia < 0 && !reflejo) {
            ilegales.push(`${quien} se quitó ${-propio.diferencia} con ${aviso.accion.codigo}`);
          }
          if (aviso.idObjetivo === aviso.idEjecutor && propio && propio.diferencia < 0) {
            ilegales.push(`${quien} se apuntó a sí misma con ${aviso.accion.codigo}`);
          }
          // El daño que recibe cada uno lo causa otro.
          for (const afectado of aviso.afectados ?? []) {
            const propias = (afectado.causas ?? []).filter(
              (c) => ['DANO', 'REFLEJO'].includes(c.tipo) && c.origen === afectado.idJugador,
            );
            if (afectado.diferencia < 0 && propias.length > 0) {
              ilegales.push(`daño de ${afectado.idJugador} atribuido a sí mismo`);
            }
          }
        }
        expect(ilegales, 'ni la jugadora ni la IA se hacen daño a sí mismas').toEqual([]);

        const registro = page.locator('[data-zona="registro"]');
        await expect(registro).toContainText('(IA)');
        await expect(registro).not.toContainText(/\(tú\) golpea a [^.]*\(tú\)/);
        const desenlace = ((await resultado.textContent()) ?? '').replace(/\s+/g, ' ').trim();
        const deLaIa = acciones.filter((a) => a.idEjecutor !== yo).length;
        return (
          `${golpes} golpes; ${acciones.length - deLaIa} acciones propias y ${deLaIa} de la IA, ` +
          `ninguna con auto-daño; «(IA)» en el registro; ${desenlace.slice(0, 80)}`
        );
      });

      await paso(6, 'Misión de nivel 1 con el héroe nuevo y su progreso (D-42)', async () => {
        const heroe = await heroeDe(api, jugadora);
        expect(heroe.nivel, 'el héroe del alta empieza en el nivel 1').toBe(1);
        expect(heroe.disponible).toBe(true);

        // Ninguna misión recomienda más del nivel 8, y la de nivel 1 está a mano.
        const todas = [];
        for (const categoria of CATEGORIAS) {
          todas.push(...(await tablonDe(api, jugadora, categoria)));
        }
        const niveles = todas.map((m) => m.nivelRecomendado).filter(Number.isFinite);
        expect(niveles.length).toBeGreaterThan(0);
        expect(Math.max(...niveles), 'nada por encima del nivel 8').toBeLessThanOrEqual(
          NIVEL_MAXIMO,
        );
        expect(Math.min(...niveles)).toBeGreaterThanOrEqual(1);
        const primera = todas.find((m) => m.id === PRIMERA_MISION);
        expect(primera, 'la primera misión de la progresión').toMatchObject({
          nivelRecomendado: 1,
          estado: 'DISPONIBLE',
        });
        expect(todas.find((m) => m.id === SEGUNDA_MISION)?.estado).toBe('BLOQUEADA');

        // Desde la vista, como la empezaría una persona.
        await page.goto(RUTA.misiones);
        const tarjeta = page.locator(`.mision-card[data-mision="${PRIMERA_MISION}"]`);
        await expect(tarjeta).toBeVisible({ timeout: 30_000 });
        const enPantalla = await page
          .locator('.mision-card__datos > div')
          .filter({ has: page.locator('dt', { hasText: 'Nivel recomendado' }) })
          .locator('dd')
          .allInnerTexts();
        expect(
          enPantalla.map(Number).every((n) => n >= 1 && n <= NIVEL_MAXIMO),
          `niveles en pantalla: ${enPantalla.join(', ')}`,
        ).toBe(true);
        await tarjeta.locator('[data-accion="iniciar"]').click();
        const pasoDeLaEstrategia = page.locator('.estrategia__paso select').first();
        await expect(pasoDeLaEstrategia).toBeVisible({ timeout: 30_000 });
        const habilidad = await pasoDeLaEstrategia
          .locator('option:not([value=""])')
          .first()
          .getAttribute('value');
        expect(habilidad).toBeTruthy();
        await pasoDeLaEstrategia.selectOption(habilidad);
        await page.locator('[data-accion="comprobar-estrategia"]').click();
        await expect(page.locator('.estrategia__veredicto .aviso--exito')).toBeVisible({
          timeout: 30_000,
        });
        // La vista navega en cuanto la matrícula contesta: se lee al pasar.
        let matricula = null;
        const ruta = `**/api/v1/misiones/${PRIMERA_MISION}/ejecuciones`;
        await page.route(ruta, async (r) => {
          const respuesta = await r.fetch();
          matricula = {
            status: respuesta.status(),
            cuerpo: await respuesta.json().catch(() => null),
          };
          await r.fulfill({ response: respuesta });
        });
        await page.locator('[data-accion="iniciar-mision"]').click();
        await page.locator('[role="dialog"] [data-accion="confirmar"]').click();
        await expect.poll(() => matricula?.status, { timeout: 30_000 }).toBeTruthy();
        await page.unroute(ruta);
        expect(matricula.status, JSON.stringify(matricula.cuerpo)).toBe(201);
        const ejecucionId = matricula.cuerpo.ejecucionId;
        const salida = Date.now();

        const reporte = await reporteTerminado(api, jugadora, ejecucionId, 8 * 60_000);
        expect(['EXITO', 'FALLO']).toContain(reporte.resultado);
        expect(reporte).toMatchObject({ mision: { id: PRIMERA_MISION }, heroe: { nivel: 1 } });
        // Que se peleó se ve en los turnos y en el daño. `encuentros` son los
        // rivales DERROTADOS: un héroe de nivel 1 que cae en el primer combate
        // deja 0 (FALLO) y eso también es pelear (D-29: el kit de DEV pierde
        // a menudo en su nivel). El 4-oct falló así, con 0 y FALLO.
        expect(reporte.combate.turnos, 'se peleó con el motor real: hubo turnos').toBeGreaterThan(
          0,
        );
        expect(
          reporte.combate.danoInfligido + reporte.combate.danoRecibido,
          'y hubo golpes',
        ).toBeGreaterThan(0);
        if (reporte.resultado === 'EXITO') {
          expect(reporte.combate.encuentros, 'ganar es derrotar a alguien').toBeGreaterThan(0);
        }

        // La progresión persiste fuera de misiones: experiencia en el inventario.
        let despues = heroe;
        await expect
          .poll(
            async () => {
              despues = await heroeDe(api, jugadora);
              return despues.disponible === true && !despues.ejecucionMisionId;
            },
            { timeout: 60_000, message: 'el inventario no liberó al héroe' },
          )
          .toBe(true);
        const experiencia = Number(reporte.recompensas.experiencia ?? 0);
        expect(Number(despues.experiencia)).toBeCloseTo(
          Number(heroe.experiencia ?? 0) + experiencia,
          6,
        );
        expect(despues.nivel).toBe(reporte.heroe.nivelAlcanzado ?? heroe.nivel);

        // Sin callejón sin salida.
        const historia = await tablonDe(api, jugadora, 'HISTORIA');
        let siguiente;
        if (reporte.resultado === 'EXITO') {
          expect(historia.find((m) => m.id === PRIMERA_MISION)?.estado).toBe('COMPLETADA');
          expect(
            historia.find((m) => m.id === SEGUNDA_MISION)?.estado,
            'desbloquea la siguiente',
          ).toBe('DISPONIBLE');
          siguiente = `desbloquea «${historia.find((m) => m.id === SEGUNDA_MISION)?.nombre}»`;
        } else {
          expect(historia.find((m) => m.id === PRIMERA_MISION)?.estado).toBe('FALLIDA');
          // Se puede volver a intentar: la matrícula entra y se cancela enseguida
          // para dejar al héroe libre.
          const otra = await api.post(`/api/v1/misiones/${PRIMERA_MISION}/ejecuciones`, {
            headers: conToken(jugadora.token, {
              'Idempotency-Key': `qa-fb-reintento-${ejecucionId}`,
            }),
            data: { heroeId: heroe.id, rotaciones: [{ pasos: [habilidad] }] },
          });
          expect(otra.status(), await otra.text()).toBe(201);
          const cancelada = await api.post(
            `/api/v1/misiones/ejecuciones/${(await otra.json()).ejecucionId}/cancelacion`,
            { headers: conToken(jugadora.token) },
          );
          expect(cancelada.status(), await cancelada.text()).toBe(200);
          siguiente = 'se puede volver a intentar (matrícula 201, cancelada)';
        }

        await page.goto(`${RUTA.misiones}?reporte=${ejecucionId}`);
        await expect(page.locator('.mision-reporte h1')).toHaveText(reporte.mision.nombre, {
          timeout: 20_000,
        });
        await capturar(page, testInfo, 'fb-06-reporte-de-mision');
        const minutos = ((Date.now() - salida) / 60_000).toFixed(1);
        return (
          `«${reporte.mision.nombre}» (nivel 1) con «${reporte.heroe.nombre}» ` +
          `nivel 1: ${reporte.resultado} en ${reporte.combate.encuentros} encuentros ` +
          `(${minutos} min); +${Math.round(experiencia)} de experiencia en el inventario ` +
          `(nivel ${despues.nivel}); ${siguiente}`
        );
      });

      // Ningún error de página ni 5xx en todo el recorrido (salvo los servicios
      // que el catálogo declara fuera de DEV, que la vista ya dice que faltan).
      expect(bitacora.incidencias, 'errores de página o 5xx durante el recorrido').toEqual([]);
    } finally {
      await bitacora.cerrar(testInfo);
      await api.dispose();
    }
  });
});
