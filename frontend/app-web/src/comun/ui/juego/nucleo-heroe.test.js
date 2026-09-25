/**
 * UXC-1 — núcleo del héroe: prototipos, StatBlock, estado y carta propia.
 */
import { jest } from '@jest/globals';

import { identidadDePrototipo, normalizarNombre, PROTOTIPOS } from './prototipos.js';
import { bloqueDeEstadisticas, cifrasDe, formulaLegible } from './estadisticas.js';
import {
  ESTADOS,
  estadoDeHeroe,
  estadoDeObjeto,
  ranurasOcupadas,
  selloDeEstado,
} from './estado-heroe.js';
import { cartaDeHeroePropio, retratoDeHeroe } from './heroe.js';

describe('prototipos (§6.1.1, Tabla 6)', () => {
  test('son los ocho del documento, cada uno con su simbolo propio', () => {
    expect(PROTOTIPOS).toHaveLength(8);
    const simbolos = PROTOTIPOS.map((p) => p.icono);
    expect(new Set(simbolos).size).toBe(8);
  });

  test('se reconocen con o sin tildes y mayusculas', () => {
    expect(identidadDePrototipo('CHAMAN').nombre).toBe('Chamán');
    expect(identidadDePrototipo('picaro  machete').icono).toBe('daga');
    expect(identidadDePrototipo('Médico').sanador).toBe(true);
    expect(identidadDePrototipo('Guerrero Tanque').etiquetaFamilia).toBe('Guerrero');
    expect(normalizarNombre(' Pícaro   Veneno ')).toBe('picaro veneno');
  });

  test('uno desconocido no se inventa: queda sin identificar', () => {
    const identidad = identidadDePrototipo('Nigromante');
    expect(identidad.conocido).toBe(false);
    expect(identidad.familia).toBeNull();
    expect(identidad.icono).toBe('usuario');
    expect(identidadDePrototipo(null).nombre).toBe('Prototipo sin identificar');
  });
});

describe('StatBlock', () => {
  test('acepta la forma del inventario (formulas como objeto)', () => {
    const cifras = cifrasDe({
      poder: 10,
      vida: 44,
      defensa: 11,
      ataque: { base: 10, cantidadDados: 1, caras: 6, formula: '10 + 1d6' },
      dano: { base: 0, cantidadDados: 1, caras: 4, formula: '1d4' },
      sanar: null,
    });
    expect(cifras.map((c) => [c.etiqueta, c.valor])).toEqual([
      ['Poder', '10'],
      ['Vida', '44'],
      ['Defensa', '11'],
      ['Ataque', '10 + 1d6'],
      ['Daño', '1d4'],
    ]);
  });

  test('acepta la forma del catalogo (formulas como texto de la Tabla 6)', () => {
    const cifras = cifrasDe({ poder: 10, vida: 28, defensa: 4, ataque: '-', sanar: '6 + 1d6' });
    expect(cifras.map((c) => c.etiqueta)).toEqual(['Poder', 'Vida', 'Defensa', 'Sanar']);
  });

  test('lo ausente no se pinta: ni ceros ni guiones', () => {
    const bloque = bloqueDeEstadisticas({ vida: 44 });
    expect(bloque.textContent).toContain('44');
    expect(bloque.textContent).not.toMatch(/Defensa|—|\b0\b/);
    expect(bloqueDeEstadisticas({})).toBeNull();
    expect(bloqueDeEstadisticas(null)).toBeNull();
  });

  test('con titulo y nota se envuelve en una seccion', () => {
    const seccion = bloqueDeEstadisticas(
      { poder: 8 },
      { titulo: 'Tus cifras', nota: 'Con equipo' },
    );
    expect(seccion.tagName).toBe('SECTION');
    expect(seccion.querySelector('h3').textContent).toBe('Tus cifras');
    expect(seccion.querySelector('dl.stat-block')).not.toBeNull();
  });

  test('formulaLegible', () => {
    expect(formulaLegible({ base: 10, cantidadDados: 1, caras: 6 })).toBe('10 + 1d6');
    expect(formulaLegible({ base: 0, cantidadDados: 0, caras: 0, formula: '4' })).toBe('4');
    expect(formulaLegible(' 4 + 1d8 ')).toBe('4 + 1d8');
    expect(formulaLegible('-')).toBeNull();
  });
});

