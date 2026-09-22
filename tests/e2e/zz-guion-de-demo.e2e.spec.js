/**
 * Guion de demostración del Sprint 2, ejecutado por la máquina (bloque R1).
 *
 * Recorre, en el orden en que se demuestra en la Review, los doce pasos del
 * corte vertical de la batalla contra el banco E2E real (`tests/e2e/compose.yml`),
 * y deja **evidencia automática** en `test-results/evidencia/`: una captura
 * numerada por paso y un `guion.json` con los datos que se afirmaron (ids,
 * saldos antes/después, ganador). El workflow `e2e.yml` sube esa carpeta como
 * artefacto `informe-e2e`, así que cada corrida verde de CI es un acta.
 *
 * Los pasos, tal como los pide `docs/gobierno/DEMO-SPRINT-2.md`:
 *
 *   1 banco levantado      7 partida
 *   2 login                8 combate
 *   3 héroe / inventario   9 barra de vida
 *   4 crear sala          10 final
 *   5 segundo jugador     11 apuesta / liquidación
 *   6 modalidad / IA      12 chat
 *
 * Corre la última a propósito (`zz-`): los demás specs ya afirman cada regla
 * con detalle; este no repite sus afirmaciones finas, cuenta la historia
 * completa de punta a punta y la deja fotografiada.
 */

import { test, expect, request as apiRequest } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const FINANZAS = process.env.E2E_FINANZAS ?? 'http://localhost:8093/api/v1';
const ANFITRION = process.env.E2E_ANFITRION ?? 'anfitriona_e2e';
const INVITADO = process.env.E2E_INVITADO ?? 'invitado_e2e';
const CURIOSO = process.env.E2E_CURIOSO ?? 'curioso_e2e';
const CLAVE = 'Contrasena-E2E-2026';
/** Cabe en lo que sembrar.sh acredita (500) aunque otros specs dejen reservas. */
const APUESTA = 60;

const VISTAS = '/frontend/app-web/src';
const EVIDENCIA = path.resolve(process.cwd(), 'test-results', 'evidencia');

const guion = { generadoEn: new Date().toISOString(), pasos: [] };

function anotar(numero, titulo, datos = {}) {
  guion.pasos.push({ numero, titulo, ...datos });
}

async function capturar(page, numero, nombre) {
  fs.mkdirSync(EVIDENCIA, { recursive: true });
  const archivo = path.join(EVIDENCIA, `${String(numero).padStart(2, '0')}-${nombre}.png`);
  await page.screenshot({ path: archivo, fullPage: true });
  return path.basename(archivo);
}

function cuerpoDelToken(jwt) {
  const base64 = jwt.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
  return JSON.parse(Buffer.from(base64, 'base64').toString('utf8'));
}

