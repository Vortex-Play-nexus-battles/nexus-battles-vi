/**
 * La consola de sanciones — SA-4 (§7.3.2, §7.3.8; informe, puntos 12 y 20):
 * lo que se calcula, y la consola montada contra un servidor simulado con la
 * forma de moderacion-sanciones-admin 1.4.0 y ms-identidad-admin 1.9.0.
 */

import { jest } from '@jest/globals';

import {
  consecuenciasDeLaEmision,
  duracionEnHoras,
  duracionLegible,
  estadoDeLaSancion,
  inicioDelMes,
  instanteDelDia,
  montarConsolaDeSanciones,
  motivoDeDuracionInvalida,
  OPCIONES_DE_CAUSAL,
  plazoDelPanel,
  POR_PAGINA,
  rangoDeLaSuspension,
  recursoDelListado,
  solicitudDeEmision,
} from './consola-sanciones.js';

const AHORA = Date.parse('2026-10-14T15:00:00Z');
const DIA = 24 * 60 * 60 * 1000;
const ANA = '11111111-1111-4111-8111-111111111111';
const BETO = '22222222-2222-4222-8222-222222222222';
const CRIS = '33333333-3333-4333-8333-333333333333';

const LIMITES = {
  suspensionMinimaHoras: 1,
  suspensionMaximaHoras: 720,
  suspensionMaximaDias: 30,
  apelacionPlazoDias: 30,
};

const TITULARES = {
  [ANA]: { uid: ANA, apodo: 'Ana', estado: 'SUSPENDIDO', rol: 'JUGADOR' },
  [BETO]: { uid: BETO, apodo: 'Beto', estado: 'ACTIVO', rol: 'JUGADOR' },
  [CRIS]: { uid: CRIS, apodo: 'Cris', estado: 'BANEADO', rol: 'JUGADOR' },
};

const sancion = (extra = {}) => ({
  id: 'aaaaaaaa-0000-4000-8000-000000000001',
  usuarioId: ANA,
  tipo: 'SUSPENSION',
  causal: 'ACOSO',
  motivo: 'Insultó a otro jugador en la sala 12',
  politica: null,
  comentarioId: null,
  emitidaPor: '99999999-9999-4999-8999-999999999999',
  rolEmisor: 'MODERADOR',
  emitidaEn: '2026-10-13T15:00:00Z',
  vigenteHasta: '2026-10-16T15:00:00Z',
  revertidaEn: null,
  motivoReversion: null,
  vigente: true,
  ...extra,
});

const SUSPENSION_DE_ANA = sancion();
const ADVERTENCIA_DE_BETO = sancion({
  id: 'aaaaaaaa-0000-4000-8000-000000000002',
  usuarioId: BETO,
  tipo: 'ADVERTENCIA',
  causal: 'LENGUAJE_OFENSIVO',
  vigenteHasta: null,
});
const BANEO_DE_CRIS = sancion({
  id: 'aaaaaaaa-0000-4000-8000-000000000003',
  usuarioId: CRIS,
  tipo: 'BANEO',
  causal: 'FRAUDE',
  rolEmisor: 'ADMINISTRADOR',
  vigenteHasta: null,
});

const apelacion = (extra = {}) => ({
  id: 'bbbbbbbb-0000-4000-8000-000000000001',
  sancionId: SUSPENSION_DE_ANA.id,
  usuarioId: ANA,
  argumento: 'Fue una broma entre amigos, no un insulto.',
  creadaEn: '2026-10-13T16:00:00Z',
  estado: 'PENDIENTE',
  decisionMotivo: null,
  resueltaPor: null,
  resueltaEn: null,
  nuevaVigencia: null,
  decisionAntesDe: '2026-11-03T04:59:59.999Z',
  vencida: false,
  ...extra,
});

const pagina = (contenido, total = contenido.length) => ({
  contenido,
  pagina: 0,
  tamano: POR_PAGINA,
  total,
  totalPaginas: Math.max(1, Math.ceil(total / POR_PAGINA)),
});

/* ------------------------------------------------------------------ puro */

