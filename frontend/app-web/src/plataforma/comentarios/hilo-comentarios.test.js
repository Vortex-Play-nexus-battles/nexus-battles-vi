/**
 * UXC-3 — el hilo de opiniones dentro del detalle del producto.
 *
 * Se prueba contra dobles de los tres clientes (consultar, publicar, eliminar,
 * reportar), con la forma exacta de `comentarios.yaml` 1.3.0. Los estados que
 * pide el bloque: cargando, vacío, uno, muchos, con imágenes, reportado,
 * error y sin sesión; y los caminos de publicar, retirar y reportar.
 */

import { jest } from '@jest/globals';

import { ErrorDeApi } from './cliente-comentarios.js';
import {
  complementoDeOpiniones,
  masRecientesPrimero,
  montarHiloDeComentarios,
  OPINIONES_POR_TANDA,
  yaCalifico,
} from './hilo-comentarios.js';
import { mensajeDePublicacion } from './redactor-comentario.js';
import { mensajeDelRechazo, RESULTADO_REPORTE } from './reportar-comentario.js';

const YO = 'uid-yo';
const SESION = { yo: YO, apodo: 'yo' };
const SIN_SESION = { yo: null, apodo: null };

const esperar = () => new Promise((r) => setTimeout(r, 0));

function comentario(i, cambios = {}) {
  return {
    id: `c-${i}`,
    productoId: 'p-1',
    autorId: `uid-${i}`,
    apodoAutor: `Jugador ${i}`,
    texto: `Opinión ${i}`,
    imagenes: [],
    fechaPublicacion: `2026-09-${String(10 + i).padStart(2, '0')}T10:00:00Z`,
    estado: 'PUBLICADO',
    ...cambios,
  };
}

function hilo(comentarios, extra = {}) {
  const conEstrellas = comentarios.filter((c) => Number.isInteger(c.estrellas));
  return {
    productoId: 'p-1',
    comentarios,
    total: comentarios.length,
    totalCalificaciones: conEstrellas.length,
    calificacionPromedio: conEstrellas.length
      ? conEstrellas.reduce((s, c) => s + c.estrellas, 0) / conEstrellas.length
      : null,
    ...extra,
  };
}

function montar(opciones = {}) {
  const zona = document.createElement('div');
  document.body.replaceChildren(zona);
  const consultarImpl = opciones.consultarImpl ?? jest.fn().mockResolvedValue(hilo([]));
  const api = montarHiloDeComentarios(zona, {
    productoId: 'p-1',
    sesion: SESION,
    consultarImpl,
    publicarImpl: jest.fn(),
    eliminarImpl: jest.fn(),
    reportarImpl: jest.fn(),
    ...opciones,
  });
  return { zona, consultarImpl, ...api };
}

beforeEach(() => {
  document.body.replaceChildren();
});

