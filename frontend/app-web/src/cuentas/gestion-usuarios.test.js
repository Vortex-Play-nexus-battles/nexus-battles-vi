import { jest } from '@jest/globals';
import { setCurrentRole, setPermissionMatrix } from './directives/has-permission.directive.js';
import {
  abrirFichaDesdeLaDireccion,
  cargarMatrizYVerificarAcceso,
  clasificarBusqueda,
  configurarEventos,
  RESULTADOS_POR_BUSQUEDA,
  usuarioDeLaDireccion,
} from './gestion-usuarios.js';

const MATRIZ_BACKEND = {
  ADMINISTRADOR: {
    GESTIONAR_CUENTAS: 'GRANTED',
    ASIGNAR_ROL: 'DENIED',
  },
  SUPER_ADMINISTRADOR: {
    GESTIONAR_CUENTAS: 'GRANTED',
    ASIGNAR_ROL: 'GRANTED',
  },
};

function prepararDom() {
  document.body.innerHTML = `
    <section id="acceso-denegado" hidden>
      <h2 data-zona="titulo-bloqueo"></h2>
      <p data-zona="detalle-bloqueo"></p>
      <button data-zona="reintentar-bloqueo" hidden></button>
    </section>
    <section id="gestion-contenedor" hidden>
      <button id="btn-cambiar-rol" data-has-permission="ASIGNAR_ROL"></button>
    </section>
  `;
}

const bloqueo = () => ({
  titulo: document.querySelector('[data-zona="titulo-bloqueo"]').textContent,
  detalle: document.querySelector('[data-zona="detalle-bloqueo"]').textContent,
  reintentar: document.querySelector('[data-zona="reintentar-bloqueo"]'),
});

function respuestaMatriz(matrix = MATRIZ_BACKEND) {
  return {
    ok: true,
    status: 200,
    json: async () => ({ version: '1.1.0', matrix }),
  };
}

describe('Gestión de usuarios - carga de matriz RBAC', () => {
  beforeEach(() => {
    sessionStorage.clear();
    setPermissionMatrix({});
    prepararDom();
  });

  test('ADMINISTRADOR entra a la pantalla pero no ve Cambiar rol', async () => {
    setCurrentRole('ADMINISTRADOR');
    sessionStorage.setItem('nexus.token', 'jwt-admin');
    const fetchImpl = jest.fn().mockResolvedValue(respuestaMatriz());

    await cargarMatrizYVerificarAcceso({ fetchImpl });

    expect(fetchImpl).toHaveBeenCalledWith('/api/v1/rbac/matrix', {
      headers: { Authorization: 'Bearer jwt-admin' },
    });
    expect(document.getElementById('gestion-contenedor').hidden).toBe(false);
    expect(document.getElementById('acceso-denegado').hidden).toBe(true);
    expect(document.getElementById('btn-cambiar-rol').style.display).toBe('none');
  });

  test('SUPER_ADMINISTRADOR entra y ve Cambiar rol', async () => {
    setCurrentRole('SUPER_ADMINISTRADOR');

    await cargarMatrizYVerificarAcceso({
      fetchImpl: jest.fn().mockResolvedValue(respuestaMatriz()),
    });

    expect(document.getElementById('gestion-contenedor').hidden).toBe(false);
    expect(document.getElementById('acceso-denegado').hidden).toBe(true);
    expect(document.getElementById('btn-cambiar-rol').style.display).toBe('');
  });

  test('mantiene acceso denegado si no puede cargar la matriz', async () => {
    setCurrentRole('SUPER_ADMINISTRADOR');
    const errorSpy = jest.spyOn(console, 'error').mockImplementation(() => {});

    await cargarMatrizYVerificarAcceso({
      fetchImpl: jest.fn().mockRejectedValue(new Error('backend caído')),
    });

    expect(document.getElementById('gestion-contenedor').hidden).toBe(true);
    expect(document.getElementById('acceso-denegado').hidden).toBe(false);
    expect(document.getElementById('btn-cambiar-rol').style.display).toBe('none');
    errorSpy.mockRestore();
  });
});

/**
 * UX-R3.3 — «no puedes» y «no pude comprobarlo» no son el mismo mensaje.
 *
 * La UI falla cerrada contra la matriz del servidor, y eso no cambia. Lo que
 * cambia es lo que lee la persona: cuando `ms-identidad` no contestaba —en
 * dev no corre— un administrador legítimo leía «No tienes permisos
 * suficientes». Es falso, y es la clase de mensaje que hace perder una tarde:
 * quien lo lee va a pedir que le revisen el rol, no a mirar si el servicio
 * está caído.
 */