describe('lo que se calcula', () => {
  test('el estado de una sanción: revertida antes que nada, luego vigente o cumplida', () => {
    expect(estadoDeLaSancion(sancion())).toBe('VIGENTE');
    expect(estadoDeLaSancion(sancion({ vigente: false }))).toBe('CUMPLIDA');
    expect(
      estadoDeLaSancion(sancion({ vigente: false, revertidaEn: '2026-10-14T10:00:00Z' })),
    ).toBe('REVERTIDA');
  });

  test('la duración en horas: entera y positiva, en horas, días o semanas', () => {
    expect(duracionEnHoras('3', 'DIAS')).toBe(72);
    expect(duracionEnHoras(2, 'SEMANAS')).toBe(336);
    expect(duracionEnHoras(5, 'HORAS')).toBe(5);
    expect(duracionEnHoras('1.5', 'DIAS')).toBeNull();
    expect(duracionEnHoras(0, 'HORAS')).toBeNull();
    expect(duracionEnHoras(-2, 'HORAS')).toBeNull();
    expect(duracionEnHoras(2, 'MESES')).toBeNull();
  });

  test('el rango que aplica hoy el servicio, en horas; sin dato, ninguno', () => {
    expect(rangoDeLaSuspension(LIMITES)).toEqual({ minimo: 1, maximo: 720 });
    expect(rangoDeLaSuspension({ suspensionMinimaHoras: 2, suspensionMaximaDias: 7 })).toEqual({
      minimo: 2,
      maximo: 168,
    });
    expect(rangoDeLaSuspension(null)).toBeNull();
    expect(rangoDeLaSuspension({})).toBeNull();
    expect(
      rangoDeLaSuspension({ suspensionMinimaHoras: 48, suspensionMaximaHoras: 24 }),
    ).toBeNull();
  });

  test('una duración dicha en la unidad más grande que la divide', () => {
    expect(duracionLegible(1)).toBe('1 hora');
    expect(duracionLegible(30)).toBe('30 horas');
    expect(duracionLegible(24)).toBe('1 día');
    expect(duracionLegible(720)).toBe('30 días');
    expect(duracionLegible(168)).toBe('1 semana');
    expect(duracionLegible(336)).toBe('2 semanas');
    expect(duracionLegible(0)).toBe('');
  });

  test('por qué no se puede emitir una duración, con el rango en palabras', () => {
    const rango = rangoDeLaSuspension(LIMITES);
    expect(motivoDeDuracionInvalida(null, rango)).toBe(
      'Escribe la duración como un número entero mayor que cero.',
    );
    expect(motivoDeDuracionInvalida(31 * 24, rango)).toBe(
      'La suspensión tiene que durar entre 1 hora y 30 días.',
    );
    expect(motivoDeDuracionInvalida(72, rango)).toBeNull();
    // Sin rango conocido no se inventa uno: decide el servicio.
    expect(motivoDeDuracionInvalida(10_000, null)).toBeNull();
  });

  test('un día del filtro es el principio de ese día en Colombia; «hasta» cuenta el día entero', () => {
    expect(instanteDelDia('2026-10-05')).toBe('2026-10-05T00:00:00-05:00');
    expect(instanteDelDia('2026-10-05', { finDelDia: true })).toBe('2026-10-06T00:00:00-05:00');
    expect(instanteDelDia('2026-10-31', { finDelDia: true })).toBe('2026-11-01T00:00:00-05:00');
    expect(instanteDelDia('2026-12-31', { finDelDia: true })).toBe('2027-01-01T00:00:00-05:00');
    expect(instanteDelDia('05/10/2026')).toBeNull();
    expect(instanteDelDia('')).toBeNull();
  });

  test('el mes en curso se cuenta en hora de Colombia', () => {
    expect(inicioDelMes(AHORA)).toBe('2026-10-01T00:00:00-05:00');
    // El 1 de octubre a las 3:00 UTC todavía es 30 de septiembre en Colombia.
    expect(inicioDelMes(Date.parse('2026-10-01T03:00:00Z'))).toBe('2026-09-01T00:00:00-05:00');
  });

  test('la consulta del listado lleva solo los filtros puestos, la cuenta y la página', () => {
    expect(recursoDelListado({})).toBe('/sanciones?pagina=0&tamano=16');
    const recurso = recursoDelListado(
      {
        tipo: 'SUSPENSION',
        causal: 'ACOSO',
        estado: 'VIGENTE',
        desde: '2026-10-01',
        hasta: '2026-10-14',
      },
      { usuarioId: ANA, pagina: 2 },
    );
    const parametros = new URL(recurso, 'http://x').searchParams;
    expect(Object.fromEntries(parametros)).toEqual({
      tipo: 'SUSPENSION',
      causal: 'ACOSO',
      estado: 'VIGENTE',
      usuarioId: ANA,
      desde: '2026-10-01T00:00:00-05:00',
      hasta: '2026-10-15T00:00:00-05:00',
      pagina: '2',
      tamano: '16',
    });
  });

  test('el plazo del panel: cuántos días quedan, si vence hoy o si ya se pasó', () => {
    expect(plazoDelPanel(apelacion(), AHORA)).toEqual({ texto: 'Quedan 20 días', tono: 'info' });
    expect(
      plazoDelPanel(
        apelacion({ decisionAntesDe: new Date(AHORA + 2.5 * DIA).toISOString() }),
        AHORA,
      ),
    ).toEqual({
      texto: 'Quedan 3 días',
      tono: 'advertencia',
    });
    expect(
      plazoDelPanel(
        apelacion({ decisionAntesDe: new Date(AHORA + 3600_000).toISOString() }),
        AHORA,
      ),
    ).toEqual({
      texto: 'Vence hoy',
      tono: 'advertencia',
    });
    expect(plazoDelPanel(apelacion({ vencida: true }), AHORA)).toEqual({
      texto: 'Fuera de plazo',
      tono: 'error',
    });
    expect(plazoDelPanel(apelacion({ estado: 'MANTENIDA', vencida: true }), AHORA).texto).toBe(
      'Resuelta',
    );
    expect(plazoDelPanel(apelacion({ decisionAntesDe: null }), AHORA).texto).toBe(
      'Sin fecha límite',
    );
  });

  test('la solicitud de emisión: causal siempre; duración solo en la suspensión; confirmación en el baneo', () => {
    const titular = { uid: ANA };
    expect(
      solicitudDeEmision(
        { tipo: 'ADVERTENCIA', causal: 'ACOSO', motivo: '  Spam en el chat  ', politica: ' ' },
        titular,
      ),
    ).toEqual({ usuarioId: ANA, tipo: 'ADVERTENCIA', causal: 'ACOSO', motivo: 'Spam en el chat' });
    expect(
      solicitudDeEmision(
        {
          tipo: 'SUSPENSION',
          causal: 'ACOSO',
          motivo: 'x',
          cantidad: '3',
          unidad: 'DIAS',
          politica: 'Normas',
        },
        titular,
        { comentarioId: 'c-7' },
      ),
    ).toEqual({
      usuarioId: ANA,
      tipo: 'SUSPENSION',
      causal: 'ACOSO',
      motivo: 'x',
      politica: 'Normas',
      comentarioId: 'c-7',
      duracionHoras: 72,
    });
    expect(
      solicitudDeEmision({ tipo: 'BANEO', causal: 'FRAUDE', motivo: 'Duplicó créditos' }, titular),
    ).toEqual({
      usuarioId: ANA,
      tipo: 'BANEO',
      causal: 'FRAUDE',
      motivo: 'Duplicó créditos',
      confirmacion: true,
    });
  });

  test('lo que va a pasar, en frases, según el tipo', () => {
    expect(consecuenciasDeLaEmision('ADVERTENCIA')[0]).toMatch(/no restringe su acceso/);
    const suspension = consecuenciasDeLaEmision('SUSPENSION', { horas: 72, ahora: AHORA });
    expect(suspension[0]).toMatch(/^No podrá entrar al juego hasta el /);
    expect(suspension[1]).toMatch(/inventario y su progreso se conservan/);
    expect(consecuenciasDeLaEmision('BANEO')).toEqual(
      expect.arrayContaining([
        expect.stringMatching(/inventario queda congelado/),
        expect.stringMatching(/apelación/),
      ]),
    );
  });

  test('las ocho causales de §7.3.2, en su orden', () => {
    expect(OPCIONES_DE_CAUSAL.map(([valor]) => valor)).toEqual([
      'LENGUAJE_OFENSIVO',
      'ACOSO',
      'FRAUDE',
      'SOFTWARE_NO_AUTORIZADO',
      'SUPLANTACION_DE_IDENTIDAD',
      'VIOLACION_DE_PRIVACIDAD',
      'INCUMPLIMIENTO_DE_TERMINOS',
      'TERMINACION_DE_RELACION_LEGAL',
    ]);
  });
});

