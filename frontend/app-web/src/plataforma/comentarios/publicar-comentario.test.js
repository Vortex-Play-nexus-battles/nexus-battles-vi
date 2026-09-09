/**
 * HU-COM-001 — Vista de publicacion de comentarios.
 *
 * Se prueba lo que ve la persona, criterio por criterio de #34:
 *
 *   CA-01  publica con texto, imagen y estrellas -> exito y entra al hilo con
 *          apodo, calificacion y fecha
 *   CA-02  segundo comentario sin estrellas -> se acepta y se explica
 *   CA-03  retenido por el filtro (202), silenciado (403) e imagen no admitida
 *          (422) -> cada uno con su tratamiento, sin comparar textos
 *
 * Mas lo administrativo: sin sesion no se publica, el 409 tiene salida, y
 * los errores de campo van en el campo.
 */

import { jest } from '@jest/globals';

import {
  montarPublicarComentario,
  leerFormulario,
  leerSesion,
  nombresDeImagenes,
  pintarEstrellas,
  agregarAlHilo,
  fechaLegible,
  tonoPara,
} from './publicar-comentario.js';
import { ErrorDeApi, ESTADO } from './cliente-comentarios.js';

const ESTRELLA = (n) =>
  `<input class="estrellas__entrada" type="radio" name="estrellas" value="${n}" id="e-${n}" />` +
  `<label class="estrellas__estrella" for="e-${n}"><svg></svg></label>`;

const HTML = `
  <form id="f" data-producto-id="prod-1" novalidate>
    <div data-zona="aviso" hidden></div>

    <div class="campo campo--area">
      <label class="campo__etiqueta" for="texto">Tu comentario</label>
      <textarea class="campo__control" id="texto" name="texto"></textarea>
    </div>

    <div class="campo">
      <div class="zona-carga" data-zona="carga" role="button" tabindex="0">
        <input class="zona-carga__entrada" type="file" name="imagenes" id="imagenes" multiple />
        <p class="zona-carga__ayuda">Opcional.</p>
        <ul class="zona-carga__miniaturas" data-zona="miniaturas"></ul>
      </div>
    </div>

    <div class="estrellas estrellas--editable" data-zona="calificacion">
      <span class="estrellas__lista">${[1, 2, 3, 4, 5].map(ESTRELLA).join('')}</span>
      <span class="estrellas__detalle">Sin calificar</span>
    </div>
    <button type="button" data-accion="sin-calificar">Sin calificar</button>

    <button type="submit">PUBLICAR COMENTARIO</button>
  </form>

  <section data-zona="hilo">
    <p data-zona="hilo-vacio">Aqui aparece lo que publiques.</p>
    <div data-zona="hilo-lista"></div>
  </section>
`;

const SESION = { usuarioId: 'jugador-7', apodo: 'Simon_P' };

function preparar() {
  document.body.innerHTML = HTML;
  return document.getElementById('f');
}

/** Deja que se resuelvan las promesas encadenadas del manejador de submit. */
const asentar = () => new Promise((resolve) => setTimeout(resolve, 0));

function escribir(formulario, texto) {
  formulario.querySelector('[name="texto"]').value = texto;
}

function calificar(formulario, valor) {
  const entrada = formulario.querySelector(`[name="estrellas"][value="${valor}"]`);
  entrada.checked = true;
  entrada.dispatchEvent(new Event('change', { bubbles: true }));
}

function adjuntar(formulario, nombres) {
  const entrada = formulario.querySelector('[name="imagenes"]');
  const archivos = nombres.map((nombre) => new File(['x'], nombre, { type: 'image/png' }));
  Object.defineProperty(entrada, 'files', { value: archivos, configurable: true });
  entrada.dispatchEvent(new Event('change', { bubbles: true }));
}

function enviar(formulario) {
  formulario.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
  return asentar();
}

function problema(estado, extra = {}) {
  return new ErrorDeApi(
    { type: 'https://nexusbattles.local/errores/x', title: 'Titulo', status: estado, ...extra },
    estado,
  );
}

const publicado = (extra = {}) => ({
  id: 'c-1',
  productoId: 'prod-1',
  autorId: 'jugador-7',
  apodoAutor: 'Simon_P',
  texto: 'Buena espada',
  imagenes: [],
  fechaPublicacion: '2026-09-09T10:00:00Z',
  estado: 'PUBLICADO',
  ...extra,
});

