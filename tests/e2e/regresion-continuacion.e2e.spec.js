// @ts-check
/**
 * G9 (continuación técnica del 4-oct, FASE 12) — los quince puntos de
 * regresión, de punta a punta por el borde del banco, con cuentas nuevas y
 * sin tocar ninguna base de datos (nada de SQL: todo lo siembran las APIs,
 * como lo haría el producto).
 *
 *    1. portada anónima                 9. la experiencia persiste (y el aviso llega)
 *    2. producto en dinero real        10. combate contra la IA
 *    3. producto solo en créditos      11. cero auto-daño
 *    4. login seguro                   12. subasta: incremento de 5
 *    5. registro                       13. puja concurrente: entra una
 *    6. comprar con créditos           14. producto público sin datos internos
 *    7. el inventario lo recibe            (y la ficha de subasta sin el uid)
 *    8. misión de nivel 1              15. comentario público sin uid
 *
 * Cada punto tiene además su prueba detallada en otro fichero (portada,
 * tienda, compra-con-creditos, login-sin-envio-nativo, alta-del-jugador,
 * misiones, combate-sin-autodano, subastas, comentarios); aquí van juntos y en
 * orden, para que una sola corrida diga si la continuación sigue en pie.
 */

import { randomBytes } from 'node:crypto';

import { test, expect, request as apiRequest } from '@playwright/test';

import { sesionDe as sesionDelBanco } from './ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const FINANZAS = process.env.E2E_FINANZAS ?? 'http://localhost:8093/api/v1';
const ADMIN = process.env.E2E_ADMIN ?? 'admin_e2e';
const CLAVE = 'Contrasena-E2E-2026';
const IMAGEN = '/frontend/app-web/src/cuentas/avatares/arquero-cazador.jpg';
const SALA = '/frontend/app-web/src/plataforma/salas-partidas/sala-batalla.html';
const BANCO = { id: 'e2e-banco', secreto: 'e2e-secreto-del-banco-de-pruebas' };
const PRODUCTO_SUBASTABLE = 'dddddddd-0000-0000-0000-00000000000a';
const MISION_DE_NIVEL_1 = 'sendero-de-los-aprendices';
const SUFIJO = Date.now().toString(36);

/** Los campos que la vitrina pública puede enseñar (ProductoDeVitrina, ecommerce-carrito.yaml). */
const CAMPOS_PUBLICOS = new Set([
  'id',
  'nombre',
  'imagenUrl',
  'descripcion',
  'habilidades',
  'tipo',
  'precioFinal',
  'precioOriginal',
  'moneda',
  'enPromocion',
  'porcentajeDescuento',
  'esPropio',
  'enListaDeseos',
  'precioCreditos',
]);

function sesionDe(api, apodo, opciones = {}) {
  return sesionDelBanco(api, apodo, { clave: CLAVE, base: BORDE, ...opciones });
}

