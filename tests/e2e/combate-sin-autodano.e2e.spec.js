/**
 * Auditoría del 4-oct — «cuando yo ataco, también me hago daño a mí mismo».
 *
 * Contra el motor de combate real del banco: un jugador recién creado juega
 * contra la IA y se escucha el canal de la partida tal cual llega al
 * navegador. En cada aviso `partida.accion.resuelta`:
 *
 *   - un ataque propio nunca apunta a quien lo lanza (`idObjetivo`);
 *   - la vida de quien ataca no baja con su propio golpe, salvo un `REFLEJO`
 *     contractual (Pinchos de escudo, Toma y lleva) que el aviso declara;
 *   - el daño que recibe el jugador lo causa otro (`causas[].origen`).
 *
 * Y en el registro de la vista: nunca «X (tú) golpea a X (tú)», y la máquina
 * se nombra con «(IA)».
 */
import { test, expect, request as apiRequest } from '@playwright/test';

import { sesionDe as sesionDelBanco } from './ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const CLAVE = 'Contrasena-E2E-2026';
const SUFIJO = Date.now().toString(36);
const SALA = '/frontend/app-web/src/plataforma/salas-partidas/sala-batalla.html';
const MAXIMO_DE_GOLPES = 12;

function conToken(token) {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
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

test.describe('Combate contra la IA sin auto-daño (auditoría del 4-oct)', () => {
  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let jugador;

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    jugador = await sesionDelBanco(api, `sin_autodano_${SUFIJO}`, { clave: CLAVE, base: BORDE });
    await expect
      .poll(
        async () => {
          const r = await api.get('/api/v1/auth/onboarding', { headers: conToken(jugador.token) });
          return r.ok() ? (await r.json()).estado : `HTTP ${r.status()}`;
        },
        { timeout: 60_000, message: 'el alta del jugador no terminó (héroe equipado)' },
      )
      .toBe('COMPLETO');
  });

  test.afterAll(async () => {
    await api?.dispose();
  });

  test('atacar baja la vida del objetivo y nunca la propia; la máquina se nombra «(IA)»', async ({
    page,
  }) => {
    test.setTimeout(300_000);
    const yo = jugador.claims.uid;
    const creada = await api.post('/api/v1/salas', {
      headers: conToken(jugador.token),
      data: { modalidad: 'CONTRA_IA', maximoParticipantes: 2, recompensaCreditos: 0, heroesIA: 1 },
    });
    expect(creada.status(), await creada.text()).toBe(201);
    const sala = await creada.json();
    const inicio = await api.post(`/api/v1/salas/${sala.id}/partida`, {
      headers: conToken(jugador.token),
    });
    expect(inicio.status(), await inicio.text()).toBe(201);
    let partida = await inicio.json();

    // Lo que llega por el canal, tal cual.
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

    await page.addInitScript(
      ([token, apodo, uid]) => {
        sessionStorage.setItem('nexus.token', token);
        sessionStorage.setItem('nexus.apodoActual', apodo);
        sessionStorage.setItem('nexus.rolActual', 'JUGADOR');
        sessionStorage.setItem('nexus.usuarioId', uid);
      },
      [jugador.token, jugador.apodo, yo],
    );
    await page.goto(`${BORDE}${SALA}?sala=${sala.id}&partida=${partida.id}`);

    const deLaPartida = async () => {
      const r = await api.get(`/api/v1/partidas/${partida.id}`, { headers: conToken(jugador.token) });
      expect(r.status(), await r.text()).toBe(200);
      return r.json();
    };
    let golpes = 0;
    while (partida.estado === 'EN_CURSO' && golpes < MAXIMO_DE_GOLPES) {
      await expect
        .poll(
          async () => {
            partida = await deLaPartida();
            return partida.estado !== 'EN_CURSO' || partida.turnoActual.idJugador === yo;
          },
          { timeout: 25_000, message: `golpe ${golpes + 1}: la máquina no devuelve el turno` },
        )
        .toBe(true);
      if (partida.estado !== 'EN_CURSO') {
        break;
      }
      const boton = page.locator('[data-zona="acciones"] [data-atacar]').first();
      await expect(boton).toBeEnabled({ timeout: 20_000 });
      const turnoPrevio = partida.turnoActual.numeroTurno;
      await boton.click({ timeout: 5_000 });
      await expect
        .poll(
          async () => {
            partida = await deLaPartida();
            return partida.estado !== 'EN_CURSO' || partida.turnoActual.numeroTurno > turnoPrevio;
          },
          { timeout: 25_000, message: `golpe ${golpes + 1}: ni rota el turno ni acaba` },
        )
        .toBe(true);
      golpes += 1;
    }
    expect(golpes, 'se jugó al menos un golpe').toBeGreaterThan(0);

    // El último aviso puede ir con la pausa de la vista: se espera a tenerlos todos.
    await expect.poll(() => avisos.filter((a) => a.idEjecutor === yo).length).toBeGreaterThan(0);

    const propios = avisos.filter(
      (a) => a.idEjecutor === yo && a.accion?.codigo !== 'EFECTO_POR_TURNO',
    );
    const autoDano = propios.filter((a) => {
      const mio = (a.afectados ?? []).find((x) => x.idJugador === yo);
      const reflejo = (mio?.causas ?? []).some((c) => c.tipo === 'REFLEJO');
      return mio && mio.diferencia < 0 && !reflejo;
    });
    expect(autoDano, 'ningún golpe propio le quita vida a quien lo lanza').toEqual([]);
    for (const aviso of propios) {
      expect(aviso.idObjetivo, 'un golpe propio nunca apunta a quien lo lanza').not.toBe(yo);
    }

    // El daño que recibe el jugador fuera de su golpe lo causa otro.
    const ajenos = avisos.filter(
      (a) => a.idEjecutor !== yo && a.accion?.codigo !== 'EFECTO_POR_TURNO',
    );
    for (const aviso of ajenos) {
      const mio = (aviso.afectados ?? []).find((x) => x.idJugador === yo);
      if (mio && mio.diferencia < 0) {
        expect(
          (mio.causas ?? []).every((c) => c.origen !== yo),
          'el daño de la máquina no se le atribuye al jugador',
        ).toBe(true);
      }
    }

    // El registro de la vista lo cuenta igual.
    const registro = page.locator('[data-zona="registro"]');
    await expect(registro).toContainText('(IA)', { timeout: 10_000 });
    await expect(registro).not.toContainText(/\(tú\) golpea a [^.]*\(tú\)/);
  });
});
