/**
 * UXC-3 y B3 — el hilo de opiniones dentro del detalle del producto.
 *
 * Se prueba contra dobles de los clientes con la forma exacta de
 * `comentarios.yaml` 1.5.0: el hilo lo pagina el servidor (del más reciente al
 * más antiguo, con `total`), el resumen sale de `GET /rating`, la calificación
 * propia de `GET /rating/mia` y las imágenes se sirven por su `id`. Los estados
 * que pide el bloque: cargando, vacío, uno, muchos, con imágenes, reportado,
 * error y sin sesión; y los caminos de calificar, publicar, retirar y reportar.
 */

import { jest } from '@jest/globals';

import { ErrorDeApi } from './cliente-comentarios.js';
import {
  complementoDeOpiniones,
  montarHiloDeComentarios,
  OPINIONES_POR_TANDA,
  TAMANO_MAXIMO_DE_PAGINA,
  tamanoParaRecargar,
  textoDeVerMas,
} from './hilo-comentarios.js';
import { mensajeDelRechazo, RESULTADO_REPORTE } from './reportar-comentario.js';

const YO = 'uid-yo';
const SESION = { yo: YO, apodo: 'yo' };
const SIN_SESION = { yo: null, apodo: null };
const IMAGEN = '3f1c2b4a-1111-4222-8333-944455566677';

const esperar = () => new Promise((r) => setTimeout(r, 0));

function comentario(i, cambios = {}) {
  return {
    id: `c-${i}`,
    productoId: 'p-1',
    autorId: `uid-${i}`,
    apodoAutor: `Jugador ${i}`,
    texto: `Opinión ${i}`,
    imagenes: [],
    estrellas: null,
    fechaPublicacion: `2026-09-${String(10 + (i % 18)).padStart(2, '0')}T10:00:00Z`,
    estado: 'PUBLICADO',
    editado: false,
    ...cambios,
  };
}

function resumen(promedio = null, total = 0) {
  return { productoId: 'p-1', promedio, total, distribucion: { 1: 0, 2: 0, 3: 0, 4: 0, 5: 0 } };
}

/**
 * Un servicio falso que pagina como el de verdad: la lista ya viene del más
 * reciente al más antiguo y cada página es un corte por posición.
 */
function servidor(lista) {
  return jest.fn(async (_productoId, { pagina = 0, tamano = 16 } = {}) => {
    const aplicado = Math.min(tamano, TAMANO_MAXIMO_DE_PAGINA);
    const desde = pagina * aplicado;
    return {
      productoId: 'p-1',
      comentarios: lista.slice(desde, desde + aplicado),
      pagina,
      tamano: aplicado,
      total: lista.length,
      totalPaginas: Math.ceil(lista.length / aplicado),
      calificacionPromedio: null,
      totalCalificaciones: 0,
    };
  });
}

function montar(opciones = {}) {
  const zona = document.createElement('div');
  document.body.replaceChildren(zona);
  const consultarImpl = opciones.consultarImpl ?? servidor([]);
  const resumenImpl = opciones.resumenImpl ?? jest.fn().mockResolvedValue(resumen());
  const miCalificacionImpl = opciones.miCalificacionImpl ?? jest.fn().mockResolvedValue(null);
  const api = montarHiloDeComentarios(zona, {
    productoId: 'p-1',
    sesion: SESION,
    consultarImpl,
    resumenImpl,
    miCalificacionImpl,
    calificarImpl: jest.fn(),
    publicarImpl: jest.fn(),
    subirImagenImpl: jest.fn(),
    eliminarImpl: jest.fn(),
    reportarImpl: jest.fn(),
    urlDeImagenImpl: (id) => `/api/v1/comentarios/imagenes/${id}`,
    ...opciones,
  });
  return { zona, consultarImpl, resumenImpl, miCalificacionImpl, ...api };
}

const apodos = (zona) =>
  Array.from(zona.querySelectorAll('.comentario__apodo')).map((n) => n.textContent);

