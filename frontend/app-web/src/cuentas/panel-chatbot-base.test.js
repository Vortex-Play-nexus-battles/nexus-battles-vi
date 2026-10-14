/**
 * Pestaña de base de conocimiento del panel del asistente — HU-CHA-012
 * (RF-CHA-013).
 */

import { jest } from '@jest/globals';

import { ErrorDelChatbot } from '../comun/cliente-chatbot.js';
import {
  CATEGORIAS,
  casillasDeIntenciones,
  editarTemaEnDialogo,
  erroresDelTema,
  esCambioConcurrente,
  leerArchivoDeExportacion,
  montarBaseConocimiento,
  nombresDeIntenciones,
  TEXTO_CAMBIO_CONCURRENTE,
  textoDeErrorDeLaBase,
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

  // ms-chatbot.yaml 1.6.0: hasta 5 intenciones adicionales y nunca la principal.
  test('erroresDelTema limita las intenciones adicionales y no admite la principal', () => {
    const base = {
      titulo: 'x',
      categoria: 'PRODUCTO',
      variantesEs: ['a'],
      variantesEn: [],
      respuestaEs: 'y',
      prioridad: 0,
    };
    expect(erroresDelTema({ ...base, intencionesAdicionales: ['FAQ_GENERAL'] })).toEqual({});
    expect(erroresDelTema(base)).toEqual({});

    const seis = Object.keys(CATEGORIAS).filter((c) => c !== 'PRODUCTO' && c !== 'FAQ_GENERAL');
    expect(seis).toHaveLength(6);
    expect(
      erroresDelTema({ ...base, intencionesAdicionales: seis }).intencionesAdicionales,
    ).toContain('como máximo 5');
    expect(
      erroresDelTema({ ...base, intencionesAdicionales: ['PRODUCTO'] }).intencionesAdicionales,
    ).toContain('principal');
  });

  test('nombresDeIntenciones usa los nombres del panel', () => {
    expect(nombresDeIntenciones(['CUENTA_Y_REGISTRO', 'OTRA'])).toEqual([
      'Cuenta y registro',
      'OTRA',
    ]);
    expect(nombresDeIntenciones(undefined)).toEqual([]);
  });

  test('textoDeErrorDeLaBase decide por el motivo y luego por el estado', () => {
    const duplicado = new ErrorDelChatbot(
      { detail: "El titulo 'x' es el mismo que el del tema 'X'.", motivo: 'TEMA_DUPLICADO' },
      409,
    );
    expect(textoDeErrorDeLaBase(duplicado)).toBe("El titulo 'x' es el mismo que el del tema 'X'.");
    expect(textoDeErrorDeLaBase(new ErrorDelChatbot({ motivo: 'TEMA_DUPLICADO' }, 409))).toBe(
      'Ya hay un tema con ese título en la candidata.',
    );

    const concurrente = new ErrorDelChatbot({ detail: 'x', motivo: 'CAMBIO_CONCURRENTE' }, 409);
    expect(esCambioConcurrente(concurrente)).toBe(true);
    expect(esCambioConcurrente(duplicado)).toBe(false);
    expect(textoDeErrorDeLaBase(concurrente)).toBe(TEXTO_CAMBIO_CONCURRENTE);

    const grande = new ErrorDelChatbot(
      { detail: 'El archivo trae 612 temas.', motivo: 'IMPORTACION_DEMASIADO_GRANDE' },
      413,
    );
    expect(textoDeErrorDeLaBase(grande)).toBe('El archivo trae 612 temas.');
    // El 413 del borde llega sin cuerpo del servicio.
    expect(textoDeErrorDeLaBase(new ErrorDelChatbot(null, 413))).toContain('500 temas y 8 MB');

    expect(textoDeErrorDeLaBase(new ErrorDelChatbot({ detail: 'Mal.' }, 400))).toBe('Mal.');
    expect(textoDeErrorDeLaBase(new ErrorDelChatbot(null, 400), 'Rechazado.')).toBe('Rechazado.');
    expect(
      textoDeErrorDeLaBase(new ErrorDelChatbot(null, 400), { siRechazado: 'Rechazado.' }),
    ).toBe('Rechazado.');
    expect(textoDeErrorDeLaBase(new ErrorDelChatbot(null, 0))).toContain('no responde');
  });

  // 1.7.0: cada acción dice lo que falló, no «No se pudieron cargar las analíticas».
  test('textoDeErrorDeLaBase dice qué falló y, en un 409 sin motivo, la explicación propia', () => {
    const caido = textoDeErrorDeLaBase(new ErrorDelChatbot(null, 503), {
      queFallo: 'No se pudo crear la candidata',
    });
    expect(caido).toBe(
      'No se pudo crear la candidata. El servicio del asistente no responde. Vuelve a intentarlo en unos minutos.',
    );
    expect(caido).not.toContain('analíticas');
    expect(
      textoDeErrorDeLaBase(new ErrorDelChatbot({ title: 'Conflict' }, 409), {
        queFallo: 'No se pudo crear la candidata',
        siConflicto: 'Ya hay una.',
      }),
    ).toBe('Ya hay una.');
    expect(
      textoDeErrorDeLaBase(new ErrorDelChatbot(null, 403), { queFallo: 'No se pudo X' }),
    ).toContain('permiso');
  });
});