/* -------------------------------------------------------- el servidor falso */

/**
 * Un servidor con las rutas que usa la consola. Cada prueba cambia lo que
 * necesita; todas las peticiones quedan en `peticiones`.
 */
function servidorFalso(cambios = {}) {
  const estado = {
    listado: [SUSPENSION_DE_ANA, ADVERTENCIA_DE_BETO, BANEO_DE_CRIS],
    vigentes: 7,
    delMes: 3,
    pendientes: [
      apelacion(),
      apelacion({
        id: 'bbbbbbbb-0000-4000-8000-000000000002',
        vencida: true,
        decisionAntesDe: '2026-10-10T04:59:59.999Z',
        usuarioId: BETO,
        sancionId: ADVERTENCIA_DE_BETO.id,
      }),
    ],
    resueltas: [],
    historial: {
      [ANA]: [SUSPENSION_DE_ANA],
      [BETO]: [ADVERTENCIA_DE_BETO],
      [CRIS]: [BANEO_DE_CRIS],
    },
    buscar: (texto) =>
      Object.values(TITULARES).filter((t) => t.apodo.toLowerCase().includes(texto.toLowerCase())),
    ...cambios,
  };
  const peticiones = [];
  const responder = (metodo, url, cuerpo) => {
    const { pathname, searchParams } = url;
    const sinVersion = pathname.replace(/^\/api\/v1/, '');
    if (metodo === 'GET' && sinVersion === '/sanciones') {
      if (searchParams.get('estado') === 'VIGENTE' && searchParams.get('tamano') === '1') {
        return [200, pagina(estado.vigentes ? [SUSPENSION_DE_ANA] : [], estado.vigentes)];
      }
      if (searchParams.get('desde') && searchParams.get('tamano') === '1') {
        return [200, pagina(estado.delMes ? [SUSPENSION_DE_ANA] : [], estado.delMes)];
      }
      let filas = estado.listado;
      if (searchParams.get('tipo')) {
        filas = filas.filter((s) => s.tipo === searchParams.get('tipo'));
      }
      if (searchParams.get('usuarioId')) {
        filas = filas.filter((s) => s.usuarioId === searchParams.get('usuarioId'));
      }
      return [200, pagina(filas)];
    }
    if (metodo === 'GET' && sinVersion === '/apelaciones') {
      return [
        200,
        searchParams.get('estado') === 'RESUELTAS' ? estado.resueltas : estado.pendientes,
      ];
    }
    if (metodo === 'GET' && sinVersion === '/sanciones/limites') {
      return [200, LIMITES];
    }
    if (metodo === 'GET' && sinVersion.startsWith('/sanciones/usuarios/')) {
      return [200, estado.historial[sinVersion.split('/').pop()] ?? []];
    }
    if (metodo === 'GET' && /^\/sanciones\/[0-9a-f-]{36}$/.test(sinVersion)) {
      const encontrada = [SUSPENSION_DE_ANA, ADVERTENCIA_DE_BETO, BANEO_DE_CRIS].find(
        (s) => s.id === sinVersion.split('/').pop(),
      );
      return encontrada
        ? [200, encontrada]
        : [404, { title: 'No encontrada', detail: 'No existe esa sanción.' }];
    }
    if (metodo === 'GET' && sinVersion === '/admin/titulares') {
      if (estado.titularesCaidos) {
        return [503, { title: 'No disponible', detail: 'Identidad no responde.' }];
      }
      if (searchParams.get('uids')) {
        return [
          200,
          searchParams
            .get('uids')
            .split(',')
            .map((u) => TITULARES[u])
            .filter(Boolean),
        ];
      }
      return [200, estado.buscar(searchParams.get('buscar') ?? '')];
    }
    if (metodo === 'POST' && sinVersion === '/sanciones') {
      return estado.alEmitir ? estado.alEmitir(cuerpo) : [201, sancion({ ...cuerpo, id: 'nueva' })];
    }
    if (metodo === 'POST' && sinVersion.endsWith('/levantamiento')) {
      return [
        200,
        {
          ...SUSPENSION_DE_ANA,
          vigente: false,
          revertidaEn: '2026-10-14T15:00:00Z',
          motivoReversion: cuerpo.motivo,
        },
      ];
    }
    if (metodo === 'POST' && sinVersion.endsWith('/resolucion')) {
      return [200, { ...apelacion(), estado: cuerpo.decision, decisionMotivo: cuerpo.motivo }];
    }
    return [404, { title: 'Sin ruta', detail: `${metodo} ${pathname}` }];
  };
  const fetchImpl = jest.fn(async (direccion, opciones = {}) => {
    const metodo = opciones.method ?? 'GET';
    const url = new URL(direccion, 'http://borde');
    const cuerpo = opciones.body ? JSON.parse(opciones.body) : null;
    peticiones.push({ metodo, url, cuerpo });
    const [codigo, datos] = responder(metodo, url, cuerpo);
    return {
      ok: codigo >= 200 && codigo < 300,
      status: codigo,
      headers: { get: () => null },
      json: async () => datos,
      text: async () => (datos === null ? '' : JSON.stringify(datos)),
    };
  });
  return { fetchImpl, peticiones, estado };
}

