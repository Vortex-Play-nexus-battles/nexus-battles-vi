/**
 * UXC-3 y B3 — estrellas, resumen y tarjeta de comentario.
 *
 * Lo que se fija: que la calificación se lee sin depender del color (nombre
 * accesible y cifra), que «sin valoraciones» no es un cero y que el resumen es
 * el de `GET /rating` (comentarios.yaml 1.5.0), que el texto de una persona
 * entra como texto, que cada quien ve solo la acción que le toca, que una
 * imagen guardada se ve de verdad y que un nombre de archivo antiguo no finge
 * una miniatura que no existe.
 */

import { jest } from '@jest/globals';

import {
  cifraDeCalificacion,
  estrellasDeCalificacion,
  selectorDeEstrellas,
  MAXIMO_ESTRELLAS,
} from './estrellas.js';
import { conCuenta, hayPromedio, resumenDeCalificacion, textoDelResumen } from './resumen.js';
import {
  adjuntosDeComentario,
  esIdDeImagen,
  ESTADO_LOCAL,
  inicialDe,
  LADO_DE_MINIATURA,
  tarjetaDeComentario,
  textoAlternativo,
} from './comentario.js';

const YO = 'uid-yo';
const IMAGEN_1 = '3f1c2b4a-1111-4222-8333-944455566677';
const IMAGEN_2 = '3f1c2b4a-2222-4222-8333-944455566677';
const urlDeImagen = (id) => `/api/v1/comentarios/imagenes/${id}`;

function comentario(cambios = {}) {
  return {
    id: 'c-1',
    productoId: 'p-1',
    autorId: 'uid-otro',
    apodoAutor: 'Lyra',
    texto: 'Buen escudo.',
    imagenes: [],
    estrellas: 4,
    fechaPublicacion: '2026-09-20T15:30:00Z',
    estado: 'PUBLICADO',
    ...cambios,
  };
}

describe('estrellas de solo lectura', () => {
  test('la cifra se escribe como se lee: sin decimales si es entera, uno si no', () => {
    expect(cifraDeCalificacion(4)).toBe('4');
    expect(cifraDeCalificacion(4.33)).toBe('4,3');
    expect(cifraDeCalificacion(null)).toBeNull();
  });

  test('llevan nombre accesible, cifra al lado y tantas llenas como el redondeo', () => {
    const estrellas = estrellasDeCalificacion(4.33);

    expect(estrellas.getAttribute('role')).toBe('img');
    expect(estrellas.getAttribute('aria-label')).toBe(`4,3 de ${MAXIMO_ESTRELLAS} estrellas`);
    expect(estrellas.querySelector('.estrellas__detalle').textContent).toBe('4,3');
    expect(estrellas.querySelectorAll('.estrellas__estrella')).toHaveLength(5);
    expect(estrellas.querySelectorAll('.estrellas__estrella--llena')).toHaveLength(4);
  });

  test('un valor fuera de escala se acota, no rompe', () => {
    expect(estrellasDeCalificacion(9).getAttribute('aria-label')).toBe('5 de 5 estrellas');
    expect(
      estrellasDeCalificacion(-2).querySelectorAll('.estrellas__estrella--llena'),
    ).toHaveLength(0);
  });

  test('decorativas: cinco vacías y ocultas al lector, sin afirmar un «0 de 5»', () => {
    const vacias = estrellasDeCalificacion(0, { decorativas: true });

    expect(vacias.getAttribute('aria-hidden')).toBe('true');
    expect(vacias.getAttribute('role')).toBeNull();
    expect(vacias.getAttribute('aria-label')).toBeNull();
    expect(vacias.querySelectorAll('.estrellas__estrella--llena')).toHaveLength(0);
  });
});

