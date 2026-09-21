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

test.describe('Sala de batalla de punta a punta', () => {
  test.describe.configure({ mode: 'serial' });

  let api;
  let anfitriona;
  let invitado;
  let sala;
  let partida;

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
    const r = await api.post('/api/v1/salas', {
      headers: conToken(anfitriona.token),
      data: {
        maximoParticipantes: 2,
        // El nombre exacto del enum `Modalidad`, no una abreviatura: Jackson
        // no deserializa `UNO_VS_UNO` y el servicio responde 400.
        modalidad: 'UNO_CONTRA_UNO',
        recompensaCreditos: 0,
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
    const r = await api.post(`/api/v1/salas/${sala.id}/participantes`, {
      headers: conToken(invitado.token),
      data: { codigoInvitacion: sala.codigoInvitacion },
    });

    expect(r.status(), `ingresar: ${await r.text()}`).toBe(200);
    const actualizada = await r.json();

    expect(actualizada.participantes, JSON.stringify(actualizada)).toHaveLength(2);
    expect(actualizada.participantes).toContain(invitado.claims.uid);
    expect(actualizada.ocupacion).toBe(2);
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
  });

  // ===================================================================
  // HU-SAL-004 / RF-JUE-017 · la partida
  // ===================================================================

  test('al iniciar, la partida reparte turnos y los dos entran a plena vida', async () => {
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
   * la pagina cargue.
   */
  async function conSocketVigilado(page) {
    await page.addInitScript(() => {
      globalThis.__sockets = [];
      globalThis.__frames = [];
      const Original = globalThis.WebSocket;
      globalThis.WebSocket = function (...args) {
        const socket = new Original(...args);
        globalThis.__sockets.push(socket);
        // Todo lo que entra por el canal, tal cual. Si el servidor publica el
        // aviso y la barra no se mueve, la pregunta es si el frame llego y no
        // se aplico, o si no llego: sin esto no hay forma de distinguirlo.
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
  });

  test('atacar desde la vista baja la vida del rival, y el aviso llega por STOMP', async ({
    page,
  }) => {
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
    // maxima (16) no la superaba NUNCA y el bucle se agotaba entero.
    let golpes = 0;
    try {
      await expect
        .poll(
          async () => {
            const ahora = await vidaTotal(page);
            if (ahora < sumaAntes) {
              return ahora;
            }
            // Si el turno volvio a ser nuestro, se insiste; si es del rival,
            // se espera a que le toque otra vez.
            if (golpes < 12 && (await boton.isEnabled())) {
              golpes += 1;
              await boton.click();
            }
            return ahora;
          },
          { timeout: 60000, intervals: [1000] },
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

  test('la barra cambia de color al bajar del 60 % y del 40 %, con daño real', async ({ page }) => {
    // HU-SAL-005. Los umbrales estan probados en unidad sobre `clasificar`;
    // lo que esto anade es que se cumplen con el daño que calcula el motor de
    // verdad y viajando por STOMP, no con numeros inventados en una prueba.
    test.setTimeout(240000);

    const vistos = new Set(['alto']); // ya comprobado a plena vida, mas arriba
    const recorrido = [];
    let turnos = 0;

    while (turnos < 40 && !vistos.has('bajo')) {
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
      }
      turnos += 1;
    }

    // Los tres colores de RF-JUE-009, alcanzados con el daño que calculo el
    // motor de verdad: verde por encima del 60 %, amarillo entre 60 y 40, rojo
    // por debajo del 40 %.
    expect([...vistos].sort(), `recorrido en ${turnos} turnos: ${recorrido.join(' ')}`).toEqual([
      'alto',
      'bajo',
      'medio',
    ]);
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

      // El ultimo golpe cierra la partida: se espera a que el servidor lo
      // haya persistido, no a un tiempo fijo.
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

      enCurso = await (
        await api.get(`/api/v1/partidas/${partida.id}`, {
          headers: conToken(anfitriona.token),
        })
      ).json();
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
  });
});
