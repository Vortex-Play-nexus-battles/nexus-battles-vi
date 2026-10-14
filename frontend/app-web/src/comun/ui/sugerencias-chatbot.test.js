/**
 * Preguntas rápidas, temas frecuentes y autocompletado del asistente.
 */

import { jest } from '@jest/globals';

import {
  CUANTAS,
  TEXTOS_PARA_TI,
  TEXTOS_SUGERENCIAS,
  conectarAutocompletado,
  contenidoParaTi,
  crearPreguntasRapidas,
  gruposParaTi,
  motivoParaTi,
} from './sugerencias-chatbot.js';

const esperar = () => new Promise((resolver) => setTimeout(resolver, 0));

const TORNEO = {
  clave: 'k-torneo',
  titulo: 'Cómo funciona el Torneo',
  categoria: 'MODALIDAD_JUEGO',
  pregunta: 'Cómo funciona el Torneo',
};
const SUBASTA = {
  clave: 'k-subasta',
  titulo: 'Cómo publicar un producto en subasta',
  categoria: 'SUBASTA_Y_COMERCIO',
  pregunta: 'Cómo publicar un producto en subasta',
};

beforeEach(() => {
  document.body.replaceChildren();
});

describe('preguntas rápidas', () => {
  async function montar(sugerencias = jest.fn(async () => [TORNEO, SUBASTA])) {
    const alElegir = jest.fn();
    const rapidas = crearPreguntasRapidas({ cliente: { sugerencias }, alElegir });
    document.body.append(rapidas.elemento);
    await rapidas.cargar();
    return { rapidas, alElegir, sugerencias, el: rapidas.elemento };
  }

  test('pide las más consultadas y pinta un botón por pregunta', async () => {
    const { el, sugerencias } = await montar();

    expect(sugerencias).toHaveBeenCalledWith({ categoria: null, limite: CUANTAS.rapidas });
    expect(el.hidden).toBe(false);
    expect(el.textContent).toContain(TEXTOS_SUGERENCIAS.titulo);
    const botones = el.querySelectorAll('[data-accion="pregunta-rapida"]');
    expect([...botones].map((b) => b.textContent)).toEqual([TORNEO.titulo, SUBASTA.titulo]);
  });

  test('es un desplegable que empieza cerrado, con el título a la vista', async () => {
    const { el } = await montar();

    expect(el.tagName).toBe('DETAILS');
    expect(el.open).toBe(false);
    const titulo = el.querySelector('summary');
    expect(titulo.textContent).toBe(TEXTOS_SUGERENCIAS.titulo);
    // La lista se anuncia con el mismo título que se ve.
    const lista = el.querySelector('[role="group"]');
    expect(lista.getAttribute('aria-labelledby')).toBe(titulo.id);
  });

  test('al elegir una pregunta se pliega para dejarle espacio a la conversación', async () => {
    const { el, alElegir } = await montar();
    el.open = true;

    el.querySelector('[data-clave="k-torneo"]').click();

    expect(el.open).toBe(false);
    expect(alElegir).toHaveBeenCalledWith(TORNEO.pregunta);
  });

  test('al pulsar una manda su pregunta', async () => {
    const { el, alElegir } = await montar();

    el.querySelector('[data-clave="k-subasta"]').click();

    expect(alElegir).toHaveBeenCalledWith(SUBASTA.pregunta);
  });

  test('sin sugerencias o si el servicio falla, no se muestra', async () => {
    const vacia = await montar(jest.fn(async () => []));
    expect(vacia.el.hidden).toBe(true);

    const falla = await montar(
      jest.fn(async () => {
        throw new Error('sin servicio');
      }),
    );
    expect(falla.el.hidden).toBe(true);
  });

  test('elegir un tema pide las de ese tema y se puede volver', async () => {
    const { el, sugerencias } = await montar();
    const selector = el.querySelector('select[name="tema-rapidas"]');
    sugerencias.mockResolvedValueOnce([]);

    selector.value = 'CUENTA_Y_REGISTRO';
    selector.dispatchEvent(new Event('change'));
    await esperar();

    expect(sugerencias).toHaveBeenLastCalledWith({
      categoria: 'CUENTA_Y_REGISTRO',
      limite: CUANTAS.porTema,
    });
    // Con un tema elegido se queda visible aunque no haya nada, para poder volver.
    expect(el.hidden).toBe(false);
    expect(el.querySelectorAll('[data-accion="pregunta-rapida"]')).toHaveLength(0);

    selector.value = '';
    selector.dispatchEvent(new Event('change'));
    await esperar();
    expect(sugerencias).toHaveBeenLastCalledWith({ categoria: null, limite: CUANTAS.rapidas });
  });

  test('una respuesta vieja no pisa a la nueva', async () => {
    let resolverVieja;
    const sugerencias = jest
      .fn()
      .mockImplementationOnce(() => new Promise((resolver) => (resolverVieja = resolver)))
      .mockResolvedValueOnce([SUBASTA]);
    const rapidas = crearPreguntasRapidas({ cliente: { sugerencias }, alElegir: jest.fn() });

    const vieja = rapidas.cargar();
    await rapidas.cargar();
    resolverVieja([TORNEO]);
    await vieja;

    expect(rapidas.elemento.textContent).toContain(SUBASTA.titulo);
    expect(rapidas.elemento.textContent).not.toContain(TORNEO.titulo);
  });

  test('habilitar(false) desactiva los botones', async () => {
    const { rapidas, el } = await montar();

    rapidas.habilitar(false);
    expect([...el.querySelectorAll('button')].every((b) => b.disabled)).toBe(true);

    rapidas.habilitar(true);
    expect([...el.querySelectorAll('button')].some((b) => b.disabled)).toBe(false);
  });
});

