/**
 * SA-7 — punto crítico externo del informe de revisión: ningún enlace enseña
 * la carpeta ni el `.html` de una vista cuando el borde sirve las direcciones
 * limpias.
 */

import { MATRIZ } from './matriz-acceso.js';
import {
  direccionLimpia,
  limpiarEnlaces,
  limpiasPorCamino,
  vigilarEnlaces,
} from './enlaces-limpios.js';

const BASE = 'http://localhost/frontend/app-web/src/comun/enlaces-limpios.js';
const ORIGEN = 'http://localhost';

/** Como sirve el borde una vista de `cuentas/`: `<base>` a su carpeta y la marca. */
function paginaDelBorde({ conMarca = true } = {}) {
  document.head.innerHTML = `
    <base href="/frontend/app-web/src/cuentas/">
    ${conMarca ? '<meta name="nexus-rutas" content="limpias">' : ''}`;
  document.body.innerHTML = `
    <a data-id="alta" href="./crear-cuenta-admin.html">Crear cuenta administrativa</a>
    <a data-id="ficha" href="../plataforma/moderacion-sanciones/ficha-usuario.html?usuario=7#sanciones">Ver ficha</a>
    <a data-id="fuera" href="https://ejemplo.test/cuentas/login.html">Otra web</a>
    <a data-id="api" href="/api/v1/productos/recursos/1">Recurso</a>
    <a data-id="correo" href="mailto:soporte@nexus.test">Soporte</a>
    <a data-id="modulo" href="./portada-tienda.js">Un módulo</a>`;
}

const enlace = (id) => document.querySelector(`[data-id="${id}"]`);
const tras = () => new Promise((resolver) => setTimeout(resolver, 0));

let dejar = () => {};

afterEach(() => {
  dejar();
  dejar = () => {};
  document.head.innerHTML = '';
  document.body.innerHTML = '';
});

test('cada vista de la matriz tiene su dirección limpia, por el camino de su fichero', () => {
  const limpias = limpiasPorCamino(BASE);
  expect(limpias.size).toBe(Object.keys(MATRIZ).length);
  expect(limpias.get('/frontend/app-web/src/cuentas/login.html')).toBe('/login');
  expect(limpias.get('/frontend/app-web/src/cuentas/gestion-usuarios.html')).toBe(
    '/consola/usuarios',
  );
  expect(limpias.get('/frontend/app-web/src/plataforma/salas-partidas/sala-batalla.html')).toBe(
    '/jugar/sala',
  );
  expect([...limpias.values()].some((limpia) => limpia.includes('.html'))).toBe(false);
});

test('una URL de vista se traduce con su consulta y su ancla; lo demás no', () => {
  const limpias = limpiasPorCamino(BASE);
  expect(
    direccionLimpia(
      'http://localhost/frontend/app-web/src/cuentas/pujas.html?id=9&accion=comprar#detalle',
      limpias,
      ORIGEN,
    ),
  ).toBe('http://localhost/subastas/pujar?id=9&accion=comprar#detalle');
  expect(
    direccionLimpia('https://otra.test/frontend/app-web/src/cuentas/pujas.html', limpias, ORIGEN),
  ).toBeNull();
  expect(direccionLimpia('http://localhost/api/v1/salas', limpias, ORIGEN)).toBeNull();
  expect(direccionLimpia('no es una url', limpias, ORIGEN)).toBeNull();
});

test('detrás del borde, los enlaces escritos con la ruta del fichero pasan a la limpia', () => {
  paginaDelBorde();
  dejar = vigilarEnlaces({ documento: document, base: BASE });

  expect(enlace('alta').getAttribute('href')).toBe(
    'http://localhost/consola/usuarios/nueva-cuenta',
  );
  expect(enlace('ficha').getAttribute('href')).toBe(
    'http://localhost/consola/usuarios/ficha?usuario=7#sanciones',
  );
  expect(enlace('fuera').getAttribute('href')).toBe('https://ejemplo.test/cuentas/login.html');
  expect(enlace('api').getAttribute('href')).toBe('/api/v1/productos/recursos/1');
  expect(enlace('correo').getAttribute('href')).toBe('mailto:soporte@nexus.test');
  expect(enlace('modulo').getAttribute('href')).toBe('./portada-tienda.js');
  const conCarpeta = [...document.querySelectorAll('a')].filter((a) =>
    /\/frontend\/app-web\/src\/.*\.html/.test(a.href),
  );
  expect(conCarpeta).toEqual([]);
});

test('los enlaces que la vista pinta o cambia después también se traducen', async () => {
  paginaDelBorde();
  dejar = vigilarEnlaces({ documento: document, base: BASE });

  const tarjeta = document.createElement('article');
  tarjeta.innerHTML = '<a href="./publicar-subasta.html">Publicar una subasta</a>';
  document.body.append(tarjeta);
  const nuevo = document.createElement('a');
  nuevo.href = '../plataforma/salas-partidas/sala-batalla.html?sala=s1';
  document.body.append(nuevo);
  await tras();
  expect(tarjeta.querySelector('a').getAttribute('href')).toBe(
    'http://localhost/subastas/publicar',
  );
  expect(nuevo.getAttribute('href')).toBe('http://localhost/jugar/sala?sala=s1');

  enlace('api').setAttribute('href', './historial-transacciones.html');
  await tras();
  expect(enlace('api').getAttribute('href')).toBe('http://localhost/cuenta/movimientos');
});

test('sin la marca del borde no toca nada: la dirección limpia ahí no existe', async () => {
  paginaDelBorde({ conMarca: false });
  dejar = vigilarEnlaces({ documento: document, base: BASE });

  expect(enlace('alta').getAttribute('href')).toBe('./crear-cuenta-admin.html');
  const nuevo = document.createElement('a');
  nuevo.setAttribute('href', './tienda.html');
  document.body.append(nuevo);
  await tras();
  expect(nuevo.getAttribute('href')).toBe('./tienda.html');
});

test('vigilar dos veces no duplica, y dejar de vigilar deja los enlaces nuevos como vienen', async () => {
  paginaDelBorde();
  dejar = vigilarEnlaces({ documento: document, base: BASE });
  const otra = vigilarEnlaces({ documento: document, base: BASE });
  otra();

  const nuevo = document.createElement('a');
  nuevo.setAttribute('href', './tienda.html');
  document.body.append(nuevo);
  await tras();
  expect(nuevo.getAttribute('href')).toBe('http://localhost/tienda');

  dejar();
  dejar = () => {};
  const despues = document.createElement('a');
  despues.setAttribute('href', './tienda.html');
  document.body.append(despues);
  await tras();
  expect(despues.getAttribute('href')).toBe('./tienda.html');
});

test('limpiarEnlaces cuenta lo que cambió y deja la raíz que no es un enlace', () => {
  paginaDelBorde();
  const limpias = limpiasPorCamino(BASE);
  expect(limpiarEnlaces(document.body, limpias, ORIGEN)).toBe(2);
  expect(limpiarEnlaces(document.body, limpias, ORIGEN)).toBe(0);
  expect(limpiarEnlaces(null, limpias, ORIGEN)).toBe(0);
});
