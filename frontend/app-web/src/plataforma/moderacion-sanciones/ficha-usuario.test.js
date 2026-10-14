/**
 * HU-USR-010 — la ficha administrativa del usuario (§7.3.4), montada contra un
 * sistema medio caído.
 *
 * La identidad responde con la ficha (ms-identidad-admin.yaml 1.4.0) y cada
 * sección pide lo suyo a su dueño por el uid de la cuenta (SA-3): partidas,
 * créditos, sanciones, comentarios, reportes, inventario y, para el Super
 * Administrador, la auditoría. Lo que se prueba es lo que piden los
 * criterios: que la ficha consolide lo que hay (CA-01, CA-04), que un módulo
 * caído diga que no pudo cargar sin tumbar al resto y que un usuario
 * inexistente se explique (CA-03), y que la línea de tiempo una los hechos
 * con fecha, el más reciente primero.
 */

import { jest } from '@jest/globals';

import { RESULTADO } from '../consola/cliente-consola.js';
import {
  claveDeLaDireccion,
  hechosDeAuditoria,
  hechosDeComentarios,
  hechosDeLaCuenta,
  hechosDeMovimientos,
  hechosDePartidas,
  hechosDeReportes,
  montarFichaDeUsuario,
  pedirFicha,
  PESTANAS_DE_LA_FICHA,
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

/** `EstadisticasDeJugador` de salas-partidas.yaml 1.11.0. */
const ESTADISTICAS = {
  idJugador: UID,
  jugadas: 12,
  victorias: 7,
  derrotas: 4,
  empates: 1,
  enCurso: 1,
  porModalidad: {
    UNO_CONTRA_UNO: { jugadas: 9, victorias: 6, derrotas: 3, empates: 0 },
    HASTA_SEIS: { jugadas: 3, victorias: 1, derrotas: 1, empates: 1 },
  },
  ultimaPartidaEn: '2026-10-04T20:00:00Z',
  parcial: false,
};

/** `PaginaDePartidas` de salas-partidas.yaml 1.11.0, de la más reciente a la más antigua. */
const PARTIDAS = {
  contenido: [
    {
      id: 'dddddddd-0000-0000-0000-000000000002',
      idSala: 'eeeeeeee-0000-0000-0000-000000000002',
      modalidad: 'UNO_CONTRA_UNO',
      estado: 'EN_CURSO',
      resultado: null,
      heroe: 'Aquiles',
      participantes: 2,
      iniciadaEn: '2026-10-04T20:00:00Z',
      finalizadaEn: null,
    },
    {
      id: 'dddddddd-0000-0000-0000-000000000001',
      idSala: 'eeeeeeee-0000-0000-0000-000000000001',
      modalidad: 'HASTA_SEIS',
      estado: 'FINALIZADA',
      resultado: 'VICTORIA',
      heroe: 'Brisa',
      participantes: 4,
      iniciadaEn: '2026-10-01T18:00:00Z',
      finalizadaEn: '2026-10-01T18:12:00Z',
    },
  ],
  pagina: 0,
  tamano: 10,
  totalElementos: 13,
  totalPaginas: 2,
};

/** `Saldo` de creditos.yaml 1.5.0. */
const SALDO = { jugadorUid: UID, saldoBruto: 1500, saldoReservado: 120, saldoDisponible: 1380 };

/** Página de `MovimientoDeCredito` de creditos.yaml 1.5.0. */
const MOVIMIENTOS = {
  content: [
    {
      id: 'ffffffff-0000-0000-0000-000000000002',
      monto: 40,
      concepto: 'recompensa-victoria',
      referenciaId: 'partida-1',
      tipo: 'CREDITO',
      estado: 'CONSUMIDA',
      signo: 'SUMA',
      creado: '2026-10-01T18:12:30Z',
    },
    {
      id: 'ffffffff-0000-0000-0000-000000000001',
      monto: 120,
      concepto: 'apuesta-sala',
      referenciaId: 'sala-2',
      tipo: 'RESERVA',
      estado: 'ACTIVA',
      signo: 'APARTA',
      creado: '2026-10-04T19:59:00Z',
    },
  ],
  totalElements: 2,
  totalPages: 1,
  number: 0,
  size: 10,
};

/** `ReportesDelUsuarioResponse` de comentarios.yaml 1.12.0. */
const REPORTES_REALIZADOS = {
  usuarioId: UID,
  direccion: 'REALIZADOS',
  reportes: [
    {
      id: 'r-1',
      comentarioId: 'c-9',
      categoria: 'SPAM',
      descripcion: 'Publicidad de otra página',
      fecha: '2026-10-02T11:00:00Z',
      comentario: {
        id: 'c-9',
        productoId: 'p-1',
        autorId: 'cccccccc-0000-0000-0000-000000000009',
        apodoAutor: 'Bruno',
        extracto: 'Compra monedas baratas en otra web',
        estado: 'OCULTO',
      },
    },
  ],
  total: 1,
  pagina: 0,
  tamano: 10,
  totalRealizados: 1,
  totalRecibidos: 2,
};

const REPORTES_RECIBIDOS = {
  ...REPORTES_REALIZADOS,
  direccion: 'RECIBIDOS',
  reportes: [
    {
      id: 'r-3',
      comentarioId: 'c2',
      categoria: 'ACOSO',
      descripcion: null,
      fecha: '2026-10-03T08:30:00Z',
      comentario: {
        id: 'c2',
        productoId: 'p-7',
        autorId: UID,
        apodoAutor: 'Ana',
        extracto: 'La espada corta rinde muy bien en la arena.',
        estado: 'PUBLICADO',
      },
    },
    {
      id: 'r-2',
      comentarioId: 'c-borrado',
      categoria: 'VIOLACION_DE_DERECHOS',
      descripcion: 'Usa una imagen mía',
      fecha: '2026-09-28T08:30:00Z',
      comentario: null,
    },
  ],
  total: 2,
};

/** `PaginaInventario` de inventario.yaml 1.8.0. */
const INVENTARIO = {
  elementos: [
    {
      id: 'i-1',
      productoId: 'p-h1',
      tipo: 'HEROE',
      nombrePropio: 'Aquiles',
      disponible: true,
      subastaId: null,
      nivel: 3,
    },
    {
      id: 'i-2',
      productoId: 'p-a1',
      tipo: 'ARMA',
      nombrePropio: 'Espada corta',
      disponible: true,
      subastaId: null,
      equipadoEn: { heroeId: 'i-1', nombreHeroe: 'Aquiles' },
    },
    {
      id: 'i-3',
      productoId: 'p-a2',
      tipo: 'ARMADURA',
      nombrePropio: 'Yelmo de bronce',
      disponible: false,
      subastaId: 'aaaaaaaa-1111-4111-8111-111111111111',
    },
  ],
  numero: 0,
  tamanio: 16,
  totalElementos: 3,
  totalPaginas: 1,
  ultima: true,
};

/** `PermissionMatrixResponse` de rbac.yaml 2.0.0 (lo que importa aquí). */
const MATRIZ = {
  version: '2.0.0',
  matrix: {
    JUGADOR: {
      CREAR_CUENTA_JUGADOR: 'GRANTED',
      MODIFICAR_PERFIL_PROPIO: 'GRANTED',
      PUBLICAR_COMENTARIOS: 'GRANTED',
      ELIMINAR_COMENTARIO_PROPIO: 'GRANTED',
      MODERAR_COMENTARIOS: 'DENIED',
      GESTIONAR_CUENTAS: 'DENIED',
    },
    MODERADOR: { MODERAR_COMENTARIOS: 'GRANTED', SUSPENDER_USUARIOS: 'TEMPORARY' },
  },
};

/** `PaginaDeAuditoria` de ms-cumplimiento-auditoria.yaml 1.2.0. */
const AUDITORIA = {
  content: [
    {
      id: 'a-3',
      fechaHora: '2026-10-05T11:59:00Z',
      administrador: 'ada_admin',
      tipoAccion: 'OTRO',
      afectado: UID,
      valorAnterior: null,
      valorNuevo: null,
      motivo: 'Consulta de la ficha administrativa (datos personales)',
      ipOrigen: '10.0.0.1',
    },
    {
      id: 'a-2',
      fechaHora: '2026-10-03T09:00:05Z',
      administrador: 'mod_lia',
      tipoAccion: 'SANCION',
      afectado: UID,
      valorAnterior: null,
      valorNuevo: 'SUSPENSION',
      motivo: 'Insultos en el chat',
      ipOrigen: '10.0.0.2',
    },
    {
      id: 'a-1',
      fechaHora: '2026-09-15T10:00:00Z',
      administrador: 'ada_admin',
      tipoAccion: 'ACTUALIZACION',
      afectado: '15',
      valorAnterior: 'apodo=ana_r',
      valorNuevo: 'apodo=Ana',
      motivo: 'Corrección pedida por la jugadora',
      ipOrigen: '10.0.0.1',
    },
  ],
  totalElements: 3,
  totalPages: 1,
  number: 0,
  size: 20,
  first: true,
  last: true,
};

const SUPER = Object.freeze({ autenticado: true, rol: 'SUPER_ADMINISTRADOR' });
const ADMIN = Object.freeze({ autenticado: true, rol: 'ADMINISTRADOR' });

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
    const respuestas = [
      ['/sanciones/usuarios/', SANCIONES],
      ['/comentarios/moderacion/autores/', COMENTARIOS],
      ['/rbac/matrix', MATRIZ],
      [`/partidas/jugadores/${UID}/estadisticas`, ESTADISTICAS],
      ['/partidas/jugadores/', PARTIDAS],
      [`/creditos/${UID}/saldo`, SALDO],
      [`/creditos/${UID}/movimientos`, MOVIMIENTOS],
      [
        `/comentarios/moderacion/usuarios/${UID}/reportes?direccion=REALIZADOS`,
        REPORTES_REALIZADOS,
      ],
      [`/comentarios/moderacion/usuarios/${UID}/reportes?direccion=RECIBIDOS`, REPORTES_RECIBIDOS],
      ['/inventario/usuarios/', INVENTARIO],
      ['/admin/auditoria', AUDITORIA],
    ];
    for (const [prefijo, datos] of respuestas) {
      if (recurso.startsWith(prefijo)) {
        return conDatos(datos);
      }
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

async function montar({
  clave = '15',
  buscar = identidad(),
  consultarApi = secciones(),
  sesion = ADMIN,
} = {}) {
  const raiz = pagina();
  const montaje = montarFichaDeUsuario(raiz, {
    clave,
    buscar,
    consultarApi,
    ahora: () => AHORA,
    sesion,
  });
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
    expect(pares.Registro).not.toBe('Sin dato');
    expect(pares['Última entrada']).not.toBe('Sin dato');
    // La clave y el uid sirven para pedir lo demás, no para leerlos.
    expect(raiz.querySelector('main').textContent).not.toMatch(UUID);
  });

  test('una cuenta sin perfil ni entradas lo dice en vez de inventar', async () => {
    const sinPerfil = { ...FICHA, nombres: null, apellidos: null, ultimoAcceso: null, rol: null };
    const { raiz } = await montar({ buscar: identidad(200, sinPerfil) });

    const cuenta = panelDe(raiz, 'cuenta');
    expect(cuenta.textContent).toContain('Nunca ha entrado');
    expect(cuenta.textContent).toContain('Sin dato');
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
    expect(sanciones.querySelector('.panel-admin__estado').textContent).toBe('Sin conexión');
    expect(sanciones.textContent).not.toMatch(/SERVICIO DEGRADADO|GET \/api|HTTP/);
    expect(sanciones.querySelector('[data-accion="reintentar"]')).not.toBeNull();
    // El identificador de la cuenta no se pinta ni en el detalle técnico.
    expect(sanciones.textContent).not.toMatch(UUID);
    expect(panelDe(raiz, 'comentarios').querySelectorAll('tbody tr')).toHaveLength(2);
    expect(panelDe(raiz, 'cuenta').textContent).toContain('ana@nexus.test');
  });

  test('una cuenta sin uid no se puede pedir a ningún servicio por uid, y se dice', async () => {
    const { raiz, consultarApi } = await montar({
      buscar: identidad(200, { ...FICHA, uid: null }),
    });

    // Solo la matriz de permisos, que no va por la cuenta.
    expect(consultarApi.mock.calls.map((c) => c[0])).toEqual(['/rbac/matrix']);
    for (const id of [
      'sanciones',
      'comentarios',
      'estadisticas',
      'partidas',
      'saldo',
      'movimientos',
      'reportes-realizados',
      'reportes-recibidos',
      'inventario-de-la-cuenta',
    ]) {
      expect(panelDe(raiz, id).textContent).toContain('Sin identificador público');
    }
  });

  test('SA-2: sin secciones vacías de relleno ni texto de andamio', async () => {
    const { raiz, consultarApi } = await montar();

    expect(raiz.querySelector('[data-seccion="sin-fuente"]')).toBeNull();
    expect(raiz.body.textContent).not.toMatch(
      /Sin fuente publicada|SIN FUENTE|Fuente:|GET \/api|HU-|NO IMPLEMENTADO|HTTP \d/,
    );
    // La matriz de permisos y nueve secciones por uid; la auditoría no, que
    // no es del Administrador.
    expect(consultarApi).toHaveBeenCalledTimes(10);
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
    expect(linea.querySelector('.panel-admin__estado').textContent).toBe('Incompleta');
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

describe('SA-3 — las pestañas de la ficha (§7.3.4)', () => {
  test('siete pestañas en el orden del requisito, con su sección y el Resumen abierto', async () => {
    const { raiz } = await montar();

    const pestanas = [...raiz.querySelectorAll('[role="tab"]')];
    expect(pestanas.map((p) => p.textContent)).toEqual(
      PESTANAS_DE_LA_FICHA.map(({ etiqueta }) => etiqueta),
    );
    expect(pestanas.map((p) => p.textContent)).toEqual([
      'Resumen',
      'Juego',
      'Créditos',
      'Sanciones',
      'Comunidad',
      'Inventario',
      'Actividad',
    ]);
    expect(raiz.querySelector('[role="tab"][aria-selected="true"]').textContent).toBe('Resumen');
    const resumen = raiz.querySelector('[role="tabpanel"][data-seccion="resumen"]');
    expect(resumen.hidden).toBe(false);
    for (const id of ['cuenta', 'permisos', 'acciones']) {
      expect(resumen.querySelector(`[data-panel="${id}"]`)).not.toBeNull();
    }
    const juego = raiz.querySelector('[role="tabpanel"][data-seccion="juego"]');
    expect(juego.hidden).toBe(true);
    expect(juego.querySelector('[data-panel="estadisticas"]')).not.toBeNull();
    expect(juego.querySelector('[data-panel="partidas"]')).not.toBeNull();
  });

  test('cambiar de pestaña enseña su sección y esconde la anterior', async () => {
    const { raiz } = await montar();

    raiz.querySelector('#pestana-comunidad').click();

    const comunidad = raiz.querySelector('[role="tabpanel"][data-seccion="comunidad"]');
    expect(comunidad.hidden).toBe(false);
    expect(raiz.querySelector('[role="tabpanel"][data-seccion="resumen"]').hidden).toBe(true);
    for (const id of ['comentarios', 'reportes-realizados', 'reportes-recibidos']) {
      expect(comunidad.querySelector(`[data-panel="${id}"]`)).not.toBeNull();
    }
  });
});

describe('SA-3 — rol y permisos asignados', () => {
  test('el rol de la cuenta y lo que la matriz del servidor le permite, en palabras', async () => {
    const { raiz, consultarApi } = await montar();

    expect(consultarApi.mock.calls.map((c) => c[0])).toContain('/rbac/matrix');
    const permisos = panelDe(raiz, 'permisos');
    expect(permisos.textContent).toContain('Jugador');
    const lista = [...permisos.querySelectorAll('li')].map((li) => li.textContent);
    expect(lista).toEqual([
      'Crear una cuenta de jugador',
      'Editar su propio perfil',
      'Publicar comentarios',
      'Eliminar sus comentarios',
    ]);
    expect(permisos.textContent).not.toContain('Moderar comentarios');
  });

  test('un permiso temporal lo dice', async () => {
    const { raiz } = await montar({ buscar: identidad(200, { ...FICHA, rol: 'MODERADOR' }) });

    const lista = [...panelDe(raiz, 'permisos').querySelectorAll('li')].map((li) => li.textContent);
    expect(lista).toEqual(['Moderar comentarios', 'Suspender cuentas (solo de forma temporal)']);
  });

  test('si la matriz no se puede leer, el panel lo dice y la ficha sigue', async () => {
    const { raiz } = await montar({
      consultarApi: secciones({
        '/rbac/matrix': (recurso) => conFallo(RESULTADO.SERVICIO_DEGRADADO, 503, recurso),
      }),
    });

    expect(panelDe(raiz, 'permisos').querySelector('[data-accion="reintentar"]')).not.toBeNull();
    expect(panelDe(raiz, 'cuenta').textContent).toContain('ana@nexus.test');
  });
});

describe('SA-3 — estadísticas de juego y últimas partidas', () => {
  test('las cifras del servicio de partidas, sin recalcularlas, y el reparto por modalidad', async () => {
    const { raiz, consultarApi } = await montar();

    const pedidas = consultarApi.mock.calls.map((c) => c[0]);
    expect(pedidas).toContain(`/partidas/jugadores/${UID}/estadisticas`);
    expect(pedidas).toContain(`/partidas/jugadores/${UID}?pagina=0&tamano=10`);

    const estadisticas = panelDe(raiz, 'estadisticas');
    const cifras = Object.fromEntries(
      [...estadisticas.querySelectorAll('.indicador-admin')].map((tarjeta) => [
        tarjeta.dataset.indicador,
        tarjeta.querySelector('.indicador-admin__valor').textContent,
      ]),
    );
    expect(cifras['Partidas terminadas']).toBe('12');
    expect(cifras.Victorias).toBe('7');
    expect(cifras.Derrotas).toBe('4');
    expect(cifras.Empates).toBe('1');
    expect(cifras['En curso']).toBe('1');
    expect(cifras['Última partida']).toMatch(/^4 de oct/);
    const filas = [...estadisticas.querySelectorAll('tbody tr')].map((tr) => tr.textContent);
    expect(filas).toHaveLength(2);
    expect(filas[0]).toContain('1 contra 1');
    expect(filas[1]).toContain('Hasta seis');
  });

  test('una cuenta que nunca jugó tiene ceros y lo dice, sin fecha inventada', async () => {
    const { raiz } = await montar({
      consultarApi: secciones({
        [`/partidas/jugadores/${UID}/estadisticas`]: conDatos({
          ...ESTADISTICAS,
          jugadas: 0,
          victorias: 0,
          derrotas: 0,
          empates: 0,
          enCurso: 0,
          porModalidad: {},
          ultimaPartidaEn: null,
        }),
        '/partidas/jugadores/': { ...conDatos({ contenido: [] }), resultado: RESULTADO.VACIO },
      }),
    });

    const estadisticas = panelDe(raiz, 'estadisticas');
    expect(
      estadisticas.querySelector('[data-indicador="Última partida"] .indicador-admin__valor')
        .textContent,
    ).toBe('Nunca');
    expect(estadisticas.textContent).toContain('Todavía no ha terminado ninguna partida.');
    expect(
      estadisticas.querySelector('[data-indicador="Partidas terminadas"] .indicador-admin__valor')
        .textContent,
    ).toBe('0');
    expect(panelDe(raiz, 'partidas').textContent).toContain('No ha jugado ninguna partida.');
  });

  test('si se contaron solo las mil más recientes, se avisa', async () => {
    const { raiz } = await montar({
      consultarApi: secciones({
        [`/partidas/jugadores/${UID}/estadisticas`]: conDatos({ ...ESTADISTICAS, parcial: true }),
      }),
    });

    expect(panelDe(raiz, 'estadisticas').textContent).toContain('mil partidas más recientes');
  });

  test('las últimas partidas con modalidad, héroe, jugadores y resultado', async () => {
    const { raiz } = await montar();

    const partidas = panelDe(raiz, 'partidas');
    const filas = [...partidas.querySelectorAll('tbody tr')].map((tr) => tr.textContent);
    expect(filas).toHaveLength(2);
    expect(filas[0]).toContain('1 contra 1');
    expect(filas[0]).toContain('Aquiles');
    expect(filas[0]).toContain('En curso');
    expect(filas[1]).toContain('Hasta seis');
    expect(filas[1]).toContain('Victoria');
    expect(partidas.querySelector('caption').textContent).toContain('2 más recientes de 13');
  });
});

describe('SA-3 — créditos: saldo y movimientos', () => {
  test('el saldo del libro de créditos: disponible, apartado y total', async () => {
    const { raiz, consultarApi } = await montar();

    expect(consultarApi.mock.calls.map((c) => c[0])).toContain(`/creditos/${UID}/saldo`);
    const saldo = panelDe(raiz, 'saldo');
    const valor = (etiqueta) =>
      saldo.querySelector(`[data-indicador="${etiqueta}"] .indicador-admin__cifra`).textContent;
    expect(valor('Disponible')).toBe('1.380');
    expect(valor('Apartado')).toBe('120');
    expect(valor('Total')).toBe('1.500');
  });

  test('los movimientos con su concepto y el importe que decide el libro', async () => {
    const { raiz, consultarApi } = await montar();

    expect(consultarApi.mock.calls.map((c) => c[0])).toContain(
      `/creditos/${UID}/movimientos?page=0&size=10`,
    );
    const movimientos = panelDe(raiz, 'movimientos');
    const filas = [...movimientos.querySelectorAll('tbody tr')];
    expect(filas).toHaveLength(2);
    expect(filas[0].textContent).toContain('Recompensa por ganar');
    expect(filas[0].querySelector('.movimiento__importe').textContent).toBe('+40');
    expect(filas[0].querySelector('.movimiento__importe').dataset.tono).toBe('suma');
    expect(filas[1].textContent).toContain('Apuesta de una batalla');
    expect(filas[1].querySelector('.movimiento__importe').textContent).toBe('120 apartados');
  });

  test('un saldo que no se mueve lo dice', async () => {
    const { raiz } = await montar({
      consultarApi: secciones({
        [`/creditos/${UID}/movimientos`]: {
          ...conDatos({ content: [], totalElements: 0 }),
          resultado: RESULTADO.VACIO,
        },
      }),
    });

    expect(panelDe(raiz, 'movimientos').textContent).toContain('Su saldo no se ha movido todavía.');
  });
});

describe('SA-3 — reportes realizados y recibidos', () => {
  test('se piden por las dos direcciones al servicio de comentarios', async () => {
    const { consultarApi } = await montar();

    const pedidas = consultarApi.mock.calls.map((c) => c[0]);
    expect(pedidas).toContain(
      `/comentarios/moderacion/usuarios/${UID}/reportes?direccion=REALIZADOS&pagina=0&tamano=10`,
    );
    expect(pedidas).toContain(
      `/comentarios/moderacion/usuarios/${UID}/reportes?direccion=RECIBIDOS&pagina=0&tamano=10`,
    );
  });

  test('los realizados dicen de quién era el comentario; los recibidos, nunca quién reportó', async () => {
    const { raiz } = await montar();

    const realizados = panelDe(raiz, 'reportes-realizados');
    const fila = realizados.querySelector('tbody tr').textContent;
    expect(fila).toContain('Spam o publicidad');
    expect(fila).toContain('Bruno');
    expect(fila).toContain('Compra monedas baratas');
    expect(fila).toContain('Oculto');
    expect(fila).toContain('Publicidad de otra página');

    const recibidos = panelDe(raiz, 'reportes-recibidos');
    const encabezados = [...recibidos.querySelectorAll('thead th')].map((th) => th.textContent);
    expect(encabezados).not.toContain('Autor del comentario');
    const filas = [...recibidos.querySelectorAll('tbody tr')].map((tr) => tr.textContent);
    expect(filas).toHaveLength(2);
    expect(filas[0]).toContain('Acoso a otra persona');
    expect(filas[1]).toContain('Violación de derechos');
    expect(filas[1]).toContain('El comentario ya no existe');
    expect(filas[0]).toContain('Sin descripción');
    expect(recibidos.textContent).not.toMatch(UUID);
  });

  test('sin reportes en una dirección lo dice', async () => {
    const vacio = { ...REPORTES_REALIZADOS, reportes: [], total: 0 };
    const { raiz } = await montar({
      consultarApi: secciones({
        [`/comentarios/moderacion/usuarios/${UID}/reportes?direccion=RECIBIDOS`]: conDatos({
          ...vacio,
          direccion: 'RECIBIDOS',
        }),
        [`/comentarios/moderacion/usuarios/${UID}/reportes?direccion=REALIZADOS`]: conDatos(vacio),
      }),
    });

    expect(panelDe(raiz, 'reportes-recibidos').textContent).toContain(
      'Nadie ha reportado sus comentarios.',
    );
    expect(panelDe(raiz, 'reportes-realizados').textContent).toContain(
      'No ha reportado ningún comentario.',
    );
  });
});

describe('SA-3 — inventario de la cuenta', () => {
  test('héroes y objetos con dónde está cada uno, leídos del inventario', async () => {
    const { raiz, consultarApi } = await montar();

    expect(consultarApi.mock.calls.map((c) => c[0])).toContain(
      `/inventario/usuarios/${UID}/elementos?pagina=0`,
    );
    const inventario = panelDe(raiz, 'inventario-de-la-cuenta');
    const filas = [...inventario.querySelectorAll('tbody tr')].map((tr) => tr.textContent);
    expect(filas).toHaveLength(3);
    expect(filas[0]).toContain('Aquiles');
    expect(filas[0]).toContain('Héroe');
    expect(filas[0]).toContain('3');
    expect(filas[0]).toContain('Disponible');
    expect(filas[1]).toContain('Equipado en Aquiles');
    expect(filas[1]).toContain('No aplica');
    expect(filas[2]).toContain('En subasta');
    expect(inventario.textContent).not.toMatch(UUID);
    // Leer el inventario no es modificarlo: no hay botones que no hagan nada.
    expect(inventario.querySelector('button:not([data-accion="reintentar"])')).toBeNull();
  });

  test('una cuenta sin productos lo dice', async () => {
    const { raiz } = await montar({
      consultarApi: secciones({
        '/inventario/usuarios/': conDatos({ ...INVENTARIO, elementos: [], totalElementos: 0 }),
      }),
    });

    expect(panelDe(raiz, 'inventario-de-la-cuenta').textContent).toContain(
      'No tiene héroes ni objetos.',
    );
  });

  test('CA-03: el inventario caído no tumba al resto', async () => {
    const { raiz } = await montar({
      consultarApi: secciones({
        '/inventario/usuarios/': (recurso) => conFallo(RESULTADO.SERVICIO_DEGRADADO, 503, recurso),
      }),
    });

    const inventario = panelDe(raiz, 'inventario-de-la-cuenta');
    expect(inventario.querySelector('.panel-admin__estado').textContent).toBe('Sin conexión');
    expect(inventario.querySelector('[data-accion="reintentar"]')).not.toBeNull();
    expect(panelDe(raiz, 'saldo').querySelector('.indicador-admin')).not.toBeNull();
  });
});

describe('SA-3 — acciones administrativas sobre la cuenta (solo Super Administrador)', () => {
  test('el Administrador no pide la auditoría ni ve su panel', async () => {
    const { raiz, consultarApi } = await montar({ sesion: ADMIN });

    expect(
      consultarApi.mock.calls.map((c) => c[0]).some((r) => r.startsWith('/admin/auditoria')),
    ).toBe(false);
    expect(panelDe(raiz, 'auditoria')).toBeNull();
  });

  test('el Super Administrador la pide por el uid y la clave de la cuenta, lo más reciente primero', async () => {
    const { raiz, consultarApi } = await montar({ sesion: SUPER });

    const pedida = consultarApi.mock.calls
      .map((c) => c[0])
      .find((r) => r.startsWith('/admin/auditoria'));
    const consulta = new URLSearchParams(pedida.split('?')[1]);
    expect(consulta.getAll('afectado')).toEqual([UID, '15', 'usuario-15']);
    expect(consulta.get('sort')).toBe('fechaHora,desc');
    expect(consulta.get('size')).toBe('20');

    const auditoria = panelDe(raiz, 'auditoria');
    const filas = [...auditoria.querySelectorAll('tbody tr')].map((tr) => tr.textContent);
    expect(filas).toHaveLength(3);
    expect(filas[0]).toContain('Otra acción');
    expect(filas[0]).toContain('Consulta de la ficha administrativa');
    expect(filas[1]).toContain('Sanción');
    expect(filas[2]).toContain('Modificación');
    expect(filas[2]).toContain('apodo=ana_r → apodo=Ana');
    expect(filas[2]).toContain('ada_admin');
  });

  test('sin el permiso de auditoría el panel lo dice sin ofrecer reintentar', async () => {
    const { raiz } = await montar({
      sesion: SUPER,
      consultarApi: secciones({
        '/admin/auditoria': (recurso) => conFallo(RESULTADO.SIN_PERMISO, 403, recurso),
      }),
    });

    const auditoria = panelDe(raiz, 'auditoria');
    expect(auditoria.querySelector('.panel-admin__estado').textContent).toBe('Sin acceso');
    expect(auditoria.querySelector('[data-accion="reintentar"]')).toBeNull();
  });
});

describe('SA-3 — el historial completo de actividad', () => {
  test('la línea une partidas, créditos, reportes, comentarios y sanciones', async () => {
    const { raiz } = await montar();

    const titulos = [
      ...panelDe(raiz, 'linea-de-tiempo').querySelectorAll('.linea-tiempo__titulo'),
    ].map((t) => t.textContent);
    expect(titulos).toEqual(
      expect.arrayContaining([
        'Empezó una partida',
        'Jugó una partida',
        'Recompensa por ganar',
        'Apuesta de una batalla',
        'Reportó un comentario',
        'Un comentario suyo fue reportado',
        'Publicó un comentario',
        'Suspensión emitida',
      ]),
    );
  });

  test('con la auditoría, las acciones administrativas también, sin repetir las sanciones', async () => {
    const { raiz } = await montar({ sesion: SUPER });

    const linea = panelDe(raiz, 'linea-de-tiempo');
    const titulos = [...linea.querySelectorAll('.linea-tiempo__titulo')].map((t) => t.textContent);
    expect(titulos).toContain('Modificación');
    expect(titulos).toContain('Otra acción');
    // La sanción la cuenta moderación (con su vigencia): la auditoría no la repite.
    expect(titulos.filter((t) => t === 'Sanción')).toHaveLength(0);
    expect(titulos.filter((t) => t === 'Suspensión emitida')).toHaveLength(1);
  });

  test('se filtra por tipo de hecho y se vuelve a ver todo', async () => {
    const { raiz } = await montar();

    const linea = panelDe(raiz, 'linea-de-tiempo');
    const filtro = linea.querySelector('select[name="familia"]');
    filtro.value = 'creditos';
    filtro.dispatchEvent(new Event('change'));
    const deCreditos = [...linea.querySelectorAll('.linea-tiempo__hecho')];
    expect(deCreditos).toHaveLength(2);
    expect(deCreditos.every((hecho) => hecho.dataset.categoria === 'creditos')).toBe(true);

    filtro.value = '';
    filtro.dispatchEvent(new Event('change'));
    expect(linea.querySelectorAll('.linea-tiempo__hecho').length).toBeGreaterThan(10);
  });

  test('si faltan varias secciones lo dice con sus nombres', async () => {
    const caido = (recurso) => conFallo(RESULTADO.SERVICIO_DEGRADADO, 503, recurso);
    const { raiz } = await montar({
      consultarApi: secciones({
        '/partidas/jugadores/': caido,
        [`/creditos/${UID}/movimientos`]: caido,
      }),
    });

    const linea = panelDe(raiz, 'linea-de-tiempo');
    expect(linea.textContent).toContain('Faltan los hechos de: partidas, créditos');
  });

  test('los hechos de cada fuente, sin inventar fechas ni quién reportó', () => {
    expect(hechosDePartidas(PARTIDAS).map((h) => h.titulo)).toEqual([
      'Empezó una partida',
      'Jugó una partida',
    ]);
    expect(hechosDePartidas(PARTIDAS)[1].cuando).toBe('2026-10-01T18:12:00Z');
    expect(hechosDePartidas(null)).toEqual([]);

    expect(hechosDeMovimientos(MOVIMIENTOS)[0].detalle).toBe('+40 créditos');
    expect(hechosDeMovimientos(MOVIMIENTOS)[1].detalle).toBe('120 créditos apartados');
    expect(hechosDeMovimientos({ content: [{ monto: 1 }] })).toEqual([]);

    const recibidos = hechosDeReportes(REPORTES_RECIBIDOS, 'RECIBIDOS');
    expect(recibidos).toHaveLength(2);
    expect(recibidos[0].detalle).toContain('Acoso a otra persona');
    expect(JSON.stringify(recibidos)).not.toMatch(/reportante/i);
    expect(hechosDeReportes(null, 'REALIZADOS')).toEqual([]);

    const administrativas = hechosDeAuditoria(AUDITORIA);
    expect(administrativas.map((h) => h.titulo)).toEqual(['Otra acción', 'Modificación']);
    expect(administrativas[1].actor).toBe('Por ada_admin');
  });
});
