/**
 * HU-USR-010 — la ficha administrativa del usuario, montada contra un sistema
 * medio caído.
 *
 * La identidad responde con la ficha (ms-identidad-admin.yaml 1.4.0), el
 * servicio de moderación y el de comentarios responden o se caen según la
 * prueba, y seis secciones no tienen fuente publicada. Lo que se prueba es lo
 * que piden los criterios: que la ficha consolide lo que hay (CA-01, CA-04),
 * que un módulo caído diga «no disponible» sin tumbar al resto y que un
 * usuario inexistente se explique (CA-03), y que la línea de tiempo una los
 * hechos con fecha, el más reciente primero.
 */

import { jest } from '@jest/globals';

import { RESULTADO } from '../consola/cliente-consola.js';
import {
  SIN_FUENTE,
  claveDeLaDireccion,
  hechosDeComentarios,
  hechosDeLaCuenta,
  montarFichaDeUsuario,
  pedirFicha,
} from './ficha-usuario.js';

const UID = '11111111-2222-3333-4444-555555555555';
const UUID = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i;
const AHORA = Date.parse('2026-10-05T12:00:00Z');

/** `FichaAdministrativa` de ms-identidad-admin.yaml 1.4.0, tal cual. */
const FICHA = Object.freeze({
  id: 15,
  uid: UID,
  apodo: 'Ana',
  email: 'ana@nexus.test',
  nombres: 'Ana María',
  apellidos: 'Rueda',
  avatar: null,
  rol: 'JUGADOR',
  estado: 'ACTIVO',
  suspendidoHasta: null,
  bloqueada: false,
  creadoEn: '2026-09-01T10:30:00',
  ultimoAcceso: '2026-10-04T21:15:00',
  accesoAuditado: true,
});

/** `Sancion` de moderacion-sanciones-admin.yaml, del más reciente al más antiguo. */
const SANCIONES = [
  {
    id: 'aaaaaaaa-0000-0000-0000-000000000002',
    usuarioId: UID,
    tipo: 'SUSPENSION',
    motivo: 'Insultos en el chat',
    emitidaPor: 'bbbbbbbb-0000-0000-0000-000000000001',
    rolEmisor: 'MODERADOR',
    emitidaEn: '2026-10-03T09:00:00Z',
    vigenteHasta: '2026-10-04T09:00:00Z',
    revertidaEn: null,
    vigente: false,
  },
  {
    id: 'aaaaaaaa-0000-0000-0000-000000000001',
    usuarioId: UID,
    tipo: 'ADVERTENCIA',
    motivo: 'Spam en comentarios',
    emitidaPor: 'bbbbbbbb-0000-0000-0000-000000000001',
    rolEmisor: 'ADMINISTRADOR',
    emitidaEn: '2026-09-20T15:00:00Z',
    vigenteHasta: null,
    revertidaEn: null,
    vigente: true,
  },
];

/** `HistorialDelAutorResponse` de comentarios.yaml 1.7.0. */
const COMENTARIOS = {
  autorId: UID,
  apodoAutor: 'Ana',
  comentarios: [
    {
      id: 'c2',
      productoId: 'p-7',
      texto: 'La espada corta rinde muy bien en la arena.',
      fechaPublicacion: '2026-10-02T18:00:00Z',
      estado: 'PUBLICADO',
      editado: false,
    },
    {
      id: 'c1',
      productoId: 'p-3',
      texto: 'Comentario que moderación ocultó.',
      fechaPublicacion: '2026-09-25T08:00:00Z',
      estado: 'OCULTO',
      editado: true,
    },
  ],
  total: 7,
  pagina: 0,
  tamano: 20,
};

const asentar = async () => {
  for (let i = 0; i < 6; i += 1) {
    await new Promise((resolve) => setTimeout(resolve, 0));
  }
};

const conDatos = (datos) => ({
  resultado: RESULTADO.DATOS,
  datos,
  estado: 200,
  motivo: '',
  recurso: '',
  detalle: null,
});

const conFallo = (resultado, estado, recurso) => ({
  resultado,
  datos: null,
  estado,
  motivo: 'El servicio que responde esta consulta no está atendiendo.',
  recurso,
  detalle: null,
});

