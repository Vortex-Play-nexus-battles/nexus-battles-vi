/**
 * Torneos — HU-TOR-008: presentación pura y montaje de la vista contra un
 * servicio simulado (jugador, espectador y administrador).
 */

import { jest } from '@jest/globals';

import {
  ErrorDeTorneos,
  accionesDe,
  encuentrosDe,
  miEquipo,
  montarTorneos,
  nombreDe,
  resumenDe,
} from './torneos.js';

const UID = '11111111-1111-1111-1111-111111111111';
const OTRO = '22222222-2222-2222-2222-222222222222';
const asentar = () => new Promise((resolve) => setTimeout(resolve, 0));

const equipo = (extra = {}) => ({
  id: 'eq-1',
  torneoId: 't-1',
  nombre: 'Los Valientes',
  avatar: 'a',
  ia: false,
  capitanUid: UID,
  integrantes: [UID, OTRO],
  inscrito: false,
  pagadoPor: null,
  reservaId: null,
  posicion: null,
  derrotas: 0,
  eliminado: false,
  ...extra,
});

const torneo = (extra = {}) => ({
  id: 't-1',
  nombre: 'Copa Otono',
  estado: 'INSCRIPCIONES_ABIERTAS',
  creadoEn: '2026-10-01T10:00:00Z',
  inscripcionesCierranEn: '2026-10-08T10:00:00Z',
  costoInscripcion: 10,
  equiposInscritos: 0,
  cupos: 8,
  campeonEquipoId: null,
  equipos: [],
  encuentros: [],
  ...extra,
});

function servicio(rutas) {
  return jest.fn(async (url, opciones = {}) => {
    const metodo = opciones.method ?? 'GET';
    const clave = `${metodo} ${new URL(url, 'http://x').pathname}`;
    const respuesta = rutas[clave];
    if (!respuesta) {
      throw new Error(`sin ruta simulada para ${clave}`);
    }
    const { estado = 200, cuerpo = null } =
      typeof respuesta === 'function' ? respuesta(opciones) : respuesta;
    return { ok: estado >= 200 && estado < 300, status: estado, json: async () => cuerpo };
  });
}

describe('presentacion', () => {
  test('resumenDe dice estado, ocupacion y costo', () => {
    expect(resumenDe(torneo({ equiposInscritos: 3 }))).toBe(
      'Inscripciones abiertas · 3 de 8 equipos · 10 creditos',
    );
    expect(resumenDe(torneo({ estado: 'EN_CURSO', costoInscripcion: 0 }))).toBe(
      'En curso · 0 de 8 equipos · gratuito',
    );
  });

  test('miEquipo y nombreDe', () => {
    const t = torneo({ equipos: [equipo(), equipo({ id: 'ia', ia: true, integrantes: [] })] });
    expect(miEquipo(t, UID).id).toBe('eq-1');
    expect(miEquipo(t, 'nadie')).toBeNull();
    expect(nombreDe(t, 'eq-1')).toBe('Los Valientes');
    expect(nombreDe(t, null)).toBe('por definir');
    expect(nombreDe(t, 'desconocido-123')).toBe('desconoc');
  });

  test('accionesDe: sesion, estado, cupo, equipo sin inscribir e inscrito (CA-03)', () => {
    expect(accionesDe(torneo(), null)).toMatchObject({ crearEquipo: false, inscribir: false });
    expect(accionesDe(torneo({ estado: 'EN_CURSO' }), UID).motivo).toMatch(/cerradas/);
    expect(accionesDe(torneo({ equiposInscritos: 8 }), UID).motivo).toMatch(/Cupo agotado/);
    expect(accionesDe(torneo(), UID)).toMatchObject({ crearEquipo: true, inscribir: false });
    expect(accionesDe(torneo({ equipos: [equipo()] }), UID)).toMatchObject({
      crearEquipo: false,
      inscribir: true,
    });
    expect(
      accionesDe(torneo({ equipos: [equipo({ inscrito: true, posicion: 2 })] }), UID).motivo,
    ).toMatch(/posicion 2/);
  });

  test('encuentrosDe filtra por llave y ordena por numero', () => {
    const t = torneo({
      encuentros: [
        { numero: 11, llave: 'GANADORES', estado: 'PENDIENTE' },
        { numero: 1, llave: 'GANADORES', estado: 'LISTO' },
        { numero: 7, llave: 'SECUNDARIOS', estado: 'PENDIENTE' },
      ],
    });
    expect(encuentrosDe(t, 'GANADORES').map((e) => e.numero)).toEqual([1, 11]);
    expect(encuentrosDe(t, 'FINAL')).toEqual([]);
  });

  test('ErrorDeTorneos conserva motivo y proxima fecha', () => {
    const e = new ErrorDeTorneos(
      {
        title: 'T',
        detail: 'D',
        motivo: 'VENTANA_DE_91_DIAS',
        proximaFechaPosible: '2027-01-01T00:00:00Z',
      },
      409,
    );
    expect(e.motivo).toBe('VENTANA_DE_91_DIAS');
    expect(e.proximaFechaPosible).toBe('2027-01-01T00:00:00Z');
  });
});

