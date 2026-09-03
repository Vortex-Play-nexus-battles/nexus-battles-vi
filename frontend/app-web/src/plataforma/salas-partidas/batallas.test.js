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

import { montarBatallas, metaDeLaSala, subtituloDeSalas, textoDePaginacion } from './batallas.js';
import { ErrorDeApi } from './cliente-salas.js';

const HTML = `
  <main id="vista">
    <p data-zona="subtitulo"></p>
    <select name="modalidad"><option value="">todas</option><option value="CONTRA_IA">IA</option></select>
    <select name="estado"><option value="">todos</option><option value="ABIERTA">Abierta</option></select>
    <div class="estado-vista" data-zona="estado"></div>
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