/** Respuesta HTTP mínima, como la que devuelve `fetch`. */
function respuesta(estado, cuerpo) {
  return {
    ok: estado >= 200 && estado < 300,
    status: estado,
    json: async () => cuerpo,
  };
}

function identidad(estado = 200, cuerpo = FICHA) {
  return jest.fn(async () => respuesta(estado, cuerpo));
}

function secciones(sobrescribir = {}) {
  return jest.fn(async (recurso) => {
    for (const [prefijo, valor] of Object.entries(sobrescribir)) {
      if (recurso.startsWith(prefijo)) {
        return typeof valor === 'function' ? valor(recurso) : valor;
      }
    }
    if (recurso.startsWith('/sanciones/usuarios/')) {
      return conDatos(SANCIONES);
    }
    if (recurso.startsWith('/comentarios/moderacion/autores/')) {
      return conDatos(COMENTARIOS);
    }
    return conFallo(RESULTADO.NO_DISPONIBLE, 418, recurso);
  });
}

function pagina() {
  document.body.innerHTML =
    '<main><div data-zona="encabezado"></div><div data-zona="aviso"></div>' +
    '<div data-zona="ficha"></div></main>';
  return document;
}

async function montar({ clave = '15', buscar = identidad(), consultarApi = secciones() } = {}) {
  const raiz = pagina();
  const montaje = montarFichaDeUsuario(raiz, { clave, buscar, consultarApi, ahora: () => AHORA });
  await montaje;
  await asentar();
  return { raiz, buscar, consultarApi };
}

const panelDe = (raiz, id) => raiz.querySelector(`[data-panel="${id}"]`);

describe('qué cuenta abre la ficha', () => {
  test('la clave interna o el uid de la dirección; cualquier otra cosa, ninguna', () => {
    expect(claveDeLaDireccion('?usuario=15')).toBe('15');
    expect(claveDeLaDireccion(`?usuario=${UID}`)).toBe(UID);
    expect(claveDeLaDireccion(`?usuario=${UID.toUpperCase()}`)).toBe(UID.toUpperCase());
    for (const raro of [
      '',
      '?usuario=',
      '?usuario=0',
      '?usuario=-3',
      '?usuario=15abc',
      '?otro=15',
    ]) {
      expect(claveDeLaDireccion(raro)).toBeNull();
    }
    expect(claveDeLaDireccion('?usuario=1-1-1-1-1')).toBeNull();
  });

  test('sin cuenta en la dirección no consulta a nadie y dice desde dónde se abre', async () => {
    const { raiz, buscar, consultarApi } = await montar({ clave: null });

    expect(buscar).not.toHaveBeenCalled();
    expect(consultarApi).not.toHaveBeenCalled();
    const ficha = raiz.querySelector('[data-zona="ficha"]');
    expect(ficha.textContent).toContain('Falta la cuenta');
    expect(ficha.querySelector('a').getAttribute('href')).toMatch(/control-integral\.html$/);
  });
});

