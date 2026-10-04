/**
 * F6 (auditoría del 4-oct, cambio autorizado n.º 3) — la portada pública en
 * `/`: los enlaces llevan a `/registro`, `/login` e `/inicio` (limpias detrás
 * del borde) y, con sesión, se ofrece «Ir a mi inicio» en vez de entrar otra
 * vez.
 */

import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { DESTINOS, enlazar, mostrarAcceso } from './portada.js';

const AQUI = dirname(fileURLToPath(import.meta.url));
const HTML = readFileSync(join(AQUI, 'portada.html'), 'utf8');

function montar({ limpias = false } = {}) {
  document.documentElement.innerHTML = HTML.replace(/<script[\s\S]*?<\/script>/g, '');
  if (limpias) {
    const marca = document.createElement('meta');
    marca.name = 'nexus-rutas';
    marca.content = 'limpias';
    document.head.append(marca);
  }
}

afterEach(() => {
  document.documentElement.innerHTML = '<head></head><body></body>';
});

describe('la portada pública (F6)', () => {
  test('qué es el juego, cómo empezar y la tienda: un h1, las dos salidas y la zona de productos', () => {
    montar();

    expect(document.querySelectorAll('h1')).toHaveLength(1);
    expect(document.querySelector('h1 img').getAttribute('alt')).toMatch(/Nexus Battles VI/);
    expect(document.querySelector('[data-accion="crear-cuenta"]').textContent).toMatch(
      /Crear una cuenta/,
    );
    expect(document.querySelector('[data-accion="entrar"]').textContent).toMatch(/entrar/);
    expect(document.querySelector('[data-zona="productos-publicos"]')).not.toBeNull();
    // Sin formulario de entrada: entrar es ir a /login.
    expect(document.getElementById('email')).toBeNull();
    expect(document.body.dataset.vista).toBe('portada');
  });

  test('detrás del borde, los enlaces van a las direcciones limpias', () => {
    montar({ limpias: true });

    enlazar(document);

    const ruta = (accion) =>
      new URL(document.querySelector(`a[data-accion="${accion}"]`).href).pathname;
    expect(ruta('crear-cuenta')).toBe('/registro');
    expect(ruta('entrar')).toBe('/login');
    expect(ruta('ir-al-inicio')).toBe('/inicio');
    // Los dos «crear cuenta» (el de la tarjeta y el del pie de la tienda).
    expect(
      Array.from(document.querySelectorAll('a[data-accion="crear-cuenta"]')).map(
        (a) => new URL(a.href).pathname,
      ),
    ).toEqual(['/registro', '/registro']);
  });

  test('sin la marca del borde, la ruta de cada fichero', () => {
    montar();

    enlazar(document);

    expect(document.querySelector('a[data-accion="entrar"]').href).toMatch(/cuentas\/login\.html$/);
    expect(Object.keys(DESTINOS)).toEqual(['crear-cuenta', 'entrar', 'ir-al-inicio']);
  });

  test('sin sesión: crear cuenta o entrar; con sesión: «Ir a mi inicio» y su saludo', () => {
    montar();
    const sin = document.querySelector('[data-zona="acciones-sin-sesion"]');
    const con = document.querySelector('[data-zona="acciones-con-sesion"]');

    mostrarAcceso(document, { autenticado: false });
    expect(sin.hidden).toBe(false);
    expect(con.hidden).toBe(true);

    mostrarAcceso(document, { autenticado: true, apodo: 'Ana' });
    expect(sin.hidden).toBe(true);
    expect(con.hidden).toBe(false);
    expect(document.querySelector('[data-zona="saludo"]').textContent).toBe('Hola, Ana.');
  });

  test('el apodo entra como texto, nunca como marcado', () => {
    montar();

    mostrarAcceso(document, { autenticado: true, apodo: '<img src=x onerror=alert(1)>' });

    const saludo = document.querySelector('[data-zona="saludo"]');
    expect(saludo.querySelector('img')).toBeNull();
    expect(saludo.textContent).toContain('<img');
  });
});