describe('selector de estrellas', () => {
  test('cinco radios con nombre, sin valor de entrada', () => {
    const selector = selectorDeEstrellas();
    document.body.replaceChildren(selector.elemento);

    const radios = selector.elemento.querySelectorAll('input[type="radio"]');
    expect(radios).toHaveLength(5);
    expect(radios[2].closest('label').textContent).toContain('3 estrellas');
    expect(selector.valor()).toBeNull();
    expect(selector.elemento.querySelector('[data-accion="quitar-calificacion"]').hidden).toBe(
      true,
    );
  });

  test('elegir pinta las llenas, dice la cifra y avisa; «Sin calificar» lo deshace', () => {
    const alCambiar = jest.fn();
    const selector = selectorDeEstrellas({ alCambiar });
    document.body.replaceChildren(selector.elemento);
    const radios = selector.elemento.querySelectorAll('input[type="radio"]');

    radios[3].checked = true;
    radios[3].dispatchEvent(new Event('change', { bubbles: true }));

    expect(selector.valor()).toBe(4);
    expect(alCambiar).toHaveBeenLastCalledWith(4);
    expect(selector.elemento.querySelectorAll('.estrellas__estrella--llena')).toHaveLength(4);
    expect(selector.elemento.querySelector('.selector-estrellas__cifra').textContent).toBe(
      '4 de 5 estrellas',
    );

    const quitar = selector.elemento.querySelector('[data-accion="quitar-calificacion"]');
    expect(quitar.hidden).toBe(false);
    quitar.click();
    expect(selector.valor()).toBeNull();
    expect(alCambiar).toHaveBeenLastCalledWith(null);
    expect(selector.elemento.querySelectorAll('.estrellas__estrella--llena')).toHaveLength(0);
  });

  test('deshabilitar deja el porqué a la vista y no conserva ninguna nota', () => {
    const selector = selectorDeEstrellas();
    const radios = selector.elemento.querySelectorAll('input[type="radio"]');
    radios[0].checked = true;

    selector.deshabilitar('Ya calificaste este producto.');

    expect(selector.elemento.disabled).toBe(true);
    expect(selector.valor()).toBeNull();
    expect(selector.elemento.textContent).toContain('Ya calificaste este producto.');
  });

  test('B3: ya no dice «opcional» y avisa de que la calificación es única', () => {
    const selector = selectorDeEstrellas();

    expect(selector.elemento.querySelector('legend').textContent).toBe('Tu calificación');
    expect(selector.elemento.textContent).toMatch(/una vez y no se cambia/);
    expect(selector.elemento.textContent).not.toMatch(/opcional/i);
  });

  test('enfocar lleva el foco a la marcada o, sin nota, a la primera', () => {
    const selector = selectorDeEstrellas();
    document.body.replaceChildren(selector.elemento);
    const radios = selector.elemento.querySelectorAll('input[type="radio"]');

    selector.enfocar();
    expect(document.activeElement).toBe(radios[0]);

    radios[2].checked = true;
    selector.enfocar();
    expect(document.activeElement).toBe(radios[2]);
  });
});