describe('casillas «También responde a»', () => {
  test('una casilla por categoría, con su nombre; la principal desactivada', () => {
    const grupo = casillasDeIntenciones({ principal: 'PRODUCTO', marcadas: ['FAQ_GENERAL'] });
    const casillas = [...grupo.elemento.querySelectorAll('input[name="intencionesAdicionales"]')];

    expect(grupo.elemento.tagName).toBe('FIELDSET');
    expect(grupo.elemento.querySelector('legend').textContent).toBe('También responde a');
    expect(casillas.map((c) => c.value)).toEqual(Object.keys(CATEGORIAS));
    expect(grupo.elemento.textContent).toContain('Preguntas frecuentes');
    expect(casillas.find((c) => c.value === 'PRODUCTO').disabled).toBe(true);
    expect(grupo.leer()).toEqual(['FAQ_GENERAL']);
  });

  test('cambiar la principal desmarca y desactiva su casilla, y libera la anterior', () => {
    const grupo = casillasDeIntenciones({
      principal: 'PRODUCTO',
      marcadas: ['FAQ_GENERAL', 'SOPORTE_TECNICO'],
    });
    const casilla = (valor) => grupo.elemento.querySelector(`input[value="${valor}"]`);

    grupo.cambiarPrincipal('FAQ_GENERAL');

    expect(casilla('FAQ_GENERAL').disabled).toBe(true);
    expect(casilla('FAQ_GENERAL').checked).toBe(false);
    expect(casilla('PRODUCTO').disabled).toBe(false);
    expect(grupo.leer()).toEqual(['SOPORTE_TECNICO']);
  });

  test('marcarError lo muestra y lo asocia al grupo', () => {
    const grupo = casillasDeIntenciones({ principal: 'PRODUCTO' });
    const error = grupo.elemento.querySelector('.campo__error');

    grupo.marcarError('Demasiadas.');
    expect(grupo.elemento.classList.contains('campo--invalido')).toBe(true);
    expect(error.hidden).toBe(false);
    expect(grupo.elemento.getAttribute('aria-describedby')).toContain(error.id);

    grupo.marcarError(null);
    expect(error.hidden).toBe(true);
    expect(grupo.elemento.getAttribute('aria-describedby')).not.toContain(error.id);
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
    expect(vista.raiz.querySelector('.panel-chatbot__nota').textContent).toContain(
      'No se pudo crear la candidata',
    );
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
    expect(fila.textContent).not.toContain('También:');
    // 1.17.0: dice si le falta el título en inglés.
    expect(fila.querySelector('[data-titulo-en="no"]').textContent).toBe('Sin título en inglés');
  });

  test('con título en inglés, lo muestra en la lista', async () => {
    const vista = await montar({
      cliente: clienteFalso({
        listarTemas: jest.fn(async () => [{ ...TEMA, tituloEn: 'How to bid' }]),
      }),
    });
    const fila = vista.raiz.querySelector('[data-zona="temas-candidata"] tbody tr');
    expect(fila.querySelector('[data-titulo-en="si"]').textContent).toBe('En inglés: How to bid');
  });

  // 1.6.0: la columna «Intención» dice también las intenciones adicionales.
  test('la columna Intención muestra las intenciones adicionales', async () => {
    const conIntenciones = {
      ...TEMA,
      intencionesAdicionales: ['PRODUCTO', 'CUENTA_Y_REGISTRO'],
    };
    const vista = await montar({
      cliente: clienteFalso({ listarTemas: jest.fn(async () => [conIntenciones]) }),
    });
    const celda = vista.raiz.querySelectorAll('[data-zona="temas-candidata"] tbody tr td')[1];
    expect(celda.textContent).toContain('Subastas y comercio');
    expect(celda.querySelector('.t-meta').textContent).toBe(
      'También: Productos · Cuenta y registro',
    );
  });

  test('si otro administrador cambió la base a la vez, lo dice y recarga', async () => {
    const vista = await montar({
      cliente: clienteFalso({
        eliminarTema: jest.fn(async () => {
          throw new ErrorDelChatbot(
            { title: 'Cambio simultaneo', motivo: 'CAMBIO_CONCURRENTE' },
            409,
          );
        }),
      }),
    });
    vista.raiz.querySelector('[data-accion="eliminar-tema"]').click();
    await esperar();
    await esperar();

    expect(vista.raiz.querySelector('.panel-chatbot__nota').textContent).toBe(
      TEXTO_CAMBIO_CONCURRENTE,
    );
    expect(vista.cliente.listarVersiones).toHaveBeenCalledTimes(2);
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

  // 1.6.0: 413 (más de 500 temas o de 8 MB) y 409 TEMA_DUPLICADO del archivo.
  test.each([
    [
      new ErrorDelChatbot(
        {
          detail: 'El archivo trae 612 temas y se importan como maximo 500.',
          motivo: 'IMPORTACION_DEMASIADO_GRANDE',
        },
        413,
      ),
      'El archivo trae 612 temas y se importan como maximo 500.',
    ],
    [new ErrorDelChatbot(null, 413), 'se importan como máximo 500 temas y 8 MB'],
    [
      new ErrorDelChatbot(
        { detail: 'Los temas 1 y 2 del archivo tienen el mismo titulo.', motivo: 'TEMA_DUPLICADO' },
        409,
      ),
      'Los temas 1 y 2 del archivo tienen el mismo titulo.',
    ],
  ])('si la importación se rechaza (%#), dice por qué', async (rechazo, texto) => {
    const vista = await montar({
      cliente: clienteFalso({
        importarTemas: jest.fn(async () => {
          throw rechazo;
        }),
      }),
    });
    const entrada = vista.raiz.querySelector('input[type="file"]');
    Object.defineProperty(entrada, 'files', {
      value: [new File(['{"temas":[{"titulo":"a"}]}'], 'v1.json')],
      configurable: true,
    });

    entrada.dispatchEvent(new Event('change'));
    for (let i = 0; i < 5; i += 1) {
      await esperar();
    }

    expect(vista.raiz.querySelector('.panel-chatbot__nota').textContent).toContain(texto);
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
      tituloEn: null,
      categoria: 'SOPORTE_TECNICO',
      intencionesAdicionales: [],
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

  // 1.6.0: «También responde a», debajo de la intención principal.
  test('envía las intenciones adicionales marcadas, sin la principal', async () => {
    const guardar = jest.fn(async () => undefined);
    const { resultado, dialogo } = abrir(guardar, TEMA);
    const casilla = (valor) =>
      dialogo.querySelector(`input[name="intencionesAdicionales"][value="${valor}"]`);
    const leyendas = [...dialogo.querySelectorAll('.campo__etiqueta')].map((e) => e.textContent);
    expect(leyendas.indexOf('También responde a')).toBe(
      leyendas.indexOf('Intención (categoría)') + 1,
    );
    expect(casilla('SUBASTA_Y_COMERCIO').disabled).toBe(true);

    casilla('PRODUCTO').checked = true;
    casilla('CUENTA_Y_REGISTRO').checked = true;
    // Si la principal pasa a ser una de las marcadas, deja de ser adicional.
    const categoria = dialogo.querySelector('select[name="categoria"]');
    categoria.value = 'PRODUCTO';
    categoria.dispatchEvent(new Event('change'));
    expect(casilla('PRODUCTO').disabled).toBe(true);
    expect(casilla('SUBASTA_Y_COMERCIO').disabled).toBe(false);

    dialogo.querySelector('[data-accion="guardar-tema"]').click();

    await expect(resultado).resolves.toBe(true);
    expect(guardar).toHaveBeenCalledWith(
      expect.objectContaining({
        categoria: 'PRODUCTO',
        intencionesAdicionales: ['CUENTA_Y_REGISTRO'],
      }),
    );
  });

  // 1.17.0 (§7.4.2): el título en inglés, opcional, debajo del de español.
  test('envía el título en inglés y, al editar, lo muestra', async () => {
    const guardar = jest.fn(async () => undefined);
    const { resultado, dialogo } = abrir(guardar, { ...TEMA, tituloEn: 'How to bid' });
    const enIngles = dialogo.querySelector('input[name="tituloEn"]');
    expect(enIngles.value).toBe('How to bid');
    expect(enIngles.maxLength).toBe(150);
    const etiquetas = [...dialogo.querySelectorAll('.campo__etiqueta')].map((e) => e.textContent);
    expect(etiquetas.indexOf('Título en inglés (opcional)')).toBe(etiquetas.indexOf('Título') + 1);

    enIngles.value = '  How to place a bid  ';
    dialogo.querySelector('[data-accion="guardar-tema"]').click();

    await expect(resultado).resolves.toBe(true);
    expect(guardar).toHaveBeenCalledWith(
      expect.objectContaining({ titulo: 'Cómo pujar', tituloEn: 'How to place a bid' }),
    );
  });

  test('un título en inglés igual al de español no se envía', async () => {
    const guardar = jest.fn();
    const { dialogo } = abrir(guardar, TEMA);
    dialogo.querySelector('input[name="tituloEn"]').value = 'cómo PUJAR';

    dialogo.querySelector('[data-accion="guardar-tema"]').click();
    await esperar();

    expect(guardar).not.toHaveBeenCalled();
    expect(dialogo.textContent).toContain('Es el mismo título en español');
    expect(
      erroresDelTema({ ...TEMA, tituloEn: 'Otro', variantesEs: ['x'], variantesEn: [] }),
    ).toEqual({});
  });

  test('al editar marca las intenciones que el tema ya tiene', () => {
    const { dialogo } = abrir(jest.fn(), {
      ...TEMA,
      intencionesAdicionales: ['FAQ_GENERAL', 'PRODUCTO'],
    });
    const marcadas = [
      ...dialogo.querySelectorAll('input[name="intencionesAdicionales"]:checked'),
    ].map((c) => c.value);
    expect(marcadas).toEqual(['PRODUCTO', 'FAQ_GENERAL']);
  });

  test('con más de 5 intenciones no envía y lo marca en el grupo', async () => {
    const guardar = jest.fn();
    const { dialogo } = abrir(guardar, TEMA);
    for (const valor of [
      'PRODUCTO',
      'MECANICA_JUEGO',
      'MODALIDAD_JUEGO',
      'CUENTA_Y_REGISTRO',
      'SOPORTE_TECNICO',
      'FAQ_GENERAL',
    ]) {
      dialogo.querySelector(`input[name="intencionesAdicionales"][value="${valor}"]`).checked =
        true;
    }

    dialogo.querySelector('[data-accion="guardar-tema"]').click();
    await esperar();

    expect(guardar).not.toHaveBeenCalled();
    const grupo = dialogo.querySelector('[data-campo="intencionesAdicionales"]');
    expect(grupo.classList.contains('campo--invalido')).toBe(true);
    expect(grupo.textContent).toContain('como máximo 5');
  });

  test('si el título ya es de otro tema (409 TEMA_DUPLICADO), sigue abierto y lo dice', async () => {
    const guardar = jest.fn(async () => {
      throw new ErrorDelChatbot(
        {
          title: 'Tema duplicado',
          detail: "El titulo 'como pujar' es el mismo que el del tema 'Cómo pujar'.",
          motivo: 'TEMA_DUPLICADO',
        },
        409,
      );
    });
    const { dialogo } = abrir(guardar, TEMA);

    dialogo.querySelector('[data-accion="guardar-tema"]').click();
    await esperar();

    expect(document.querySelector('[role="dialog"]')).not.toBeNull();
    expect(dialogo.querySelector('[role="alert"]').textContent).toBe(
      "El titulo 'como pujar' es el mismo que el del tema 'Cómo pujar'.",
    );
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
