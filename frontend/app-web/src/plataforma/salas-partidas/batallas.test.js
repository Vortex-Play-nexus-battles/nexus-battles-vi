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
  invitacionDeLaUrl,
  rutaDeVerificacion,
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
  test('reproduce la linea del diseno cuando no hay héroe de la IA', () => {
    expect(metaDeLaSala(sala())).toBe('4 de 6 jugadores · 320 créditos');
  });

  test('añade el sufijo de la IA solo cuando la hay', () => {
    expect(metaDeLaSala(sala({ incluirHeroeIA: true }))).toBe(
      '4 de 6 jugadores · 320 créditos · Con héroe de la IA',
    );
  });

  test('con varios cupos de la IA dice cuantos (HU-SAL-004)', () => {
    expect(metaDeLaSala(sala({ incluirHeroeIA: true, heroesIA: 3 }))).toBe(
      '4 de 6 jugadores · 320 créditos · Con 3 héroes de la IA',
    );
    expect(metaDeLaSala(sala({ incluirHeroeIA: true, heroesIA: 1 }))).toMatch(
      /Con héroe de la IA$/,
    );
  });

  test('una apuesta de cero se escribe igual: el diseno la pinta como 0 créditos', () => {
    expect(
      metaDeLaSala(sala({ ocupacion: 1, maximoParticipantes: 2, recompensaCreditos: 0 })),
    ).toBe('1 de 2 jugadores · 0 créditos');
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
      '4 de 6 jugadores · 320 créditos',
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

  test('sin salas muestra el estado vacío, no una rejilla en blanco', async () => {
    const raiz = preparar();
    montarBatallas(raiz, { listar: jest.fn().mockResolvedValue(pagina([])) });
    await asentar();

    expect(raiz.querySelector('[data-zona="estado"]').hidden).toBe(false);
    expect(raiz.querySelector('[data-zona="salas"]').hidden).toBe(true);
    expect(raiz.querySelector('[data-zona="estado"]').textContent).toContain(
      'No hay batallas abiertas',
    );
  });

  test('pulsar una sala sin apuesta la ingresa y avisa a quien monto la vista', async () => {
    const raiz = preparar();
    const ingresar = jest.fn().mockResolvedValue(sala({ ocupacion: 5 }));
    const alEntrar = jest.fn();

    // FI-R6 — sin recompensa comprometida no hay nada que confirmar y se
    // entra directo. Con apuesta el listado manda antes a la verificacion,
    // que es donde se ensena el heroe y cuanto va a costar.
    montarBatallas(raiz, {
      listar: jest.fn().mockResolvedValue(pagina([sala({ recompensaCreditos: 0 })])),
      ingresar,
      alEntrar,
    });
    await asentar();

    raiz.querySelector('[data-sala]').click();
    await asentar();

    // FI-R4: la firma lleva ahora el codigo de invitacion; en una sala
    // publica es null y el cliente no manda cuerpo.
    expect(ingresar).toHaveBeenCalledWith('11111111-1111-1111-1111-111111111111', {
      codigoInvitacion: null,
    });
    expect(alEntrar).toHaveBeenCalledWith(expect.objectContaining({ ocupacion: 5 }));
  });

  test('un rechazo por sala privada le dice el motivo a la persona', async () => {
    const raiz = preparar();
    const ingresar = jest.fn().mockRejectedValue(
      new ErrorDeApi(
        {
          type: 'https://nexusbattles.local/errores/sala-privada',
          title: 'Esta sala es privada',
          detail: 'Necesitas un código de invitación para entrar.',
          status: 403,
        },
        403,
      ),
    );

    montarBatallas(raiz, {
      listar: jest
        .fn()
        .mockResolvedValue(pagina([sala({ estado: 'PRIVADA', recompensaCreditos: 0 })])),
      ingresar,
    });
    await asentar();

    raiz.querySelector('[data-sala]').click();
    await asentar();

    // FI-R4 — sin la zona del formulario en el marcado (este DOM de prueba no
    // la tiene) el motivo sigue diciendose, que es lo que esta prueba
    // comprueba: que el rechazo llega a la persona con su texto.
    const estado = raiz.querySelector('[data-zona="estado"]');
    expect(estado.hidden).toBe(false);
    expect(estado.textContent).toContain('Necesitas un código de invitación');
  });

  // HU-DIS-003 · CA-02 y CA-03: si el inventario no responde al entrar, el
  // listado NO desaparece; se pinta Seccion degradada aparte y se puede
  // reintentar la misma sala.
  test('inventario degradado al entrar: el listado sigue y aparece Sección degradada con reintento', async () => {
    const raiz = preparar();
    const degradado = new ErrorDeApi(
      {
        type: 'https://nexusbattles.local/errores/seccion-no-disponible',
        title: 'Inventario no disponible temporalmente',
        status: 503,
        detail: 'La sección de Inventario no esta disponible temporalmente.',
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

    // Sin apuesta: la degradacion que se prueba es la del ingreso, no la del
    // paso de confirmacion (FI-R6).
    montarBatallas(raiz, {
      listar: jest.fn().mockResolvedValue(pagina([sala({ recompensaCreditos: 0 })])),
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
    expect(ingresar).toHaveBeenLastCalledWith(sala().id, { codigoInvitacion: null });
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
            detail: 'El servicio respondió 503.',
            status: 503,
          },
          503,
        ),
      ),
    });
    await asentar();

    expect(raiz.querySelector('[data-zona="estado"]').textContent).toContain(
      'El servicio respondió 503.',
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
  test('cuando la ocupacion alcanza el máximo la sala pasa a LLENA', () => {
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
    const privada = sala({
      id: PRIVADA,
      estado: 'PRIVADA',
      privada: true,
      ocupacion: 1,
      // Sin apuesta: lo que se prueba es el canal, no el paso de confirmacion.
      recompensaCreditos: 0,
    });
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

  test('sin sesión no hay canal, la vista lo dice y el listado funciona igual', async () => {
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
      conectarCanal: () => Promise.reject(new Error('El token de acceso no es válido.')),
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

  test('pinta título, detalle y tono, y destapa la zona', () => {
    document.body.innerHTML = ZONA;

    const mostrado = mostrarAvisoDeSala(document, {
      tono: 'advertencia',
      titulo: 'La sala se cerró',
      detalle: 'El anfitrion cancelo la sala. Se te devolvieron 150 créditos.',
    });

    const zona = document.querySelector('[data-zona="aviso-sala"]');
    expect(mostrado).toBe(true);
    expect(zona.hidden).toBe(false);
    expect(zona.className).toBe('aviso aviso--advertencia');
    expect(zona.querySelector('[data-zona="aviso-sala-titulo"]').textContent).toBe(
      'La sala se cerró',
    );
    expect(zona.querySelector('[data-zona="aviso-sala-detalle"]').textContent).toContain(
      '150 créditos',
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

/**
 * FI-R4 — una sala privada se puede usar.
 *
 * El 403 de sala privada se pintaba como error final («Esta sala es privada»)
 * y ahi se acababa: no habia donde escribir el codigo ni forma de mandarlo, y
 * `ingresarASala` hacia POST sin cuerpo. El backend acepta el codigo desde la
 * migracion V5.
 */
describe('FI-R4 - entrar a una sala privada con codigo', () => {
  const ID = '22222222-2222-2222-2222-222222222222';

  const HTML_CON_CODIGO = `
    <main id="vista">
      <p data-zona="subtitulo"></p>
      <select name="modalidad"><option value="">todas</option></select>
      <select name="estado"><option value="">todos</option></select>
      <div class="estado-vista" data-zona="estado"></div>
      <div data-zona="degradacion" data-seccion="Inventario" hidden></div>
      <form data-zona="pedir-codigo" hidden>
        <p data-zona="aviso-codigo"></p>
        <input name="codigoInvitacion" />
        <button type="submit">Entrar</button>
        <button type="button" data-accion="cerrar-codigo">Cancelar</button>
      </form>
      <p data-zona="canal" hidden></p>
      <div class="rejilla-salas" data-zona="salas" hidden></div>
      <nav class="paginacion" data-zona="paginacion" hidden></nav>
    </main>
  `;

  const privada = () => ({
    id: ID,
    estado: 'PRIVADA',
    modalidad: 'HASTA_SEIS',
    maximoParticipantes: 6,
    ocupacion: 1,
    recompensaCreditos: 0,
    incluirHeroeIA: false,
    privada: true,
    idAnfitrion: '33333333-3333-3333-3333-333333333333',
    participantes: [],
  });

  const rechazoPrivada = () =>
    new ErrorDeApi(
      {
        type: 'https://nexusbattles.local/errores/sala-privada',
        title: 'Esta sala es privada',
        detail: 'A una sala privada se entra por invitacion, no desde el listado.',
        status: 403,
      },
      403,
    );

  let raiz;
  const vaciarCola = async () => {
    for (let i = 0; i < 8; i++) {
      await Promise.resolve();
    }
  };
  const formulario = () => raiz.querySelector('[data-zona="pedir-codigo"]');
  const campo = () => raiz.querySelector('[name="codigoInvitacion"]');

  function montar(ingresar) {
    document.body.innerHTML = HTML_CON_CODIGO;
    raiz = document.getElementById('vista');
    const listar = jest.fn().mockResolvedValue({
      contenido: [privada()],
      pagina: 0,
      tamano: 12,
      totalElementos: 1,
      totalPaginas: 1,
    });
    const alEntrar = jest.fn();
    const vista = montarBatallas(raiz, { listar, ingresar, alEntrar });
    return { vista, alEntrar, listar };
  }

  test('el 403 de sala privada pide el codigo en vez de cerrar la puerta', async () => {
    const ingresar = jest.fn().mockRejectedValue(rechazoPrivada());
    montar(ingresar);
    await vaciarCola();

    raiz.querySelector(`[data-sala="${ID}"]`).click();
    await vaciarCola();

    expect(formulario().hidden).toBe(false);
    expect(formulario().dataset.salaInvitada).toBe(ID);
    // Y la rejilla sigue ahi: quien se equivoco de sala elige otra sin recargar.
    expect(raiz.querySelector('[data-zona="salas"]').hidden).toBe(false);
  });

  test('el codigo escrito se manda al servicio', async () => {
    const ingresar = jest
      .fn()
      .mockRejectedValueOnce(rechazoPrivada())
      .mockResolvedValueOnce({ ...privada(), ocupacion: 2 });
    const { alEntrar } = montar(ingresar);
    await vaciarCola();

    raiz.querySelector(`[data-sala="${ID}"]`).click();
    await vaciarCola();

    campo().value = 'WXYZ-2345';
    formulario().dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await vaciarCola();

    expect(ingresar).toHaveBeenNthCalledWith(2, ID, { codigoInvitacion: 'WXYZ-2345' });
    expect(alEntrar).toHaveBeenCalledTimes(1);
  });

  test('un codigo aceptado cierra el formulario', async () => {
    const ingresar = jest
      .fn()
      .mockRejectedValueOnce(rechazoPrivada())
      .mockResolvedValueOnce({ ...privada(), ocupacion: 2 });
    montar(ingresar);
    await vaciarCola();
    raiz.querySelector(`[data-sala="${ID}"]`).click();
    await vaciarCola();

    campo().value = 'WXYZ-2345';
    formulario().dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await vaciarCola();

    expect(formulario().hidden).toBe(true);
    expect(campo().value).toBe('');
  });

  test('un codigo que no vale lo dice y deja volver a intentarlo', async () => {
    const ingresar = jest.fn().mockRejectedValue(rechazoPrivada());
    montar(ingresar);
    await vaciarCola();
    raiz.querySelector(`[data-sala="${ID}"]`).click();
    await vaciarCola();

    campo().value = 'MALO-0000';
    formulario().dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await vaciarCola();

    expect(formulario().hidden).toBe(false);
    // El aviso cambia: la primera vez dice que pidas el codigo, la segunda que
    // el que escribiste no vale. Repetir el mismo texto haria dudar de si se
    // envio.
    expect(raiz.querySelector('[data-zona="aviso-codigo"]').textContent).toMatch(/no vale/i);
    expect(campo().value).toBe('MALO-0000');
  });

  test('un codigo vacio no molesta al servicio', async () => {
    const ingresar = jest.fn().mockRejectedValue(rechazoPrivada());
    montar(ingresar);
    await vaciarCola();
    raiz.querySelector(`[data-sala="${ID}"]`).click();
    await vaciarCola();
    expect(ingresar).toHaveBeenCalledTimes(1);

    campo().value = '   ';
    formulario().dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await vaciarCola();

    expect(ingresar).toHaveBeenCalledTimes(1);
  });

  test('enviar el codigo manda UN ingreso, no dos', async () => {
    // El defecto que encontro CI contra el backend real: el formulario llevaba
    // `data-sala`, y la escucha de las tarjetas esta delegada en toda la vista
    // con `closest('[data-sala]')`. Pulsar «Entrar con el codigo» disparaba dos
    // ingresos —el del formulario con codigo, y el de la delegacion sin el— y
    // el 403 del segundo llegaba despues, reescribiendo el aviso con el mensaje
    // de la primera vez. En una sala con apuesta habrian sido dos reservas.
    const ingresar = jest.fn().mockRejectedValue(rechazoPrivada());
    montar(ingresar);
    await vaciarCola();

    raiz.querySelector(`[data-sala="${ID}"]`).click();
    await vaciarCola();
    const trasAbrir = ingresar.mock.calls.length;

    campo().value = 'WXYZ-2345';
    formulario().querySelector('button[type="submit"]').click();
    await vaciarCola();

    expect(ingresar.mock.calls.length - trasAbrir).toBe(1);
    expect(ingresar).toHaveBeenLastCalledWith(ID, { codigoInvitacion: 'WXYZ-2345' });
  });

  test('un codigo rechazado no borra lo que la persona escribio', async () => {
    const ingresar = jest.fn().mockRejectedValue(rechazoPrivada());
    montar(ingresar);
    await vaciarCola();
    raiz.querySelector(`[data-sala="${ID}"]`).click();
    await vaciarCola();

    campo().value = 'WXYZ-2345';
    formulario().querySelector('button[type="submit"]').click();
    await vaciarCola();

    expect(campo().value).toBe('WXYZ-2345');
  });

  test('Cancelar cierra el formulario sin entrar a ninguna parte', async () => {
    const ingresar = jest.fn().mockRejectedValue(rechazoPrivada());
    const { alEntrar } = montar(ingresar);
    await vaciarCola();
    raiz.querySelector(`[data-sala="${ID}"]`).click();
    await vaciarCola();

    raiz.querySelector('[data-accion="cerrar-codigo"]').click();

    expect(formulario().hidden).toBe(true);
    expect(alEntrar).not.toHaveBeenCalled();
  });

  test('una sala publica entra sin pedir nada', async () => {
    const ingresar = jest.fn().mockResolvedValue({ ...privada(), privada: false, ocupacion: 2 });
    const { alEntrar } = montar(ingresar);
    await vaciarCola();

    raiz.querySelector(`[data-sala="${ID}"]`).click();
    await vaciarCola();

    expect(ingresar).toHaveBeenCalledWith(ID, { codigoInvitacion: null });
    expect(formulario().hidden).toBe(true);
    expect(alEntrar).toHaveBeenCalledTimes(1);
  });

  test('un 409 no pide codigo: escribirlo no arreglaria una sala llena', async () => {
    const ingresar = jest
      .fn()
      .mockRejectedValue(
        new ErrorDeApi(
          { type: 'urn:llena', title: 'Sala llena', detail: 'Ya esta completa.', status: 409 },
          409,
        ),
      );
    montar(ingresar);
    await vaciarCola();

    raiz.querySelector(`[data-sala="${ID}"]`).click();
    await vaciarCola();

    expect(formulario().hidden).toBe(true);
    expect(raiz.querySelector('[data-zona="estado"]').textContent).toMatch(/llena/i);
  });

  test('entrarA queda expuesto para el enlace de invitacion', async () => {
    const ingresar = jest.fn().mockResolvedValue({ ...privada(), ocupacion: 2 });
    const { vista, alEntrar } = montar(ingresar);
    await vaciarCola();

    await vista.entrarA(ID, 'WXYZ-2345');

    expect(ingresar).toHaveBeenCalledWith(ID, { codigoInvitacion: 'WXYZ-2345' });
    expect(alEntrar).toHaveBeenCalledTimes(1);
  });
});

describe('FI-R4 - invitacionDeLaUrl', () => {
  test('lee la sala y el codigo del enlace que reparte el anfitrion', () => {
    expect(invitacionDeLaUrl('?sala=abc&codigo=WXYZ-2345')).toEqual({
      idSala: 'abc',
      codigo: 'WXYZ-2345',
    });
  });

  test('un enlace sin codigo sigue valiendo: la sala puede ser publica', () => {
    expect(invitacionDeLaUrl('?sala=abc')).toEqual({ idSala: 'abc', codigo: null });
    expect(invitacionDeLaUrl('?sala=abc&codigo=')).toEqual({ idSala: 'abc', codigo: null });
  });

  test('sin sala no hay invitacion que seguir', () => {
    expect(invitacionDeLaUrl('')).toBeNull();
    expect(invitacionDeLaUrl('?codigo=WXYZ-2345')).toBeNull();
  });
});

/**
 * FI-R6 — RF-JUE-003 dentro del flujo real.
 *
 * `validacion-heroe.html` existia, funcionaba, estaba en la matriz de acceso
 * y **no la enlazaba nadie**: el unico sitio del repositorio que la nombraba
 * era la propia matriz. El flujo real iba del listado a `ingresarASala` y de
 * ahi a la sala, sin pasar por ninguna verificacion.
 *
 * El servidor si la exige: `IngresarASala` llama a `PuertaDeHeroe.comprobar`
 * antes que a nada, y rechaza con 422. Ese rechazo se pintaba en el listado
 * como un aviso rojo mas, sin decir que hacer.
 */
describe('FI-R6 - la verificacion de heroe esta en el camino', () => {
  const ID = '55555555-5555-5555-5555-555555555555';

  const HTML_R6 = `
    <main id="vista">
      <p data-zona="subtitulo"></p>
      <select name="modalidad"><option value="">todas</option></select>
      <select name="estado"><option value="">todos</option></select>
      <div class="estado-vista" data-zona="estado"></div>
      <div data-zona="degradacion" data-seccion="Inventario" hidden></div>
      <form data-zona="pedir-codigo" hidden>
        <p data-zona="aviso-codigo"></p>
        <input name="codigoInvitacion" />
        <button type="submit">Entrar</button>
        <button type="button" data-accion="cerrar-codigo">Cancelar</button>
      </form>
      <p data-zona="canal" hidden></p>
      <div class="rejilla-salas" data-zona="salas" hidden></div>
      <nav class="paginacion" data-zona="paginacion" hidden></nav>
    </main>
  `;

  const salaR6 = (cambios = {}) => ({
    id: ID,
    estado: 'ABIERTA',
    modalidad: 'HASTA_SEIS',
    maximoParticipantes: 6,
    ocupacion: 2,
    recompensaCreditos: 0,
    incluirHeroeIA: false,
    privada: false,
    idAnfitrion: '66666666-6666-6666-6666-666666666666',
    participantes: [],
    ...cambios,
  });

  const rechazoDeHeroe = (tipo) =>
    new ErrorDeApi(
      {
        type: `https://nexusbattles.local/errores/${tipo}`,
        title: 'No tienes un héroe equipado',
        detail: 'Equipa un héroe desde tu inventario antes de entrar a la sala.',
        status: 422,
      },
      422,
    );

  let raiz;
  const vaciarCola = async () => {
    for (let i = 0; i < 8; i++) {
      await Promise.resolve();
    }
  };

  function montar({ ingresar, sala: salaDelCaso = salaR6() }) {
    document.body.innerHTML = HTML_R6;
    raiz = document.getElementById('vista');
    const irAVerificacion = jest.fn();
    const alEntrar = jest.fn();
    const vista = montarBatallas(raiz, {
      listar: jest.fn().mockResolvedValue({
        contenido: [salaDelCaso],
        pagina: 0,
        tamano: 12,
        totalElementos: 1,
        totalPaginas: 1,
      }),
      ingresar,
      alEntrar,
      irAVerificacion,
    });
    return { vista, irAVerificacion, alEntrar };
  }

  test('rutaDeVerificacion apunta a la pantalla que explica el veredicto', () => {
    expect(rutaDeVerificacion(ID)).toBe(`./validacion-heroe.html?sala=${ID}`);
    expect(rutaDeVerificacion(ID, 'WXYZ-2345')).toBe(
      `./validacion-heroe.html?sala=${ID}&codigo=WXYZ-2345`,
    );
  });

  test('sin heroe equipado se va a la verificacion, no a un aviso rojo', async () => {
    const ingresar = jest.fn().mockRejectedValue(rechazoDeHeroe('heroe-no-equipado'));
    const { irAVerificacion } = montar({ ingresar });
    await vaciarCola();

    raiz.querySelector(`[data-sala="${ID}"]`).click();
    await vaciarCola();

    expect(irAVerificacion).toHaveBeenCalledWith(`./validacion-heroe.html?sala=${ID}`);
    // Y no se queda como error final del listado, que es donde antes moria:
    // un aviso rojo que decia el veredicto y no ofrecia nada que hacer.
    expect(raiz.querySelector('[data-zona="estado"]').textContent).not.toMatch(/h[eé]roe/i);
  });

  test('con el heroe ocupado, lo mismo: es el otro veredicto que el dialogo sabe explicar', async () => {
    const ingresar = jest.fn().mockRejectedValue(rechazoDeHeroe('heroe-ocupado'));
    const { irAVerificacion } = montar({ ingresar });
    await vaciarCola();

    raiz.querySelector(`[data-sala="${ID}"]`).click();
    await vaciarCola();

    expect(irAVerificacion).toHaveBeenCalledWith(`./validacion-heroe.html?sala=${ID}`);
  });

  test('una sala con apuesta se confirma ANTES de entrar: no se reserva nada a ciegas', async () => {
    // `IngresarASala` reserva los creditos antes de meter a nadie. El dialogo
    // ensena el heroe y cuanto va a costar; saltarselo seria comprometer el
    // saldo sin ensenarlo.
    const ingresar = jest.fn();
    const { irAVerificacion } = montar({
      ingresar,
      sala: salaR6({ recompensaCreditos: 320 }),
    });
    await vaciarCola();

    raiz.querySelector(`[data-sala="${ID}"]`).click();
    await vaciarCola();

    expect(irAVerificacion).toHaveBeenCalledWith(`./validacion-heroe.html?sala=${ID}`);
    expect(ingresar).not.toHaveBeenCalled();
  });

  test('sin apuesta no se interpone ningun paso: no hay nada que confirmar', async () => {
    const ingresar = jest.fn().mockResolvedValue(salaR6({ ocupacion: 3 }));
    const { irAVerificacion, alEntrar } = montar({ ingresar });
    await vaciarCola();

    raiz.querySelector(`[data-sala="${ID}"]`).click();
    await vaciarCola();

    expect(irAVerificacion).not.toHaveBeenCalled();
    expect(ingresar).toHaveBeenCalledTimes(1);
    expect(alEntrar).toHaveBeenCalledTimes(1);
  });

  test('una sala privada con apuesta lleva el codigo a la verificacion', async () => {
    const ingresar = jest.fn();
    const { vista, irAVerificacion } = montar({
      ingresar,
      sala: salaR6({ recompensaCreditos: 100, privada: true, estado: 'PRIVADA' }),
    });
    await vaciarCola();

    await vista.entrarA(ID, 'WXYZ-2345');

    expect(irAVerificacion).toHaveBeenCalledWith(
      `./validacion-heroe.html?sala=${ID}&codigo=WXYZ-2345`,
    );
    // Volver a pedir el codigo en la otra pantalla seria hacer repetir el
    // mismo tramite.
    expect(ingresar).not.toHaveBeenCalled();
  });

  test('un 409 sigue siendo un error del listado: no lo arregla el inventario', async () => {
    const ingresar = jest
      .fn()
      .mockRejectedValue(
        new ErrorDeApi(
          { type: 'urn:llena', title: 'Sala llena', detail: 'Ya esta completa.', status: 409 },
          409,
        ),
      );
    const { irAVerificacion } = montar({ ingresar });
    await vaciarCola();

    raiz.querySelector(`[data-sala="${ID}"]`).click();
    await vaciarCola();

    expect(irAVerificacion).not.toHaveBeenCalled();
    expect(raiz.querySelector('[data-zona="estado"]').textContent).toMatch(/llena/i);
  });

  test('un 422 de otro tipo no se manda al inventario', async () => {
    // Solo los dos tipos de RF-JUE-003 se arreglan yendo a equipar un heroe.
    const ingresar = jest
      .fn()
      .mockRejectedValue(
        new ErrorDeApi(
          { type: 'urn:otra-cosa', title: 'Rechazado', detail: 'Por otro motivo.', status: 422 },
          422,
        ),
      );
    const { irAVerificacion } = montar({ ingresar });
    await vaciarCola();

    raiz.querySelector(`[data-sala="${ID}"]`).click();
    await vaciarCola();

    expect(irAVerificacion).not.toHaveBeenCalled();
    expect(raiz.querySelector('[data-zona="estado"]').textContent).toMatch(/rechazado/i);
  });
});