describe('autocompletado', () => {
  function montar(sugerencias = jest.fn(async () => [TORNEO, SUBASTA])) {
    const entrada = document.createElement('textarea');
    const alElegir = jest.fn();
    const auto = conectarAutocompletado({
      entrada,
      cliente: { sugerencias },
      alElegir,
      esperaMs: 0,
    });
    document.body.append(auto.elemento, entrada);
    entrada.focus();
    return { entrada, alElegir, sugerencias, lista: auto.elemento, auto };
  }

  async function escribir(entrada, texto) {
    entrada.value = texto;
    entrada.dispatchEvent(new Event('input'));
    await esperar();
    await esperar();
  }

  function tecla(entrada, key) {
    const evento = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true });
    entrada.dispatchEvent(evento);
    return evento;
  }

  test('la caja es un combobox que controla la lista', () => {
    const { entrada, lista } = montar();

    expect(entrada.getAttribute('role')).toBe('combobox');
    expect(entrada.getAttribute('aria-controls')).toBe(lista.id);
    expect(entrada.getAttribute('aria-expanded')).toBe('false');
    expect(lista.getAttribute('role')).toBe('listbox');
  });

  test('con dos letras o más sugiere; con menos, no', async () => {
    const { entrada, lista, sugerencias } = montar();

    await escribir(entrada, 'c');
    expect(sugerencias).not.toHaveBeenCalled();
    expect(lista.hidden).toBe(true);

    await escribir(entrada, ' co ');
    expect(sugerencias).toHaveBeenCalledWith({ q: 'co', limite: CUANTAS.autocompletar });
    expect(lista.hidden).toBe(false);
    expect(entrada.getAttribute('aria-expanded')).toBe('true');
    expect([...lista.children].map((li) => li.textContent)).toEqual([
      TORNEO.titulo,
      SUBASTA.titulo,
    ]);
  });

  test('las flechas marcan y Enter elige sin dejar pasar el envío', async () => {
    const { entrada, lista, alElegir } = montar();
    const otroManejador = jest.fn();
    entrada.addEventListener('keydown', otroManejador);
    await escribir(entrada, 'como');

    tecla(entrada, 'ArrowDown');
    tecla(entrada, 'ArrowDown');
    expect(lista.children[1].getAttribute('aria-selected')).toBe('true');
    expect(entrada.getAttribute('aria-activedescendant')).toBe(lista.children[1].id);

    tecla(entrada, 'ArrowDown');
    expect(lista.children[0].getAttribute('aria-selected')).toBe('true');

    const enter = tecla(entrada, 'Enter');
    expect(enter.defaultPrevented).toBe(true);
    expect(alElegir).toHaveBeenCalledWith(TORNEO.pregunta);
    expect(otroManejador).not.toHaveBeenCalledWith(expect.objectContaining({ key: 'Enter' }));
    expect(lista.hidden).toBe(true);
  });

  test('Enter sin nada marcado deja que la ventana envíe lo escrito', async () => {
    const { entrada, alElegir } = montar();
    await escribir(entrada, 'como');

    const enter = tecla(entrada, 'Enter');

    expect(enter.defaultPrevented).toBe(false);
    expect(alElegir).not.toHaveBeenCalled();
  });

  test('Escape cierra la lista sin llegar a la ventana', async () => {
    const { entrada, lista } = montar();
    const ventana = jest.fn();
    document.body.addEventListener('keydown', ventana);
    await escribir(entrada, 'como');

    tecla(entrada, 'Escape');

    expect(lista.hidden).toBe(true);
    expect(ventana).not.toHaveBeenCalled();
  });

  test('con el ratón se elige una sugerencia', async () => {
    const { entrada, lista, alElegir } = montar();
    await escribir(entrada, 'como');

    lista.children[1].dispatchEvent(
      new MouseEvent('mousedown', { bubbles: true, cancelable: true }),
    );

    expect(alElegir).toHaveBeenCalledWith(SUBASTA.pregunta);
  });

  test('si el servicio falla o no hay nada, no muestra la lista', async () => {
    const { entrada, lista } = montar(
      jest.fn(async () => {
        throw new Error('sin servicio');
      }),
    );

    await escribir(entrada, 'como');

    expect(lista.hidden).toBe(true);
  });

  test('perder el foco o cerrar() la esconde', async () => {
    const { entrada, lista, auto } = montar();
    await escribir(entrada, 'como');

    entrada.dispatchEvent(new Event('blur'));
    expect(lista.hidden).toBe(true);

    await escribir(entrada, 'torneo');
    auto.cerrar();
    expect(lista.hidden).toBe(true);
    expect(entrada.hasAttribute('aria-activedescendant')).toBe(false);
  });
});

