// HU-JUE-012 · Economía de créditos por partidas (Santiago Sanabria)
import { test } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { CORRIDA, VISTA, entrar, expect, foto, fotoHttp, fotoTexto, llamar, sesion, sql, tokenServicio } from './ayudante-qa.js';

const FINANZAS = 'http://localhost:8093/api/v1';
const saldo = async (api, s) => Number((await llamar(api, 'GET', `${FINANZAS}/creditos/${s.claims.uid}/saldo`, { token: s.token })).cuerpo.saldoDisponible);

async function informar(api, servicio, { tipoPartida, ganadores, participantes }) {
  return llamar(api, 'POST', `${FINANZAS}/partidas/resultado`, {
    token: servicio,
    data: { partidaId: randomUUID(), tipoPartida, ganadoresUid: ganadores.map((x) => x.claims.uid), participantes: participantes.map(([x, sancionado = false]) => ({ uid: x.claims.uid, sancionado })) },
  });
}

test.describe('HU-JUE-012 · Economía de créditos por partidas', () => {
  test('CP-JUE-012-01 · Partida 1v1 contra la IA jugada desde la vista: +2 al ganar o +1 por participar', async ({ page, request }) => {
    test.setTimeout(480_000);
    const s = await sesion(request, `batalla${CORRIDA}`);
    const antes = await saldo(request, s);
    const sala = (await llamar(request, 'POST', '/api/v1/salas', { token: s.token, data: { modalidad: 'CONTRA_IA', maximoParticipantes: 2, recompensaCreditos: 0, heroesIA: 1 } })).cuerpo;
    let partida = (await llamar(request, 'POST', `/api/v1/salas/${sala.id}/partida`, { token: s.token })).cuerpo;
    await entrar(page, s.apodo);
    await page.goto(VISTA(`plataforma/salas-partidas/sala-batalla.html?sala=${sala.id}&partida=${partida.id}`));
    await expect(page.locator('[data-zona="acciones"]')).toBeVisible();
    await foto(page, 'CP-JUE-012-01', `Partida 1v1 contra la IA en curso (saldo antes: ${antes} créditos)`);
    const leer = async () => (await llamar(request, 'GET', `/api/v1/partidas/${partida.id}`, { token: s.token })).cuerpo;
    for (let golpes = 0; partida.estado === 'EN_CURSO' && golpes < 150; golpes += 1) {
      await expect.poll(async () => { partida = await leer(); return partida.estado !== 'EN_CURSO' || partida.turnoActual.idJugador === s.claims.uid; }, { timeout: 25_000 }).toBe(true);
      if (partida.estado !== 'EN_CURSO') break;
      const previo = partida.turnoActual.numeroTurno;
      await page.locator('[data-zona="acciones"] [data-atacar]').first().click({ timeout: 10_000 }).catch(() => {});
      await expect.poll(async () => { partida = await leer(); return partida.estado !== 'EN_CURSO' || partida.turnoActual.numeroTurno > previo; }, { timeout: 25_000 }).toBe(true);
    }
    expect(partida.estado).toBe('FINALIZADA');
    const gano = partida.participantes.find((p) => !p.esIA).heroe.vidaActual > 0;
    const esperado = gano ? 2 : 1;
    const resultado = page.locator('[data-zona="resultado"]');
    await expect(resultado).toContainText(gano ? '+2 créditos' : '+1 crédito', { timeout: 20_000 });
    await expect.poll(async () => (await saldo(request, s)) - antes, { timeout: 20_000 }).toBe(esperado);
    await foto(page, 'CP-JUE-012-01', `Fin de la partida: ${gano ? 'GANÓ' : 'PERDIÓ'} → la vista anuncia la recompensa y el saldo sube ${esperado} (de ${antes} a ${antes + esperado})`, { resaltar: resultado });
  });

  test('CP-JUE-012-02 · Partida grupal: el ganador recibe 4 créditos y los demás 1 por participar', async ({ page, request }) => {
    const servicio = await tokenServicio(request);
    const [a, b, c] = [await sesion(request, `grupoa${CORRIDA}`), await sesion(request, `grupob${CORRIDA}`), await sesion(request, `grupoc${CORRIDA}`)];
    const antes = [await saldo(request, a), await saldo(request, b), await saldo(request, c)];
    const r = await informar(request, servicio, { tipoPartida: 'GRUPAL', ganadores: [a], participantes: [[a], [b], [c]] });
    const despues = [await saldo(request, a), await saldo(request, b), await saldo(request, c)];
    await page.goto('about:blank');
    await fotoHttp(page, 'CP-JUE-012-02', 'salas-partidas informa el fin de una partida GRUPAL (ganador A; B y C participan)', r, { nota: `Saldos: A ${antes[0]}→${despues[0]} · B ${antes[1]}→${despues[1]} · C ${antes[2]}→${despues[2]}` });
    expect(despues[0] - antes[0]).toBe(4);
    expect(despues[1] - antes[1]).toBe(1);
    expect(despues[2] - antes[2]).toBe(1);
    await entrar(page, a.apodo);
    await page.goto('/cuenta#historial');
    const fila = page.getByRole('row', { name: /Recompensa por ganar/ }).first();
    await expect(fila).toBeVisible();
    await foto(page, 'CP-JUE-012-02', 'Mi cuenta › Historial del ganador: movimiento de recompensa por victoria (+4)', { resaltar: fila });
  });

  test('CP-JUE-012-03 · Al acumular 20 créditos ganados se entrega un cofre; máximo 2 por semana', async ({ page, request }) => {
    const servicio = await tokenServicio(request);
    const s = await sesion(request, `cofre${CORRIDA}`);
    const victorias = [];
    for (let i = 0; i < 15; i += 1) victorias.push(await informar(request, servicio, { tipoPartida: 'GRUPAL', ganadores: [s], participantes: [[s]] }));
    const cofres = sql('finanzas', `SELECT count(*) FROM finanzas.cofre_entregado WHERE uid_jugador='${s.claims.uid}';`).trim();
    const contador = sql('finanzas', `SELECT 'semana='||semana_iso||' | creditos_ganados='||creditos_ganados||' | cofres_entregados='||cofres_entregados FROM finanzas.contador_semanal_cofre WHERE uid_jugador='${s.claims.uid}';`);
    await page.goto('about:blank');
    await fotoTexto(page, 'CP-JUE-012-03', '15 victorias grupales (60 créditos ganados en la semana): cofres entregados y contador semanal', 'finanzas.cofre_entregado / contador_semanal_cofre', `cofres entregados = ${cofres}\ncontador semanal: ${contador}`);
    expect(Number(cofres)).toBe(2);
    await entrar(page, s.apodo);
    await page.goto(VISTA('cuentas/mis-cofres.html'));
    const lista = page.locator('main');
    await expect(page.getByText(/cofre/i).first()).toBeVisible();
    await foto(page, 'CP-JUE-012-03', '«Mis cofres»: los 2 cofres de la semana con su recompensa aleatoria; no se entrega un tercero', { resaltar: lista, completa: true });
  });

  test('CP-JUE-012-04 · Un participante sancionado al finalizar la partida no recibe créditos', async ({ page, request }) => {
    const servicio = await tokenServicio(request);
    const ganador = await sesion(request, `limpio${CORRIDA}`);
    const sancionado = await sesion(request, `sancion${CORRIDA}`);
    const antes = [await saldo(request, ganador), await saldo(request, sancionado)];
    const r = await informar(request, servicio, { tipoPartida: 'UNO_A_UNO', ganadores: [ganador], participantes: [[ganador], [sancionado, true]] });
    const despues = [await saldo(request, ganador), await saldo(request, sancionado)];
    await page.goto('about:blank');
    await fotoHttp(page, 'CP-JUE-012-04', 'Resultado 1v1 con un participante sancionado al finalizar', r, { nota: `Ganador ${antes[0]}→${despues[0]} (+2) · Sancionado ${antes[1]}→${despues[1]} (sin acreditación)` });
    expect(despues[0] - antes[0]).toBe(2);
    expect(despues[1] - antes[1]).toBe(0);
  });
});
