/**
 * Pestaña «Preguntas sin respuesta» del panel del asistente —
 * ms-chatbot.yaml 1.7.0 (7.4.5).
 */

import { jest } from '@jest/globals';

import { ErrorDelChatbot } from '../comun/cliente-chatbot.js';
import {
  ESTADOS_DE_BRECHA,
  LARGO_DE_VARIANTE,
  POR_PAGINA,
  detalleDeEstado,
  detalleDeVeces,
  montarBrechas,
  varianteDesdePregunta,
} from './panel-chatbot-brechas.js';

const esperar = () => new Promise((resolver) => setTimeout(resolver, 0));

const PUJAR = {
  brechaId: 'b-1',
  pregunta: 'como pujo en una subasta',
  escalamientos: 3,
  noUtiles: 1,
  total: 4,
  primeraVez: '2026-10-01T15:00:00Z',
  ultimaVez: '2026-10-05T15:00:00Z',
  estado: 'PENDIENTE',
  estadoCambiadoPor: null,
  estadoCambiadoEn: null,
  reaperturas: 0,
};
const APODO = {
  ...PUJAR,
  brechaId: 'b-2',
  pregunta: 'puedo cambiar mi apodo',
  escalamientos: 1,
  noUtiles: 0,
  total: 1,
};
const ATENDIDA = {
  ...PUJAR,
  brechaId: 'b-3',
  pregunta: 'donde veo mis torneos',
  estado: 'ATENDIDA',
  estadoCambiadoPor: 'uid-admin',
  estadoCambiadoEn: '2026-10-06T15:00:00Z',
};
const CANDIDATA = { versionId: 'v-2', numero: 2, estado: 'BORRADOR' };
const PRODUCCION = { versionId: 'v-1', numero: 1, estado: 'PRODUCCION' };

function pagina(contenido, totalElementos = contenido.length) {
  return { contenido, pagina: 0, tamano: POR_PAGINA, totalElementos };
}

function clienteFalso(sobrescribir = {}) {
  return {
    listarBrechas: jest.fn(async () => pagina([PUJAR, APODO])),
    cambiarEstadoDeBrecha: jest.fn(async () => ({ ...PUJAR, estado: 'ATENDIDA' })),
    listarVersiones: jest.fn(async () => [CANDIDATA, PRODUCCION]),
    agregarTema: jest.fn(async () => ({ id: 't-9' })),
    ...sobrescribir,
  };
}

async function montar({ cliente = clienteFalso(), editarTema, irA } = {}) {
  const raiz = document.createElement('div');
  document.body.replaceChildren(raiz);
  const pestana = montarBrechas(raiz, {
    cliente,
    ...(editarTema ? { editarTema } : {}),
    ...(irA ? { irA } : {}),
  });
  await pestana.cargar();
  return { raiz, cliente, pestana };
}

const nota = (raiz) => raiz.querySelector('[data-zona="nota-brechas"]');

describe('funciones puras', () => {
  test('varianteDesdePregunta quita comas y espacios de más, y respeta el largo de una variante', () => {
    expect(varianteDesdePregunta('  hola,   como   pujo ,  ya ')).toBe('hola como pujo ya');
    expect(varianteDesdePregunta('a'.repeat(250))).toHaveLength(LARGO_DE_VARIANTE);
    expect(varianteDesdePregunta(null)).toBe('');
  });

  test('detalleDeVeces separa lo que no entendió de lo que no sirvió', () => {
    expect(detalleDeVeces({ escalamientos: 3, noUtiles: 1 })).toBe(
      '3 sin entenderla · 1 «no útil»',
    );
    expect(detalleDeVeces({ escalamientos: 0, noUtiles: 2 })).toBe('2 «no útil»');
    expect(detalleDeVeces({ escalamientos: 0, noUtiles: 0 })).toBe('');
  });

  test('detalleDeEstado dice quién la marcó y si volvió a preguntarse', () => {
    expect(detalleDeEstado(PUJAR)).toBeNull();
    expect(detalleDeEstado(ATENDIDA)).toMatch(/^Por uid-admin el /);
    expect(detalleDeEstado({ ...ATENDIDA, estadoCambiadoPor: null })).toMatch(/^El /);
    expect(detalleDeEstado({ ...PUJAR, reaperturas: 1 })).toBe(
      'Volvió a preguntarse después de marcarla atendida',
    );
    expect(detalleDeEstado({ ...PUJAR, reaperturas: 3 })).toBe(
      'Volvió a preguntarse 3 veces después de marcarla atendida',
    );
  });

  test('los tres estados del contrato tienen nombre y distintivo', () => {
    expect(Object.keys(ESTADOS_DE_BRECHA)).toEqual(['PENDIENTE', 'ATENDIDA', 'DESCARTADA']);
  });
});