const MARCO = `
  <main>
    <div data-zona="encabezado"></div>
    <div data-zona="aviso" hidden></div>
    <div data-zona="indicadores"></div>
    <div data-zona="consola"></div>
  </main>`;

const asentar = () => new Promise((resolver) => setTimeout(resolver, 0));
async function esperar(condicion, vueltas = 40) {
  for (let i = 0; i < vueltas; i += 1) {
    if (condicion()) {
      return;
    }
    await asentar();
  }
  throw new Error('la condición no se cumplió');
}
const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => [...document.querySelectorAll(selector)];
const dialogo = () => $$('[role="dialog"]').at(-1);
function escribir(campo, valor) {
  campo.value = valor;
  campo.dispatchEvent(new Event('input', { bubbles: true }));
  campo.dispatchEvent(new Event('change', { bubbles: true }));
}

function montar(rol, servidor, { busqueda = '' } = {}) {
  window.location.hash = '';
  return montarConsolaDeSanciones(document, {
    rol,
    fetchImpl: servidor.fetchImpl,
    ahora: () => AHORA,
    busqueda,
  });
}

beforeEach(() => {
  document.body.innerHTML = MARCO;
  jest.spyOn(console, 'warn').mockImplementation(() => {});
});

afterEach(() => {
  $$('[role="dialog"]').forEach(
    (caja) => caja.closest('.dialogo-velo, .velo')?.remove() ?? caja.remove(),
  );
  jest.restoreAllMocks();
});

/* ---------------------------------------------------------------- montaje */

