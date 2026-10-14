// @ts-check
/**
 * Causales, listado global, plazo del panel y baneo definitivo — SA-4
 * (§7.3.2 y §7.3.7; moderacion-sanciones-admin.yaml 1.4.0), por el borde y
 * con los servicios reales del banco: moderacion-sanciones, notificaciones y
 * ms-identidad.
 *
 *   1. una sancion se emite con una de las ocho causales de §7.3.2, que
 *      vuelve en la respuesta; una causal que no es de las ocho es 400.
 *   2. el listado global (`GET /sanciones`) filtra por cuenta, causal, tipo
 *      y estado, pagina de 16 en 16 y es solo de quien modera (403 a una
 *      jugadora; 400 a un filtro fuera de su enumerado).
 *   3. apelar devuelve `decisionAntesDe` —el final del decimoquinto dia habil
 *      (§7.3.2)— y deja en la bandeja el aviso APELACION_RECIBIDA; el panel
 *      la ve en «pendientes» y en «todas», no en «resueltas».
 *   4. el baneo es definitivo: levantarlo a mano es 409
 *      `baneo-solo-por-apelacion`, tambien para el Super Administrador, y
 *      sigue restringiendo.
 *
 * Cuentas nuevas en cada corrida: la del baneo queda inhabilitada y no se
 * puede reutilizar, y la de la apelacion no debe chocar con las de
 * `sanciones.e2e.spec.js`.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import { cuentaAdministrativaDeRol, sesionDe as sesionDelBanco } from './ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const MODERADORA = process.env.E2E_MODERADORA ?? 'moderadora_e2e';
const ADMIN = process.env.E2E_ADMIN ?? 'admin_e2e';
const SUPER = process.env.E2E_SUPER ?? cuentaAdministrativaDeRol('SUPER_ADMINISTRADOR')?.apodo;
const CLAVE = 'Contrasena-E2E-2026';
const SUFIJO = Date.now().toString(36);
const APELANTE = `causal_${SUFIJO}`;
const BANEADA = `baneo_${SUFIJO}`;
const DIA = 24 * 60 * 60 * 1000;

function sesionDe(api, apodo) {
  return sesionDelBanco(api, apodo, { clave: CLAVE, base: BORDE });
}

function conToken(token) {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

test.describe('Causales, listado y plazos de las sanciones (SA-4, §7.3.2)', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let moderadora;
  let admin;
  let apelante;
  let baneada;
  let advertencia;

  async function emitir(quien, cuerpo) {
    return api.post('/api/v1/sanciones', { headers: conToken(quien.token), data: cuerpo });
  }

  /** @param {Record<string, string>} filtros */
  async function listar(quien, filtros) {
    const consulta = new URLSearchParams(filtros).toString();
    return api.get(`/api/v1/sanciones?${consulta}`, { headers: conToken(quien.token) });
  }

  async function idsDelListado(filtros) {
    const r = await listar(moderadora, filtros);
    expect(r.status(), await r.text()).toBe(200);
    return (await r.json()).contenido.map((s) => s.id);
  }

  /** La bandeja se llena por un entregador con reintento: se espera un poco. */
  async function esperarAviso(sesion, tipo) {
    await expect
      .poll(
        async () => {
          const r = await api.get(`/api/v1/users/${sesion.claims.uid}/notifications`, {
            headers: conToken(sesion.token),
          });
          if (r.status() !== 200) {
            return `HTTP ${r.status()}`;
          }
          const bandeja = await r.json();
          return (bandeja.avisos ?? []).some((a) => a.tipo === tipo) ? tipo : 'todavia no';
        },
        { timeout: 20000, intervals: [500, 1000, 2000] },
      )
      .toBe(tipo);
  }

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    moderadora = await sesionDe(api, MODERADORA);
    admin = await sesionDe(api, ADMIN);
    apelante = await sesionDe(api, APELANTE);
    baneada = await sesionDe(api, BANEADA);
    expect(moderadora.claims.rol).toBe('MODERADOR');
    expect(admin.claims.rol).toBe('ADMINISTRADOR');
    expect(apelante.claims.rol).toBe('JUGADOR');
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  test('1 · la causal de §7.3.2 se guarda con la sancion; una que no es de las ocho, 400', async () => {
    const r = await emitir(moderadora, {
      usuarioId: apelante.claims.uid,
      tipo: 'ADVERTENCIA',
      causal: 'LENGUAJE_OFENSIVO',
      motivo: 'Insulto a otro jugador en el chat de la sala',
      politica: 'Normas de convivencia',
    });
    expect(r.status(), await r.text()).toBe(201);
    advertencia = await r.json();
    expect(advertencia.causal).toBe('LENGUAJE_OFENSIVO');
    expect(advertencia.politica).toBe('Normas de convivencia');

    const otra = await emitir(moderadora, {
      usuarioId: apelante.claims.uid,
      tipo: 'ADVERTENCIA',
      causal: 'MAL_COMPORTAMIENTO',
      motivo: 'No es una causal del documento',
    });
    expect(otra.status(), 'solo las ocho causales del documento').toBe(400);

    const historial = await api.get(`/api/v1/sanciones/usuarios/${apelante.claims.uid}`, {
      headers: conToken(apelante.token),
    });
    expect(historial.status()).toBe(200);
    expect(await historial.json(), 'la rechazada no quedo en el historial').toHaveLength(1);
  });

  test('2 · el listado global filtra por cuenta, causal, tipo y estado, y es de moderacion', async () => {
    const delApelante = { usuarioId: apelante.claims.uid };
    expect(await idsDelListado(delApelante)).toEqual([advertencia.id]);
    expect(await idsDelListado({ ...delApelante, causal: 'LENGUAJE_OFENSIVO' })).toEqual([
      advertencia.id,
    ]);
    expect(await idsDelListado({ ...delApelante, causal: 'ACOSO' })).toEqual([]);
    expect(await idsDelListado({ ...delApelante, tipo: 'SUSPENSION' })).toEqual([]);
    expect(await idsDelListado({ ...delApelante, estado: 'VIGENTE' })).toEqual([advertencia.id]);
    expect(await idsDelListado({ ...delApelante, estado: 'REVERTIDA' })).toEqual([]);

    const pagina = await (await listar(moderadora, delApelante)).json();
    expect(pagina).toMatchObject({ pagina: 0, tamano: 16, total: 1, totalPaginas: 1 });

    const reciente = await listar(moderadora, { desde: new Date(Date.now() - DIA).toISOString() });
    expect(reciente.status(), await reciente.text()).toBe(200);
    expect((await reciente.json()).contenido.map((s) => s.id)).toContain(advertencia.id);

    const deJugadora = await listar(apelante, delApelante);
    expect(deJugadora.status(), 'el listado es de moderacion').toBe(403);
    const fueraDelEnumerado = await listar(moderadora, { tipo: 'MULTA' });
    expect(fueraDelEnumerado.status()).toBe(400);
    const sinToken = await api.get('/api/v1/sanciones');
    expect(sinToken.status()).toBe(401);
  });

  test('3 · apelar trae el plazo de 15 dias habiles y el aviso de recibida', async () => {
    const r = await api.post(`/api/v1/sanciones/${advertencia.id}/apelaciones`, {
      headers: conToken(apelante.token),
      data: { argumento: 'Era una cita de otro jugador, no un insulto mio' },
    });
    expect(r.status(), await r.text()).toBe(201);
    const apelacion = await r.json();
    expect(apelacion.estado).toBe('PENDIENTE');
    expect(apelacion.vencida).toBe(false);
    // Quince dias de lunes a viernes: tres semanas desde un dia habil (hasta
    // el final del dia), algo menos si se presento en fin de semana.
    const plazo = Date.parse(apelacion.decisionAntesDe) - Date.parse(apelacion.creadaEn);
    expect(plazo).toBeGreaterThanOrEqual(19 * DIA);
    expect(plazo).toBeLessThan(22 * DIA);

    const delPanel = async (estado) => {
      const respuesta = await api.get(`/api/v1/apelaciones?estado=${estado}`, {
        headers: conToken(moderadora.token),
      });
      expect(respuesta.status(), await respuesta.text()).toBe(200);
      return (await respuesta.json()).map((a) => a.id);
    };
    expect(await delPanel('PENDIENTES')).toContain(apelacion.id);
    expect(await delPanel('TODAS')).toContain(apelacion.id);
    expect(await delPanel('RESUELTAS')).not.toContain(apelacion.id);
    const fuera = await api.get('/api/v1/apelaciones?estado=ARCHIVADAS', {
      headers: conToken(moderadora.token),
    });
    expect(fuera.status()).toBe(400);

    await esperarAviso(apelante, 'APELACION_RECIBIDA');
  });

  test('4 · el baneo solo lo revierte una apelacion: levantarlo a mano es 409, tambien al Super', async () => {
    const r = await emitir(admin, {
      usuarioId: baneada.claims.uid,
      tipo: 'BANEO',
      causal: 'FRAUDE',
      motivo: 'Duplico creditos con dos sesiones a la vez',
      confirmacion: true,
    });
    expect(r.status(), await r.text()).toBe(201);
    const baneo = await r.json();
    expect(baneo.causal).toBe('FRAUDE');

    const quienes = [admin];
    if (SUPER) {
      quienes.push(await sesionDe(api, SUPER));
    }
    for (const quien of quienes) {
      const levantar = await api.post(`/api/v1/sanciones/${baneo.id}/levantamiento`, {
        headers: conToken(quien.token),
        data: { motivo: 'Lo levanto a mano' },
      });
      expect(levantar.status(), `${quien.claims.rol}: ${await levantar.text()}`).toBe(409);
      expect((await levantar.json()).motivo).toBe('BANEO_SOLO_POR_APELACION');
    }

    const activa = await api.get(`/api/v1/sanciones/usuarios/${baneada.claims.uid}/activa`, {
      headers: conToken(moderadora.token),
    });
    expect(activa.status(), await activa.text()).toBe(200);
    expect(await activa.json()).toMatchObject({ sancionActiva: true, tipo: 'BANEO' });
    expect(
      await idsDelListado({ usuarioId: baneada.claims.uid, tipo: 'BANEO', estado: 'VIGENTE' }),
    ).toEqual([baneo.id]);
  });
});
