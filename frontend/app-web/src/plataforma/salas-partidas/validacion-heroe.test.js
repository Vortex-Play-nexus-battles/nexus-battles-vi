/**
 * HU-SAL-003 — Dialogo de verificacion de heroe.
 *
 * Se prueban las tres variantes de Figma con datos inyectados, que las
 * acciones respondan, y sobre todo que **ninguna variante muestre lo que
 * pertenece a otra**: con el backend todavia bloqueado, esa separacion es lo
 * unico que impide que el dialogo invente un veredicto.
 */

import { jest } from '@jest/globals';

import {
  montarValidacionDeHeroe,
  pintarValidacion,
  estadisticasDe,
  RESULTADOS,
} from './validacion-heroe.js';
import { ErrorDeApi } from './cliente-salas.js';

const HEROE = {
  id: 'aaaa-0001',
  nombre: 'Arquero del Norte',
  vidaActual: 120,
  vidaMaxima: 120,
  ataque: 34,
  defensa: 21,
  nivel: 12,
};

function raiz() {
  document.body.innerHTML = '<section id="d"></section>';
  return document.getElementById('d');
}

const sinHeroe = (extra = {}) => ({
  resultado: RESULTADOS.SIN_HEROE,
  puedeIngresar: false,
  heroesSinEquipar: 3,
  ...extra,
});

const ocupado = (extra = {}) => ({
  resultado: RESULTADOS.OCUPADO,
  puedeIngresar: false,
  heroe: HEROE,
  salaQueLoOcupa: 'Torre del Alba',
  minutosRestantes: 4,
  ...extra,
});

const disponible = (extra = {}) => ({
  resultado: RESULTADOS.DISPONIBLE,
  puedeIngresar: true,
  heroe: HEROE,
  creditosRequeridos: 150,
  ...extra,
});

const asentar = () => new Promise((r) => setTimeout(r, 0));

describe('estadisticasDe', () => {
  test('escribe las cuatro con el separador del diseno', () => {
    expect(estadisticasDe(HEROE)).toBe('Vida 120 · Ataque 34 · Defensa 21 · Nivel 12');
  });

  test('omite las que el contrato no garantiza en vez de inventarlas', () => {
    expect(estadisticasDe({ vidaMaxima: 120, nivel: 12 })).toBe('Vida 120 · Nivel 12');
  });

  test('sin heroe no hay linea', () => {
    expect(estadisticasDe(undefined)).toBeNull();
  });
});

describe('variante Sin heroe', () => {
  test('dice el motivo y manda al inventario', () => {
    const d = raiz();
    pintarValidacion(d, sinHeroe());

    expect(d.dataset.resultado).toBe('SIN_HEROE_EQUIPADO');
    expect(d.textContent).toContain('No tienes un heroe equipado');
    expect(d.textContent).toContain(
      'Equipa un heroe desde tu inventario antes de entrar a la sala.',
    );
    expect(d.querySelector('[data-accion="confirmar"]').textContent).toBe('IR AL INVENTARIO');
  });

  test('el aviso es de error y cuenta cuantos heroes hay sin equipar', () => {
    const d = raiz();
    pintarValidacion(d, sinHeroe());

    const aviso = d.querySelector('.aviso');
    expect(aviso.className).toContain('aviso--error');
    expect(aviso.textContent).toContain('Tu inventario');
    expect(aviso.textContent).toContain('Tienes 3 heroes sin equipar. Equipa uno y vuelve.');
  });

  test('sin el dato del inventario no se pinta el aviso, en vez de inventar un numero', () => {
    const d = raiz();
    pintarValidacion(d, sinHeroe({ heroesSinEquipar: undefined }));

    expect(d.querySelector('.aviso')).toBeNull();
    expect(d.textContent).toContain('No tienes un heroe equipado');
  });

  test('no hay selector de heroe: el diseno no lo tiene', () => {
    const d = raiz();
    pintarValidacion(d, sinHeroe());

    expect(d.querySelector('select')).toBeNull();
    expect(d.querySelectorAll('button')).toHaveLength(2);
  });
});