describe('la consola', () => {
  test('un jugador no la usa: se dice y no se consulta nada', () => {
    const servidor = servidorFalso();
    expect(montar('JUGADOR', servidor)).toBeNull();
    expect($('[data-zona="aviso"]').textContent).toContain('Esta consola es de moderación');
    expect(servidor.fetchImpl).not.toHaveBeenCalled();
  });

  test('las cuatro cifras son los totales del servicio, nunca inventadas', async () => {
    const servidor = servidorFalso();
    montar('MODERADOR', servidor);
    await esperar(
      () =>
        $$(
          '[data-zona="cifras-de-sanciones"] .indicador-admin, [data-zona="cifras-de-sanciones"] > *',
        ).length >= 4,
    );
    const texto = $('[data-zona="indicadores"]').textContent;
    expect(texto).toMatch(/Sanciones vigentes\s*7/);
    expect(texto).toMatch(/Emitidas este mes\s*3/);
    expect(texto).toMatch(/Apelaciones por resolver\s*2/);
    expect(texto).toMatch(/Fuera de plazo\s*1/);
    const delMes = servidor.peticiones.find(
      (p) => p.url.searchParams.get('desde') && p.url.searchParams.get('tamano') === '1',
    );
    expect(delMes.url.searchParams.get('desde')).toBe('2026-10-01T00:00:00-05:00');
  });

  test('si una consulta falla, su cifra dice «Sin dato», no cero', async () => {
    const servidor = servidorFalso();
    servidor.fetchImpl.mockImplementation(async (direccion) => {
      const url = new URL(direccion, 'http://borde');
      const caida = url.pathname.endsWith('/apelaciones');
      return {
        ok: !caida,
        status: caida ? 503 : 200,
        headers: { get: () => null },
        json: async () => ({}),
        text: async () => (caida ? '' : JSON.stringify(pagina([SUSPENSION_DE_ANA], 7))),
      };
    });
    montar('ADMINISTRADOR', servidor);
    await esperar(() =>
      /Apelaciones por resolver/.test($('[data-zona="indicadores"]').textContent),
    );
    const tarjeta = $$('[data-zona="indicadores"] *').find((n) =>
      n.textContent.startsWith('Apelaciones por resolver'),
    );
    expect(tarjeta.closest('[data-zona="cifras-de-sanciones"] > *').textContent).toContain(
      'Sin dato',
    );
  });

  test('el listado nombra cada cuenta por su apodo con una sola consulta a identidad', async () => {
    const servidor = servidorFalso();
    montar('MODERADOR', servidor);
    await esperar(() => $$('[data-panel="listado-de-sanciones"] tbody tr').length === 3);
    const filas = $$('[data-panel="listado-de-sanciones"] tbody tr');
    expect(filas.map((f) => f.querySelector('th').textContent)).toEqual(['Ana', 'Beto', 'Cris']);
    expect(filas[0].textContent).toContain('Acoso a otros jugadores');
    expect(filas[0].textContent).toContain('Vigente');
    expect(filas[0].textContent).toContain('Moderador');
    const nombrar = servidor.peticiones.filter((p) => p.url.pathname.endsWith('/admin/titulares'));
    expect(nombrar).toHaveLength(1);
    expect(nombrar[0].url.searchParams.get('uids').split(',').sort()).toEqual(
      [ANA, BETO, CRIS].sort(),
    );
    // El moderador no gestiona cuentas: el apodo no enlaza a la ficha.
    expect(filas[0].querySelector('th a')).toBeNull();
    // Nada de identificadores en pantalla.
    expect($('[data-panel="listado-de-sanciones"]').textContent).not.toContain(ANA);
  });

  test('a la administración el apodo la lleva a la ficha de la cuenta', async () => {
    const servidor = servidorFalso();
    montar('ADMINISTRADOR', servidor);
    await esperar(() => $$('[data-panel="listado-de-sanciones"] tbody th a').length === 3);
    const enlace = $('[data-panel="listado-de-sanciones"] tbody th a');
    expect(enlace.getAttribute('href')).toContain(`ficha-usuario.html?usuario=${ANA}`);
  });

  test('si identidad no responde, el listado sigue y se avisa que faltan los apodos', async () => {
    const servidor = servidorFalso({ titularesCaidos: true });
    montar('ADMINISTRADOR', servidor);
    await esperar(() => $$('[data-panel="listado-de-sanciones"] tbody tr').length === 3);
    expect($('[data-panel="listado-de-sanciones"] tbody th').textContent).toBe(
      'Cuenta sin identificar',
    );
    expect($('[data-zona="aviso"]').textContent).toContain('No se pudieron cargar los apodos');
  });

  test('un filtro va al servidor con la página 0 y dieciséis por página', async () => {
    const servidor = servidorFalso();
    montar('MODERADOR', servidor);
    await esperar(() => $$('[data-panel="listado-de-sanciones"] tbody tr').length === 3);
    const tipo = $('[data-panel="listado-de-sanciones"] select[name="tipo"]');
    escribir(tipo, 'BANEO');
    await esperar(() => $$('[data-panel="listado-de-sanciones"] tbody tr').length === 1);
    const ultima = servidor.peticiones.filter((p) => p.url.pathname === '/api/v1/sanciones').at(-1);
    expect(ultima.url.searchParams.get('tipo')).toBe('BANEO');
    expect(ultima.url.searchParams.get('pagina')).toBe('0');
    expect(ultima.url.searchParams.get('tamano')).toBe('16');
  });

  test('buscar una cuenta por su apodo filtra por su uid; si coinciden varias, se elige', async () => {
    const servidor = servidorFalso({
      buscar: (texto) => (texto === 'ana' ? [TITULARES[ANA]] : [TITULARES[ANA], TITULARES[BETO]]),
    });
    montar('MODERADOR', servidor);
    await esperar(() => $$('[data-panel="listado-de-sanciones"] tbody tr').length === 3);
    const cuenta = $('[data-panel="listado-de-sanciones"] input[name="cuenta"]');
    const formulario = cuenta.closest('form');

    escribir(cuenta, 'ana');
    formulario.requestSubmit();
    await esperar(() => $$('[data-panel="listado-de-sanciones"] tbody tr').length === 1);
    expect(
      servidor.peticiones
        .filter((p) => p.url.pathname === '/api/v1/sanciones')
        .at(-1)
        .url.searchParams.get('usuarioId'),
    ).toBe(ANA);

    escribir(cuenta, 'varias');
    formulario.requestSubmit();
    await esperar(() => $$('[data-accion="elegir-cuenta"]').length === 2);
    $$('[data-accion="elegir-cuenta"]')[1].click();
    await esperar(
      () =>
        $$('[data-panel="listado-de-sanciones"] tbody th')
          .map((t) => t.textContent)
          .join() === 'Beto',
    );
    expect(cuenta.value).toBe('Beto');
  });

  test('una cuenta que no existe se dice con su texto', async () => {
    const servidor = servidorFalso({ buscar: () => [] });
    montar('MODERADOR', servidor);
    await esperar(() => $$('[data-panel="listado-de-sanciones"] tbody tr').length === 3);
    const cuenta = $('[data-panel="listado-de-sanciones"] input[name="cuenta"]');
    escribir(cuenta, 'nadie');
    cuenta.closest('form').requestSubmit();
    await esperar(() =>
      /Ninguna cuenta coincide con «nadie»/.test(
        $('[data-panel="listado-de-sanciones"]').textContent,
      ),
    );
  });

  test('el detalle de una suspensión vigente deja a la administración levantarla, con motivo', async () => {
    const servidor = servidorFalso();
    montar('ADMINISTRADOR', servidor);
    await esperar(() => $$('[data-accion="ver-sancion"]').length === 3);
    $$('[data-accion="ver-sancion"]')[0].click();
    expect(dialogo().textContent).toContain('Insultó a otro jugador en la sala 12');
    expect(dialogo().textContent).toContain('Acoso a otros jugadores');
    dialogo().querySelector('[data-accion="levantar"]').click();
    await asentar();
    const confirmacion = dialogo();
    expect(confirmacion.textContent).toContain('¿Levantar la suspensión de Ana?');
    const boton = confirmacion.querySelector('[data-accion="confirmar"]');
    expect(boton.disabled).toBe(true);
    escribir(confirmacion.querySelector('textarea'), 'La sanción se emitió por error');
    expect(boton.disabled).toBe(false);
    boton.click();
    await esperar(() => servidor.peticiones.some((p) => p.metodo === 'POST'));
    const levantamiento = servidor.peticiones.find((p) => p.metodo === 'POST');
    expect(levantamiento.url.pathname).toBe(
      `/api/v1/sanciones/${SUSPENSION_DE_ANA.id}/levantamiento`,
    );
    expect(levantamiento.cuerpo).toEqual({ motivo: 'La sanción se emitió por error' });
    await esperar(() => /Suspensión levantada/.test($('[data-zona="aviso"]').textContent));
  });

  test('un baneo no se levanta a mano, ni siquiera para la administración', async () => {
    const servidor = servidorFalso();
    montar('SUPER_ADMINISTRADOR', servidor);
    await esperar(() => $$('[data-accion="ver-sancion"]').length === 3);
    $$('[data-accion="ver-sancion"]')[2].click();
    expect(dialogo().querySelector('[data-accion="levantar"]')).toBeNull();
    expect(dialogo().textContent).toContain('solo lo revierte una apelación aceptada por el panel');
  });

  test('el moderador ve el detalle pero no levanta nada', async () => {
    const servidor = servidorFalso();
    montar('MODERADOR', servidor);
    await esperar(() => $$('[data-accion="ver-sancion"]').length === 3);
    $$('[data-accion="ver-sancion"]')[0].click();
    expect(dialogo().querySelector('[data-accion="levantar"]')).toBeNull();
    expect(dialogo().querySelector('[data-accion="emitir-a-la-cuenta"]')).not.toBeNull();
  });
});

