/**
 * HU-SAL-002 — Vista del listado de batallas.
 *
 * Lo que se prueba es lo que la Pantalla 2 promete: que la tarjeta diga
 * exactamente los cuatro datos del diseno, que una sala llena se vea atenuada y
 * no se pueda pulsar, que una privada SI aparezca, y que los tres rechazos del
 * contrato lleguen a la persona con su motivo.
 */

// Con modulos ES, Jest NO inyecta `jest` como global: hay que importarlo.
import { jest } from '@jest/globals';

import {
  montarBatallas,
  mostrarAvisoDeSala,
  fichaEnVivo,
  metaDeLaSala,
  subtituloDeSalas,
  textoDePaginacion,
} from './batallas.js';
import { ErrorDeApi } from './cliente-salas.js';

const HTML = `
  <main id="vista">
    <p data-zona="subtitulo"></p>
    <select name="modalidad"><option value="">todas</option><option value="CONTRA_IA">IA</option></select>
    <select name="estado"><option value="">todos</option><option value="ABIERTA">Abierta</option></select>
    <div class="estado-vista" data-zona="estado"></div>
    <div data-zona="degradacion" data-seccion="Inventario" hidden></div>
    <p data-zona="canal" hidden></p>
    <div class="rejilla-salas" data-zona="salas" hidden></div>
    <nav class="paginacion" data-zona="paginacion" hidden></nav>
  </main>
`;

/** Sala minima con los campos que el contrato marca como obligatorios. */
function sala(cambios = {}) {
  return {
    id: '11111111-1111-1111-1111-111111111111',
    estado: 'ABIERTA',
    modalidad: 'HASTA_SEIS',
    maximoParticipantes: 6,
    ocupacion: 4,
    recompensaCreditos: 320,
    incluirHeroeIA: false,
    ...cambios,
  };
}

function pagina(contenido, cambios = {}) {
  return {
    contenido,
    pagina: 0,
    tamano: 16,
    totalElementos: contenido.length,
    totalPaginas: 1,
    ...cambios,
  };
}

function preparar() {
  document.body.innerHTML = HTML;
  return document.getElementById('vista');
}

/** Deja que se resuelvan las promesas encadenadas del montaje. */
const asentar = () => new Promise((resolve) => setTimeout(resolve, 0));

describe('metaDeLaSala', () => {
  test('reproduce la linea del diseno cuando no hay heroe de la IA', () => {
    expect(metaDeLaSala(sala())).toBe('4 de 6 jugadores · 320 creditos');
  });

  test('anade el sufijo de la IA solo cuando la hay', () => {
    expect(metaDeLaSala(sala({ incluirHeroeIA: true }))).toBe(
      '4 de 6 jugadores · 320 creditos · Con heroe de la IA',
    );
  });

  test('con varios cupos de la IA dice cuantos (HU-SAL-004)', () => {
    expect(metaDeLaSala(sala({ incluirHeroeIA: true, heroesIA: 3 }))).toBe(
      '4 de 6 jugadores · 320 creditos · Con 3 heroes de la IA',
    );
    expect(metaDeLaSala(sala({ incluirHeroeIA: true, heroesIA: 1 }))).toMatch(
      /Con heroe de la IA$/,
    );
  });

  test('una apuesta de cero se escribe igual: el diseno la pinta como 0 creditos', () => {
    expect(
      metaDeLaSala(sala({ ocupacion: 1, maximoParticipantes: 2, recompensaCreditos: 0 })),
    ).toBe('1 de 2 jugadores · 0 creditos');
  });
});

describe('textos de la pantalla', () => {
  test('el subtitulo lleva el total real', () => {
    expect(subtituloDeSalas(38)).toBe('38 salas abiertas ahora mismo');
  });

  test('con una sola sala no dice "1 salas"', () => {
    expect(subtituloDeSalas(1)).toBe('1 sala abierta ahora mismo');
  });

  test('la paginacion dice cuantos de cuantos, como exige el componente', () => {
    expect(textoDePaginacion({ contenido: new Array(16), totalElementos: 38 })).toBe(
      'Mostrando 16 de 38 salas',
    );
  });
});

