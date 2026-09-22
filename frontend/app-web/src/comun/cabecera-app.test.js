/**
 * HU-UX-001 · cabecera única: navegación, sesión y estado activo.
 *
 * Cubre también los criterios de HU-INV-004 (#88) que antes probaba
 * `barra-navegacion.test.js`: las seis secciones en orden, estado activo
 * único, y que un visitante solo ve registro/inicio de sesión.
 */

import { jest } from '@jest/globals';

import {
  CLAVES,
  SECCIONES,
  cerrarSesion,
  exigirSesion,
  leerSesion,
  montarCabecera,
  resolver,
  rutaDeVuelta,
} from './cabecera-app.js';

const BASE = 'http://localhost:8099/frontend/app-web/src/comun/cabecera-app.js';
const UID = '7a1e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55';

function tokenCon(claims) {
  const cuerpo = btoa(JSON.stringify(claims))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
  return `cabecera.${cuerpo}.firma`;
}

function conSesion({
  apodo = 'Ana',
  rol = 'JUGADOR',
  exp = Math.floor(Date.now() / 1000) + 3600,
} = {}) {
  sessionStorage.setItem(CLAVES.token, tokenCon({ uid: UID, sub: apodo, rol, exp }));
  sessionStorage.setItem(CLAVES.apodo, apodo);
  sessionStorage.setItem(CLAVES.rol, rol);
}

function montar(opciones = {}) {
  const raiz = document.createElement('div');
  document.body.appendChild(raiz);
  return montarCabecera(raiz, { base: BASE, navegar: jest.fn(), ...opciones });
}

beforeEach(() => {
  sessionStorage.clear();
  document.body.innerHTML = '';
});

describe('navegacion (HU-INV-004)', () => {
  test('presenta las seis secciones del alcance, en su orden, y ninguna mas', () => {
    const { elemento } = montar();
    const destinos = [...elemento.querySelectorAll('.cabecera__destino')].map((d) => d.textContent);
    expect(destinos).toEqual([
      'Jugar online',
      'Misiones',
      'Torneo',
      'Mi inventario',
      'Subasta',
      'Mi Cuenta',
    ]);
    expect(SECCIONES.map((s) => s.id)).toEqual([
      'jugar',
      'misiones',
      'torneo',
      'inventario',
      'subasta',
      'cuenta',
    ]);
  });

  test('marca como activa la seccion indicada, y solo esa', () => {
    conSesion();
    const { elemento } = montar({ seccionActiva: 'inventario' });
    const activos = [...elemento.querySelectorAll('.cabecera__destino[aria-current="page"]')];
    expect(activos.map((a) => a.dataset.seccion)).toEqual(['inventario']);
    expect(activos[0].classList.contains('activo')).toBe(true);
  });

  test('las secciones con vista tienen destino real; las pendientes lo dicen y no fingen', () => {
    conSesion();
    const { elemento } = montar();
    const jugar = elemento.querySelector('[data-seccion="jugar"]');
    expect(jugar.href).toBe(
      'http://localhost:8099/frontend/app-web/src/plataforma/salas-partidas/batallas.html',
    );
    const misiones = elemento.querySelector('[data-seccion="misiones"]');
    expect(misiones.hasAttribute('href')).toBe(false);
    expect(misiones.getAttribute('aria-disabled')).toBe('true');
    expect(misiones.title).toMatch(/Todavía no publicada/);
  });

  test('funciona igual servido desde src/, no solo desde el borde', () => {
    const { elemento } = montar({ base: 'http://localhost:5173/src/comun/cabecera-app.js' });
    expect(elemento.querySelector('[data-seccion="subasta"]').href).toBe(
      'http://localhost:5173/src/cuentas/subastas.html',
    );
    expect(resolver('../cuentas/login.html', 'http://localhost:5173/src/comun/x.js')).toBe(
      'http://localhost:5173/src/cuentas/login.html',
    );
  });

  test('sin sesion, una seccion privada lleva al login con vuelta a ella; la publica no', () => {
    const { elemento } = montar();
    const inventario = new URL(elemento.querySelector('[data-seccion="inventario"]').href);
    expect(inventario.pathname).toBe('/frontend/app-web/src/cuentas/login.html');
    expect(inventario.searchParams.get('volver')).toBe(
      '/frontend/app-web/src/contenido/inventario/inventario.html',
    );
    expect(elemento.querySelector('[data-seccion="subasta"]').href).toMatch(/subastas\.html$/);
  });
});