describe('§17 — por qué está bloqueado', () => {
  beforeEach(() => {
    prepararDom();
    setPermissionMatrix({});
  });

  test('con la matriz cargada y sin permiso: es tu rol, y no se ofrece reintentar', async () => {
    setCurrentRole('JUGADOR');

    await cargarMatrizYVerificarAcceso({
      fetchImpl: jest.fn().mockResolvedValue(respuestaMatriz()),
    });

    expect(document.getElementById('acceso-denegado').hidden).toBe(false);
    const visto = bloqueo();
    expect(visto.titulo).toBe('No tienes acceso a esta sección.');
    expect(visto.detalle).toContain('no tiene habilitada');
    // Reintentar no arreglaría nada: el rol seguiría siendo el mismo.
    expect(visto.reintentar.hidden).toBe(true);
  });

  test('sin matriz: es el servicio, se dice, y se ofrece reintentar', async () => {
    setCurrentRole('SUPER_ADMINISTRADOR');
    const errorSpy = jest.spyOn(console, 'error').mockImplementation(() => {});

    await cargarMatrizYVerificarAcceso({
      fetchImpl: jest.fn().mockRejectedValue(new Error('backend caído')),
    });

    expect(document.getElementById('gestion-contenedor').hidden).toBe(true);
    const visto = bloqueo();
    expect(visto.titulo).toBe('Esta función no está disponible temporalmente.');
    expect(visto.detalle).toContain('No es un problema de tu cuenta');
    expect(visto.reintentar.hidden).toBe(false);
    // Ni un código crudo a la vista (§17).
    expect(`${visto.titulo} ${visto.detalle}`).not.toMatch(/\b(401|403|404|500|502)\b/);
    errorSpy.mockRestore();
  });

  test('una matriz vacía del servidor cuenta como «no pude comprobarlo»', async () => {
    // Un 200 con `{ matrix: {} }` no es una respuesta válida: sin matriz no se
    // puede decidir nada, y decirle a un super administrador que no tiene
    // permiso sería inventarse una razón.
    setCurrentRole('SUPER_ADMINISTRADOR');
    const errorSpy = jest.spyOn(console, 'error').mockImplementation(() => {});

    await cargarMatrizYVerificarAcceso({
      fetchImpl: jest.fn().mockResolvedValue(respuestaMatriz({})),
    });

    expect(bloqueo().titulo).toBe('Esta función no está disponible temporalmente.');
    errorSpy.mockRestore();
  });
});

/**
 * FI-R3 — «Buscar» busca.
 *
 * Lo que hacia antes: guardar el ID en `sessionStorage`, abrir el panel con
 * todos los campos en «-» y escribir que la consulta «quedara conectada cuando
 * el backend exponga el endpoint administrativo». Ese endpoint existe:
 * `AdminGestionUsuarioController` tiene `@GetMapping("/{usuarioId}")`.
 *
 * Lo grave no era la falta de datos, era que el panel se abria igual: las
 * acciones de suspender, banear y restablecer quedaban habilitadas sobre un ID
 * que nadie habia comprobado.
 */
