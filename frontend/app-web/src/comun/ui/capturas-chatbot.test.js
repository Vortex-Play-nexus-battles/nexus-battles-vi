/**
 * Capturas de pantalla de una solicitud de soporte (ms-chatbot.yaml 1.14.0,
 * §7.4.6): la lista con «Ver captura» y el selector para adjuntarlas.
 */

import { jest } from '@jest/globals';

import {
  FORMATOS_DE_CAPTURA,
  MAXIMO_DE_CAPTURAS,
  TAMANO_MAXIMO_DE_CAPTURA,
  TEXTOS_CAPTURAS,
  crearRegistroDeImagenes,
  descripcionDeCaptura,
  listaDeCapturas,
  pesoLegible,
  rechazoDeCaptura,
  selectorDeCaptura,
} from './capturas-chatbot.js';

const esperar = () => new Promise((resolver) => setTimeout(resolver, 0));

const CAPTURAS = [
  {
    capturaId: 'c-1',
    tipo: 'image/png',
    tamano: 348_160,
    ancho: 1280,
    alto: 720,
    subidaEn: '2026-10-14T15:00:00Z',
  },
  {
    capturaId: 'c-2',
    tipo: 'image/jpeg',
    tamano: 1_468_006,
    ancho: 800,
    alto: 600,
    subidaEn: '2026-10-14T15:01:00Z',
  },
];

function registroFalso() {
  let n = 0;
  const liberadas = [];
  const registro = crearRegistroDeImagenes({
    crearUrl: () => {
      n += 1;
      return `blob:captura-${n}`;
    },
    liberarUrl: (url) => liberadas.push(url),
  });
  return { registro, liberadas };
}

function montarLista({ leer = jest.fn(async () => new Blob(['x'], { type: 'image/png' })) } = {}) {
  const { registro, liberadas } = registroFalso();
  const lista = listaDeCapturas({ capturas: CAPTURAS, asunto: 'No carga', leer, registro });
  document.body.replaceChildren(lista);
  const ver = (n) => lista.querySelectorAll('[data-accion="ver-captura"]')[n - 1];
  return { lista, leer, registro, liberadas, ver };
}

describe('lo que se dice de una captura', () => {
  test('formato, medidas y peso, como los lee una persona', () => {
    expect(descripcionDeCaptura(CAPTURAS[0])).toBe('PNG · 1280 × 720 · 340 KB');
    expect(descripcionDeCaptura(CAPTURAS[1])).toBe('JPEG · 800 × 600 · 1,4 MB');
    expect(descripcionDeCaptura({ tipo: 'image/gif' })).toBe('Imagen');
  });

  test('el peso, en B, KB o MB', () => {
    expect(pesoLegible(512)).toBe('512 B');
    expect(pesoLegible(2048)).toBe('2 KB');
    expect(pesoLegible(TAMANO_MAXIMO_DE_CAPTURA)).toBe('2,0 MB');
  });
});

describe('rechazoDeCaptura: lo que seguro no pasa, antes de subirlo', () => {
  test.each([
    [null, 'vacia'],
    [{ size: 0, type: 'image/png' }, 'vacia'],
    [{ size: 10, type: 'image/gif' }, 'tipo'],
    [{ size: 10, type: 'image/svg+xml' }, 'tipo'],
    [{ size: TAMANO_MAXIMO_DE_CAPTURA + 1, type: 'image/png' }, 'tamano'],
    [{ size: TAMANO_MAXIMO_DE_CAPTURA, type: 'image/jpeg' }, null],
    // Sin tipo (el sistema no lo reconoce): lo decide el servicio por sus bytes.
    [{ size: 10, type: '' }, null],
  ])('%#', (archivo, rechazo) => {
    expect(rechazoDeCaptura(archivo)).toBe(rechazo);
  });

  test('las cifras de D-CHA-12', () => {
    expect(MAXIMO_DE_CAPTURAS).toBe(3);
    expect(TAMANO_MAXIMO_DE_CAPTURA).toBe(2 * 1024 * 1024);
    expect(FORMATOS_DE_CAPTURA).toBe('image/png,image/jpeg');
  });
});

describe('crearRegistroDeImagenes', () => {
  test('libera de una vez los enlaces que creó, y solo una vez', () => {
    const { registro, liberadas } = registroFalso();
    registro.crear(new Blob(['a']));
    registro.crear(new Blob(['b']));

    registro.liberar();
    registro.liberar();

    expect(liberadas).toEqual(['blob:captura-1', 'blob:captura-2']);
  });

  test('sin URL.createObjectURL (jsdom) no rompe ni guarda nada', () => {
    const liberar = jest.fn();
    const registro = crearRegistroDeImagenes({ crearUrl: () => '', liberarUrl: liberar });

    expect(registro.crear(new Blob(['a']))).toBe('');
    registro.liberar();
    expect(liberar).not.toHaveBeenCalled();
  });
});