describe('sesion', () => {
  test('sin sesion: Iniciar sesion y Registrarse, sin avatar ni campana', () => {
    const { elemento, sesion } = montar();
    expect(sesion.autenticado).toBe(false);
    const botones = [...elemento.querySelectorAll('[data-zona="sesion"] a')].map(
      (a) => a.textContent,
    );
    expect(botones).toEqual(['Iniciar sesión', 'Registrarse']);
    expect(elemento.querySelector('[data-zona="cuenta"]')).toBeNull();
    expect(elemento.querySelector('.cabecera__campana')).toBeNull();
  });

  test('con sesion: avatar con inicial, apodo, campana y menu de cuenta; nunca Registrarse', () => {
    conSesion({ apodo: 'demo_grupo6' });
    const { elemento, sesion } = montar();
    expect(sesion.autenticado).toBe(true);
    expect(elemento.querySelector('[data-zona="apodo"]').textContent).toBe('demo_grupo6');
    expect(elemento.querySelector('.cabecera__avatar').textContent).toBe('D');
    expect(elemento.querySelector('.cabecera__campana').href).toMatch(/notificaciones\.html$/);
    expect(elemento.textContent).not.toMatch(/Registrarse/);
  });

  test('el menu de cuenta se abre y se cierra, y trae las opciones de la cuenta', () => {
    conSesion();
    const { elemento } = montar();
    const boton = elemento.querySelector('[data-zona="cuenta"]');
    const menu = elemento.querySelector('[data-zona="menu-cuenta"]');
    expect(menu.hidden).toBe(true);
    boton.click();
    expect(menu.hidden).toBe(false);
    expect(boton.getAttribute('aria-expanded')).toBe('true');
    const opciones = [...menu.querySelectorAll('[role="menuitem"]')].map((o) => o.textContent);
    expect(opciones).toEqual([
      'Mi perfil',
      'Historial de transacciones',
      'Mis cofres',
      'Tienda',
      'Mis sanciones',
      'Cerrar sesión',
    ]);
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(menu.hidden).toBe(true);
  });

  test('un administrador ve ademas las opciones administrativas; un jugador no', () => {
    conSesion({ rol: 'ADMINISTRADOR' });
    const { elemento } = montar();
    const opciones = [...elemento.querySelectorAll('[role="menuitem"]')].map((o) => o.textContent);
    expect(opciones).toEqual(
      expect.arrayContaining([
        'Gestión de usuarios',
        'Lista negra',
        'Sanciones',
        'Parámetros',
        'Auditoría',
      ]),
    );
    expect(elemento.querySelector('[data-zona="rol"]').textContent).toBe('administrador');
  });

  test('cerrar sesion borra todo lo que dejo el login y lleva al login', () => {
    conSesion();
    sessionStorage.setItem(CLAVES.usuarioId, '7');
    const navegar = jest.fn();
    const { elemento } = montar({ navegar });
    elemento.querySelector('[data-zona="cerrar-sesion"]').click();
    for (const clave of Object.values(CLAVES)) {
      expect(sessionStorage.getItem(clave)).toBeNull();
    }
    expect(navegar).toHaveBeenCalledWith(
      'http://localhost:8099/frontend/app-web/src/cuentas/login.html',
    );
  });

  test('un token caducado no es sesion: se ofrece iniciar sesion y se dice por que', () => {
    conSesion({ exp: Math.floor(Date.now() / 1000) - 10 });
    const { elemento, sesion } = montar();
    expect(sesion.autenticado).toBe(false);
    expect(sesion.caducada).toBe(true);
    expect(elemento.querySelector('.cabecera__aviso-sesion').textContent).toMatch(/caducó/);
    expect(elemento.querySelector('[data-zona="cuenta"]')).toBeNull();
  });

  test('leerSesion prefiere el uid del token y el apodo del login', () => {
    conSesion({ apodo: 'Ana' });
    const sesion = leerSesion(sessionStorage);
    expect(sesion.uid).toBe(UID);
    expect(sesion.apodo).toBe('Ana');
    expect(sesion.rol).toBe('JUGADOR');
  });
});