describe('montarBatallas', () => {
  test('pinta una tarjeta por sala con su insignia y su meta', async () => {
    const raiz = preparar();
    const listar = jest.fn().mockResolvedValue(pagina([sala()]));

    montarBatallas(raiz, { listar });
    await asentar();

    const tarjetas = raiz.querySelectorAll('[data-sala]');
    expect(tarjetas).toHaveLength(1);
    expect(tarjetas[0].querySelector('.distintivo').textContent).toBe('Abierta');
    expect(tarjetas[0].querySelector('.tarjeta__meta').textContent).toBe(
      '4 de 6 jugadores · 320 creditos',
    );
  });

  test('la tarjeta no muestra nada mas que la insignia y la meta', async () => {
    const raiz = preparar();
    montarBatallas(raiz, { listar: jest.fn().mockResolvedValue(pagina([sala()])) });
    await asentar();

    // Dos hijos exactos. Si alguien anade un nombre de sala, un apodo o un
    // heroe, esta prueba lo detiene: el diseno no los tiene.
    expect(raiz.querySelector('[data-sala]').children).toHaveLength(2);
  });

  test('una sala llena va atenuada y no es pulsable', async () => {
    const raiz = preparar();
    const ingresar = jest.fn();
    montarBatallas(raiz, {
      listar: jest.fn().mockResolvedValue(pagina([sala({ estado: 'LLENA', ocupacion: 6 })])),
      ingresar,
    });
    await asentar();

    const tarjeta = raiz.querySelector('[data-sala]');
    expect(tarjeta.className).toContain('tarjeta--bloqueada');
    expect(tarjeta.className).not.toContain('tarjeta--pulsable');
    expect(tarjeta.disabled).toBe(true);

    tarjeta.click();
    await asentar();
    expect(ingresar).not.toHaveBeenCalled();
  });

  test('una sala privada aparece en el listado con su insignia', async () => {
    const raiz = preparar();
    montarBatallas(raiz, {
      listar: jest.fn().mockResolvedValue(pagina([sala({ estado: 'PRIVADA' })])),
    });
    await asentar();

    const tarjeta = raiz.querySelector('[data-sala]');
    expect(tarjeta.querySelector('.distintivo').textContent).toBe('Privada');
    expect(tarjeta.disabled).toBe(false);
  });

  test('sin salas muestra el estado vacio, no una rejilla en blanco', async () => {
    const raiz = preparar();
    montarBatallas(raiz, { listar: jest.fn().mockResolvedValue(pagina([])) });
    await asentar();

    expect(raiz.querySelector('[data-zona="estado"]').hidden).toBe(false);
    expect(raiz.querySelector('[data-zona="salas"]').hidden).toBe(true);
    expect(raiz.querySelector('[data-zona="estado"]').textContent).toContain(
      'No hay batallas abiertas',
    );
  });

  test('pulsar una sala la ingresa y avisa a quien monto la vista', async () => {
    const raiz = preparar();
    const ingresar = jest.fn().mockResolvedValue(sala({ ocupacion: 5 }));
    const alEntrar = jest.fn();

    montarBatallas(raiz, {
      listar: jest.fn().mockResolvedValue(pagina([sala()])),
      ingresar,
      alEntrar,
    });
    await asentar();

    raiz.querySelector('[data-sala]').click();
    await asentar();

    expect(ingresar).toHaveBeenCalledWith('11111111-1111-1111-1111-111111111111');
    expect(alEntrar).toHaveBeenCalledWith(expect.objectContaining({ ocupacion: 5 }));
  });

  test('un rechazo por sala privada le dice el motivo a la persona', async () => {
    const raiz = preparar();
    const ingresar = jest.fn().mockRejectedValue(
      new ErrorDeApi(
        {
          type: 'https://nexusbattles.local/errores/sala-privada',
          title: 'Esta sala es privada',
          detail: 'Necesitas un codigo de invitacion para entrar.',
          status: 403,
        },
        403,
      ),
    );

    montarBatallas(raiz, {
      listar: jest.fn().mockResolvedValue(pagina([sala({ estado: 'PRIVADA' })])),
      ingresar,
    });
    await asentar();

    raiz.querySelector('[data-sala]').click();
    await asentar();

    const estado = raiz.querySelector('[data-zona="estado"]');
    expect(estado.hidden).toBe(false);
    expect(estado.textContent).toContain('Necesitas un codigo de invitacion');
  });

  // HU-DIS-003 · CA-02 y CA-03: si el inventario no responde al entrar, el
  // listado NO desaparece; se pinta Seccion degradada aparte y se puede
  // reintentar la misma sala.
  test('inventario degradado al entrar: el listado sigue y aparece Seccion degradada con reintento', async () => {
    const raiz = preparar();
    const degradado = new ErrorDeApi(
      {
        type: 'https://nexusbattles.local/errores/seccion-no-disponible',
        title: 'Inventario no disponible temporalmente',
        status: 503,
        detail: 'La seccion de Inventario no esta disponible temporalmente.',
        seccion: 'Inventario',
        reintentarEnSegundos: 3,
      },
      503,
    );
    const ingresar = jest
      .fn()
      .mockRejectedValueOnce(degradado)
      .mockResolvedValueOnce(sala({ ocupacion: 5 }));
    const alEntrar = jest.fn();

    montarBatallas(raiz, {
      listar: jest.fn().mockResolvedValue(pagina([sala()])),
      ingresar,
      alEntrar,
    });
    await asentar();

    raiz.querySelector('[data-sala]').click();
    await asentar();

    const degradada = raiz.querySelector('[data-zona="degradacion"] .seccion-degradada');
    expect(degradada).not.toBeNull();
    expect(degradada.textContent).toContain('Inventario no disponible temporalmente');
    expect(raiz.querySelector('[data-zona="salas"]').hidden).toBe(false);
    expect(raiz.querySelector('[data-zona="estado"]').hidden).toBe(true);
    expect(alEntrar).not.toHaveBeenCalled();

    degradada.querySelector('.seccion-degradada__reintentar').click();
    await asentar();

    expect(ingresar).toHaveBeenCalledTimes(2);
    expect(ingresar).toHaveBeenLastCalledWith(sala().id);
    expect(raiz.querySelector('[data-zona="degradacion"] .seccion-degradada')).toBeNull();
    expect(alEntrar).toHaveBeenCalledWith(expect.objectContaining({ ocupacion: 5 }));
  });

  test('un fallo al listar no deja la vista en blanco', async () => {
    const raiz = preparar();
    montarBatallas(raiz, {
      listar: jest.fn().mockRejectedValue(
        new ErrorDeApi(
          {
            title: 'No se pudo cargar el listado',
            detail: 'El servicio respondio 503.',
            status: 503,
          },
          503,
        ),
      ),
    });
    await asentar();

    expect(raiz.querySelector('[data-zona="estado"]').textContent).toContain(
      'El servicio respondio 503.',
    );
  });

  test('cambiar un filtro vuelve a la primera pagina y consulta de nuevo', async () => {
    const raiz = preparar();
    const listar = jest.fn().mockResolvedValue(pagina([sala()], { totalPaginas: 3 }));

    montarBatallas(raiz, { listar });
    await asentar();

    raiz.querySelector('[data-pagina="2"]').click();
    await asentar();
    expect(listar).toHaveBeenLastCalledWith(expect.objectContaining({ pagina: 2 }));

    const filtro = raiz.querySelector('[name="modalidad"]');
    filtro.value = 'CONTRA_IA';
    filtro.dispatchEvent(new Event('change'));
    await asentar();

    expect(listar).toHaveBeenLastCalledWith(
      expect.objectContaining({ pagina: 0, modalidad: 'CONTRA_IA' }),
    );
  });

  test('la pagina en curso se marca para los lectores de pantalla', async () => {
    const raiz = preparar();
    montarBatallas(raiz, {
      listar: jest.fn().mockResolvedValue(pagina([sala()], { totalPaginas: 3 })),
    });
    await asentar();

    const activa = raiz.querySelectorAll('[aria-current="page"]');
    expect(activa).toHaveLength(1);
    expect(activa[0].textContent).toBe('1');
  });
});

