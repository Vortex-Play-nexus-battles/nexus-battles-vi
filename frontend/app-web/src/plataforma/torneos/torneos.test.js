/**
 * Torneos — HU-TOR-008: presentación pura y montaje de la vista contra un
 * servicio simulado (jugador, espectador y administrador).
 */

import { jest } from '@jest/globals';

import {
  ErrorDeTorneos,
  MOTIVOS,
  accionesDe,
  distintivoDeTorneo,
  faseDe,
  nombreDeLlave,
  nombreDeRonda,
  panelDeMiTorneo,
  panelDeTransmision,
  situacionDeEquipo,
  encuentrosDe,
  tarjetaDeEncuentro,
  porRonda,
  miEquipo,
  montarTorneos,
  nombreDe,
  puedoJugar,
  resumenDe,
  rutaDeSalaDelEncuentro,
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
      'Inscripciones abiertas · 3 de 8 equipos · 10 créditos',
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
    // UXC-9 — un equipo que no está en la lista no enseña su identificador.
    expect(nombreDe(t, 'desconocido-123')).toBe('Equipo sin nombre');
  });

  test('accionesDe: sesión, estado, cupo, equipo sin inscribir e inscrito (CA-03)', () => {
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
    ).toMatch(/posición 2/);
  });

  test('encuentrosDe filtra por llave y ordena por número', () => {
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

  test('puedoJugar: solo un encuentro LISTO de un torneo en curso en el que juega mi equipo (HU-TOR-004)', () => {
    const mio = equipo({ id: 'eq-1', inscrito: true, posicion: 1 });
    const rival = equipo({
      id: 'eq-2',
      nombre: 'Rivales',
      integrantes: ['x', 'y'],
      inscrito: true,
    });
    const listo = {
      numero: 1,
      llave: 'GANADORES',
      estado: 'LISTO',
      equipoA: 'eq-1',
      equipoB: 'eq-2',
    };
    const enCurso = torneo({ estado: 'EN_CURSO', equipos: [mio, rival], encuentros: [listo] });

    expect(puedoJugar(enCurso, listo, UID)).toBe(true);
    expect(puedoJugar(enCurso, listo, 'x')).toBe(true);
    expect(puedoJugar(enCurso, listo, 'nadie')).toBe(false);
    expect(puedoJugar(enCurso, listo, null)).toBe(false);
    expect(puedoJugar(enCurso, { ...listo, estado: 'JUGADO' }, UID)).toBe(false);
    expect(puedoJugar(enCurso, { ...listo, equipoA: 'eq-3' }, UID)).toBe(false);
    expect(puedoJugar({ ...enCurso, estado: 'FINALIZADO' }, listo, UID)).toBe(false);
  });

  test('tarjetaDeEncuentro pone «Crear sala del encuentro» hacia crear-sala con torneo y encuentro', () => {
    const mio = equipo({ id: 'eq-1', inscrito: true, posicion: 1 });
    const rival = equipo({
      id: 'eq-2',
      nombre: 'Rivales',
      integrantes: ['x', 'y'],
      inscrito: true,
    });
    const listo = {
      numero: 5,
      llave: 'GANADORES',
      estado: 'LISTO',
      equipoA: 'eq-1',
      equipoB: 'eq-2',
    };
    const enCurso = torneo({
      id: 't-9',
      estado: 'EN_CURSO',
      equipos: [mio, rival],
      encuentros: [listo],
    });

    expect(rutaDeSalaDelEncuentro(enCurso, listo)).toBe(
      '../salas-partidas/crear-sala.html?torneo=t-9&encuentro=5',
    );
    const conAcceso = tarjetaDeEncuentro(enCurso, listo, UID);
    const enlace = conAcceso.querySelector('[data-accion="jugar-encuentro"]');
    expect(enlace.getAttribute('href')).toBe(
      '../salas-partidas/crear-sala.html?torneo=t-9&encuentro=5',
    );
    // Los dos equipos, cada uno en su fila del componente `Encuentro`, en vez
    // de la frase corrida «A vs B» que habia antes.
    const filas = [...conAcceso.querySelectorAll('.encuentro__equipo')];
    expect(filas[0].textContent).toContain('Los Valientes');
    expect(filas[1].textContent).toContain('Rivales');

    expect(tarjetaDeEncuentro(enCurso, listo, 'nadie').querySelector('a')).toBeNull();
    expect(tarjetaDeEncuentro(enCurso, listo).querySelector('a')).toBeNull();
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
    // UX-R2.7 — el vacio pasa de un parrafo gris a un estado util: dice que
    // NO hay torneo, explica el formato por temporadas y ofrece lo unico que
    // el jugador puede hacer ahora. Sin inventar una fecha del proximo.
    const listado = document.querySelector('[data-zona="listado"]');
    expect(listado.textContent).toMatch(/no hay ningún torneo abierto/i);
    expect(listado.textContent).toMatch(/temporadas/i);
    expect(listado.querySelector('a[href*="batallas"]')).not.toBeNull();
    expect(listado.textContent).not.toMatch(/\d+\s*d[ií]as/);
    expect(document.querySelector('[data-zona="crear-torneo"]').hidden).toBe(true);

    document.body.innerHTML = VISTA;
    montarTorneos(document, {
      rol: 'ADMINISTRADOR',
      uid: UID,
      fetchImpl: servicio({ 'GET /api/v1/torneos': { cuerpo: [] } }),
    });
    expect(document.querySelector('[data-zona="crear-torneo"]').hidden).toBe(false);
  });

  test('abrir un torneo pinta equipos, acciones y árbol; el jugador registra su equipo y lo inscribe', async () => {
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
    expect(inscribir.textContent).toMatch(/10 créditos/);
    inscribir.click();
    await asentar();
    await asentar();
    await asentar();
    expect(document.querySelector('.aviso--exito').textContent).toMatch(/posición 1/);
    expect(document.querySelector('[data-zona="acciones"]').textContent).toMatch(
      /Ya estás inscrito/,
    );
  });

  test('en curso se pinta el árbol por llaves y el campeón; un rechazo del servicio se avisa', async () => {
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
    expect(detalle.querySelector('[data-zona="campeon"]').textContent).toBe('Campeón: A');
    // El encuentro es el componente `Encuentro` del kit: cabecera con estado y
    // una fila por equipo, no la frase corrida que habia antes.
    const primero = detalle.querySelector('[data-llave="GANADORES"] [data-numero="1"]');
    expect(primero.querySelector('.encuentro__cabecera').textContent).toBe('Encuentro 1Jugado');
    const ganadores = [...primero.querySelectorAll('.encuentro__equipo')];
    expect(ganadores[0].classList.contains('encuentro__equipo--ganador')).toBe(true);
    expect(ganadores[0].textContent).toContain('A');
    expect(ganadores[1].textContent).toContain('B');

    const final = detalle.querySelector('[data-llave="FINAL"] [data-numero="14"]');
    expect(final.querySelector('.encuentro__cabecera').textContent).toBe('FinalPendiente');
    expect([...final.querySelectorAll('.encuentro__equipo')][1].textContent).toContain(
      'por definir',
    );
    expect(detalle.querySelector('[data-equipo-id="b"][data-ia="true"]').textContent).toMatch(
      /máquina/,
    );
    expect(detalle.querySelector('[data-zona="acciones"]').textContent).toMatch(/Inicia sesión/);

    // UX-R3.6 — un rechazo al CARGAR el listado se dice donde iban los
    // torneos, y solo ahi.
    //
    // Antes se decia dos veces: el listado pintaba su estado de error con el
    // motivo y su boton de reintentar, y ademas saltaba el aviso flotante de
    // arriba con «No se pudo completar» y nada mas. Dos avisos del mismo
    // fallo, y el peor primero — una caja amarilla, encima del mensaje bueno,
    // que no decia que habia pasado ni que se podia hacer.
    //
    // El aviso flotante sigue siendo para los fallos de una ACCION (inscribir
    // un equipo, abrir un torneo), donde el contenido de la pantalla sigue
    // siendo valido y hay que decir que fallo lo que se acaba de pulsar.
    document.body.innerHTML = VISTA;
    const avisos = jest.spyOn(console, 'warn').mockImplementation(() => {});
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

    const listado = document.querySelector('[data-zona="listado"]');
    expect(listado.textContent).toContain('Los torneos no están disponibles');
    expect(listado.querySelector('[data-accion="reintentar"]')).not.toBeNull();
    expect(document.querySelector('.aviso--error')).toBeNull();
    expect(avisos.mock.calls.flat().join(' ')).toContain('torneos');
    avisos.mockRestore();
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

describe('el árbol usa el componente Encuentro del sistema de diseno', () => {
  const equipos = [
    { id: 'eq-1', nombre: 'Los Valientes', integrantes: [UID, 'x'], inscrito: true, posicion: 1 },
    { id: 'eq-2', nombre: 'Rivales', integrantes: ['y', 'z'], inscrito: true, posicion: 2 },
  ];
  const enCurso = { id: 't-1', estado: 'EN_CURSO', equipos, encuentros: [] };

  function encuentro(extra) {
    return { numero: 1, llave: 'GANADORES', ronda: 1, equipoA: 'eq-1', equipoB: 'eq-2', ...extra };
  }

  test('pinta cabecera con el estado y una fila por equipo', () => {
    const tarjeta = tarjetaDeEncuentro(enCurso, encuentro({ estado: 'PENDIENTE' }));

    expect(tarjeta.classList.contains('encuentro')).toBe(true);
    expect(tarjeta.querySelector('.encuentro__cabecera').textContent).toContain('Encuentro 1');
    expect(tarjeta.querySelector('.encuentro__cabecera').textContent).toContain('Pendiente');
    expect(tarjeta.querySelectorAll('.encuentro__equipo')).toHaveLength(2);
  });

  test('un encuentro listo se marca como en curso', () => {
    const tarjeta = tarjetaDeEncuentro(enCurso, encuentro({ estado: 'LISTO' }));

    expect(tarjeta.classList.contains('encuentro--en-curso')).toBe(true);
  });

  test('el ganador se distingue por la clase del kit, no solo por color', () => {
    const tarjeta = tarjetaDeEncuentro(enCurso, encuentro({ estado: 'JUGADO', ganador: 'eq-2' }));

    const filas = [...tarjeta.querySelectorAll('.encuentro__equipo')];
    expect(filas[0].classList.contains('encuentro__equipo--ganador')).toBe(false);
    expect(filas[1].classList.contains('encuentro__equipo--ganador')).toBe(true);
    expect(tarjeta.classList.contains('encuentro--finalizado')).toBe(true);
  });

  test('el ganador tambien se dice para quien no ve la negrita', () => {
    const tarjeta = tarjetaDeEncuentro(enCurso, encuentro({ estado: 'JUGADO', ganador: 'eq-1' }));

    const oculto = tarjeta.querySelector('.solo-lectores');
    expect(oculto).not.toBeNull();
    expect(oculto.textContent).toContain('gana');
  });

  test('un lado sin equipo dice «por definir» en vez de quedarse en blanco', () => {
    const tarjeta = tarjetaDeEncuentro(
      enCurso,
      encuentro({ numero: 14, estado: 'PENDIENTE', equipoA: 'eq-1', equipoB: null }),
    );

    const filas = [...tarjeta.querySelectorAll('.encuentro__equipo')];
    expect(filas[1].textContent).toContain('por definir');
    expect(tarjeta.querySelector('.encuentro__cabecera').textContent).toContain('Final');
  });

  test('sin ganador ninguna fila se marca', () => {
    const tarjeta = tarjetaDeEncuentro(enCurso, encuentro({ estado: 'PENDIENTE', ganador: null }));

    expect(tarjeta.querySelectorAll('.encuentro__equipo--ganador')).toHaveLength(0);
  });

  test('no se inventa marcador: el contrato no trae puntuacion', () => {
    const tarjeta = tarjetaDeEncuentro(enCurso, encuentro({ estado: 'JUGADO', ganador: 'eq-1' }));

    expect(tarjeta.querySelector('.encuentro__marcador')).toBeNull();
  });
});

describe('porRonda()', () => {
  test('agrupa por ronda y las devuelve en orden', () => {
    const grupos = porRonda([
      { numero: 3, ronda: 2 },
      { numero: 1, ronda: 1 },
      { numero: 2, ronda: 1 },
    ]);

    expect(grupos.map((g) => g.ronda)).toEqual([1, 2]);
    expect(grupos[0].encuentros.map((e) => e.numero)).toEqual([1, 2]);
  });

  test('un encuentro sin ronda no se pierde', () => {
    const grupos = porRonda([{ numero: 1 }, { numero: 2, ronda: 1 }]);

    expect(grupos).toHaveLength(2);
    expect(grupos[0].ronda).toBe(0);
  });

  test('sin encuentros devuelve una lista vacia', () => {
    expect(porRonda([])).toEqual([]);
  });
});

describe('UXC-8 — el torneo del jugador', () => {
  const A = equipo({ id: 'a', nombre: 'Los Valientes', inscrito: true, posicion: 1 });
  const B = equipo({
    id: 'b',
    nombre: 'Los Lobos',
    capitanUid: 'otro',
    integrantes: ['otro', 'otro-2'],
    inscrito: true,
    posicion: 2,
  });
  const C = equipo({
    id: 'c',
    nombre: 'La Máquina',
    ia: true,
    capitanUid: null,
    integrantes: [],
    inscrito: true,
    posicion: 3,
  });
  const enCurso = () =>
    torneo({
      estado: 'EN_CURSO',
      equiposInscritos: 3,
      equipos: [A, B, C],
      encuentros: [
        {
          numero: 1,
          llave: 'GANADORES',
          ronda: 1,
          equipoA: 'a',
          equipoB: 'c',
          ganador: 'a',
          estado: 'JUGADO',
        },
        {
          numero: 5,
          llave: 'GANADORES',
          ronda: 2,
          equipoA: 'a',
          equipoB: 'b',
          ganador: null,
          estado: 'LISTO',
        },
        {
          numero: 7,
          llave: 'SECUNDARIOS',
          ronda: 1,
          equipoA: 'c',
          equipoB: null,
          ganador: null,
          estado: 'PENDIENTE',
        },
      ],
    });

  test('las rondas tienen nombre y las llaves no enseñan la numeración interna', () => {
    expect(nombreDeRonda('GANADORES', 1, { cantidad: 4, ultima: false })).toBe('Cuartos de final');
    expect(nombreDeRonda('GANADORES', 2, { cantidad: 2, ultima: false })).toBe('Semifinales');
    expect(nombreDeRonda('GANADORES', 3, { cantidad: 1, ultima: true })).toBe('Final de ganadores');
    expect(nombreDeRonda('SECUNDARIOS', 2, { cantidad: 2, ultima: false })).toBe('Ronda 2');
    expect(nombreDeRonda('FINAL', 4, { cantidad: 1, ultima: true })).toBe('Gran final');
    expect(nombreDeLlave('SECUNDARIOS')).toBe('Llave de segunda oportunidad');
    expect(nombreDeLlave('GANADORES')).not.toMatch(/\d/);
  });

  test('la tarjeta dice lo que toca a cada fase, con su distintivo', () => {
    expect(faseDe(torneo())).toMatch(/^Inscripciones hasta/);
    expect(faseDe(torneo({ estado: 'EN_CURSO' }))).toMatch(/Se está jugando/);
    expect(faseDe(torneo({ estado: 'FINALIZADO', campeonEquipoId: 'a' }))).toMatch(/campeón/);
    expect(faseDe(torneo({ estado: 'CANCELADO' }))).toMatch(/devolvieron/);
    expect(distintivoDeTorneo(torneo({ estado: 'EN_CURSO' })).textContent).toBe('En curso');
  });

  test('«Tu torneo»: mi equipo, mi próximo encuentro con su sala y mi camino', () => {
    const panel = panelDeMiTorneo(enCurso(), A, UID);

    const mio = panel.querySelector('[data-zona="mi-equipo"]');
    expect(mio.textContent).toContain('Los Valientes');
    expect(mio.textContent).toContain('Tú (capitán) y tu compañero');
    expect(mio.textContent).not.toContain(OTRO);

    const proximo = panel.querySelector('[data-zona="proximo-encuentro"]');
    expect(proximo.textContent).toContain('Contra Los Lobos');
    expect(proximo.textContent).toContain('Llave de ganadores');
    expect(proximo.querySelector('[data-accion="jugar-mi-encuentro"]').getAttribute('href')).toBe(
      '../salas-partidas/crear-sala.html?torneo=t-1&encuentro=5',
    );

    const camino = panel.querySelectorAll('[data-zona="mi-camino"] li');
    expect(camino).toHaveLength(1);
    expect(camino[0].textContent).toContain('Victoria contra La Máquina');
  });

  test('eliminado, o sin árbol todavía, el próximo encuentro lo dice sin inventar', () => {
    const eliminado = { ...A, eliminado: true, derrotas: 2 };
    const t = torneo({ estado: 'EN_CURSO', equipos: [eliminado], encuentros: [] });
    expect(panelDeMiTorneo(t, eliminado, UID).textContent).toContain('ya no juega más encuentros');
    expect(situacionDeEquipo(t, eliminado)).toBe('Eliminado tras dos derrotas');

    const abierto = torneo({ equipos: [A] });
    expect(panelDeMiTorneo(abierto, A, UID).textContent).toContain('El árbol se genera');
    expect(situacionDeEquipo(abierto, A)).toMatch(/posición 1/);
  });

  test('en el árbol, los encuentros de tu equipo se marcan con texto', () => {
    const t = enCurso();
    const tarjeta = tarjetaDeEncuentro(t, t.encuentros[1], UID);
    expect(tarjeta.classList.contains('encuentro--mio')).toBe(true);
    expect(tarjeta.querySelector('.encuentro__tuyo').textContent).toBe('Tu equipo');
    expect(tarjetaDeEncuentro(t, t.encuentros[2], UID).classList.contains('encuentro--mio')).toBe(
      false,
    );
  });

  test('la transmisión se dice como es: sin señal y con cómo seguir el torneo', () => {
    const alActualizar = jest.fn();
    const panel = panelDeTransmision(enCurso(), { alActualizar });
    expect(panel.classList.contains('transmision--sin-senal')).toBe(true);
    expect(panel.textContent).toContain('no se transmite en vivo');
    expect(panel.textContent).not.toMatch(/en directo/i);
    panel.querySelector('[data-accion="actualizar-torneo"]').click();
    expect(alActualizar).toHaveBeenCalled();
  });

  test('un rechazo nunca dice «Error 503»: dice el motivo en palabras del juego', () => {
    const caido = new ErrorDeTorneos(null, 503);
    expect(caido.message).not.toMatch(/\d{3}/);
    expect(caido.message).toMatch(/no responden/);

    const cupo = new ErrorDeTorneos({ motivo: 'CUPO_AGOTADO', detail: 'x' }, 409);
    expect(cupo.titulo).toBe('El torneo está completo');
    expect(MOTIVOS.CUPO_AGOTADO.detalle).toMatch(/siguiente torneo/);

    const tecnico = new ErrorDeTorneos({ detail: 'NullPointerException at x.y(Z.java:1)' }, 409);
    expect(tecnico.message).not.toContain('Exception');
  });

  test('la vista monta «Tu torneo» y la transmisión; cancelar usa el diálogo del kit', async () => {
    document.body.innerHTML = VISTA;
    const t = enCurso();
    montarTorneos(document, {
      uid: UID,
      fetchImpl: servicio({
        'GET /api/v1/torneos': { cuerpo: [t] },
        'GET /api/v1/torneos/t-1': { cuerpo: t },
      }),
      torneoInicial: 't-1',
    });
    await asentar();
    await asentar();
    const detalle = document.querySelector('[data-zona="detalle"]');
    expect(detalle.querySelector('[data-zona="mi-torneo"]')).not.toBeNull();
    expect(detalle.querySelector('[data-zona="transmision"]')).not.toBeNull();
    const cabeceras = [...detalle.querySelectorAll('.arbol-torneo__ronda > .t-meta')].map(
      (p) => p.textContent,
    );
    // Con este árbol parcial, la última ronda de ganadores es su final.
    expect(cabeceras).toContain('Final de ganadores');
    expect(detalle.textContent).not.toMatch(/1-6 y 11|7-10/);

    // El administrador cancela con el diálogo del kit, no con prompt().
    document.body.innerHTML = VISTA;
    const abierto = torneo();
    const prompt = jest.fn();
    globalThis.prompt = prompt;
    montarTorneos(document, {
      rol: 'ADMINISTRADOR',
      uid: 'admin',
      fetchImpl: servicio({
        'GET /api/v1/torneos': { cuerpo: [abierto] },
        'GET /api/v1/torneos/t-1': { cuerpo: abierto },
      }),
      torneoInicial: 't-1',
    });
    await asentar();
    await asentar();
    document.querySelector('[data-accion="cancelar"]').click();
    await asentar();
    expect(prompt).not.toHaveBeenCalled();
    expect(document.querySelector('[role="dialog"]').textContent).toContain(
      '¿Cancelar «Copa Otono»?',
    );
    delete globalThis.prompt;
  });
});
