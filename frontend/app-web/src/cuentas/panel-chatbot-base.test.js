/**
 * Pestaña de base de conocimiento del panel del asistente — HU-CHA-012
 * (RF-CHA-013).
 */

import { jest } from '@jest/globals';

import { ErrorDelChatbot } from '../comun/cliente-chatbot.js';
import {
  editarTemaEnDialogo,
  erroresDelTema,
  leerArchivoDeExportacion,
  montarBaseConocimiento,
  variantesDesdeTexto,
} from './panel-chatbot-base.js';

const esperar = () => new Promise((resolver) => setTimeout(resolver, 0));

const PRODUCCION = {
  id: 'v-1',
  numero: 1,
  estado: 'PRODUCCION',
  descripcion: 'Base inicial',
  fechaCreacion: '2026-09-01T00:00:00Z',
  fechaDespliegue: '2026-09-01T00:00:00Z',
};
const CANDIDATA = {
  id: 'v-2',
  numero: 2,
  estado: 'BORRADOR',
  descripcion: 'Temas de torneos',
  fechaCreacion: '2026-09-20T00:00:00Z',
  fechaDespliegue: null,
};
const TEMA = {
  id: 't-1',
  clave: 'clave-pujas',
  categoria: 'SUBASTA_Y_COMERCIO',
  tipoRespuesta: 'DIRECTA',
  titulo: 'Cómo pujar',
  variantesEs: ['como pujo', 'hacer una puja'],
  variantesEn: [],
  respuestaEs: 'Entra a Subasta.',
  respuestaEn: null,
  prioridad: 2,
  activo: true,
};

function clienteFalso(sobrescribir = {}) {
  return {
    listarVersiones: jest.fn(async () => [CANDIDATA, PRODUCCION]),
    listarTemas: jest.fn(async () => [TEMA]),
    crearCandidata: jest.fn(async () => CANDIDATA),
    descartarCandidata: jest.fn(async () => null),
    agregarTema: jest.fn(async () => TEMA),
    editarTema: jest.fn(async () => TEMA),
    eliminarTema: jest.fn(async () => null),
    importarTemas: jest.fn(async () => [TEMA, TEMA]),
    exportarVersion: jest.fn(async () => ({ nombre: 'v1.json', contenido: new Blob(['{}']) })),
    ...sobrescribir,
  };
}

async function montar({ cliente = clienteFalso(), confirmar = true, editarTema } = {}) {
  const raiz = document.createElement('div');
  document.body.replaceChildren(raiz);
  const descargarArchivo = jest.fn();
  const confirmarAccion = jest.fn(async () => confirmar);
  const pestana = montarBaseConocimiento(raiz, {
    cliente,
    descargarArchivo,
    confirmarAccion,
    editarTema: editarTema ?? jest.fn(async () => false),
  });
  await pestana.cargar();
  return { raiz, cliente, descargarArchivo, confirmarAccion };
}

describe('funciones puras', () => {
  test('variantesDesdeTexto: una por línea, sin vacías', () => {
    expect(variantesDesdeTexto(' como pujo \n\nhacer una puja\n')).toEqual([
      'como pujo',
      'hacer una puja',
    ]);
  });

  test('erroresDelTema exige título, variante y respuesta, y rechaza comas', () => {
    const vacio = erroresDelTema({
      titulo: ' ',
      variantesEs: [],
      variantesEn: [],
      respuestaEs: '',
      prioridad: -1,
    });
    expect(Object.keys(vacio).sort()).toEqual(
      ['prioridad', 'respuestaEs', 'titulo', 'variantesEs'].sort(),
    );

    const conComa = erroresDelTema({
      titulo: 'x',
      variantesEs: ['como pujo, rápido'],
      variantesEn: [],
      respuestaEs: 'y',
      prioridad: 0,
    });
    expect(conComa.variantesEs).toContain('comas');
  });

  test('leerArchivoDeExportacion acepta una exportación y rechaza lo demás', () => {
    expect(leerArchivoDeExportacion('{"version":1,"temas":[{}]}').temas).toHaveLength(1);
    expect(() => leerArchivoDeExportacion('no es json')).toThrow('JSON');
    expect(() => leerArchivoDeExportacion('{"temas":[]}')).toThrow('temas');
  });
});

