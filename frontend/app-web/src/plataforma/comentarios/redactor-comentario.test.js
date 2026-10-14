/**
 * UXC-3 y B3 — el redactor de opiniones con imágenes reales y sin estrellas.
 *
 * Contra dobles de `subirImagen` y `publicarComentario` con la forma exacta de
 * `comentarios.yaml` 1.5.0: cada imagen se sube al elegirla y el comentario
 * viaja con sus `id` (máximo tres); 413 y 415 dicen por qué, y solo lo que se
 * puede arreglar reintentando ofrece «Reintentar».
 */

import { jest } from '@jest/globals';

import { ErrorDeApi, MAXIMO_DE_CARACTERES, MOTIVO, TIPO } from './cliente-comentarios.js';
import {
  caracteresDe,
  ESTADO_IMAGEN,
  FORMATOS_DE_IMAGEN,
  mensajeDePublicacion,
  mensajeDeSubida,
  redactorDeComentario,
} from './redactor-comentario.js';

const esperar = () => new Promise((resolver) => setTimeout(resolver, 0));

const ID = (n) => `3f1c2b4a-000${n}-4222-8333-944455566677`;

function subidaCon(id) {
  return { id, tipo: 'image/png', tamano: 4, url: `/api/v1/comentarios/imagenes/${id}` };
}

/** Promesas que la prueba resuelve cuando quiere. */
function diferida() {
  let resolver;
  let rechazar;
  const promesa = new Promise((si, no) => {
    resolver = si;
    rechazar = no;
  });
  return { promesa, resolver, rechazar };
}

function montar(opciones = {}) {
  const publicarImpl =
    opciones.publicarImpl ??
    jest.fn().mockResolvedValue({ comentario: { id: 'c-1' }, estado: 'PUBLICADO' });
  const subirImagenImpl = opciones.subirImagenImpl ?? jest.fn();
  const liberarUrl = jest.fn();
  const alPublicar = jest.fn();
  const redactor = redactorDeComentario({
    productoId: 'p-1',
    publicarImpl,
    subirImagenImpl,
    alPublicar,
    crearUrl: (archivo) => `blob:${archivo.name}`,
    liberarUrl,
  });
  document.body.replaceChildren(redactor.elemento);
  return { formulario: redactor.elemento, publicarImpl, subirImagenImpl, liberarUrl, alPublicar };
}

function elegirArchivos(formulario, nombres) {
  const entrada = formulario.querySelector('input[type="file"]');
  Object.defineProperty(entrada, 'files', {
    configurable: true,
    value: nombres.map((nombre) => new File(['x'], nombre, { type: 'image/png' })),
  });
  entrada.dispatchEvent(new Event('change'));
  return entrada;
}

const miniaturas = (formulario) =>
  Array.from(formulario.querySelectorAll('.redactor-comentario__miniatura'));

beforeEach(() => {
  document.body.replaceChildren();
});

describe('sin estrellas (B3)', () => {
  test('el redactor ya no ofrece calificar ni manda estrellas', async () => {
    const { formulario, publicarImpl } = montar();

    expect(formulario.querySelector('.selector-estrellas')).toBeNull();
    expect(formulario.querySelector('input[type="radio"]')).toBeNull();

    formulario.querySelector('textarea').value = '  Muy buena  ';
    formulario.requestSubmit();
    await esperar();

    expect(publicarImpl).toHaveBeenCalledWith('p-1', { texto: 'Muy buena' });
  });

  test('ninguna frase de antes: ni «vista previa aún no», ni «nombre de cada imagen»', () => {
    const { formulario } = montar();

    expect(formulario.textContent).not.toMatch(/vista previa|nombre de cada imagen/i);
    expect(formulario.textContent).toMatch(/Hasta 3 imágenes JPEG, PNG o WebP de 2 MB/);
    expect(formulario.querySelector('input[type="file"]').getAttribute('accept')).toBe(
      FORMATOS_DE_IMAGEN,
    );
  });
});