describe('variante Ocupado', () => {
  test('nombra el heroe y la sala que lo ocupa', () => {
    const d = raiz();
    pintarValidacion(d, ocupado());

    expect(d.dataset.resultado).toBe('HEROE_OCUPADO');
    expect(d.textContent).toContain('Tu heroe esta en otra partida');
    expect(d.textContent).toContain('«Arquero del Norte» esta en la sala «Torre del Alba»');
    expect(d.querySelector('[data-accion="confirmar"]').textContent).toBe('ELEGIR OTRO HEROE');
  });

  test('el aviso es de advertencia y dice cuanto falta', () => {
    const d = raiz();
    pintarValidacion(d, ocupado());

    const aviso = d.querySelector('.aviso');
    expect(aviso.className).toContain('aviso--advertencia');
    expect(aviso.textContent).toContain('La partida en curso termina en unos 4 minutos.');
  });

  test('sin estimacion de tiempo no se pinta el aviso', () => {
    const d = raiz();
    pintarValidacion(d, ocupado({ minutosRestantes: undefined }));

    expect(d.querySelector('.aviso')).toBeNull();
  });

  test('sin nombre de sala no se inventa el mensaje', () => {
    const d = raiz();
    pintarValidacion(d, ocupado({ salaQueLoOcupa: undefined }));

    expect(d.textContent).toContain('Tu heroe esta en otra partida');
    expect(d.textContent).not.toContain('esta en la sala');
  });
});

describe('variante Disponible', () => {
  test('nombra el heroe y muestra sus estadisticas', () => {
    const d = raiz();
    pintarValidacion(d, disponible());

    expect(d.dataset.resultado).toBe('DISPONIBLE');
    expect(d.textContent).toContain('Arquero del Norte, listo para combatir');
    expect(d.textContent).toContain('Vida 120 · Ataque 34 · Defensa 21 · Nivel 12');
    expect(d.querySelector('[data-accion="confirmar"]').textContent).toBe('ENTRAR A LA SALA');
  });

  test('el aviso es de exito y advierte del descuento', () => {
    const d = raiz();
    pintarValidacion(d, disponible());

    const aviso = d.querySelector('.aviso');
    expect(aviso.className).toContain('aviso--exito');
    expect(aviso.textContent).toContain('Antes de entrar');
    expect(aviso.textContent).toContain('Se descontaran 150 creditos de tu saldo al confirmar.');
  });
});

describe('separacion entre variantes', () => {
  test('Disponible no filtra textos de las otras dos', () => {
    const d = raiz();
    pintarValidacion(d, disponible());

    expect(d.textContent).not.toContain('No tienes un heroe equipado');
    expect(d.textContent).not.toContain('otra partida');
    expect(d.textContent).not.toContain('IR AL INVENTARIO');
  });

  test('Sin heroe no muestra estadisticas ni el descuento', () => {
    const d = raiz();
    pintarValidacion(d, sinHeroe({ heroe: HEROE, creditosRequeridos: 150 }));

    expect(d.textContent).not.toContain('Vida 120');
    expect(d.textContent).not.toContain('Se descontaran');
    expect(d.textContent).not.toContain('ENTRAR A LA SALA');
  });

  test('Ocupado no ofrece entrar a la sala', () => {
    const d = raiz();
    pintarValidacion(d, ocupado());

    expect(d.textContent).not.toContain('ENTRAR A LA SALA');
    expect(d.textContent).not.toContain('listo para combatir');
  });

  test('un resultado sin variante disenada no se inventa', () => {
    const d = raiz();
    pintarValidacion(d, { resultado: 'CREDITOS_INSUFICIENTES', puedeIngresar: false });

    expect(d.dataset.resultado).toBe('DESCONOCIDO');
    expect(d.textContent).toContain('No se pudo interpretar');
  });
});

