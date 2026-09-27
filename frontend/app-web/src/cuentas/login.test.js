/**
 * HU-RBAC-005 - Separación de credenciales por ambiente.
 *
 * Verifica que el frontend presente un rechazo explícito cuando
 * las credenciales no son válidas para el ambiente actual.
 */

describe('Login - aislamiento de credenciales por ambiente', () => {
  beforeAll(() => {
    document.body.innerHTML = `
      <form id="formLogin">
        <input id="email" name="email" type="email">
        <input id="password" name="password" type="password">
        <button id="botonEnviar" type="submit">Iniciar sesión</button>
      </form>

      <p id="estadoLogin" hidden></p>
      <p id="avisoDispositivo" hidden></p>
    `;
  });

  test('muestra un rechazo explícito cuando las credenciales no pertenecen al ambiente', async () => {
    const { mensajeDeError } = await import('./login.js');

    const mensaje = mensajeDeError(401, 'Correo o contraseña incorrectos.');

    expect(mensaje).toContain('Acceso rechazado');
    expect(mensaje).toContain('estas credenciales no están registradas en este ambiente');
  });

  test('no expone información que permita saber si el correo existe', async () => {
    const { mensajeDeError } = await import('./login.js');

    const mensaje = mensajeDeError(401);

    expect(mensaje).toContain('correo o la contraseña son incorrectos');
    expect(mensaje).not.toContain('usuario inexistente');
    expect(mensaje).not.toContain('correo registrado');
  });

  test('mantiene el mensaje específico enviado por el backend para un 403', async () => {
    const { mensajeDeError } = await import('./login.js');

    const mensaje = mensajeDeError(403, 'Esta cuenta ha sido suspendida.');

    expect(mensaje).toBe('Esta cuenta ha sido suspendida.');
  });

  test('mantiene el mensaje de bloqueo temporal para un 423', async () => {
    const { mensajeDeError } = await import('./login.js');

    const mensaje = mensajeDeError(423, 'Cuenta bloqueada temporalmente.');

    expect(mensaje).toBe('Cuenta bloqueada temporalmente.');
  });
});

describe('Login - por qué se llega aquí (R17)', () => {
  test('cada motivo tiene su aviso, y uno desconocido no pinta nada', async () => {
    const { avisoDelMotivo } = await import('./login.js');

    expect(avisoDelMotivo('caducada')).toEqual({
      tipo: 'advertencia',
      texto: 'Tu sesión terminó. Vuelve a entrar y te llevamos a donde estabas.',
    });
    expect(avisoDelMotivo('cerrada').texto).toContain('Cerraste sesión');
    expect(avisoDelMotivo('registrada').tipo).toBe('exito');
    expect(avisoDelMotivo('<script>')).toBeNull();
    expect(avisoDelMotivo(null)).toBeNull();
  });

  test('B1 — tras verificar el correo y tras restablecer la contraseña', async () => {
    const { avisoDelMotivo } = await import('./login.js');

    expect(avisoDelMotivo('verificada')).toEqual({
      tipo: 'exito',
      texto: 'Tu correo quedó verificado. Entra con tu contraseña y preparamos tu cuenta.',
    });
    expect(avisoDelMotivo('restablecida').tipo).toBe('exito');
    expect(avisoDelMotivo('restablecida').texto).toContain('contraseña nueva');
  });
});