beforeEach(() => {
  document.body.replaceChildren();
});

describe('lectura del hilo, paginada por el servidor', () => {
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

  test('la primera lectura pide la primera página de cinco al servidor', async () => {
    const { consultarImpl } = montar();
    await esperar();

    expect(consultarImpl).toHaveBeenCalledWith('p-1', { pagina: 0, tamano: OPINIONES_POR_TANDA });
  });

  test('uno: su tarjeta, sin conteo ni «Ver más»', async () => {
    const { zona } = montar({ consultarImpl: servidor([comentario(1)]) });
    await esperar();

    expect(zona.querySelectorAll('.comentario')).toHaveLength(1);
    expect(zona.querySelector('[data-zona="conteo"]')).toBeNull();
    expect(zona.querySelector('[data-accion="ver-mas-opiniones"]')).toBeNull();
  });

  test('muchos: en el orden del servidor, de cinco en cinco, pidiendo cada página', async () => {
    // El servidor ya da del más reciente al más antiguo: la vista no reordena.
    const lista = Array.from({ length: 12 }, (_, i) => comentario(12 - i));
    const consultarImpl = servidor(lista);
    const { zona } = montar({ consultarImpl });
    await esperar();

    expect(apodos(zona)).toEqual([
      'Jugador 12',
      'Jugador 11',
      'Jugador 10',
      'Jugador 9',
      'Jugador 8',
    ]);
    expect(zona.querySelector('[data-zona="conteo"]').textContent).toBe(
      'Mostrando 5 de 12 opiniones, de la más reciente a la más antigua.',
    );
    const mas = zona.querySelector('[data-accion="ver-mas-opiniones"]');
    expect(mas.textContent).toBe('Ver 5 opiniones más (quedan 7)');

    mas.click();
    await esperar();

    expect(consultarImpl).toHaveBeenLastCalledWith('p-1', { pagina: 1, tamano: 5 });
    expect(apodos(zona)).toHaveLength(10);
    expect(apodos(zona)[5]).toBe('Jugador 7');
    // El foco va a la primera opinión nueva, no al principio.
    expect(document.activeElement).toBe(zona.querySelectorAll('.comentario')[5]);
    expect(zona.querySelector('[data-zona="conteo"]').textContent).toMatch(/Mostrando 10 de 12/);

    zona.querySelector('[data-accion="ver-mas-opiniones"]').click();
    await esperar();

    expect(consultarImpl).toHaveBeenLastCalledWith('p-1', { pagina: 2, tamano: 5 });
    expect(apodos(zona)).toHaveLength(12);
    expect(zona.querySelector('[data-accion="ver-mas-opiniones"]')).toBeNull();
    expect(zona.querySelector('[data-zona="conteo"]').textContent).toBe(
      '12 opiniones, de la más reciente a la más antigua.',
    );
  });

  test('si alguien publica entre dos páginas, la repetida se descarta por su id', async () => {
    const lista = Array.from({ length: 8 }, (_, i) => comentario(8 - i));
    const consultarImpl = servidor(lista);
    const { zona } = montar({ consultarImpl });
    await esperar();

    // Llega una nueva arriba: todo se corre una posición.
    lista.unshift(comentario(99));
    zona.querySelector('[data-accion="ver-mas-opiniones"]').click();
    await esperar();

    const ids = Array.from(zona.querySelectorAll('.comentario')).map((c) => c.dataset.comentarioId);
    expect(new Set(ids).size).toBe(ids.length);
    expect(ids).toHaveLength(8);
  });

  test('si la página siguiente falla, lo ya leído se queda y se dice', async () => {
    const lista = Array.from({ length: 7 }, (_, i) => comentario(7 - i));
    const consultarImpl = servidor(lista);
    const { zona } = montar({ consultarImpl });
    await esperar();

    consultarImpl.mockRejectedValueOnce(new ErrorDeApi({ status: 503 }, 503));
    zona.querySelector('[data-accion="ver-mas-opiniones"]').click();
    await esperar();

    expect(apodos(zona)).toHaveLength(5);
    expect(zona.querySelector('.hilo-comentarios__aviso .aviso--error').textContent).toMatch(
      /No pudimos cargar más opiniones/,
    );
    expect(zona.querySelector('[data-accion="ver-mas-opiniones"]').disabled).toBe(false);
  });

  test('con imágenes: miniaturas reales con la dirección del servicio', async () => {
    const { zona } = montar({
      consultarImpl: servidor([comentario(1, { imagenes: [IMAGEN] })]),
    });
    await esperar();

    const imagen = zona.querySelector('.comentario img.comentario__imagen');
    expect(imagen.getAttribute('src')).toBe(`/api/v1/comentarios/imagenes/${IMAGEN}`);
    expect(imagen.getAttribute('alt')).toBe('Imagen que adjuntó Jugador 1');
  });

  test('editado por moderación: la tarjeta lo dice', async () => {
    const { zona } = montar({ consultarImpl: servidor([comentario(1, { editado: true })]) });
    await esperar();

    expect(zona.querySelector('.comentario__editado').textContent).toBe('Editado por moderación');
  });

  test('error: se dice, se reintenta, y calificar y publicar siguen disponibles', async () => {
    const consultarImpl = jest
      .fn()
      .mockRejectedValueOnce(new ErrorDeApi({ title: 'x' }, 503))
      .mockImplementation(servidor([comentario(1)]));
    const { zona } = montar({ consultarImpl });
    await esperar();

    const error = zona.querySelector('[data-estado="error"]');
    expect(error.textContent).toMatch(/No pudimos cargar las opiniones/);
    expect(error.textContent).not.toMatch(/503/);
    expect(zona.querySelector('form.redactor-comentario')).not.toBeNull();
    expect(zona.querySelector('.calificar-producto')).not.toBeNull();

    error.querySelector('[data-accion="reintentar"]').click();
    await esperar();
    expect(zona.querySelectorAll('.comentario')).toHaveLength(1);
  });

  test('sin sesión: se lee todo; ni estrellas ni formulario, y opinar lleva a entrar', async () => {
    const { zona, miCalificacionImpl } = montar({
      sesion: SIN_SESION,
      consultarImpl: servidor([comentario(1)]),
    });
    await esperar();

    expect(zona.querySelectorAll('.comentario')).toHaveLength(1);
    expect(zona.querySelector('button[data-accion="reportar-comentario"]')).toBeNull();
    expect(zona.querySelector('form.redactor-comentario')).toBeNull();
    expect(zona.querySelector('.calificar-producto')).toBeNull();
    expect(miCalificacionImpl).not.toHaveBeenCalled();
    const entrar = zona.querySelector('[data-accion="entrar-para-opinar"]');
    expect(entrar.tagName).toBe('A');
    expect(entrar.getAttribute('href')).toMatch(/login/);
    expect(entrar.getAttribute('href')).toMatch(/volver=/);
    expect(zona.textContent).toMatch(/Para calificar, opinar o reportar/);
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

  test('ayudantes: tamaño al recargar y texto de «Ver más»', () => {
    expect(tamanoParaRecargar(0)).toBe(5);
    expect(tamanoParaRecargar(5)).toBe(5);
    expect(tamanoParaRecargar(9)).toBe(10);
    expect(tamanoParaRecargar(400)).toBe(TAMANO_MAXIMO_DE_PAGINA);
    expect(textoDeVerMas(7)).toBe('Ver 5 opiniones más (quedan 7)');
    expect(textoDeVerMas(1)).toBe('Ver 1 opinión más (queda 1)');
  });
});

describe('el resumen de la calificación sale de GET /rating', () => {
  test('se pinta el del servicio, con las opiniones del hilo al lado', async () => {
    const { zona, resumenImpl } = montar({
      resumenImpl: jest.fn().mockResolvedValue(resumen(4.3, 12)),
      consultarImpl: servidor([comentario(1), comentario(2)]),
    });
    await esperar();

    expect(resumenImpl).toHaveBeenCalledWith('p-1');
    const bloque = zona.querySelector('.hilo-comentarios__cabecera .resumen-calificacion');
    expect(bloque.querySelector('.resumen-calificacion__cifra').textContent).toBe('4,3');
    expect(bloque.querySelector('.resumen-calificacion__detalle').textContent).toBe(
      '12 valoraciones · 2 opiniones',
    );
  });

  test('aunque el hilo traiga otro promedio, manda el de /rating', async () => {
    const consultarImpl = jest.fn().mockResolvedValue({
      productoId: 'p-1',
      comentarios: [comentario(1)],
      pagina: 0,
      tamano: 5,
      total: 1,
      totalPaginas: 1,
      calificacionPromedio: 1,
      totalCalificaciones: 99,
    });
    const { zona } = montar({
      consultarImpl,
      resumenImpl: jest.fn().mockResolvedValue(resumen(4, 3)),
    });
    await esperar();

    expect(zona.querySelector('.resumen-calificacion').textContent).toMatch(/3 valoraciones/);
    expect(zona.querySelector('.resumen-calificacion').textContent).not.toMatch(/99/);
  });

  test('si no se puede leer, se dice y se reintenta; nunca se inventa un cero', async () => {
    const resumenImpl = jest
      .fn()
      .mockRejectedValueOnce(new ErrorDeApi({ status: 503 }, 503))
      .mockResolvedValue(resumen(5, 1));
    const { zona } = montar({ resumenImpl });
    await esperar();

    const bloque = zona.querySelector('.resumen-calificacion');
    expect(bloque.dataset.estado).toBe('error');
    expect(bloque.textContent).toMatch(/No pudimos cargar la valoración/);
    expect(bloque.textContent).not.toMatch(/\b0\b/);

    bloque.querySelector('[data-accion="reintentar-resumen"]').click();
    await esperar();
    expect(zona.querySelector('.resumen-calificacion').dataset.estado).toBe('con-valoraciones');
  });
});

describe('calificar sin comentar', () => {
  test('con sesión y sin calificación: estrellas para calificar, aparte del redactor', async () => {
    const { zona, miCalificacionImpl } = montar();
    await esperar();

    expect(miCalificacionImpl).toHaveBeenCalledWith('p-1');
    const control = zona.querySelector('[data-zona="calificacion"] .calificar-producto');
    expect(control.dataset.estado).toBe('pendiente');
    expect(control.querySelectorAll('input[type="radio"]')).toHaveLength(5);
    // El redactor ya no lleva estrellas.
    expect(zona.querySelector('form.redactor-comentario input[type="radio"]')).toBeNull();
  });

  test('ya calificado: «Tu calificación: N de 5» sin opción de cambiarla', async () => {
    const { zona } = montar({
      miCalificacionImpl: jest
        .fn()
        .mockResolvedValue({ productoId: 'p-1', estrellas: 3, fecha: 'x' }),
    });
    await esperar();

    const control = zona.querySelector('.calificar-producto');
    expect(control.textContent).toContain('Tu calificación: 3 de 5');
    expect(control.querySelector('input, button')).toBeNull();
  });

  test('al calificar, el resumen se repinta con el que devolvió el servicio y el hilo se relee', async () => {
    const consultarImpl = servidor([comentario(1, { autorId: YO })]);
    const resumenImpl = jest.fn().mockResolvedValue(resumen());
    const calificarImpl = jest
      .fn()
      .mockResolvedValue({ productoId: 'p-1', estrellas: 5, fecha: 'x', resumen: resumen(5, 1) });
    const { zona } = montar({ consultarImpl, resumenImpl, calificarImpl });
    await esperar();

    const radio = zona.querySelectorAll('.calificar-producto input[type="radio"]')[4];
    radio.checked = true;
    radio.dispatchEvent(new Event('change', { bubbles: true }));
    zona.querySelector('.calificar-producto form').requestSubmit();
    await esperar();
    await esperar();

    expect(calificarImpl).toHaveBeenCalledWith('p-1', 5);
    expect(zona.querySelector('.hilo-comentarios__cabecera').textContent).toMatch(/1 valoración/);
    // El resumen vino en la respuesta: no hizo falta pedirlo otra vez.
    expect(resumenImpl).toHaveBeenCalledTimes(1);
    // El hilo se relee: las estrellas de mis opiniones son las de mi calificación.
    expect(consultarImpl).toHaveBeenCalledTimes(2);
  });
});

describe('publicar', () => {
  test('201 vuelve a leer el hilo, sin estrellas en el cuerpo y sin perder lo desplegado', async () => {
    const lista = Array.from({ length: 12 }, (_, i) => comentario(12 - i));
    const consultarImpl = servidor(lista);
    const publicarImpl = jest.fn(async () => {
      lista.unshift(comentario(50, { autorId: YO, apodoAutor: 'yo' }));
      return { comentario: lista[0], estado: 'PUBLICADO' };
    });
    const { zona } = montar({ consultarImpl, publicarImpl });
    await esperar();
    zona.querySelector('[data-accion="ver-mas-opiniones"]').click();
    await esperar();

    const formulario = zona.querySelector('form.redactor-comentario');
    formulario.querySelector('textarea').value = '  Muy bueno  ';
    formulario.requestSubmit();
    await esperar();
    await esperar();

    expect(publicarImpl).toHaveBeenCalledWith('p-1', { texto: 'Muy bueno' });
    // Se relee desde el principio con lo que ya estaba desplegado (10).
    expect(consultarImpl).toHaveBeenLastCalledWith('p-1', { pagina: 0, tamano: 10 });
    expect(apodos(zona)[0]).toBe('yo');
    expect(apodos(zona)).toHaveLength(10);
    expect(zona.querySelector('.redactor-comentario .aviso--exito').textContent).toMatch(
      /Opinión publicada/,
    );
  });

  test('202: en revisión, dicho, sin volver a leer (no está en el hilo)', async () => {
    const consultarImpl = servidor([]);
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

  test('si relee y falla, lo que había se queda y se ofrece reintentar', async () => {
    const consultarImpl = servidor([comentario(1)]);
    const publicarImpl = jest
      .fn()
      .mockResolvedValue({ comentario: comentario(2), estado: 'PUBLICADO' });
    const { zona } = montar({ consultarImpl, publicarImpl });
    await esperar();

    consultarImpl.mockRejectedValueOnce(new ErrorDeApi({ status: 503 }, 503));
    const formulario = zona.querySelector('form.redactor-comentario');
    formulario.querySelector('textarea').value = 'Otra';
    formulario.requestSubmit();
    await esperar();
    await esperar();

    expect(zona.querySelectorAll('.comentario')).toHaveLength(1);
    const aviso = zona.querySelector('.hilo-comentarios__aviso .aviso--error');
    expect(aviso.textContent).toMatch(/No pudimos actualizar las opiniones/);
    expect(aviso.querySelector('[data-accion="reintentar-hilo"]')).not.toBeNull();
  });
});

describe('retirar lo propio', () => {
  test('con confirmación que no promete lo que ya no pasa; al aceptar, se elimina y se relee', async () => {
    const lista = [comentario(1, { autorId: YO, estrellas: 4 })];
    const consultarImpl = servidor(lista);
    const eliminarImpl = jest.fn(async () => {
      lista.splice(0, 1);
    });
    const { zona } = montar({ consultarImpl, eliminarImpl });
    await esperar();

    zona.querySelector('[data-accion="eliminar-comentario"]').click();
    const dialogo = document.querySelector('[role="dialog"]');
    // 7.1: retirar el comentario NO retira la calificación.
    expect(dialogo.textContent).toMatch(/Tu calificación del producto se mantiene/);
    expect(dialogo.textContent).not.toMatch(/volver a calificar|dejan de contar/);
    dialogo.querySelector('[data-accion="confirmar"]').click();
    await esperar();
    await esperar();

    expect(eliminarImpl).toHaveBeenCalledWith('p-1', 'c-1');
    expect(zona.querySelector('[data-estado="vacio"]')).not.toBeNull();
    expect(zona.querySelector('.hilo-comentarios__aviso .aviso--exito')).not.toBeNull();
  });

  test('G4 (comentarios.yaml 1.9.0): sin autorId en el hilo, `propio` del servidor decide Eliminar o Reportar', async () => {
    const delHilo = (i, propio) => {
      const sinUid = { ...comentario(i), propio };
      delete sinUid.autorId;
      return sinUid;
    };
    const { zona } = montar({ consultarImpl: servidor([delHilo(1, true), delHilo(2, false)]) });
    await esperar();

    const [mio, ajeno] = zona.querySelectorAll('.comentario');
    expect(mio.querySelector('.comentario__propio').textContent).toBe('Tú');
    expect(mio.querySelector('[data-accion="eliminar-comentario"]')).not.toBeNull();
    expect(mio.querySelector('[data-accion="reportar-comentario"]')).toBeNull();
    expect(ajeno.querySelector('.comentario__propio')).toBeNull();
    expect(ajeno.querySelector('[data-accion="reportar-comentario"]')).not.toBeNull();
    // Nada en la vista lleva el uid de un autor: no lo tiene.
    expect(zona.innerHTML).not.toMatch(/uid-\d/);
  });

  test('«Conservar» no elimina nada', async () => {
    const eliminarImpl = jest.fn();
    const { zona } = montar({
      consultarImpl: servidor([comentario(1, { autorId: YO })]),
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
      consultarImpl: servidor([comentario(1)]),
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

    expect(dialogo.textContent).toMatch(/límite de reportes/);
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
  function fichaCon(tipo = document.createElement('p')) {
    const ficha = document.createElement('article');
    tipo.className = 'ficha__tipo';
    ficha.append(tipo);
    document.body.replaceChildren(ficha);
    return { ficha, tipo };
  }

  test('pinta el hilo al final y la valoración compacta (de /rating) bajo el tipo', async () => {
    const { ficha, tipo } = fichaCon();
    const complemento = complementoDeOpiniones({
      sesion: SIN_SESION,
      consultarImpl: servidor([comentario(1), comentario(2)]),
      resumenImpl: jest.fn().mockResolvedValue(resumen(4, 1)),
    });
    ficha.append(complemento({ id: 'p-1' }, { ficha }));
    await esperar();

    expect(ficha.querySelector('.hilo-comentarios')).not.toBeNull();
    const valoracion = tipo.nextElementSibling;
    expect(valoracion.classList.contains('ficha__valoracion')).toBe(true);
    expect(valoracion.textContent).toMatch(/1 valoración · 2 opiniones/);
    expect(valoracion.querySelector('.resumen-calificacion--compacto')).not.toBeNull();
  });

  test('si el resumen no se puede leer, la cabecera no enseña una valoración inventada', async () => {
    const { ficha } = fichaCon();
    const complemento = complementoDeOpiniones({
      sesion: SIN_SESION,
      consultarImpl: servidor([]),
      resumenImpl: jest.fn().mockRejectedValue(new ErrorDeApi({ status: 503 }, 503)),
    });
    ficha.append(complemento({ id: 'p-1' }, { ficha }));
    await esperar();

    expect(ficha.querySelector('.ficha__valoracion')).toBeNull();
  });

  test('sin id de producto no pinta nada', () => {
    expect(complementoDeOpiniones()({}, {})).toBeNull();
  });
});
