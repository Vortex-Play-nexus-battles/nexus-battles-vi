/**
 * «Tus datos» — portal de privacidad (HU-PRV-004) y portabilidad (HU-PRV-006).
 *
 * Se monta sobre la sección real de `perfil.html`. Lo que se prueba: que cada
 * módulo se lee de su ruta de contrato con el token de la persona; que cada
 * bloque dice qué datos hay y para qué se usan; que un módulo caído deja su
 * bloque «no disponible», lo nombra arriba y deja ver el resto (CA-03); que un
 * listado largo se marca parcial; que el JSON lleva la fecha y los módulos
 * incompletos; y que nada acaba en la dirección ni en `localStorage`.
 */

import { readFileSync } from 'node:fs';

import { jest } from '@jest/globals';

import {
  ESTADOS_DEL_BLOQUE,
  FORMATO_DE_EXPORTACION,
  FUENTES,
  MAXIMO_DE_PAGINAS,
  bloquesIncompletos,
  consolidarDatos,
  descargarJson,
  exportacionJson,
  fraseDelResumen,
  leerPaginas,
  montarPrivacidad,
  nombreDelArchivo,
  pintarReporte,
} from './privacidad-cuenta.js';

const MARCADO = readFileSync(new URL('./perfil.html', import.meta.url), 'utf8');
const UID = '0f3c9a4e-5d1b-4c8e-9a2f-6b7d8e9f0a1b';
const SESION = { uid: UID, apodo: 'ada', rol: 'JUGADOR' };
const AHORA = () => new Date('2026-10-05T19:30:00.000Z');

/** Lo que responde cada ruta (sin la consulta), como los contratos. */
function respuestasCompletas() {
  return {
    [`/api/v1/perfiles/${UID}`]: {
      id: 8,
      apodo: 'ada',
      email: 'ada@upb.edu.co',
      nombres: 'Ada',
      apellidos: 'Lovelace',
      avatar: '/avatares-subidos/x.png',
      preferencias: 'Modo claro',
    },
    '/api/v1/auth/preguntas-seguridad': {
      configuradas: true,
      preguntas: [{ id: 'p1', texto: '¿Ciudad natal?' }],
    },
    [`/api/v1/creditos/${UID}/saldo`]: {
      jugadorUid: UID,
      saldoBruto: 600,
      saldoReservado: 100,
      saldoDisponible: 500,
    },
    [`/api/v1/creditos/${UID}/movimientos`]: {
      content: [
        {
          id: 'm1',
          monto: 500,
          concepto: 'bono-registro',
          tipo: 'CREDITO',
          estado: 'CONSUMIDA',
          signo: 'SUMA',
          creado: '2026-09-01T10:00:00Z',
        },
      ],
      totalPages: 1,
    },
    '/api/v1/transacciones/mi-historial': { content: [], totalPages: 0 },
    '/api/v1/ordenes': [
      {
        id: 'o1',
        estado: 'COMPLETA',
        moneda: 'CREDITOS',
        formaDePago: 'CREDITOS',
        total: 300,
        lineas: [
          { productoId: 'p', nombre: 'Espada', cantidad: 1, precioUnitario: 300, subtotal: 300 },
        ],
        creadaEn: '2026-09-02T10:00:00Z',
      },
    ],
    '/api/v1/lista-deseos': [],
    '/api/v1/inventario/elementos': {
      elementos: [
        {
          id: 'e1',
          productoId: 'p',
          tipo: 'HEROE',
          nombrePropio: 'Guerrero Tanque',
          disponible: true,
          subastaId: null,
          nivel: 2,
        },
      ],
      numero: 0,
      tamanio: 16,
      totalElementos: 1,
      totalPaginas: 1,
      ultima: true,
    },
    '/api/v1/cofres/mios': { content: [], totalPages: 0 },
    '/api/v1/partidas/mias': {
      contenido: [
        {
          id: 'pa1',
          idSala: 's1',
          estado: 'FINALIZADA',
          resultado: 'VICTORIA',
          heroe: 'Guerrero Tanque',
          iniciadaEn: '2026-09-03T10:00:00Z',
        },
        {
          id: 'pa2',
          idSala: 's2',
          estado: 'FINALIZADA',
          resultado: 'DERROTA',
          heroe: 'Guerrero Tanque',
          iniciadaEn: '2026-09-04T10:00:00Z',
        },
      ],
      pagina: 0,
      tamano: 50,
      totalElementos: 2,
      totalPaginas: 1,
    },
    '/api/v1/misiones/historial': {
      completadas: [],
      porCategoria: [{ categoria: 'HISTORIA', completadas: 2, fallidas: 1 }],
      mejoresTiempos: [],
      epicas: [{}],
      cadenas: [],
    },
    '/api/v1/mis-subastas/publicadas': [],
    '/api/v1/mis-pujas': [],
    [`/api/v1/sanciones/usuarios/${UID}`]: [],
    [`/api/v1/users/${UID}/notifications`]: {
      usuarioId: UID,
      noLeidas: 1,
      avisos: [{ id: 'n1', titulo: 'Bienvenida', creadaEn: '2026-09-01T10:00:00Z', leida: false }],
    },
    '/api/v1/mensajes-directos/conversaciones': [],
    '/api/v1/chat/historial': [],
  };
}