describe('Login - el rechazo se explica por su type (B1, identidad 2.0.0)', () => {
  const ERRORES = 'https://nexusbattles.upb.edu.co/errors/';
  const problema = (tipo, extra = {}) => ({
    type: `${ERRORES}${tipo}`,
    title: 'Título que no se usa para decidir',
    status: 403,
    ...extra,
  });

  test('cuenta-no-verificada: falta confirmar el correo', async () => {
    const { rechazoDelLogin } = await import('./login.js');

    const rechazo = rechazoDelLogin(403, problema('cuenta-no-verificada'));

    expect(rechazo.caso).toBe('no-verificada');
    expect(rechazo.titulo).toBe('Falta confirmar tu correo');
    expect(rechazo.detalle).toContain('pide uno nuevo');
    expect(rechazo.tono).toBe('advertencia');
  });

  test('cuenta-suspendida: hasta cuándo, con la fecha que manda el servidor', async () => {
    const { rechazoDelLogin } = await import('./login.js');
    const { fechaHora } = await import('../comun/ui/formato.js');
    const hasta = '2026-10-02T15:00:00Z';

    const rechazo = rechazoDelLogin(403, problema('cuenta-suspendida', { suspendidoHasta: hasta }));

    expect(rechazo.caso).toBe('suspendida');
    expect(rechazo.suspendidoHasta).toBe(hasta);
    expect(rechazo.detalle).toContain(fechaHora(hasta));
    expect(rechazo.detalle).toContain('se conservan');
  });

  test('cuenta-suspendida sin fecha legible: sin contador y sin «Invalid Date»', async () => {
    const { rechazoDelLogin } = await import('./login.js');

    const rechazo = rechazoDelLogin(
      403,
      problema('cuenta-suspendida', { suspendidoHasta: 'mañana' }),
    );

    expect(rechazo.suspendidoHasta).toBeNull();
    expect(rechazo.detalle).not.toMatch(/Invalid|NaN|—/);
  });

  test('cuenta-baneada: aviso formal, con el detalle del servidor si lo hay', async () => {
    const { rechazoDelLogin } = await import('./login.js');

    const sinDetalle = rechazoDelLogin(403, problema('cuenta-baneada'));
    expect(sinDetalle.caso).toBe('baneada');
    expect(sinDetalle.titulo).toBe('Esta cuenta está inhabilitada de forma definitiva');
    expect(sinDetalle.detalle).toContain('ya no puede iniciar sesión');

    const conDetalle = rechazoDelLogin(
      403,
      problema('cuenta-baneada', { detail: 'Motivo registrado por moderación.' }),
    );
    expect(conDetalle.detalle).toBe('Motivo registrado por moderación.');
  });

  test('cuenta-bloqueada (423) y credenciales-invalidas (401)', async () => {
    const { rechazoDelLogin } = await import('./login.js');

    expect(rechazoDelLogin(423, problema('cuenta-bloqueada', { status: 423 })).caso).toBe(
      'bloqueada',
    );
    const credenciales = rechazoDelLogin(401, problema('credenciales-invalidas', { status: 401 }));
    expect(credenciales.caso).toBe('credenciales');
    // El mismo mensaje genérico de siempre: no dice si falló el correo o la clave.
    expect(credenciales.titulo).toContain('correo o la contraseña son incorrectos');
  });

  test('sin type (servidor en texto plano) se decide por el estado, como antes', async () => {
    const { rechazoDelLogin } = await import('./login.js');

    const rechazo = rechazoDelLogin(403, 'Esta cuenta ha sido suspendida.');
    expect(rechazo.caso).toBe('otro');
    expect(rechazo.titulo).toBe('Esta cuenta ha sido suspendida.');
    expect(rechazoDelLogin(500, '').tono).toBe('error');
  });
});

describe('Login - cómo se pinta el rechazo (B1)', () => {
  test('título y detalle con nodos, con el rol que toca', async () => {
    const { pintarRechazo } = await import('./login.js');
    const zona = document.createElement('div');
    zona.hidden = true;

    pintarRechazo(zona, {
      caso: 'baneada',
      tono: 'advertencia',
      titulo: 'Esta cuenta está inhabilitada de forma definitiva',
      detalle: '<img src=x onerror=alert(1)>',
    });

    expect(zona.hidden).toBe(false);
    expect(zona.querySelector('.aviso--advertencia').getAttribute('role')).toBe('alert');
    expect(zona.querySelector('.aviso__titulo').textContent).toBe(
      'Esta cuenta está inhabilitada de forma definitiva',
    );
    // El detalle puede venir del servidor: entra como texto, nunca como marcado.
    expect(zona.querySelector('img')).toBeNull();
    expect(zona.querySelector('.aviso__detalle').textContent).toBe('<img src=x onerror=alert(1)>');
  });

  test('sin anunciar cuando el foco va a ir a la propia zona', async () => {
    const { pintarRechazo } = await import('./login.js');
    const zona = document.createElement('div');
    pintarRechazo(
      zona,
      { caso: 'inactiva', tono: 'advertencia', titulo: 'x', detalle: null },
      { anunciar: false },
    );
    expect(zona.querySelector('.aviso').hasAttribute('role')).toBe(false);
  });

  test('una suspensión lleva su contador vivo, y se puede detener', async () => {
    const { pintarRechazo } = await import('./login.js');
    const zona = document.createElement('div');
    const hasta = new Date(Date.now() + 2 * 3_600_000).toISOString();

    const detener = pintarRechazo(zona, {
      caso: 'suspendida',
      tono: 'advertencia',
      titulo: 'Tu cuenta está suspendida',
      detalle: 'No puedes entrar hasta…',
      suspendidoHasta: hasta,
    });

    const contador = zona.querySelector('time.cuenta-atras');
    expect(contador).not.toBeNull();
    expect(contador.getAttribute('datetime')).toBe(hasta);
    expect(contador.getAttribute('aria-label')).toMatch(/^La suspensión termina en /);
    expect(zona.querySelector('[data-zona="tiempo-restante"]').textContent).toContain(
      'Tiempo restante',
    );
    expect(typeof detener).toBe('function');
    detener();
  });

  test('las salidas de una cuenta sin verificar van dentro del aviso', async () => {
    const { pintarRechazo } = await import('./login.js');
    const zona = document.createElement('div');
    const acciones = document.createElement('div');
    acciones.append(document.createElement('a'));

    pintarRechazo(
      zona,
      {
        caso: 'no-verificada',
        tono: 'advertencia',
        titulo: 'Falta confirmar tu correo',
        detalle: 'x',
      },
      { acciones },
    );

    expect(zona.querySelector('.aviso__cuerpo').lastElementChild).toBe(acciones);
  });
});

