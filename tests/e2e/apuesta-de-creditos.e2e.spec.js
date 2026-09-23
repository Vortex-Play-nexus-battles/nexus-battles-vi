/**
 * HU-JUE-014 · Apuesta de creditos en la batalla — lo que no cabe en el
 * flujo principal de `sala-de-batalla.e2e.spec.js` (que ya cubre CA-01 al
 * crear y entrar, y CA-04 al terminar con ganador).
 *
 * Aqui, por la API y contra el libro de creditos REAL (ms-finanzas):
 *   - CA-02: sin saldo, 422 `creditos-insuficientes` con las cifras, y la
 *     sala no cambia.
 *   - CA-03: quien abandona antes de empezar recupera su reserva; al
 *     cancelar la sala, la recuperan todos.
 *   - CA-05: una sala sin recompensa no toca el libro.
 *
 * CA-06 (el libro caido) se prueba a nivel de servicio: tumbar ms-finanzas a
 * mitad de un E2E compartido dejaria el resto del banco en un estado que no
 * es el de ningun jugador.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const FINANZAS = process.env.E2E_FINANZAS ?? 'http://localhost:8093/api/v1';
const ANFITRION = process.env.E2E_ANFITRION ?? 'anfitriona_e2e';
const INVITADO = process.env.E2E_INVITADO ?? 'invitado_e2e';
const POBRE = process.env.E2E_POBRE ?? 'pobre_e2e';
const CLAVE = 'Contrasena-E2E-2026';
const APUESTA = 120;

function cuerpoDelToken(jwt) {
  const base64 = jwt.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
  return JSON.parse(Buffer.from(base64, 'base64').toString('utf8'));
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
  return { ...cuerpo, claims: cuerpoDelToken(cuerpo.token) };
}

function conToken(token) {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

async function saldoDe(api, quien) {
  // Desde #455 el saldo es del propio jugador: se consulta con SU token (un
  // servicio ve el de cualquiera; un usuario, solo el suyo).
  const uid = quien.claims.uid;
  const r = await api.get(`${FINANZAS}/creditos/${uid}/saldo`, { headers: conToken(quien.token) });
  expect(r.status(), `saldo de ${uid}: ${await r.text()}`).toBe(200);
  const s = await r.json();
  return {
    bruto: Number(s.saldoBruto),
    reservado: Number(s.saldoReservado),
    disponible: Number(s.saldoDisponible),
  };
}

async function crearSala(api, quien, recompensaCreditos) {
  const r = await api.post('/api/v1/salas', {
    headers: conToken(quien.token),
    data: { maximoParticipantes: 4, modalidad: 'HASTA_SEIS', recompensaCreditos, privada: false },
  });
  expect(r.status(), `crear sala: ${await r.text()}`).toBe(201);
  return r.json();
}

async function salaActual(api, quien, id) {
  const r = await api.get(`/api/v1/salas/${id}`, { headers: conToken(quien.token) });
  expect(r.status()).toBe(200);
  return r.json();
}

/**
 * Crea la sala, corre el cuerpo y la cancela PASE LO QUE PASE. Si una
 * afirmacion falla a medias, la reserva de la anfitriona vuelve igual: una
 * sala huerfana aqui dejaria creditos comprometidos que las demas pruebas
 * verian como saldo reservado ajeno.
 */
async function conSala(api, quien, recompensaCreditos, cuerpo) {
  const sala = await crearSala(api, quien, recompensaCreditos);
  try {
    await cuerpo(sala);
  } finally {
    await api.delete(`/api/v1/salas/${sala.id}`, { headers: conToken(quien.token) });
  }
}

