/**
 * Mi cuenta · Estadísticas — UXC-9 (§7.1.1, RF-USR-012).
 *
 * Qué se comprueba: que cada cifra sale del servidor (el total de partidas,
 * el resultado de cada una, los contadores de misiones), que el recuento de
 * resultados dice de cuántas partidas sale, y que cada bloque tiene sus
 * estados vacío y de error por separado —si misiones no responde, las
 * batallas se siguen viendo—.
 */

import { jest } from '@jest/globals';

import {
  COLUMNAS_DE_PARTIDAS,
  PARTIDAS_POR_PAGINA,
  fraseDelRecuento,
  montarEstadisticas,
  pintarMisiones,
  pintarPartidas,
  pintarTorneosYLogros,
} from './estadisticas-cuenta.js';
import {
  nombreDeModalidad,
  recuentoDeResultados,
  resultadoDePartida,
} from '../comun/ui/juego/partida.js';

function respuesta(cuerpo, { ok = true, status = 200 } = {}) {
  return Promise.resolve({
    ok,
    status,
    headers: { get: () => 'application/json' },
    json: () => Promise.resolve(cuerpo),
  });
}

/** Devuelve la respuesta que corresponda a cada ruta pedida. */
function fetchFalso(rutas) {
  return jest.fn((url) => {
    for (const [fragmento, valor] of Object.entries(rutas)) {
      if (String(url).includes(fragmento)) {
        return typeof valor === 'function' ? valor(url) : valor;
      }
    }
    return respuesta({ title: 'No encontrado' }, { ok: false, status: 404 });
  });
}

/** Espera a que terminen las promesas encadenadas del pintado. */
async function asentar() {
  for (let i = 0; i < 6; i += 1) {
    await Promise.resolve();
  }
}

const VICTORIA = {
  id: 'p-1',
  idSala: 's-1',
  modalidad: 'UNO_CONTRA_UNO',
  estado: 'FINALIZADA',
  resultado: 'VICTORIA',
  heroe: 'Guerrero Tanque',
  participantes: 2,
  iniciadaEn: '2026-09-20T18:00:00Z',
  finalizadaEn: '2026-09-20T18:12:00Z',
};
const EN_CURSO = {
  id: 'p-2',
  idSala: 's-2',
  modalidad: 'HASTA_SEIS',
  estado: 'EN_CURSO',
  resultado: null,
  heroe: 'Mago Fuego',
  participantes: 5,
  iniciadaEn: '2026-09-27T20:00:00Z',
  finalizadaEn: null,
};
const DERROTA = { ...VICTORIA, id: 'p-3', resultado: 'DERROTA', modalidad: 'CONTRA_IA' };

function pagina(contenido, extra = {}) {
  return {
    contenido,
    pagina: 0,
    tamano: PARTIDAS_POR_PAGINA,
    totalElementos: contenido.length,
    totalPaginas: 1,
    ...extra,
  };
}

describe('partida.js — el resultado dicho por el servidor', () => {
  test('cada resultado se dice con su palabra; en curso no es un resultado', () => {
    expect(resultadoDePartida(VICTORIA)).toEqual({ texto: 'Victoria', variante: 'victoria' });
    expect(resultadoDePartida(DERROTA)).toEqual({ texto: 'Derrota', variante: 'derrota' });
    expect(resultadoDePartida({ estado: 'FINALIZADA', resultado: 'EMPATE' }).texto).toBe('Empate');
    expect(resultadoDePartida(EN_CURSO)).toEqual({ texto: 'En curso', variante: 'en-juego' });
    // Terminada sin resultado (no debería pasar): no se inventa uno.
    expect(resultadoDePartida({ estado: 'FINALIZADA', resultado: null })).toEqual({
      texto: 'Terminada',
      variante: null,
    });
  });

  test('las modalidades del contrato tienen nombre; otra cosa no rompe', () => {
    expect(nombreDeModalidad('UNO_CONTRA_UNO')).toBe('1 contra 1');
    expect(nombreDeModalidad('CONTRA_IA')).toBe('Contra la IA');
    expect(nombreDeModalidad('HASTA_SEIS')).toBe('Hasta seis');
    expect(nombreDeModalidad(null)).toBe('Partida');
    expect(nombreDeModalidad(undefined, 'Sala de batalla')).toBe('Sala de batalla');
  });

  test('el recuento solo cuenta lo que recibe', () => {
    expect(recuentoDeResultados([VICTORIA, DERROTA, EN_CURSO, VICTORIA])).toEqual({
      victorias: 2,
      derrotas: 1,
      empates: 0,
      enCurso: 1,
    });
    expect(recuentoDeResultados(null)).toEqual({
      victorias: 0,
      derrotas: 0,
      empates: 0,
      enCurso: 0,
    });
  });

  test('la frase del recuento dice de cuántas partidas sale', () => {
    expect(fraseDelRecuento([VICTORIA, DERROTA, EN_CURSO])).toBe(
      'De las 3 de esta página: 1 victoria · 1 derrota · 0 empates · 1 en curso.',
    );
    expect(fraseDelRecuento([VICTORIA])).toBe(
      'De la partida de esta página: 1 victoria · 0 derrotas · 0 empates.',
    );
  });
});