describe('Login - identidad de la sesion (#426, ADR-002)', () => {
  const token = (claims) =>
    `x.${btoa(JSON.stringify(claims)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')}.y`;

  test('guarda el uid del token y no la clave primaria', async () => {
    const { identificadorDeSesion } = await import('./login.js');
    expect(
      identificadorDeSesion(token({ uid: '11111111-1111-1111-1111-111111111111', sub: 'lyra' }), 7),
    ).toBe('11111111-1111-1111-1111-111111111111');
  });

  test('sin uid legible cae a la clave primaria, sin romper', async () => {
    const { identificadorDeSesion } = await import('./login.js');
    expect(identificadorDeSesion(token({ sub: 'lyra' }), 7)).toBe('7');
    expect(identificadorDeSesion('no-es-un-jwt', 7)).toBe('7');
    expect(identificadorDeSesion(undefined, undefined)).toBe('');
  });
});

describe('Login - sesión que ofrece otra pestaña (R18)', () => {
  /** Almacén de sesión de mentira: lo justo para las dependencias de la función. */
  function sesionFalsa({ yaHaySesion = false, quedaAutenticada = true } = {}) {
    const estado = { guardada: null, olvidada: false };
    return {
      estado,
      leer: () => ({ autenticado: yaHaySesion || (estado.guardada !== null && quedaAutenticada) }),
      guardar: (sesion) => {
        estado.guardada = sesion;
      },
      olvidar: () => {
        estado.olvidada = true;
        estado.guardada = null;
      },
    };
  }

  const COMPARTIDA = { token: 'x.y.z', apodo: 'Novato', rol: 'JUGADOR', uid: 'u1' };

  test('la adopta solo si el emisor dice que vale', async () => {
    const { adoptarSesionCompartida } = await import('./login.js');
    const falsa = sesionFalsa();
    const preguntas = [];

    const adoptada = await adoptarSesionCompartida(COMPARTIDA, {
      ...falsa,
      comprobar: async (token) => {
        preguntas.push(token);
        return 'valida';
      },
    });

    expect(adoptada).toBe(true);
    expect(preguntas).toEqual(['x.y.z']);
    expect(falsa.estado.guardada).toEqual(COMPARTIDA);
  });

  test('un token que el emisor ya no acepta no se adopta: era el bucle /login <-> /inicio', async () => {
    const { adoptarSesionCompartida } = await import('./login.js');
    const falsa = sesionFalsa();

    const adoptada = await adoptarSesionCompartida(COMPARTIDA, {
      ...falsa,
      comprobar: async () => 'invalida',
    });

    expect(adoptada).toBe(false);
    expect(falsa.estado.guardada).toBeNull();
  });

  test('si el emisor no contesta tampoco se adopta: se entra con la contraseña', async () => {
    const { adoptarSesionCompartida } = await import('./login.js');
    const falsa = sesionFalsa();

    const adoptada = await adoptarSesionCompartida(COMPARTIDA, {
      ...falsa,
      comprobar: async () => 'desconocida',
    });

    expect(adoptada).toBe(false);
    expect(falsa.estado.guardada).toBeNull();
  });

  test('sin nada que adoptar, o con sesión propia, ni siquiera pregunta', async () => {
    const { adoptarSesionCompartida } = await import('./login.js');
    let preguntas = 0;
    const comprobar = async () => {
      preguntas += 1;
      return 'valida';
    };

    expect(await adoptarSesionCompartida(null, { ...sesionFalsa(), comprobar })).toBe(false);
    expect(await adoptarSesionCompartida({ token: '' }, { ...sesionFalsa(), comprobar })).toBe(
      false,
    );
    expect(
      await adoptarSesionCompartida(COMPARTIDA, {
        ...sesionFalsa({ yaHaySesion: true }),
        comprobar,
      }),
    ).toBe(false);
    expect(preguntas).toBe(0);
  });

  test('si una vez guardada no queda como sesión válida, se olvida', async () => {
    const { adoptarSesionCompartida } = await import('./login.js');
    const falsa = sesionFalsa({ quedaAutenticada: false });

    const adoptada = await adoptarSesionCompartida(COMPARTIDA, {
      ...falsa,
      comprobar: async () => 'valida',
    });

    expect(adoptada).toBe(false);
    expect(falsa.estado.olvidada).toBe(true);
  });
});