describe('resumen de la calificación (GET /rating)', () => {
  const VACIO = { productoId: 'p-1', promedio: null, total: 0, distribucion: {} };

  test('sin nadie que califique: «sin valoraciones», nunca un cero', () => {
    expect(hayPromedio(VACIO)).toBe(false);
    expect(hayPromedio(null)).toBe(false);
    expect(textoDelResumen(VACIO)).toBe('Sin valoraciones todavía');
    const resumen = resumenDeCalificacion(VACIO);
    expect(resumen.dataset.estado).toBe('sin-valoraciones');
    expect(resumen.textContent).not.toMatch(/\b0\b/);
    expect(resumen.querySelector('[role="img"]')).toBeNull();
  });

  test('las opiniones las da el hilo, aparte: se cuentan aunque nadie califique', () => {
    expect(textoDelResumen(VACIO, { opiniones: 3 })).toBe('Sin valoraciones todavía · 3 opiniones');
    expect(textoDelResumen(VACIO, { opiniones: 0 })).toBe('Sin valoraciones todavía');
  });

  test('con promedio: cifra grande, estrellas con nombre y los dos conteos', () => {
    const datos = { promedio: 4.3, total: 1, distribucion: { 4: 1 } };
    const resumen = resumenDeCalificacion(datos, { opiniones: 2 });

    expect(resumen.dataset.estado).toBe('con-valoraciones');
    expect(resumen.querySelector('.resumen-calificacion__cifra').textContent).toBe('4,3');
    expect(resumen.querySelector('[role="img"]').getAttribute('aria-label')).toBe(
      '4,3 de 5 estrellas',
    );
    expect(resumen.querySelector('.resumen-calificacion__detalle').textContent).toBe(
      '1 valoración · 2 opiniones',
    );
    expect(textoDelResumen(datos, { opiniones: 2 })).toBe('4,3 de 5 · 1 valoración · 2 opiniones');
  });

  test('`total` del resumen son valoraciones, no opiniones', () => {
    const resumen = resumenDeCalificacion({ promedio: 3, total: 7 });

    expect(resumen.querySelector('.resumen-calificacion__detalle').textContent).toBe(
      '7 valoraciones',
    );
  });

  test('compacto: una línea, con la cifra junto a las estrellas', () => {
    const resumen = resumenDeCalificacion({ promedio: 3, total: 5 }, { compacto: true });

    expect(resumen.classList.contains('resumen-calificacion--compacto')).toBe(true);
    expect(resumen.querySelector('.resumen-calificacion__cifra')).toBeNull();
    expect(resumen.querySelector('.estrellas__detalle').textContent).toBe('3');
  });

  test('conCuenta distingue singular y plural', () => {
    expect(conCuenta(1, 'opinión', 'opiniones')).toBe('1 opinión');
    expect(conCuenta(0, 'opinión', 'opiniones')).toBe('0 opiniones');
  });
});