/* ------------------------------------------------------------- apelaciones */

describe('apelaciones', () => {
  async function abrirApelaciones(rol = 'MODERADOR', servidor = servidorFalso()) {
    montar(rol, servidor);
    $('[data-pestana="apelaciones"]').click();
    await esperar(() => $$('[data-panel="apelaciones"] tbody tr').length === 2);
    return servidor;
  }

  test('primero la que vence antes, con su plazo; la vencida va marcada', async () => {
    await abrirApelaciones();
    const filas = $$('[data-panel="apelaciones"] tbody tr');
    expect(filas[0].querySelector('th').textContent).toBe('Beto');
    expect(filas[0].textContent).toContain('Fuera de plazo');
    expect(filas[1].textContent).toContain('Quedan 20 días');
    const apelada = filas[1].querySelector('[data-columna="sancion"] .celda-doble');
    expect([...apelada.children].map((n) => n.textContent)).toEqual([
      'Suspensión',
      'Acoso a otros jugadores',
    ]);
  });

  test('resolver pide decisión y motivación; reducir pide la nueva fecha fin', async () => {
    const servidor = await abrirApelaciones();
    $$('[data-panel="apelaciones"] [data-accion="resolver"]')[1].click();
    const panelLateral = dialogo();
    expect(panelLateral.textContent).toContain('Fue una broma entre amigos, no un insulto.');
    const enviar = panelLateral.querySelector('[data-accion="enviar-resolucion"]');

    enviar.click();
    expect(panelLateral.querySelector('[data-zona="error-resolucion"]').textContent).toBe(
      'Elige la decisión.',
    );

    escribir(panelLateral.querySelector('select[name="decision"]'), 'REDUCIDA');
    escribir(
      panelLateral.querySelector('textarea[name="motivo"]'),
      'Primera falta: se reduce a dos días',
    );
    enviar.click();
    expect(panelLateral.querySelector('[data-zona="error-resolucion"]').textContent).toBe(
      'Indica la nueva fecha fin de la suspensión.',
    );

    escribir(panelLateral.querySelector('input[name="nuevaVigencia"]'), '2026-10-15T10:00');
    enviar.click();
    await asentar();
    dialogo().querySelector('[data-accion="confirmar"]').click();
    await esperar(() => servidor.peticiones.some((p) => p.metodo === 'POST'));
    const resolucion = servidor.peticiones.find((p) => p.metodo === 'POST');
    expect(resolucion.url.pathname).toBe(`/api/v1/apelaciones/${apelacion().id}/resolucion`);
    expect(resolucion.cuerpo).toEqual({
      decision: 'REDUCIDA',
      motivo: 'Primera falta: se reduce a dos días',
      nuevaVigencia: new Date('2026-10-15T10:00').toISOString(),
    });
    await esperar(() => /Apelación resuelta/.test($('[data-zona="aviso"]').textContent));
  });

  test('reducir solo existe para una suspensión', async () => {
    await abrirApelaciones();
    // La de Beto apela una advertencia.
    $$('[data-panel="apelaciones"] [data-accion="resolver"]')[0].click();
    const opciones = [...dialogo().querySelectorAll('select[name="decision"] option')].map(
      (o) => o.value,
    );
    expect(opciones).toEqual(['', 'MANTENIDA', 'REVERTIDA']);
  });

  test('sin pendientes se dice', async () => {
    const servidor = servidorFalso({ pendientes: [] });
    montar('MODERADOR', servidor);
    $('[data-pestana="apelaciones"]').click();
    await esperar(() =>
      /No hay apelaciones por resolver/.test($('[data-panel="apelaciones"]').textContent),
    );
  });
});

