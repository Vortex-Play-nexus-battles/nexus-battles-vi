import { jest } from '@jest/globals';
import { setCurrentRole, setPermissionMatrix } from './directives/has-permission.directive.js';
import { cargarMatrizYVerificarAcceso, configurarEventos } from './gestion-usuarios.js';

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
        <input id="usuario-id" type="number" />
        <button type="submit" id="btn-buscar">Buscar</button>
      </form>
      <div id="mensaje-busqueda" hidden></div>
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
    expect(document.getElementById('usuario-rol-mostrado').textContent).toBe('MODERADOR');
    expect(document.getElementById('usuario-estado-mostrado').textContent).toBe('ACTIVO');
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
    await buscar(0);

    expect(globalThis.fetch).not.toHaveBeenCalled();
    expect(mensaje()).toMatch(/v[aá]lido/i);
    expect(panelVisible()).toBe(false);
  });

  test('ya no queda en pantalla la promesa de conectar el backend algun dia', async () => {
    globalThis.fetch.mockResolvedValue({ ok: true, status: 200, json: async () => USUARIO });

    await buscar(15);

    expect(document.body.textContent).not.toMatch(/quedar[aá] conectada/i);
    expect(document.body.textContent).not.toMatch(/cuando el backend exponga/i);
  });
});