describe('tarjeta de un comentario', () => {
  test('apodo, estrellas, fecha legible con su datetime y el texto como TEXTO', () => {
    const tarjeta = tarjetaDeComentario(
      comentario({ texto: '<img src=x onerror=alert(1)> gran escudo' }),
    );

    expect(tarjeta.querySelector('.comentario__apodo').textContent).toBe('Lyra');
    expect(tarjeta.querySelector('.comentario__avatar').textContent).toBe('L');
    expect(tarjeta.querySelector('[role="img"]').getAttribute('aria-label')).toBe(
      '4 de 5 estrellas',
    );
    const fecha = tarjeta.querySelector('time');
    expect(fecha.getAttribute('datetime')).toBe('2026-09-20T15:30:00Z');
    expect(fecha.textContent).not.toBe('—');
    expect(tarjeta.querySelector('.comentario__texto img')).toBeNull();
    expect(tarjeta.querySelector('.comentario__texto').textContent).toContain('<img');
  });

  test('sin estrellas es normal (segunda opinión del mismo jugador): no se pinta ninguna', () => {
    const tarjeta = tarjetaDeComentario(comentario({ estrellas: undefined }));

    expect(tarjeta.querySelector('.estrellas')).toBeNull();
  });

  test('lo propio lleva «Tú» y «Eliminar», nunca «Reportar»', () => {
    const alEliminar = jest.fn();
    const alReportar = jest.fn();
    const tarjeta = tarjetaDeComentario(comentario({ autorId: YO }), {
      yo: YO,
      alEliminar,
      alReportar,
    });

    expect(tarjeta.classList.contains('comentario--propio')).toBe(true);
    expect(tarjeta.querySelector('.comentario__propio').textContent).toBe('Tú');
    expect(tarjeta.querySelector('[data-accion="reportar-comentario"]')).toBeNull();
    tarjeta.querySelector('[data-accion="eliminar-comentario"]').click();
    expect(alEliminar).toHaveBeenCalledWith(expect.objectContaining({ id: 'c-1' }), tarjeta);
  });

  test('lo ajeno lleva «Reportar», con el apodo en su nombre, y no «Eliminar»', () => {
    const alReportar = jest.fn();
    const tarjeta = tarjetaDeComentario(comentario(), {
      yo: YO,
      alEliminar: jest.fn(),
      alReportar,
    });

    const reportar = tarjeta.querySelector('[data-accion="reportar-comentario"]');
    expect(reportar.getAttribute('aria-label')).toBe('Reportar el comentario de Lyra');
    expect(tarjeta.querySelector('[data-accion="eliminar-comentario"]')).toBeNull();
    reportar.click();
    expect(alReportar).toHaveBeenCalledTimes(1);
  });

  test('sin sesión no se ofrece ninguna acción', () => {
    const tarjeta = tarjetaDeComentario(comentario(), {
      alEliminar: jest.fn(),
      alReportar: jest.fn(),
    });

    expect(tarjeta.querySelector('button')).toBeNull();
  });

  test('reportado por quien mira: se queda en su sitio, marcado y sin botón', () => {
    const tarjeta = tarjetaDeComentario(comentario(), {
      yo: YO,
      estadoLocal: ESTADO_LOCAL.REPORTADO,
      alReportar: jest.fn(),
    });

    expect(tarjeta.dataset.estado).toBe('REPORTADO');
    expect(tarjeta.querySelector('.comentario__estado').textContent).toMatch(/Reportado/);
    expect(tarjeta.querySelector('.comentario__estado').textContent).toMatch(/moderador/);
    expect(tarjeta.querySelector('button')).toBeNull();
  });

  test('la tarjeta se puede enfocar por programa (tras «Ver más» o un reporte)', () => {
    expect(tarjetaDeComentario(comentario()).getAttribute('tabindex')).toBe('-1');
  });

  test('la inicial de un apodo vacío es «?», no una cadena vacía', () => {
    expect(inicialDe('')).toBe('?');
    expect(inicialDe('  ñandú')).toBe('Ñ');
  });

  test('editado por moderación: se dice, porque el texto no es exactamente el del autor', () => {
    const editado = tarjetaDeComentario(comentario({ editado: true }));
    expect(editado.querySelector('.comentario__editado').textContent).toBe(
      'Editado por moderación',
    );
    expect(
      tarjetaDeComentario(comentario({ editado: false })).querySelector('.comentario__editado'),
    ).toBeNull();
    expect(tarjetaDeComentario(comentario()).querySelector('.comentario__editado')).toBeNull();
  });

  test('las imágenes de la tarjeta usan la dirección que le dan, con el apodo en el alt', () => {
    const tarjeta = tarjetaDeComentario(comentario({ imagenes: [IMAGEN_1] }), { urlDeImagen });

    const imagen = tarjeta.querySelector('img.comentario__imagen');
    expect(imagen.getAttribute('src')).toBe(`/api/v1/comentarios/imagenes/${IMAGEN_1}`);
    expect(imagen.getAttribute('alt')).toBe('Imagen que adjuntó Lyra');
  });
});