describe('lectura', () => {
  test('mientras carga se ve el esqueleto con su nombre', () => {
    const { zona } = montar({ consultarImpl: () => new Promise(() => {}) });

    const carga = zona.querySelector('[data-estado="cargando"]');
    expect(carga).not.toBeNull();
    expect(carga.getAttribute('aria-label')).toBe('Cargando opiniones…');
  });

  test('vacío con sesión: nadie opina todavía, y se invita a ser el primero', async () => {
    const { zona } = montar();
    await esperar();

    const vacio = zona.querySelector('[data-estado="vacio"]');
    expect(vacio.textContent).toMatch(/Todavía nadie opina/);
    expect(vacio.textContent).toMatch(/escribe tu opinión aquí abajo/);
    expect(zona.querySelector('form.redactor-comentario')).not.toBeNull();
    expect(zona.querySelector('.resumen-calificacion').dataset.estado).toBe('sin-valoraciones');
  });

  test('uno: su tarjeta, y el resumen del servicio', async () => {
    const { zona } = montar({
      consultarImpl: jest.fn().mockResolvedValue(hilo([comentario(1, { estrellas: 5 })])),
    });
    await esperar();

    expect(zona.querySelectorAll('.comentario')).toHaveLength(1);
    expect(zona.querySelector('.resumen-calificacion').textContent).toMatch(/1 valoración/);
  });

  test('muchos: del más reciente al más antiguo, de cinco en cinco', async () => {
    const lista = Array.from({ length: 12 }, (_, i) => comentario(i + 1));
    const { zona } = montar({ consultarImpl: jest.fn().mockResolvedValue(hilo(lista)) });
    await esperar();

    const apodos = () =>
      Array.from(zona.querySelectorAll('.comentario__apodo')).map((n) => n.textContent);
    expect(apodos()).toHaveLength(OPINIONES_POR_TANDA);
    expect(apodos()[0]).toBe('Jugador 12');

    const mas = zona.querySelector('[data-accion="ver-mas-opiniones"]');
    expect(mas.textContent).toBe('Ver 5 opiniones más (quedan 7)');
    mas.click();
    expect(apodos()).toHaveLength(10);
    // El foco va a la primera opinión nueva, no al principio.
    expect(document.activeElement).toBe(zona.querySelectorAll('.comentario')[5]);
    zona.querySelector('[data-accion="ver-mas-opiniones"]').click();
    expect(apodos()).toHaveLength(12);
    expect(zona.querySelector('[data-accion="ver-mas-opiniones"]')).toBeNull();
  });

  test('con imágenes: el adjunto con su nombre', async () => {
    const { zona } = montar({
      consultarImpl: jest
        .fn()
        .mockResolvedValue(hilo([comentario(1, { imagenes: ['captura.png'] })])),
    });
    await esperar();

    expect(zona.querySelector('.comentario__adjunto').textContent).toContain('captura.png');
  });

  test('error: se dice, se reintenta, y publicar sigue disponible', async () => {
    const consultarImpl = jest
      .fn()
      .mockRejectedValueOnce(new ErrorDeApi({ title: 'x' }, 503))
      .mockResolvedValue(hilo([comentario(1)]));
    const { zona } = montar({ consultarImpl });
    await esperar();

    const error = zona.querySelector('[data-estado="error"]');
    expect(error.textContent).toMatch(/No pudimos cargar las opiniones/);
    expect(error.textContent).not.toMatch(/503/);
    expect(zona.querySelector('form.redactor-comentario')).not.toBeNull();

    error.querySelector('[data-accion="reintentar"]').click();
    await esperar();
    expect(zona.querySelectorAll('.comentario')).toHaveLength(1);
  });

  test('sin sesión: se lee todo; opinar lleva a entrar, con la vuelta aquí', async () => {
    const { zona } = montar({
      sesion: SIN_SESION,
      consultarImpl: jest.fn().mockResolvedValue(hilo([comentario(1)])),
    });
    await esperar();

    expect(zona.querySelectorAll('.comentario')).toHaveLength(1);
    expect(zona.querySelector('button[data-accion="reportar-comentario"]')).toBeNull();
    expect(zona.querySelector('form.redactor-comentario')).toBeNull();
    const entrar = zona.querySelector('[data-accion="entrar-para-opinar"]');
    expect(entrar.tagName).toBe('A');
    expect(entrar.getAttribute('href')).toMatch(/login/);
    expect(entrar.getAttribute('href')).toMatch(/volver=/);
  });

  test('sin sesión en la portada: «Entrar para opinar» lleva al formulario de al lado', async () => {
    const alPedirEntrada = jest.fn();
    const { zona } = montar({ sesion: SIN_SESION, alPedirEntrada });
    await esperar();

    const entrar = zona.querySelector('[data-accion="entrar-para-opinar"]');
    expect(entrar.tagName).toBe('BUTTON');
    entrar.click();
    expect(alPedirEntrada).toHaveBeenCalled();
  });

  test('ayudantes: orden del hilo y calificación propia', () => {
    const lista = [comentario(1), comentario(2, { autorId: YO, estrellas: 3 })];
    expect(masRecientesPrimero(hilo(lista)).map((c) => c.id)).toEqual(['c-2', 'c-1']);
    expect(yaCalifico(lista, YO)).toBe(true);
    expect(yaCalifico(lista, 'otro')).toBe(false);
    expect(yaCalifico(lista, null)).toBe(false);
  });
});

