/**
 * Revisión del modo jugador del 6-oct — salas-partidas 1.10.0 contra los
 * servicios de verdad (puntos 8, 13 y 18).
 *
 *   - Al entrar a «Jugar online» se ven las salas a las que se puede entrar;
 *     una sala contra la IA (nace llena) solo sale con «Todos».
 *   - El anfitrión invita buscando por apodo; al invitado le llega el aviso
 *     a su bandeja, y una segunda invitación no lo repite.
 *   - «Salir» en pleno combate pregunta con lo que cuesta y, al confirmar,
 *     te rinde: la partida termina con el rival como ganador (lo decide el
 *     servidor, no la vista).
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import { sesionDe as sesionDelBanco } from './ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const ANFITRION = process.env.E2E_ANFITRION ?? 'anfitriona_e2e';
const INVITADO = process.env.E2E_INVITADO ?? 'invitado_e2e';
const CLAVE = 'Contrasena-E2E-2026';
const VISTAS = '/frontend/app-web/src/plataforma/salas-partidas';
const EN_EL_LISTADO = /\/jugar(?:[?#]|$)|batallas\.html/;

function sesionDe(api, apodo) {
  return sesionDelBanco(api, apodo, { clave: CLAVE, base: BORDE });
}

function conToken(token) {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

async function conSesion(page, jugador, apodo) {
  await page.addInitScript(
    ([token, nombre]) => {
      sessionStorage.setItem('nexus.token', token);
      sessionStorage.setItem('nexus.apodoActual', nombre);
    },
    [jugador.token, apodo],
  );
}

async function crearSala(api, quien, datos) {
  const creada = await api.post('/api/v1/salas', {
    headers: conToken(quien.token),
    data: { recompensaCreditos: 0, privada: false, ...datos },
  });
  expect(creada.status(), `crear sala: ${await creada.text()}`).toBe(201);
  return creada.json();
}

async function cancelar(api, quien, idSala) {
  await api.delete(`/api/v1/salas/${idSala}`, { headers: conToken(quien.token) });
}

test.describe('Salas 1.10.0 · disponibles, invitar y rendirse (revisión del 6-oct)', () => {
  test.describe.configure({ mode: 'serial' });

  let api;
  let anfitriona;
  let invitado;

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    anfitriona = await sesionDe(api, ANFITRION);
    invitado = await sesionDe(api, INVITADO);
  });

  test.afterAll(async () => {
    await api?.dispose();
  });

  test('punto 8 · por omisión, las salas a las que se puede entrar; la de la IA, con «Todos»', async ({
    page,
  }) => {
    const contraLaIa = await crearSala(api, anfitriona, {
      modalidad: 'CONTRA_IA',
      maximoParticipantes: 2,
      heroesIA: 1,
    });
    expect(contraLaIa.estado, 'una sala contra la IA nace llena').toBe('LLENA');
    try {
      // El servicio: «disponibles» son abiertas y privadas, la más reciente primero.
      const disponibles = await api.get('/api/v1/salas?estado=ABIERTA,PRIVADA&tamano=50', {
        headers: conToken(invitado.token),
      });
      expect(disponibles.status()).toBe(200);
      const { contenido } = await disponibles.json();
      expect(contenido.map((s) => s.id)).not.toContain(contraLaIa.id);
      expect(contenido.every((s) => ['ABIERTA', 'PRIVADA'].includes(s.estado))).toBe(true);

      // La vista.
      await conSesion(page, invitado, INVITADO);
      await page.goto(`${BORDE}${VISTAS}/batallas.html`);
      const subtitulo = page.locator('[data-zona="subtitulo"]');
      await expect(subtitulo).not.toHaveText('Buscando batallas', { timeout: 20_000 });
      await expect(page.locator(`[data-sala="${contraLaIa.id}"]`)).toHaveCount(0);
      await expect(page.locator('[data-zona="canal"]')).toBeHidden();
      await expect(page.locator('body')).not.toContainText('Canal en tiempo real conectado');

      await page.locator('[name="estado"]').selectOption('TODOS');
      await expect(page.locator(`[data-sala="${contraLaIa.id}"]`)).toBeVisible({ timeout: 20_000 });
    } finally {
      await cancelar(api, anfitriona, contraLaIa.id);
    }
  });

  test('punto 13 · el anfitrión invita por apodo y al invitado le llega el aviso, una sola vez', async ({
    page,
  }) => {
    const sala = await crearSala(api, anfitriona, {
      modalidad: 'UNO_CONTRA_UNO',
      maximoParticipantes: 2,
    });
    try {
      await conSesion(page, anfitriona, ANFITRION);
      await page.goto(`${BORDE}${VISTAS}/sala-batalla.html?sala=${sala.id}`);

      // La sala de espera: tres zonas, sin la tarjeta genérica de antes.
      const tarjeta = page.locator('[data-zona="sala-espera"]');
      await expect(tarjeta).toBeVisible({ timeout: 20_000 });
      await expect(page.locator('[data-zona="contador-plazas"]')).toHaveText('1 / 2');
      await expect(page.locator('[data-plaza="libre"]')).toHaveCount(1);
      await expect(page.locator('[data-zona="sin-partida"]')).toBeHidden();
      await expect(tarjeta).not.toContainText('«');

      await page.locator('[data-accion="abrir-invitar"]').click();
      await page.locator('#buscar-invitado').fill(INVITADO.slice(0, 6));
      const invitar = page.getByRole('button', { name: `Invitar a ${INVITADO}`, exact: true });
      await expect(invitar).toBeVisible({ timeout: 20_000 });
      await invitar.click();
      await expect(page.locator('[data-zona="acuse-invitacion"]')).toHaveText(
        `Invitación enviada a ${INVITADO}.`,
        { timeout: 20_000 },
      );
      // En pantalla no hay identificadores: solo apodos.
      await expect(page.locator('[data-zona="panel-invitar"]')).not.toContainText(
        invitado.claims.uid,
      );

      // Al invitado le llega el aviso, con la sala en su id.
      const idDelAviso = `sala:${sala.id}:invitacion:${invitado.claims.uid}`;
      const bandeja = `/api/v1/users/${invitado.claims.uid}/notifications`;
      const aviso = async () => {
        const respuesta = await api.get(bandeja, { headers: conToken(invitado.token) });
        if (respuesta.status() !== 200) {
          return null;
        }
        const { avisos } = await respuesta.json();
        return avisos.find((a) => a.id === idDelAviso) ?? null;
      };
      await expect
        .poll(aviso, { timeout: 20_000, message: 'el aviso INVITACION_SALA en la bandeja' })
        .toMatchObject({ tipo: 'INVITACION_SALA' });
      expect((await aviso()).titulo).toContain(ANFITRION);

      // Invitar otra vez no manda otro aviso.
      const otraVez = await api.post(`/api/v1/salas/${sala.id}/invitaciones`, {
        headers: conToken(anfitriona.token),
        data: { idJugador: invitado.claims.uid },
      });
      expect(otraVez.status(), await otraVez.text()).toBe(200);
      expect((await otraVez.json()).enviada).toBe(false);

      // Solo invita el anfitrión.
      const ajeno = await api.post(`/api/v1/salas/${sala.id}/invitaciones`, {
        headers: conToken(invitado.token),
        data: { idJugador: anfitriona.claims.uid },
      });
      expect(ajeno.status()).toBe(403);
    } finally {
      await cancelar(api, anfitriona, sala.id);
    }
  });

  test('punto 18 · «Salir» en pleno combate pregunta y, al confirmar, te rinde: gana el rival', async ({
    page,
  }) => {
    const sala = await crearSala(api, anfitriona, {
      modalidad: 'UNO_CONTRA_UNO',
      maximoParticipantes: 2,
    });
    const entrada = await api.post(`/api/v1/salas/${sala.id}/participantes`, {
      headers: conToken(invitado.token),
    });
    expect(entrada.status(), `entrar: ${await entrada.text()}`).toBe(200);
    const inicio = await api.post(`/api/v1/salas/${sala.id}/partida`, {
      headers: conToken(anfitriona.token),
    });
    expect(inicio.status(), `iniciar: ${await inicio.text()}`).toBe(201);
    const partida = await inicio.json();

    await conSesion(page, invitado, INVITADO);
    await page.goto(`${BORDE}${VISTAS}/sala-batalla.html?sala=${sala.id}&partida=${partida.id}`);
    await expect(page.locator('[data-barra-vida]')).toHaveCount(2, { timeout: 20_000 });

    await page.locator('[data-zona="salir"]').click();
    const dialogo = page.getByRole('dialog', { name: '¿Abandonar la batalla?' });
    await expect(dialogo).toBeVisible();
    await expect(dialogo).toContainText(
      'Si abandonas la batalla se contará como derrota. Tu rival será declarado ganador',
    );
    // «Seguir jugando» no toca nada.
    await dialogo.getByRole('button', { name: 'Seguir jugando' }).click();
    await expect(dialogo).toBeHidden();
    const sigue = await api.get(`/api/v1/partidas/${partida.id}`, {
      headers: conToken(anfitriona.token),
    });
    expect((await sigue.json()).estado).toBe('EN_CURSO');

    await page.locator('[data-zona="salir"]').click();
    await page
      .getByRole('dialog', { name: '¿Abandonar la batalla?' })
      .getByRole('button', { name: 'Abandonar batalla' })
      .click();
    await page.waitForURL(EN_EL_LISTADO, { timeout: 20_000 });
    await expect(page.locator('[data-zona="aviso-sala"]')).toContainText('Abandonaste la batalla');

    // El servidor decidió: terminada, y gana quien siguió en pie.
    const final = await api.get(`/api/v1/partidas/${partida.id}`, {
      headers: conToken(anfitriona.token),
    });
    expect(final.status()).toBe(200);
    const terminada = await final.json();
    expect(terminada.estado).toBe('FINALIZADA');
    expect(terminada.resultado).toBe('GANADOR');
    expect(terminada.ganadores).toContain(anfitriona.claims.uid);

    // Rendirse otra vez no cambia nada.
    const otraVez = await api.post(`/api/v1/partidas/${partida.id}/rendicion`, {
      headers: conToken(invitado.token),
    });
    expect(otraVez.status()).toBe(200);
    expect((await otraVez.json()).estado).toBe('FINALIZADA');
  });
});