describe('acciones', () => {
  test('cancelar y confirmar avisan con la verificacion', () => {
    const d = raiz();
    const alCancelar = jest.fn();
    const alConfirmar = jest.fn();
    const v = disponible();

    pintarValidacion(d, v, { alCancelar, alConfirmar });
    d.querySelector('[data-accion="cancelar"]').click();
    d.querySelector('[data-accion="confirmar"]').click();

    expect(alCancelar).toHaveBeenCalledWith(v);
    expect(alConfirmar).toHaveBeenCalledWith(v);
  });

  test('sin manejadores, pulsar no rompe nada', () => {
    const d = raiz();
    pintarValidacion(d, disponible());

    expect(() => d.querySelector('[data-accion="confirmar"]').click()).not.toThrow();
  });
});

describe('accesibilidad', () => {
  test('es un dialogo modal con titulo asociado', () => {
    const d = raiz();
    pintarValidacion(d, disponible());

    expect(d.getAttribute('role')).toBe('dialog');
    expect(d.getAttribute('aria-modal')).toBe('true');
    const titulo = document.getElementById(d.getAttribute('aria-labelledby'));
    expect(titulo.textContent).toBe('Verificacion de heroe');
  });

  test('el retrato es decorativo: lo que comunica es el texto', () => {
    const d = raiz();
    pintarValidacion(d, disponible());

    expect(d.querySelector('.dialogo__icono').getAttribute('aria-hidden')).toBe('true');
  });

  test('el aviso de error se anuncia como alerta y el resto como estado', () => {
    const d = raiz();
    pintarValidacion(d, sinHeroe());
    expect(d.querySelector('.aviso').getAttribute('role')).toBe('alert');

    pintarValidacion(d, disponible());
    expect(d.querySelector('.aviso').getAttribute('role')).toBe('status');
  });

  test('tambien es dialogo modal con titulo mientras carga y cuando falla', async () => {
    const d = raiz();
    let rechazar;
    montarValidacionDeHeroe(d, {
      idSala: 's1',
      verificar: () => new Promise((_, r) => (rechazar = r)),
    });

    expect(d.dataset.resultado).toBe('CARGANDO');
    expect(d.getAttribute('role')).toBe('dialog');
    expect(d.getAttribute('aria-modal')).toBe('true');
    expect(document.getElementById(d.getAttribute('aria-labelledby')).textContent).toBe(
      'Verificacion de heroe',
    );

    rechazar(new Error('sin red'));
    await asentar();

    expect(d.dataset.resultado).toBe('ERROR');
    expect(d.getAttribute('role')).toBe('dialog');
    expect(document.getElementById(d.getAttribute('aria-labelledby')).textContent).toBe(
      'Verificacion de heroe',
    );
  });

  test('al abrir, el foco entra al dialogo por el titulo y el primer Tab cae en Cancelar', () => {
    const d = raiz();
    pintarValidacion(d, disponible());

    expect(document.activeElement).toBe(document.getElementById('titulo-validacion-heroe'));
    // El titulo no entra en el orden de tabulacion: solo recibe foco por programa.
    expect(document.activeElement.tabIndex).toBe(-1);
    const primero = d.querySelector('a[href], button:not([disabled])');
    expect(primero.dataset.accion).toBe('cancelar');
  });

  test('Escape equivale a Cancelar', () => {
    const d = raiz();
    const alCancelar = jest.fn();
    const v = disponible();
    pintarValidacion(d, v, { alCancelar });

    d.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));

    expect(alCancelar).toHaveBeenCalledWith(v);
  });

  test('Escape tambien cancela desde el estado de error', async () => {
    const d = raiz();
    const alCancelar = jest.fn();
    await montarValidacionDeHeroe(d, {
      idSala: 's1',
      verificar: jest.fn().mockRejectedValue(new Error('sin red')),
      alCancelar,
    });
    await asentar();

    d.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));

    expect(alCancelar).toHaveBeenCalledTimes(1);
  });

  test('Tab no sale del dialogo: del ultimo boton vuelve al primero y al reves', () => {
    const d = raiz();
    pintarValidacion(d, disponible());
    const cancelar = d.querySelector('[data-accion="cancelar"]');
    const confirmar = d.querySelector('[data-accion="confirmar"]');

    confirmar.focus();
    const adelante = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true });
    d.dispatchEvent(adelante);
    expect(adelante.defaultPrevented).toBe(true);
    expect(document.activeElement).toBe(cancelar);

    const atras = new KeyboardEvent('keydown', {
      key: 'Tab',
      shiftKey: true,
      bubbles: true,
      cancelable: true,
    });
    d.dispatchEvent(atras);
    expect(atras.defaultPrevented).toBe(true);
    expect(document.activeElement).toBe(confirmar);
  });

  test('entre medias, Tab sigue su curso normal', () => {
    const d = raiz();
    pintarValidacion(d, disponible());
    d.querySelector('[data-accion="cancelar"]').focus();

    const evento = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true });
    d.dispatchEvent(evento);

    expect(evento.defaultPrevented).toBe(false);
  });

  test('el teclado se instala una sola vez aunque se repinte', () => {
    const d = raiz();
    const alCancelar = jest.fn();
    pintarValidacion(d, disponible(), { alCancelar });
    pintarValidacion(d, disponible(), { alCancelar });

    d.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));

    expect(alCancelar).toHaveBeenCalledTimes(1);
  });
});