test.describe('Apuesta de creditos (HU-JUE-014)', () => {
  test.describe.configure({ mode: 'serial' });

  let api;
  let anfitriona;
  let invitado;
  let pobre;

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    anfitriona = await sesionDe(api, ANFITRION);
    invitado = await sesionDe(api, INVITADO);
    pobre = await sesionDe(api, POBRE);
  });

  test.afterAll(async () => {
    await api?.dispose();
  });

  test('#455: el libro de creditos no se mueve con el token de un jugador (ni el suyo ni el de otro)', async () => {
    // Suplantacion directa contra ms-finanzas, saltandose la sala: antes de
    // R0 esto respondia 200 y el jugador se regalaba creditos.
    const autoregalo = await api.post(`${FINANZAS}/creditos/acreditar`, {
      headers: conToken(pobre.token),
      data: {
        uid: pobre.claims.uid,
        monto: 999999,
        refId: `autoregalo-${Date.now()}`,
        concepto: 'suplantacion',
      },
    });
    expect(autoregalo.status(), await autoregalo.text()).toBe(403);
    expect((await saldoDe(api, pobre)).disponible).toBe(0);

    const reservaAjena = await api.post(`${FINANZAS}/creditos/reservar`, {
      headers: { ...conToken(pobre.token), 'Idempotency-Key': `robo-${Date.now()}` },
      data: {
        jugadorUid: anfitriona.claims.uid,
        monto: 50,
        concepto: 'reserva ajena',
        referenciaId: 'x',
      },
    });
    expect(reservaAjena.status()).toBe(403);

    const resultadoInventado = await api.post(`${FINANZAS}/partidas/resultado`, {
      headers: conToken(pobre.token),
      data: {
        partidaId: `inventada-${Date.now()}`,
        tipoPartida: 'UNO_A_UNO',
        ganadorUid: pobre.claims.uid,
        participantes: [{ uid: pobre.claims.uid, sancionado: false }],
      },
    });
    expect(resultadoInventado.status()).toBe(403);

    const saldoAjeno = await api.get(`${FINANZAS}/creditos/${anfitriona.claims.uid}/saldo`, {
      headers: conToken(pobre.token),
    });
    expect(saldoAjeno.status()).toBe(403);

    const sinToken = await api.get(`${FINANZAS}/creditos/${pobre.claims.uid}/saldo`);
    expect(sinToken.status()).toBe(401);
  });

  test('CA-02: sin saldo no se entra; el 422 dice cuanto hay y cuanto falta, y la sala no cambia', async () => {
    await conSala(api, anfitriona, APUESTA, async (sala) => {
      // `pobre_e2e` tiene heroe (lo siembra sembrar.sh) y CERO creditos: si
      // no tuviera heroe, la puerta de heroe lo pararia antes con otro 422.
      expect((await saldoDe(api, pobre)).disponible).toBe(0);

      const r = await api.post(`/api/v1/salas/${sala.id}/participantes`, {
        headers: conToken(pobre.token),
      });

      expect(r.status(), `ingreso sin saldo: ${await r.text()}`).toBe(422);
      const problema = await r.json();
      expect(problema.type).toBe('https://nexusbattles.local/errores/creditos-insuficientes');
      expect(problema.detail).toMatch(new RegExp(`Tienes 0 creditos y necesitas ${APUESTA}`));
      expect((await salaActual(api, anfitriona, sala.id)).ocupacion).toBe(1);
      expect((await saldoDe(api, pobre)).reservado).toBe(0);
    });
  });

  test('CA-03: quien abandona antes de empezar recupera su reserva, y al volver a entrar reserva de nuevo', async () => {
    await conSala(api, anfitriona, APUESTA, async (sala) => {
      const antes = await saldoDe(api, invitado);

      const entrada = await api.post(`/api/v1/salas/${sala.id}/participantes`, {
        headers: conToken(invitado.token),
      });
      expect(entrada.status(), await entrada.text()).toBe(200);
      expect((await saldoDe(api, invitado)).reservado).toBe(antes.reservado + APUESTA);

      const salida = await api.delete(`/api/v1/salas/${sala.id}/participantes`, {
        headers: conToken(invitado.token),
      });
      expect(salida.status(), await salida.text()).toBe(204);
      const tras = await saldoDe(api, invitado);
      expect(tras.reservado).toBe(antes.reservado);
      expect(tras.disponible).toBe(antes.disponible);
      expect((await salaActual(api, anfitriona, sala.id)).ocupacion).toBe(1);

      // Vuelve a entrar: reserva NUEVA, no la que ya se le devolvio.
      const otraVez = await api.post(`/api/v1/salas/${sala.id}/participantes`, {
        headers: conToken(invitado.token),
      });
      expect(otraVez.status(), await otraVez.text()).toBe(200);
      expect((await saldoDe(api, invitado)).reservado).toBe(antes.reservado + APUESTA);
    });
  });

  test('CA-03: cancelar la sala devuelve la reserva de TODOS los participantes', async () => {
    const deAnfitriona = await saldoDe(api, anfitriona);
    const deInvitado = await saldoDe(api, invitado);
    const sala = await crearSala(api, anfitriona, APUESTA);
    const entrada = await api.post(`/api/v1/salas/${sala.id}/participantes`, {
      headers: conToken(invitado.token),
    });
    expect(entrada.status(), await entrada.text()).toBe(200);
    expect((await saldoDe(api, anfitriona)).reservado).toBe(deAnfitriona.reservado + APUESTA);
    expect((await saldoDe(api, invitado)).reservado).toBe(deInvitado.reservado + APUESTA);

    const cancelacion = await api.delete(`/api/v1/salas/${sala.id}`, {
      headers: conToken(anfitriona.token),
    });
    expect(cancelacion.status(), await cancelacion.text()).toBe(204);

    expect((await saldoDe(api, anfitriona)).reservado).toBe(deAnfitriona.reservado);
    expect((await saldoDe(api, invitado)).reservado).toBe(deInvitado.reservado);
    expect((await salaActual(api, anfitriona, sala.id)).estado).toBe('CANCELADA');
  });

  test('CA-05: una sala sin recompensa no toca el libro de creditos', async () => {
    const antes = await saldoDe(api, anfitriona);
    await conSala(api, anfitriona, 0, async (sala) => {
      const entrada = await api.post(`/api/v1/salas/${sala.id}/participantes`, {
        headers: conToken(invitado.token),
      });
      expect(entrada.status(), await entrada.text()).toBe(200);

      expect(await saldoDe(api, anfitriona)).toEqual(antes);
      // Y el pobre, sin un credito, entra igual: no hay nada que reservar.
      const elPobre = await api.post(`/api/v1/salas/${sala.id}/participantes`, {
        headers: conToken(pobre.token),
      });
      expect(elPobre.status(), await elPobre.text()).toBe(200);
    });
  });
});