describe('los cuatro estados', () => {
  test('mientras carga muestra el esqueleto', () => {
    const raiz = document.createElement('div');
    document.body.replaceChildren(raiz);
    const pestana = montarBrechas(raiz, {
      cliente: clienteFalso({ listarBrechas: jest.fn(() => new Promise(() => {})) }),
    });

    pestana.cargar();

    expect(raiz.querySelector('[data-zona="brechas"] [data-estado="cargando"]')).not.toBeNull();
  });

  test('lista las pendientes, en el orden del servicio (las más repetidas primero)', async () => {
    const { raiz, cliente } = await montar();

    expect(cliente.listarBrechas).toHaveBeenCalledWith({
      desde: null,
      hasta: null,
      estado: 'PENDIENTE',
      pagina: 0,
      tamano: POR_PAGINA,
    });
    const filas = [...raiz.querySelectorAll('[data-zona="tabla-brechas"] tbody tr')];
    expect(filas.map((fila) => fila.dataset.brecha)).toEqual(['b-1', 'b-2']);
    expect(filas[0].textContent).toContain('como pujo en una subasta');
    expect(filas[0].textContent).toContain('3 sin entenderla · 1 «no útil»');
    expect(filas[0].textContent).toContain('Pendiente');
    expect(filas[0].querySelectorAll('td')[1].querySelector('p').textContent).toBe('4');
  });

  test('vacío: lo dice según el estado elegido', async () => {
    const { raiz, cliente } = await montar({
      cliente: clienteFalso({ listarBrechas: jest.fn(async () => pagina([])) }),
    });
    expect(raiz.textContent).toContain('No hay preguntas pendientes en este período');

    const filtro = raiz.querySelector('select[name="estado"]');
    filtro.value = '';
    filtro.dispatchEvent(new Event('change'));
    await esperar();

    expect(cliente.listarBrechas).toHaveBeenLastCalledWith(
      expect.objectContaining({ estado: null }),
    );
    expect(raiz.textContent).toContain('No hay preguntas sin respuesta en este período');
  });

  test('error: dice qué falló y por qué, y deja reintentar', async () => {
    const listarBrechas = jest
      .fn()
      .mockRejectedValueOnce(new ErrorDelChatbot(null, 503))
      .mockResolvedValueOnce(pagina([PUJAR]));
    const { raiz } = await montar({ cliente: clienteFalso({ listarBrechas }) });

    const error = raiz.querySelector('[data-estado="error"]');
    expect(error.textContent).toContain('No se pudieron cargar las preguntas sin respuesta');
    expect(error.textContent).toContain('El servicio del asistente no responde');

    raiz.querySelector('[data-accion="reintentar"]').click();
    await esperar();
    expect(listarBrechas).toHaveBeenCalledTimes(2);
    expect(raiz.querySelector('[data-brecha="b-1"]')).not.toBeNull();
  });

  test('sin permiso lo dice; un 400 es el período', async () => {
    const sinPermiso = await montar({
      cliente: clienteFalso({
        listarBrechas: jest.fn(async () => {
          throw new ErrorDelChatbot(null, 403);
        }),
      }),
    });
    expect(sinPermiso.raiz.textContent).toContain('Tu sesión no tiene permiso para esto');

    const periodo = await montar({
      cliente: clienteFalso({
        listarBrechas: jest.fn(async () => {
          throw new ErrorDelChatbot({ title: 'Bad Request' }, 400);
        }),
      }),
    });
    expect(periodo.raiz.textContent).toContain('El período no es válido');
  });
});

