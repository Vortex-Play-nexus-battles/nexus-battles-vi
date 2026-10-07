/**
 * Punto 24 de la revisión del modo jugador (6-oct): la tarjeta del tablón y
 * la ruta de un torneo — su portada, sus seis hitos y la navegación entre el
 * tablón y la ruta sin recargar, con la dirección siguiendo al modo.
 */

import { jest } from '@jest/globals';

import {
  ESTADOS_DEL_HITO,
  HITOS_DEL_TORNEO,
  cuposDelTorneo,
  descripcionDelTorneo,
  dueloDelEncuentro,
  estadosDeLaRuta,
  hitoActual,
  rutaDelTorneo,
} from './torneo-ruta.js';
import { bloquesDeMiTorneo, montarTorneos, tarjetaDeTorneo } from './torneos.js';

const UID = '11111111-1111-1111-1111-111111111111';
const OTRO = '22222222-2222-2222-2222-222222222222';
const asentar = () => new Promise((resolve) => setTimeout(resolve, 0));
const asentarVarias = async (veces = 4) => {
  for (let i = 0; i < veces; i += 1) {
    await asentar();
  }
};

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
  equiposInscritos: 1,
  cupos: 8,
  campeonEquipoId: null,
  equipos: [],
  encuentros: [],
  ...extra,
});

const MAQUINA = equipo({
  id: 'ia-1',
  nombre: 'La Máquina',
  ia: true,
  capitanUid: null,
  integrantes: [],
  inscrito: true,
  posicion: 2,
});

/** Un torneo en curso en el que mi equipo ganó el primero y espera el 5. */
const enCurso = () => {
  const mio = equipo({ inscrito: true, posicion: 1 });
  return torneo({
    estado: 'EN_CURSO',
    equiposInscritos: 2,
    equipos: [mio, MAQUINA],
    encuentros: [
      {
        numero: 1,
        llave: 'GANADORES',
        ronda: 1,
        equipoA: 'eq-1',
        equipoB: 'ia-1',
        ganador: 'eq-1',
        estado: 'JUGADO',
      },
      {
        numero: 5,
        llave: 'GANADORES',
        ronda: 2,
        equipoA: 'eq-1',
        equipoB: 'ia-1',
        ganador: null,
        estado: 'LISTO',
      },
    ],
  });
};

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

/** Un navegador de mentira: la dirección, el historial y su `popstate`. */
function navegador(inicial = 'https://nexus.test/torneos') {
  const ubicacion = new URL(inicial);
  const empujes = [];
  const historial = {
    pushState: (_estado, _titulo, url) => {
      empujes.push(url);
      ubicacion.href = new URL(url, ubicacion.href).href;
    },
  };
  const ventana = new EventTarget();
  return {
    ubicacion,
    historial,
    ventana,
    empujes,
    /** Atrás o adelante: cambia la dirección y avisa. */
    irA(url) {
      ubicacion.href = new URL(url, ubicacion.href).href;
      ventana.dispatchEvent(new Event('popstate'));
    },
  };
}

const VISTA = `
  <main data-vista="torneos">
    <header data-zona="encabezado"><h1 tabindex="-1">Torneos</h1></header>
    <div data-zona="aviso" hidden></div>
    <form data-zona="crear-torneo" hidden>
      <input name="nombre" /><input name="inscripcionesCierranEn" />
      <input name="costoInscripcion" value="0" />
      <button type="submit">Crear</button>
    </form>
    <section data-zona="tablon"><div data-zona="listado"></div></section>
    <section data-zona="detalle" hidden></section>
  </main>`;

const zona = (nombre) => document.querySelector(`[data-zona="${nombre}"]`);
const hito = (id) => document.querySelector(`.torneo-ruta [data-hito="${id}"]`);