/** Cliente de canal de mentira: registra suscripciones y permite entregar avisos. */
function canalDeMentira() {
  const suscripciones = new Map();
  return {
    suscripciones,
    alCerrar: null,
    suscribir(destino, alRecibir) {
      suscripciones.set(destino, alRecibir);
      return `sub-${suscripciones.size}`;
    },
    entregar(destino, aviso) {
      suscripciones.get(destino)?.(aviso);
    },
  };
}

const OTRA = '22222222-2222-2222-2222-222222222222';
const PRIVADA = '33333333-3333-3333-3333-333333333333';
const JUGADOR = 'bbbbbbbb-0000-0000-0000-000000000002';

describe('fichaEnVivo', () => {
  test('cuando la ocupacion alcanza el maximo la sala pasa a LLENA', () => {
    const viva = fichaEnVivo(sala({ ocupacion: 5, maximoParticipantes: 6 }), {
      ocupacion: { actual: 6, maximo: 6 },
    });
    expect(viva.ocupacion).toBe(6);
    expect(viva.estado).toBe('LLENA');
  });

  test('una sala privada no cambia de estado aunque se llene: PRIVADA manda', () => {
    const viva = fichaEnVivo(sala({ estado: 'PRIVADA', privada: true }), {
      ocupacion: { actual: 6, maximo: 6 },
    });
    expect(viva.estado).toBe('PRIVADA');
  });
});