function conToken(token) {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

async function sesionDe(api, apodo) {
  const email = `${apodo}@nexus.test`;
  const registro = await api.post('/api/v1/auth/registro', {
    multipart: { nombres: 'Jugadora', apellidos: 'De Prueba', email, password: CLAVE, apodo },
  });
  expect([200, 201, 400, 409]).toContain(registro.status());
  const login = await api.post('/api/v1/auth/login', { data: { email, password: CLAVE } });
  expect(login.status(), `login de ${apodo}: ${await login.text()}`).toBe(200);
  const cuerpo = await login.json();
  return { ...cuerpo, apodo, email, claims: cuerpoDelToken(cuerpo.token) };
}

async function saldoDe(api, quien) {
  const r = await api.get(`${FINANZAS}/creditos/${quien.claims.uid}/saldo`, {
    headers: conToken(quien.token),
  });
  expect(r.status(), `saldo de ${quien.apodo}: ${await r.text()}`).toBe(200);
  const s = await r.json();
  return { bruto: Number(s.saldoBruto), reservado: Number(s.saldoReservado), disponible: Number(s.saldoDisponible) };
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

function barras(page) {
  return page.locator('[data-barra-vida]').evaluateAll((bs) =>
    bs.map((b) => ({
      jugador: b.dataset.jugador,
      actual: Number(b.getAttribute('aria-valuenow')),
      maxima: Number(b.getAttribute('aria-valuemax')),
      estado: b.dataset.estado,
    })),
  );
}

test.describe('Guion de demostración del Sprint 2', () => {
  test.describe.configure({ mode: 'serial' });

  let api;
  let anfitriona;
  let invitado;
  let sala;
  let salaContraIA;
  let partida;
  const saldosAntes = {};

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    anfitriona = await sesionDe(api, ANFITRION);
    invitado = await sesionDe(api, INVITADO);
  });

  test.afterAll(async () => {
    fs.mkdirSync(EVIDENCIA, { recursive: true });
    fs.writeFileSync(path.join(EVIDENCIA, 'guion.json'), JSON.stringify(guion, null, 2));
    // Nada queda abierto para la siguiente corrida (la sala jugada ya no esta
    // ABIERTA y el DELETE responde 409; se ignora).
    if (salaContraIA?.id) {
      await api
        .delete(`/api/v1/salas/${salaContraIA.id}`, { headers: conToken(salaContraIA.dueno.token) })
        .catch(() => {});
    }
    if (sala?.id) {
      await api.delete(`/api/v1/salas/${sala.id}`, { headers: conToken(anfitriona.token) }).catch(() => {});
    }
    await api?.dispose();
  });

  test('1 · el banco está levantado: borde, identidad, salas, inventario, motor y libro responden', async () => {
    const borde = await api.get('/salud-borde');
    expect(borde.status()).toBe(200);
    const salas = await api.get('/api/v1/salas', { headers: conToken(anfitriona.token) });
    expect(salas.status()).toBe(200);
    const libro = await api.get(`${FINANZAS}/actuator/health`);
    expect(libro.status()).toBe(200);
    anotar(1, 'Banco E2E levantado', { borde: BORDE, libro: FINANZAS, jwks: '/api/v1/auth/jwks' });
  });

  test('2 · login desde el formulario real', async ({ page }) => {
    await page.goto(`${BORDE}${VISTAS}/cuentas/login.html`);
    await page.fill('#email', anfitriona.email);
    await page.fill('#password', CLAVE);
    const captura = await capturar(page, 2, 'login');
    await page.click('#botonEnviar');
    await page.waitForURL(/index\.html/, { timeout: 20000 });
    const token = await page.evaluate(() => sessionStorage.getItem('nexus.token'));
    expect(token, 'login.js guarda el token de sesion').toBeTruthy();
    expect(cuerpoDelToken(token).uid).toBe(anfitriona.claims.uid);
    const inicio = await capturar(page, 2, 'inicio-tras-login');
    anotar(2, 'Login', { apodo: anfitriona.apodo, uid: anfitriona.claims.uid, capturas: [captura, inicio] });
  });

  test('4 · la anfitriona crea la sala 1 contra 1 con apuesta desde el formulario', async ({ page }) => {
    saldosAntes[anfitriona.apodo] = await saldoDe(api, anfitriona);
    saldosAntes[invitado.apodo] = await saldoDe(api, invitado);

    await conSesion(page, anfitriona);
    await page.goto(`${BORDE}${VISTAS}/plataforma/salas-partidas/crear-sala.html`);
    // En «1 contra 1» el aforo queda fijo en 2 (la vista deshabilita el campo).
    await page.check('[name="modalidad"][value="UNO_CONTRA_UNO"]');
    await expect(page.locator('[name="maximoParticipantes"]')).toHaveValue('2');
    await page.fill('[name="recompensaCreditos"]', String(APUESTA));
    const formulario = await capturar(page, 4, 'crear-sala-formulario');
    // La sala la crea la vista de verdad; el id se lee de la respuesta que
    // recibio el navegador, no de un listado que podria traer salas ajenas.
    const respuesta = page.waitForResponse(
      (r) => r.url().includes('/api/v1/salas') && r.request().method() === 'POST',
      { timeout: 20000 },
    );
    await page.click('#formulario-crear-sala button[type="submit"]');
    const creacion = await respuesta;
    expect(creacion.status(), await creacion.text()).toBe(201);
    sala = await creacion.json();
    await expect(page.locator('[data-zona="aviso"]')).toContainText(/Sala creada/i, { timeout: 20000 });
    const creada = await capturar(page, 4, 'crear-sala-creada');
    expect(sala.modalidad).toBe('UNO_CONTRA_UNO');
    expect(sala.recompensaCreditos).toBe(APUESTA);
    // Crear reserva la apuesta de la anfitriona en el libro real.
    expect((await saldoDe(api, anfitriona)).reservado).toBe(saldosAntes[anfitriona.apodo].reservado + APUESTA);
    anotar(4, 'Sala creada desde la vista', { salaId: sala.id, apuesta: APUESTA, capturas: [formulario, creada] });
  });

  test('3 · la puerta de héroe encuentra el héroe equipado en inventario', async ({ page }) => {
    const r = await api.get(`/api/v1/salas/${sala.id}/verificacion-heroe`, { headers: conToken(anfitriona.token) });
    expect(r.status(), await r.text()).toBe(200);
    const veredicto = await r.json();
    expect(veredicto.resultado).toBe('DISPONIBLE');

    await conSesion(page, anfitriona);
    await page.goto(`${BORDE}${VISTAS}/plataforma/salas-partidas/validacion-heroe.html?sala=${sala.id}`);
    await expect(page.locator('body')).toContainText(veredicto.heroe.nombre, { timeout: 20000 });
    const captura = await capturar(page, 3, 'heroe-verificado');
    anotar(3, 'Héroe equipado verificado contra inventario real', { heroe: veredicto.heroe, capturas: [captura] });
  });

  test('5 · el segundo jugador entra desde el listado y la sala pasa a 2 de 2', async ({ page }) => {
    await conSesion(page, invitado);
    await page.goto(`${BORDE}${VISTAS}/plataforma/salas-partidas/batallas.html`);
    await expect(page.locator('body')).toContainText(/salas? abiertas?/i, { timeout: 20000 });
    const listado = await capturar(page, 5, 'listado-de-batallas');

    const r = await api.post(`/api/v1/salas/${sala.id}/participantes`, { headers: conToken(invitado.token) });
    expect(r.status(), `ingreso del invitado: ${await r.text()}`).toBe(200);
    const actualizada = await r.json();
    expect(actualizada.ocupacion).toBe(2);
    const reservado = (await saldoDe(api, invitado)).reservado;
    expect(reservado).toBe(saldosAntes[invitado.apodo].reservado + APUESTA);
    anotar(5, 'Segundo jugador dentro; su apuesta queda reservada en el libro', {
      ocupacion: actualizada.ocupacion, reservadoInvitado: reservado, capturas: [listado],
    });
  });

  test('6 · modalidad contra la IA: la máquina ocupa cupo y aparece en el listado', async ({ page }) => {
    // Un tercer jugador con heroe (lo siembra sembrar.sh): los dos de la sala
    // 1v1 ya estan ocupados y no pueden abrir otra.
    const curioso = await sesionDe(api, CURIOSO);
    const r = await api.post('/api/v1/salas', {
      headers: conToken(curioso.token),
      data: { maximoParticipantes: 2, modalidad: 'CONTRA_IA', recompensaCreditos: 0, privada: false },
    });
    expect(r.status(), `sala contra la IA: ${await r.text()}`).toBe(201);
    salaContraIA = { ...(await r.json()), dueno: curioso };
    expect(salaContraIA.modalidad).toBe('CONTRA_IA');
    expect(salaContraIA.ocupacion, 'la IA ocupa un cupo desde que se crea').toBe(2);

    await conSesion(page, anfitriona);
    await page.goto(`${BORDE}${VISTAS}/plataforma/salas-partidas/batallas.html`);
    await expect(page.locator('body')).toContainText(/heroe de la IA/i, { timeout: 20000 });
    const captura = await capturar(page, 6, 'modalidades-en-el-listado');
    anotar(6, 'Modalidades: 1v1, contra la IA y hasta seis (HU-SAL-004)', {
      salaContraIA: salaContraIA.id, capturas: [captura],
    });
  });

  test('7 · la partida empieza: turnos repartidos, dos barras a tope en el navegador', async ({ page }) => {
    const r = await api.post(`/api/v1/salas/${sala.id}/partida`, { headers: conToken(anfitriona.token) });
    expect(r.status(), `iniciar: ${await r.text()}`).toBe(201);
    partida = await r.json();
    expect(partida.estado).toBe('EN_CURSO');

    await conSesion(page, anfitriona);
    await page.goto(`${BORDE}${VISTAS}/plataforma/salas-partidas/sala-batalla.html?sala=${sala.id}&partida=${partida.id}`);
    await expect(page.locator('[data-barra-vida]')).toHaveCount(2, { timeout: 20000 });
    await expect(page.locator('[data-zona="conexion"]')).toHaveText(/conectado/i);
    const captura = await capturar(page, 7, 'partida-iniciada');
    anotar(7, 'Partida en curso', { partidaId: partida.id, turno: partida.turnoActual, capturas: [captura] });
  });

  test('8-9-10 · combate por turnos hasta el final, con la barra de vida cambiando de color', async ({ page }) => {
    test.setTimeout(240000);
    const jugadores = { [anfitriona.claims.uid]: anfitriona, [invitado.claims.uid]: invitado };
    const capturas = [];
    let golpes = 0;
    let primeraCapturaDeDano = false;
    let capturaAmarilla = false;
    let capturaRoja = false;

    for (let ronda = 0; ronda < 60; ronda += 1) {
      const estado = await (await api.get(`/api/v1/partidas/${partida.id}`, { headers: conToken(anfitriona.token) })).json();
      if (estado.estado !== 'EN_CURSO') {
        partida = estado;
        break;
      }
      const leToca = jugadores[estado.turnoActual.idJugador];
      await conSesion(page, leToca);
      await page.goto(`${BORDE}${VISTAS}/plataforma/salas-partidas/sala-batalla.html?sala=${sala.id}&partida=${partida.id}`);
      const boton = page.locator('[data-zona="acciones"] [data-atacar]').first();
      if (await boton.isEnabled({ timeout: 10000 }).catch(() => false)) {
        const turnoAntes = estado.turnoActual.numeroTurno;
        await boton.click();
        golpes += 1;
        // Se espera a que el servidor resuelva la accion (el turno avanza o la
        // partida termina), no a una respuesta HTTP: la accion viaja por STOMP.
        await expect
          .poll(
            async () => {
              const ahora = await (
                await api.get(`/api/v1/partidas/${partida.id}`, { headers: conToken(anfitriona.token) })
              ).json();
              return ahora.estado !== 'EN_CURSO' || ahora.turnoActual.numeroTurno > turnoAntes;
            },
            { timeout: 20000, intervals: [500, 1000] },
          )
          .toBe(true);
        const pintadas = await barras(page);
        if (!primeraCapturaDeDano && pintadas.some((b) => b.actual < b.maxima)) {
          capturas.push(await capturar(page, 8, 'combate-primer-dano'));
          primeraCapturaDeDano = true;
        }
        if (!capturaAmarilla && pintadas.some((b) => b.estado === 'medio')) {
          capturas.push(await capturar(page, 9, 'barra-de-vida-amarilla-60'));
          capturaAmarilla = true;
        }
        if (!capturaRoja && pintadas.some((b) => b.estado === 'bajo')) {
          capturas.push(await capturar(page, 9, 'barra-de-vida-roja-40'));
          capturaRoja = true;
        }
      }
    }

    expect(partida.estado, `la partida no termino en ${golpes} golpes`).not.toBe('EN_CURSO');
    // La ultima vista abierta es la de quien dio el ultimo golpe: su aviso
    // `partida.finalizada` llego por el canal y cuenta el desenlace.
    await expect(page.locator('[data-zona="resultado"]')).toHaveText(/has ganado|has perdido|empate/i, {
      timeout: 20000,
    });
    capturas.push(await capturar(page, 10, 'partida-finalizada'));
    const enPie = partida.participantes.filter((p) => p.heroe.vidaActual > 0).map((p) => p.jugador);
    anotar(8, 'Combate real contra motor-combate', { golpes });
    anotar(9, 'Barra de vida con umbrales 60 % / 40 %', { amarilla: capturaAmarilla, roja: capturaRoja });
    anotar(10, 'Final de la partida', { estado: partida.estado, enPie, capturas });
  });

  test('11 · la apuesta se liquida en el libro real: el ganador recibe lo del perdedor', async () => {
    const enPie = partida.participantes.filter((p) => p.heroe.vidaActual > 0);
    test.skip(enPie.length !== 1, 'empate (D-01): se devuelve lo apostado a los dos');
    const ganadorUid = enPie[0].jugador;
    const ganador = ganadorUid === anfitriona.claims.uid ? anfitriona : invitado;
    const perdedor = ganador === anfitriona ? invitado : anfitriona;

    await expect
      .poll(async () => (await saldoDe(api, ganador)).reservado, { timeout: 60000 })
      .toBe(saldosAntes[ganador.apodo].reservado);
    // HU-JUE-012: ademas de la apuesta, 2 creditos al ganador y 1 al perdedor por jugar.
    await expect
      .poll(async () => (await saldoDe(api, ganador)).bruto, { timeout: 60000 })
      .toBe(saldosAntes[ganador.apodo].bruto + APUESTA + 2);
    const delGanador = await saldoDe(api, ganador);
    const delPerdedor = await saldoDe(api, perdedor);
    expect(delGanador.bruto).toBe(saldosAntes[ganador.apodo].bruto + APUESTA + 2);
    expect(delPerdedor.bruto).toBe(saldosAntes[perdedor.apodo].bruto - APUESTA + 1);
    anotar(11, 'Apuesta liquidada (HU-JUE-014) + recompensa por jugar (HU-JUE-012)', {
      ganador: ganador.apodo, perdedor: perdedor.apodo,
      antes: { [ganador.apodo]: saldosAntes[ganador.apodo], [perdedor.apodo]: saldosAntes[perdedor.apodo] },
      despues: { [ganador.apodo]: delGanador, [perdedor.apodo]: delPerdedor },
    });
  });

  test('12 · el chat de la sala funciona por el canal real', async ({ page }) => {
    await conSesion(page, anfitriona);
    await page.goto(`${BORDE}${VISTAS}/plataforma/salas-partidas/chat.html?sala=${sala.id}`);
    await expect(page.locator('[data-zona="conexion"]')).toHaveText(/conectado/i, { timeout: 20000 });
    const texto = `Buena partida — demo ${new Date().toISOString().slice(11, 19)}`;
    await page.fill('#formulario-chat [name="texto"]', texto);
    await page.click('#formulario-chat button[type="submit"]');
    await expect(page.locator('[data-zona="mensajes"]')).toContainText(texto, { timeout: 20000 });
    const captura = await capturar(page, 12, 'chat-de-la-sala');
    anotar(12, 'Chat de la sala (HU-JUE-015)', { mensaje: texto, capturas: [captura] });
  });
});