describe('sin sala en la direccion', () => {
  test('no llama al servicio con /salas/null y explica como llegar', async () => {
    const d = raiz();
    const verificar = jest.fn();

    await montarValidacionDeHeroe(d, { idSala: null, verificar });

    expect(verificar).not.toHaveBeenCalled();
    expect(d.dataset.resultado).toBe('SIN_SALA');
    expect(d.getAttribute('role')).toBe('dialog');
    expect(d.textContent).toContain('?sala=');
    expect(d.querySelector('[data-accion="cancelar"]')).not.toBeNull();
    expect(d.querySelector('[data-accion="confirmar"]')).toBeNull();
  });

  test('una cadena vacia cuenta como sala ausente', async () => {
    const d = raiz();
    const verificar = jest.fn();

    await montarValidacionDeHeroe(d, { idSala: '', verificar });

    expect(verificar).not.toHaveBeenCalled();
    expect(d.dataset.resultado).toBe('SIN_SALA');
  });
});

describe('montarValidacionDeHeroe', () => {
  test('muestra que esta comprobando antes de tener respuesta', () => {
    const d = raiz();
    montarValidacionDeHeroe(d, { idSala: 's1', verificar: () => new Promise(() => {}) });

    expect(d.dataset.resultado).toBe('CARGANDO');
    expect(d.textContent).toContain('Comprobando tu heroe');
  });

  test('pinta la variante que devuelve el puerto', async () => {
    const d = raiz();
    const verificar = jest.fn().mockResolvedValue(disponible());

    await montarValidacionDeHeroe(d, { idSala: 's1', verificar });
    await asentar();

    expect(verificar).toHaveBeenCalledWith('s1');
    expect(d.dataset.resultado).toBe('DISPONIBLE');
  });

  test('si la verificacion falla lo dice, no deja el dialogo en blanco', async () => {
    const d = raiz();
    const verificar = jest.fn().mockRejectedValue(
      new ErrorDeApi(
        {
          title: 'No se pudo verificar tu heroe',
          detail: 'El servicio respondio 404.',
          status: 404,
        },
        404,
      ),
    );

    await montarValidacionDeHeroe(d, { idSala: 's1', verificar });
    await asentar();

    expect(d.dataset.resultado).toBe('ERROR');
    expect(d.textContent).toContain('El servicio respondio 404.');
    expect(d.querySelector('[data-accion="cancelar"]')).not.toBeNull();
    expect(d.querySelector('[data-accion="confirmar"]')).toBeNull();
  });
});
