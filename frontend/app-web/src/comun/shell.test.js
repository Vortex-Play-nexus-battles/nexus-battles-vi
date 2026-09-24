/**
 * Los tres armazones — UX-R3.0.
 *
 * RF-INV-008 (barra de navegación principal y su adaptación al estado de
 * autenticación y al rol) · HU-UX-001 · RNF-USA-002 · RNF-USA-003.
 *
 * Sustituye a la mitad de `cabecera-app.test.js`, que probaba una cabecera
 * única para todo el producto. Lo que se comprueba ahora es lo contrario:
 * que **no** es única, y que cuál se monta lo decide la matriz de acceso.
 */

import { jest } from '@jest/globals';

import {
  SECCIONES,
  SECCIONES_CONSOLA,
  montarArmazon,
  montarArmazonAdmin,
  montarArmazonJugador,
  montarArmazonPublico,
} from './shell.js';
import { CLAVES, leerSesion } from './sesion.js';

const BASE = 'http://localhost:8099/frontend/app-web/src/comun/shell.js';
const UID = '7a1e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55';

function tokenCon(claims) {
  const cuerpo = btoa(JSON.stringify(claims))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
  return `shell.${cuerpo}.firma`;
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
  // `vigilar` falso: el vigilante real deja temporizadores y oyentes en la
  // página, y aquí se prueba el armazón, no el vigilante (tiene sus pruebas).
  return montarArmazon(raiz, { base: BASE, navegar: jest.fn(), vigilar: jest.fn(), ...opciones });
}

function etiquetas(elemento) {
  return [...elemento.querySelectorAll('.cabecera__destino')].map((a) => a.textContent);
}

beforeEach(() => {
  sessionStorage.clear();
  document.body.innerHTML = '';
});

/* ========================================================================
   1. Portal — el hallazgo que abrió este bloque
   ======================================================================== */

describe('armazón público (portal de entrada)', () => {
  test('la pantalla de entrada NO lleva la navegación de la aplicación', () => {
    // Era el problema: quien todavía no tenía cuenta veía arriba «Jugar
    // online · Misiones · Torneo · Mi inventario · Subasta · Mi Cuenta», seis
    // destinos de los que cuatro le devolvían al login del que no había
    // salido. RF-INV-008 no lo pedía: su proceso principal dice que la barra
    // «adapta las opciones visibles al estado de autenticación».
    const { elemento } = montar({ vista: 'login' });
    expect(elemento.dataset.armazon).toBe('publico');
    expect(elemento.querySelector('.cabecera__nav')).toBeNull();
    expect(etiquetas(elemento)).toEqual([]);
    for (const seccion of SECCIONES) {
      expect(elemento.textContent).not.toContain(seccion.etiqueta);
    }
  });

  test('tampoco lleva campana, saldo ni menú de jugador', () => {
    const { elemento } = montar({ vista: 'registro' });
    expect(elemento.querySelector('.cabecera__campana')).toBeNull();
    expect(elemento.querySelector('[data-zona="creditos"]')).toBeNull();
    expect(elemento.querySelector('[data-zona="cuenta"]')).toBeNull();
    expect(elemento.querySelector('[data-zona="menu-cuenta"]')).toBeNull();
  });

  test('conserva la marca y ofrece la salida que no es esta pantalla', () => {
    // Ofrecer las dos siempre significa que una lleva a donde ya estás.
    const enLogin = montar({ vista: 'login' }).elemento;
    expect(enLogin.querySelector('.cabecera__marca')).not.toBeNull();
    expect(enLogin.querySelector('[data-zona="sesion"] a').textContent).toBe('Crear cuenta');
    expect(enLogin.textContent).not.toContain('Iniciar sesión');

    document.body.innerHTML = '';
    const enRegistro = montar({ vista: 'registro' }).elemento;
    expect(enRegistro.querySelector('[data-zona="sesion"] a').textContent).toBe('Iniciar sesión');
    expect(enRegistro.textContent).not.toContain('Crear cuenta');
  });

  test('las cuatro vistas del portal montan el mismo armazón', () => {
    for (const vista of ['login', 'registro', 'restablecer-solicitar', 'restablecer-confirmar']) {
      document.body.innerHTML = '';
      expect(montar({ vista }).elemento.dataset.armazon).toBe('publico');
    }
  });

  test('el nombre accesible de la marca no depende de la anchura', () => {
    const { elemento } = montarArmazonPublico(document.createElement('div'), { base: BASE });
    const marca = elemento.querySelector('.cabecera__marca');
    expect(marca.getAttribute('aria-label')).toBe('Nexus Battles VI — inicio');
    expect(marca.querySelector('.cabecera__marca-corta').getAttribute('aria-hidden')).toBe('true');
  });
});