describe('versiones', () => {
  test('lista las versiones con su estado y exporta la elegida', async () => {
    const vista = await montar();
    const filas = vista.raiz.querySelectorAll('[data-zona="versiones"] tbody tr');
    expect(filas).toHaveLength(2);
    expect(filas[1].textContent).toContain('En producción');

    vista.raiz.querySelector('[data-accion="exportar-version"][data-version="v-1"]').click();
    await esperar();
    expect(vista.cliente.exportarVersion).toHaveBeenCalledWith('v-1');
    expect(vista.descargarArchivo).toHaveBeenCalledWith(
      expect.objectContaining({ nombre: 'v1.json' }),
    );
  });

  test('si el servicio falla, lo dice y permite reintentar', async () => {
    const listarVersiones = jest
      .fn()
      .mockRejectedValueOnce(new ErrorDelChatbot(null, 0))
      .mockResolvedValueOnce([PRODUCCION]);
    const vista = await montar({ cliente: clienteFalso({ listarVersiones }) });
    expect(vista.raiz.textContent).toContain('no responde');

    vista.raiz.querySelector('[data-accion="reintentar"]').click();
    await esperar();
    expect(listarVersiones).toHaveBeenCalledTimes(2);
  });
});

describe('sin candidata', () => {
  test('ofrece crearla con una descripción opcional', async () => {
    const listarVersiones = jest
      .fn()
      .mockResolvedValueOnce([PRODUCCION])
      .mockResolvedValue([CANDIDATA, PRODUCCION]);
    const vista = await montar({ cliente: clienteFalso({ listarVersiones }) });
    expect(vista.raiz.textContent).toContain('No hay una versión candidata');

    vista.raiz.querySelector('input[name="descripcion"]').value = ' Temas de torneos ';
    vista.raiz
      .querySelector('[data-zona="candidata"] form')
      .dispatchEvent(new Event('submit', { cancelable: true }));
    await esperar();
    await esperar();

    expect(vista.cliente.crearCandidata).toHaveBeenCalledWith('Temas de torneos');
    expect(vista.raiz.textContent).toContain('Versión candidata 2');
  });

  test('si otro administrador ya la creó (409), muestra el estado real y lo avisa', async () => {
    const listarVersiones = jest
      .fn()
      .mockResolvedValueOnce([PRODUCCION])
      .mockResolvedValue([CANDIDATA, PRODUCCION]);
    const vista = await montar({
      cliente: clienteFalso({
        listarVersiones,
        crearCandidata: jest.fn(async () => {
          throw new ErrorDelChatbot({ title: 'Conflict' }, 409);
        }),
      }),
    });
    vista.raiz
      .querySelector('[data-zona="candidata"] form')
      .dispatchEvent(new Event('submit', { cancelable: true }));
    await esperar();
    await esperar();

    expect(vista.raiz.textContent).toContain('Versión candidata 2');
    expect(vista.raiz.querySelector('.panel-chatbot__nota').textContent).not.toBe('');
  });
});

describe('con candidata', () => {
  test('lista sus temas con intención, prioridad y estado', async () => {
    const vista = await montar();
    const fila = vista.raiz.querySelector('[data-zona="temas-candidata"] tbody tr');
    expect(fila.textContent).toContain('Cómo pujar');
    expect(fila.textContent).toContain('como pujo · hacer una puja');
    expect(fila.textContent).toContain('Subastas y comercio');
    expect(fila.textContent).toContain('Activo');
  });

  test('Editar abre el formulario con el tema y recarga si se guardó', async () => {
    const editarTema = jest.fn(async () => true);
    const vista = await montar({ editarTema });

    vista.raiz.querySelector('[data-accion="editar-tema"]').click();
    await esperar();

    expect(editarTema).toHaveBeenCalledWith(expect.objectContaining({ tema: TEMA }));
    await editarTema.mock.calls[0][0].guardar({ titulo: 'nuevo' });
    expect(vista.cliente.editarTema).toHaveBeenCalledWith('t-1', { titulo: 'nuevo' });
    expect(vista.cliente.listarVersiones).toHaveBeenCalledTimes(2);
  });

  test('Eliminar pide confirmación', async () => {
    const vista = await montar({ confirmar: false });
    vista.raiz.querySelector('[data-accion="eliminar-tema"]').click();
    await esperar();
    expect(vista.cliente.eliminarTema).not.toHaveBeenCalled();

    const confirmado = await montar({ confirmar: true });
    confirmado.raiz.querySelector('[data-accion="eliminar-tema"]').click();
    await esperar();
    await esperar();
    expect(confirmado.cliente.eliminarTema).toHaveBeenCalledWith('t-1');
  });

  test('Descartar pide confirmación y descarta', async () => {
    const vista = await montar();
    vista.raiz.querySelector('[data-accion="descartar-candidata"]').click();
    await esperar();
    await esperar();
    expect(vista.confirmarAccion).toHaveBeenCalled();
    expect(vista.cliente.descartarCandidata).toHaveBeenCalled();
  });

  test('Importar lee el archivo, confirma, reemplaza y dice cuántos temas entraron', async () => {
    const vista = await montar();
    const entrada = vista.raiz.querySelector('input[type="file"]');
    const archivo = new File(['{"version":1,"temas":[{"titulo":"a"},{"titulo":"b"}]}'], 'v1.json', {
      type: 'application/json',
    });
    Object.defineProperty(entrada, 'files', { value: [archivo], configurable: true });

    entrada.dispatchEvent(new Event('change'));
    for (let i = 0; i < 5; i += 1) {
      await esperar();
    }

    expect(vista.confirmarAccion).toHaveBeenCalledWith(
      expect.objectContaining({ mensaje: expect.stringContaining('2 del archivo') }),
    );
    expect(vista.cliente.importarTemas).toHaveBeenCalledWith({
      version: 1,
      temas: [{ titulo: 'a' }, { titulo: 'b' }],
    });
    expect(vista.raiz.querySelector('.panel-chatbot__nota').textContent).toBe(
      'Se importaron 2 temas.',
    );
  });

  test('un archivo que no es una exportación no llega al servicio', async () => {
    const vista = await montar();
    const entrada = vista.raiz.querySelector('input[type="file"]');
    Object.defineProperty(entrada, 'files', {
      value: [new File(['hola'], 'x.json')],
      configurable: true,
    });

    entrada.dispatchEvent(new Event('change'));
    for (let i = 0; i < 3; i += 1) {
      await esperar();
    }

    expect(vista.cliente.importarTemas).not.toHaveBeenCalled();
    expect(vista.raiz.querySelector('.panel-chatbot__nota').textContent).toContain('JSON');
  });
});