describe('filtros y páginas', () => {
  test('consultar con fechas las manda y vuelve a la primera página', async () => {
    const { raiz, cliente } = await montar({
      cliente: clienteFalso({ listarBrechas: jest.fn(async () => pagina([PUJAR], 40)) }),
    });
    raiz.querySelector('[data-accion="pagina-siguiente"]').click();
    await esperar();
    expect(cliente.listarBrechas).toHaveBeenLastCalledWith(expect.objectContaining({ pagina: 1 }));

    raiz.querySelector('input[name="desde"]').value = '2026-09-01';
    raiz.querySelector('input[name="hasta"]').value = '2026-09-30';
    raiz.querySelector('form').dispatchEvent(new Event('submit', { cancelable: true }));
    await esperar();

    expect(cliente.listarBrechas).toHaveBeenLastCalledWith({
      desde: '2026-09-01',
      hasta: '2026-09-30',
      estado: 'PENDIENTE',
      pagina: 0,
      tamano: POR_PAGINA,
    });
  });

  test('un período al revés no consulta al servicio', async () => {
    const { raiz, cliente } = await montar();
    raiz.querySelector('input[name="desde"]').value = '2026-09-30';
    raiz.querySelector('input[name="hasta"]').value = '2026-09-01';

    raiz.querySelector('form').dispatchEvent(new Event('submit', { cancelable: true }));
    await esperar();

    expect(cliente.listarBrechas).toHaveBeenCalledTimes(1);
    expect(raiz.textContent).toContain('no puede ser anterior');
  });

  test('de 16 en 16: se puede avanzar y volver', async () => {
    const { raiz, cliente } = await montar({
      cliente: clienteFalso({ listarBrechas: jest.fn(async () => pagina([PUJAR], 40)) }),
    });

    expect(raiz.textContent).toContain('Página 1 de 3 · 40 preguntas');
    expect(raiz.querySelector('[data-accion="pagina-anterior"]').disabled).toBe(true);

    raiz.querySelector('[data-accion="pagina-siguiente"]').click();
    await esperar();
    raiz.querySelector('[data-accion="pagina-anterior"]').click();
    await esperar();

    expect(cliente.listarBrechas.mock.calls.map(([filtro]) => filtro.pagina)).toEqual([0, 1, 0]);
  });

  test('si la página se queda vacía al cambiar un estado, vuelve a la última con algo', async () => {
    const listarBrechas = jest
      .fn()
      .mockResolvedValueOnce(pagina([PUJAR], 17))
      .mockResolvedValueOnce(pagina([APODO], 17))
      // La única de la página 2 se marcó atendida: ya solo hay 16.
      .mockResolvedValueOnce(pagina([], 16))
      .mockResolvedValueOnce(pagina([PUJAR], 16));
    const { raiz, cliente } = await montar({ cliente: clienteFalso({ listarBrechas }) });
    raiz.querySelector('[data-accion="pagina-siguiente"]').click();
    await esperar();

    raiz.querySelector('[data-brecha="b-2"] [data-accion="brecha-atendida"]').click();
    await esperar();
    await esperar();

    expect(listarBrechas.mock.calls.map(([filtro]) => filtro.pagina)).toEqual([0, 1, 1, 0]);
    expect(cliente.cambiarEstadoDeBrecha).toHaveBeenCalledWith('b-2', 'ATENDIDA');
    expect(raiz.querySelector('[data-brecha="b-1"]')).not.toBeNull();
    expect(raiz.textContent).toContain('Página 1 de 1');
  });
});

describe('cambiar el estado', () => {
  test('una pendiente se marca atendida o se descarta; se recarga y se avisa', async () => {
    const { raiz, cliente } = await montar();
    const fila = raiz.querySelector('[data-brecha="b-1"]');
    const acciones = [...fila.querySelectorAll('button')].map((boton) => boton.textContent);
    expect(acciones).toEqual(['Marcar atendida', 'Descartar', 'Crear tema con esta pregunta']);

    fila.querySelector('[data-accion="brecha-atendida"]').click();
    await esperar();
    await esperar();

    expect(cliente.cambiarEstadoDeBrecha).toHaveBeenCalledWith('b-1', 'ATENDIDA');
    expect(cliente.listarBrechas).toHaveBeenCalledTimes(2);
    expect(nota(raiz).textContent).toContain('Marcada como atendida');
    expect(nota(raiz).textContent).toContain('regresará a pendientes');

    raiz.querySelector('[data-brecha="b-2"] [data-accion="brecha-descartada"]').click();
    await esperar();
    await esperar();
    expect(cliente.cambiarEstadoDeBrecha).toHaveBeenLastCalledWith('b-2', 'DESCARTADA');
    expect(nota(raiz).textContent).toContain('Pregunta descartada');
  });

  test('una atendida o descartada puede volver a pendiente, y se ve quién la marcó', async () => {
    const { raiz, cliente } = await montar({
      cliente: clienteFalso({ listarBrechas: jest.fn(async () => pagina([ATENDIDA])) }),
    });
    const fila = raiz.querySelector('[data-brecha="b-3"]');
    expect(fila.textContent).toContain('Atendida');
    expect(fila.textContent).toContain('Por uid-admin el');
    expect([...fila.querySelectorAll('button')].map((boton) => boton.textContent)).toEqual([
      'Volver a pendiente',
    ]);

    fila.querySelector('[data-accion="brecha-pendiente"]').click();
    await esperar();
    await esperar();

    expect(cliente.cambiarEstadoDeBrecha).toHaveBeenCalledWith('b-3', 'PENDIENTE');
    expect(nota(raiz).textContent).toContain('volvió a pendientes');
  });

  test('si falla, dice qué falló y por qué', async () => {
    const { raiz } = await montar({
      cliente: clienteFalso({
        cambiarEstadoDeBrecha: jest.fn(async () => {
          throw new ErrorDelChatbot({ title: 'Not Found' }, 404);
        }),
      }),
    });

    raiz.querySelector('[data-brecha="b-1"] [data-accion="brecha-descartada"]').click();
    await esperar();
    await esperar();

    expect(nota(raiz).querySelector('[role="alert"]').textContent).toBe(
      'No se pudo cambiar el estado de la pregunta. Ya no existe: recarga la pestaña para ver lo que hay ahora.',
    );
  });
});