/** `fetch` falso: responde por ruta; lo que no conoce o se pide caído, 502. */
function fetchFalso(respuestas = respuestasCompletas(), { caidas = [] } = {}) {
  return jest.fn((url) => {
    const ruta = String(url).split('?')[0];
    if (caidas.includes(ruta) || !(ruta in respuestas)) {
      return Promise.resolve({ ok: false, status: 502, json: async () => ({}) });
    }
    return Promise.resolve({ ok: true, status: 200, json: async () => respuestas[ruta] });
  });
}

const bloque = (consolidado, id) => consolidado.bloques.find((b) => b.id === id);

/** jsdom no sabe navegar: el clic del enlace de descarga se cancela aquí para no llenar la salida de avisos. */
function sinNavegarAlDescargar(evento) {
  if (evento.target instanceof HTMLAnchorElement && evento.target.hasAttribute('download')) {
    evento.preventDefault();
  }
}

beforeEach(() => {
  document.documentElement.innerHTML = MARCADO.replace(/<script[\s\S]*?<\/script>/g, '');
  localStorage.clear();
  document.addEventListener('click', sinNavegarAlDescargar, true);
});

afterEach(() => {
  document.removeEventListener('click', sinNavegarAlDescargar, true);
});

describe('consolidar', () => {
  test('lee cada módulo de su ruta de contrato, en paralelo, y los deja completos', async () => {
    const fetchImpl = fetchFalso();

    const consolidado = await consolidarDatos({ sesion: SESION, fetchImpl, ahora: AHORA });

    expect(consolidado.generadoEn).toBe('2026-10-05T19:30:00.000Z');
    expect(consolidado.bloques.map((b) => b.id)).toEqual(FUENTES.map((f) => f.id));
    const pedidas = fetchImpl.mock.calls.map(([url]) => String(url).split('?')[0]);
    expect(new Set(pedidas)).toEqual(new Set(Object.keys(respuestasCompletas())));
    for (const b of consolidado.bloques.filter((x) => x.id !== 'comentarios')) {
      expect([b.id, b.estado]).toEqual([b.id, ESTADOS_DEL_BLOQUE.COMPLETO]);
    }
    expect(bloque(consolidado, 'comentarios').estado).toBe(ESTADOS_DEL_BLOQUE.SIN_CONSULTA);
  });

  test('cada bloque dice qué datos hay y para qué se usan', async () => {
    const consolidado = await consolidarDatos({
      sesion: SESION,
      fetchImpl: fetchFalso(),
      ahora: AHORA,
    });

    for (const b of consolidado.bloques) {
      expect(b.finalidad.length).toBeGreaterThan(20);
    }
    const perfil = Object.fromEntries(
      bloque(consolidado, 'perfil').resumen.map((d) => [d.etiqueta, d.valor]),
    );
    expect(perfil).toMatchObject({
      Apodo: 'ada',
      Correo: 'ada@upb.edu.co',
      Nombres: 'Ada',
      Rol: 'JUGADOR',
    });
    const creditos = Object.fromEntries(
      bloque(consolidado, 'creditos').resumen.map((d) => [d.etiqueta, d.valor]),
    );
    expect(creditos.Disponibles).toBe('500');
    expect(creditos.Movimientos).toBe('1');
    const batallas = Object.fromEntries(
      bloque(consolidado, 'batallas').resumen.map((d) => [d.etiqueta, d.valor]),
    );
    expect(batallas).toMatchObject({ Partidas: '2', Victorias: '1', Derrotas: '1' });
    const misiones = Object.fromEntries(
      bloque(consolidado, 'misiones').resumen.map((d) => [d.etiqueta, d.valor]),
    );
    expect(misiones).toMatchObject({ Cumplidas: '2', Fallidas: '1', 'Épicas obtenidas': '1' });
    expect(bloque(consolidado, 'compras').detalle[0].filas[0]).toEqual(
      expect.arrayContaining(['Espada × 1', 'Créditos del juego']),
    );
  });

  test('un módulo caído deja su bloque no disponible y el resto completo (CA-03)', async () => {
    const consolidado = await consolidarDatos({
      sesion: SESION,
      fetchImpl: fetchFalso(respuestasCompletas(), { caidas: ['/api/v1/mis-pujas'] }),
      ahora: AHORA,
    });

    const subastas = bloque(consolidado, 'subastas');
    expect(subastas.estado).toBe(ESTADOS_DEL_BLOQUE.NO_DISPONIBLE);
    expect(subastas.motivo).toContain('No pudimos consultar «Tus subastas y pujas»');
    expect(bloque(consolidado, 'perfil').estado).toBe(ESTADOS_DEL_BLOQUE.COMPLETO);
    expect(bloquesIncompletos(consolidado).map((b) => b.id)).toEqual(['subastas', 'comentarios']);

    const frase = fraseDelResumen(consolidado);
    expect(frase).toContain(
      'Consulta parcial: no pudimos consultar «Tus subastas y pujas»; el resto está completo.',
    );
    expect(frase).toContain('«Tus comentarios» todavía no se puede reunir aquí.');
  });

  test('sin fallos, la frase no habla de consulta parcial', async () => {
    const consolidado = await consolidarDatos({
      sesion: SESION,
      fetchImpl: fetchFalso(),
      ahora: AHORA,
    });

    const frase = fraseDelResumen(consolidado);
    expect(frase).toMatch(/^Reunimos tus datos de 15 módulos\./);
    expect(frase).not.toContain('Consulta parcial');
  });

  test('una red caída tampoco rompe el portal: todo no disponible menos lo que no se consulta', async () => {
    const consolidado = await consolidarDatos({
      sesion: SESION,
      fetchImpl: jest.fn(() => Promise.reject(new TypeError('Failed to fetch'))),
      ahora: AHORA,
    });

    expect(
      consolidado.bloques.filter((b) => b.estado === ESTADOS_DEL_BLOQUE.NO_DISPONIBLE),
    ).toHaveLength(15);
  });
});