function montar(t, { rol = 'JUGADOR', uid = UID, torneoInicial = null, nav = navegador() } = {}) {
  document.body.innerHTML = VISTA;
  document.title = 'Torneo · The Nexus Battles VI';
  const fetchImpl = servicio({
    'GET /api/v1/torneos': () => ({ cuerpo: [t] }),
    'GET /api/v1/torneos/t-1': () => ({ cuerpo: t }),
  });
  montarTorneos(document, {
    rol,
    uid,
    fetchImpl,
    torneoInicial,
    historial: nav.historial,
    ubicacion: nav.ubicacion,
    ventana: nav.ventana,
  });
  return { fetchImpl, nav };
}

describe('la ruta: hitos y estados', () => {
  test('seis hitos, en el orden que pide la revisión', () => {
    expect(HITOS_DEL_TORNEO.map((h) => h.titulo)).toEqual([
      'Cómo se juega',
      'Mi equipo',
      'Próximo encuentro',
      'El árbol',
      'Mi camino',
      'Resultado y premio',
    ]);
  });

  test('el hito de ahora sigue a la fase del torneo y a tu equipo', () => {
    expect(hitoActual(torneo(), null)).toBe('equipo');
    expect(hitoActual(enCurso(), enCurso().equipos[0])).toBe('proximo');
    expect(hitoActual(enCurso(), null)).toBe('arbol');
    expect(hitoActual(enCurso(), equipo({ inscrito: true, eliminado: true }))).toBe('arbol');
    expect(hitoActual(torneo({ estado: 'FINALIZADO' }), null)).toBe('resultado');
    expect(hitoActual(torneo({ estado: 'CANCELADO' }), null)).toBe('resultado');
  });

  test('pendiente lo que todavía no existe: el árbol antes de cerrar, tu camino sin jugar', () => {
    expect(estadosDeLaRuta(torneo(), null)).toEqual({
      descripcion: 'disponible',
      equipo: 'actual',
      proximo: 'pendiente',
      arbol: 'pendiente',
      camino: 'pendiente',
      resultado: 'pendiente',
    });
    const t = enCurso();
    expect(estadosDeLaRuta(t, t.equipos[0])).toEqual({
      descripcion: 'disponible',
      equipo: 'disponible',
      proximo: 'actual',
      arbol: 'disponible',
      camino: 'disponible',
      resultado: 'pendiente',
    });
  });

  test('la lista marca el hito de ahora con aria-current y con palabras', () => {
    const t = enCurso();
    const ruta = rutaDelTorneo(t, t.equipos[0], { proximo: [document.createElement('p')] });
    const hitos = [...ruta.querySelectorAll('.torneo-ruta__hito')];
    expect(hitos).toHaveLength(6);
    expect(ruta.querySelectorAll('[aria-current="step"]')).toHaveLength(1);
    const actual = ruta.querySelector('[aria-current="step"]');
    expect(actual.dataset.hito).toBe('proximo');
    expect(actual.textContent).toContain(ESTADOS_DEL_HITO.actual);
    // Pendiente se dice, no solo se atenúa.
    expect(ruta.querySelector('[data-hito="resultado"]').textContent).toContain('Pendiente');
    // Cada hito es una sección con su título h2 (la portada lleva el h1).
    const titulo = actual.querySelector('h2');
    expect(titulo.textContent).toBe('Próximo encuentro');
    expect(actual.querySelector('section').getAttribute('aria-labelledby')).toBe(titulo.id);
    // La marca es un adorno.
    expect(actual.querySelector('.torneo-ruta__marca').getAttribute('aria-hidden')).toBe('true');
  });

  test('«Cómo se juega» con los datos del torneo, sin inventar', () => {
    const descripcion = descripcionDelTorneo(torneo());
    expect(descripcion.textContent).toContain('segunda oportunidad');
    expect(descripcion.textContent).toContain('a un solo encuentro');
    expect(descripcion.textContent).toContain('10 créditos');
    expect(descripcion.textContent).toContain('Inscripciones hasta');
    expect(descripcionDelTorneo(torneo({ costoInscripcion: 0 })).textContent).toContain('Gratis');
    // En curso ya no se anuncia el cierre de inscripciones.
    expect(descripcionDelTorneo(enCurso()).textContent).not.toContain('Inscripciones hasta');
  });

  test('los cupos como barra con su cifra escrita', () => {
    const cupos = cuposDelTorneo(torneo({ equiposInscritos: 3 }));
    expect(cupos.textContent).toContain('3 de 8');
    const riel = cupos.querySelector('[role="progressbar"]');
    expect(riel.getAttribute('aria-valuenow')).toBe('3');
    expect(riel.getAttribute('aria-valuemax')).toBe('8');
    expect(riel.getAttribute('aria-label')).toBe('Equipos inscritos en Copa Otono');
  });

  test('el duelo es un adorno: la frase «Contra …» ya lo dice', () => {
    const duelo = dueloDelEncuentro('Los Valientes', 'La Máquina');
    expect(duelo.getAttribute('aria-hidden')).toBe('true');
    expect(duelo.textContent).toContain('VS');
  });
});