const VISTA = `
  <div data-zona="aviso" hidden></div>
  <form data-zona="crear-torneo" hidden>
    <input name="nombre" /><input name="inscripcionesCierranEn" /><input name="costoInscripcion" value="5" />
    <button type="submit">Crear</button>
  </form>
  <div data-zona="listado"></div>
  <section data-zona="detalle" hidden></section>`;

describe('vista', () => {
  beforeEach(() => {
    document.body.innerHTML = VISTA;
  });

  test('sin torneos lo dice; el formulario de crear solo aparece al administrador', async () => {
    montarTorneos(document, {
      rol: 'JUGADOR',
      uid: UID,
      fetchImpl: servicio({ 'GET /api/v1/torneos': { cuerpo: [] } }),
    });
    await asentar();
    expect(document.querySelector('[data-zona="listado"]').textContent).toMatch(/no hay torneos/);
    expect(document.querySelector('[data-zona="crear-torneo"]').hidden).toBe(true);

    document.body.innerHTML = VISTA;
    montarTorneos(document, {
      rol: 'ADMINISTRADOR',
      uid: UID,
      fetchImpl: servicio({ 'GET /api/v1/torneos': { cuerpo: [] } }),
    });
    expect(document.querySelector('[data-zona="crear-torneo"]').hidden).toBe(false);
  });

  test('abrir un torneo pinta equipos, acciones y arbol; el jugador registra su equipo y lo inscribe', async () => {
    let t = torneo();
    const fetchImpl = servicio({
      'GET /api/v1/torneos': () => ({ cuerpo: [t] }),
      'GET /api/v1/torneos/t-1': () => ({ cuerpo: t }),
      'POST /api/v1/torneos/t-1/equipos': (opciones) => {
        const cuerpo = JSON.parse(opciones.body);
        t = torneo({
          equipos: [equipo({ nombre: cuerpo.nombre, integrantes: [UID, cuerpo.companeroUid] })],
        });
        return { estado: 201, cuerpo: t.equipos[0] };
      },
      'POST /api/v1/torneos/t-1/equipos/eq-1/inscripcion': () => {
        t = torneo({ equiposInscritos: 1, equipos: [equipo({ inscrito: true, posicion: 1 })] });
        return { estado: 201, cuerpo: t.equipos[0] };
      },
    });
    montarTorneos(document, { rol: 'JUGADOR', uid: UID, fetchImpl });
    await asentar();
    expect(document.querySelectorAll('[data-zona="listado"] article')).toHaveLength(1);
    document.querySelector('[data-accion="abrir"]').click();
    await asentar();
    await asentar();
    const detalle = document.querySelector('[data-zona="detalle"]');
    expect(detalle.hidden).toBe(false);
    expect(detalle.querySelector('[data-zona="arbol"]').textContent).toMatch(/se genera al cerrar/);
    expect(detalle.querySelector('[data-zona="administracion"]')).toBeNull();

    const form = detalle.querySelector('[data-zona="crear-equipo"]');
    expect(form).not.toBeNull();
    form.querySelector('[name="nombre"]').value = 'Los Valientes';
    form.querySelector('[name="avatar"]').value = 'avatar-1';
    form.querySelector('[name="companeroUid"]').value = OTRO;
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await asentar();
    await asentar();
    await asentar();
    const llamada = fetchImpl.mock.calls.find(
      (c) => String(c[0]).endsWith('/equipos') && c[1]?.method === 'POST',
    );
    expect(JSON.parse(llamada[1].body)).toEqual({
      nombre: 'Los Valientes',
      avatar: 'avatar-1',
      companeroUid: OTRO,
    });
    expect(document.querySelector('.aviso--exito').textContent).toMatch(/Equipo registrado/);

    const inscribir = document.querySelector('[data-accion="inscribir"]');
    expect(inscribir.textContent).toMatch(/10 creditos/);
    inscribir.click();
    await asentar();
    await asentar();
    await asentar();
    expect(document.querySelector('.aviso--exito').textContent).toMatch(/posicion 1/);
    expect(document.querySelector('[data-zona="acciones"]').textContent).toMatch(
      /Ya estas inscrito/,
    );
  });

  test('en curso se pinta el arbol por llaves y el campeon; un rechazo del servicio se avisa', async () => {
    const a = equipo({ id: 'a', nombre: 'A', inscrito: true, posicion: 1 });
    const b = equipo({
      id: 'b',
      nombre: 'B',
      ia: true,
      integrantes: [],
      inscrito: true,
      posicion: 2,
    });
    const t = torneo({
      estado: 'FINALIZADO',
      equiposInscritos: 1,
      campeonEquipoId: 'a',
      equipos: [a, b],
      encuentros: [
        {
          numero: 1,
          llave: 'GANADORES',
          ronda: 1,
          equipoA: 'a',
          equipoB: 'b',
          ganador: 'a',
          estado: 'JUGADO',
        },
        {
          numero: 7,
          llave: 'SECUNDARIOS',
          ronda: 1,
          equipoA: 'b',
          equipoB: null,
          ganador: null,
          estado: 'PENDIENTE',
        },
        {
          numero: 14,
          llave: 'FINAL',
          ronda: 4,
          equipoA: 'a',
          equipoB: null,
          ganador: null,
          estado: 'PENDIENTE',
        },
      ],
    });
    montarTorneos(document, {
      uid: null,
      fetchImpl: servicio({
        'GET /api/v1/torneos': { cuerpo: [t] },
        'GET /api/v1/torneos/t-1': { cuerpo: t },
      }),
      torneoInicial: 't-1',
    });
    await asentar();
    await asentar();
    const detalle = document.querySelector('[data-zona="detalle"]');
    expect(detalle.querySelector('[data-zona="campeon"]').textContent).toBe('Campeon: A');
    expect(detalle.querySelector('[data-llave="GANADORES"] [data-numero="1"]').textContent).toBe(
      'Encuentro 1: A vs B → gana A',
    );
    expect(detalle.querySelector('[data-llave="FINAL"] [data-numero="14"]').textContent).toBe(
      'Final: A vs por definir',
    );
    expect(detalle.querySelector('[data-equipo-id="b"][data-ia="true"]').textContent).toMatch(
      /maquina/,
    );
    expect(detalle.querySelector('[data-zona="acciones"]').textContent).toMatch(/Inicia sesion/);

    document.body.innerHTML = VISTA;
    montarTorneos(document, {
      uid: UID,
      fetchImpl: servicio({
        'GET /api/v1/torneos': {
          estado: 503,
          cuerpo: { title: 'Caido', detail: 'x', motivo: 'LIBRO_NO_DISPONIBLE' },
        },
      }),
    });
    await asentar();
    expect(document.querySelector('.aviso--error .aviso__titulo').textContent).toBe('Caido');
  });

  test('el administrador crea el torneo desde el formulario, e inicia el torneo abierto', async () => {
    let t = torneo({ equiposInscritos: 1, equipos: [equipo({ inscrito: true, posicion: 1 })] });
    const fetchImpl = servicio({
      'GET /api/v1/torneos': () => ({ cuerpo: [t] }),
      'GET /api/v1/torneos/t-1': () => ({ cuerpo: t }),
      'POST /api/v1/torneos': (opciones) => {
        const cuerpo = JSON.parse(opciones.body);
        return {
          estado: 201,
          cuerpo: torneo({
            id: 't-2',
            nombre: cuerpo.nombre,
            costoInscripcion: cuerpo.costoInscripcion,
          }),
        };
      },
      'POST /api/v1/torneos/t-1/inicio': () => {
        t = torneo({ ...t, estado: 'EN_CURSO' });
        return { cuerpo: t };
      },
    });
    montarTorneos(document, { rol: 'ADMINISTRADOR', uid: 'admin', fetchImpl });
    await asentar();
    const form = document.querySelector('[data-zona="crear-torneo"]');
    form.querySelector('[name="nombre"]').value = 'Copa Invierno';
    form.querySelector('[name="inscripcionesCierranEn"]').value = '2026-12-01T10:00';
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await asentar();
    await asentar();
    await asentar();
    const creada = fetchImpl.mock.calls.find(
      (c) => String(c[0]).endsWith('/api/v1/torneos') && c[1]?.method === 'POST',
    );
    const cuerpo = JSON.parse(creada[1].body);
    expect(cuerpo.nombre).toBe('Copa Invierno');
    expect(cuerpo.costoInscripcion).toBe(5);
    expect(cuerpo.inscripcionesCierranEn).toMatch(/^2026-12-01T/);
    expect(document.querySelector('.aviso--exito').textContent).toMatch(/Torneo creado/);
    expect(document.querySelector('[data-zona="detalle"]').dataset.torneoId).toBe('t-2');

    document.querySelector('[data-accion="abrir"]').click();
    await asentar();
    await asentar();
    const iniciar = document.querySelector('[data-accion="iniciar"]');
    expect(iniciar).not.toBeNull();
    iniciar.click();
    await asentar();
    await asentar();
    await asentar();
    expect(document.querySelector('.aviso--exito').textContent).toMatch(/Torneo iniciado/);
    expect(document.querySelector('[data-zona="detalle"]').dataset.estado).toBe('EN_CURSO');
    expect(document.querySelector('[data-zona="administracion"]')).toBeNull();
  });
});