describe('Tus batallas — GET /partidas/mias', () => {
  test('pide la primera página de dieciséis y pinta el total del servidor', async () => {
    const zona = document.createElement('div');
    const fetchImpl = fetchFalso({
      '/api/v1/partidas/mias': respuesta(
        pagina([VICTORIA, EN_CURSO], { totalElementos: 37, totalPaginas: 1 }),
      ),
    });

    await pintarPartidas(zona, { fetchImpl });

    const url = String(fetchImpl.mock.calls[0][0]);
    expect(url).toContain('/api/v1/partidas/mias?');
    expect(url).toContain('pagina=0');
    expect(url).toContain('tamano=16');
    // El total es el del servidor, no el de la página.
    expect(zona.textContent).toContain('Partidas jugadas');
    expect(zona.querySelector('.metrica__valor').textContent).toBe('37');
    expect(zona.querySelector('[data-zona="recuento"]').textContent).toBe(
      'De las 2 de esta página: 1 victoria · 0 derrotas · 0 empates · 1 en curso.',
    );
  });

  test('cada fila dice resultado, héroe, modalidad y jugadores; en curso se puede volver y la terminada se puede ver', async () => {
    const zona = document.createElement('div');
    const fetchImpl = fetchFalso({
      '/api/v1/partidas/mias': respuesta(pagina([VICTORIA, EN_CURSO])),
    });

    await pintarPartidas(zona, { fetchImpl });

    const filas = [...zona.querySelectorAll('tbody tr')];
    expect(filas).toHaveLength(2);
    expect(filas[0].querySelector('.distintivo').textContent).toBe('Victoria');
    expect(filas[0].querySelector('.distintivo').className).toContain('distintivo--victoria');
    expect(filas[0].textContent).toContain('Guerrero Tanque');
    expect(filas[0].textContent).toContain('1 contra 1');
    // Auditoría de DEV del 30-sep: la columna «Acción» salía vacía en las
    // terminadas. Ahora lleva a verlas, con su desenlace.
    const ver = filas[0].querySelector('a');
    expect(ver.textContent).toBe('Ver resultado');
    expect(ver.getAttribute('href')).toBe(
      '../plataforma/salas-partidas/sala-batalla.html?sala=s-1&partida=p-1',
    );

    expect(filas[1].querySelector('.distintivo').textContent).toBe('En curso');
    expect(filas[1].textContent).toContain('Hasta seis');
    const volver = filas[1].querySelector('a');
    expect(volver.textContent).toBe('Volver a la partida');
    expect(volver.getAttribute('href')).toBe(
      '../plataforma/salas-partidas/sala-batalla.html?sala=s-2',
    );
    // La tabla se desplaza dentro de su tarjeta y se alcanza con el teclado.
    const region = zona.querySelector('.tabla-envoltorio');
    expect(region.getAttribute('tabindex')).toBe('0');
    expect(region.getAttribute('role')).toBe('region');
  });

  test('el encabezado y la celda de cada columna comparten alineación (auditoría 30-sep: no casaban)', async () => {
    const zona = document.createElement('div');
    const fetchImpl = fetchFalso({
      '/api/v1/partidas/mias': respuesta(pagina([VICTORIA, EN_CURSO])),
    });

    await pintarPartidas(zona, { fetchImpl });

    const tabla = zona.querySelector('table[data-zona="mis-partidas"]');
    expect(tabla.classList.contains('tabla')).toBe(true);
    expect(tabla.classList.contains('tabla--datos')).toBe(true);
    const encabezados = [...tabla.querySelectorAll('thead th')];
    expect(encabezados.map((th) => th.textContent)).toEqual(
      COLUMNAS_DE_PARTIDAS.map((c) => c.titulo),
    );
    expect(encabezados.every((th) => th.getAttribute('scope') === 'col')).toBe(true);
    for (const fila of tabla.querySelectorAll('tbody tr')) {
      const celdas = [...fila.children];
      expect(celdas).toHaveLength(encabezados.length);
      celdas.forEach((td, i) => {
        const clase = COLUMNAS_DE_PARTIDAS[i].clase;
        if (clase) {
          expect(td.classList.contains(clase)).toBe(true);
          expect(encabezados[i].classList.contains(clase)).toBe(true);
        } else {
          expect(td.classList.contains('tabla__numero')).toBe(false);
          expect(td.classList.contains('tabla__accion')).toBe(false);
        }
      });
    }
    // «Jugadores» es una cifra; «Acción», el botón: los dos a la derecha.
    expect(encabezados[3].className).toBe('tabla__numero');
    expect(encabezados[5].className).toBe('tabla__accion');
    // «Cuándo» conserva su tono de metadato.
    expect(tabla.querySelector('tbody tr td:nth-child(5)').classList.contains('t-meta')).toBe(true);
  });

  test('una partida sin sala a la que ir no deja la celda vacía: lo dice', async () => {
    const zona = document.createElement('div');
    const fetchImpl = fetchFalso({
      '/api/v1/partidas/mias': respuesta(pagina([{ ...DERROTA, idSala: null }])),
    });

    await pintarPartidas(zona, { fetchImpl });

    const celda = zona.querySelector('tbody tr td:last-child');
    expect(celda.querySelector('a')).toBeNull();
    expect(celda.querySelector('[aria-label="Sin acción"]').textContent).toBe('—');
  });

  test('sin partidas: qué es, por qué está vacío y qué hacer', async () => {
    const zona = document.createElement('div');
    const fetchImpl = fetchFalso({ '/api/v1/partidas/mias': respuesta(pagina([])) });

    await pintarPartidas(zona, { fetchImpl });

    expect(zona.textContent).toContain('Todavía no has jugado ninguna batalla');
    const accion = zona.querySelector('a');
    expect(accion.textContent).toBe('Jugar una batalla');
    expect(accion.getAttribute('href')).toMatch(/crear-sala\.html$/);
  });

  test('si el servicio falla se dice con palabras y se puede reintentar', async () => {
    const zona = document.createElement('div');
    let intentos = 0;
    const fetchImpl = fetchFalso({
      '/api/v1/partidas/mias': () => {
        intentos += 1;
        return intentos === 1
          ? respuesta({ title: 'Bad Gateway' }, { ok: false, status: 502 })
          : respuesta(pagina([VICTORIA]));
      },
    });

    await pintarPartidas(zona, { fetchImpl });

    expect(zona.textContent).toContain('Tus partidas no están disponibles');
    expect(zona.textContent).not.toMatch(/502|Bad Gateway/);

    zona.querySelector('[data-accion="reintentar"], button').click();
    await asentar();

    expect(intentos).toBe(2);
    expect(zona.querySelectorAll('tbody tr')).toHaveLength(1);
  });

  test('con más de una página hay paginación con su nombre y pide la elegida', async () => {
    const zona = document.createElement('div');
    const fetchImpl = fetchFalso({
      '/api/v1/partidas/mias': (url) =>
        respuesta(
          pagina([VICTORIA], {
            totalElementos: 40,
            totalPaginas: 3,
            pagina: Number(new URL(url, 'http://x').searchParams.get('pagina')),
          }),
        ),
    });

    await pintarPartidas(zona, { fetchImpl });

    const nav = zona.querySelector('nav.paginacion');
    expect(nav.getAttribute('aria-label')).toBe('Páginas de tus partidas');
    [...nav.querySelectorAll('.paginacion__pagina')].find((b) => b.textContent === '2').click();
    await asentar();

    expect(String(fetchImpl.mock.calls.at(-1)[0])).toContain('pagina=1');
    expect(zona.querySelector('.paginacion__pagina[aria-current="page"]').textContent).toBe('2');
  });
});