describe('publicar', () => {
  test('texto, estrellas y el nombre de las imágenes; 201 vuelve a leer el hilo', async () => {
    const consultarImpl = jest
      .fn()
      .mockResolvedValueOnce(hilo([]))
      .mockResolvedValue(hilo([comentario(9, { autorId: YO, estrellas: 5 })]));
    const publicarImpl = jest.fn().mockResolvedValue({
      comentario: comentario(9, { autorId: YO, estrellas: 5 }),
      estado: 'PUBLICADO',
    });
    const { zona } = montar({ consultarImpl, publicarImpl });
    await esperar();

    const formulario = zona.querySelector('form.redactor-comentario');
    formulario.querySelector('textarea').value = '  Muy bueno  ';
    const radio = formulario.querySelectorAll('input[type="radio"]')[4];
    radio.checked = true;
    radio.dispatchEvent(new Event('change', { bubbles: true }));
    formulario.requestSubmit();
    await esperar();
    await esperar();

    expect(publicarImpl).toHaveBeenCalledWith('p-1', { texto: 'Muy bueno', estrellas: 5 });
    expect(consultarImpl).toHaveBeenCalledTimes(2);
    expect(zona.querySelectorAll('.comentario')).toHaveLength(1);
    expect(zona.querySelector('.redactor-comentario .aviso--exito').textContent).toMatch(
      /Opinión publicada/,
    );
    // Ya calificó: el mismo formulario deja de ofrecer estrellas.
    expect(zona.querySelector('.selector-estrellas').disabled).toBe(true);
  });

  test('vacío no se envía: se marca el campo y se enfoca', async () => {
    const publicarImpl = jest.fn();
    const { zona } = montar({ publicarImpl });
    await esperar();

    const formulario = zona.querySelector('form.redactor-comentario');
    formulario.requestSubmit();
    await esperar();

    expect(publicarImpl).not.toHaveBeenCalled();
    const texto = formulario.querySelector('textarea');
    expect(texto.getAttribute('aria-invalid')).toBe('true');
    expect(document.activeElement).toBe(texto);
  });

  test('202: en revisión, dicho, sin volver a leer (no está en el hilo)', async () => {
    const consultarImpl = jest.fn().mockResolvedValue(hilo([]));
    const publicarImpl = jest
      .fn()
      .mockResolvedValue({ comentario: comentario(9), estado: 'EN_REVISION' });
    const { zona } = montar({ consultarImpl, publicarImpl });
    await esperar();

    const formulario = zona.querySelector('form.redactor-comentario');
    formulario.querySelector('textarea').value = 'Texto dudoso';
    formulario.requestSubmit();
    await esperar();

    expect(zona.querySelector('.redactor-comentario .aviso--info').textContent).toMatch(
      /quedó en revisión/,
    );
    expect(consultarImpl).toHaveBeenCalledTimes(1);
  });

  test('con calificación previa en el hilo, las estrellas llegan deshabilitadas y dicen por qué', async () => {
    const { zona } = montar({
      consultarImpl: jest
        .fn()
        .mockResolvedValue(hilo([comentario(1, { autorId: YO, estrellas: 4 })])),
    });
    await esperar();

    const selector = zona.querySelector('.selector-estrellas');
    expect(selector.disabled).toBe(true);
    expect(selector.textContent).toMatch(/Ya calificaste este producto/);
  });

  test('los rechazos del contrato tienen cada uno su frase y su salida', () => {
    expect(
      mensajeDePublicacion(new ErrorDeApi({ motivo: 'AUTOR_SILENCIADO', status: 403 }, 403)),
    ).toEqual(
      expect.objectContaining({
        tono: 'advertencia',
        enlace: expect.objectContaining({ texto: 'Ver mis sanciones' }),
      }),
    );
    expect(
      mensajeDePublicacion(
        new ErrorDeApi({ motivo: 'FORMATO_DE_IMAGEN_NO_ADMITIDO', status: 422 }, 422),
      ).campo,
    ).toBe('imagenes');
    expect(mensajeDePublicacion(new ErrorDeApi({ status: 409 }, 409)).sinCalificar).toBe(true);
    expect(mensajeDePublicacion(new ErrorDeApi({ status: 401 }, 401)).sesion).toBe(true);
    expect(mensajeDePublicacion(new ErrorDeApi({ status: 503 }, 503)).reintentar).toBe(true);
    expect(mensajeDePublicacion(new TypeError('sin red')).reintentar).toBe(true);
    // Nunca el código HTTP ni el nombre de un servicio en la frase.
    for (const estado of [401, 403, 409, 422, 503]) {
      const { titulo, detalle } = mensajeDePublicacion(new ErrorDeApi({ status: estado }, estado));
      expect(`${titulo} ${detalle}`).not.toMatch(/\b\d{3}\b|ms-|comentarios\.yaml/);
    }
  });

  test('409: «Publicar sin estrellas» reenvía sin la calificación', async () => {
    const publicarImpl = jest
      .fn()
      .mockRejectedValueOnce(new ErrorDeApi({ status: 409, title: 'Choque' }, 409))
      .mockResolvedValue({ comentario: comentario(9), estado: 'PUBLICADO' });
    const { zona } = montar({ publicarImpl });
    await esperar();

    const formulario = zona.querySelector('form.redactor-comentario');
    formulario.querySelector('textarea').value = 'Hola';
    const radio = formulario.querySelectorAll('input[type="radio"]')[2];
    radio.checked = true;
    radio.dispatchEvent(new Event('change', { bubbles: true }));
    formulario.requestSubmit();
    await esperar();

    formulario.querySelector('[data-accion="publicar-sin-estrellas"]').click();
    await esperar();

    expect(publicarImpl).toHaveBeenLastCalledWith('p-1', { texto: 'Hola' });
  });

  test('las imágenes elegidas viajan por su nombre y se pueden quitar antes', async () => {
    const publicarImpl = jest
      .fn()
      .mockResolvedValue({ comentario: comentario(9), estado: 'PUBLICADO' });
    const { zona } = montar({ publicarImpl });
    await esperar();

    const formulario = zona.querySelector('form.redactor-comentario');
    const archivo = formulario.querySelector('input[type="file"]');
    Object.defineProperty(archivo, 'files', {
      configurable: true,
      value: [new File(['a'], 'uno.png'), new File(['b'], 'dos.png')],
    });
    archivo.dispatchEvent(new Event('change'));
    expect(formulario.querySelectorAll('.redactor-comentario__miniatura')).toHaveLength(2);

    formulario.querySelector('[aria-label="Quitar uno.png"]').click();
    formulario.querySelector('textarea').value = 'Con foto';
    formulario.requestSubmit();
    await esperar();

    expect(publicarImpl).toHaveBeenCalledWith('p-1', { texto: 'Con foto', imagenes: ['dos.png'] });
  });
});