describe('rutas privadas', () => {
  test('exigirSesion sin sesion lleva al login con la ruta de vuelta y devuelve null', () => {
    const navegar = jest.fn();
    const resultado = exigirSesion({
      almacen: sessionStorage,
      ubicacion: { pathname: '/frontend/app-web/src/cuentas/perfil.html', search: '?tab=2' },
      navegar,
      base: BASE,
    });
    expect(resultado).toBeNull();
    const destino = new URL(navegar.mock.calls[0][0]);
    expect(destino.pathname).toBe('/frontend/app-web/src/cuentas/login.html');
    expect(destino.searchParams.get('volver')).toBe(
      '/frontend/app-web/src/cuentas/perfil.html?tab=2',
    );
  });

  test('exigirSesion con sesion caducada la borra y lo dice en el motivo', () => {
    conSesion({ exp: 1 });
    const navegar = jest.fn();
    exigirSesion({
      almacen: sessionStorage,
      ubicacion: { pathname: '/x', search: '' },
      navegar,
      base: BASE,
    });
    expect(sessionStorage.getItem(CLAVES.token)).toBeNull();
    expect(new URL(navegar.mock.calls[0][0]).searchParams.get('motivo')).toBe('caducada');
  });

  test('exigirSesion con sesion valida no navega y la devuelve', () => {
    conSesion();
    const navegar = jest.fn();
    const sesion = exigirSesion({
      almacen: sessionStorage,
      ubicacion: { pathname: '/x', search: '' },
      navegar,
      base: BASE,
    });
    expect(sesion.autenticado).toBe(true);
    expect(navegar).not.toHaveBeenCalled();
  });

  test('la ruta de vuelta solo admite rutas del propio origen', () => {
    expect(
      rutaDeVuelta(
        '?volver=%2Ffrontend%2Fapp-web%2Fsrc%2Fplataforma%2Fsalas-partidas%2Fbatallas.html',
      ),
    ).toBe('/frontend/app-web/src/plataforma/salas-partidas/batallas.html');
    expect(rutaDeVuelta('?volver=https%3A%2F%2Fmalo.example%2F')).toBeNull();
    expect(rutaDeVuelta('?volver=%2F%2Fmalo.example')).toBeNull();
    expect(rutaDeVuelta('')).toBeNull();
  });

  test('cerrarSesion funciona sin cabecera montada (desde cualquier vista)', () => {
    conSesion();
    const navegar = jest.fn();
    cerrarSesion({ almacen: sessionStorage, navegar, base: BASE });
    expect(sessionStorage.getItem(CLAVES.token)).toBeNull();
    expect(navegar).toHaveBeenCalledTimes(1);
  });
});

describe('buscador solo donde aplica', () => {
  test('por omision no hay buscador', () => {
    const { elemento } = montar();
    expect(elemento.querySelector('[role="search"]')).toBeNull();
  });

  test('con buscador, enviar entrega el texto a la vista', () => {
    const alBuscar = jest.fn();
    const { elemento } = montar({ buscador: { placeholder: 'Buscar productos', alBuscar } });
    const campo = elemento.querySelector('input[type="search"]');
    campo.value = '  espada  ';
    elemento
      .querySelector('[role="search"]')
      .dispatchEvent(new Event('submit', { cancelable: true }));
    expect(alBuscar).toHaveBeenCalledWith('espada');
  });
});