describe('estado del heroe y del objeto', () => {
  const equipo = { armas: ['a'], armaduras: { CASCO: 'c' }, items: [] };

  test('bloqueado por subasta gana a todo lo demas', () => {
    expect(
      estadoDeHeroe({ elemento: { disponible: false, subastaId: 's' }, equipamiento: equipo }),
    ).toEqual({ estado: ESTADOS.BLOQUEADO, detalle: 'en subasta' });
    expect(estadoDeObjeto({ elemento: { disponible: false }, equipadoEn: 'Ayla' })).toEqual({
      estado: ESTADOS.BLOQUEADO,
      detalle: 'no disponible',
    });
  });

  test('un heroe sin nada puesto no puede combatir (HU-SAL-003)', () => {
    expect(
      estadoDeHeroe({ elemento: {}, equipamiento: { armas: [], armaduras: {}, items: [] } }),
    ).toEqual({ estado: ESTADOS.NO_ELEGIBLE, detalle: 'sin equipo' });
  });

  test('si el equipo no se pudo leer no se afirma que le falte', () => {
    expect(estadoDeHeroe({ elemento: {}, equipamiento: undefined }).estado).toBe(
      ESTADOS.DISPONIBLE,
    );
  });

  test('un objeto equipado dice en quien', () => {
    expect(estadoDeObjeto({ elemento: {}, equipadoEn: 'Ayla' })).toEqual({
      estado: ESTADOS.EQUIPADO,
      detalle: 'Ayla',
    });
  });

  test('ranuras ocupadas: 2 armas, 6 armaduras, 2 items', () => {
    expect(ranurasOcupadas(equipo)).toBe(2);
    expect(ranurasOcupadas(null)).toBe(0);
  });

  test('el sello lleva icono y texto, no solo color', () => {
    const sello = selloDeEstado(ESTADOS.EQUIPADO, { detalle: 'Ayla' });
    expect(sello.textContent).toBe('Equipado · Ayla');
    expect(sello.querySelector('svg')).not.toBeNull();
    expect(sello.dataset.estado).toBe('EQUIPADO');
    // «En mision» existe como estado del componente aunque nada lo deduzca.
    expect(selloDeEstado(ESTADOS.EN_MISION).textContent).toBe('En misión');
  });
});

describe('retrato y carta del heroe propio', () => {
  test('con prototipo conocido el simbolo sustituye a la inicial', () => {
    const retrato = retratoDeHeroe({ nombre: 'Oyá', prototipo: 'Chamán' });
    expect(retrato.querySelector('.marco-heroe__simbolo')).not.toBeNull();
    expect(retrato.querySelector('.marco-heroe__inicial')).toBeNull();
    expect(retrato.getAttribute('aria-label')).toBe('Oyá, Chamán');
  });

  test('sin prototipo se queda la inicial', () => {
    const retrato = retratoDeHeroe({ nombre: 'Kael' });
    expect(retrato.querySelector('.marco-heroe__inicial').textContent).toBe('K');
  });

  test('la carta junta nombre, prototipo, estado, cifras, equipo y acciones', () => {
    const verFicha = jest.fn();
    const carta = cartaDeHeroePropio(
      {
        nombre: 'Aquiles',
        prototipo: 'Guerrero Tanque',
        estadisticas: { poder: 10, vida: 44, defensa: 11 },
        estado: { estado: ESTADOS.DISPONIBLE },
        ranurasOcupadas: 4,
      },
      [{ texto: 'Ver ficha', alPulsar: verFicha, datos: { accion: 'ver-ficha' } }],
    );
    expect(carta.dataset.prototipo).toBe('guerrero-tanque');
    expect(carta.querySelector('.hero-card__nombre').textContent).toBe('Aquiles');
    expect(carta.querySelector('.hero-card__prototipo').textContent).toContain('Guerrero Tanque');
    expect(carta.querySelector('.stat-block')).not.toBeNull();
    expect(carta.textContent).toContain('Equipo: 4 de 10 ranuras');
    carta.querySelector('[data-accion="ver-ficha"]').click();
    expect(verFicha).toHaveBeenCalled();
  });

  test('los sanadores se dicen sanadores; sin prototipo, se dice que no se conoce', () => {
    const medico = cartaDeHeroePropio({ nombre: 'Lumen', prototipo: 'Médico' });
    expect(medico.textContent).toContain('Médico · Sanador');
    const anonimo = cartaDeHeroePropio({ nombre: 'X', prototipo: null });
    expect(anonimo.textContent).toContain('Prototipo sin identificar');
  });

  test('una accion deshabilitada lo esta de verdad y dice por que', () => {
    const alPulsar = jest.fn();
    const carta = cartaDeHeroePropio({ nombre: 'Sombra', prototipo: 'Pícaro Veneno' }, [
      { texto: 'Equipamiento', alPulsar, deshabilitada: 'Está en subasta' },
    ]);
    const boton = carta.querySelector('button');
    expect(boton.disabled).toBe(true);
    expect(boton.title).toBe('Está en subasta');
    boton.click();
    expect(alPulsar).not.toHaveBeenCalled();
  });

  test('no pinta nivel, rareza ni experiencia', () => {
    const carta = cartaDeHeroePropio({ nombre: 'Aquiles', prototipo: 'Guerrero Tanque' });
    expect(carta.textContent).not.toMatch(/nivel|rareza|experiencia/i);
  });
});