describe('leerSesion', () => {
  test('lee la identidad que dejan las vistas de cuentas', () => {
    const almacen = new Map([
      ['nexus.usuarioId', 'jugador-7'],
      ['nexus.apodoActual', 'Simon_P'],
    ]);
    expect(leerSesion({ getItem: (k) => almacen.get(k) ?? null })).toEqual(SESION);
  });

  test('sin sesion devuelve nulos, no cadenas vacias', () => {
    expect(leerSesion({ getItem: () => null })).toEqual({ usuarioId: null, apodo: null });
  });
});

describe('leerFormulario', () => {
  test('arma PublicacionComentarioRequest con autor, apodo, texto, imagenes y estrellas', () => {
    const formulario = preparar();
    montarPublicarComentario(formulario, { sesion: SESION, publicarImpl: jest.fn() });
    escribir(formulario, '  Buena espada  ');
    adjuntar(formulario, ['espada.png', 'detalle.jpg']);
    calificar(formulario, 4);

    expect(leerFormulario(formulario, SESION)).toEqual({
      autorId: 'jugador-7',
      apodoAutor: 'Simon_P',
      texto: 'Buena espada',
      imagenes: ['espada.png', 'detalle.jpg'],
      estrellas: 4,
    });
  });

  test('sin calificar, estrellas se omite del cuerpo (no viaja null)', () => {
    const formulario = preparar();
    escribir(formulario, 'Sin estrellas');

    const cuerpo = leerFormulario(formulario, SESION);

    expect(cuerpo).not.toHaveProperty('estrellas');
    expect(cuerpo.imagenes).toEqual([]);
  });
});

describe('zona de carga', () => {
  test('pinta una miniatura por archivo, sin repetir nombres, y permite quitarla', () => {
    const formulario = preparar();
    montarPublicarComentario(formulario, { sesion: SESION, publicarImpl: jest.fn() });

    adjuntar(formulario, ['a.png', 'b.png']);
    adjuntar(formulario, ['b.png', 'c.png']);
    expect(nombresDeImagenes(formulario)).toEqual(['a.png', 'b.png', 'c.png']);
    expect(
      formulario
        .querySelector('[data-zona="carga"]')
        .classList.contains('zona-carga--con-archivos'),
    ).toBe(true);

    formulario.querySelector('[data-nombre="b.png"] .zona-carga__quitar').click();
    expect(nombresDeImagenes(formulario)).toEqual(['a.png', 'c.png']);
  });

  test('la zona abre el selector con teclado y clic', () => {
    const formulario = preparar();
    montarPublicarComentario(formulario, { sesion: SESION, publicarImpl: jest.fn() });
    const entrada = formulario.querySelector('[name="imagenes"]');
    const abrir = jest.spyOn(entrada, 'click');

    const zona = formulario.querySelector('[data-zona="carga"]');
    zona.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    zona.click();

    expect(abrir).toHaveBeenCalledTimes(2);
  });
});

describe('calificacion', () => {
  test('pintarEstrellas llena hasta el valor y escribe el detalle textual', () => {
    const formulario = preparar();
    const contenedor = formulario.querySelector('[data-zona="calificacion"]');

    pintarEstrellas(contenedor, 3);
    const llenas = contenedor.querySelectorAll('.estrellas__estrella--llena');
    expect(llenas).toHaveLength(3);
    expect(contenedor.querySelector('.estrellas__detalle').textContent).toBe('3 de 5');

    pintarEstrellas(contenedor, null);
    expect(contenedor.querySelectorAll('.estrellas__estrella--llena')).toHaveLength(0);
    expect(contenedor.querySelector('.estrellas__detalle').textContent).toBe('Sin calificar');
  });

  test('al elegir una estrella se repinta, y «Sin calificar» la quita', () => {
    const formulario = preparar();
    montarPublicarComentario(formulario, { sesion: SESION, publicarImpl: jest.fn() });

    calificar(formulario, 5);
    expect(formulario.querySelectorAll('.estrellas__estrella--llena')).toHaveLength(5);

    formulario.querySelector('[data-accion="sin-calificar"]').click();
    expect(formulario.querySelector('[name="estrellas"]:checked')).toBeNull();
    expect(formulario.querySelectorAll('.estrellas__estrella--llena')).toHaveLength(0);
  });
});

