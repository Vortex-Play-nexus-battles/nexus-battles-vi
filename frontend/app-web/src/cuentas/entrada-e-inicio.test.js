/**
 * Revisión del modo jugador del 6-oct, puntos 2, 3, 5, 6 y 7 — lo que se ve
 * al entrar y al llegar al inicio, comprobado sobre el marcado de las vistas.
 *
 *   2 · fuera el salto «Mira lo que se vende en el Nexo»: con el <base> de las
 *       direcciones limpias, `href="#…"` llevaba a /frontend/app-web/src/cuentas/#…
 *   3 · la entrada (/login) no lleva la tienda debajo (sigue en la portada /)
 *   5 · el mismo logotipo en entrar, crear cuenta y recuperar la contraseña
 *   6 · el inicio del jugador tiene su escaparate de la tienda
 *   7 · sin «A dónde ir» repitiendo la barra
 */

import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const AQUI = dirname(fileURLToPath(import.meta.url));

/** El documento de una vista, sin sus guiones (aquí solo importa el marcado). */
function vista(fichero) {
  const html = readFileSync(join(AQUI, fichero), 'utf8');
  const doc = document.implementation.createHTMLDocument('');
  doc.documentElement.innerHTML = html.replace(/<script[\s\S]*?<\/script>/g, '');
  return { doc, html };
}

const LOGO = /shared\/ui-kit\/marca\/nexus-battles-vi\.png$/;

describe('la entrada (/login), solo para entrar — puntos 2 y 3', () => {
  const { doc, html } = vista('login.html');

  test('sin la vitrina de la tienda ni su guion', () => {
    expect(doc.querySelector('.vitrina-publica')).toBeNull();
    expect(doc.querySelector('[data-zona="productos-publicos"]')).toBeNull();
    expect(doc.getElementById('titulo-vitrina-publica')).toBeNull();
    expect(html).not.toMatch(/portada-tienda\.js/);
    expect(html).not.toMatch(/tienda-producto\.css|ficha-producto\.css/);
  });

  test('sin «Mira lo que se vende en el Nexo» ni anclas que el <base> rompa', () => {
    expect(doc.body.textContent).not.toMatch(/Mira lo que se vende/);
    expect(doc.querySelector('a[href^="#"]')).toBeNull();
  });

  test('el formulario de entrada sigue ahí', () => {
    expect(doc.getElementById('email')).not.toBeNull();
    expect(doc.getElementById('password')).not.toBeNull();
  });
});

describe('la portada pública (/) — punto 2', () => {
  const { doc } = vista('portada.html');

  test('conserva la tienda, sin el salto roto', () => {
    expect(doc.querySelector('[data-zona="productos-publicos"]')).not.toBeNull();
    expect(doc.querySelector('a[href^="#"]')).toBeNull();
    expect(doc.body.textContent).not.toMatch(/Mira lo que se vende/);
  });
});

describe('el mismo logotipo en las vistas de entrada — punto 5', () => {
  const login = vista('login.html').doc.querySelector('h1 img');

  test.each([
    'login.html',
    'registro.html',
    'restablecer-solicitar.html',
    'restablecer-confirmar.html',
    'verificar-cuenta.html',
  ])('%s: un solo h1, que es el logotipo oficial', (fichero) => {
    const { doc } = vista(fichero);
    const titulos = doc.querySelectorAll('h1');
    expect(titulos).toHaveLength(1);
    const logo = titulos[0].querySelector('img.entrada__logo');
    expect(logo).not.toBeNull();
    expect(logo.getAttribute('src')).toMatch(LOGO);
    expect(logo.getAttribute('alt')).toBe(login.getAttribute('alt'));
    expect(titulos[0].querySelector('source').getAttribute('srcset')).toMatch(
      /marca\/nexus-battles-vi\.webp$/,
    );
    // Ya no el nombre escrito a mano.
    expect(titulos[0].textContent.trim()).toBe('');
  });
});

describe('el inicio del jugador — puntos 6 y 7', () => {
  const { doc } = vista('index.html');

  test('tiene su escaparate: «Explora el Nexo», «Tienda» y «Ver toda la tienda»', () => {
    const seccion = doc.querySelector('.home__tienda');
    expect(seccion).not.toBeNull();
    expect(seccion.querySelector('.home__sobretitulo').textContent).toBe('Explora el Nexo');
    expect(seccion.querySelector('h2').textContent).toBe('Tienda');
    expect(seccion.getAttribute('aria-labelledby')).toBe(seccion.querySelector('h2').id);
    expect(seccion.querySelector('[data-zona="bloque-tienda"]')).not.toBeNull();
    const verTodo = seccion.querySelector('[data-zona="ver-tienda"]');
    expect(verTodo.textContent.trim()).toBe('Ver toda la tienda');
    expect(verTodo.getAttribute('href')).toBe('./tienda.html');
  });

  test('sin «A dónde ir» repitiendo la barra de arriba', () => {
    expect(doc.body.textContent).not.toMatch(/A dónde ir/);
    expect(doc.querySelector('[data-zona="accesos"]')).toBeNull();
  });
});