describe('FI-R3 - la busqueda de usuario consulta al servicio', () => {
  const USUARIO = {
    id: 15,
    apodo: 'nyx_valiente',
    email: 'nyx@example.com',
    estado: 'ACTIVO',
    rolNombre: 'MODERADOR',
    nombres: 'Nyx',
    apellidos: 'Valiente',
    avatar: '/avatares/15.png',
    preferencias: 'tema-oscuro',
  };

  function domDeBusqueda() {
    document.body.innerHTML = `
      <form id="form-buscar-usuario">
        <input id="usuario-id" type="search" />
        <button type="submit" id="btn-buscar">Buscar</button>
      </form>
      <div id="mensaje-busqueda" hidden></div>
      <div id="resultados-busqueda" hidden></div>
      <a id="enlace-ficha-usuario" hidden></a>
      <div id="panel-usuario" hidden>
        <dd id="usuario-id-mostrado">-</dd>
        <dd id="usuario-apodo-mostrado">-</dd>
        <dd id="usuario-email-mostrado">-</dd>
        <dd id="usuario-rol-mostrado">-</dd>
        <dd id="usuario-estado-mostrado">-</dd>
        <form id="formulario-perfil-admin">
          <input id="nombres" /><input id="apellidos" /><input id="apodo" />
          <input id="preferencias" /><input id="estado" />
          <input id="suspendido-hasta" />
          <select id="nuevo-rol">
            <option value="JUGADOR">JUGADOR</option>
            <option value="MODERADOR">MODERADOR</option>
            <option value="ADMINISTRADOR">ADMINISTRADOR</option>
            <option value="SUPER_ADMINISTRADOR">SUPER_ADMINISTRADOR</option>
          </select>
        </form>
        <div id="mensaje-perfil" hidden></div>
        <div id="mensaje-cambio-rol" hidden></div>
      </div>
    `;
  }

  async function buscar(id) {
    document.getElementById('usuario-id').value = String(id);
    document
      .getElementById('form-buscar-usuario')
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    // Dos vueltas: la respuesta y su .json().
    for (let i = 0; i < 6; i++) {
      await Promise.resolve();
    }
  }

  const mensaje = () => document.getElementById('mensaje-busqueda').textContent;
  const panelVisible = () => document.getElementById('panel-usuario').hidden === false;

  beforeEach(() => {
    sessionStorage.clear();
    domDeBusqueda();
    configurarEventos();
    globalThis.fetch = jest.fn();
  });

  test('pide el usuario al endpoint administrativo que si existe', async () => {
    globalThis.fetch.mockResolvedValue({ ok: true, status: 200, json: async () => USUARIO });

    await buscar(15);

    expect(globalThis.fetch).toHaveBeenCalledTimes(1);
    expect(globalThis.fetch.mock.calls[0][0]).toBe('/api/v1/admin/usuarios/15');
  });

  test('con el usuario encontrado pinta sus datos reales, no guiones', async () => {
    globalThis.fetch.mockResolvedValue({ ok: true, status: 200, json: async () => USUARIO });

    await buscar(15);

    expect(panelVisible()).toBe(true);
    expect(document.getElementById('usuario-apodo-mostrado').textContent).toBe('nyx_valiente');
    expect(document.getElementById('usuario-email-mostrado').textContent).toBe('nyx@example.com');
    // SA-3 — el rol y el estado como los dice una persona, el estado con su
    // distintivo: nunca la constante del servidor.
    expect(document.getElementById('usuario-rol-mostrado').textContent).toBe('Moderador');
    const estado = document.getElementById('usuario-estado-mostrado');
    expect(estado.textContent.trim()).toBe('Activa');
    expect(estado.querySelector('.estado-admin[data-estado="ACTIVO"]')).not.toBeNull();
    expect(document.getElementById('nombres').value).toBe('Nyx');
    expect(document.getElementById('apellidos').value).toBe('Valiente');
  });

  test('el selector de rol arranca en el rol que el usuario ya tiene', async () => {
    // Dejarlo en el primero de la lista invitaba a degradar a un moderador de
    // un solo clic sin haberlo pedido.
    globalThis.fetch.mockResolvedValue({ ok: true, status: 200, json: async () => USUARIO });

    await buscar(15);

    expect(document.getElementById('nuevo-rol').value).toBe('MODERADOR');
  });

  test('un usuario inexistente no abre el panel de acciones', async () => {
    globalThis.fetch.mockResolvedValue({
      ok: false,
      status: 404,
      clone: () => ({ json: async () => 'no existe', text: async () => 'no existe' }),
    });

    await buscar(9999);

    expect(panelVisible()).toBe(false);
    expect(mensaje()).toContain('No existe');
    expect(mensaje()).toContain('9999');
  });

  test('un 403 habla de permisos, no de un usuario que falta', async () => {
    globalThis.fetch.mockResolvedValue({
      ok: false,
      status: 403,
      clone: () => ({ json: async () => ({ detail: 'Acceso denegado' }) }),
    });

    await buscar(15);

    expect(panelVisible()).toBe(false);
    expect(mensaje()).toMatch(/permiso/i);
    expect(mensaje()).not.toMatch(/no existe/i);
  });

  test('un 401 manda a iniciar sesion, y no dice que el usuario no existe', async () => {
    globalThis.fetch.mockResolvedValue({
      ok: false,
      status: 401,
      clone: () => ({ json: async () => ({}) }),
    });

    await buscar(15);

    expect(panelVisible()).toBe(false);
    expect(mensaje()).toMatch(/sesi[oó]n/i);
    expect(mensaje()).not.toMatch(/no existe/i);
  });

  test('un servicio caido no se disfraza de usuario inexistente', async () => {
    globalThis.fetch.mockRejectedValue(new Error('sin red'));

    await buscar(15);

    expect(panelVisible()).toBe(false);
    expect(mensaje()).toMatch(/no responde/i);
    expect(mensaje()).not.toMatch(/no existe/i);
  });

  test('una busqueda fallida cierra el panel que una anterior habia abierto', async () => {
    // Es el caso que dejaba las acciones apuntando al usuario equivocado.
    globalThis.fetch.mockResolvedValue({ ok: true, status: 200, json: async () => USUARIO });
    await buscar(15);
    expect(panelVisible()).toBe(true);

    globalThis.fetch.mockResolvedValue({
      ok: false,
      status: 404,
      clone: () => ({ json: async () => 'no existe', text: async () => 'no existe' }),
    });
    await buscar(9999);

    expect(panelVisible()).toBe(false);
  });

  test('un ID no valido no llega a molestar al servicio', async () => {
    for (const malo of [0, -3]) {
      await buscar(malo);

      expect(globalThis.fetch).not.toHaveBeenCalled();
      expect(mensaje()).toMatch(/v[aá]lido/i);
      expect(panelVisible()).toBe(false);
    }
  });

  test('RFINAL-06 — el ID que se muestra es el de la cuenta buscada', async () => {
    globalThis.fetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ ...USUARIO, id: 99 }),
    });

    await buscar(15);

    expect(document.getElementById('usuario-id-mostrado').textContent).toBe('15');
  });

  /**
   * RFINAL-06 — «hoy solo se edita tras buscar por ID». Desde el directorio de
   * Control integral cada fila trae `?usuario=<clave>`: la ficha se abre sola,
   * sin copiar ni pegar nada.
   */
  test('RFINAL-06 — con ?usuario= en la dirección abre la ficha de esa cuenta', async () => {
    globalThis.fetch.mockResolvedValue({ ok: true, status: 200, json: async () => USUARIO });

    const abierta = await abrirFichaDesdeLaDireccion('?usuario=15');
    for (let i = 0; i < 6; i++) {
      await Promise.resolve();
    }

    expect(abierta).toBe(true);
    expect(globalThis.fetch.mock.calls[0][0]).toBe('/api/v1/admin/usuarios/15');
    expect(document.getElementById('usuario-id').value).toBe('15');
    expect(panelVisible()).toBe(true);
    expect(document.getElementById('usuario-apodo-mostrado').textContent).toBe('nyx_valiente');
  });

  test('RFINAL-06 — un ?usuario= que no es una clave de cuenta no consulta nada', async () => {
    for (const busqueda of [
      '',
      '?usuario=',
      '?usuario=abc',
      '?usuario=0',
      '?usuario=-3',
      '?usuario=1e3',
    ]) {
      expect(await abrirFichaDesdeLaDireccion(busqueda)).toBe(false);
    }
    expect(globalThis.fetch).not.toHaveBeenCalled();
    expect(panelVisible()).toBe(false);
  });

  test('ya no queda en pantalla la promesa de conectar el backend algun dia', async () => {
    globalThis.fetch.mockResolvedValue({ ok: true, status: 200, json: async () => USUARIO });

    await buscar(15);

    expect(document.body.textContent).not.toMatch(/quedar[aá] conectada/i);
    expect(document.body.textContent).not.toMatch(/cuando el backend exponga/i);
  });
});