describe('listados paginados', () => {
  test('pide página a página hasta la última', async () => {
    const fetchImpl = jest.fn((url) => {
      const pagina = Number(new URL(String(url), 'http://x').searchParams.get('page'));
      return Promise.resolve({
        ok: true,
        status: 200,
        json: async () => ({ content: [pagina], totalPages: 3 }),
      });
    });

    const resultado = await leerPaginas({
      ruta: '/api/v1/transacciones/mi-historial',
      fetchImpl,
      parametros: (pagina) => ({ page: String(pagina), size: '100' }),
      extraer: (cuerpo) => ({ lista: cuerpo.content, paginas: cuerpo.totalPages }),
    });

    expect(resultado).toEqual({ elementos: [0, 1, 2], completo: true });
    expect(fetchImpl).toHaveBeenCalledTimes(3);
  });

  test('con más páginas que el tope, se para y lo marca incompleto', async () => {
    const fetchImpl = jest.fn(() =>
      Promise.resolve({
        ok: true,
        status: 200,
        json: async () => ({ content: ['x'], totalPages: 50 }),
      }),
    );

    const resultado = await leerPaginas({
      ruta: '/api/v1/transacciones/mi-historial',
      fetchImpl,
      parametros: (pagina) => ({ page: String(pagina) }),
      extraer: (cuerpo) => ({ lista: cuerpo.content, paginas: cuerpo.totalPages }),
    });

    expect(fetchImpl).toHaveBeenCalledTimes(MAXIMO_DE_PAGINAS);
    expect(resultado.completo).toBe(false);
  });

  test('un listado incompleto deja el bloque parcial, con su motivo', async () => {
    const respuestas = respuestasCompletas();
    respuestas['/api/v1/transacciones/mi-historial'] = { content: [{ id: 't' }], totalPages: 99 };

    const consolidado = await consolidarDatos({
      sesion: SESION,
      fetchImpl: fetchFalso(respuestas),
      ahora: AHORA,
    });

    const pagos = bloque(consolidado, 'pagos');
    expect(pagos.estado).toBe(ESTADOS_DEL_BLOQUE.PARCIAL);
    expect(pagos.motivo).toContain('se incluyeron los más recientes');
    expect(fraseDelResumen(consolidado)).toContain(
      'De «Pagos con dinero real» se incluyeron los registros más recientes.',
    );
  });
});