describe('imágenes: se suben al elegirlas', () => {
  test('cada una se sube al momento, dice que sube y luego que está lista', async () => {
    const subidas = [diferida(), diferida()];
    const subirImagenImpl = jest
      .fn()
      .mockReturnValueOnce(subidas[0].promesa)
      .mockReturnValueOnce(subidas[1].promesa);
    const { formulario } = montar({ subirImagenImpl });

    elegirArchivos(formulario, ['uno.png', 'dos.png']);

    expect(subirImagenImpl).toHaveBeenCalledTimes(2);
    expect(subirImagenImpl.mock.calls[0][0].name).toBe('uno.png');
    let items = miniaturas(formulario);
    expect(items.map((li) => li.dataset.estado)).toEqual([
      ESTADO_IMAGEN.SUBIENDO,
      ESTADO_IMAGEN.SUBIENDO,
    ]);
    expect(items[0].getAttribute('aria-busy')).toBe('true');
    expect(items[0].querySelector('progress').getAttribute('aria-label')).toBe('Subiendo uno.png');
    expect(items[0].textContent).toContain('Subiendo…');
    // La miniatura es el archivo elegido, en el navegador.
    expect(items[0].querySelector('img').getAttribute('src')).toBe('blob:uno.png');

    subidas[0].resolver(subidaCon(ID(1)));
    await esperar();

    items = miniaturas(formulario);
    expect(items[0].dataset.estado).toBe(ESTADO_IMAGEN.LISTA);
    expect(items[0].textContent).toContain('Lista');
    expect(items[0].querySelector('progress')).toBeNull();
    expect(items[1].dataset.estado).toBe(ESTADO_IMAGEN.SUBIENDO);
    expect(formulario.querySelector('[data-zona="anuncio-imagenes"]').textContent).toBe(
      'uno.png: lista para publicar.',
    );
  });

  test('se publica con los id de las imágenes, en su orden', async () => {
    const subirImagenImpl = jest
      .fn()
      .mockResolvedValueOnce(subidaCon(ID(1)))
      .mockResolvedValueOnce(subidaCon(ID(2)));
    const { formulario, publicarImpl, liberarUrl, alPublicar } = montar({ subirImagenImpl });

    elegirArchivos(formulario, ['uno.png', 'dos.png']);
    await esperar();
    formulario.querySelector('textarea').value = 'Con fotos';
    formulario.requestSubmit();
    await esperar();

    expect(publicarImpl).toHaveBeenCalledWith('p-1', {
      texto: 'Con fotos',
      imagenes: [ID(1), ID(2)],
    });
    expect(alPublicar).toHaveBeenCalled();
    // Publicado: el formulario queda limpio y las URL del navegador, liberadas.
    expect(miniaturas(formulario)).toHaveLength(0);
    expect(liberarUrl).toHaveBeenCalledWith('blob:uno.png');
    expect(liberarUrl).toHaveBeenCalledWith('blob:dos.png');
  });

  test('publicar con una todavía subiendo espera a que termine', async () => {
    const subida = diferida();
    const subirImagenImpl = jest.fn().mockReturnValue(subida.promesa);
    const { formulario, publicarImpl } = montar({ subirImagenImpl });

    elegirArchivos(formulario, ['uno.png']);
    formulario.querySelector('textarea').value = 'Espera';
    formulario.requestSubmit();
    await esperar();

    const boton = formulario.querySelector('[data-accion="publicar-opinion"]');
    expect(publicarImpl).not.toHaveBeenCalled();
    expect(boton.disabled).toBe(true);
    expect(boton.textContent).toBe('Subiendo imágenes…');

    subida.resolver(subidaCon(ID(7)));
    await esperar();
    await esperar();

    expect(publicarImpl).toHaveBeenCalledWith('p-1', { texto: 'Espera', imagenes: [ID(7)] });
  });

  test('como mucho tres: las demás se quedan fuera, se dice, y el selector se apaga', () => {
    const subirImagenImpl = jest.fn(() => new Promise(() => {}));
    const { formulario } = montar({ subirImagenImpl });

    const entrada = elegirArchivos(formulario, ['1.png', '2.png', '3.png', '4.png', '5.png']);

    expect(subirImagenImpl).toHaveBeenCalledTimes(3);
    expect(miniaturas(formulario)).toHaveLength(3);
    expect(entrada.disabled).toBe(true);
    expect(formulario.querySelector('.aviso--advertencia').textContent).toMatch(
      /Caben 3 imágenes por opinión.*2 de las que elegiste se quedaron fuera/,
    );
  });

  test('quitar una la saca, libera su URL, reactiva el selector y le da el foco', () => {
    const subirImagenImpl = jest.fn(() => new Promise(() => {}));
    const { formulario, liberarUrl } = montar({ subirImagenImpl });

    const entrada = elegirArchivos(formulario, ['1.png', '2.png', '3.png']);
    formulario.querySelector('[aria-label="Quitar 2.png"]').click();

    expect(miniaturas(formulario).map((li) => li.textContent)).toEqual([
      expect.stringContaining('1.png'),
      expect.stringContaining('3.png'),
    ]);
    expect(liberarUrl).toHaveBeenCalledWith('blob:2.png');
    expect(entrada.disabled).toBe(false);
    expect(document.activeElement).toBe(entrada);
  });

  test('una que se quita mientras sube no vuelve a aparecer al terminar', async () => {
    const subida = diferida();
    const { formulario, publicarImpl } = montar({
      subirImagenImpl: jest.fn().mockReturnValue(subida.promesa),
    });

    elegirArchivos(formulario, ['uno.png']);
    formulario.querySelector('[aria-label="Quitar uno.png"]').click();
    subida.resolver(subidaCon(ID(1)));
    await esperar();

    expect(miniaturas(formulario)).toHaveLength(0);
    formulario.querySelector('textarea').value = 'Sin fotos';
    formulario.requestSubmit();
    await esperar();
    expect(publicarImpl).toHaveBeenCalledWith('p-1', { texto: 'Sin fotos' });
  });
});

