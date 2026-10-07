/**
 * PLAYER-08 · Los dos recorridos del jugador que cierran la revisión del modo
 * jugador del 6-oct, con cuentas NUEVAS y por la interfaz.
 *
 * Por el sufijo `.profesor.spec.js` corren donde corre la prueba del profesor:
 *
 *   - en el banco (`e2e.yml`, «La prueba del profesor en el banco»);
 *   - contra AWS DEV después de cada despliegue (`prueba-del-profesor.yml`).
 *     Es la evidencia en AWS de que los 27 puntos se sostienen juntos, no
 *     solo cada uno en su PR.
 *
 * Recorrido 1 — de la entrada a la subasta:
 *
 *   login → inicio → tienda → jugar → crear Solo → cuenta atrás → combate
 *   contra la IA (vida y poder del HUD = los del servidor; nadie se daña a sí
 *   mismo ni se apunta a sí mismo) → resultado → salas → misión → preparar
 *   misión → torneo → inventario → subasta → publicar con el nombre del
 *   catálogo.
 *
 * Recorrido 2 — entre dos jugadores:
 *
 *   sala privada → su código → invitar por apodo → el invitado entra desde el
 *   aviso → chat grupal en la misma página → combate → «Salir» = rendirse.
 *
 * Reglas que se impone, las mismas que la prueba del profesor:
 *
 *   - Cuentas desechables de QA (`qa_rj…@nexus.test`) con su alta real. La
 *     contraseña es aleatoria y no se imprime ni va a ningún título de paso
 *     (la configuración apaga la traza y el vídeo).
 *   - Lo que hace el jugador, por la interfaz. La API solo prepara las cuentas
 *     y comprueba lo que dice el servidor, que es quien decide vida, poder,
 *     ganador y rendición: la vista presenta.
 *   - Una línea `PROFESOR|…` por paso para el resumen de DEV, y ningún error
 *     de página ni 5xx en todo el recorrido.
 *
 * Solo en escritorio (1440): lo que se prueba no depende de la anchura.
 */

import { randomBytes } from 'node:crypto';

import { test, expect, request as apiRequest } from '@playwright/test';

import { sesionDe } from './ayudantes/cuentas.js';
import { servicioNoDesplegadoDe } from './ayudantes/no-desplegados.js';

const BASE = process.env.PROFESOR_URL ?? process.env.E2E_AWS ?? 'http://localhost:8099';
const ESCRITORIO = 'escritorio-1440';

