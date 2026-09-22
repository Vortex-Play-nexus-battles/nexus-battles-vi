/** Tarjeta, cifra, boton y distintivo (PR-UX-1). */

import { jest } from '@jest/globals';

import { boton, conCarga } from './boton.js';
import { claseDeMarco, distintivo, distintivoDeRareza } from './distintivo.js';
import { tarjeta, tarjetaDeCifra } from './tarjeta.js';
import { encabezadoDePagina, encabezadoDeSeccion } from './pagina.js';

describe('tarjeta', () => {
  test('titulo, subtitulo, distintivos y pares etiqueta/valor', () => {
    const t = tarjeta({
      titulo: 'Espada del alba',
      subtitulo: 'Publicada por vael',
      variante: 'subasta',
      distintivos: [distintivo('Termina pronto', 'en-juego')],
      datos: [
        { etiqueta: 'Puja actual', valor: '120' },
        { etiqueta: 'Pujas', valor: '4' },
      ],
      atributosDeDatos: { subastaId: 's-1' },
    });

    expect(t.tagName).toBe('ARTICLE');
    expect(t.className).toContain('tarjeta--subasta');
    expect(t.dataset.subastaId).toBe('s-1');
    expect(t.querySelector('.tarjeta__titulo').textContent).toBe('Espada del alba');
    // <dl> de verdad: un lector de pantalla lee «Puja actual, 120».
    expect(t.querySelectorAll('.tarjeta__datos dt')).toHaveLength(2);
    expect(t.querySelectorAll('.tarjeta__datos dd')[0].textContent).toBe('120');
  });

  test('con href es un enlace y no necesita teclado propio', () => {
    const t = tarjeta({ titulo: 'Copa Otono', href: './torneos.html?torneo=t-1' });

    expect(t.tagName).toBe('A');
    expect(t.className).toContain('tarjeta--pulsable');
    expect(t.hasAttribute('tabindex')).toBe(false);
  });

  test('sin href pero pulsable: responde a Enter y a Espacio, no solo al raton', () => {
    const alAbrir = jest.fn();
    const t = tarjeta({ titulo: 'Sala de vael', alAbrir });

    expect(t.getAttribute('role')).toBe('button');
    expect(t.getAttribute('tabindex')).toBe('0');
    t.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    t.dispatchEvent(new KeyboardEvent('keydown', { key: ' ' }));
    t.click();
    expect(alAbrir).toHaveBeenCalledTimes(3);
  });

  test('sin datos ni acciones no deja contenedores vacios', () => {
    const t = tarjeta({ titulo: 'Pelada' });

    expect(t.querySelector('.tarjeta__datos')).toBeNull();
    expect(t.querySelector('.tarjeta__acciones')).toBeNull();
    expect(t.querySelector('.tarjeta__distintivos')).toBeNull();
  });
});

describe('tarjetaDeCifra', () => {
  test('etiqueta, valor y detalle', () => {
    const c = tarjetaDeCifra({ etiqueta: 'Saldo', valor: '500', detalle: 'disponible' });

    expect(c.className).toContain('metrica--cifra');
    expect(c.querySelector('.metrica__valor').textContent).toBe('500');
  });
});

describe('boton', () => {
  test('variante, tipo y accion', () => {
    const alPulsar = jest.fn();
    const b = boton({ texto: 'Crear sala', variante: 'primario', nombre: 'crear', alPulsar });

    expect(b.className).toBe('boton boton--primario');
    expect(b.dataset.accion).toBe('crear');
    b.click();
    expect(alPulsar).toHaveBeenCalled();
  });

  test('con href es un enlace con aspecto de boton', () => {
    expect(boton({ texto: 'Ir', href: './x.html' }).tagName).toBe('A');
  });

  test('conCarga bloquea, lo anuncia y devuelve el texto exacto', () => {
    const b = boton({ texto: 'Crear sala' });

    conCarga(b, true, 'Creando la sala…');
    expect(b.disabled).toBe(true);
    expect(b.getAttribute('aria-busy')).toBe('true');
    expect(b.textContent).toBe('Creando la sala…');

    conCarga(b, false);
    expect(b.disabled).toBe(false);
    expect(b.getAttribute('aria-busy')).toBe('false');
    expect(b.textContent).toBe('Crear sala');
  });
});

describe('distintivo', () => {
  test('usa la variante del kit cuando existe y la ignora cuando no', () => {
    expect(distintivo('Abierta', 'abierta').className).toBe('distintivo distintivo--abierta');
    expect(distintivo('Cualquiera', 'inventada').className).toBe('distintivo');
  });

  test('la rareza lleva su color por variable, no un hex a mano', () => {
    const d = distintivoDeRareza('LEGENDARIA');

    expect(d.dataset.rareza).toBe('legendaria');
    expect(d.textContent).toBe('Legendaria');
    expect(d.getAttribute('style')).toContain('--rareza-legendaria');
    expect(distintivoDeRareza(null)).toBeNull();
  });

  test('el marco de heroe solo cambia con las rarezas que el kit dibuja', () => {
    expect(claseDeMarco('epica')).toBe('marco-heroe marco-heroe--epica');
    expect(claseDeMarco('COMUN')).toBe('marco-heroe');
    expect(claseDeMarco(null)).toBe('marco-heroe');
  });
});

describe('encabezados', () => {
  test('la pagina lleva titulo, descripcion y zona de acciones', () => {
    const e = encabezadoDePagina({
      titulo: 'Torneo',
      descripcion: 'Un torneo cada 91 dias.',
      acciones: [boton({ texto: 'Crear torneo' })],
    });

    expect(e.querySelector('h1').textContent).toBe('Torneo');
    expect(e.querySelector('[data-zona="acciones-pagina"] .boton')).not.toBeNull();
  });

  test('sin acciones no se pinta la zona', () => {
    expect(
      encabezadoDeSeccion({ titulo: 'Equipos' }).querySelector('.encabezado-seccion__acciones'),
    ).toBeNull();
  });
});