describe('exportación JSON (RF-PRV-006)', () => {
  test('lleva formato, fecha, cuenta, datos de cada módulo y los incompletos', async () => {
    const consolidado = await consolidarDatos({
      sesion: SESION,
      fetchImpl: fetchFalso(respuestasCompletas(), { caidas: ['/api/v1/chat/historial'] }),
      ahora: AHORA,
    });

    const json = exportacionJson(consolidado);

    expect(json.formato).toBe(FORMATO_DE_EXPORTACION);
    expect(json.version).toBe(1);
    expect(json.generadoEn).toBe('2026-10-05T19:30:00.000Z');
    expect(json.cuenta).toEqual({ uid: UID, apodo: 'ada', rol: 'JUGADOR' });
    expect(json.completo).toBe(false);
    expect(json.modulosIncompletos).toEqual([
      expect.objectContaining({ modulo: 'asistente', estado: 'no-disponible' }),
      expect.objectContaining({ modulo: 'comentarios', estado: 'sin-consulta' }),
    ]);
    expect(json.modulos.perfil.datos.email).toBe('ada@upb.edu.co');
    expect(json.modulos.perfil.finalidad).toContain('correo');
    expect(json.modulos.inventario.datos.elementos).toHaveLength(1);
    expect(json.modulos.asistente.datos).toBeNull();
  });

  test('el nombre del archivo solo lleva la fecha, ningún dato de la persona', async () => {
    const consolidado = await consolidarDatos({
      sesion: SESION,
      fetchImpl: fetchFalso(),
      ahora: AHORA,
    });

    expect(nombreDelArchivo(consolidado)).toBe('mis-datos-nexus-battles-2026-10-05.json');
  });

  test('descargar crea el archivo en el navegador y libera la dirección', async () => {
    const consolidado = await consolidarDatos({
      sesion: SESION,
      fetchImpl: fetchFalso(),
      ahora: AHORA,
    });
    let archivo = null;
    const urlImpl = {
      createObjectURL: jest.fn((blob) => {
        archivo = blob;
        return 'blob:falso';
      }),
      revokeObjectURL: jest.fn(),
    };
    const clics = [];
    document.addEventListener('click', (evento) => clics.push(evento.target), { once: true });

    const nombre = descargarJson(consolidado, { documento: document, urlImpl });

    expect(nombre).toBe('mis-datos-nexus-battles-2026-10-05.json');
    expect(clics[0].getAttribute('download')).toBe(nombre);
    expect(clics[0].isConnected).toBe(false);
    // Se libera en la vuelta siguiente, no antes de que el navegador lo lea.
    expect(urlImpl.revokeObjectURL).not.toHaveBeenCalled();
    await new Promise((resolver) => setTimeout(resolver, 0));
    expect(urlImpl.revokeObjectURL).toHaveBeenCalledWith('blob:falso');
    expect(archivo.type).toBe('application/json');
    const texto = await new Promise((resolver) => {
      const lector = new FileReader();
      lector.onload = () => resolver(lector.result);
      lector.readAsText(archivo);
    });
    expect(JSON.parse(texto).modulos.perfil.datos.apodo).toBe('ada');
  });
});