describe('la tarjeta del tablón (TournamentCard)', () => {
  test('arte, distintivo, nombre, fase, cupos y «Ver torneo» como enlace a su ruta', () => {
    const tarjeta = tarjetaDeTorneo(torneo(), { href: '/torneos?torneo=t-1' });
    expect(tarjeta.dataset.torneoId).toBe('t-1');
    expect(tarjeta.dataset.estado).toBe('INSCRIPCIONES_ABIERTAS');
    expect(tarjeta.querySelector('.torneo-card__arte .distintivo').textContent).toBe(
      'Inscripciones abiertas',
    );
    const nombre = tarjeta.querySelector('h2');
    expect(nombre.textContent).toBe('Copa Otono');
    expect(tarjeta.getAttribute('aria-labelledby')).toBe(nombre.id);
    expect(tarjeta.querySelector('.torneo-card__fase').textContent).toMatch(/^Inscripciones hasta/);
    expect(tarjeta.querySelector('[role="progressbar"]')).not.toBeNull();
    expect(tarjeta.textContent).toContain('Doble eliminación');
    const ver = tarjeta.querySelector('a[data-accion="abrir"]');
    expect(ver.getAttribute('href')).toBe('/torneos?torneo=t-1');
    expect(ver.getAttribute('aria-label')).toBe('Ver torneo: Copa Otono');
  });

  test('en curso no hay barra de cupos: dice los equipos que juegan', () => {
    const tarjeta = tarjetaDeTorneo(enCurso());
    expect(tarjeta.querySelector('[role="progressbar"]')).toBeNull();
    expect(tarjeta.textContent).toContain('2 de 8');
  });

  test('un clic normal abre en la vista; con Ctrl se deja al navegador', () => {
    const alAbrir = jest.fn();
    const tarjeta = tarjetaDeTorneo(torneo(), { alAbrir, href: '/torneos?torneo=t-1' });
    document.body.replaceChildren(tarjeta);
    const ver = tarjeta.querySelector('a[data-accion="abrir"]');
    // Lo que decidió la tarjeta se mira al llegar el clic al documento; luego
    // se cancela, porque jsdom no sabe navegar.
    let cancelado = null;
    const espia = (evento) => {
      cancelado = evento.defaultPrevented;
      evento.preventDefault();
    };
    document.addEventListener('click', espia);

    ver.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, button: 0 }));
    expect(alAbrir).toHaveBeenCalledTimes(1);
    expect(cancelado).toBe(true);

    ver.dispatchEvent(
      new MouseEvent('click', { bubbles: true, cancelable: true, button: 0, ctrlKey: true }),
    );
    expect(alAbrir).toHaveBeenCalledTimes(1);
    expect(cancelado).toBe(false);
    document.removeEventListener('click', espia);
  });
});