describe('montarPublicarComentario', () => {
  test('sin sesion, deshabilita el formulario y lo explica; no llama al servicio', async () => {
    const formulario = preparar();
    const publicarImpl = jest.fn();

    montarPublicarComentario(formulario, {
      sesion: { usuarioId: null, apodo: null },
      publicarImpl,
    });
    escribir(formulario, 'hola');
    await enviar(formulario);

    expect(formulario.querySelector('[type="submit"]').disabled).toBe(true);
    expect(formulario.querySelector('.aviso--advertencia .aviso__titulo').textContent).toBe(
      'Inicia sesion para comentar',
    );
    expect(publicarImpl).not.toHaveBeenCalled();
  });

  test('texto vacio: marca el campo y no llama al servicio', async () => {
    const formulario = preparar();
    const publicarImpl = jest.fn();
    montarPublicarComentario(formulario, { sesion: SESION, publicarImpl });

    await enviar(formulario);

    expect(publicarImpl).not.toHaveBeenCalled();
    const campo = formulario.querySelector('[name="texto"]').closest('.campo');
    expect(campo.classList.contains('campo--invalido')).toBe(true);
    expect(campo.querySelector('.campo__error').textContent).toBe(
      'Escribe el comentario antes de publicarlo.',
    );
    expect(document.activeElement).toBe(formulario.querySelector('[name="texto"]'));
  });

  test('CA-01: publica con texto, imagen y estrellas; exito y entra al hilo con apodo, estrellas y fecha', async () => {
    const formulario = preparar();
    const comentario = publicado({ imagenes: ['espada.png'], estrellas: 4 });
    const publicarImpl = jest.fn(async () => ({ comentario, estado: ESTADO.PUBLICADO }));
    const alPublicar = jest.fn();
    montarPublicarComentario(formulario, { sesion: SESION, publicarImpl, alPublicar });

    escribir(formulario, 'Buena espada');
    adjuntar(formulario, ['espada.png']);
    calificar(formulario, 4);
    await enviar(formulario);

    expect(publicarImpl).toHaveBeenCalledWith('prod-1', {
      autorId: 'jugador-7',
      apodoAutor: 'Simon_P',
      texto: 'Buena espada',
      imagenes: ['espada.png'],
      estrellas: 4,
    });
    expect(formulario.querySelector('.aviso--exito .aviso__titulo').textContent).toBe(
      'Comentario publicado',
    );

    const articulo = document.querySelector('[data-zona="hilo-lista"] article');
    expect(articulo.querySelector('[data-campo="apodo"]').textContent).toBe('Simon_P');
    expect(articulo.querySelectorAll('.estrellas__estrella--llena')).toHaveLength(4);
    expect(articulo.querySelector('.estrellas__detalle').textContent).toBe('4 de 5');
    expect(articulo.querySelector('time').dateTime).toBe('2026-09-09T10:00:00Z');
    expect(articulo.querySelector('[data-campo="texto"]').textContent).toBe('Buena espada');
    expect(articulo.querySelector('[data-nombre="espada.png"]')).not.toBeNull();
    expect(document.querySelector('[data-zona="hilo-vacio"]').hidden).toBe(true);

    // El formulario queda listo para el siguiente comentario.
    expect(formulario.querySelector('[name="texto"]').value).toBe('');
    expect(nombresDeImagenes(formulario)).toEqual([]);
    expect(formulario.querySelector('[name="estrellas"]:checked')).toBeNull();
    expect(alPublicar).toHaveBeenCalledWith(comentario, ESTADO.PUBLICADO);
  });

  test('CA-02: el segundo comentario llega sin estrellas, se acepta y se explica', async () => {
    const formulario = preparar();
    const publicarImpl = jest.fn(async () => ({
      comentario: publicado({ id: 'c-2', texto: 'Otra opinion' }),
      estado: ESTADO.PUBLICADO,
    }));
    montarPublicarComentario(formulario, { sesion: SESION, publicarImpl });

    escribir(formulario, 'Otra opinion');
    calificar(formulario, 5);
    await enviar(formulario);

    const aviso = formulario.querySelector('.aviso--exito');
    expect(aviso.textContent).toContain('va sin estrellas');
    const articulo = document.querySelector('[data-zona="hilo-lista"] article');
    expect(articulo.querySelector('.estrellas')).toBeNull();
    expect(articulo.querySelector('[data-campo="apodo"]').textContent).toBe('Simon_P');
  });

  test('CA-03: retenido por el filtro (202) -> aviso de informacion y NO entra al hilo', async () => {
    const formulario = preparar();
    const publicarImpl = jest.fn(async () => ({
      comentario: publicado({ estado: 'EN_REVISION' }),
      estado: ESTADO.EN_REVISION,
    }));
    montarPublicarComentario(formulario, { sesion: SESION, publicarImpl });

    escribir(formulario, 'texto senalado');
    await enviar(formulario);

    const aviso = formulario.querySelector('.aviso--info');
    expect(aviso.getAttribute('role')).toBe('status');
    expect(aviso.querySelector('.aviso__titulo').textContent).toBe(
      'Tu comentario esta en revision',
    );
    expect(document.querySelector('[data-zona="hilo-lista"]').children).toHaveLength(0);
    expect(document.querySelector('[data-zona="hilo-vacio"]').hidden).toBe(false);
  });

  test('CA-03: jugador silenciado (403, AUTOR_SILENCIADO) -> advertencia con el detalle del servicio', async () => {
    const formulario = preparar();
    const publicarImpl = jest.fn(async () => {
      throw problema(403, {
        title: 'No puedes publicar',
        detail: 'Tienes una sancion de silencio hasta el viernes.',
        motivo: 'AUTOR_SILENCIADO',
      });
    });
    montarPublicarComentario(formulario, { sesion: SESION, publicarImpl });

    escribir(formulario, 'hola');
    await enviar(formulario);

    const aviso = formulario.querySelector('.aviso--advertencia');
    expect(aviso.getAttribute('role')).toBe('alert');
    expect(aviso.querySelector('.aviso__titulo').textContent).toBe('No puedes publicar');
    expect(aviso.textContent).toContain('sancion de silencio hasta el viernes');
    expect(formulario.querySelector('[type="submit"]').disabled).toBe(false);
  });

  test('CA-03: imagen no admitida (422) -> la zona de carga en error con el motivo escrito', async () => {
    const formulario = preparar();
    const publicarImpl = jest.fn(async () => {
      throw problema(422, {
        title: 'Imagen no admitida',
        detail: 'Solo se admiten png y jpg.',
        motivo: 'FORMATO_DE_IMAGEN_NO_ADMITIDO',
      });
    });
    montarPublicarComentario(formulario, { sesion: SESION, publicarImpl });

    escribir(formulario, 'hola');
    adjuntar(formulario, ['captura.bmp']);
    await enviar(formulario);

    const zona = formulario.querySelector('[data-zona="carga"]');
    expect(zona.classList.contains('zona-carga--error')).toBe(true);
    expect(zona.querySelector('.zona-carga__ayuda').textContent).toBe('Solo se admiten png y jpg.');
    expect(formulario.querySelector('[data-zona="aviso"]').hidden).toBe(true);
    // Lo elegido no se pierde: la persona quita la imagen mala y reintenta.
    expect(nombresDeImagenes(formulario)).toEqual(['captura.bmp']);

    // Al reintentar, el error se limpia y la ayuda vuelve a su texto.
    publicarImpl.mockImplementation(async () => ({
      comentario: publicado(),
      estado: ESTADO.PUBLICADO,
    }));
    await enviar(formulario);
    expect(zona.classList.contains('zona-carga--error')).toBe(false);
    expect(zona.querySelector('.zona-carga__ayuda').textContent).toBe('Opcional.');
  });

  test('409 (calificacion simultanea) -> aviso con salida: publicar sin calificacion', async () => {
    const formulario = preparar();
    const publicarImpl = jest
      .fn()
      .mockImplementationOnce(async () => {
        throw problema(409, {
          title: 'Ya calificaste este producto',
          detail: 'Otra solicitud tuya se adelanto.',
        });
      })
      .mockImplementationOnce(async () => ({
        comentario: publicado(),
        estado: ESTADO.PUBLICADO,
      }));
    montarPublicarComentario(formulario, { sesion: SESION, publicarImpl });

    escribir(formulario, 'hola');
    calificar(formulario, 3);
    await enviar(formulario);

    const boton = formulario.querySelector('[data-accion="reintentar-sin-calificar"]');
    expect(boton).not.toBeNull();
    boton.click();
    await asentar();

    expect(publicarImpl).toHaveBeenCalledTimes(2);
    expect(publicarImpl.mock.calls[1][1]).not.toHaveProperty('estrellas');
    expect(formulario.querySelector('.aviso--exito')).not.toBeNull();
  });

  test('errores[] del servicio marcan el campo concreto en vez de un aviso general', async () => {
    const formulario = preparar();
    const publicarImpl = jest.fn(async () => {
      throw problema(400, {
        errores: [{ campo: 'texto', mensaje: 'Supera el largo maximo.' }],
      });
    });
    montarPublicarComentario(formulario, { sesion: SESION, publicarImpl });

    escribir(formulario, 'x'.repeat(10));
    await enviar(formulario);

    const campo = formulario.querySelector('[name="texto"]').closest('.campo');
    expect(campo.classList.contains('campo--invalido')).toBe(true);
    expect(campo.querySelector('.campo__error').textContent).toBe('Supera el largo maximo.');
    expect(formulario.querySelector('[data-zona="aviso"]').hidden).toBe(true);
  });

  test('sin conexion -> aviso de error y el boton vuelve a su estado', async () => {
    const formulario = preparar();
    const publicarImpl = jest.fn(async () => {
      throw new TypeError('Failed to fetch');
    });
    montarPublicarComentario(formulario, { sesion: SESION, publicarImpl });

    escribir(formulario, 'hola');
    await enviar(formulario);

    const boton = formulario.querySelector('[type="submit"]');
    expect(formulario.querySelector('.aviso--error .aviso__titulo').textContent).toBe(
      'No pudimos contactar con el servicio',
    );
    expect(boton.disabled).toBe(false);
    expect(boton.textContent).toBe('PUBLICAR COMENTARIO');
  });

  test('mientras publica, el boton queda ocupado', async () => {
    const formulario = preparar();
    let liberar;
    const publicarImpl = jest.fn(
      () =>
        new Promise((resolve) => {
          liberar = () => resolve({ comentario: publicado(), estado: ESTADO.PUBLICADO });
        }),
    );
    montarPublicarComentario(formulario, { sesion: SESION, publicarImpl });

    escribir(formulario, 'hola');
    formulario.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

    const boton = formulario.querySelector('[type="submit"]');
    expect(boton.disabled).toBe(true);
    expect(boton.getAttribute('aria-busy')).toBe('true');
    expect(boton.textContent).toBe('Publicando…');

    liberar();
    await asentar();
    expect(boton.disabled).toBe(false);
  });
});