function conToken(token) {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

async function tokenDeServicio(api) {
  const r = await api.post('/api/v1/auth/token', {
    headers: {
      Authorization: `Basic ${Buffer.from(`${BANCO.id}:${BANCO.secreto}`).toString('base64')}`,
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    data: 'grant_type=client_credentials',
  });
  expect(r.status(), `token de servicio: ${await r.text()}`).toBe(200);
  return (await r.json()).access_token;
}

async function acreditar(api, servicio, quien, monto, refId) {
  const r = await api.post(`${FINANZAS}/creditos/acreditar`, {
    headers: conToken(servicio),
    data: { uid: quien.claims.uid, monto, refId, concepto: 'semilla-regresion-continuacion-e2e' },
  });
  expect(r.status(), `acreditar a ${quien.claims.uid}: ${await r.text()}`).toBe(200);
}

async function saldoDe(api, quien) {
  const r = await api.get(`/api/v1/creditos/${quien.claims.uid}/saldo`, {
    headers: conToken(quien.token),
  });
  expect(r.status(), await r.text()).toBe(200);
  return Number((await r.json()).saldoDisponible);
}

async function vitrinaCompleta(api) {
  const productos = [];
  for (let pagina = 0; pagina < 20; pagina++) {
    const r = await api.get(`/api/v1/vitrina?page=${pagina}&size=50`);
    expect(r.status(), await r.text()).toBe(200);
    const cuerpo = await r.json();
    productos.push(...cuerpo.content);
    if (cuerpo.last) {
      break;
    }
  }
  return productos;
}

async function elementosDe(api, quien) {
  const r = await api.get('/api/v1/inventario/elementos?pagina=0', {
    headers: conToken(quien.token),
  });
  expect(r.status(), await r.text()).toBe(200);
  return (await r.json()).elementos ?? [];
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

test.describe('G9 — los quince puntos de la continuación técnica', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let servicio;
  let jugadora;
  const ids = {};
  const PRECIO_EN_CREDITOS = { mixto: 120, soloCreditos: 70 };
  /** Lo que avisa el combate, tal cual llega al navegador. */
  const avisosDelCombate = [];

  const producto = (nombre, extra) => ({
    nombre: `${nombre} regresión G9 ${SUFIJO}`,
    imagen: IMAGEN,
    descripcion: 'Producto de prueba de la regresión G9.',
    tipo: 'ARMA',
    tiraje: -1,
    premium: false,
    poderDeAtaque: 10,
    tasaDeCaida: 50,
    ...extra,
  });

  test.beforeAll(async () => {
    test.setTimeout(240_000);
    api = await apiRequest.newContext({ baseURL: BORDE });
    servicio = await tokenDeServicio(api);
    const admin = await sesionDe(api, ADMIN);
    const altas = {
      mixto: producto('Hacha mixta', {
        precioMonedaReal: 9000,
        precioCreditos: PRECIO_EN_CREDITOS.mixto,
      }),
      // G3: sin precio en dinero real (JSON no escribe un campo undefined).
      soloCreditos: producto('Amuleto solo en créditos', {
        precioCreditos: PRECIO_EN_CREDITOS.soloCreditos,
      }),
    };
    for (const [clave, datos] of Object.entries(altas)) {
      const r = await api.post('/api/v1/productos', {
        headers: conToken(admin.token),
        data: datos,
      });
      expect(r.status(), await r.text()).toBe(201);
      ids[clave] = (await r.json()).id;
    }
    await expect
      .poll(
        async () => {
          const vitrina = await vitrinaCompleta(api);
          return Object.values(ids).every((id) => vitrina.some((p) => p.id === id));
        },
        { timeout: 45_000, intervals: [1_000, 2_000, 5_000] },
      )
      .toBe(true);
  });

  test.afterAll(async () => {
    await api?.dispose();
  });

  test('1 · portada anónima: `/` es la tienda pública, sin redirección ni sesión', async () => {
    const raiz = await api.get('/', { maxRedirects: 0 });
    expect(raiz.status()).toBe(200);
    expect(await raiz.text()).toContain('data-vista="portada"');
    // Y comprar pide entrar.
    const carrito = await api.post('/api/v1/carrito/items', {
      data: { productoId: ids.mixto, cantidad: 1 },
    });
    expect(carrito.status()).toBe(401);
  });

  test('2 · producto en dinero real: precio final en su moneda, y también en créditos', async () => {
    const mixto = (await vitrinaCompleta(api)).find((p) => p.id === ids.mixto);
    expect(Number(mixto.precioFinal)).toBe(9000);
    expect(mixto.moneda).toBeTruthy();
    expect(mixto.precioCreditos).toBe(PRECIO_EN_CREDITOS.mixto);
  });

  test('3 · producto solo en créditos: se vende sin precio en dinero real (G3)', async () => {
    const amuleto = (await vitrinaCompleta(api)).find((p) => p.id === ids.soloCreditos);
    expect(amuleto.precioFinal ?? null).toBeNull();
    expect(amuleto.moneda ?? null).toBeNull();
    expect(amuleto.precioCreditos).toBe(PRECIO_EN_CREDITOS.soloCreditos);
  });

  test('5 · registro: la cuenta nueva verifica su correo y nace con su héroe equipado', async () => {
    test.setTimeout(180_000);
    jugadora = await sesionDe(api, `g9_${SUFIJO}`);
    await expect
      .poll(
        async () => {
          const r = await api.get('/api/v1/auth/onboarding', { headers: conToken(jugadora.token) });
          return r.ok() ? (await r.json()).estado : `HTTP ${r.status()}`;
        },
        { timeout: 60_000, message: 'el alta no terminó' },
      )
      .toBe('COMPLETO');
    const heroe = (await elementosDe(api, jugadora)).find((e) => e.tipo === 'HEROE');
    expect(heroe, 'el alta deja un héroe').toBeTruthy();
    expect(heroe.nivel ?? 1).toBe(1);
  });

  test('4 · login seguro: la contraseña nunca va en una dirección (G1)', async ({ browser }) => {
    const directa = await api.get(`/login?email=nadie%40nexus.test&password=G9-${SUFIJO}`, {
      maxRedirects: 0,
    });
    expect(directa.status()).toBe(303);
    expect(directa.headers().location).toBe('/login');

    const clave = `Clave-G9-${randomBytes(4).toString('hex')}-Aa1!`;
    const cuenta = await sesionDe(api, `g9login_${SUFIJO}`, { clave });
    const contexto = await browser.newContext({ baseURL: BORDE });
    const vistas = [];
    contexto.on('request', (p) =>
      vistas.push({ url: p.url(), referer: p.headers().referer ?? '' }),
    );
    const page = await contexto.newPage();
    try {
      await page.goto('/login');
      await expect(page.locator('#formLogin[data-listo]')).toBeAttached();
      await page.fill('#email', cuenta.email);
      await page.fill('#password', clave);
      await page.locator('#password').press('Enter');
      await page.waitForURL((url) => url.pathname !== '/login', { timeout: 30_000 });
    } finally {
      await contexto.close();
    }
    for (const { url, referer } of vistas) {
      for (const texto of [url, referer]) {
        expect(texto).not.toMatch(/[?&][^=&#]*password[^=&#]*=/i);
        expect(texto.includes(clave)).toBe(false);
      }
    }
  });

  test('6 · comprar con créditos: el saldo baja una vez y exactamente el precio', async () => {
    await acreditar(api, servicio, jugadora, 500, `g9-semilla-${SUFIJO}`);
    const antes = await saldoDe(api, jugadora);
    const alCarrito = await api.post('/api/v1/carrito/items', {
      headers: conToken(jugadora.token),
      data: { productoId: ids.soloCreditos, cantidad: 1 },
    });
    expect(alCarrito.status(), await alCarrito.text()).toBe(200);

    const clave = `g9-compra-${SUFIJO}`;
    const pagar = () =>
      api.post('/api/v1/checkout/creditos', {
        headers: { Authorization: `Bearer ${jugadora.token}`, 'Idempotency-Key': clave },
      });
    const pago = await pagar();
    expect(pago.status(), await pago.text()).toBe(201);
    const orden = await pago.json();
    expect(orden).toMatchObject({ estado: 'COMPLETA', formaDePago: 'CREDITOS' });
    const otraVez = await pagar();
    expect(otraVez.status()).toBe(200);
    expect((await otraVez.json()).id).toBe(orden.id);
    expect(await saldoDe(api, jugadora)).toBe(antes - PRECIO_EN_CREDITOS.soloCreditos);
  });

  test('7 · el inventario recibe lo comprado, una sola vez', async () => {
    const comprados = (await elementosDe(api, jugadora)).filter(
      (e) => e.productoId === ids.soloCreditos,
    );
    expect(comprados).toHaveLength(1);
  });

  let ejecucionId;
  let experienciaAntes;

  test('8 · misión de nivel 1: el héroe nuevo sale y vuelve con su reporte', async () => {
    test.setTimeout(120_000);
    const heroe = (await elementosDe(api, jugadora)).find((e) => e.tipo === 'HEROE');
    experienciaAntes = Number(heroe.experiencia ?? 0);
    const tablon = await api.get('/api/v1/misiones?categoria=HISTORIA', {
      headers: conToken(jugadora.token),
    });
    expect(tablon.status(), await tablon.text()).toBe(200);
    const primera = (await tablon.json()).misiones.find((m) => m.id === MISION_DE_NIVEL_1);
    expect(primera).toMatchObject({ nivelRecomendado: 1, estado: 'DISPONIBLE' });

    const matricula = await api.post(`/api/v1/misiones/${MISION_DE_NIVEL_1}/ejecuciones`, {
      headers: { ...conToken(jugadora.token), 'Idempotency-Key': `g9-mision-${SUFIJO}` },
      data: { heroeId: heroe.id },
    });
    expect(matricula.status(), await matricula.text()).toBe(201);
    ejecucionId = (await matricula.json()).ejecucionId;

    let reporte;
    await expect
      .poll(
        async () => {
          const r = await api.get(`/api/v1/misiones/ejecuciones/${ejecucionId}`, {
            headers: conToken(jugadora.token),
          });
          if (r.status() !== 200) {
            return `HTTP ${r.status()}`;
          }
          reporte = await r.json();
          return reporte.recompensas?.entregaPendiente ? 'entrega pendiente' : 'terminada';
        },
        { timeout: 60_000, intervals: [1_000, 1_000, 2_000] },
      )
      .toBe('terminada');
    expect(['EXITO', 'FALLO']).toContain(reporte.resultado);
    expect(reporte.combate.turnos).toBeGreaterThan(0);
  });

  test('9 · la experiencia persiste en el inventario y el aviso llega a la bandeja (RF-NOT-004)', async () => {
    const r = await api.get(`/api/v1/misiones/ejecuciones/${ejecucionId}`, {
      headers: conToken(jugadora.token),
    });
    const reporte = await r.json();
    let heroe;
    await expect
      .poll(
        async () => {
          heroe = (await elementosDe(api, jugadora)).find((e) => e.tipo === 'HEROE');
          return heroe.disponible === true && !heroe.ejecucionMisionId;
        },
        { timeout: 30_000, message: 'el inventario no liberó al héroe' },
      )
      .toBe(true);
    const ganada = Number(reporte.recompensas.experiencia ?? 0);
    if (heroe.nivel === 1) {
      expect(Number(heroe.experiencia)).toBeCloseTo(experienciaAntes + ganada, 6);
    } else {
      expect(heroe.nivel, 'subió de nivel con lo ganado').toBeGreaterThan(1);
    }

    await expect
      .poll(
        async () => {
          const bandeja = await api.get(`/api/v1/users/${jugadora.claims.uid}/notifications`, {
            headers: conToken(jugadora.token),
          });
          if (bandeja.status() !== 200) {
            return `HTTP ${bandeja.status()}`;
          }
          const aviso = ((await bandeja.json()).avisos ?? []).find(
            (a) => a.id === `mision-${ejecucionId}-aviso`,
          );
          return aviso ? aviso.tipo : 'todavía no';
        },
        { timeout: 30_000, message: 'el aviso de la misión no llegó a la bandeja' },
      )
      .toBe('MISION');
  });

  test('10 · combate contra la IA: se juega por turnos con el motor real', async ({ page }) => {
    test.setTimeout(240_000);
    const yo = jugadora.claims.uid;
    const creada = await api.post('/api/v1/salas', {
      headers: conToken(jugadora.token),
      data: { modalidad: 'CONTRA_IA', maximoParticipantes: 2, recompensaCreditos: 0, heroesIA: 1 },
    });
    expect(creada.status(), await creada.text()).toBe(201);
    const sala = await creada.json();
    const inicio = await api.post(`/api/v1/salas/${sala.id}/partida`, {
      headers: conToken(jugadora.token),
    });
    expect(inicio.status(), await inicio.text()).toBe(201);
    let partida = await inicio.json();

    page.on('websocket', (ws) => {
      ws.on('framereceived', (marco) => {
        for (const cuerpo of cuerposStomp(marco.payload)) {
          if (cuerpo?.tipo === 'partida.accion.resuelta' && cuerpo.idPartida === partida.id) {
            avisosDelCombate.push(cuerpo);
          }
        }
      });
    });
    await page.addInitScript(
      ([token, apodo, uid]) => {
        sessionStorage.setItem('nexus.token', token);
        sessionStorage.setItem('nexus.apodoActual', apodo);
        sessionStorage.setItem('nexus.rolActual', 'JUGADOR');
        sessionStorage.setItem('nexus.usuarioId', uid);
      },
      [jugadora.token, jugadora.apodo, yo],
    );
    await page.goto(`${BORDE}${SALA}?sala=${sala.id}&partida=${partida.id}`);

    const leer = async () => {
      const r = await api.get(`/api/v1/partidas/${partida.id}`, {
        headers: conToken(jugadora.token),
      });
      expect(r.status(), await r.text()).toBe(200);
      return r.json();
    };
    let golpes = 0;
    while (partida.estado === 'EN_CURSO' && golpes < 6) {
      await expect
        .poll(
          async () => {
            partida = await leer();
            return partida.estado !== 'EN_CURSO' || partida.turnoActual.idJugador === yo;
          },
          { timeout: 25_000 },
        )
        .toBe(true);
      if (partida.estado !== 'EN_CURSO') {
        break;
      }
      const boton = page.locator('[data-zona="acciones"] [data-atacar]').first();
      await expect(boton).toBeEnabled({ timeout: 20_000 });
      const previo = partida.turnoActual.numeroTurno;
      await boton.click({ timeout: 5_000 });
      await expect
        .poll(
          async () => {
            partida = await leer();
            return partida.estado !== 'EN_CURSO' || partida.turnoActual.numeroTurno > previo;
          },
          { timeout: 25_000 },
        )
        .toBe(true);
      golpes += 1;
    }
    expect(golpes, 'se jugó al menos un golpe').toBeGreaterThan(0);
    await expect
      .poll(() => avisosDelCombate.filter((a) => a.idEjecutor === yo).length)
      .toBeGreaterThan(0);
    await expect(page.locator('[data-zona="registro"]')).toContainText('(IA)', { timeout: 10_000 });
  });

  test('11 · cero auto-daño: ningún golpe propio apunta ni quita vida a quien lo lanza', async () => {
    const yo = jugadora.claims.uid;
    const propios = avisosDelCombate.filter(
      (a) => a.idEjecutor === yo && a.accion?.codigo !== 'EFECTO_POR_TURNO',
    );
    expect(propios.length).toBeGreaterThan(0);
    for (const aviso of propios) {
      expect(aviso.idObjetivo).not.toBe(yo);
      const mio = (aviso.afectados ?? []).find((x) => x.idJugador === yo);
      const reflejo = (mio?.causas ?? []).some((c) => c.tipo === 'REFLEJO');
      expect(Boolean(mio && mio.diferencia < 0 && !reflejo), 'auto-daño').toBe(false);
    }
  });

  let subastaId;
  let vendedora;
  let postores;
  const pujar = (quien, monto) =>
    api.post(`/api/v1/subastas/${subastaId}/pujas`, {
      headers: {
        ...conToken(quien.token),
        'Idempotency-Key': `g9-${quien.claims.uid}-${monto}-${Date.now()}`,
      },
      data: { monto: String(monto) },
    });

  test('12 · subasta: con 100 vigente, 104 se rechaza y 105 entra (D-43)', async () => {
    test.setTimeout(180_000);
    vendedora = await sesionDe(api, `g9vende_${SUFIJO}`);
    postores = [];
    // Uno por puja, como en D-43: el intervalo de 5 s es por jugador y subasta.
    for (let i = 1; i <= 5; i += 1) {
      const postor = await sesionDe(api, `g9puja${i}_${SUFIJO}`);
      await acreditar(api, servicio, postor, 500, `g9-semilla-puja${i}-${SUFIJO}`);
      postores.push(postor);
    }
    await acreditar(api, servicio, vendedora, 50, `g9-semilla-vende-${SUFIJO}`);
    const clave = `g9-entrega-${SUFIJO}`;
    const entrega = await api.post('/api/v1/inventario/entregas', {
      headers: { ...conToken(servicio), 'Idempotency-Key': clave },
      data: {
        uid: vendedora.claims.uid,
        origen: 'ADMINISTRACION',
        referencia: clave,
        productos: [{ productoId: PRODUCTO_SUBASTABLE, cantidad: 1 }],
      },
    });
    expect(entrega.status(), await entrega.text()).toBe(201);
    const publicar = await api.post('/api/v1/subastas', {
      headers: { ...conToken(vendedora.token), 'Idempotency-Key': `g9-publicar-${SUFIJO}` },
      data: {
        elementoInventarioId: (await entrega.json()).elementos[0].id,
        productoId: PRODUCTO_SUBASTABLE,
        duracion: '24H',
        precioInicial: 100,
        precioCompraInmediata: 1000,
      },
    });
    expect(publicar.status(), await publicar.text()).toBe(201);
    subastaId = (await publicar.json()).id;

    // El incremento que manda es el de admin-parametros (D-43): 5.
    await expect
      .poll(
        async () => {
          const reglas = await (await api.get('/api/v1/subastas/reglas')).json();
          return reglas.incrementoMinimoConfigurado ? Number(reglas.incrementoMinimo) : null;
        },
        { timeout: 15_000 },
      )
      .toBe(5);
    expect((await pujar(postores[0], 100)).status()).toBe(201);
    const corta = await pujar(postores[1], 104);
    expect(corta.status()).toBe(409);
    expect((await corta.json()).motivo).toBe('OFERTA_INSUFICIENTE');
    expect((await pujar(postores[2], 105)).status()).toBe(201);
  });

  test('13 · puja concurrente: dos a la vez con el mismo monto, entra una sola', async () => {
    const [una, otra] = await Promise.all([pujar(postores[3], 110), pujar(postores[4], 110)]);
    expect([una.status(), otra.status()].sort()).toEqual([201, 409]);
    const ficha = await (await api.get(`/api/v1/subastas/${subastaId}`)).json();
    expect(Number(ficha.ofertaVigente)).toBe(110);
  });

  test('14 · lo público no lleva datos internos: vitrina sin administración, ficha de subasta sin el uid', async () => {
    const nuestros = (await vitrinaCompleta(api)).filter((p) => Object.values(ids).includes(p.id));
    expect(nuestros).toHaveLength(2);
    for (const p of nuestros) {
      expect(Object.keys(p).filter((campo) => !CAMPOS_PUBLICOS.has(campo))).toEqual([]);
    }
    const ficha = await api.get(`/api/v1/subastas/${subastaId}`);
    const texto = await ficha.text();
    expect(texto.includes(vendedora.claims.uid), 'la ficha pública no publica el uid').toBe(false);
    expect(JSON.parse(texto)).toMatchObject({ vendedorId: null, esPropia: false });
  });

  test('15 · comentario público sin uid: el hilo dice de quién es por el servidor (`propio`)', async () => {
    const publicar = await api.post(`/api/v1/products/${ids.mixto}/comments`, {
      headers: conToken(jugadora.token),
      data: { texto: `Buen hacha para empezar (${SUFIJO})` },
    });
    expect([200, 201], await publicar.text()).toContain(publicar.status());

    const anonimo = await api.get(`/api/v1/products/${ids.mixto}/comments`);
    const texto = await anonimo.text();
    expect(anonimo.status()).toBe(200);
    expect(texto.includes(jugadora.claims.uid)).toBe(false);
    const hilo = JSON.parse(texto);
    expect(hilo.comentarios.length).toBeGreaterThan(0);
    for (const c of hilo.comentarios) {
      expect(c).not.toHaveProperty('autorId');
      expect(c.propio).toBe(false);
    }
    const suyo = await (
      await api.get(`/api/v1/products/${ids.mixto}/comments`, { headers: conToken(jugadora.token) })
    ).json();
    expect(suyo.comentarios.some((c) => c.propio === true)).toBe(true);
  });
});