describe('imágenes: rechazos de la subida', () => {
  test('413: dice por qué y NO ofrece reintentar (la misma imagen volvería a pesar lo mismo)', async () => {
    const { formulario } = montar({
      subirImagenImpl: jest
        .fn()
        .mockRejectedValue(
          new ErrorDeApi({ type: TIPO.IMAGEN_DEMASIADO_GRANDE, status: 413 }, 413),
        ),
    });

    elegirArchivos(formulario, ['enorme.png']);
    await esperar();

    const [item] = miniaturas(formulario);
    expect(item.dataset.estado).toBe(ESTADO_IMAGEN.ERROR);
    expect(item.textContent).toContain('Pesa más de 2 MB o mide más de 4096 píxeles de lado.');
    expect(item.querySelector('[data-accion="reintentar-imagen"]')).toBeNull();
    expect(item.querySelector('[data-accion="quitar-imagen"]')).not.toBeNull();
  });

  test('415: no es una imagen JPEG, PNG o WebP válida', async () => {
    const { formulario } = montar({
      subirImagenImpl: jest.fn().mockRejectedValue(
        new ErrorDeApi(
          {
            type: TIPO.IMAGEN_NO_ADMITIDA,
            status: 415,
            motivo: MOTIVO.FORMATO_DE_IMAGEN_NO_ADMITIDO,
          },
          415,
        ),
      ),
    });

    elegirArchivos(formulario, ['documento.png']);
    await esperar();

    expect(miniaturas(formulario)[0].textContent).toContain(
      'No es una imagen JPEG, PNG o WebP válida.',
    );
    expect(formulario.querySelector('[data-zona="anuncio-imagenes"]').textContent).toMatch(
      /documento\.png no se pudo adjuntar/,
    );
  });

  test('un fallo de red sí se reintenta, y al reintentar vuelve a subir', async () => {
    const subirImagenImpl = jest
      .fn()
      .mockRejectedValueOnce(new TypeError('sin red'))
      .mockResolvedValue(subidaCon(ID(3)));
    const { formulario } = montar({ subirImagenImpl });

    elegirArchivos(formulario, ['uno.png']);
    await esperar();
    formulario.querySelector('[data-accion="reintentar-imagen"]').click();
    await esperar();

    expect(subirImagenImpl).toHaveBeenCalledTimes(2);
    expect(miniaturas(formulario)[0].dataset.estado).toBe(ESTADO_IMAGEN.LISTA);
  });

  test('con una imagen fallida no se publica: se dice y el foco va a esa imagen', async () => {
    const { formulario, publicarImpl } = montar({
      subirImagenImpl: jest
        .fn()
        .mockRejectedValue(
          new ErrorDeApi({ type: TIPO.IMAGEN_DEMASIADO_GRANDE, status: 413 }, 413),
        ),
    });

    elegirArchivos(formulario, ['enorme.png']);
    await esperar();
    formulario.querySelector('textarea').value = 'Mira';
    formulario.requestSubmit();
    await esperar();

    expect(publicarImpl).not.toHaveBeenCalled();
    expect(formulario.querySelector('.aviso--advertencia').textContent).toMatch(
      /Alguna imagen no se pudo adjuntar/,
    );
    expect(document.activeElement).toBe(
      formulario.querySelector('[aria-label="Quitar enorme.png"]'),
    );
    // El texto no se pierde.
    expect(formulario.querySelector('textarea').value).toBe('Mira');
  });
});