describe('«Crear tema con esta pregunta»', () => {
  test('con candidata abre el formulario con la pregunta como primera variante en español', async () => {
    const editarTema = jest.fn(async ({ guardar }) => {
      await guardar({ titulo: 'Cómo pujar', variantesEs: ['como pujo en una subasta'] });
      return true;
    });
    const { raiz, cliente } = await montar({
      cliente: clienteFalso({
        listarBrechas: jest.fn(async () => pagina([{ ...PUJAR, pregunta: 'hola, como pujo' }])),
      }),
      editarTema,
    });

    raiz.querySelector('[data-accion="crear-tema-desde-brecha"]').click();
    await esperar();
    await esperar();

    expect(editarTema).toHaveBeenCalledWith(
      expect.objectContaining({
        titulo: 'Crear tema con esta pregunta',
        tema: { variantesEs: ['hola como pujo'] },
      }),
    );
    expect(cliente.agregarTema).toHaveBeenCalledWith({
      titulo: 'Cómo pujar',
      variantesEs: ['como pujo en una subasta'],
    });
    expect(nota(raiz).textContent).toContain('Se agregó el tema a la versión candidata 2.');
    expect(nota(raiz).textContent).toContain('marca esta pregunta como atendida');
  });

  test('si se cancela el formulario no dice nada', async () => {
    const editarTema = jest.fn(async () => false);
    const { raiz } = await montar({ editarTema });

    raiz.querySelector('[data-accion="crear-tema-desde-brecha"]').click();
    await esperar();
    await esperar();

    expect(editarTema).toHaveBeenCalled();
    expect(nota(raiz).textContent).toBe('');
  });

  test('sin candidata explica cómo crearla y lleva a «Base de conocimiento»', async () => {
    const editarTema = jest.fn();
    const irA = jest.fn();
    const { raiz } = await montar({
      cliente: clienteFalso({ listarVersiones: jest.fn(async () => [PRODUCCION]) }),
      editarTema,
      irA,
    });

    raiz.querySelector('[data-accion="crear-tema-desde-brecha"]').click();
    await esperar();

    expect(editarTema).not.toHaveBeenCalled();
    expect(nota(raiz).textContent).toContain('No hay una versión candidata donde agregar el tema.');
    expect(nota(raiz).textContent).toContain('pulsa «Crear candidata»');
    nota(raiz).querySelector('[data-accion="ir-a-la-base"]').click();
    expect(irA).toHaveBeenCalledWith('base');
  });

  test('sin a dónde ir, la explicación no trae botón', async () => {
    const { raiz } = await montar({
      cliente: clienteFalso({ listarVersiones: jest.fn(async () => []) }),
      editarTema: jest.fn(),
    });

    raiz.querySelector('[data-accion="crear-tema-desde-brecha"]').click();
    await esperar();

    expect(nota(raiz).textContent).toContain('No hay una versión candidata');
    expect(nota(raiz).querySelector('button')).toBeNull();
  });

  test('si no se pueden consultar las versiones lo dice y no abre nada', async () => {
    const editarTema = jest.fn();
    const { raiz } = await montar({
      cliente: clienteFalso({
        listarVersiones: jest.fn(async () => {
          throw new ErrorDelChatbot(null, 0);
        }),
      }),
      editarTema,
    });
    const boton = raiz.querySelector('[data-accion="crear-tema-desde-brecha"]');

    boton.click();
    await esperar();

    expect(editarTema).not.toHaveBeenCalled();
    expect(boton.disabled).toBe(false);
    expect(nota(raiz).textContent).toContain('No se pudo consultar la versión candidata');
  });
});