describe('formulario de un tema', () => {
  function abrir(guardar, tema = null) {
    document.body.replaceChildren();
    const resultado = editarTemaEnDialogo({ titulo: 'Agregar tema', tema, guardar });
    const dialogo = document.querySelector('[role="dialog"]');
    return { resultado, dialogo };
  }

  test('no envía si faltan datos, y marca los campos', async () => {
    const guardar = jest.fn();
    const { dialogo } = abrir(guardar);

    dialogo.querySelector('[data-accion="guardar-tema"]').click();
    await esperar();

    expect(guardar).not.toHaveBeenCalled();
    expect(dialogo.querySelectorAll('.campo--invalido').length).toBeGreaterThan(0);
  });

  test('envía los datos en el formato del servicio y se cierra', async () => {
    const guardar = jest.fn(async () => undefined);
    const { resultado, dialogo } = abrir(guardar);
    dialogo.querySelector('input[name="titulo"]').value = 'Horario de soporte';
    dialogo.querySelector('select[name="categoria"]').value = 'SOPORTE_TECNICO';
    dialogo.querySelector('textarea[name="variantesEs"]').value =
      'horario de soporte\na que hora atienden';
    dialogo.querySelector('textarea[name="respuestaEs"]').value = 'De 8 a 18.';
    dialogo.querySelector('input[name="prioridad"]').value = '3';

    dialogo.querySelector('[data-accion="guardar-tema"]').click();

    await expect(resultado).resolves.toBe(true);
    expect(guardar).toHaveBeenCalledWith({
      titulo: 'Horario de soporte',
      categoria: 'SOPORTE_TECNICO',
      tipoRespuesta: 'DIRECTA',
      variantesEs: ['horario de soporte', 'a que hora atienden'],
      variantesEn: [],
      respuestaEs: 'De 8 a 18.',
      respuestaEn: null,
      prioridad: 3,
      activo: true,
    });
    expect(document.querySelector('[role="dialog"]')).toBeNull();
  });

  test('si el servicio rechaza (400), sigue abierto y muestra el motivo', async () => {
    const guardar = jest.fn(async () => {
      throw new ErrorDelChatbot({ detail: 'Una variante no puede contener comas.' }, 400);
    });
    const { dialogo } = abrir(guardar, TEMA);

    dialogo.querySelector('[data-accion="guardar-tema"]').click();
    await esperar();

    expect(document.querySelector('[role="dialog"]')).not.toBeNull();
    expect(dialogo.textContent).toContain('Una variante no puede contener comas.');
  });

  test('Cancelar cierra sin guardar', async () => {
    const guardar = jest.fn();
    const { resultado, dialogo } = abrir(guardar);
    dialogo.querySelector('[data-accion="cancelar"]').click();

    await expect(resultado).resolves.toBe(false);
    expect(guardar).not.toHaveBeenCalled();
  });
});