describe('SA-3 — buscar por apodo, correo, nombre o ID y luego elegir (§7.3.4, punto 16)', () => {
  const ENTRADAS = {
    contenido: [
      {
        id: 15,
        uid: '11111111-2222-3333-4444-555555555555',
        apodo: 'nyx_valiente',
        email: 'nyx@example.com',
        estado: 'ACTIVO',
        rol: 'MODERADOR',
        bloqueada: false,
        sancionesRegistradas: 0,
      },
      {
        id: 22,
        uid: '22222222-2222-3333-4444-555555555555',
        apodo: 'nyxie',
        email: 'otra@example.com',
        estado: 'SUSPENDIDO',
        rol: 'JUGADOR',
        bloqueada: false,
        sancionesRegistradas: 2,
      },
    ],
    pagina: 0,
    tamano: 10,
    total: 2,
    totalPaginas: 1,
  };
  const USUARIO = {
    id: 15,
    apodo: 'nyx_valiente',
    email: 'nyx@example.com',
    estado: 'ACTIVO',
    rolNombre: 'MODERADOR',
  };

  function dom() {
    document.body.innerHTML = `
      <form id="form-buscar-usuario">
        <input id="usuario-id" type="search" />
        <button type="submit" id="btn-buscar">Buscar</button>
      </form>
      <div id="mensaje-busqueda" hidden></div>
      <div id="resultados-busqueda" hidden></div>
      <a id="enlace-ficha-usuario" hidden></a>
      <div id="panel-usuario" hidden>
        <dd id="usuario-id-mostrado">-</dd>
        <dd id="usuario-apodo-mostrado">-</dd>
        <dd id="usuario-email-mostrado">-</dd>
        <dd id="usuario-rol-mostrado">-</dd>
        <dd id="usuario-estado-mostrado">-</dd>
      </div>
    `;
  }

  async function asentar() {
    for (let i = 0; i < 8; i++) {
      await Promise.resolve();
    }
  }

  async function buscar(texto) {
    document.getElementById('usuario-id').value = texto;
    document
      .getElementById('form-buscar-usuario')
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await asentar();
  }

  const resultados = () => document.getElementById('resultados-busqueda');
  const mensaje = () => document.getElementById('mensaje-busqueda').textContent;
  const panelVisible = () => document.getElementById('panel-usuario').hidden === false;

  beforeEach(() => {
    sessionStorage.clear();
    dom();
    configurarEventos();
    globalThis.fetch = jest.fn();
  });

  test('qué se escribió decide cómo se busca', () => {
    expect(clasificarBusqueda('15')).toEqual({ tipo: 'id', valor: '15' });
    expect(clasificarBusqueda('  15 ')).toEqual({ tipo: 'id', valor: '15' });
    expect(clasificarBusqueda('0').tipo).toBe('invalido');
    expect(clasificarBusqueda('-4').tipo).toBe('invalido');
    expect(clasificarBusqueda('').tipo).toBe('vacio');
    expect(clasificarBusqueda('n').tipo).toBe('corto');
    expect(clasificarBusqueda('nyx@example.com')).toEqual({
      tipo: 'texto',
      valor: 'nyx@example.com',
    });
    expect(clasificarBusqueda('Ana Rueda').tipo).toBe('texto');
    expect(clasificarBusqueda('11111111-2222-3333-4444-555555555555').tipo).toBe('texto');
  });

  test('un apodo busca en el directorio y enseña las cuentas sin abrir ninguna', async () => {
    globalThis.fetch.mockResolvedValue({ ok: true, status: 200, json: async () => ENTRADAS });

    await buscar('nyx');

    expect(globalThis.fetch).toHaveBeenCalledTimes(1);
    expect(globalThis.fetch.mock.calls[0][0]).toBe(
      `/api/v1/admin/jugadores?buscar=nyx&page=0&size=${RESULTADOS_POR_BUSQUEDA}`,
    );
    expect(resultados().hidden).toBe(false);
    expect(panelVisible()).toBe(false);
    const filas = [...resultados().querySelectorAll('tbody tr')].map((tr) => tr.textContent);
    expect(filas).toHaveLength(2);
    expect(filas[0]).toContain('nyx_valiente');
    expect(filas[0]).toContain('nyx@example.com');
    expect(filas[0]).toContain('Moderador');
    expect(filas[0]).toContain('Activa');
    expect(filas[1]).toContain('Suspendida');
    expect(resultados().textContent).not.toMatch(/MODERADOR|SUSPENDIDO|[0-9a-f]{8}-[0-9a-f]{4}/);
    expect(resultados().querySelector('.tabla-admin__resumen').textContent).toBe(
      '2 cuentas coinciden',
    );
  });

  test('el correo o el nombre se mandan codificados', async () => {
    globalThis.fetch.mockResolvedValue({ ok: true, status: 200, json: async () => ENTRADAS });

    await buscar('Ana Pérez');

    expect(globalThis.fetch.mock.calls[0][0]).toBe(
      `/api/v1/admin/jugadores?buscar=Ana%20P%C3%A9rez&page=0&size=${RESULTADOS_POR_BUSQUEDA}`,
    );
  });

  test('elegir una cuenta la abre por su clave, y su ficha completa queda a un clic', async () => {
    globalThis.fetch
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => ENTRADAS })
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => USUARIO });

    await buscar('nyx');
    const elegir = resultados().querySelector('button[data-accion="seleccionar-cuenta"]');
    expect(elegir.getAttribute('aria-label')).toBe('Gestionar la cuenta de nyx_valiente');
    elegir.click();
    await asentar();

    expect(globalThis.fetch.mock.calls[1][0]).toBe('/api/v1/admin/usuarios/15');
    expect(panelVisible()).toBe(true);
    expect(document.getElementById('usuario-apodo-mostrado').textContent).toBe('nyx_valiente');
    const ficha = document.getElementById('enlace-ficha-usuario');
    expect(ficha.hidden).toBe(false);
    expect(new URL(ficha.href).pathname).toMatch(/moderacion-sanciones\/ficha-usuario\.html$/);
    expect(new URL(ficha.href).searchParams.get('usuario')).toBe('15');
    const enlace = resultados().querySelector('a[data-accion="ver-ficha"]');
    expect(new URL(enlace.href).searchParams.get('usuario')).toBe('15');
  });

  test('sin coincidencias lo dice y no abre nada', async () => {
    globalThis.fetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ contenido: [], pagina: 0, tamano: 10, total: 0, totalPaginas: 0 }),
    });

    await buscar('nadie_asi');

    expect(mensaje()).toBe('Ninguna cuenta coincide con «nadie_asi».');
    expect(resultados().hidden).toBe(true);
    expect(panelVisible()).toBe(false);
  });

  test('si hay más de las que se enseñan, pide afinar', async () => {
    globalThis.fetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ ...ENTRADAS, total: 37, totalPaginas: 4 }),
    });

    await buscar('ny');

    expect(resultados().querySelector('.tabla-admin__resumen').textContent).toContain(
      'Las 2 primeras de 37 cuentas',
    );
  });

  test('un 403 del directorio habla de permisos, no de que no haya nadie', async () => {
    globalThis.fetch.mockResolvedValue({
      ok: false,
      status: 403,
      clone: () => ({ json: async () => ({ detail: 'Acceso denegado' }) }),
    });

    await buscar('nyx');

    expect(mensaje()).toMatch(/permiso/i);
    expect(mensaje()).not.toMatch(/no existe|ninguna cuenta/i);
  });

  test('el directorio caído no se disfraza de búsqueda sin resultados', async () => {
    globalThis.fetch.mockRejectedValue(new Error('sin red'));

    await buscar('nyx');

    expect(mensaje()).toMatch(/no responde/i);
    expect(mensaje()).not.toMatch(/ninguna cuenta/i);
  });

  test('una letra sola no molesta al servicio', async () => {
    await buscar('n');

    expect(globalThis.fetch).not.toHaveBeenCalled();
    expect(mensaje()).toContain('al menos dos letras');
  });

  test('una búsqueda nueva cierra la cuenta abierta antes', async () => {
    globalThis.fetch.mockResolvedValueOnce({ ok: true, status: 200, json: async () => USUARIO });
    await buscar('15');
    expect(panelVisible()).toBe(true);

    globalThis.fetch.mockResolvedValueOnce({ ok: true, status: 200, json: async () => ENTRADAS });
    await buscar('nyx');

    expect(panelVisible()).toBe(false);
    expect(document.getElementById('enlace-ficha-usuario').hidden).toBe(true);
  });
});