describe('retirar lo propio', () => {
  test('con confirmación; al aceptar, se elimina y se vuelve a leer', async () => {
    const consultarImpl = jest
      .fn()
      .mockResolvedValueOnce(hilo([comentario(1, { autorId: YO, estrellas: 4 })]))
      .mockResolvedValue(hilo([]));
    const eliminarImpl = jest.fn().mockResolvedValue(undefined);
    const { zona } = montar({ consultarImpl, eliminarImpl });
    await esperar();

    zona.querySelector('[data-accion="eliminar-comentario"]').click();
    const dialogo = document.querySelector('[role="dialog"]');
    expect(dialogo.textContent).toMatch(/tus estrellas dejan de contar/);
    dialogo.querySelector('[data-accion="confirmar"]').click();
    await esperar();
    await esperar();

    expect(eliminarImpl).toHaveBeenCalledWith('p-1', 'c-1');
    expect(zona.querySelector('[data-estado="vacio"]')).not.toBeNull();
    expect(zona.querySelector('.hilo-comentarios__aviso .aviso--exito')).not.toBeNull();
  });

  test('«Conservar» no elimina nada', async () => {
    const eliminarImpl = jest.fn();
    const { zona } = montar({
      consultarImpl: jest.fn().mockResolvedValue(hilo([comentario(1, { autorId: YO })])),
      eliminarImpl,
    });
    await esperar();

    zona.querySelector('[data-accion="eliminar-comentario"]').click();
    document.querySelector('[role="dialog"] [data-accion="cancelar"]').click();
    await esperar();

    expect(eliminarImpl).not.toHaveBeenCalled();
    expect(zona.querySelectorAll('.comentario')).toHaveLength(1);
  });
});

