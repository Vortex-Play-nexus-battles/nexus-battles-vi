/**
 * Corte vertical de la sala de batalla, contra servicios de verdad.
 *
 * ## Qué demuestra, y por qué no se puede demostrar en AWS
 *
 * Este es el flujo que el host de dev NO puede correr (#435): la puerta de
 * héroe de HU-SAL-003 consulta a `inventario`, que no está desplegado allí, y
 * `ClienteInventarioHeroes` falla cerrado a propósito. Aquí están los seis
 * servicios reales —identidad, héroes, productos, inventario, salas-partidas y
 * motor-combate— con sus bases de datos y el **mismo `borde-dev.conf`** que
 * corre en producción.
 *
 * Ningún doble. Si el contrato entre dos servicios se rompe, esto se pone rojo.
 *
 * ## Qué se afirma
 *
 * Estado y comportamiento observable, nunca un `200` a secas:
 *
 * - el JWT trae el `uid` de ADR-002 y el apodo en `preferred_username`;
 * - la sala nace con su código de invitación y el anfitrión dentro CON su héroe;
 * - quien no tiene héroe equipado NO entra, y el error dice por qué;
 * - el segundo jugador entra por código y el roster pasa a dos;
 * - al iniciar, la partida reparte turnos y da vida inicial a los dos;
 * - una acción real baja la vida del objetivo, y el número lo calculó el motor;
 * - la vida persiste: se relee de la base y sigue bajada;
 * - el turno cambia de jugador.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
// El libro de creditos (HU-JUE-014) no esta detras del borde: se le pregunta
// el saldo por el puerto que expone el compose. Ningun jugador pasa por aqui;
// la prueba lo usa solo para AFIRMAR lo que la apuesta hizo con el dinero.
const FINANZAS = process.env.E2E_FINANZAS ?? 'http://localhost:8093/api/v1';
// Lo que cada participante pone en juego en la sala de este flujo. Tiene que
// caber en lo que `sembrar.sh` acredita (E2E_SALDO_INICIAL, 500 por defecto).
const APUESTA = 100;
const ANFITRION = process.env.E2E_ANFITRION ?? 'anfitriona_e2e';
const INVITADO = process.env.E2E_INVITADO ?? 'invitado_e2e';
/** Tercero con héroe, para poder probar el código de invitación de verdad. */
const CURIOSO = process.env.E2E_CURIOSO ?? 'curioso_e2e';
const CLAVE = 'Contrasena-E2E-2026';

/** Cuerpo de un JWT, sin verificar la firma: aquí solo se lee para afirmar. */
function cuerpoDelToken(jwt) {
  const base64 = jwt.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
  return JSON.parse(Buffer.from(base64, 'base64').toString('utf8'));
}

/**
 * Registra (si hace falta) e inicia sesión. El registro avisa por correo, pero
 * es fail-open: sin servicio de correo la cuenta se crea igual, y por eso este
 * banco no levanta ni correo ni mailpit.
 */
async function sesionDe(api, apodo) {
  const email = `${apodo}@nexus.test`;

  const registro = await api.post('/api/v1/auth/registro', {
    multipart: {
      nombres: 'Jugadora',
      apellidos: 'De Prueba',
      email,
      password: CLAVE,
      apodo,
    },
  });
  // 409/400 si ya existe de una corrida anterior: no es un fallo del flujo.
  expect([200, 201, 400, 409]).toContain(registro.status());

  const login = await api.post('/api/v1/auth/login', {
    data: { email, password: CLAVE },
  });
  expect(login.status(), `login de ${apodo}: ${await login.text()}`).toBe(200);

  const cuerpo = await login.json();
  return { ...cuerpo, claims: cuerpoDelToken(cuerpo.token) };
}