describe('canal en tiempo real en el listado', () => {
  test('con canal, cada sala publica visible se sigue y la tarjeta se actualiza al llegar un ingreso', async () => {
    const raiz = preparar();
    const canal = canalDeMentira();
    montarBatallas(raiz, {
      listar: jest.fn().mockResolvedValue(pagina([sala(), sala({ id: OTRA, ocupacion: 1 })])),
      conectarCanal: () => Promise.resolve(canal),
    });
    await asentar();

    expect([...canal.suscripciones.keys()]).toEqual([
      `/tema/salas/${sala().id}`,
      `/tema/salas/${OTRA}`,
    ]);
    expect(raiz.querySelector('[data-zona="canal"]').dataset.estado).toBe('conectado');

    canal.entregar(`/tema/salas/${OTRA}`, {
      tipo: 'sala.participante.ingreso',
      idSala: OTRA,
      idJugador: JUGADOR,
      ocupacion: { actual: 2, maximo: 6 },
    });

    expect(raiz.querySelector(`[data-sala="${OTRA}"] .tarjeta__meta`).textContent).toContain(
      '2 de 6 jugadores',
    );
    expect(raiz.querySelector(`[data-sala="${sala().id}"] .tarjeta__meta`).textContent).toContain(
      '4 de 6 jugadores',
    );
  });

  test('una sala que se llena en vivo deja de ser pulsable', async () => {
    const raiz = preparar();
    const canal = canalDeMentira();
    montarBatallas(raiz, {
      listar: jest.fn().mockResolvedValue(pagina([sala({ ocupacion: 5 })])),
      conectarCanal: () => Promise.resolve(canal),
    });
    await asentar();

    canal.entregar(`/tema/salas/${sala().id}`, {
      tipo: 'sala.participante.ingreso',
      idSala: sala().id,
      idJugador: JUGADOR,
      ocupacion: { actual: 6, maximo: 6 },
    });

    const tarjeta = raiz.querySelector(`[data-sala="${sala().id}"]`);
    expect(tarjeta.disabled).toBe(true);
    expect(tarjeta.dataset.estado).toBe('LLENA');
  });

  test('las salas privadas ajenas no se siguen desde el listado', async () => {
    const raiz = preparar();
    const canal = canalDeMentira();
    montarBatallas(raiz, {
      listar: jest
        .fn()
        .mockResolvedValue(
          pagina([sala(), sala({ id: PRIVADA, estado: 'PRIVADA', privada: true })]),
        ),
      conectarCanal: () => Promise.resolve(canal),
    });
    await asentar();

    expect(canal.suscripciones.has(`/tema/salas/${PRIVADA}`)).toBe(false);
    expect(canal.suscripciones.has(`/tema/salas/${sala().id}`)).toBe(true);
  });

  test('al entrar a una sala privada, ya como participante, se empieza a seguir', async () => {
    const raiz = preparar();
    const canal = canalDeMentira();
    const privada = sala({ id: PRIVADA, estado: 'PRIVADA', privada: true, ocupacion: 1 });
    montarBatallas(raiz, {
      listar: jest.fn().mockResolvedValue(pagina([privada])),
      ingresar: jest.fn().mockResolvedValue({ ...privada, ocupacion: 2, participantes: [JUGADOR] }),
      conectarCanal: () => Promise.resolve(canal),
    });
    await asentar();

    raiz.querySelector(`[data-sala="${PRIVADA}"]`).click();
    await asentar();

    expect(canal.suscripciones.has(`/tema/salas/${PRIVADA}`)).toBe(true);
    expect(raiz.querySelector(`[data-sala="${PRIVADA}"] .tarjeta__meta`).textContent).toContain(
      '2 de 6 jugadores',
    );
  });

  test('sin sesion no hay canal, la vista lo dice y el listado funciona igual', async () => {
    const raiz = preparar();
    montarBatallas(raiz, {
      listar: jest.fn().mockResolvedValue(pagina([sala()])),
      conectarCanal: () => Promise.resolve(null),
    });
    await asentar();

    expect(raiz.querySelector('[data-zona="canal"]').dataset.estado).toBe('sin-sesion');
    expect(raiz.querySelectorAll('[data-sala]')).toHaveLength(1);
  });

  test('si el canal se rechaza, se dice que consecuencia tiene, no cual fue el error', async () => {
    // UX-R3.4 — decía «Canal en tiempo real no disponible: El token de acceso
    // no es valido.»: el motivo tecnico del fallo, dirigido a quien programa,
    // en la pantalla de quien queria jugar. Y no decía lo unico que le importa
    // a quien lo lee: que el listado SIGUE funcionando, solo que no se
    // actualiza solo. El motivo no se pierde — baja a la consola.
    const avisos = jest.spyOn(console, 'warn').mockImplementation(() => {});
    const raiz = preparar();
    montarBatallas(raiz, {
      listar: jest.fn().mockResolvedValue(pagina([sala()])),
      conectarCanal: () => Promise.reject(new Error('El token de acceso no es valido.')),
    });
    await asentar();

    const zona = raiz.querySelector('[data-zona="canal"]');
    expect(zona.dataset.estado).toBe('error');
    expect(zona.textContent).toContain('no se actualizan solas');
    expect(zona.textContent).toContain('El listado funciona');
    expect(zona.textContent).not.toContain('token');
    // Y el listado, efectivamente, sigue ahi.
    expect(raiz.querySelectorAll('[data-sala]')).toHaveLength(1);
    expect(avisos.mock.calls.flat().join(' ')).toContain('canal en tiempo real');
    avisos.mockRestore();
  });

  test('sin el puerto del canal, la vista se comporta como siempre', async () => {
    const raiz = preparar();
    montarBatallas(raiz, { listar: jest.fn().mockResolvedValue(pagina([sala()])) });
    await asentar();

    expect(raiz.querySelector('[data-zona="canal"]').dataset.estado).toBe('sin-sesion');
  });
});