describe('la sección «Tus datos»', () => {
  const zona = (nombre) => document.querySelector(`[data-zona="${nombre}"]`);
  const accion = (nombre) => document.querySelector(`[data-accion="${nombre}"]`);

  test('no consulta nada hasta cargar, y cargar dos veces no repite la consulta', async () => {
    const fetchImpl = fetchFalso();
    const vista = montarPrivacidad(document, { sesion: SESION, fetchImpl, ahora: AHORA });

    expect(fetchImpl).not.toHaveBeenCalled();
    expect(accion('descargar-datos').disabled).toBe(true);
    await vista.cargar();
    const llamadas = fetchImpl.mock.calls.length;
    await vista.cargar();

    expect(fetchImpl).toHaveBeenCalledTimes(llamadas);
    expect(accion('descargar-datos').disabled).toBe(false);
    expect(accion('imprimir-datos').disabled).toBe(false);
  });

  test('pinta un bloque por módulo, con su finalidad, su resumen y su estado', async () => {
    const vista = montarPrivacidad(document, {
      sesion: SESION,
      fetchImpl: fetchFalso(),
      ahora: AHORA,
    });
    await vista.cargar();

    const bloques = zona('bloques-portal').querySelectorAll('article[data-fuente]');
    expect(bloques).toHaveLength(FUENTES.length);
    const perfil = zona('bloques-portal').querySelector('[data-fuente="perfil"]');
    expect(perfil.dataset.estado).toBe('completo');
    expect(perfil.textContent).toContain('Para qué se usa:');
    expect(perfil.textContent).toContain('ada@upb.edu.co');
    expect(
      zona('bloques-portal').querySelector('[data-fuente="comentarios"]').textContent,
    ).toContain('todavía no ofrece una consulta');
    expect(zona('resumen-portal').textContent).toMatch(/^Reunimos tus datos de 15 módulos\./);
  });

  test('con un módulo caído avisa de la consulta parcial y deja volver a consultar', async () => {
    const respuestas = respuestasCompletas();
    const fetchImpl = fetchFalso(respuestas, { caidas: ['/api/v1/inventario/elementos'] });
    const vista = montarPrivacidad(document, { sesion: SESION, fetchImpl, ahora: AHORA });
    await vista.cargar();

    const aviso = zona('aviso-portal');
    expect(aviso.hidden).toBe(false);
    expect(aviso.textContent).toContain(
      'No pudimos consultar «Tu inventario»; el resto está completo.',
    );
    const inventario = zona('bloques-portal').querySelector('[data-fuente="inventario"]');
    expect(inventario.dataset.estado).toBe('no-disponible');
    expect(inventario.textContent).toContain('El resto de tus datos está completo.');

    // El inventario vuelve: «Volver a consultar» lo trae.
    fetchImpl.mockImplementation(fetchFalso(respuestas));
    aviso.querySelector('[data-accion="reconsultar"]').click();
    await vista.cargar();
    expect(zona('bloques-portal').querySelector('[data-fuente="inventario"]').dataset.estado).toBe(
      'completo',
    );
    expect(zona('aviso-portal').hidden).toBe(true);
  });

  test('el reporte imprimible lleva todo y solo existe mientras se imprime', async () => {
    const imprimir = jest.fn();
    const vista = montarPrivacidad(document, {
      sesion: SESION,
      fetchImpl: fetchFalso(),
      ahora: AHORA,
      imprimir,
    });
    await vista.cargar();

    accion('imprimir-datos').click();

    const reporte = zona('reporte-privacidad');
    expect(imprimir).toHaveBeenCalledTimes(1);
    expect(document.body.classList.contains('imprimiendo-privacidad')).toBe(true);
    expect(reporte.hidden).toBe(false);
    expect(reporte.querySelector('h1').textContent).toBe('Tus datos en The Nexus Battles VI');
    expect(reporte.querySelectorAll('.reporte-privacidad__bloque')).toHaveLength(FUENTES.length);
    expect(reporte.textContent).toContain(
      'Este reporte está incompleto: Tus comentarios (sin consulta).',
    );

    window.dispatchEvent(new Event('afterprint'));
    expect(document.body.classList.contains('imprimiendo-privacidad')).toBe(false);
    expect(reporte.hidden).toBe(true);
    expect(reporte.childElementCount).toBe(0);
  });

  test('ningún dato de la persona va a la dirección ni a localStorage', async () => {
    const antes = window.location.href;
    const vista = montarPrivacidad(document, {
      sesion: SESION,
      fetchImpl: fetchFalso(),
      ahora: AHORA,
      urlImpl: { createObjectURL: () => 'blob:falso', revokeObjectURL: () => {} },
    });
    await vista.cargar();
    accion('descargar-datos').click();

    expect(window.location.href).toBe(antes);
    expect(localStorage.length).toBe(0);
  });
});

describe('reporte', () => {
  test('pinta todas las filas del detalle, no solo el resumen', async () => {
    const consolidado = await consolidarDatos({
      sesion: SESION,
      fetchImpl: fetchFalso(),
      ahora: AHORA,
    });
    const zona = document.createElement('section');

    pintarReporte(zona, consolidado);

    const batallas = zona.querySelector('[data-fuente="batallas"]');
    expect(batallas.querySelectorAll('tbody tr')).toHaveLength(2);
    expect(batallas.textContent).toContain('Victoria');
  });
});