function conToken(token) {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

/** Saldo del jugador en ms-finanzas: bruto, reservado y disponible. */
async function saldoDe(api, uid) {
  const r = await api.get(`${FINANZAS}/creditos/${uid}/saldo`);
  expect(r.status(), `saldo de ${uid}: ${await r.text()}`).toBe(200);
  const s = await r.json();
  return {
    bruto: Number(s.saldoBruto),
    reservado: Number(s.saldoReservado),
    disponible: Number(s.saldoDisponible),
  };
}

test.describe('Sala de batalla de punta a punta', () => {
  test.describe.configure({ mode: 'serial' });

  let api;
  let anfitriona;
  let invitado;
  let sala;
  let partida;
  /** Saldo bruto y reservado por uid justo antes de iniciar la partida (HU-JUE-014). */
  const brutoAlEmpezar = {};
  const reservadoAlEmpezar = {};

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    anfitriona = await sesionDe(api, ANFITRION);
    invitado = await sesionDe(api, INVITADO);
  });

  test.afterAll(async () => {
    await api?.dispose();
  });

  // ===================================================================
  // Identidad — ADR-002
  // ===================================================================

  test('el token trae el uid estable y el apodo, no un apodo por identificador', async () => {
    // El defecto que esto cierra: leer `sub` como identificador provocó el 500
    // del PR #404. Tras ADR-002 el `sub` es el apodo —mutable— y el UUID
    // estable vive en `uid`. `JwtService` emite exactamente eso: subject(apodo)
    // mas los claims `uid`, `rol` y `ver`; no hay `preferred_username`.
    const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    const claims = anfitriona.claims;

    expect(claims.uid, JSON.stringify(claims)).toMatch(uuid);
    // El apodo va en `sub`, y `sub` NO es un UUID: esa confusion es justo la
    // que rompio el servicio.
    expect(claims.sub, JSON.stringify(claims)).toBe(ANFITRION);
    expect(claims.sub).not.toMatch(uuid);
    expect(claims.uid).not.toBe(invitado.claims.uid);
    expect(invitado.claims.sub).toBe(INVITADO);
  });

  // ===================================================================
  // HU-SAL-003 · la puerta de héroe, contra el inventario real
  // ===================================================================

  test('la verificación encuentra el héroe equipado que sembramos en inventario', async () => {
    const antes = await saldoDe(api, anfitriona.claims.uid);

    const r = await api.post('/api/v1/salas', {
      headers: conToken(anfitriona.token),
      data: {
        maximoParticipantes: 2,
        // El nombre exacto del enum `Modalidad`, no una abreviatura: Jackson
        // no deserializa `UNO_VS_UNO` y el servicio responde 400.
        modalidad: 'UNO_CONTRA_UNO',
        // HU-JUE-014: la sala pone creditos en juego. Hasta #442 esto era un
        // 503 fijo; ahora reserva de verdad en ms-finanzas.
        recompensaCreditos: APUESTA,
        privada: true,
      },
    });

    // Que esto sea 201 y no 422 YA prueba que la puerta de héroe consultó al
    // inventario real y encontró el héroe equipado: `CrearSala` pasa por
    // `PuertaDeHeroe`, que lanza `HeroeNoDisponible` (422) si falta.
    expect(r.status(), `crear sala: ${await r.text()}`).toBe(201);
    sala = await r.json();

    // `SalaResponse.participantes` es una lista de identificadores, no de
    // fichas: el héroe no viaja con la sala, solo con la partida.
    expect(sala.participantes, JSON.stringify(sala)).toContain(anfitriona.claims.uid);
    expect(sala.idAnfitrion).toBe(anfitriona.claims.uid);
    expect(sala.ocupacion).toBe(1);
    expect(sala.recompensaCreditos).toBe(APUESTA);

    // HU-JUE-014 CA-01: la recompensa queda RESERVADA en el libro, no
    // descontada: el bruto no baja, el disponible si.
    const despues = await saldoDe(api, anfitriona.claims.uid);
    expect(despues.bruto).toBe(antes.bruto);
    expect(despues.reservado).toBe(antes.reservado + APUESTA);
    expect(despues.disponible).toBe(antes.disponible - APUESTA);
  });

  test('la ruta de verificación cuenta qué héroe encontró en el inventario', async () => {
    // HU-SAL-003 de frente: este endpoint existe para que el diálogo diga el
    // motivo ANTES de pulsar Entrar. Aquí demuestra, ademas, que el héroe que
    // sembramos en inventario es el que ve salas-partidas.
    const r = await api.get(`/api/v1/salas/${sala.id}/verificacion-heroe`, {
      headers: conToken(invitado.token),
    });

    expect(r.status(), `verificacion: ${await r.text()}`).toBe(200);
    const veredicto = await r.json();

    expect(veredicto.resultado, JSON.stringify(veredicto)).toBe('DISPONIBLE');
    expect(veredicto.puedeIngresar).toBe(true);
    // El héroe llega con nombre y vida: si inventario no hubiera contestado,
    // `ClienteInventarioHeroes` falla cerrado y esto seria 503.
    expect(veredicto.heroe, JSON.stringify(veredicto)).toBeTruthy();
    expect(veredicto.heroe.nombre).toBeTruthy();
    expect(veredicto.heroe.vidaMaxima).toBeGreaterThan(0);
    expect(veredicto.heroe.vidaActual).toBe(veredicto.heroe.vidaMaxima);
    expect(veredicto.salaQueLoOcupa).toBeNull();
  });

  test('una sala privada nace con código de invitación', async () => {
    expect(sala.privada).toBe(true);
    expect(sala.codigoInvitacion, JSON.stringify(sala)).toBeTruthy();
    expect(sala.codigoInvitacion.length).toBeGreaterThanOrEqual(4);
  });

  test('sin héroe equipado no se entra, y el error lo explica', async () => {
    // Jugador nuevo, sin sembrar inventario: la puerta tiene que cerrarse.
    const sinHeroe = await sesionDe(api, `sin_heroe_${Date.now()}`);

    const r = await api.post(`/api/v1/salas/${sala.id}/participantes`, {
      headers: conToken(sinHeroe.token),
      data: { codigoInvitacion: sala.codigoInvitacion },
    });

    expect(r.status(), `sin heroe: ${await r.text()}`).toBe(422);
    const problema = await r.json();
    // El texto nombra el caso concreto: «no puedes entrar» a secas obligaria a
    // adivinar si falta equipar un heroe o si el suyo esta en otra batalla.
    expect(problema.title, JSON.stringify(problema)).toMatch(/no tienes un heroe equipado/i);
    expect(problema.detail).toMatch(/equipa un heroe en tu inventario/i);
    expect(problema.type).toMatch(/heroe/i);
  });

  // ===================================================================
  // HU-SAL-002 · el segundo jugador entra por código
  // ===================================================================

  test('el invitado entra con el código y el roster pasa a dos', async () => {
    const antes = await saldoDe(api, invitado.claims.uid);

    const r = await api.post(`/api/v1/salas/${sala.id}/participantes`, {
      headers: conToken(invitado.token),
      data: { codigoInvitacion: sala.codigoInvitacion },
    });

    expect(r.status(), `ingresar: ${await r.text()}`).toBe(200);
    const actualizada = await r.json();

    expect(actualizada.participantes, JSON.stringify(actualizada)).toHaveLength(2);
    expect(actualizada.participantes).toContain(invitado.claims.uid);
    expect(actualizada.ocupacion).toBe(2);

    // HU-JUE-014 CA-01: entrar tambien compromete la apuesta, en el libro real.
    const despues = await saldoDe(api, invitado.claims.uid);
    expect(despues.reservado).toBe(antes.reservado + APUESTA);
    expect(despues.disponible).toBe(antes.disponible - APUESTA);
    // A quien no es anfitrión no se le entrega el código. El campo ni siquiera
    // aparece —`@JsonInclude(NON_NULL)`—: que no exista deja claro que no hay
    // nada que ver, en vez de anunciar que existe algo así y le tocó un nulo.
    expect(actualizada.codigoInvitacion).toBeUndefined();
  });

  test('un código equivocado no abre la sala', async () => {
    // `curioso_e2e` SÍ tiene héroe (lo siembra `sembrar.sh`): sin él la puerta
    // de héroe lo rechazaría con un 422 antes de mirar el código, y esta
    // prueba no estaría probando el código.
    const otro = await sesionDe(api, CURIOSO);
    const reservadoAntes = (await saldoDe(api, otro.claims.uid)).reservado;
    const r = await api.post(`/api/v1/salas/${sala.id}/participantes`, {
      headers: conToken(otro.token),
      data: { codigoInvitacion: 'NO-ES-ESTE' },
    });

    // 409 y no 403: no es un problema de permisos, es que la sala no admite
    // la operación. Lo fija el contrato OpenAPI.
    expect(r.status(), `codigo malo: ${await r.text()}`).toBe(409);
    const problema = await r.json();
    expect(problema.title, JSON.stringify(problema)).toBe('No puedes entrar a esta sala');
    // Y la sala sigue con dos: el intento no ocupó cupo.
    const despues = await api.get(`/api/v1/salas/${sala.id}`, {
      headers: conToken(anfitriona.token),
    });
    expect((await despues.json()).ocupacion).toBe(2);
    // HU-JUE-014: un ingreso rechazado no deja creditos comprometidos. La
    // reserva se hizo antes de que la sala dijera que no, y se devolvio.
    expect((await saldoDe(api, otro.claims.uid)).reservado).toBe(reservadoAntes);
  });

  // ===================================================================
  // HU-SAL-004 / RF-JUE-017 · la partida
  // ===================================================================

  test('al iniciar, la partida reparte turnos y los dos entran a plena vida', async () => {
    // Foto del saldo de cada uno ANTES de jugar: la liquidacion de HU-JUE-014
    // se afirma contra esto al final. Tambien lo reservado: en este momento
    // los dos tienen comprometida la apuesta de ESTA sala, y al final debe
    // haber bajado exactamente eso — no «ser cero», porque otra prueba que
    // fallara a medias dejaria reservas de otras salas y este flujo no tiene
    // por que pagarlo.
    for (const j of [anfitriona, invitado]) {
      const saldo = await saldoDe(api, j.claims.uid);
      brutoAlEmpezar[j.claims.uid] = saldo.bruto;
      reservadoAlEmpezar[j.claims.uid] = saldo.reservado;
    }

    const r = await api.post(`/api/v1/salas/${sala.id}/partida`, {
      headers: conToken(anfitriona.token),
    });

    expect(r.status(), `iniciar partida: ${await r.text()}`).toBe(201);
    partida = await r.json();

    expect(partida.estado).toBe('EN_CURSO');
    expect(partida.participantes).toHaveLength(2);
    expect(partida.turnoActual.numeroTurno).toBe(1);
    // El turno es de alguien que está en la partida, no de un id cualquiera.
    expect(partida.participantes.map((p) => p.jugador)).toContain(partida.turnoActual.idJugador);

    for (const p of partida.participantes) {
      expect(p.heroe.vidaActual).toBe(p.heroe.vidaMaxima);
    }
  });

  // ===================================================================
  // El combate, en el navegador y por STOMP
  //
  // No hay endpoint HTTP para atacar: la accion viaja por
  // `/app/partidas/{id}/acciones` del canal STOMP, que es lo que declara
  // contracts/websocket/salas-partidas.yaml. Asi que esta parte se juega
  // como la juega una persona — con la vista de verdad, el cliente STOMP de
  // verdad y la barra de vida de verdad.
  // ===================================================================

  /**
   * Engancha la consola del navegador y los errores de pagina a un array.
   *
   * Sin esto, un fallo dentro del modulo de la vista es invisible: la prueba
   * solo ve «la barra no cambio» y hay que adivinar por que. Se vuelca en el
   * mensaje de la afirmacion que falle.
   */
  function vozDelNavegador(page) {
    const dicho = [];
    page.on('console', (m) => dicho.push(`[${m.type()}] ${m.text()}`));
    page.on('pageerror', (e) => dicho.push(`[pageerror] ${e.message}`));
    page.on('requestfailed', (r) => dicho.push(`[fallo] ${r.method()} ${r.url()}`));
    return dicho;
  }

  /**
   * Estado real del WebSocket de la vista, no el del indicador.
   *
   * `conectarChat` devuelve un objeto que sigue existiendo aunque el socket se
   * haya cerrado despues, asi que «hay canal» no implica «hay conexion». Esto
   * mira el `readyState` de verdad, interceptando el constructor antes de que
   * la pagina cargue. No es instrumentacion de paso: es lo que distingue «el
   * envio se perdio» de «el servidor lo ignoro», que desde fuera se ven igual.
   *
   * Los frames que llegan se guardan por lo mismo. Solo se vuelcan cuando una
   * afirmacion falla, y ahorran una vuelta entera de CI a quien lo investigue.
   */
  async function conSocketVigilado(page) {
    await page.addInitScript(() => {
      globalThis.__sockets = [];
      globalThis.__frames = [];
      const Original = globalThis.WebSocket;
      globalThis.WebSocket = function (...args) {
        const socket = new Original(...args);
        globalThis.__sockets.push(socket);
        socket.addEventListener('message', (e) =>
          globalThis.__frames.push(String(e.data).slice(0, 400)),
        );
        return socket;
      };
      Object.assign(globalThis.WebSocket, Original);
      globalThis.WebSocket.prototype = Original.prototype;
    });
  }

  /** @returns {Promise<string[]>} frames STOMP que recibio el navegador */
  function framesRecibidos(page) {
    return page.evaluate(() => globalThis.__frames ?? []);
  }

  /** @returns {Promise<number[]>} readyState de cada socket abierto (1 = OPEN) */
  function estadoDeSockets(page) {
    return page.evaluate(() => (globalThis.__sockets ?? []).map((s) => s.readyState));
  }

  /** Deja la sesion puesta antes de que cargue cualquier script de la vista. */
  async function conSesion(page, jugador, apodo) {
    await page.addInitScript(
      ([token, nombre]) => {
        sessionStorage.setItem('nexus.token', token);
        sessionStorage.setItem('nexus.apodoActual', nombre);
      },
      [jugador.token, apodo],
    );
  }

  const VISTA = '/frontend/app-web/src/plataforma/salas-partidas/sala-batalla.html';

  /**
   * Vida de cada barra, leida del DOM.
   *
   * Se lee de `aria-valuenow` / `aria-valuemax` y no de `data-vida-actual`:
   * `barra-vida.js#actualizar` escribe los atributos ARIA y `data-estado`,
   * pero NO reescribe los `data-vida-*` —esos solo los lee `inicializar` para
   * el primer pintado—. Ademas ARIA es el contrato accesible de la barra, que
   * es lo que de verdad ve quien usa un lector de pantalla.
   */
  function barras(page) {
    return page.locator('[data-barra-vida]').evaluateAll((bs) =>
      bs.map((b) => ({
        jugador: b.dataset.jugador,
        actual: Number(b.getAttribute('aria-valuenow')),
        maxima: Number(b.getAttribute('aria-valuemax')),
        estado: b.dataset.estado,
        texto: b.querySelector('.barra-vida__valor')?.textContent ?? '',
      })),
    );
  }

  /** Suma de la vida en pantalla: baja en cuanto alguien recibe un golpe. */
  async function vidaTotal(page) {
    return (await barras(page)).reduce((t, b) => t + b.actual, 0);
  }

  test('el navegador abre la sala, se conecta al canal y pinta las dos barras a tope', async ({
    page,
  }) => {
    await conSesion(page, anfitriona, ANFITRION);
    await page.goto(`${BORDE}${VISTA}?sala=${sala.id}&partida=${partida.id}`);

    // Dos barras: una por participante. Que existan ya prueba que la vista
    // pidio la partida por el borde y la pinto.
    await expect(page.locator('[data-barra-vida]')).toHaveCount(2, { timeout: 20000 });

    const pintadas = await barras(page);

    for (const barra of pintadas) {
      // A plena vida la barra es verde: por encima del 60 %.
      expect(barra.estado, JSON.stringify(pintadas)).toBe('alto');
      expect(barra.actual, JSON.stringify(barra)).toBe(barra.maxima);
      // El color nunca es el unico indicador: el valor tambien se escribe.
      expect(barra.texto).toBe(`${barra.actual}/${barra.maxima}`);
    }

    // Y el canal esta vivo de verdad: la vista solo pinta «conectado» cuando
    // recibio una funcion de suscripcion, y eso solo pasa si el CONNECT de
    // STOMP con el JWT prospero.
    await expect(page.locator('[data-zona="conexion"]')).toHaveText(/conectado/i);

    // Con la partida ya cargada no hay nada que arrancar, y el estado vacio no
    // pinta. Los dos llevan el atributo `hidden`, que no ocultaba nada: la
    // hoja del navegador lo aplica con `display: none`, que pierde contra el
    // `display: flex` de `.fila` y `.pila`. Se veian los dos a la vez.
    await expect(page.locator('[data-zona="arranque"]')).toBeHidden();
    await expect(page.locator('[data-zona="sin-partida"]')).toBeHidden();

    // HU-JUE-015: desde la sala se llega a SU chat (#441 lo tenia como hueco:
    // la vista del chat existia sin que ninguna pantalla enlazara a ella).
    const enlaceChat = page.locator('[data-zona="enlace-chat-sala"]');
    await expect(enlaceChat).toBeVisible();
    await expect(enlaceChat).toHaveAttribute('href', `./chat.html?sala=${sala.id}`);
  });

  test('atacar desde la vista baja la vida del rival, y el aviso llega por STOMP', async ({
    page,
  }) => {
    test.setTimeout(150000);
    const atacanteEsAnfitriona = partida.turnoActual.idJugador === anfitriona.claims.uid;
    const quien = atacanteEsAnfitriona ? anfitriona : invitado;
    const apodo = atacanteEsAnfitriona ? ANFITRION : INVITADO;

    const dicho = vozDelNavegador(page);
    await conSocketVigilado(page);
    await conSesion(page, quien, apodo);
    await page.goto(`${BORDE}${VISTA}?sala=${sala.id}&partida=${partida.id}`);

    const boton = page.locator('[data-zona="acciones"] [data-atacar]').first();
    await expect(boton).toBeVisible({ timeout: 20000 });

    // El canal tiene que estar ABIERTO, no solo haber estado. `conectarChat`
    // devuelve un objeto que sigue ahi aunque el socket se cierre despues:
    // sin esta comprobacion, un envio a un socket cerrado se ve exactamente
    // igual que una accion que el servidor ignora.
    expect(
      await estadoDeSockets(page),
      `sockets al atacar (1 = OPEN). Consola:\n${dicho.join('\n')}`,
    ).toEqual([1]);

    // El defecto que esto cierra: los botones nacian deshabilitados y solo los
    // abria un `partida.turno.cambiado`, mensaje que el servidor unicamente
    // emite DESPUES de que alguien juegue. En el turno 1 nadie ha jugado, asi
    // que el combate no se podia arrancar desde el navegador.
    await expect(boton, 'a quien le toca no puede atacar en el turno 1').toBeEnabled();
    // Y solo hay boton para el rival: a uno mismo no se ataca.
    await expect(page.locator('[data-atacar]')).toHaveCount(1);

    const sumaAntes = await vidaTotal(page);

    await boton.click();
    let golpes = 1;

    // Lo que se espera NO es una respuesta HTTP: es que el servidor resuelva
    // la accion contra motor-combate, la persista y la anuncie por
    // `/tema/partidas/{id}`, y que la vista repinte con lo que llego.
    //
    // Puede hacer falta mas de un golpe, y no es un apano: el motor acierta si
    // la tirada supera la defensa, y «Guerrero Tanque» ataca con 10+1d6 contra
    // defensa 11, asi que un 1 en el dado falla. Fallar es un resultado
    // legitimo del combate. Lo que se afirma es que atacando se acaba haciendo
    // dano, no que el primer golpe entre. Antes del arreglo de la defensa esto
    // no era cuestion de insistir: con la vida (44) como defensa, la tirada
    // maxima (16) no la superaba NUNCA.
    //
    // Se turnan los dos jugadores. Insistir solo con el primero no sirve: tras
    // su golpe el turno pasa al rival, y si nadie juega por el rival la partida
    // se queda parada para siempre.
    try {
      await expect
        .poll(
          async () => {
            const ahora = await vidaTotal(page);
            if (ahora < sumaAntes || golpes >= 12) {
              return ahora;
            }

            const estado = await (
              await api.get(`/api/v1/partidas/${partida.id}`, {
                headers: conToken(anfitriona.token),
              })
            ).json();
            if (estado.estado !== 'EN_CURSO') {
              return ahora;
            }

            const leToca = estado.turnoActual.idJugador === anfitriona.claims.uid;
            await conSesion(page, leToca ? anfitriona : invitado, leToca ? ANFITRION : INVITADO);
            await page.goto(`${BORDE}${VISTA}?sala=${sala.id}&partida=${partida.id}`);

            const suyo = page.locator('[data-zona="acciones"] [data-atacar]').first();
            if (await suyo.isEnabled({ timeout: 10000 }).catch(() => false)) {
              golpes += 1;
              await suyo.click();
            }
            return await vidaTotal(page);
          },
          { timeout: 90000, intervals: [1500] },
        )
        .toBeLessThan(sumaAntes);
    } catch (fallo) {
      // Se vuelve a lanzar con lo que hace falta para diagnosticarlo: el
      // mensaje pelado de Playwright («recibido 88, esperado < 88») no dice
      // si el envio salio, si el socket estaba abierto, ni si el modulo de la
      // vista reventó por el camino.
      fallo.message =
        `${fallo.message}\n\n--- estado al fallar (${golpes} golpes) ---\n` +
        `sockets (1 = OPEN): ${JSON.stringify(await estadoDeSockets(page))}\n` +
        `barras: ${JSON.stringify(await barras(page))}\n` +
        `frames STOMP recibidos por el navegador:\n` +
        `${(await framesRecibidos(page)).join('\n---\n') || '(ninguno)'}\n` +
        `consola del navegador:\n${dicho.join('\n') || '(nada)'}`;
      throw fallo;
    }

    // El golpe cayo sobre alguien, y la barra lo dice con su numero.
    const despues = await barras(page);
    const herido = despues.find((b) => b.actual < b.maxima);
    expect(herido, JSON.stringify(despues)).toBeTruthy();
    expect(herido.texto).toBe(`${herido.actual}/${herido.maxima}`);
  });

  test('la vida que se ve en pantalla es la que quedo guardada en la base', async ({ page }) => {
    // Que la barra baje puede ser cosa del navegador. Esto compara lo pintado
    // contra lo que devuelve el servidor al releer la partida.
    await conSesion(page, anfitriona, ANFITRION);
    await page.goto(`${BORDE}${VISTA}?sala=${sala.id}&partida=${partida.id}`);
    await expect(page.locator('[data-barra-vida]')).toHaveCount(2, { timeout: 20000 });

    const enPantalla = (await barras(page)).map((b) => b.actual).sort((a, b) => a - b);

    const r = await api.get(`/api/v1/partidas/${partida.id}`, {
      headers: conToken(anfitriona.token),
    });
    expect(r.status()).toBe(200);
    const releida = await r.json();
    const enBase = releida.participantes.map((p) => p.heroe.vidaActual).sort((a, b) => a - b);

    expect(enPantalla).toEqual(enBase);
    // Y alguien ya recibio un golpe: no estamos comparando dos estados iniciales.
    expect(Math.min(...enBase)).toBeLessThan(
      Math.max(...releida.participantes.map((p) => p.heroe.vidaMaxima)),
    );
  });

  test('el color de la barra corresponde al numero, con daño real', async ({ page }) => {
    // HU-SAL-005 / RF-JUE-009. Las fronteras exactas (60 y 40, y que el 60 no
    // sea verde y el 40 si sea amarillo) estan probadas en unidad sobre
    // `clasificar`. Lo que anade el E2E es que esa regla se cumple sobre los
    // numeros que produce el motor de verdad y que viajan por STOMP.
    //
    // NO se exige ver los tres colores en una partida: el daño lo deciden los
    // dados y un golpe grande salta la banda amarilla entera —paso: 29/44
    // (alto) -> 12/44 (bajo)—. Exigirlo pondria la prueba roja por suerte. Lo
    // que si se exige es que CADA estado pintado case con su porcentaje, y que
    // la barra salga de «alto», que es lo que demuestra que hubo daño real.
    test.setTimeout(240000);

    /** La regla de RF-JUE-009, escrita aqui a proposito y no importada. */
    const colorEsperado = (actual, maxima) => {
      const porcentaje = (actual / maxima) * 100;
      if (porcentaje > 60) return 'alto';
      if (porcentaje >= 40) return 'medio';
      return 'bajo';
    };

    const vistos = new Set(['alto']); // ya comprobado a plena vida, mas arriba
    const recorrido = [];
    let turnos = 0;

    while (turnos < 40 && !vistos.has('bajo') && !vistos.has('medio')) {
      const enCurso = await (
        await api.get(`/api/v1/partidas/${partida.id}`, {
          headers: conToken(anfitriona.token),
        })
      ).json();
      if (enCurso.estado !== 'EN_CURSO') break;

      const esAnfitriona = enCurso.turnoActual.idJugador === anfitriona.claims.uid;
      const quien = esAnfitriona ? anfitriona : invitado;

      // Se recarga la vista con la sesion de quien tiene el turno. Recargar a
      // mitad de partida y poder seguir jugando es, en si mismo, lo que el
      // defecto de `turnoDe` rompia.
      await conSesion(page, quien, esAnfitriona ? ANFITRION : INVITADO);
      await page.goto(`${BORDE}${VISTA}?sala=${sala.id}&partida=${partida.id}`);

      const boton = page.locator('[data-zona="acciones"] [data-atacar]').first();
      await expect(boton, `turno ${turnos + 1}: el boton no se abrio`).toBeEnabled({
        timeout: 20000,
      });
      await boton.click();

      // Se espera al CAMBIO DE TURNO, no al daño. Un golpe puede fallar
      // —«Guerrero Tanque» ataca con 10+1d6 contra defensa 11, asi que un 1
      // en el dado no entra— y fallar es un resultado legitimo del combate:
      // exigir daño en cada turno pondria la prueba roja por una tirada. El
      // turno, en cambio, rota siempre: `AvanzarTurno` corre tras resolver la
      // accion, acierte o no. Lo que se afirma abajo es el recorrido de
      // colores a lo largo de la partida, que es lo que dice RF-JUE-009.
      await expect
        .poll(
          async () =>
            (
              await (
                await api.get(`/api/v1/partidas/${partida.id}`, {
                  headers: conToken(anfitriona.token),
                })
              ).json()
            ).turnoActual.numeroTurno,
          { timeout: 25000, message: `turno ${turnos + 1}: el turno no avanzo` },
        )
        .toBeGreaterThan(enCurso.turnoActual.numeroTurno);

      for (const barra of await barras(page)) {
        vistos.add(barra.estado);
        recorrido.push(`${barra.actual}/${barra.maxima}=${barra.estado}`);

        // Cada barra pintada, contra la regla, con el numero que le toco. Esto
        // es lo que de verdad demuestra RF-JUE-009 de punta a punta: el color
        // sale del dato que llego por STOMP, no de un valor de prueba.
        expect(
          barra.estado,
          `${barra.actual}/${barra.maxima} deberia ser ` +
            `${colorEsperado(barra.actual, barra.maxima)}`,
        ).toBe(colorEsperado(barra.actual, barra.maxima));
        // Y el numero se escribe junto al color: el color nunca es el unico
        // indicador (accesibilidad, RNF).
        expect(barra.texto).toBe(`${barra.actual}/${barra.maxima}`);
      }
      turnos += 1;
    }

    // La barra salio de verde con daño de verdad: si nadie hubiera acertado,
    // las dos seguirian a tope y esto no se cumpliria.
    expect(
      [...vistos].some((estado) => estado !== 'alto'),
      `recorrido en ${turnos} turnos: ${recorrido.join(' ')}`,
    ).toBe(true);
  });

  test('a fuerza de golpes alguien cae, y la vista lo dice', async ({ page }) => {
    // HU-JUE-005 / RF-JUE-017: el combate acaba de verdad. Se sigue golpeando
    // desde donde lo dejo la prueba anterior hasta que la partida cierre.
    test.setTimeout(240000);

    let enCurso = await (
      await api.get(`/api/v1/partidas/${partida.id}`, {
        headers: conToken(anfitriona.token),
      })
    ).json();

    let turnos = 0;
    while (enCurso.estado === 'EN_CURSO' && turnos < 40) {
      const esAnfitriona = enCurso.turnoActual.idJugador === anfitriona.claims.uid;
      const quien = esAnfitriona ? anfitriona : invitado;

      await conSesion(page, quien, esAnfitriona ? ANFITRION : INVITADO);
      await page.goto(`${BORDE}${VISTA}?sala=${sala.id}&partida=${partida.id}`);

      const boton = page.locator('[data-zona="acciones"] [data-atacar]').first();
      await expect(boton).toBeEnabled({ timeout: 20000 });
      await boton.click();

      // Se espera a que el servidor haya persistido el golpe, no a un tiempo
      // fijo. Vale cualquiera de las dos senales: que el turno rote, o que la
      // partida se cierre. Y hay que aceptar las dos porque el ULTIMO golpe no
      // rota nada: `AvanzarTurno` rechaza una partida ya terminada, asi que el
      // numero de turno se queda congelado justo en el golpe que la acaba
      // —que es precisamente el que esta prueba persigue—.
      const turnoPrevio = enCurso.turnoActual.numeroTurno;
      await expect
        .poll(
          async () => {
            enCurso = await (
              await api.get(`/api/v1/partidas/${partida.id}`, {
                headers: conToken(anfitriona.token),
              })
            ).json();
            return enCurso.estado !== 'EN_CURSO' || enCurso.turnoActual.numeroTurno > turnoPrevio;
          },
          { timeout: 25000, message: `turno ${turnos + 1}: ni rota el turno ni acaba` },
        )
        .toBe(true);

      turnos += 1;
    }

    expect(enCurso.estado, `no termino en ${turnos} turnos`).toBe('FINALIZADA');

    // Alguien quedo en cero: no es un final por abandono ni por tiempo.
    const caidos = enCurso.participantes.filter((p) => p.heroe.vidaActual === 0);
    expect(caidos.length).toBeGreaterThanOrEqual(1);
    // Y su barra es la roja.
    const barrasFinales = await barras(page);
    for (const caido of caidos) {
      expect(barrasFinales.find((b) => b.jugador === caido.jugador)?.estado).toBe('bajo');
    }

    // La vista de quien dio el ultimo golpe cuenta el desenlace y retira los
    // botones: el aviso `partida.finalizada` llego por el canal.
    await expect(page.locator('[data-zona="resultado"]')).toHaveText(
      /has ganado|has perdido|empate/i,
      { timeout: 20000 },
    );
    await expect(page.locator('[data-zona="acciones"]')).toBeHidden();

    // HU-JUE-014 CA-04: la apuesta se liquido en el libro REAL. El ganador
    // se lleva lo del otro y recupera lo suyo; el perdedor pierde lo
    // apostado; a nadie le queda nada reservado. Y la vista lo cuenta con
    // la cifra, que viajo en `reparto` del mismo aviso.
    const enPie = enCurso.participantes.filter((p) => p.heroe.vidaActual > 0);
    expect(enPie, 'este flujo termina con un ganador, no en empate').toHaveLength(1);
    const ganador = enPie[0].jugador === anfitriona.claims.uid ? anfitriona : invitado;
    const perdedor = ganador === anfitriona ? invitado : anfitriona;

    await expect
      .poll(async () => (await saldoDe(api, ganador.claims.uid)).reservado, {
        timeout: 20000,
        message: 'la reserva del ganador no se libero',
      })
      .toBe(reservadoAlEmpezar[ganador.claims.uid] - APUESTA);
    const delGanador = await saldoDe(api, ganador.claims.uid);
    const delPerdedor = await saldoDe(api, perdedor.claims.uid);
    // Relativo a lo que tenian al empezar la partida, no a la semilla: asi la
    // afirmacion vale tambien en un banco reutilizado de una corrida anterior.
    expect(delGanador.bruto).toBe(brutoAlEmpezar[ganador.claims.uid] + APUESTA);
    expect(delPerdedor.bruto).toBe(brutoAlEmpezar[perdedor.claims.uid] - APUESTA);
    // Al perdedor se le cobro la reserva (consumida), no se le devolvio: en
    // cualquiera de los dos casos deja de estar reservada.
    expect(delPerdedor.reservado).toBe(reservadoAlEmpezar[perdedor.claims.uid] - APUESTA);

    await expect(page.locator('[data-zona="resultado"]')).toHaveText(
      new RegExp(`(llevas|pierdes los) ${APUESTA} creditos`, 'i'),
      { timeout: 20000 },
    );
  });
});