describe('la ficha de la cuenta (identidad)', () => {
  test('pide la ficha por la ruta del contrato, con la cuenta escapada', async () => {
    const buscar = identidad();
    await pedirFicha(UID, { buscar });

    expect(buscar).toHaveBeenCalledTimes(1);
    expect(buscar.mock.calls[0][0]).toBe(`/api/v1/admin/usuarios/${UID}/ficha`);
  });

  test('CA-03: un usuario inexistente es un problem details y no se consulta ninguna sección', async () => {
    const problema = {
      type: 'https://nexusbattles.upb.edu.co/errors/cuenta-no-encontrada',
      title: 'Cuenta no encontrada',
      status: 404,
      detail: 'No existe una cuenta con ese identificador.',
    };
    const { raiz, consultarApi } = await montar({ clave: '999', buscar: identidad(404, problema) });

    expect(consultarApi).not.toHaveBeenCalled();
    const ficha = raiz.querySelector('[data-zona="ficha"]');
    expect(ficha.textContent).toContain('No existe esa cuenta');
    expect(ficha.textContent).toContain('No existe una cuenta con ese identificador.');
    expect(ficha.querySelector('[data-accion="reintentar"]')).toBeNull();
  });

  test('un 404 sin ese tipo es un servicio que todavía no publica la ficha, no un usuario inexistente', async () => {
    const { raiz } = await montar({ buscar: identidad(404, null) });

    const ficha = raiz.querySelector('[data-zona="ficha"]');
    expect(ficha.textContent).toContain('no publica todavía la ficha');
    expect(ficha.textContent).not.toContain('No existe esa cuenta');
  });

  test('sin permiso lo dice y no ofrece reintentar', async () => {
    const { raiz, consultarApi } = await montar({ buscar: identidad(403, { status: 403 }) });

    expect(consultarApi).not.toHaveBeenCalled();
    const ficha = raiz.querySelector('[data-zona="ficha"]');
    expect(ficha.textContent).toContain('Tu rol no permite abrir fichas de usuario');
    expect(ficha.querySelector('[data-accion="reintentar"]')).toBeNull();
  });

  test('con la identidad caída no hay a quién consultar: se dice, y reintentar vuelve a pedirla', async () => {
    const buscar = jest
      .fn()
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValue(respuesta(200, FICHA));
    const { raiz, consultarApi } = await montar({ buscar });

    const ficha = raiz.querySelector('[data-zona="ficha"]');
    expect(ficha.textContent).toContain('El servicio de identidad no responde');
    expect(consultarApi).not.toHaveBeenCalled();

    ficha.querySelector('[data-accion="reintentar"]').click();
    await asentar();

    expect(buscar).toHaveBeenCalledTimes(2);
    expect(raiz.querySelector('h1').textContent).toBe('Ficha de Ana');
  });

  test('CA-01: datos de la cuenta, rol, estado, registro, última entrada y contacto', async () => {
    const { raiz } = await montar();

    expect(raiz.querySelector('h1').textContent).toBe('Ficha de Ana');
    const cuenta = panelDe(raiz, 'cuenta');
    const pares = Object.fromEntries(
      [...cuenta.querySelectorAll('dt')].map((dt) => [
        dt.textContent,
        dt.nextElementSibling.textContent,
      ]),
    );
    expect(pares.Apodo).toBe('Ana');
    expect(pares.Nombre).toBe('Ana María Rueda');
    expect(pares.Correo).toBe('ana@nexus.test');
    expect(pares.Rol).toBe('Jugador');
    expect(pares.Estado).toBe('Activa');
    expect(pares.Registro).not.toBe('sin dato');
    expect(pares['Última entrada']).not.toBe('sin dato');
    // La clave y el uid sirven para pedir lo demás, no para leerlos.
    expect(raiz.querySelector('main').textContent).not.toMatch(UUID);
  });

  test('una cuenta sin perfil ni entradas lo dice en vez de inventar', async () => {
    const sinPerfil = { ...FICHA, nombres: null, apellidos: null, ultimoAcceso: null, rol: null };
    const { raiz } = await montar({ buscar: identidad(200, sinPerfil) });

    const cuenta = panelDe(raiz, 'cuenta');
    expect(cuenta.textContent).toContain('Nunca ha entrado');
    expect(cuenta.textContent).toContain('sin dato');
  });

  test('una cuenta suspendida y bloqueada lo dice con palabras', async () => {
    const suspendida = {
      ...FICHA,
      estado: 'SUSPENDIDO',
      suspendidoHasta: '2026-10-09T08:00:00',
      bloqueada: true,
    };
    const { raiz } = await montar({ buscar: identidad(200, suspendida) });

    const cuenta = panelDe(raiz, 'cuenta');
    expect(cuenta.textContent).toContain('Suspendida hasta');
    expect(cuenta.textContent).toContain('Bloqueada por intentos fallidos');
  });
});

describe('la auditoría del acceso', () => {
  test('la consulta registrada en la auditoría se anuncia', async () => {
    const { raiz } = await montar();

    const aviso = raiz.querySelector('[data-zona="aviso"]');
    expect(aviso.dataset.auditado).toBe('si');
    expect(aviso.textContent).toContain('quedó registrada en la auditoría');
  });

  test('si la auditoría no la registró, la ficha lo advierte (nunca en silencio)', async () => {
    const { raiz } = await montar({ buscar: identidad(200, { ...FICHA, accesoAuditado: false }) });

    const aviso = raiz.querySelector('[data-zona="aviso"]');
    expect(aviso.dataset.auditado).toBe('no');
    expect(aviso.querySelector('.aviso--advertencia')).not.toBeNull();
    expect(aviso.textContent).toContain('no quedó registrada en la auditoría');
    // La ficha se ve igual: la auditoría es un módulo más (CA-03).
    expect(panelDe(raiz, 'cuenta').textContent).toContain('ana@nexus.test');
  });
});