// 1.15.0 (§7.4.3 «sugerencias proactivas», D-CHA-14).
describe('sugerencias para ti', () => {
  const COMISION = {
    clave: 'comision',
    titulo: 'Comisión de la subasta',
    categoria: 'SUBASTA_Y_COMERCIO',
    pregunta: 'Comisión de la subasta',
    motivo: 'POR_LO_QUE_PREGUNTASTE',
    relacionadoCon: 'Cómo funciona la Subasta',
  };
  const PUJAR = { ...COMISION, clave: 'pujar', titulo: 'Cómo pujar', pregunta: 'Cómo pujar' };
  const MISION = {
    clave: 'mision',
    titulo: 'Qué es una misión',
    categoria: 'MODALIDAD_JUEGO',
    pregunta: 'Qué es una misión',
    motivo: 'POR_DONDE_ESTAS',
    relacionadoCon: null,
  };

  test('cada motivo se explica en palabras del jugador', () => {
    expect(motivoParaTi(COMISION)).toBe('Porque preguntaste por «Cómo funciona la Subasta»');
    expect(motivoParaTi({ motivo: 'POR_LO_QUE_PREGUNTASTE' })).toBe(
      TEXTOS_PARA_TI.porLoQueYaPreguntaste,
    );
    expect(motivoParaTi(MISION)).toBe(TEXTOS_PARA_TI.porDondeEstas);
    expect(motivoParaTi({ motivo: 'PARA_EMPEZAR' })).toBe(TEXTOS_PARA_TI.paraEmpezar);
    // Un motivo que esta vista aún no conoce no rompe nada.
    expect(motivoParaTi({ motivo: 'OTRO' })).toBe(TEXTOS_PARA_TI.paraEmpezar);
    expect(motivoParaTi()).toBe(TEXTOS_PARA_TI.paraEmpezar);
    expect(CUANTAS.paraTi).toBe(3);
  });

  test('se agrupan por motivo, en el orden en que llegan', () => {
    const otraConsulta = { ...PUJAR, clave: 'torneo', relacionadoCon: 'Cómo funciona el Torneo' };
    const grupos = gruposParaTi([COMISION, MISION, PUJAR, otraConsulta]);

    expect(grupos.map((g) => g.titulo)).toEqual([
      'Porque preguntaste por «Cómo funciona la Subasta»',
      TEXTOS_PARA_TI.porDondeEstas,
      'Porque preguntaste por «Cómo funciona el Torneo»',
    ]);
    expect(grupos[0].sugerencias.map((s) => s.clave)).toEqual(['comision', 'pujar']);
    expect(gruposParaTi(null)).toEqual([]);
  });

  test('la burbuja dice por qué y cada botón manda su pregunta', () => {
    const alElegir = jest.fn();
    const contenido = contenidoParaTi([COMISION, PUJAR, MISION], { alElegir });
    document.body.append(contenido);

    expect(contenido.querySelector('.chatbot-ventana__texto').textContent).toBe(
      TEXTOS_PARA_TI.titulo,
    );
    const grupos = [...contenido.querySelectorAll('[role="group"]')];
    expect(grupos).toHaveLength(2);
    const motivo = document.getElementById(grupos[0].getAttribute('aria-labelledby'));
    expect(motivo.textContent).toBe('Porque preguntaste por «Cómo funciona la Subasta»');
    const botones = [...contenido.querySelectorAll('[data-accion="sugerencia-para-ti"]')];
    expect(botones.map((b) => b.textContent)).toEqual([
      'Comisión de la subasta',
      'Cómo pujar',
      'Qué es una misión',
    ]);
    expect(botones.every((b) => b.type === 'button')).toBe(true);

    botones[2].click();
    expect(alElegir).toHaveBeenCalledWith('Qué es una misión');
  });

  test('el texto del servidor nunca entra como HTML; sin título manda la pregunta', () => {
    const alElegir = jest.fn();
    const contenido = contenidoParaTi(
      [
        {
          clave: 'x',
          pregunta: '<img src=x onerror=alert(1)>',
          motivo: 'POR_LO_QUE_PREGUNTASTE',
          relacionadoCon: '<b>tema</b>',
        },
      ],
      { alElegir },
    );

    expect(contenido.querySelector('img, b')).toBeNull();
    const boton = contenido.querySelector('button');
    expect(boton.textContent).toBe('<img src=x onerror=alert(1)>');
    boton.click();
    expect(alElegir).toHaveBeenCalledWith('<img src=x onerror=alert(1)>');
    expect(contenido.textContent).toContain('«<b>tema</b>»');
  });

  test('sin ninguna que mostrar no hay burbuja', () => {
    expect(contenidoParaTi([], { alElegir: jest.fn() })).toBeNull();
    expect(contenidoParaTi(null, { alElegir: jest.fn() })).toBeNull();
    expect(contenidoParaTi([null, { clave: 'vacia' }], { alElegir: jest.fn() })).toBeNull();
  });
});