// ===========================================================================
// HU-SAL-006 — por que se volvio al listado
// ===========================================================================

describe('mostrarAvisoDeSala', () => {
  const ZONA = `
    <div class="aviso" data-zona="aviso-sala" hidden>
      <p class="aviso__titulo" data-zona="aviso-sala-titulo"></p>
      <p class="t-cuerpo" data-zona="aviso-sala-detalle"></p>
    </div>
  `;

  test('pinta titulo, detalle y tono, y destapa la zona', () => {
    document.body.innerHTML = ZONA;

    const mostrado = mostrarAvisoDeSala(document, {
      tono: 'advertencia',
      titulo: 'La sala se cerro',
      detalle: 'El anfitrion cancelo la sala. Se te devolvieron 150 creditos.',
    });

    const zona = document.querySelector('[data-zona="aviso-sala"]');
    expect(mostrado).toBe(true);
    expect(zona.hidden).toBe(false);
    expect(zona.className).toBe('aviso aviso--advertencia');
    expect(zona.querySelector('[data-zona="aviso-sala-titulo"]').textContent).toBe(
      'La sala se cerro',
    );
    expect(zona.querySelector('[data-zona="aviso-sala-detalle"]').textContent).toContain(
      '150 creditos',
    );
  });

  test('sin aviso no toca nada; sin detalle lo esconde; un tono desconocido cae a info', () => {
    document.body.innerHTML = ZONA;

    expect(mostrarAvisoDeSala(document, null)).toBe(false);
    expect(document.querySelector('[data-zona="aviso-sala"]').hidden).toBe(true);

    mostrarAvisoDeSala(document, { tono: 'raro', titulo: 'Saliste de la sala.' });
    const zona = document.querySelector('[data-zona="aviso-sala"]');
    expect(zona.className).toBe('aviso aviso--info');
    expect(zona.querySelector('[data-zona="aviso-sala-detalle"]').hidden).toBe(true);
  });
});