describe('las secciones de los otros servicios', () => {
  test('sanciones y comentarios se piden por el uid, cada una a su servicio', async () => {
    const { consultarApi } = await montar();

    const pedidas = consultarApi.mock.calls.map((c) => c[0]);
    expect(pedidas).toContain(`/sanciones/usuarios/${UID}`);
    expect(pedidas).toContain(
      `/comentarios/moderacion/autores/${UID}/comentarios?pagina=0&tamano=20`,
    );
  });

  test('el historial de sanciones dice el estado de la cuenta y cada sanción', async () => {
    const { raiz } = await montar();

    const sanciones = panelDe(raiz, 'sanciones');
    expect(sanciones.querySelector('[data-estado-cuenta="en-regla"]')).not.toBeNull();
    const filas = [...sanciones.querySelectorAll('tbody tr')].map((tr) => tr.textContent);
    expect(filas).toHaveLength(2);
    expect(filas[0]).toContain('Suspensión');
    expect(filas[0]).toContain('Cumplida');
    expect(filas[1]).toContain('Advertencia');
    expect(filas[1]).toContain('Spam en comentarios');
  });

  test('una cuenta sin sanciones lo dice, no «sin registros»', async () => {
    const { raiz } = await montar({
      consultarApi: secciones({
        '/sanciones/usuarios/': { ...conDatos([]), resultado: RESULTADO.VACIO },
      }),
    });

    const sanciones = panelDe(raiz, 'sanciones');
    expect(sanciones.textContent).toContain('No tiene sanciones ni advertencias.');
  });

  test('los comentarios publicados, con su estado y cuántos hay en total', async () => {
    const { raiz } = await montar();

    const comentarios = panelDe(raiz, 'comentarios');
    const filas = [...comentarios.querySelectorAll('tbody tr')].map((tr) => tr.textContent);
    expect(filas).toHaveLength(2);
    expect(filas[0]).toContain('La espada corta rinde muy bien en la arena.');
    expect(filas[0]).toContain('Publicado');
    expect(filas[1]).toContain('Oculto');
    expect(filas[1]).toContain('editado por moderación');
    expect(comentarios.querySelector('caption').textContent).toContain('2 más recientes de 7');
  });

  test('un autor sin comentarios lo dice', async () => {
    const { raiz } = await montar({
      consultarApi: secciones({
        '/comentarios/moderacion/autores/': conDatos({ ...COMENTARIOS, comentarios: [], total: 0 }),
      }),
    });

    expect(panelDe(raiz, 'comentarios').textContent).toContain('No ha publicado comentarios.');
  });

  test('CA-03: un módulo caído dice «no disponible» y el resto de la ficha sigue', async () => {
    const { raiz } = await montar({
      consultarApi: secciones({
        '/sanciones/usuarios/': (recurso) => conFallo(RESULTADO.SERVICIO_DEGRADADO, 503, recurso),
      }),
    });

    const sanciones = panelDe(raiz, 'sanciones');
    expect(sanciones.textContent).toContain('SERVICIO DEGRADADO');
    expect(sanciones.querySelector('[data-accion="reintentar"]')).not.toBeNull();
    // El identificador de la cuenta no se pinta ni en el detalle técnico.
    expect(sanciones.textContent).not.toMatch(UUID);
    expect(panelDe(raiz, 'comentarios').querySelectorAll('tbody tr')).toHaveLength(2);
    expect(panelDe(raiz, 'cuenta').textContent).toContain('ana@nexus.test');
  });

  test('una cuenta sin uid no se puede pedir a moderación ni a comentarios, y se dice', async () => {
    const { raiz, consultarApi } = await montar({
      buscar: identidad(200, { ...FICHA, uid: null }),
    });

    expect(consultarApi).not.toHaveBeenCalled();
    expect(panelDe(raiz, 'sanciones').textContent).toContain('no tiene identificador público');
    expect(panelDe(raiz, 'comentarios').textContent).toContain('no tiene identificador público');
  });

  test('lo que ningún servicio publica para administración dice «Sin fuente publicada»', async () => {
    const { raiz, consultarApi } = await montar();

    for (const seccion of SIN_FUENTE) {
      const panel = panelDe(raiz, seccion.id);
      expect(panel).not.toBeNull();
      expect(panel.textContent).toContain('Sin fuente publicada');
      expect(panel.querySelector('.sello-estado').textContent).toBe('SIN FUENTE');
    }
    expect(SIN_FUENTE.map((s) => s.id).sort()).toEqual(
      ['auditoria', 'estadisticas', 'misiones', 'reportes', 'subastas', 'transacciones'].sort(),
    );
    // Ninguna de esas secciones hace una petición: no hay a quién hacerla.
    expect(consultarApi).toHaveBeenCalledTimes(2);
  });
});