describe('listaDeCapturas', () => {
  test('cada captura con su botón, plegado, y lo que es; no se descarga nada al pintar', () => {
    const { lista, leer, ver } = montarLista();

    expect(lista.getAttribute('aria-label')).toBe('Capturas de «No carga»');
    expect(lista.querySelectorAll('li')).toHaveLength(2);
    expect(ver(1).textContent).toBe(TEXTOS_CAPTURAS.ver(1));
    expect(ver(1).getAttribute('aria-expanded')).toBe('false');
    const imagen = document.getElementById(ver(1).getAttribute('aria-controls'));
    expect(imagen.hidden).toBe(true);
    expect(lista.textContent).toContain('JPEG · 800 × 600 · 1,4 MB');
    expect(leer).not.toHaveBeenCalled();
  });

  test('«Ver captura» la pide, la enseña con su texto alternativo y vuelve a ocultarla sin pedirla otra vez', async () => {
    const { lista, leer, ver } = montarLista();

    ver(2).click();
    await esperar();

    expect(leer).toHaveBeenCalledWith('c-2');
    const imagen = lista.querySelector('img');
    expect(imagen.getAttribute('src')).toBe('blob:captura-1');
    expect(imagen.getAttribute('alt')).toBe('Captura 2 de la solicitud «No carga»');
    expect(ver(2).getAttribute('aria-expanded')).toBe('true');
    expect(ver(2).textContent).toBe(TEXTOS_CAPTURAS.ocultar(2));
    expect(ver(2).disabled).toBe(false);

    ver(2).click();
    expect(ver(2).getAttribute('aria-expanded')).toBe('false');
    expect(document.getElementById(ver(2).getAttribute('aria-controls')).hidden).toBe(true);

    ver(2).click();
    expect(ver(2).getAttribute('aria-expanded')).toBe('true');
    expect(leer).toHaveBeenCalledTimes(1);
  });

  test('mientras llega, el botón espera; si no llega lo dice y se puede reintentar', async () => {
    let fallar = true;
    const leer = jest.fn(async () => {
      if (fallar) {
        throw new Error('red');
      }
      return new Blob(['x'], { type: 'image/png' });
    });
    const { lista, ver } = montarLista({ leer });

    ver(1).click();
    expect(ver(1).getAttribute('aria-busy')).toBe('true');
    expect(ver(1).textContent).toBe(TEXTOS_CAPTURAS.cargando);
    await esperar();

    const fallo = lista.querySelector('.capturas-chatbot__fallo');
    expect(fallo.hidden).toBe(false);
    expect(fallo.textContent).toBe(TEXTOS_CAPTURAS.fallo);
    expect(ver(1).getAttribute('aria-expanded')).toBe('false');
    expect(ver(1).textContent).toBe(TEXTOS_CAPTURAS.ver(1));

    fallar = false;
    ver(1).click();
    await esperar();
    expect(fallo.hidden).toBe(true);
    expect(lista.querySelector('img')).not.toBeNull();
  });

  test('un lector que falla antes de prometer nada también se dice', async () => {
    const { lista, ver } = montarLista({
      leer: () => {
        throw new Error('sin cliente');
      },
    });

    ver(1).click();
    await esperar();

    expect(lista.querySelector('.capturas-chatbot__fallo').hidden).toBe(false);
  });
});

describe('selectorDeCaptura', () => {
  function elegir(entrada, archivo) {
    Object.defineProperty(entrada, 'files', { value: [archivo], configurable: true });
    entrada.dispatchEvent(new Event('change'));
  }

  test('una entrada de archivo con su etiqueta y la pista, que se anuncia antes de elegir', () => {
    const selector = selectorDeCaptura({
      texto: 'Adjuntar',
      pista: 'Sin contraseñas',
      alElegir: jest.fn(),
    });
    document.body.replaceChildren(selector.elemento);

    const { entrada } = selector;
    expect(entrada.type).toBe('file');
    expect(entrada.getAttribute('accept')).toBe(FORMATOS_DE_CAPTURA);
    expect(entrada.classList.contains('solo-lectores')).toBe(true);
    const etiqueta = selector.elemento.querySelector('label');
    expect(etiqueta.getAttribute('for')).toBe(entrada.id);
    // La entrada va justo antes de su etiqueta: ahí se pinta su foco.
    expect(entrada.nextElementSibling).toBe(etiqueta);
    const pista = document.getElementById(entrada.getAttribute('aria-describedby'));
    expect(pista.textContent).toBe('Sin contraseñas');
  });

  test('al elegir un archivo lo entrega; sin archivo, nada', () => {
    const alElegir = jest.fn();
    const selector = selectorDeCaptura({ texto: 'Adjuntar', pista: 'p', alElegir });
    const archivo = new File(['x'], 'pantallazo.png', { type: 'image/png' });

    elegir(selector.entrada, archivo);
    expect(alElegir).toHaveBeenCalledWith(archivo);

    Object.defineProperty(selector.entrada, 'files', { value: [], configurable: true });
    selector.entrada.dispatchEvent(new Event('change'));
    expect(alElegir).toHaveBeenCalledTimes(1);
  });

  test('ocupado apaga la entrada y lo dice en la etiqueta', () => {
    const selector = selectorDeCaptura({ texto: 'Adjuntar', pista: 'p', alElegir: jest.fn() });
    const etiqueta = selector.elemento.querySelector('label');

    selector.ocupado(true, 'Subiendo…');
    expect(selector.entrada.disabled).toBe(true);
    expect(etiqueta.getAttribute('aria-busy')).toBe('true');
    expect(etiqueta.textContent).toBe('Subiendo…');

    selector.ocupado(false);
    expect(selector.entrada.disabled).toBe(false);
    expect(etiqueta.textContent).toBe('Adjuntar');
  });
});