/** Direcciones limpias del borde (R17.3) y las vistas que no tienen una. */
const EN = {
  login: /\/login(?:[?#]|$)/,
  registro: /\/registro(?:[?#]|$)/,
  recuperar: /\/recuperar(?:[?#]|$)|restablecer-solicitar\.html/,
  inicio: /\/inicio(?:[?#]|$)/,
  jugar: /\/jugar(?:[?#]|$)|batallas\.html/,
  crearSala: /crear-sala\.html/,
  sala: /sala-batalla\.html\?sala=/,
  misiones: /\/misiones(?:[?#]|$)|misiones\.html/,
  torneos: /\/torneos(?:[?#]|$)/,
  inventario: /\/inventario(?:[?#]|$)/,
  subastas: /\/subastas(?:[?#]|$)/,
  publicar: /publicar-subasta\.html/,
};

/** Un identificador técnico en un texto: lo que el jugador nunca debe leer. */
const UUID = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i;
/** La comisión más alta de publicar (48 h): lo que se deja sin gastar en la tienda. */
const COMISION_MAXIMA = 3;
const PRECIO_INICIAL_DE_LA_SUBASTA = 20;

// ------------------------------------------------------------- utilidades

/** Apodo, correo y contraseña desechables, reconocibles como de QA. */
function cuentaDesechable(rol) {
  const sufijo = `${Date.now().toString(36).slice(-6)}${randomBytes(2).toString('hex')}`;
  const apodo = `qa_rj${rol}_${sufijo}`.slice(0, 24);
  return {
    apodo,
    email: `${apodo}@nexus.test`,
    // RF-AUT-002: más de 8, mayúscula, minúscula, cifra y símbolo.
    clave: `Qa-${randomBytes(12).toString('base64url')}-7z`,
  };
}

/** Escribe un secreto sin que su valor quede en el informe (`fill` lo pondría). */
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

/**
 * El uid de un participante. `GET /partidas/{id}` lo da hoy como el uid a
 * secas (`PartidaResponse.ParticipanteResponse.jugador`), y el contrato y el
 * aviso de inicio del canal como `{id, apodo}` (`ResumenJugador`): se aceptan
 * las dos formas para no medir la deriva sino el combate.
 */
function idDelJugador(participante) {
  const { jugador } = participante ?? {};
  return typeof jugador === 'string' ? jugador : jugador?.id;
}

/** La partida tal como la sabe el servidor (salas-partidas.yaml, `Partida`). */
async function partidaDe(api, quien, idPartida) {
  const r = await api.get(`/api/v1/partidas/${idPartida}`, { headers: conToken(quien.token) });
  expect(r.status(), `partida: ${await r.text()}`).toBe(200);
  return r.json();
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
 * Lo que el canal de la partida cuenta al navegador, desde antes de entrar a
 * la sala: cada acción resuelta, tal cual llega.
 */
function escucharElCombate(page) {
  const avisos = [];
  page.on('websocket', (ws) => {
    ws.on('framereceived', (marco) => {
      for (const cuerpo of cuerposStomp(marco.payload)) {
        if (cuerpo?.tipo === 'partida.accion.resuelta') {
          avisos.push(cuerpo);
        }
      }
    });
  });
  return avisos;
}

/**
 * Acciones ilegales en lo que contó el canal: quien golpea no pierde vida por
 * su golpe (salvo un REFLEJO declarado), nadie se apunta a sí mismo para
 * dañarse y el daño que recibe cada uno lo causa otro.
 */
function accionesIlegales(avisos) {
  const ilegales = [];
  for (const aviso of avisos) {
    const codigo = aviso.accion?.codigo ?? 'sin código';
    const propio = (aviso.afectados ?? []).find((x) => x.idJugador === aviso.idEjecutor);
    const reflejo = (propio?.causas ?? []).some((c) => c.tipo === 'REFLEJO');
    if (propio && propio.diferencia < 0 && !reflejo) {
      ilegales.push(`${aviso.idEjecutor} se quitó ${-propio.diferencia} con ${codigo}`);
    }
    if (aviso.idObjetivo === aviso.idEjecutor && propio && propio.diferencia < 0) {
      ilegales.push(`${aviso.idEjecutor} se apuntó a sí mismo con ${codigo}`);
    }
    for (const afectado of aviso.afectados ?? []) {
      const propias = (afectado.causas ?? []).filter(
        (c) => ['DANO', 'REFLEJO'].includes(c.tipo) && c.origen === afectado.idJugador,
      );
      if (afectado.diferencia < 0 && propias.length > 0) {
        ilegales.push(`daño de ${afectado.idJugador} atribuido a sí mismo`);
      }
    }
  }
  return ilegales;
}

/** Va a una sección por la barra, como una persona (abre el menú si hace falta). */
async function irA(page, seccion) {
  const destino = page.locator(`header[data-cabecera-app] a[data-seccion="${seccion}"]`);
  if (!(await destino.isVisible())) {
    const alternar = page.locator('[data-zona="alternar-nav"]');
    if (await alternar.isVisible()) {
      await alternar.click();
    }
  }
  await destino.click();
}

/**
 * Entra por el formulario y espera a estar en `destino` (la ruta). Si lo que
 * frena es el límite del borde (30 entradas por minuto y dirección), se espera
 * y se vuelve a intentar, como lo haría una persona.
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

/** Del login al inicio; si la cuenta pasa por «Preparando tu cuenta», se sigue. */
async function entrarHastaElInicio(page, cuenta) {
  await page.goto('/login');
  const intentos = await entrarPorElFormulario(page, cuenta, /^\/(inicio|preparando)$/);
  if (!EN.inicio.test(page.url())) {
    const empezar = page.locator('[data-zona="empezar"]');
    await expect(async () => {
      if (!EN.inicio.test(page.url())) {
        await expect(empezar).toBeVisible({ timeout: 2_000 });
      }
    }).toPass({ timeout: 90_000 });
    if (!EN.inicio.test(page.url())) {
      await empezar.click();
    }
  }
  await page.waitForURL(EN.inicio, { timeout: 30_000 });
  return intentos;
}

/** Cómo se le dice a quien busca con menos de cuatro letras que le faltan. */
async function avisoDeCriterioCorto(page, campo) {
  const mensaje = page.locator('.inventario__mensaje');
  if ((await mensaje.isVisible()) && /cuatro caracteres/i.test(await mensaje.innerText())) {
    return 'mensaje de la vista';
  }
  const corto = await campo.evaluate((el) => el.validity.tooShort);
  return corto ? 'validación del campo' : 'ninguno';
}

/** Lo que se lee en pantalla (`innerText`: sin lo oculto ni los comentarios). */
async function textoVisible(page, selector) {
  return (await page.locator(selector).first().innerText()).replace(/\s+/g, ' ');
}

async function capturar(page, testInfo, nombre) {
  const ruta = testInfo.outputPath(`${nombre}.png`);
  await page.screenshot({ path: ruta });
  await testInfo.attach(nombre, { path: ruta, contentType: 'image/png' });
}

/**
 * Cada paso con su resultado, y lo que los navegadores dijeron mientras tanto:
 * el mismo formato que la prueba del profesor, para el resumen de DEV.
 */
function abrirBitacora(etiqueta, contextos) {
  const pasos = [];
  const incidencias = [];
  let pasoEnCurso = 0;

  for (const context of contextos) {
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
  }

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
    await testInfo.attach(`bitacora-${etiqueta.replace(/[^a-z0-9]+/gi, '-')}.json`, {
      body: JSON.stringify({ pasos, incidencias }, null, 2),
      contentType: 'application/json',
    });
    const limpio = (texto) => String(texto).replaceAll('|', '/').replaceAll('\n', ' ');
    for (const p of pasos) {
      console.log(
        `PROFESOR|${etiqueta}|${p.paso}|${limpio(p.titulo)}|${p.estado}|${limpio(p.detalle)}|${p.ms}`,
      );
    }
    for (const i of incidencias) {
      console.log(`PROFESOR-INCIDENCIA|${etiqueta}|${i.paso}|${i.tipo}|${i.detalle}`);
    }
  }

  return { paso, cerrar, incidencias };
}

/**
 * Vida y poder: lo que pinta el HUD contra lo que dice el servidor, cuando el
 * turno es de quien mira (nada se está moviendo). La vista puede tardar un
 * instante en pintar el último golpe: se da unos segundos para que coincidan,
 * nunca para que se parezcan.
 */
async function cotejarHud(page, { api, quien, idPartida }) {
  const partida = await partidaDe(api, quien, idPartida);
  if (partida.estado !== 'EN_CURSO' || partida.turnoActual?.idJugador !== quien.claims.uid) {
    return null;
  }
  const servidor = Object.fromEntries(
    (partida.participantes ?? []).map((p) => [idDelJugador(p), p.heroe?.vidaActual]),
  );
  await expect
    .poll(
      () =>
        page
          .locator('[data-barra-vida]')
          .evaluateAll((barras) =>
            Object.fromEntries(
              barras.map((b) => [b.dataset.jugador, Number(b.getAttribute('aria-valuenow'))]),
            ),
          ),
      { timeout: 10_000, message: 'la vida del HUD es la del servidor' },
    )
    .toEqual(servidor);
  const mio = (partida.participantes ?? []).find((p) => idDelJugador(p) === quien.claims.uid);
  const poder = mio?.heroe?.poderActual;
  if (Number.isInteger(poder)) {
    await expect(
      page.locator('[data-zona="poder"] [role="meter"]'),
      'el poder del HUD es el del servidor',
    ).toHaveAttribute('aria-valuenow', String(poder), { timeout: 10_000 });
  }
  return { vidas: Object.values(servidor), poder: Number.isInteger(poder) ? poder : null };
}

/**
 * Juega desde la vista hasta el desenlace, como en la prueba del profesor; en
 * el primer turno propio coteja el HUD con el servidor.
 */
async function jugarHastaElFinal(page, { api, quien, idPartida, limiteMs = 8 * 60_000 }) {
  const resultado = page.locator('[data-zona="resultado"]');
  const ataque = page.locator('[data-zona="acciones"] [data-atacar]').first();
  const entrar = page.locator('[data-accion="entrar-al-combate"]');
  const cuentaAtras = page.locator('[data-zona="cuenta-atras"]');
  const limite = Date.now() + limiteMs;
  let golpes = 0;
  let cotejo = null;
  while (Date.now() < limite && !(await resultado.isVisible())) {
    if (await cuentaAtras.isVisible()) {
      await page.waitForTimeout(400);
    } else if (await entrar.isVisible()) {
      await entrar.click({ timeout: 5_000 }).catch(() => {});
    } else if ((await ataque.isVisible()) && (await ataque.isEnabled())) {
      if (!cotejo) {
        cotejo = await cotejarHud(page, { api, quien, idPartida });
      }
      // Entre verlo habilitado y pulsarlo, la IA puede jugar o la partida
      // terminar: el clic se intenta un rato corto y el bucle vuelve a mirar.
      const golpeo = await ataque
        .click({ timeout: 5_000 })
        .then(() => true)
        .catch(() => false);
      golpes += golpeo ? 1 : 0;
    } else {
      await page.waitForTimeout(500);
    }
  }
  await expect(resultado).toBeVisible({ timeout: 60_000 });
  return { golpes, cotejo };
}

/** Crea la sala desde «Jugar online» y entra a su sala de espera. */
async function crearSalaYEntrar(page, { modalidad, privada = false }) {
  await irA(page, 'jugar');
  await expect(page).toHaveURL(EN.jugar);
  await expect(page.locator('[data-zona="subtitulo"]')).not.toHaveText('Buscando batallas', {
    timeout: 30_000,
  });
  // Punto 9: ningún texto técnico del canal a la vista.
  expect(await textoVisible(page, 'main')).not.toMatch(/canal en tiempo real|websocket/i);
  await page.getByRole('link', { name: 'Crear sala' }).first().click();
  await expect(page).toHaveURL(EN.crearSala);
  await page.locator(modalidad).check();
  if (privada) {
    await page.locator('#formulario-crear-sala [name="privada"]').check();
  }
  await page.click('#formulario-crear-sala [type="submit"]');
  const aviso = page.locator('#formulario-crear-sala [data-zona="aviso"]');
  await expect(aviso).toContainText('Sala creada', { timeout: 30_000 });
  await aviso.locator('[data-accion="entrar-a-la-sala"]').click();
  await page.waitForURL(EN.sala, { timeout: 30_000 });
  await expect(page.locator('[data-accion="iniciar-partida"]')).toBeVisible({ timeout: 30_000 });
  return new URL(page.url()).searchParams.get('sala');
}

/** «Iniciar combate» y la partida que deja en la dirección (R17.4). */
async function iniciarCombate(page) {
  const iniciada = page.waitForResponse(
    (r) =>
      /\/api\/v1\/salas\/[^/]+\/partida$/.test(new URL(r.url()).pathname) &&
      r.request().method() === 'POST',
  );
  const boton = page.locator('[data-accion="iniciar-partida"]');
  await expect(boton).toBeEnabled({ timeout: 30_000 });
  await boton.click();
  const respuesta = await iniciada;
  expect(respuesta.status(), await respuesta.text()).toBe(201);
  await expect(page).toHaveURL(/[?&]partida=/, { timeout: 30_000 });
  return new URL(page.url()).searchParams.get('partida');
}

// ------------------------------------------------------------- recorridos

test.describe('PLAYER-08 · los dos recorridos del jugador (revisión del 6-oct)', () => {
  test('recorrido 1 · de la entrada a publicar en la subasta', async ({
    page,
    context,
  }, testInfo) => {
    test.skip(testInfo.project.name !== ESCRITORIO, 'lo que se prueba no depende de la anchura');
    test.setTimeout(15 * 60_000);

    const api = await apiRequest.newContext({ baseURL: BASE });
    const bitacora = abrirBitacora(`${ESCRITORIO} · recorrido 1`, [context]);
    const { paso } = bitacora;
    const avisosDelCanal = escucharElCombate(page);
    const cuenta = cuentaDesechable('');
    let jugadora = null;
    let comprado = null;
    let idPartida = null;

    try {
      await paso(0, 'Cuenta nueva con su alta real', async () => {
        jugadora = await sesionDe(api, cuenta.apodo, {
          clave: cuenta.clave,
          email: cuenta.email,
          base: BASE,
        });
        await altaTerminada(api, jugadora);
        const saldo = await saldoDe(api, jugadora);
        expect(saldo, 'el alta acredita los créditos de bienvenida').toBeGreaterThan(0);
        const heroes = (await inventarioDe(api, jugadora)).filter((e) => e.tipo === 'HEROE');
        expect(heroes, 'el alta deja un héroe, uno solo').toHaveLength(1);
        return `cuenta qa_rj…: ${saldo} créditos de bienvenida y un héroe`;
      });

      await paso(
        1,
        'Entrada, registro y recuperación: sin tienda y con la misma marca',
        async () => {
          // Puntos 2, 3 y 5: la entrada ya no lleva la vitrina ni el salto a ella,
          // y las tres pantallas de la cuenta llevan el mismo logotipo.
          await page.goto('/login');
          await expect(page.locator('#formLogin')).toBeVisible();
          await expect(page.locator('#titulo-vitrina-publica')).toHaveCount(0);
          await expect(page.locator('.vitrina-publica')).toHaveCount(0);
          expect(await textoVisible(page, 'body')).not.toContain('Mira lo que se vende');
          const logotipo = async () => {
            const imagen = page.locator('img.entrada__logo').first();
            await expect(imagen).toBeVisible();
            await expect
              .poll(() => imagen.evaluate((img) => img.complete && img.naturalWidth > 0))
              .toBe(true);
            return imagen.evaluate((img) => new URL(img.src).pathname);
          };
          const marca = await logotipo();
          await capturar(page, testInfo, 'rj1-01-login');

          await page.getByRole('link', { name: 'Crear una cuenta' }).first().click();
          await expect(page).toHaveURL(EN.registro);
          expect(await logotipo(), 'registro, con la marca de la entrada').toBe(marca);
          await page.goto('/login');
          await page.getByRole('link', { name: '¿Olvidaste tu contraseña?' }).click();
          await expect(page).toHaveURL(EN.recuperar);
          expect(await logotipo(), 'recuperación, con la marca de la entrada').toBe(marca);

          const intentos = await entrarHastaElInicio(page, cuenta);
          return `/login, /registro y la recuperación con ${marca.split('/').pop()}; sin vitrina → /inicio (${intentos} intento/s)`;
        },
      );

      await paso(2, 'Inicio con la tienda, sin «A dónde ir» y con el asistente', async () => {
        // Puntos 4, 6 y 7.
        const escaparate = page.locator('.home__tienda');
        await expect(escaparate).toBeVisible();
        const verEnTienda = escaparate.locator('[data-ver-en-tienda]');
        await expect(verEnTienda.first()).toBeVisible({ timeout: 30_000 });
        const enInicio = await verEnTienda.count();
        expect(enInicio).toBeLessThanOrEqual(8);
        expect(await textoVisible(page, 'main')).not.toContain('A dónde ir');
        await expect(page.getByRole('button', { name: 'Abrir asistente Nexus' })).toBeVisible();
        await capturar(page, testInfo, 'rj1-02-inicio');
        await escaparate.locator('[data-zona="ver-tienda"]').click();
        await expect(page).toHaveURL(/tienda/);
        await expect(page.locator('#productos-grid .product-card').first()).toBeVisible({
          timeout: 30_000,
        });
        return `${enInicio} productos en «Explora el Nexo · Tienda»; asistente Nexus; «Ver toda la tienda»`;
      });

      await paso(3, 'Comprar con créditos algo para luego subastarlo', async () => {
        const saldo = await saldoDe(api, jugadora);
        const candidatos = (await vitrinaDe(api, jugadora))
          .filter(
            (p) =>
              Number.isInteger(p.precioCreditos) &&
              p.precioCreditos > 0 &&
              p.precioCreditos <= saldo - COMISION_MAXIMA &&
              !p.esPropio &&
              p.tipo !== 'HEROE',
          )
          .sort((a, b) => a.precioCreditos - b.precioCreditos || a.nombre.localeCompare(b.nombre));
        expect(candidatos.length, 'algo de la tienda se paga con el alta').toBeGreaterThan(0);
        const [producto] = candidatos;
        await page.locator('#busqueda-tienda').fill(producto.nombre);
        const tarjeta = page.locator(
          `#productos-grid .product-card[data-id-producto="${producto.id}"]`,
        );
        await expect(tarjeta).toBeVisible({ timeout: 20_000 });
        await tarjeta.locator('.btn-add').click();
        await page.locator('#btn-pagar').click();
        const dialogo = page.getByRole('dialog', { name: 'Pagar tu compra' });
        await dialogo.getByLabel(/Créditos del Nexo/).check();
        await dialogo.locator('[data-accion="confirmar-pago"]').click();
        await expect(dialogo.locator('.pago__resultado')).toContainText('Compra realizada', {
          timeout: 30_000,
        });
        let elemento = null;
        await expect
          .poll(
            async () => {
              elemento =
                (await inventarioDe(api, jugadora)).find((e) => e.productoId === producto.id) ??
                null;
              return elemento !== null;
            },
            { timeout: 30_000, message: 'lo comprado llega al inventario' },
          )
          .toBe(true);
        comprado = { producto, elemento };
        await page.keyboard.press('Escape');
        await expect(dialogo).toBeHidden();
        return `«${producto.nombre}» por ${producto.precioCreditos} créditos, en el inventario`;
      });

      await paso(4, 'Jugar: crear una sala «Solo» y entrar (puntos 8, 9, 11 y 16)', async () => {
        await crearSalaYEntrar(page, { modalidad: '#modalidad-ia' });
        // Contra la IA la sala nace completa: no pide otro humano.
        await expect(page.locator('[data-zona="estado-espera"]')).toHaveText(
          /^Listo para combatir/,
          { timeout: 30_000 },
        );
        await capturar(page, testInfo, 'rj1-04-sala-de-espera');
        return '«Solo» → «Sala creada» → «Entrar a la sala» → «Listo para combatir»';
      });

      await paso(
        5,
        'Cuenta atrás y combate contra la IA: HUD = servidor, sin auto-daño',
        async () => {
          idPartida = await iniciarCombate(page);
          // Punto 17: 5…1 y «¡COMBATE!» al empezar de verdad; los controles esperan.
          const cuentaAtras = page.locator('[data-zona="cuenta-atras"]');
          await expect(cuentaAtras).toBeVisible({ timeout: 10_000 });
          const desde = Date.now();
          await expect(cuentaAtras).toBeHidden({ timeout: 15_000 });
          const cuenta = Date.now() - desde;
          expect(cuenta, 'la cuenta atrás dura sus cinco segundos').toBeGreaterThan(2_500);
          expect(cuenta).toBeLessThan(9_000);

          const { golpes, cotejo } = await jugarHastaElFinal(page, {
            api,
            quien: jugadora,
            idPartida,
          });
          const acciones = avisosDelCanal.filter(
            (a) => a.idPartida === idPartida && a.accion?.codigo !== 'EFECTO_POR_TURNO',
          );
          expect(acciones.length, 'el canal contó el combate').toBeGreaterThan(0);
          expect(
            accionesIlegales(acciones),
            'ni la jugadora ni la IA se dañan a sí mismas',
          ).toEqual([]);
          expect(cotejo, 'hubo un turno propio para cotejar el HUD').not.toBeNull();
          await expect(page.locator('[data-zona="registro"]')).toContainText('(IA)');
          const final = await partidaDe(api, jugadora, idPartida);
          expect(final.estado).toBe('FINALIZADA');
          await capturar(page, testInfo, 'rj1-05-combate');
          const deLaIa = acciones.filter((a) => a.idEjecutor !== jugadora.claims.uid).length;
          return (
            `cuenta atrás de ${(cuenta / 1000).toFixed(1)} s; ${golpes} golpes; ` +
            `${acciones.length - deLaIa} acciones propias y ${deLaIa} de la IA, ninguna ilegal; ` +
            `HUD = servidor (vidas ${cotejo.vidas.join(' / ')}` +
            `${cotejo.poder === null ? '' : `, poder ${cotejo.poder}`}); ${final.resultado}`
          );
        },
      );

      await paso(6, 'Resultado y vuelta a las salas (punto 20)', async () => {
        const resultado = page.locator('[data-zona="resultado"]');
        await expect(resultado).toContainText(/has ganado|has perdido|empate/i);
        const volver = resultado.locator('[data-accion="volver-a-jugar"]');
        await expect(volver).toHaveText('Volver a las salas');
        const desenlace = (
          await resultado
            .locator('.panel-resultado__palabra, .panel-resultado__detalle')
            .allInnerTexts()
        )
          .map((parte) => parte.replace(/\s+/g, ' ').trim())
          .join(' · ');
        await capturar(page, testInfo, 'rj1-06-resultado');
        await volver.click();
        await expect(page).toHaveURL(EN.jugar);
        await expect(page.locator('[data-zona="subtitulo"]')).not.toHaveText('Buscando batallas', {
          timeout: 30_000,
        });
        return `${desenlace.slice(0, 120)} → «Volver a las salas»`;
      });

      await paso(
        7,
        'Misión: «Ver detalles» y «Preparar misión» en pasos (puntos 21-23)',
        async () => {
          await irA(page, 'misiones');
          await expect(page).toHaveURL(EN.misiones);
          const tarjeta = page.locator('.mision-card[data-estado="disponible"]').first();
          await expect(tarjeta).toBeVisible({ timeout: 30_000 });
          const nombre = (await tarjeta.locator('.mision-card__nombre, h3').first().textContent())
            ?.replace(/\s+/g, ' ')
            .trim();
          await tarjeta.locator('[data-accion="ver-detalles"]').click();
          await expect(page).toHaveURL(/[?&]mision=/);
          const preparar = page.locator('[data-accion="preparar-mision"]');
          await expect(preparar).toBeVisible({ timeout: 30_000 });
          await preparar.click();
          const asistente = page.locator('.mision-asistente');
          await expect(asistente).toBeVisible();
          await expect(asistente.locator('.mision-asistente__contador')).toHaveText('Paso 1 de 5');
          await expect(asistente.locator('[aria-current="step"]')).toHaveCount(1);
          const siguiente = asistente.locator('[data-accion="paso-siguiente"]');
          await expect(siguiente).toHaveAttribute('aria-disabled', 'false', { timeout: 30_000 });
          await siguiente.click();
          await expect(asistente).toHaveAttribute('data-paso', 'estadisticas');
          await expect(asistente.locator('.mision-asistente__contador')).toHaveText('Paso 2 de 5');
          await capturar(page, testInfo, 'rj1-07-preparar-mision');
          return `«${nombre}»: «Ver detalles» → «Preparar misión» → paso 1 de 5 (héroe) → paso 2 (estadísticas)`;
        },
      );

      await paso(8, 'Torneo: su tarjeta y su ruta (punto 24)', async () => {
        await irA(page, 'torneo');
        await expect(page).toHaveURL(EN.torneos);
        const listado = page.locator('[data-zona="listado"]');
        await expect(
          listado.locator('.torneo-card, .estado-vista--vacio, .estado-vista--error').first(),
        ).toBeVisible({ timeout: 30_000 });
        await expect(listado.locator('.estado-vista--error')).toHaveCount(0);
        const tarjeta = listado.locator('.torneo-card').first();
        if ((await listado.locator('.torneo-card').count()) === 0) {
          await expect(listado.locator('.estado-vista--vacio')).toContainText(
            'No hay ningún torneo abierto',
          );
          await capturar(page, testInfo, 'rj1-08-torneos-vacio');
          return 'no hay torneos ahora: el tablón lo dice y ofrece jugar una batalla';
        }
        const nombre = (await tarjeta.locator('.torneo-card__nombre').textContent())?.trim();
        await tarjeta.locator('[data-accion="abrir"]').click();
        await expect(page).toHaveURL(/[?&]torneo=/);
        const ruta = page.locator('[data-zona="detalle"] .torneo-ruta');
        await expect(ruta).toBeVisible({ timeout: 30_000 });
        await expect(ruta.locator('.torneo-ruta__hito')).toHaveCount(6);
        await expect(ruta.locator('[aria-current="step"]')).toHaveCount(1);
        await expect(page.locator('[data-zona="tablon"]')).toBeHidden();
        const actual = await ruta.locator('[aria-current="step"]').getAttribute('data-hito');
        await capturar(page, testInfo, 'rj1-08-torneo');
        await page.locator('[data-accion="volver-a-torneos"]').click();
        await expect(page).not.toHaveURL(/[?&]torneo=/);
        await expect(tarjeta).toBeVisible();
        return `«${nombre}» → ruta de seis hitos (ahora «${actual}») → «Todos los torneos»`;
      });

      await paso(9, 'Inventario: mis héroes, los del Nexo y la búsqueda (punto 25)', async () => {
        await irA(page, 'inventario');
        await expect(page).toHaveURL(EN.inventario);
        const misHeroes = page.locator('.inventario__seccion--mis-heroes');
        await expect(misHeroes.locator('h2')).toHaveText('Mis héroes');
        await expect(misHeroes.locator('[data-heroe]')).toHaveCount(1, { timeout: 30_000 });
        const nexo = page.locator('[data-zona="heroes-del-nexo"]');
        await expect(
          nexo.locator('.nexo-heroe, .estado-vista--vacio, .estado-vista--error').first(),
        ).toBeVisible({ timeout: 30_000 });
        await expect(nexo.locator('.estado-vista--error')).toHaveCount(0);
        const enElNexo = await nexo.locator('.nexo-heroe').count();
        const tuyos = await nexo.locator('.nexo-heroe[data-tuyo="true"]').count();
        // El banner de misiones (RF-INV-003) ya no encabeza la página: si lo
        // hay, va después de los héroes y con su título.
        const misiones = page.locator('.inventario__misiones');
        if (await misiones.isVisible()) {
          await expect(misiones.locator('h2')).toHaveText('Misiones para tus héroes');
          const despues = await page.evaluate(() => {
            const heroes = document.querySelector('.inventario__panel--heroes');
            const banner = document.querySelector('.inventario__misiones');
            // eslint-disable-next-line no-bitwise
            return Boolean(
              heroes.compareDocumentPosition(banner) & Node.DOCUMENT_POSITION_FOLLOWING,
            );
          });
          expect(despues, 'el banner de misiones va después de los héroes').toBe(true);
        }

        // Búsqueda general (HU-INV-002): con menos de cuatro letras no se busca.
        const busqueda = page.locator('.inventario-busqueda');
        const campo = busqueda.locator('[name="criterio"]');
        const buscar = busqueda.getByRole('button', { name: 'Buscar', exact: true });
        const nombre = comprado.producto.nombre;
        let consultas = 0;
        const contar = (peticion) => {
          if (new URL(peticion.url()).pathname.endsWith('/inventario/elementos/busqueda')) {
            consultas += 1;
          }
        };
        page.on('request', contar);
        await campo.fill(nombre.slice(0, 3));
        await buscar.click();
        // Lo dice la vista o la validación del propio campo (minlength=4);
        // lo que no puede pasar es que se consulte.
        await expect
          .poll(() => avisoDeCriterioCorto(page, campo), {
            timeout: 10_000,
            message: 'con tres letras se avisa que faltan',
          })
          .not.toBe('ninguno');
        const comoAvisa = await avisoDeCriterioCorto(page, campo);
        page.off('request', contar);
        expect(consultas, 'con menos de cuatro letras no se consulta').toBe(0);
        const criterio = nombre.length >= 4 ? nombre : comprado.elemento.tipo;
        await campo.fill(criterio);
        await buscar.click();
        await expect(page.locator('.inventario__mensaje')).toContainText(/resultado/, {
          timeout: 30_000,
        });
        await expect(
          page.locator('.inventario__contenido .vitrina__producto').first(),
        ).toBeVisible();
        await capturar(page, testInfo, 'rj1-09-inventario');
        await busqueda.getByRole('button', { name: 'Limpiar' }).click();

        // Posesión no es catálogo: no se regaló ningún héroe.
        const heroes = (await inventarioDe(api, jugadora)).filter((e) => e.tipo === 'HEROE');
        expect(heroes, 'no se regalan héroes').toHaveLength(1);
        return (
          `1 héroe propio; Héroes del Nexo: ${enElNexo} (${tuyos} ya tuyo); ` +
          `búsqueda con 3 letras no consulta (${comoAvisa}), con «${criterio}» encuentra lo comprado`
        );
      });

      await paso(10, 'Subastas: filtros con su etiqueta y su panel (punto 26)', async () => {
        await irA(page, 'subasta');
        await expect(page).toHaveURL(EN.subastas);
        await expect(page.getByLabel('Buscar subastas', { exact: true })).toBeVisible({
          timeout: 30_000,
        });
        await expect(page.getByLabel('Ordenar por', { exact: true })).toBeVisible();
        await expect(page.getByLabel('Por página', { exact: true })).toBeVisible();
        await expect(page.locator('#mercado-filtros-titulo')).toHaveText('Filtros');
        await capturar(page, testInfo, 'rj1-10-subastas');
        return '«Buscar subastas», «Ordenar por» y «Por página» con etiqueta; panel «Filtros»';
      });

      await paso(11, 'Publicar en subasta con el nombre del catálogo (punto 27)', async () => {
        await page.getByRole('link', { name: 'Publicar subasta' }).first().click();
        await expect(page).toHaveURL(EN.publicar);
        const selector = page.locator('#producto');
        const opcion = selector.locator(`option[value="${comprado.elemento.id}"]`);
        await expect(opcion).toHaveCount(1, { timeout: 30_000 });
        // El catálogo puede responder un instante después: la opción se
        // re-etiqueta sola con su nombre.
        await expect(opcion).toHaveText(new RegExp(`^${escaparRegex(comprado.producto.nombre)}`), {
          timeout: 15_000,
        });
        const texto = ((await opcion.textContent()) ?? '').trim();
        expect(texto, 'la opción no lleva identificadores').not.toMatch(UUID);
        await selector.selectOption(comprado.elemento.id);
        const resumen = page.locator('[data-resumen-producto]');
        await expect(resumen).toHaveAttribute('data-origen-nombre', 'catalogo');
        expect((await resumen.textContent()) ?? '').not.toMatch(UUID);
        await page.locator('#inicial').fill(String(PRECIO_INICIAL_DE_LA_SUBASTA));
        await page.locator('#aceptar').check();
        const publicada = page.waitForResponse(
          (r) =>
            new URL(r.url()).pathname === '/api/v1/subastas' && r.request().method() === 'POST',
        );
        await page.getByRole('button', { name: 'Confirmar y publicar' }).click();
        const respuesta = await publicada;
        expect(respuesta.status(), await respuesta.text()).toBe(201);
        const subasta = await respuesta.json();
        await expect(page.locator('[data-accion="ver-publicada"]')).toBeVisible({
          timeout: 30_000,
        });
        await capturar(page, testInfo, 'rj1-11-publicada');
        const ficha = await api.get(`/api/v1/subastas/${subasta.id}`);
        expect(ficha.status(), await ficha.text()).toBe(200);
        const enElMercado = (await ficha.json()).nombreProducto ?? '';
        expect(enElMercado, 'el mercado la nombra').toBeTruthy();
        expect(enElMercado).not.toMatch(UUID);
        return `«${texto}» en la opción y en la confirmación → publicada como «${enElMercado}»`;
      });

      // Ningún error de página ni 5xx en todo el recorrido (salvo los servicios
      // que el catálogo declara fuera de DEV).
      expect(bitacora.incidencias, 'errores de página o 5xx durante el recorrido').toEqual([]);
    } finally {
      await bitacora.cerrar(testInfo);
      await api.dispose();
    }
  });

  test('recorrido 2 · sala privada, invitación, chat grupal, combate y rendición', async ({
    page,
    context,
    browser,
  }, testInfo) => {
    test.skip(testInfo.project.name !== ESCRITORIO, 'lo que se prueba no depende de la anchura');
    test.setTimeout(10 * 60_000);

    const api = await apiRequest.newContext({ baseURL: BASE });
    const contextoInvitado = await browser.newContext({
      baseURL: BASE,
      locale: 'es-CO',
      timezoneId: 'America/Bogota',
      viewport: { width: 1440, height: 900 },
    });
    const anfitriona = page;
    const invitado = await contextoInvitado.newPage();
    const bitacora = abrirBitacora(`${ESCRITORIO} · recorrido 2`, [context, contextoInvitado]);
    const { paso } = bitacora;
    const avisosDelCanal = [anfitriona, invitado].map(escucharElCombate);
    const cuentas = { anfitriona: cuentaDesechable('a'), invitado: cuentaDesechable('b') };
    const sesiones = {};
    let idSala = null;
    let codigo = null;
    let idPartida = null;

    try {
      await paso(0, 'Dos cuentas nuevas con su alta real, cada una en su navegador', async () => {
        for (const [rol, cuenta] of Object.entries(cuentas)) {
          sesiones[rol] = await sesionDe(api, cuenta.apodo, {
            clave: cuenta.clave,
            email: cuenta.email,
            base: BASE,
          });
          await altaTerminada(api, sesiones[rol]);
        }
        await entrarHastaElInicio(anfitriona, cuentas.anfitriona);
        await entrarHastaElInicio(invitado, cuentas.invitado);
        return 'dos cuentas qa_rj…, cada una en su inicio';
      });

      await paso(1, 'Sala privada 1 contra 1 con su código (puntos 10-12)', async () => {
        idSala = await crearSalaYEntrar(anfitriona, {
          modalidad: '#modalidad-duelo',
          privada: true,
        });
        const enPantalla = anfitriona.locator('[data-zona="codigo-invitacion"]');
        await expect(enPantalla).toHaveText(/^[A-Za-z0-9-]{4,20}$/, { timeout: 30_000 });
        codigo = ((await enPantalla.textContent()) ?? '').trim();
        await expect(anfitriona.locator('[data-zona="contador-plazas"]')).toHaveText('1 / 2');
        await capturar(anfitriona, testInfo, 'rj2-01-sala-privada');
        return 'sala privada con su código a la vista; plazas 1 / 2';
      });

      await paso(2, 'Invitar por apodo (punto 13)', async () => {
        const apodo = sesiones.invitado.apodo;
        await anfitriona.locator('[data-accion="abrir-invitar"]').click();
        await anfitriona.locator('#buscar-invitado').fill(apodo);
        const invitar = anfitriona.getByRole('button', { name: `Invitar a ${apodo}`, exact: true });
        await expect(invitar).toBeVisible({ timeout: 30_000 });
        await invitar.click();
        await expect(anfitriona.locator('[data-zona="acuse-invitacion"]')).toHaveText(
          `Invitación enviada a ${apodo}.`,
          { timeout: 30_000 },
        );
        await expect(anfitriona.locator('[data-zona="panel-invitar"]')).not.toContainText(
          sesiones.invitado.claims.uid,
        );
        return '«Invitación enviada a …» (por su apodo, sin su identificador)';
      });

      await paso(
        3,
        'El invitado entra desde su aviso, con el código (puntos 10 y 13)',
        async () => {
          await invitado.locator('header[data-cabecera-app] a.cabecera__campana').click();
          const unirme = invitado.locator('[data-accion="unirme-a-la-sala"]').first();
          await expect(async () => {
            if (!(await unirme.isVisible())) {
              await invitado.reload();
            }
            await expect(unirme).toBeVisible({ timeout: 5_000 });
          }).toPass({ timeout: 60_000, intervals: [2_000, 5_000] });
          const enlace = decodeURIComponent((await unirme.getAttribute('href')) ?? '');
          expect(enlace).toContain(`sala=${idSala}`);
          expect(enlace).toContain(`codigo=${codigo}`);
          await unirme.click();
          await invitado.waitForURL(EN.sala, { timeout: 30_000 });
          await expect(anfitriona.locator('[data-zona="contador-plazas"]')).toHaveText('2 / 2', {
            timeout: 30_000,
          });
          await capturar(invitado, testInfo, 'rj2-03-invitado-en-la-sala');
          return 'campana → «Unirme» (lleva la sala y el código) → sala de espera; plazas 2 / 2';
        },
      );

      await paso(4, 'Empezar el combate entre los dos', async () => {
        idPartida = await iniciarCombate(anfitriona);
        for (const quien of [anfitriona, invitado]) {
          await expect(quien.locator('[data-barra-vida]')).toHaveCount(2, { timeout: 30_000 });
        }
        return 'los dos ven las dos barras de vida';
      });

      await paso(5, 'Chat grupal en la misma página, de ida y vuelta (punto 15)', async () => {
        const texto = `Suerte, recorrido 2 ${new Date().toISOString().slice(11, 19)}`;
        const pestanas = context.pages().length;
        const abrirA = anfitriona.locator('[data-accion="abrir-chat-grupal"]');
        await expect(abrirA).toBeVisible({ timeout: 30_000 });
        await abrirA.click();
        const panelA = anfitriona.locator('[data-zona="chat-grupal"]');
        await expect(panelA).toBeVisible();
        expect(context.pages(), 'no se abre otra pestaña').toHaveLength(pestanas);
        await panelA.locator('#texto-chat-grupal').fill(texto);
        await panelA.locator('#formulario-chat-grupal button[type="submit"]').click();
        await expect(panelA.locator('[data-zona="mensajes"]')).toContainText(texto, {
          timeout: 30_000,
        });
        await anfitriona.keyboard.press('Escape');
        await expect(panelA).toBeHidden();

        await invitado.locator('[data-accion="abrir-chat-grupal"]').click();
        const panelB = invitado.locator('[data-zona="chat-grupal"]');
        await expect(panelB.locator('[data-zona="mensajes"]')).toContainText(texto, {
          timeout: 30_000,
        });
        await capturar(invitado, testInfo, 'rj2-05-chat-grupal');
        await invitado.keyboard.press('Escape');
        await expect(panelB).toBeHidden();
        return 'la anfitriona escribe y el invitado lo lee, sin salir de la batalla';
      });

      await paso(6, 'Un golpe de quien tiene el turno, sin dañarse a sí mismo', async () => {
        const antes = await partidaDe(api, sesiones.anfitriona, idPartida);
        const tieneElTurno =
          antes.turnoActual?.idJugador === sesiones.anfitriona.claims.uid ? anfitriona : invitado;
        const ataque = tieneElTurno.locator('[data-zona="acciones"] [data-atacar]').first();
        const entrar = tieneElTurno.locator('[data-accion="entrar-al-combate"]');
        await expect(async () => {
          if (await entrar.isVisible()) {
            await entrar.click({ timeout: 2_000 }).catch(() => {});
          }
          await expect(ataque).toBeEnabled({ timeout: 2_000 });
        }).toPass({ timeout: 60_000 });
        await ataque.click();
        await expect
          .poll(
            async () =>
              (await partidaDe(api, sesiones.anfitriona, idPartida)).turnoActual?.numeroTurno,
            { timeout: 30_000, message: 'el turno pasa al otro' },
          )
          .not.toBe(antes.turnoActual?.numeroTurno);
        const acciones = avisosDelCanal
          .flat()
          .filter((a) => a.idPartida === idPartida && a.accion?.codigo !== 'EFECTO_POR_TURNO');
        expect(acciones.length, 'el canal contó el golpe').toBeGreaterThan(0);
        expect(accionesIlegales(acciones), 'nadie se daña a sí mismo').toEqual([]);
        return `${tieneElTurno === anfitriona ? 'la anfitriona' : 'el invitado'} golpea; turno ${antes.turnoActual?.numeroTurno} → siguiente; sin auto-daño`;
      });

      await paso(
        7,
        '«Salir» es rendirse: pregunta, confirma y gana el otro (punto 18)',
        async () => {
          await invitado.locator('[data-zona="salir"]').click();
          const dialogo = invitado.getByRole('dialog', { name: '¿Abandonar la batalla?' });
          await expect(dialogo).toBeVisible();
          await expect(dialogo).toContainText('Si abandonas la batalla se contará como derrota');
          await capturar(invitado, testInfo, 'rj2-07-abandonar');
          await dialogo.getByRole('button', { name: 'Abandonar batalla' }).click();
          await invitado.waitForURL(EN.jugar, { timeout: 30_000 });
          await expect(invitado.locator('[data-zona="aviso-sala"]')).toContainText(
            'Abandonaste la batalla',
          );
          await expect
            .poll(async () => (await partidaDe(api, sesiones.anfitriona, idPartida)).estado, {
              timeout: 30_000,
            })
            .toBe('FINALIZADA');
          const final = await partidaDe(api, sesiones.anfitriona, idPartida);
          expect(final.ganadores, 'gana quien se queda').toContain(sesiones.anfitriona.claims.uid);
          await expect(anfitriona.locator('[data-zona="resultado"]')).toContainText(/has ganado/i, {
            timeout: 30_000,
          });
          await capturar(anfitriona, testInfo, 'rj2-07-victoria-por-abandono');
          return '«¿Abandonar la batalla?» → «Abandonar batalla» → listado con el aviso; la anfitriona gana';
        },
      );

      expect(bitacora.incidencias, 'errores de página o 5xx durante el recorrido').toEqual([]);
    } finally {
      await bitacora.cerrar(testInfo);
      await contextoInvitado.close();
      await api.dispose();
    }
  });
});

/** Un texto literal dentro de una expresión regular. */
function escaparRegex(texto) {
  return String(texto).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