describe('agregarAlHilo y fechaLegible', () => {
  test('fechaLegible devuelve el valor original si la fecha no se puede leer', () => {
    expect(fechaLegible('no-es-fecha')).toBe('no-es-fecha');
    expect(fechaLegible('2026-09-09T10:00:00Z')).not.toBe('2026-09-09T10:00:00Z');
  });

  test('los comentarios nuevos van arriba del hilo', () => {
    preparar();
    const hilo = document.querySelector('[data-zona="hilo"]');
    agregarAlHilo(hilo, publicado({ id: 'c-1', texto: 'primero' }));
    agregarAlHilo(hilo, publicado({ id: 'c-2', texto: 'segundo' }));

    const textos = Array.from(hilo.querySelectorAll('[data-campo="texto"]')).map(
      (n) => n.textContent,
    );
    expect(textos).toEqual(['segundo', 'primero']);
  });
});

describe('tonoPara', () => {
  test('sigue la tabla 4 del mapeo de errores', () => {
    expect(tonoPara(400)).toBe('advertencia');
    expect(tonoPara(403)).toBe('advertencia');
    expect(tonoPara(404)).toBe('info');
    expect(tonoPara(409)).toBe('advertencia');
    expect(tonoPara(422)).toBe('advertencia');
    expect(tonoPara(500)).toBe('error');
  });
});