/**
 * UX-R2.9 — la cabecera en pantallas estrechas.
 *
 * El laboratorio visual (#600) medía **180 px de cabecera a 375 px de ancho**
 * —casi el triple de los 64 px del diseño— en 30 de las 31 vistas en móvil y
 * en 18 en tablet: 48 de las 155 combinaciones. La página empezaba media
 * pantalla más abajo.
 *
 * Lo que se comprueba aquí es que plegar **no es esconder**: HU-INV-004 CA-01
 * exige los seis accesos siempre, y siguen estando los seis.
 */
describe('cabecera plegable (UX-R2.9, HU-INV-004 CA-01)', () => {
  test('los seis destinos siguen en el documento, plegados o no', () => {
    const { elemento } = montar();

    expect(elemento.dataset.navAbierto).toBe('no');
    // Plegado NO es `hidden` ni `display:none` en el marcado: el nav está,
    // con sus seis enlaces, y quien lo oculta es una media query.
    expect(elemento.querySelectorAll('.cabecera__destino')).toHaveLength(6);
    expect(elemento.querySelector('.cabecera__nav').hidden).toBe(false);
  });

  test('el disparador dice si está abierto, y lo cambia al pulsarlo', () => {
    const { elemento } = montar();
    const alternar = elemento.querySelector('[data-zona="alternar-nav"]');

    expect(alternar.getAttribute('aria-expanded')).toBe('false');
    expect(alternar.getAttribute('aria-controls')).toBe('cabecera-nav');
    expect(elemento.querySelector('.cabecera__nav').id).toBe('cabecera-nav');

    alternar.click();
    expect(alternar.getAttribute('aria-expanded')).toBe('true');
    expect(elemento.dataset.navAbierto).toBe('si');

    alternar.click();
    expect(alternar.getAttribute('aria-expanded')).toBe('false');
    expect(elemento.dataset.navAbierto).toBe('no');
  });

  test('la etiqueta del disparador dice lo que va a pasar, no dónde está', () => {
    const { elemento } = montar();
    const alternar = elemento.querySelector('[data-zona="alternar-nav"]');

    expect(alternar.getAttribute('aria-label')).toBe('Abrir el menú de navegación');
    alternar.click();
    expect(alternar.getAttribute('aria-label')).toBe('Cerrar el menú de navegación');
  });

  test('Escape cierra el menú y devuelve el foco al disparador', () => {
    // Sin esto el teclado se queda con el menú abierto y sin forma de
    // cerrarlo (WCAG 2.1.2, «sin trampas de teclado»).
    const { elemento } = montar();
    const alternar = elemento.querySelector('[data-zona="alternar-nav"]');
    alternar.click();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));

    expect(elemento.dataset.navAbierto).toBe('no');
    expect(alternar.getAttribute('aria-expanded')).toBe('false');
    expect(document.activeElement).toBe(alternar);
  });

  test('la marca corta no cambia el nombre accesible', () => {
    // «NEXUS BATTLES VI» mide 154 px y a 375 px se comía media fila. La
    // versión corta es decoración: quien usa lector de pantalla sigue oyendo
    // el nombre entero, que lo fija el aria-label.
    const { elemento } = montar();
    const marca = elemento.querySelector('.cabecera__marca');

    expect(marca.getAttribute('aria-label')).toBe('Nexus Battles VI — inicio');
    expect(marca.querySelector('.cabecera__marca-larga').textContent).toBe('NEXUS BATTLES VI');
    expect(marca.querySelector('.cabecera__marca-corta').getAttribute('aria-hidden')).toBe('true');
  });
});