describe('reportar lo ajeno', () => {
  async function abrirReporte(reportarImpl) {
    const { zona } = montar({
      consultarImpl: jest.fn().mockResolvedValue(hilo([comentario(1)])),
      reportarImpl,
    });
    await esperar();
    zona.querySelector('[data-accion="reportar-comentario"]').click();
    const dialogo = document.querySelector('.dialogo--reporte');
    return { zona, dialogo };
  }

  test('sin motivo no se envía; con motivo, el comentario queda marcado en su sitio', async () => {
    const reportarImpl = jest
      .fn()
      .mockResolvedValue({ id: 'r-1', estadoDelComentario: 'EN_REVISION' });
    const { zona, dialogo } = await abrirReporte(reportarImpl);

    expect(dialogo.querySelectorAll('input[type="radio"]')).toHaveLength(6);
    dialogo.querySelector('form').requestSubmit();
    await esperar();
    expect(reportarImpl).not.toHaveBeenCalled();
    expect(dialogo.querySelector('.campo__error').hidden).toBe(false);

    dialogo.querySelector('input[value="SPAM"]').checked = true;
    dialogo.querySelector('textarea').value = '  publicidad  ';
    dialogo.querySelector('form').requestSubmit();
    await esperar();
    await esperar();

    expect(reportarImpl).toHaveBeenCalledWith('p-1', 'c-1', {
      categoria: 'SPAM',
      descripcion: 'publicidad',
    });
    expect(document.querySelector('.dialogo--reporte')).toBeNull();
    const tarjeta = zona.querySelector('.comentario');
    expect(tarjeta.dataset.estado).toBe('REPORTADO');
    expect(document.activeElement).toBe(tarjeta);
  });

  test('409 ya reportado: se dice sin error y, al cerrar, queda marcado', async () => {
    const reportarImpl = jest
      .fn()
      .mockRejectedValue(new ErrorDeApi({ motivo: 'REPORTE_DUPLICADO', status: 409 }, 409));
    const { zona, dialogo } = await abrirReporte(reportarImpl);

    dialogo.querySelector('input[value="ACOSO"]').checked = true;
    dialogo.querySelector('form').requestSubmit();
    await esperar();

    expect(dialogo.textContent).toMatch(/Ya habías reportado este comentario/);
    expect(dialogo.querySelector('[data-accion="enviar-reporte"]').hidden).toBe(true);
    dialogo.querySelector('[data-accion="cancelar"]').click();
    await esperar();

    expect(zona.querySelector('.comentario').dataset.estado).toBe('REPORTADO');
  });

  test('429 límite del día: se dice, y el formulario sigue para cancelar', async () => {
    const reportarImpl = jest
      .fn()
      .mockRejectedValue(new ErrorDeApi({ motivo: 'LIMITE_DE_REPORTES', status: 429 }, 429));
    const { zona, dialogo } = await abrirReporte(reportarImpl);

    dialogo.querySelector('input[value="SPAM"]').checked = true;
    dialogo.querySelector('form').requestSubmit();
    await esperar();

    expect(dialogo.textContent).toMatch(/límite de reportes de hoy/);
    dialogo.querySelector('[data-accion="cancelar"]').click();
    await esperar();
    expect(zona.querySelector('.comentario').dataset.estado).toBeUndefined();
  });

  test('Escape cierra sin reportar nada', async () => {
    const reportarImpl = jest.fn();
    const { zona } = await abrirReporte(reportarImpl);

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await esperar();

    expect(document.querySelector('.dialogo--reporte')).toBeNull();
    expect(reportarImpl).not.toHaveBeenCalled();
    expect(zona.querySelector('.comentario').dataset.estado).toBeUndefined();
  });

  test('las frases del rechazo se deciden por motivo y estado', () => {
    expect(
      mensajeDelRechazo(new ErrorDeApi({ motivo: 'REPORTE_DUPLICADO', status: 409 }, 409))
        .resultado,
    ).toBe(RESULTADO_REPORTE.YA_REPORTADO);
    expect(mensajeDelRechazo(new ErrorDeApi({ status: 404 }, 404)).resultado).toBe(
      RESULTADO_REPORTE.RETIRADO,
    );
    expect(mensajeDelRechazo(new ErrorDeApi({ status: 500 }, 500))).toBeNull();
    expect(mensajeDelRechazo(new Error('otra cosa'))).toBeNull();
  });
});

describe('como complemento de la ficha', () => {
  test('pinta el hilo al final y la valoración compacta bajo el tipo', async () => {
    const ficha = document.createElement('article');
    const tipo = document.createElement('p');
    tipo.className = 'ficha__tipo';
    ficha.append(tipo);
    document.body.replaceChildren(ficha);

    const complemento = complementoDeOpiniones({
      sesion: SIN_SESION,
      consultarImpl: jest.fn().mockResolvedValue(hilo([comentario(1, { estrellas: 4 })])),
    });
    ficha.append(complemento({ id: 'p-1' }, { ficha }));
    await esperar();

    expect(ficha.querySelector('.hilo-comentarios')).not.toBeNull();
    const valoracion = tipo.nextElementSibling;
    expect(valoracion.classList.contains('ficha__valoracion')).toBe(true);
    expect(valoracion.textContent).toMatch(/1 valoración/);
  });

  test('sin id de producto no pinta nada', () => {
    expect(complementoDeOpiniones()({}, {})).toBeNull();
  });
});