describe('adjuntos de un comentario', () => {
  test('sin imágenes no hay bloque', () => {
    expect(adjuntosDeComentario([])).toBeNull();
    expect(adjuntosDeComentario(['', '  '])).toBeNull();
    expect(adjuntosDeComentario(undefined)).toBeNull();
  });

  test('un id de imagen es un UUID; un nombre de archivo no', () => {
    expect(esIdDeImagen(IMAGEN_1)).toBe(true);
    expect(esIdDeImagen(` ${IMAGEN_1.toUpperCase()} `)).toBe(true);
    expect(esIdDeImagen('escudo.png')).toBe(false);
    expect(esIdDeImagen(null)).toBe(false);
  });

  test('imágenes guardadas: miniatura real, acotada, perezosa y con alt útil', () => {
    const adjuntos = adjuntosDeComentario([IMAGEN_1, IMAGEN_2], { urlDeImagen, autor: 'Lyra' });

    const imagenes = adjuntos.querySelectorAll('img.comentario__imagen');
    expect(imagenes).toHaveLength(2);
    const [primera] = imagenes;
    expect(primera.getAttribute('src')).toBe(urlDeImagen(IMAGEN_1));
    expect(primera.getAttribute('alt')).toBe('Imagen 1 de 2 que adjuntó Lyra');
    expect(primera.getAttribute('loading')).toBe('lazy');
    expect(primera.getAttribute('width')).toBe(String(LADO_DE_MINIATURA));
    expect(primera.getAttribute('height')).toBe(String(LADO_DE_MINIATURA));
    // El enlace abre la imagen entera y lo dice a quien no ve la pestaña nueva.
    const enlace = primera.closest('a');
    expect(enlace.getAttribute('href')).toBe(urlDeImagen(IMAGEN_1));
    expect(enlace.getAttribute('target')).toBe('_blank');
    expect(enlace.getAttribute('rel')).toBe('noopener');
    expect(enlace.textContent).toContain('se abre en otra pestaña');
    expect(adjuntos.querySelector('ul').getAttribute('aria-label')).toBe('2 imágenes adjuntas');
    expect(adjuntos.querySelector('.comentario__adjuntos-nota')).toBeNull();
    expect(adjuntos.querySelectorAll('[data-imagen-id]')[1].dataset.imagenId).toBe(IMAGEN_2);
  });

  test('sin autor, el alt no se queda vacío', () => {
    expect(textoAlternativo(0, 1, 'un jugador')).toBe('Imagen que adjuntó un jugador');
    const adjuntos = adjuntosDeComentario([IMAGEN_1], { urlDeImagen });
    expect(adjuntos.querySelector('img').getAttribute('alt')).toBe('Imagen que adjuntó un jugador');
  });

  test('si la imagen ya no está, el hueco lo dice en vez de quedarse roto', () => {
    const adjuntos = adjuntosDeComentario([IMAGEN_1], { urlDeImagen, autor: 'Lyra' });

    adjuntos.querySelector('img').dispatchEvent(new Event('error'));

    const item = adjuntos.querySelector('.comentario__adjunto');
    expect(item.dataset.estado).toBe('no-disponible');
    expect(item.querySelector('img')).toBeNull();
    expect(item.textContent).toContain('Imagen no disponible');
  });

  test('nombres de archivo de antes de la 1.4.0: símbolo y nombre, y dicho por qué no hay miniatura', () => {
    const adjuntos = adjuntosDeComentario(['escudo.png', 'detalle-del-filo.jpg'], { urlDeImagen });

    const items = adjuntos.querySelectorAll('.comentario__adjunto');
    expect(items).toHaveLength(2);
    expect(items[0].dataset.nombre).toBe('escudo.png');
    expect(items[0].textContent).toContain('escudo.png');
    // Ni una <img> que finja la miniatura: detrás de un nombre no hay imagen.
    expect(adjuntos.querySelector('img')).toBeNull();
    expect(adjuntos.querySelector('.comentario__adjuntos-nota').textContent).toMatch(
      /antes de que se guardaran/,
    );
  });

  test('sin dirección de imágenes, todo se pinta por su nombre', () => {
    const adjuntos = adjuntosDeComentario([IMAGEN_1]);

    expect(adjuntos.querySelector('img')).toBeNull();
    expect(adjuntos.querySelector('.comentario__adjunto').dataset.nombre).toBe(IMAGEN_1);
  });

  test('el texto de la imagen no se interpreta como marcado', () => {
    const adjuntos = adjuntosDeComentario(['<img src=x onerror=alert(1)>.png'], { urlDeImagen });

    expect(adjuntos.querySelector('img')).toBeNull();
    expect(adjuntos.textContent).toContain('<img src=x');
  });
});