describe('Tus misiones — GET /misiones/historial', () => {
  const HISTORIAL = {
    completadas: [
      {
        ejecucionId: 'e-1',
        misionId: 'm-1',
        nombre: 'La cripta',
        categoria: 'HISTORIA',
        terminadaEn: '2026-09-25T10:00:00Z',
        resultado: 'EXITO',
        duracionMs: 1000,
      },
    ],
    porCategoria: [
      { categoria: 'HISTORIA', completadas: 3, fallidas: 1 },
      { categoria: 'DESAFIO', completadas: 2, fallidas: 0 },
    ],
    mejoresTiempos: [],
    epicas: [{ nombre: 'Furia del Dragón', master: 'Máster Umbra', obtenidaEn: '2026-09-25' }],
    cadenas: [],
  };

  test('suma los contadores del servidor y cuenta las épicas', async () => {
    const zona = document.createElement('div');
    const fetchImpl = fetchFalso({ '/api/v1/misiones/historial': respuesta(HISTORIAL) });

    await pintarMisiones(zona, { fetchImpl });

    const cifras = [...zona.querySelectorAll('.metrica')].map((m) => [
      m.querySelector('.t-meta').textContent,
      m.querySelector('.metrica__valor').textContent,
    ]);
    expect(cifras).toEqual([
      ['Misiones cumplidas', '5'],
      ['Misiones fallidas', '1'],
      ['Épicas obtenidas', '1'],
    ]);
    expect(zona.querySelector('a').getAttribute('href')).toBe(
      '../contenido/misiones/misiones.html#historial',
    );
  });

  test('sin misiones terminadas lleva al tablón', async () => {
    const zona = document.createElement('div');
    const fetchImpl = fetchFalso({
      '/api/v1/misiones/historial': respuesta({
        completadas: [],
        porCategoria: [],
        mejoresTiempos: [],
        epicas: [],
        cadenas: [],
      }),
    });

    await pintarMisiones(zona, { fetchImpl });

    expect(zona.textContent).toContain('Todavía no has terminado ninguna misión');
    expect(zona.querySelector('a').textContent).toBe('Elegir una misión');
  });

  test('si misiones no responde, el bloque lo dice y ofrece reintentar', async () => {
    const zona = document.createElement('div');
    const fetchImpl = fetchFalso({
      '/api/v1/misiones/historial': respuesta({}, { ok: false, status: 503 }),
    });

    await pintarMisiones(zona, { fetchImpl });

    expect(zona.textContent).toContain('Tus misiones no están disponibles');
    expect(zona.querySelector('button').textContent).toContain('Reintentar');
  });
});

