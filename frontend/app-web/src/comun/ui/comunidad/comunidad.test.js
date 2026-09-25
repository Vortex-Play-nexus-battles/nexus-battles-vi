/**
 * UXC-3 — estrellas, resumen y tarjeta de comentario.
 *
 * Lo que se fija: que la calificación se lee sin depender del color (nombre
 * accesible y cifra), que «sin valoraciones» no es un cero, que el texto de
 * una persona entra como texto, que cada quien ve solo la acción que le toca
 * y que un adjunto no finge una miniatura que no existe.
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
  ESTADO_LOCAL,
  inicialDe,
  tarjetaDeComentario,
} from './comentario.js';

const YO = 'uid-yo';

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
});

describe('resumen de la calificación', () => {
  test('sin nadie que califique: «sin valoraciones», nunca un cero', () => {
    const hilo = { calificacionPromedio: null, totalCalificaciones: 0, total: 0, comentarios: [] };

    expect(hayPromedio(hilo)).toBe(false);
    expect(textoDelResumen(hilo)).toBe('Sin valoraciones todavía');
    const resumen = resumenDeCalificacion(hilo);
    expect(resumen.dataset.estado).toBe('sin-valoraciones');
    expect(resumen.textContent).not.toMatch(/\b0\b/);
    expect(resumen.querySelector('[role="img"]')).toBeNull();
  });

  test('opiniones sin estrellas se cuentan igual', () => {
    const hilo = { calificacionPromedio: null, totalCalificaciones: 0, total: 3 };

    expect(textoDelResumen(hilo)).toBe('Sin valoraciones todavía · 3 opiniones');
  });

  test('con promedio: cifra grande, estrellas con nombre y los dos conteos', () => {
    const hilo = { calificacionPromedio: 4.25, totalCalificaciones: 1, total: 2 };
    const resumen = resumenDeCalificacion(hilo);

    expect(resumen.dataset.estado).toBe('con-valoraciones');
    expect(resumen.querySelector('.resumen-calificacion__cifra').textContent).toBe('4,3');
    expect(resumen.querySelector('[role="img"]').getAttribute('aria-label')).toBe(
      '4,3 de 5 estrellas',
    );
    expect(resumen.querySelector('.resumen-calificacion__detalle').textContent).toBe(
      '1 valoración · 2 opiniones',
    );
    expect(textoDelResumen(hilo)).toBe('4,3 de 5 · 1 valoración · 2 opiniones');
  });

  test('compacto: una línea, con la cifra junto a las estrellas', () => {
    const resumen = resumenDeCalificacion(
      { calificacionPromedio: 3, totalCalificaciones: 5, total: 5 },
      { compacto: true },
    );

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
});

describe('adjuntos de un comentario', () => {
  test('sin nombres no hay bloque', () => {
    expect(adjuntosDeComentario([])).toBeNull();
    expect(adjuntosDeComentario(['', '  '])).toBeNull();
    expect(adjuntosDeComentario(undefined)).toBeNull();
  });

  test('cada imagen con su símbolo y su nombre, y dicho que no hay vista previa', () => {
    const adjuntos = adjuntosDeComentario(['escudo.png', 'detalle-del-filo.jpg']);

    const items = adjuntos.querySelectorAll('.comentario__adjunto');
    expect(items).toHaveLength(2);
    expect(items[0].dataset.nombre).toBe('escudo.png');
    expect(items[0].textContent).toContain('escudo.png');
    expect(adjuntos.querySelector('ul').getAttribute('aria-label')).toBe('2 imágenes adjuntas');
    // Ni una <img> que finja la miniatura: el servicio no guarda la imagen.
    expect(adjuntos.querySelector('img')).toBeNull();
    expect(adjuntos.textContent).toMatch(/vista previa .* no está disponible/i);
  });
});