describe('publicar', () => {
  test('202: en revisión, dicho, y el formulario queda limpio', async () => {
    const publicarImpl = jest
      .fn()
      .mockResolvedValue({ comentario: { id: 'c-9' }, estado: 'EN_REVISION' });
    const { formulario } = montar({ publicarImpl });

    formulario.querySelector('textarea').value = 'Texto dudoso';
    formulario.requestSubmit();
    await esperar();

    expect(formulario.querySelector('.aviso--info').textContent).toMatch(/quedó en revisión/);
    expect(formulario.querySelector('textarea').value).toBe('');
  });

  test('400 imagenes-no-validas: se dice qué hacer y el texto se queda', async () => {
    const publicarImpl = jest
      .fn()
      .mockRejectedValue(new ErrorDeApi({ type: TIPO.IMAGENES_NO_VALIDAS, status: 400 }, 400));
    const { formulario } = montar({
      publicarImpl,
      subirImagenImpl: jest.fn().mockResolvedValue(subidaCon(ID(4))),
    });

    elegirArchivos(formulario, ['vieja.png']);
    await esperar();
    formulario.querySelector('textarea').value = 'Con foto vieja';
    formulario.requestSubmit();
    await esperar();

    expect(formulario.querySelector('.aviso--advertencia').textContent).toMatch(
      /Alguna imagen ya no se puede adjuntar/,
    );
    expect(formulario.querySelector('textarea').value).toBe('Con foto vieja');
    expect(miniaturas(formulario)).toHaveLength(1);
  });

  test('vacío no se envía: se marca el campo y se enfoca', async () => {
    const { formulario, publicarImpl } = montar();

    formulario.requestSubmit();
    await esperar();

    expect(publicarImpl).not.toHaveBeenCalled();
    const texto = formulario.querySelector('textarea');
    expect(texto.getAttribute('aria-invalid')).toBe('true');
    expect(document.activeElement).toBe(texto);
  });
});

describe('largo máximo: 2000 caracteres (comentarios.yaml 1.11.0, decisión del PO)', () => {
  function escribir(formulario, valor) {
    const texto = formulario.querySelector('textarea');
    texto.value = valor;
    texto.dispatchEvent(new Event('input', { bubbles: true }));
    return texto;
  }

  test('el campo no deja pasar de 2000 y la cuenta se lee al llegar a él', () => {
    const { formulario } = montar();
    const texto = formulario.querySelector('textarea');
    const cuenta = formulario.querySelector('.redactor-comentario__cuenta');

    expect(MAXIMO_DE_CARACTERES).toBe(2000);
    expect(texto.maxLength).toBe(2000);
    expect(texto.getAttribute('aria-describedby')).toBe(cuenta.id);
    expect(cuenta.textContent).toBe('0 de 2000 caracteres');

    escribir(formulario, 'Muy buena espada');
    expect(cuenta.textContent).toBe('16 de 2000 caracteres');
  });

  test('cerca del máximo dice cuántos quedan y lo anuncia una sola vez', () => {
    const { formulario } = montar();
    const cuenta = formulario.querySelector('.redactor-comentario__cuenta');
    const anuncio = formulario.querySelector('[role="status"][aria-live="polite"]');

    escribir(formulario, 'a'.repeat(1950));
    expect(cuenta.textContent).toBe('1950 de 2000 caracteres · te quedan 50');
    expect(cuenta.dataset.cerca).toBe('true');
    expect(anuncio.textContent).toBe('Te quedan 50 caracteres.');

    escribir(formulario, 'a'.repeat(1960));
    // Mismo umbral: no se vuelve a dictar con cada pulsación.
    expect(anuncio.textContent).toBe('Te quedan 50 caracteres.');

    escribir(formulario, 'a'.repeat(2000));
    expect(anuncio.textContent).toBe('Llegaste al máximo de 2000 caracteres.');
  });

  test('un emoji cuenta uno, como en el servicio', () => {
    expect(caracteresDe('🗡️')).toBe(2);
    expect(caracteresDe('🗡')).toBe(1);
    expect(caracteresDe('Espada')).toBe(6);
  });

  test('si llegara uno más largo (por programa), no se envía y se dice por qué', async () => {
    const { formulario, publicarImpl } = montar();
    const texto = formulario.querySelector('textarea');
    // `maxlength` solo frena al teclado; un valor puesto por programa no.
    texto.value = 'a'.repeat(2001);

    formulario.requestSubmit();
    await esperar();

    expect(publicarImpl).not.toHaveBeenCalled();
    expect(texto.getAttribute('aria-invalid')).toBe('true');
    expect(formulario.querySelector('.campo__error').textContent).toBe(
      'Tu opinión tiene 2001 caracteres; puede tener como mucho 2000.',
    );
    // El error y la cuenta, los dos, describen el campo.
    expect(texto.getAttribute('aria-describedby').split(' ')).toHaveLength(2);
  });

  test('400 texto-demasiado-largo del servicio: se explica, se marca el campo y el texto se queda', async () => {
    const publicarImpl = jest
      .fn()
      .mockRejectedValue(new ErrorDeApi({ type: TIPO.TEXTO_DEMASIADO_LARGO, status: 400 }, 400));
    const { formulario } = montar({ publicarImpl });

    const texto = escribir(formulario, 'Un texto que el servicio rechaza');
    formulario.requestSubmit();
    await esperar();

    expect(formulario.querySelector('.aviso--advertencia').textContent).toMatch(
      /Tu opinión es demasiado larga/,
    );
    expect(texto.value).toBe('Un texto que el servicio rechaza');
    expect(texto.getAttribute('aria-invalid')).toBe('true');
  });
});