/* ========================================================================
   2. Jugador — RF-INV-008 dentro de la aplicación
   ======================================================================== */

describe('armazón de jugador', () => {
  test('presenta las seis secciones del alcance, en su orden, y ninguna más', () => {
    conSesion();
    const { elemento } = montar({ vista: 'home' });
    expect(elemento.dataset.armazon).toBe('jugador');
    expect(etiquetas(elemento)).toEqual([
      'Jugar online',
      'Misiones',
      'Torneo',
      'Mi inventario',
      'Subasta',
      'Mi Cuenta',
    ]);
  });

  test('las etiquetas son las literales del requisito, no una versión mejorada', () => {
    // Se estudió pasarlas a «Inicio · Jugar · Colección…». RF-INV-008 las
    // nombra entre comillas y está «Confirmado»: cambiarlas es cambiar el
    // requisito, y eso no lo decide un rediseño.
    expect(SECCIONES.map((s) => s.etiqueta)).toEqual([
      'Jugar online',
      'Misiones',
      'Torneo',
      'Mi inventario',
      'Subasta',
      'Mi Cuenta',
    ]);
  });

  test('marca como activa la sección indicada, y solo esa', () => {
    conSesion();
    const { elemento } = montar({ vista: 'home', seccionActiva: 'inventario' });
    const activos = [...elemento.querySelectorAll('.cabecera__destino.activo')];
    expect(activos).toHaveLength(1);
    expect(activos[0].dataset.seccion).toBe('inventario');
    expect(activos[0].getAttribute('aria-current')).toBe('page');
  });

  test('el destino pendiente lo dice sin destapar el backlog', () => {
    // Decía «Todavía no publicada: HU-MIS (grupo-2)»: el número de una
    // historia y el nombre de un equipo interno, a la vista de cualquiera.
    // RF-INV-008 exige informar; no exige informar de esto.
    conSesion();
    const { elemento } = montar({ vista: 'home' });
    const misiones = elemento.querySelector('[data-seccion="misiones"]');
    expect(misiones.hasAttribute('href')).toBe(false);
    expect(misiones.getAttribute('aria-disabled')).toBe('true');
    expect(misiones.title).toBe('Misiones llegará en una próxima actualización');
    expect(elemento.innerHTML).not.toMatch(/HU-|grupo-\d|RF-|Sprint/);
  });

  test('funciona igual servido desde src/ que desde el borde', () => {
    conSesion();
    const raiz = document.createElement('div');
    const { elemento } = montarArmazonJugador(raiz, {
      sesion: leerSesion(sessionStorage),
      base: 'http://localhost:4321/src/comun/shell.js',
      navegar: jest.fn(),
    });
    expect(elemento.querySelector('[data-seccion="jugar"]').href).toBe(
      'http://localhost:4321/src/plataforma/salas-partidas/batallas.html',
    );
  });

  test('en una vitrina pública sin sesión, un destino privado lleva al login con vuelta', () => {
    const { elemento } = montar({ vista: 'torneos' });
    expect(elemento.dataset.armazon).toBe('jugador');
    const inventario = elemento.querySelector('[data-seccion="inventario"]');
    expect(inventario.dataset.exigeSesion).toBe('');
    expect(new URL(inventario.href).searchParams.get('volver')).toMatch(/inventario\.html$/);
    // El de torneos es público: va directo.
    expect(elemento.querySelector('[data-seccion="torneo"]').dataset.exigeSesion).toBeUndefined();
  });

  test('sin sesión ofrece entrar y registrarse; con sesión, nunca «Registrarse»', () => {
    const anonimo = montar({ vista: 'subastas' }).elemento;
    expect(
      [...anonimo.querySelectorAll('[data-zona="sesion"] a')].map((a) => a.textContent),
    ).toEqual(['Iniciar sesión', 'Registrarse']);

    document.body.innerHTML = '';
    conSesion({ apodo: 'valkiria' });
    const conCuenta = montar({ vista: 'home' }).elemento;
    expect(conCuenta.textContent).not.toMatch(/Registrarse/);
    expect(conCuenta.querySelector('[data-zona="apodo"]').textContent).toBe('valkiria');
    expect(conCuenta.querySelector('.cabecera__avatar').textContent).toBe('V');
    expect(conCuenta.querySelector('.cabecera__campana').href).toMatch(/notificaciones\.html$/);
  });

  test('el saldo se reserva pero no se inventa', () => {
    conSesion();
    const { elemento } = montar({ vista: 'home' });
    const creditos = elemento.querySelector('[data-zona="creditos"]');
    expect(creditos.hidden).toBe(true);
    expect(elemento.querySelector('[data-zona="saldo"]').textContent).toBe('');
  });

  test('un token caducado no es sesión: se dice por qué y no hay menú', () => {
    conSesion({ exp: Math.floor(Date.now() / 1000) - 10 });
    const { elemento, sesion } = montar({ vista: 'subastas' });
    expect(sesion.autenticado).toBe(false);
    expect(sesion.caducada).toBe(true);
    expect(elemento.querySelector('.cabecera__aviso-sesion').textContent).toBe('Tu sesión terminó');
    expect(elemento.querySelector('[data-zona="cuenta"]')).toBeNull();
  });

  test('el menú de cuenta se abre, se cierra con Escape y trae lo del jugador', () => {
    conSesion();
    const { elemento } = montar({ vista: 'home' });
    const boton = elemento.querySelector('[data-zona="cuenta"]');
    const menu = elemento.querySelector('[data-zona="menu-cuenta"]');
    expect(menu.hidden).toBe(true);
    boton.click();
    expect(menu.hidden).toBe(false);
    expect(boton.getAttribute('aria-expanded')).toBe('true');
    expect([...menu.querySelectorAll('[role="menuitem"]')].map((o) => o.textContent)).toEqual([
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

  test('un jugador no ve rastro de administración; quien la opera tiene su puerta', () => {
    conSesion({ rol: 'JUGADOR' });
    const jugador = montar({ vista: 'home' }).elemento;
    expect(jugador.textContent).not.toMatch(/Consola|Gestión de usuarios|Parámetros|Auditoría/);
    expect(jugador.querySelector('[data-zona="rol"]')).toBeNull();

    document.body.innerHTML = '';
    conSesion({ rol: 'ADMINISTRADOR' });
    const admin = montar({ vista: 'home' }).elemento;
    expect([...admin.querySelectorAll('[role="menuitem"]')].map((o) => o.textContent)).toContain(
      'Consola de operación',
    );
    expect(admin.querySelector('[data-zona="rol"]').textContent).toBe('Administrador');
  });

  test('cerrar sesión borra lo que dejó el login y lleva al login', () => {
    conSesion();
    sessionStorage.setItem(CLAVES.usuarioId, '7');
    const navegar = jest.fn();
    const { elemento } = montar({ vista: 'home', navegar });
    elemento.querySelector('[data-zona="cerrar-sesion"]').click();
    for (const clave of Object.values(CLAVES)) {
      expect(sessionStorage.getItem(clave)).toBeNull();
    }
    // R17 — el login dice «Cerraste sesión» en vez de aparecer sin más.
    expect(navegar).toHaveBeenCalledWith(
      'http://localhost:8099/frontend/app-web/src/cuentas/login.html?motivo=cerrada',
    );
  });
});

describe('sesión vigilada y preparación de la cuenta (R17)', () => {
  test('con sesión, el armazón deja puesto el vigilante; sin sesión, no', () => {
    const vigilar = jest.fn();
    montar({ vista: 'login', vigilar });
    expect(vigilar).not.toHaveBeenCalled();

    conSesion();
    document.body.innerHTML = '';
    montar({ vista: 'home', vigilar });
    expect(vigilar).toHaveBeenCalledTimes(1);
    expect(vigilar).toHaveBeenCalledWith(expect.objectContaining({ almacen: sessionStorage }));
  });

  test('«Preparando tu cuenta» es portal (sin navegación), pero ofrece salir', () => {
    conSesion();
    const navegar = jest.fn();
    const { elemento } = montar({ vista: 'preparando', navegar });

    expect(elemento.dataset.armazon).toBe('publico');
    expect(elemento.querySelector('.cabecera__nav')).toBeNull();
    expect(elemento.textContent).not.toContain('Crear cuenta');
    expect(elemento.textContent).not.toContain('Iniciar sesión');

    elemento.querySelector('[data-zona="cerrar-sesion"]').click();
    expect(sessionStorage.getItem(CLAVES.token)).toBeNull();
    expect(new URL(navegar.mock.calls[0][0]).searchParams.get('motivo')).toBe('cerrada');
  });
});

describe('buscador solo donde aplica', () => {
  test('por omisión no hay buscador', () => {
    conSesion();
    expect(montar({ vista: 'home' }).elemento.querySelector('[role="search"]')).toBeNull();
  });

  test('con buscador, enviar entrega el texto a la vista', () => {
    conSesion();
    const alBuscar = jest.fn();
    const { elemento } = montar({
      vista: 'inventario',
      buscador: { placeholder: 'Buscar productos', alBuscar },
    });
    elemento.querySelector('input[type="search"]').value = '  espada  ';
    elemento
      .querySelector('[role="search"]')
      .dispatchEvent(new Event('submit', { cancelable: true }));
    expect(alBuscar).toHaveBeenCalledWith('espada');
  });

  test('el portal nunca lleva buscador, aunque se lo pidan', () => {
    const { elemento } = montar({ vista: 'login', buscador: { alBuscar: jest.fn() } });
    expect(elemento.querySelector('[role="search"]')).toBeNull();
  });
});

/* ========================================================================
   3. Consola — la trastienda
   ======================================================================== */

describe('armazón de consola', () => {
  function consolaDe(rol) {
    conSesion({ rol });
    const raiz = document.createElement('div');
    document.body.appendChild(raiz);
    return montarArmazonAdmin(raiz, {
      sesion: leerSesion(sessionStorage),
      base: BASE,
      navegar: jest.fn(),
    }).elemento;
  }

  test('dice dónde estás: la marca lleva «Control» y entra en el nombre accesible', () => {
    const elemento = consolaDe('ADMINISTRADOR');
    expect(elemento.dataset.armazon).toBe('admin');
    expect(elemento.querySelector('.cabecera__marca-sufijo').textContent).toBe('Control');
    expect(elemento.querySelector('.cabecera__marca').getAttribute('aria-label')).toBe(
      'Nexus Battles VI Control — inicio',
    );
  });

  test('no es la navegación del jugador con cuatro opciones añadidas', () => {
    const elemento = consolaDe('SUPER_ADMINISTRADOR');
    for (const seccion of SECCIONES) {
      expect(etiquetas(elemento)).not.toContain(seccion.etiqueta);
    }
    expect(elemento.querySelector('.cabecera__campana')).toBeNull();
    expect(elemento.querySelector('[data-zona="creditos"]')).toBeNull();
  });

  test('el super administrador ve las ocho herramientas', () => {
    expect(etiquetas(consolaDe('SUPER_ADMINISTRADOR'))).toEqual(
      SECCIONES_CONSOLA.map((s) => s.etiqueta),
    );
  });

  test('el administrador no ve auditoría; el moderador solo ve lo suyo', () => {
    expect(etiquetas(consolaDe('ADMINISTRADOR'))).not.toContain('Auditoría');

    document.body.innerHTML = '';
    expect(etiquetas(consolaDe('MODERADOR'))).toEqual(['Resumen', 'Sanciones', 'Lista negra']);
  });

  test('enseña el rol, para que «esa opción no me aparece» tenga respuesta', () => {
    expect(consolaDe('MODERADOR').querySelector('[data-zona="rol"]').textContent).toBe('Moderador');
    document.body.innerHTML = '';
    expect(consolaDe('SUPER_ADMINISTRADOR').querySelector('[data-zona="rol"]').textContent).toBe(
      'Super administrador',
    );
  });

  test('tiene salida al juego: quien administra también juega', () => {
    const salida = consolaDe('ADMINISTRADOR').querySelector('.cabecera__salida');
    expect(salida.textContent).toBe('Volver al juego');
    expect(salida.href).toMatch(/cuentas\/index\.html$/);
  });
});

/* ========================================================================
   4. Despacho y plegado
   ======================================================================== */

describe('qué armazón se monta', () => {
  test('lo decide la matriz, no la vista', () => {
    conSesion({ rol: 'SUPER_ADMINISTRADOR' });
    expect(montar({ vista: 'gestion-usuarios' }).elemento.dataset.armazon).toBe('admin');
    document.body.innerHTML = '';
    expect(montar({ vista: 'home' }).elemento.dataset.armazon).toBe('jugador');
    document.body.innerHTML = '';
    expect(montar({ vista: 'login' }).elemento.dataset.armazon).toBe('publico');
  });

  test('sin vista conocida, cae en el portal si no hay sesión y en la aplicación si la hay', () => {
    expect(montar({ vista: null }).elemento.dataset.armazon).toBe('publico');
    document.body.innerHTML = '';
    conSesion();
    expect(montar({ vista: null }).elemento.dataset.armazon).toBe('jugador');
  });
});

describe('plegado en pantallas estrechas (UX-R2.9)', () => {
  test('los seis destinos siguen en el documento, plegados o no', () => {
    conSesion();
    const { elemento } = montar({ vista: 'home' });
    expect(elemento.querySelectorAll('.cabecera__destino')).toHaveLength(6);
    expect(elemento.dataset.navAbierto).toBe('no');
  });

  test('el disparador dice si está abierto y lo cambia al pulsarlo', () => {
    conSesion();
    const { elemento } = montar({ vista: 'home' });
    const boton = elemento.querySelector('[data-zona="alternar-nav"]');
    expect(boton.getAttribute('aria-expanded')).toBe('false');
    expect(boton.getAttribute('aria-controls')).toBe('cabecera-nav');
    boton.click();
    expect(boton.getAttribute('aria-expanded')).toBe('true');
    expect(elemento.dataset.navAbierto).toBe('si');
    expect(boton.getAttribute('aria-label')).toBe('Cerrar el menú de navegación');
  });

  test('Escape cierra el menú y devuelve el foco al disparador', () => {
    conSesion();
    const { elemento } = montar({ vista: 'home' });
    const boton = elemento.querySelector('[data-zona="alternar-nav"]');
    boton.click();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(elemento.dataset.navAbierto).toBe('no');
    expect(document.activeElement).toBe(boton);
  });

  test('la consola también se pliega', () => {
    conSesion({ rol: 'ADMINISTRADOR' });
    const { elemento } = montar({ vista: 'gestion-usuarios' });
    expect(elemento.querySelector('[data-zona="alternar-nav"]')).not.toBeNull();
  });

  test('el portal no necesita plegar nada: no tiene navegación', () => {
    expect(
      montar({ vista: 'login' }).elemento.querySelector('[data-zona="alternar-nav"]'),
    ).toBeNull();
  });
});

describe('una página interrumpida no se vuelve a montar', () => {
  test('con el documento marcado, montarArmazon no pinta nada', () => {
    conSesion({ rol: 'JUGADOR' });
    document.documentElement.dataset.acceso = 'denegado';
    const raiz = document.createElement('div');
    document.body.appendChild(raiz);
    const { elemento } = montarArmazon(raiz, { vista: 'home', base: BASE, navegar: jest.fn() });
    expect(elemento).toBeNull();
    expect(raiz.childElementCount).toBe(0);
    delete document.documentElement.dataset.acceso;
  });

  test('sin contenedor, tampoco revienta', () => {
    conSesion();
    expect(() => montarArmazon(null, { vista: 'home', base: BASE })).not.toThrow();
  });
});