describe('RFINAL-06 — la clave de cuenta que trae la dirección', () => {
  test('solo un entero positivo es una clave de cuenta', () => {
    expect(usuarioDeLaDireccion('?usuario=15')).toBe('15');
    expect(usuarioDeLaDireccion('?usuario= 42 ')).toBe('42');
    expect(usuarioDeLaDireccion('?otra=1&usuario=7')).toBe('7');
    for (const busqueda of [
      '',
      '?usuario=',
      '?usuario=abc',
      '?usuario=0',
      '?usuario=-3',
      '?usuario=1.5',
    ]) {
      expect(usuarioDeLaDireccion(busqueda)).toBeNull();
    }
    // Un uid (UUID) no es la clave que espera la gestión.
    expect(usuarioDeLaDireccion('?usuario=11111111-2222-3333-4444-555555555555')).toBeNull();
  });
});

describe('UXC-7 — acciones sobre la cuenta con los diálogos del kit, no con window.confirm', () => {
  const USUARIO = { id: 15, apodo: 'nyx_valiente', estado: 'ACTIVO', rolNombre: 'JUGADOR' };

  function dom() {
    document.body.innerHTML = `
      <form id="form-buscar-usuario">
        <input id="usuario-id" type="number" />
        <button type="submit" id="btn-buscar">Buscar</button>
      </form>
      <div id="mensaje-busqueda" hidden></div>
      <div id="panel-usuario" hidden>
        <dd id="usuario-estado-mostrado">-</dd>
        <form id="formulario-perfil-admin">
          <input id="estado" /><input id="suspendido-hasta" />
          <textarea id="motivo-sancion"></textarea>
          <select id="nuevo-rol"><option value="JUGADOR">JUGADOR</option></select>
        </form>
        <button type="button" id="btn-suspender">Suspender</button>
        <button type="button" id="btn-reactivar">Reactivar</button>
        <button type="button" id="btn-banear">Banear</button>
        <div id="mensaje-perfil" hidden></div>
        <div id="mensaje-cambio-rol" hidden></div>
      </div>
    `;
  }

  const esperar = async () => {
    for (let i = 0; i < 8; i++) {
      await Promise.resolve();
    }
  };

  async function elegirUsuario() {
    globalThis.fetch.mockResolvedValueOnce({ ok: true, status: 200, json: async () => USUARIO });
    document.getElementById('usuario-id').value = '15';
    document
      .getElementById('form-buscar-usuario')
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await esperar();
  }

  const dialogo = () => document.querySelector('[role="dialog"]');

  beforeEach(async () => {
    sessionStorage.clear();
    setCurrentRole('SUPER_ADMINISTRADOR');
    setPermissionMatrix({
      SUPER_ADMINISTRADOR: {
        GESTIONAR_CUENTAS: 'GRANTED',
        SUSPENDER_USUARIOS: 'GRANTED',
        BANEAR_DEFINITIVAMENTE: 'GRANTED',
      },
    });
    dom();
    configurarEventos();
    globalThis.fetch = jest.fn();
    window.confirm = jest.fn(() => {
      throw new Error('window.confirm no se usa');
    });
    await elegirUsuario();
  });

  /** Escribe en un campo del diálogo como lo haría una persona. */
  function escribir(campo, valor) {
    campo.value = valor;
    campo.dispatchEvent(new Event('input', { bubbles: true }));
  }

  test('suspender pide el motivo en el diálogo, con el apodo y la fecha, y lo manda con la fecha fin', async () => {
    document.getElementById('suspendido-hasta').value = '2026-10-01T10:00';
    document.getElementById('btn-suspender').click();
    await esperar();

    expect(dialogo().querySelector('h2').textContent).toBe('¿Suspender a nyx_valiente?');
    expect(dialogo().textContent).toMatch(/No podrá entrar al juego hasta el 1 de octubre/);
    const confirmarBoton = dialogo().querySelector('[data-accion="confirmar"]');
    // 7.3.9 — sin motivo no se puede confirmar.
    expect(confirmarBoton.disabled).toBe(true);
    escribir(dialogo().querySelector('textarea'), 'Lenguaje ofensivo reiterado');
    expect(confirmarBoton.disabled).toBe(false);
    globalThis.fetch.mockResolvedValueOnce({ ok: true, status: 204 });
    confirmarBoton.click();
    await esperar();

    const [ruta, opciones] = globalThis.fetch.mock.calls.at(-1);
    expect(ruta).toBe('/api/v1/admin/usuarios/15/suspender');
    expect(opciones.method).toBe('PUT');
    expect(JSON.parse(opciones.body)).toEqual({
      suspendidoHasta: '2026-10-01T10:00',
      motivo: 'Lenguaje ofensivo reiterado',
    });
    expect(window.confirm).not.toHaveBeenCalled();
  });

  test('cancelar (o cerrar) no toca la cuenta', async () => {
    const llamadas = globalThis.fetch.mock.calls.length;
    document.getElementById('btn-reactivar').click();
    await esperar();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await esperar();
    document.getElementById('suspendido-hasta').value = '2026-10-01T10:00';
    document.getElementById('btn-suspender').click();
    await esperar();
    dialogo().querySelector('[data-accion="cancelar"]').click();
    await esperar();
    expect(globalThis.fetch.mock.calls.length).toBe(llamadas);
  });

  test('banear es crítico: motivo obligatorio y, además, escribir el apodo de la cuenta', async () => {
    document.getElementById('btn-banear').click();
    await esperar();

    const caja = dialogo();
    expect(caja.querySelector('h2').textContent).toBe('Banear definitivamente a nyx_valiente');
    expect(caja.querySelector('.confirmacion-critica__consecuencias').textContent).toContain(
      'No podrá volver a entrar con esta cuenta.',
    );
    const motivo = caja.querySelector('textarea');
    const palabra = caja.querySelector('input');
    const boton = caja.querySelector('[data-accion="confirmar"]');
    expect(document.activeElement).toBe(motivo);
    expect(boton.disabled).toBe(true);

    escribir(palabra, 'nyx_valiente');
    expect(boton.disabled).toBe(true);
    escribir(motivo, 'corto');
    expect(boton.disabled).toBe(true);
    escribir(motivo, 'Fraude comprobado en subastas');
    escribir(palabra, 'nyx');
    expect(boton.disabled).toBe(true);
    escribir(palabra, 'nyx_valiente');
    expect(boton.disabled).toBe(false);
    globalThis.fetch.mockResolvedValueOnce({ ok: true, status: 204 });
    boton.click();
    await esperar();

    const [ruta, opciones] = globalThis.fetch.mock.calls.at(-1);
    expect(ruta).toBe('/api/v1/admin/usuarios/15/banear');
    expect(JSON.parse(opciones.body)).toEqual({ motivo: 'Fraude comprobado en subastas' });
  });

  test('UXC-9 — la causal escrita en el formulario llega escrita al diálogo y viaja sin espacios', async () => {
    document.getElementById('suspendido-hasta').value = '2026-10-01T10:00';
    document.getElementById('motivo-sancion').value = '  Lenguaje ofensivo en el chat  ';
    document.getElementById('btn-suspender').click();
    await esperar();

    expect(dialogo().querySelector('textarea').value).toContain('Lenguaje ofensivo en el chat');
    globalThis.fetch.mockResolvedValueOnce({ ok: true, status: 204 });
    dialogo().querySelector('[data-accion="confirmar"]').click();
    await esperar();

    expect(JSON.parse(globalThis.fetch.mock.calls.at(-1)[1].body)).toEqual({
      suspendidoHasta: '2026-10-01T10:00',
      motivo: 'Lenguaje ofensivo en el chat',
    });
  });

  /**
   * RFINAL-06 — defecto conocido «id de perfil usado como id de usuario»: la
   * ficha (`GET /admin/usuarios/{usuarioId}`) devolvía en `id` la clave del
   * PERFIL, y el panel la usaba para suspender, banear, reactivar,
   * restablecer y cambiar el rol, rutas que esperan la de la CUENTA. Las
   * acciones van ahora con la clave por la que se abrió la ficha, la que el
   * servidor acaba de resolver como cuenta.
   */
  test('RFINAL-06 — las acciones van a la cuenta abierta, no al id que trae la ficha', async () => {
    globalThis.fetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({ ...USUARIO, id: 99 }),
    });
    document.getElementById('usuario-id').value = '15';
    document
      .getElementById('form-buscar-usuario')
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await esperar();

    document.getElementById('suspendido-hasta').value = '2026-10-01T10:00';
    document.getElementById('motivo-sancion').value = 'Spam reiterado en el chat';
    document.getElementById('btn-suspender').click();
    await esperar();
    globalThis.fetch.mockResolvedValueOnce({ ok: true, status: 204 });
    dialogo().querySelector('[data-accion="confirmar"]').click();
    await esperar();

    expect(globalThis.fetch.mock.calls.at(-1)[0]).toBe('/api/v1/admin/usuarios/15/suspender');
  });

  test('7.3.9 — restablecer el segundo factor pide motivo y lo manda; sin permiso ni pregunta', async () => {
    const boton = document.createElement('button');
    boton.id = 'btn-restablecer-segundo-factor';
    document.getElementById('panel-usuario').append(boton);
    setPermissionMatrix({
      SUPER_ADMINISTRADOR: {
        GESTIONAR_CUENTAS: 'GRANTED',
        RESTABLECER_SEGUNDO_FACTOR: 'GRANTED',
      },
    });
    configurarEventos();

    boton.click();
    await esperar();
    const caja = dialogo();
    expect(caja.querySelector('h2').textContent).toContain('verificación en dos pasos');
    escribir(caja.querySelector('textarea'), 'Perdió el teléfono y los códigos');
    globalThis.fetch.mockResolvedValueOnce({ ok: true, status: 204 });
    caja.querySelector('[data-accion="confirmar"]').click();
    await esperar();

    const [ruta, opciones] = globalThis.fetch.mock.calls.at(-1);
    expect(ruta).toBe('/api/v1/admin/usuarios/15/segundo-factor/restablecimiento');
    expect(opciones.method).toBe('POST');
    expect(JSON.parse(opciones.body)).toEqual({ motivo: 'Perdió el teléfono y los códigos' });

    setCurrentRole('ADMINISTRADOR');
    const llamadas = globalThis.fetch.mock.calls.length;
    boton.click();
    await esperar();
    expect(dialogo()).toBeNull();
    expect(globalThis.fetch.mock.calls.length).toBe(llamadas);
  });
});