/* ------------------------------------------------------------------ emitir */

describe('emitir', () => {
  async function abrirConAna(rol = 'ADMINISTRADOR', servidor = servidorFalso()) {
    montar(rol, servidor, { busqueda: `?usuario=${ANA}` });
    await esperar(() => $('[data-zona="apodo-elegido"]')?.textContent === 'Ana');
    await esperar(
      () =>
        $(
          '[data-panel="emitir"] [data-zona="historial"] .linea-tiempo, [data-panel="emitir"] [data-zona="historial"] ol',
        ) !== null,
    );
    return servidor;
  }
  const formulario = () => $('[data-panel="emitir"] form[data-zona="emitir"]');
  const control = (nombre) => formulario().querySelector(`[name="${nombre}"]`);

  test('llegar con ?usuario= abre «Emitir sanción» con la cuenta, su estado y su historial', async () => {
    await abrirConAna();
    expect($('[data-pestana="emitir"]').getAttribute('aria-selected')).toBe('true');
    expect($('[data-panel="emitir"] [data-zona="cuenta-elegida"]').textContent).toContain(
      'Suspendida',
    );
    expect(formulario().hidden).toBe(false);
    expect($('[data-panel="emitir"] [data-accion="abrir-ficha"]').getAttribute('href')).toContain(
      `usuario=${ANA}`,
    );
  });

  test('sin causal, sin duración válida o sin motivo, no sale nada', async () => {
    const servidor = await abrirConAna();
    const error = () => formulario().querySelector('[data-zona="error-emision"]').textContent;
    escribir(control('tipo'), 'SUSPENSION');
    formulario().requestSubmit();
    expect(error()).toMatch(/Elige la causal/);

    escribir(control('causal'), 'ACOSO');
    escribir(control('cantidad'), '31');
    escribir(control('unidad'), 'DIAS');
    formulario().requestSubmit();
    expect(error()).toBe('La suspensión tiene que durar entre 1 hora y 30 días.');

    escribir(control('cantidad'), '3');
    escribir(control('motivo'), 'corto');
    formulario().requestSubmit();
    expect(error()).toMatch(/al menos 10 caracteres/);
    expect(servidor.peticiones.some((p) => p.metodo === 'POST')).toBe(false);
  });

  test('una suspensión de tres días se confirma con lo que va a pasar y sale en horas', async () => {
    const servidor = await abrirConAna();
    escribir(control('tipo'), 'SUSPENSION');
    escribir(control('causal'), 'ACOSO');
    escribir(control('cantidad'), '3');
    escribir(control('unidad'), 'DIAS');
    escribir(control('motivo'), 'Insultó a otro jugador en la sala 12');
    escribir(control('politica'), 'Normas de convivencia');
    expect($('[data-panel="emitir"] [data-campo="rango-suspension"]').textContent).toBe(
      'Entre 1 hora y 30 días.',
    );
    formulario().requestSubmit();
    await asentar();
    const confirmacion = dialogo();
    expect(confirmacion.textContent).toContain('¿Suspender a Ana durante 3 días?');
    expect(confirmacion.textContent).toContain('Causal: Acoso a otros jugadores.');
    expect(confirmacion.textContent).toMatch(/inventario y su progreso se conservan/);
    confirmacion.querySelector('[data-accion="confirmar"]').click();
    await esperar(() => servidor.peticiones.some((p) => p.metodo === 'POST'));
    expect(servidor.peticiones.find((p) => p.metodo === 'POST').cuerpo).toEqual({
      usuarioId: ANA,
      tipo: 'SUSPENSION',
      causal: 'ACOSO',
      motivo: 'Insultó a otro jugador en la sala 12',
      politica: 'Normas de convivencia',
      duracionHoras: 72,
    });
    await esperar(() => /Sanción emitida/.test($('[data-zona="aviso"]').textContent));
    expect(control('motivo').value).toBe('');
  });

  test('cancelar la confirmación no emite', async () => {
    const servidor = await abrirConAna();
    escribir(control('tipo'), 'ADVERTENCIA');
    escribir(control('causal'), 'LENGUAJE_OFENSIVO');
    escribir(control('motivo'), 'Lenguaje ofensivo en el chat general');
    formulario().requestSubmit();
    await asentar();
    expect(dialogo().textContent).toContain('¿Advertir a Ana?');
    dialogo().querySelector('[data-accion="cancelar"]').click();
    await asentar();
    expect(servidor.peticiones.some((p) => p.metodo === 'POST')).toBe(false);
  });

  test('el baneo pide escribir el apodo antes de salir', async () => {
    const servidor = await abrirConAna('SUPER_ADMINISTRADOR');
    escribir(control('tipo'), 'BANEO');
    escribir(control('causal'), 'FRAUDE');
    escribir(control('motivo'), 'Duplicó créditos con un programa externo');
    formulario().requestSubmit();
    await asentar();
    const confirmacion = dialogo();
    expect(confirmacion.textContent).toContain('Banear definitivamente a Ana');
    const boton = confirmacion.querySelector('[data-accion="confirmar"]');
    expect(boton.disabled).toBe(true);
    escribir(confirmacion.querySelector('input'), 'Ana');
    expect(boton.disabled).toBe(false);
    boton.click();
    await esperar(() => servidor.peticiones.some((p) => p.metodo === 'POST'));
    expect(servidor.peticiones.find((p) => p.metodo === 'POST').cuerpo).toMatchObject({
      tipo: 'BANEO',
      causal: 'FRAUDE',
      confirmacion: true,
    });
  });

  test('el moderador no tiene el baneo entre las opciones', async () => {
    await abrirConAna('MODERADOR');
    const baneo = control('tipo').querySelector('option[value="BANEO"]');
    expect(baneo.disabled).toBe(true);
    expect(baneo.textContent).toContain('solo administración');
    expect($('[data-panel="emitir"] [data-accion="abrir-ficha"]')).toBeNull();
  });

  test('el rechazo del servicio se dice en el formulario, con su texto', async () => {
    const servidor = servidorFalso({
      alEmitir: () => [
        403,
        {
          title: 'Permiso insuficiente',
          detail: 'Un moderador no puede suspender a un administrador.',
        },
      ],
    });
    await abrirConAna('MODERADOR', servidor);
    escribir(control('tipo'), 'ADVERTENCIA');
    escribir(control('causal'), 'ACOSO');
    escribir(control('motivo'), 'Mensajes de acoso por privado');
    formulario().requestSubmit();
    await asentar();
    dialogo().querySelector('[data-accion="confirmar"]').click();
    await esperar(() =>
      /Un moderador no puede suspender a un administrador/.test(formulario().textContent),
    );
  });

  test('buscar por apodo: una cuenta se elige sola; varias se enseñan para elegir', async () => {
    const servidor = servidorFalso({
      buscar: (texto) => (texto === 'beto' ? [TITULARES[BETO]] : [TITULARES[ANA], TITULARES[CRIS]]),
    });
    montar('ADMINISTRADOR', servidor);
    $('[data-pestana="emitir"]').click();
    const buscador = $('[data-panel="emitir"] input[name="cuenta"]');
    escribir(buscador, 'beto');
    buscador.closest('form').requestSubmit();
    await esperar(() => $('[data-zona="apodo-elegido"]')?.textContent === 'Beto');

    escribir(buscador, 'jugadores');
    buscador.closest('form').requestSubmit();
    await esperar(
      () => $$('[data-panel="emitir"] [data-zona="resultados-de-cuenta"] tbody tr').length === 2,
    );
    $$(
      '[data-panel="emitir"] [data-zona="resultados-de-cuenta"] [data-accion="elegir-cuenta"]',
    )[1].click();
    await esperar(() => $('[data-zona="apodo-elegido"]')?.textContent === 'Cris');
    expect($('[data-panel="emitir"] [data-zona="cuenta-elegida"]').textContent).toContain(
      'Baneada',
    );
  });

  test('«Emitir otra sanción» desde el detalle lleva a la emisión con la cuenta', async () => {
    const servidor = servidorFalso();
    montar('ADMINISTRADOR', servidor);
    await esperar(() => $$('[data-accion="ver-sancion"]').length === 3);
    $$('[data-accion="ver-sancion"]')[1].click();
    dialogo().querySelector('[data-accion="emitir-a-la-cuenta"]').click();
    await esperar(() => $('[data-zona="apodo-elegido"]')?.textContent === 'Beto');
    expect($('[data-pestana="emitir"]').getAttribute('aria-selected')).toBe('true');
  });
});