describe('las acciones', () => {
  test('enlazan a las que ya existen, con la cuenta en la dirección', async () => {
    const { raiz } = await montar();

    const acciones = panelDe(raiz, 'acciones');
    const gestionar = new URL(acciones.querySelector('a[data-accion="gestionar"]').href);
    expect(gestionar.pathname).toMatch(/cuentas\/gestion-usuarios\.html$/);
    expect(gestionar.searchParams.get('usuario')).toBe('15');
    const sancionar = new URL(acciones.querySelector('a[data-accion="sancionar"]').href);
    expect(sancionar.pathname).toMatch(/moderacion-sanciones\/sanciones-admin\.html$/);
    expect(sancionar.searchParams.get('usuario')).toBe(UID);
    expect(acciones.textContent).toContain('restablecer la contraseña');
  });

  test('sin uid no se ofrece sancionar desde moderación', async () => {
    const { raiz } = await montar({ buscar: identidad(200, { ...FICHA, uid: null }) });

    const acciones = panelDe(raiz, 'acciones');
    expect(acciones.querySelector('a[data-accion="sancionar"]')).toBeNull();
    expect(acciones.querySelector('a[data-accion="gestionar"]')).not.toBeNull();
  });
});

describe('la línea de tiempo', () => {
  test('une los hechos con fecha de las secciones cargadas, el más reciente primero', async () => {
    const { raiz } = await montar();

    const linea = panelDe(raiz, 'linea-de-tiempo');
    const fechas = [...linea.querySelectorAll('time')].map((t) => Date.parse(t.dateTime));
    expect(fechas.length).toBeGreaterThanOrEqual(6);
    expect([...fechas].sort((a, b) => b - a)).toEqual(fechas);
    const titulos = [...linea.querySelectorAll('.linea-tiempo__titulo')].map((t) => t.textContent);
    expect(titulos).toEqual(
      expect.arrayContaining([
        'Última entrada',
        'Suspensión emitida',
        'Publicó un comentario',
        'Advertencia emitida',
        'Se registró',
      ]),
    );
    expect(titulos[0]).toBe('Última entrada');
    expect(titulos.at(-1)).toBe('Se registró');
  });

  test('si una sección no respondió, la línea se pinta con el resto y dice qué falta', async () => {
    const { raiz } = await montar({
      consultarApi: secciones({
        '/comentarios/moderacion/autores/': (recurso) =>
          conFallo(RESULTADO.SERVICIO_DEGRADADO, 502, recurso),
      }),
    });

    const linea = panelDe(raiz, 'linea-de-tiempo');
    const titulos = [...linea.querySelectorAll('.linea-tiempo__titulo')].map((t) => t.textContent);
    expect(titulos).toContain('Suspensión emitida');
    expect(titulos).not.toContain('Publicó un comentario');
    expect(linea.textContent).toContain('Faltan los hechos de: comentarios');
    expect(linea.querySelector('.sello-estado').textContent).toBe('PARCIAL');
  });

  test('los hechos de la cuenta y de los comentarios, sin inventar fechas', () => {
    expect(hechosDeLaCuenta({ creadoEn: null, ultimoAcceso: null })).toEqual([]);
    const cuenta = hechosDeLaCuenta(FICHA);
    expect(cuenta.map((h) => h.titulo)).toEqual(['Se registró', 'Última entrada']);

    const comentarios = hechosDeComentarios(COMENTARIOS);
    expect(comentarios).toHaveLength(2);
    expect(comentarios[1].tono).toBe('advertencia');
    expect(comentarios[1].detalle).toContain('Oculto');
    expect(hechosDeComentarios(null)).toEqual([]);
  });
});