describe('del tablón a la ruta y de vuelta, sin recargar', () => {
  test('«Ver torneo» oculta el tablón, enseña la ruta con su dirección y lleva el foco al título', async () => {
    const { nav } = montar(torneo());
    await asentarVarias();
    expect(zona('listado').dataset.cuantos).toBe('1');
    expect(zona('detalle').hidden).toBe(true);

    document.querySelector('[data-accion="abrir"]').click();
    await asentarVarias();

    expect(zona('tablon').hidden).toBe(true);
    expect(zona('encabezado').hidden).toBe(true);
    expect(zona('detalle').hidden).toBe(false);
    expect(document.querySelector('[data-vista]').dataset.modo).toBe('ruta');
    expect(nav.empujes).toEqual(['/torneos?torneo=t-1']);
    expect(document.title).toBe('Copa Otono · Torneo · The Nexus Battles VI');
    const titulo = zona('detalle').querySelector('h1');
    expect(titulo.textContent).toBe('Copa Otono');
    expect(document.activeElement).toBe(titulo);
    // La portada: fechas y cupos de un torneo abierto.
    expect(zona('fechas').textContent).toMatch(/^Publicado el .+ · Inscripciones hasta .+/);
    expect(zona('detalle').querySelector('.torneo-portada [role="progressbar"]')).not.toBeNull();
  });

  test('«Todos los torneos» vuelve al tablón, a la tarjeta de la que se vino', async () => {
    const { nav } = montar(torneo());
    await asentarVarias();
    document.querySelector('[data-accion="abrir"]').click();
    await asentarVarias();

    const volver = zona('detalle').querySelector('[data-accion="volver-a-torneos"]');
    expect(volver.getAttribute('href')).toBe('/torneos');
    volver.click();

    expect(zona('detalle').hidden).toBe(true);
    expect(zona('detalle').childElementCount).toBe(0);
    expect(zona('tablon').hidden).toBe(false);
    expect(zona('encabezado').hidden).toBe(false);
    expect(nav.empujes).toEqual(['/torneos?torneo=t-1', '/torneos']);
    expect(document.title).toBe('Torneo · The Nexus Battles VI');
    expect(document.activeElement).toBe(document.querySelector('[data-accion="abrir"]'));
  });

  test('atrás y adelante del navegador cambian de modo sin apilar direcciones', async () => {
    const { nav, fetchImpl } = montar(torneo());
    await asentarVarias();

    nav.irA('/torneos?torneo=t-1');
    await asentarVarias();
    expect(zona('detalle').hidden).toBe(false);
    expect(zona('detalle').querySelector('h1').textContent).toBe('Copa Otono');

    nav.irA('/torneos');
    await asentarVarias();
    expect(zona('detalle').hidden).toBe(true);
    expect(zona('tablon').hidden).toBe(false);

    expect(nav.empujes).toEqual([]);
    expect(
      fetchImpl.mock.calls.filter((c) => String(c[0]).endsWith('/api/v1/torneos/t-1')),
    ).toHaveLength(1);
  });

  test('con ?torneo= se entra directo a la ruta; el tablón se carga para volver', async () => {
    const { fetchImpl } = montar(torneo(), {
      torneoInicial: 't-1',
      nav: navegador('https://nexus.test/torneos?torneo=t-1'),
    });
    // Al momento: la ruta cargando, el tablón oculto.
    expect(zona('tablon').hidden).toBe(true);
    expect(zona('detalle').hidden).toBe(false);
    expect(zona('detalle').querySelector('[data-estado="cargando"]')).not.toBeNull();
    await asentarVarias();
    expect(zona('detalle').querySelector('h1').textContent).toBe('Copa Otono');
    expect(fetchImpl.mock.calls.some((c) => String(c[0]).endsWith('/api/v1/torneos'))).toBe(true);
  });

  test('un torneo que ya no está se dice en la ruta, una vez y sin «Reintentar»', async () => {
    document.body.innerHTML = VISTA;
    const nav = navegador('https://nexus.test/torneos?torneo=t-9');
    montarTorneos(document, {
      uid: UID,
      fetchImpl: servicio({
        'GET /api/v1/torneos': { cuerpo: [] },
        'GET /api/v1/torneos/t-9': {
          estado: 404,
          cuerpo: { title: 'No encontrado', motivo: 'NO_ENCONTRADO' },
        },
      }),
      torneoInicial: 't-9',
      historial: nav.historial,
      ubicacion: nav.ubicacion,
      ventana: nav.ventana,
    });
    await asentarVarias();
    const detalle = zona('detalle');
    expect(detalle.textContent).toContain('Ese torneo ya no está');
    expect(detalle.querySelector('[data-accion="reintentar"]')).toBeNull();
    expect(detalle.querySelector('[data-accion="volver-a-torneos"]')).not.toBeNull();
    expect(zona('aviso').hidden).toBe(true);
  });

  test('si los torneos no responden al abrir, la ruta ofrece reintentar', async () => {
    document.body.innerHTML = VISTA;
    const nav = navegador();
    montarTorneos(document, {
      uid: UID,
      fetchImpl: servicio({
        'GET /api/v1/torneos': { cuerpo: [] },
        'GET /api/v1/torneos/t-1': { estado: 503, cuerpo: null },
      }),
      torneoInicial: 't-1',
      historial: nav.historial,
      ubicacion: nav.ubicacion,
      ventana: nav.ventana,
    });
    await asentarVarias();
    expect(zona('detalle').querySelector('[data-accion="reintentar"]')).not.toBeNull();
    expect(zona('detalle').textContent).not.toMatch(/\b503\b/);
  });

  test('el formulario de crear torneo es del tablón: en la ruta no está', async () => {
    montar(torneo(), { rol: 'ADMINISTRADOR', uid: 'admin' });
    await asentarVarias();
    expect(zona('crear-torneo').hidden).toBe(false);
    document.querySelector('[data-accion="abrir"]').click();
    await asentarVarias();
    expect(zona('crear-torneo').hidden).toBe(true);
    // Las acciones de administración van en la portada.
    expect(
      zona('detalle').querySelector('.torneo-portada [data-zona="administracion"]'),
    ).not.toBeNull();
    zona('detalle').querySelector('[data-accion="volver-a-torneos"]').click();
    expect(zona('crear-torneo').hidden).toBe(false);
  });
});