describe('frases', () => {
  test('los rechazos de publicar tienen cada uno su frase y su salida', () => {
    expect(
      mensajeDePublicacion(new ErrorDeApi({ motivo: 'AUTOR_SILENCIADO', status: 403 }, 403)),
    ).toEqual(
      expect.objectContaining({
        tono: 'advertencia',
        enlace: expect.objectContaining({ texto: 'Ver mis sanciones' }),
      }),
    );
    expect(
      mensajeDePublicacion(new ErrorDeApi({ type: TIPO.IMAGENES_NO_VALIDAS, status: 400 }, 400))
        .campo,
    ).toBe('imagenes');
    expect(
      mensajeDePublicacion(new ErrorDeApi({ type: TIPO.PRODUCTO_INEXISTENTE, status: 404 }, 404))
        .titulo,
    ).toMatch(/ya no está en el catálogo/);
    // 409 ya no es «tu calificación chocó»: publicar no califica.
    const choque = mensajeDePublicacion(new ErrorDeApi({ status: 409 }, 409));
    expect(choque.reintentar).toBe(true);
    expect(`${choque.titulo} ${choque.detalle}`).not.toMatch(/calificaci|estrellas/i);
    expect(mensajeDePublicacion(new ErrorDeApi({ status: 401 }, 401)).sesion).toBe(true);
    expect(mensajeDePublicacion(new ErrorDeApi({ status: 503 }, 503)).reintentar).toBe(true);
    expect(mensajeDePublicacion(new TypeError('sin red')).reintentar).toBe(true);
    for (const estado of [400, 401, 403, 404, 409, 503]) {
      const { titulo, detalle } = mensajeDePublicacion(new ErrorDeApi({ status: estado }, estado));
      expect(`${titulo} ${detalle}`).not.toMatch(/\b\d{3}\b|ms-|comentarios\.yaml/);
    }
  });

  test('los rechazos de subir: por tipo, motivo o estado (el 413 del borde no trae cuerpo)', () => {
    expect(mensajeDeSubida(new ErrorDeApi(null, 413))).toEqual({
      texto: 'Pesa más de 2 MB o mide más de 4096 píxeles de lado.',
      reintentable: false,
    });
    expect(
      mensajeDeSubida(new ErrorDeApi({ motivo: MOTIVO.FORMATO_DE_IMAGEN_NO_ADMITIDO }, 415)).texto,
    ).toMatch(/JPEG, PNG o WebP/);
    expect(
      mensajeDeSubida(new ErrorDeApi({ type: TIPO.IMAGEN_AUSENTE, status: 400 }, 400)).texto,
    ).toBe('El archivo está vacío.');
    expect(mensajeDeSubida(new ErrorDeApi({ status: 401 }, 401)).sesion).toBe(true);
    expect(mensajeDeSubida(new ErrorDeApi({ status: 503 }, 503)).reintentable).toBe(true);
    expect(mensajeDeSubida(new TypeError('sin red')).reintentable).toBe(true);
  });
});