describe('Torneos y logros — lo que no tiene resumen', () => {
  test('dice por qué no hay cifra y adónde ir, sin «próximamente»', () => {
    const zona = document.createElement('div');

    pintarTorneosYLogros(zona);

    expect(zona.textContent).toContain('Torneos');
    expect(zona.textContent).toContain('Logros');
    expect(zona.textContent).not.toMatch(/próximamente/i);
    expect(zona.querySelector('a').textContent).toBe('Ir a Torneos');
    expect(zona.querySelector('a').getAttribute('href')).toMatch(/torneos\.html$/);
  });
});

describe('montarEstadisticas()', () => {
  test('los bloques fallan por separado: sin misiones, las batallas se ven', async () => {
    document.body.innerHTML = `
      <section data-zona="panel-estadisticas">
        <div data-zona="estadisticas-partidas"></div>
        <div data-zona="estadisticas-misiones"></div>
        <div data-zona="estadisticas-pendiente"></div>
      </section>`;
    const fetchImpl = fetchFalso({
      '/api/v1/partidas/mias': respuesta(pagina([VICTORIA])),
      '/api/v1/misiones/historial': respuesta({}, { ok: false, status: 502 }),
    });

    const vista = montarEstadisticas(document, { fetchImpl });
    await vista.recargar();

    expect(document.querySelectorAll('[data-zona="estadisticas-partidas"] tbody tr')).toHaveLength(
      1,
    );
    expect(document.querySelector('[data-zona="estadisticas-misiones"]').textContent).toContain(
      'Tus misiones no están disponibles',
    );
    expect(document.querySelector('[data-zona="estadisticas-pendiente"]').textContent).toContain(
      'Logros',
    );
  });
});