describe('lo que va en cada hito', () => {
  test('sin equipo y con inscripciones abiertas: «Mi equipo» es donde estás, con el registro', async () => {
    montar(torneo({ equiposInscritos: 0 }), { torneoInicial: 't-1' });
    await asentarVarias();
    const mio = hito('equipo');
    expect(mio.getAttribute('aria-current')).toBe('step');
    expect(mio.querySelector('[data-zona="acciones"]').textContent).toContain(
      'Registra tu equipo de dos.',
    );
    expect(mio.querySelector('[data-zona="crear-equipo"]')).not.toBeNull();
    expect(hito('arbol').querySelector('[data-zona="arbol"]').textContent).toMatch(
      /se genera al cerrar/,
    );
    expect(hito('arbol').querySelector('[data-zona="equipos"]')).not.toBeNull();
    expect(hito('proximo').textContent).toContain('Cuando se cierren las inscripciones');
    // Sin equipo, la ruta no es «tu torneo».
    expect(document.querySelector('[data-zona="mi-torneo"]')).toBeNull();
  });

  test('con equipo sin inscribir: su bloque y el botón de inscribir, sin repetir la situación', async () => {
    montar(torneo({ equipos: [equipo()] }), { torneoInicial: 't-1' });
    await asentarVarias();
    const mio = hito('equipo');
    expect(mio.querySelector('[data-zona="mi-equipo"]').textContent).toContain('Los Valientes');
    expect(mio.querySelector('[data-zona="mi-equipo"] h4')).toBeNull();
    expect(mio.querySelector('[data-zona="situacion"]')).toBeNull();
    expect(mio.querySelector('[data-accion="inscribir"]').textContent).toContain('10 créditos');
    expect(document.querySelector('ol.torneo-ruta').dataset.zona).toBe('mi-torneo');
  });

  test('en curso con equipo: el próximo encuentro es donde estás, con el duelo y su sala', async () => {
    montar(enCurso(), { torneoInicial: 't-1' });
    await asentarVarias();
    const proximo = hito('proximo');
    expect(proximo.getAttribute('aria-current')).toBe('step');
    expect(proximo.querySelector('.torneo-duelo').textContent).toContain('La Máquina');
    expect(proximo.textContent).toContain('Contra La Máquina');
    expect(proximo.querySelector('[data-accion="jugar-mi-encuentro"]').getAttribute('href')).toBe(
      '../salas-partidas/crear-sala.html?torneo=t-1&encuentro=5',
    );
    // Con las inscripciones cerradas, «Mi equipo» no dice «están cerradas».
    expect(hito('equipo').querySelector('[data-zona="acciones"]')).toBeNull();
    expect(hito('equipo').querySelector('[data-zona="situacion"]').textContent).toBe(
      'Sigue en juego, sin derrotas',
    );
    expect(hito('camino').querySelectorAll('[data-zona="mi-camino"] li')).toHaveLength(1);
    expect(hito('arbol').querySelector('[data-zona="transmision"]')).not.toBeNull();
    expect(hito('arbol').querySelector('[data-llave="GANADORES"]')).not.toBeNull();
  });

  test('terminado: el resultado es donde estás, con el campeón y tu premio', async () => {
    const campeon = equipo({ inscrito: true, posicion: 1, estadoPago: 'COBRADO', pagadoPor: UID });
    const t = torneo({
      estado: 'FINALIZADO',
      campeonEquipoId: 'eq-1',
      equipos: [campeon, MAQUINA],
      premio: {
        creditosPorIntegrante: 500,
        epicaProductoId: null,
        estado: 'ENTREGADO',
        entregas: [
          { uid: UID, estado: 'ENTREGADO', creditosEntregados: true, epicaEntregada: false },
        ],
      },
    });
    montar(t, { torneoInicial: 't-1' });
    await asentarVarias();
    const resultado = hito('resultado');
    expect(resultado.getAttribute('aria-current')).toBe('step');
    expect(resultado.querySelector('[data-zona="campeon"]').textContent).toBe(
      'Campeón: Los Valientes',
    );
    expect(resultado.querySelector('[data-zona="mi-premio"]').textContent).toContain(
      'Recibiste tu premio',
    );
    // El premio para todos, en la portada; una sola vez en la vista.
    expect(document.querySelectorAll('[data-zona="premio"]')).toHaveLength(1);
    expect(document.querySelector('.torneo-portada [data-zona="premio"]')).not.toBeNull();
  });

  test('cancelado: el resultado lo dice con su motivo, y no se anuncia premio', async () => {
    const t = torneo({
      estado: 'CANCELADO',
      motivoCancelacion: 'Falla del servidor de partidas',
      premio: { creditosPorIntegrante: 500, epicaProductoId: null, estado: 'SIN_CAMPEON' },
    });
    montar(t, { torneoInicial: 't-1' });
    await asentarVarias();
    const resultado = hito('resultado');
    expect(resultado.textContent).toContain('El torneo se canceló');
    expect(resultado.querySelector('[data-zona="cancelacion"]').textContent).toBe(
      'Cancelado: Falla del servidor de partidas',
    );
    expect(document.querySelector('[data-zona="premio"]')).toBeNull();
  });
});

describe('los bloques de «tu torneo», sueltos', () => {
  test('en la ruta: sin títulos propios y con el premio aparte', () => {
    const t = enCurso();
    const bloques = bloquesDeMiTorneo(t, t.equipos[0], UID, { enRuta: true });
    expect(bloques.equipo.querySelector('h4')).toBeNull();
    expect(bloques.proximo.querySelector('.torneo-duelo')).not.toBeNull();
    expect(bloques.premio).toBeNull();
  });

  test('fuera de la ruta: con sus títulos y sin duelo', () => {
    const t = enCurso();
    const bloques = bloquesDeMiTorneo(t, t.equipos[0], UID);
    expect(bloques.equipo.querySelector('h4').textContent).toBe('Mi equipo');
    expect(bloques.proximo.querySelector('.torneo-duelo')).toBeNull();
  });
});
