/**
 * La cola de moderacion — RF-COM-005 y RF-COM-008 (R10.1), y desde B3 lo que
 * 7.3.3 anade (comentarios.yaml 1.5.0): EDITAR con el texto nuevo, MARCAR y
 * DESMARCAR para el seguimiento especial, la lista de marcados y las imagenes
 * privadas del comentario en revision.
 *
 * Lo que se prueba aqui es lo que distingue esta pantalla de una lista:
 * que una cola vacia NO es un error, que el motivo es obligatorio antes de
 * pulsar y no despues, que las acciones ofrecidas dependen del estado y de la
 * marca, y que el resultado dice la verdad sobre si el autor se entero.
 */

import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { jest } from '@jest/globals';

import {
  enlazadorDeFicha,
  montarModeracion,
  panelDeDetalle,
  tarjetaDeEntrada,
} from './moderar-comentarios.js';
import {
  accionesDesde,
  ACCIONES_EN_LOTE,
  CATEGORIAS,
  ErrorDeApi,
  ErrorDeLote,
} from './cliente-moderacion.js';
import { fechaHora } from '../../comun/ui/formato.js';
import { TEXTO_SIN_SERVICIO } from '../../comun/ui/texto-de-fallo.js';

const AHORA = '2026-09-23T10:00:00Z';
const IMAGEN = '3f1c2b4a-1111-4222-8333-944455566677';

function entrada(sobrescribir = {}) {
  return {
    comentario: {
      id: 'com-1',
      apodoAutor: 'LyraRoja',
      texto: 'Un texto reportado',
      estado: 'EN_REVISION',
    },
    reportes: 2,
    porCategoria: { ACOSO: 2 },
    primerReporte: AHORA,
    ...sobrescribir,
  };
}

function detalle(sobrescribir = {}) {
  return {
    comentario: entrada().comentario,
    reportes: [{ id: 'r-1', categoria: 'ACOSO', descripcion: 'Insulta', fecha: AHORA }],
    historial: [],
    ...sobrescribir,
  };
}

function vista() {
  document.body.innerHTML = `
    <main data-vista="moderar-comentarios">
      <div data-zona="aviso" hidden></div>
      <select data-zona="filtro">
        <option value="en-revision">Pendientes de revisión</option>
        <option value="marcados">Marcados para seguimiento</option>
        <option value="sin-marcar">Pendientes sin marcar</option>
      </select>
      <label for="filtro-categoria">Categoría del reporte</label>
      <select id="filtro-categoria" data-zona="filtro-categoria">
        <option value="">Todas</option>
      </select>
      <label for="filtro-prioridad">Prioridad</label>
      <select id="filtro-prioridad" data-zona="filtro-prioridad">
        <option value="todas">Todas</option>
      </select>
      <div data-zona="cola"></div>
      <div data-zona="detalle-contenedor"></div>
    </main>`;
  return document.querySelector('[data-vista="moderar-comentarios"]');
}

/** Deja que se resuelvan las promesas pendientes del montaje. */
const asentar = () => new Promise((resolver) => setTimeout(resolver, 0));

describe('la cola', () => {
  test('vacia es un estado vacio, no un error: no tener trabajo es una respuesta', async () => {
    const raiz = vista();
    montarModeracion(raiz, {
      api: { consultarCola: jest.fn().mockResolvedValue({ entradas: [], total: 0 }) },
    });
    await asentar();

    const zona = raiz.querySelector('[data-zona="cola"]');
    expect(zona.querySelector('[data-estado="vacio"]')).not.toBeNull();
    expect(zona.querySelector('[data-estado="error"]')).toBeNull();
    expect(zona.textContent).toContain('No hay comentarios esperando revisión');
  });

  test('un 403 se explica por lo que es: la pantalla no es para esta cuenta', async () => {
    const raiz = vista();
    montarModeracion(raiz, {
      api: {
        consultarCola: jest
          .fn()
          .mockRejectedValue(Object.assign(new Error('prohibido'), { estado: 403 })),
      },
    });
    await asentar();

    expect(raiz.querySelector('[data-zona="cola"]').textContent).toContain('solo para moderación');
  });

  test('pinta el texto del comentario con textContent, no como marcado', () => {
    const conMarcado = entrada();
    conMarcado.comentario.texto = '<img src=x onerror="robar()">';
    const tarjeta = tarjetaDeEntrada(conMarcado, () => {});

    const parrafo = tarjeta.querySelector('[data-campo="texto"]');
    expect(parrafo.textContent).toBe('<img src=x onerror="robar()">');
    // El contenido de un comentario REPORTADO es exactamente aquello de lo
    // que hay que desconfiar: si llegara a interpretarse, el ataque tendria
    // de publico justo a quien modera.
    expect(parrafo.querySelector('img')).toBeNull();
  });

  test('«Revisar» abre el detalle de ese comentario', () => {
    const alAbrir = jest.fn();
    const tarjeta = tarjetaDeEntrada(entrada(), alAbrir);
    tarjeta.querySelector('[data-accion="revisar"]').click();
    expect(alAbrir).toHaveBeenCalledWith('com-1');
  });

  test('1.5.0: la tarjeta dice si está marcado, si fue editado y, fuera de revisión, su estado', () => {
    const tarjeta = tarjetaDeEntrada(
      entrada({
        comentario: { ...entrada().comentario, estado: 'PUBLICADO', marcado: true, editado: true },
        reportes: 0,
        porCategoria: {},
      }),
      () => {},
    );

    expect(tarjeta.querySelector('[data-campo="estado"]').textContent).toBe('Publicado');
    expect(tarjeta.querySelector('[data-campo="marcado"]').textContent).toBe(
      'Marcado para seguimiento',
    );
    expect(tarjeta.querySelector('[data-campo="editado"]').textContent).toBe(
      'Editado por moderación',
    );
    // Sin reportes, «esperando desde» no dice nada: se da la fecha del comentario.
    expect(tarjeta.textContent).toMatch(/Publicado el/);
    expect(tarjeta.textContent).not.toMatch(/Esperando desde/);
  });

  test('en revisión y sin marca, la tarjeta no repite el estado de la cola', () => {
    const tarjeta = tarjetaDeEntrada(entrada(), () => {});
    expect(tarjeta.querySelector('[data-campo="estado"]')).toBeNull();
    expect(tarjeta.querySelector('[data-campo="marcado"]')).toBeNull();
    expect(tarjeta.textContent).toMatch(/Esperando desde/);
  });

  // El umbral no se asume: con el umbral en 0 el servicio manda siempre
  // `false`, así que estas pruebas inyectan `true` a mano.
  test('HU-COM-005: con prioridadElevada la tarjeta lo dice con texto, no solo con color', () => {
    const tarjeta = tarjetaDeEntrada(entrada({ prioridadElevada: true }), () => {});

    const distintivo = tarjeta.querySelector('[data-campo="prioridad"]');
    expect(distintivo).not.toBeNull();
    expect(distintivo.textContent).toBe('Prioridad elevada');
  });

  test.each([
    ['false', { prioridadElevada: false }],
    ['ausente', {}],
    ['un valor que no es booleano', { prioridadElevada: 'true' }],
  ])('HU-COM-005: con prioridadElevada %s la tarjeta no pinta nada', (_caso, campos) => {
    const tarjeta = tarjetaDeEntrada(entrada(campos), () => {});

    expect(tarjeta.querySelector('[data-campo="prioridad"]')).toBeNull();
    expect(tarjeta.textContent).not.toContain('Prioridad elevada');
  });

  test('HU-USR-010 (CA-02): con permiso, «Ver ficha» junto al autor lleva a su ficha por su uid', () => {
    const autorId = '11111111-2222-3333-4444-555555555555';
    const conAutor = entrada({ comentario: { ...entrada().comentario, autorId } });
    const tarjeta = tarjetaDeEntrada(conAutor, () => {}, {
      hrefDeFicha: enlazadorDeFicha('ADMINISTRADOR'),
    });

    const enlace = tarjeta.querySelector('a[data-accion="ver-ficha"]');
    expect(enlace).not.toBeNull();
    expect(enlace.textContent).toBe('Ver ficha');
    expect(enlace.getAttribute('aria-label')).toBe('Ver la ficha de LyraRoja');
    expect(enlace.previousElementSibling.dataset.campo).toBe('apodo');
    const destino = new URL(enlace.href);
    expect(destino.pathname).toMatch(/plataforma\/moderacion-sanciones\/ficha-usuario\.html$/);
    expect(destino.searchParams.get('usuario')).toBe(autorId);
  });

  test('HU-USR-010: a un moderador (o sin autor) no se le ofrece la ficha, que es de administración', () => {
    expect(enlazadorDeFicha('MODERADOR')).toBeNull();
    expect(enlazadorDeFicha(null)).toBeNull();
    expect(typeof enlazadorDeFicha('SUPER_ADMINISTRADOR')).toBe('function');

    const sinAutor = tarjetaDeEntrada(entrada(), () => {}, {
      hrefDeFicha: enlazadorDeFicha('ADMINISTRADOR'),
    });
    expect(sinAutor.querySelector('[data-accion="ver-ficha"]')).toBeNull();
    const conAutor = entrada({ comentario: { ...entrada().comentario, autorId: 'u-1' } });
    expect(
      tarjetaDeEntrada(conAutor, () => {}).querySelector('[data-accion="ver-ficha"]'),
    ).toBeNull();
  });

  test('HU-USR-010: la cola montada con el rol enlaza cada autor a su ficha', async () => {
    const conAutor = entrada({ comentario: { ...entrada().comentario, autorId: 'u-1' } });
    const consultarCola = jest.fn().mockResolvedValue({ entradas: [conAutor], total: 1 });
    const raiz = vista();
    montarModeracion(raiz, { api: { consultarCola }, rol: 'SUPER_ADMINISTRADOR' });
    await asentar();

    expect(raiz.querySelectorAll('[data-zona="cola"] a[data-accion="ver-ficha"]')).toHaveLength(1);

    const raizModerador = vista();
    montarModeracion(raizModerador, { api: { consultarCola }, rol: 'MODERADOR' });
    await asentar();
    expect(raizModerador.querySelector('[data-accion="ver-ficha"]')).toBeNull();
  });

  test('el filtro pide la lista de seguimiento o los sin marcar, y su vacío dice lo suyo', async () => {
    const consultarCola = jest.fn().mockResolvedValue({ entradas: [], total: 0 });
    const raiz = vista();
    montarModeracion(raiz, { api: { consultarCola } });
    await asentar();

    expect(consultarCola).toHaveBeenLastCalledWith(expect.objectContaining({ marcado: null }));

    const filtro = raiz.querySelector('[data-zona="filtro"]');
    filtro.value = 'marcados';
    filtro.dispatchEvent(new Event('change'));
    await asentar();
    expect(consultarCola).toHaveBeenLastCalledWith(expect.objectContaining({ marcado: true }));
    expect(raiz.querySelector('[data-zona="cola"]').textContent).toContain(
      'No hay comentarios marcados para seguimiento',
    );

    filtro.value = 'sin-marcar';
    filtro.dispatchEvent(new Event('change'));
    await asentar();
    expect(consultarCola).toHaveBeenLastCalledWith(expect.objectContaining({ marcado: false }));
  });
});

describe('el detalle y la decision', () => {
  test('solo ofrece las acciones que valen desde el estado actual y la marca', () => {
    const enRevision = panelDeDetalle(detalle(), () => {});
    const ofrecidas = [...enRevision.querySelectorAll('option')].map((o) => o.value);
    expect(ofrecidas).toEqual(['APROBAR', 'OCULTAR', 'ELIMINAR', 'EDITAR', 'MARCAR']);
    // No se pinta «Restaurar» sobre algo que no esta oculto, ni «Quitar la
    // marca» sobre algo sin marca: serian botones que el servicio contesta
    // con 409.
    expect(ofrecidas).not.toContain('RESTAURAR');
    expect(ofrecidas).not.toContain('DESMARCAR');

    const oculto = panelDeDetalle(
      detalle({ comentario: { ...detalle().comentario, estado: 'OCULTO', marcado: true } }),
      () => {},
    );
    expect([...oculto.querySelectorAll('option')].map((o) => o.value)).toEqual([
      'ELIMINAR',
      'RESTAURAR',
      'EDITAR',
      'DESMARCAR',
    ]);
  });

  test('1.5.0: el detalle enseña el estado, la marca y si fue editado', () => {
    const panel = panelDeDetalle(
      detalle({
        comentario: { ...detalle().comentario, estado: 'PUBLICADO', marcado: true, editado: true },
      }),
      () => {},
    );

    expect(panel.querySelector('[data-campo="estado"]').textContent).toBe('Publicado');
    expect(panel.querySelector('[data-campo="marcado"]')).not.toBeNull();
    expect(panel.querySelector('[data-campo="editado"]')).not.toBeNull();
  });

  test('HU-COM-005: el detalle pinta la prioridad elevada solo si se le indica', () => {
    const con = panelDeDetalle(detalle(), () => {}, { prioridadElevada: true });
    expect(con.querySelector('[data-campo="prioridad"]').textContent).toBe('Prioridad elevada');

    expect(
      panelDeDetalle(detalle(), () => {}).querySelector('[data-campo="prioridad"]'),
    ).toBeNull();
    expect(
      panelDeDetalle(detalle(), () => {}, { prioridadElevada: false }).querySelector(
        '[data-campo="prioridad"]',
      ),
    ).toBeNull();
  });

  test('EDITAR pide el texto nuevo, que empieza siendo el actual y viaja con la decisión', () => {
    const alDecidir = jest.fn();
    const panel = panelDeDetalle(detalle(), alDecidir);
    document.body.append(panel);

    const accion = panel.querySelector('#accion');
    const campo = panel.querySelector('[data-zona="texto-nuevo"]');
    expect(campo.hidden).toBe(true);

    accion.value = 'EDITAR';
    accion.dispatchEvent(new Event('change'));
    expect(campo.hidden).toBe(false);
    const texto = panel.querySelector('#texto-nuevo');
    expect(texto.value).toBe('Un texto reportado');
    expect(panel.querySelector('label[for="texto-nuevo"]').textContent).toBe('Texto nuevo');

    const motivo = panel.querySelector('#motivo');
    motivo.value = 'Se quita un insulto';
    motivo.dispatchEvent(new Event('input'));
    const boton = panel.querySelector('[data-accion="decidir"]');
    expect(boton.disabled).toBe(false);

    // Sin texto nuevo no hay edición posible.
    texto.value = '   ';
    texto.dispatchEvent(new Event('input'));
    expect(boton.disabled).toBe(true);

    texto.value = '  Un texto sin insultos  ';
    texto.dispatchEvent(new Event('input'));
    panel
      .querySelector('[data-zona="decision"]')
      .dispatchEvent(new Event('submit', { cancelable: true }));
    expect(alDecidir).toHaveBeenCalledWith({
      accion: 'EDITAR',
      motivo: 'Se quita un insulto',
      textoNuevo: 'Un texto sin insultos',
    });
  });

  test('MARCAR dice que es una nota interna: el autor no recibe aviso', () => {
    const panel = panelDeDetalle(detalle(), () => {});
    const accion = panel.querySelector('#accion');
    const pista = panel.querySelector('#motivo-pista');
    expect(pista.textContent).toMatch(/el autor lo recibe/);

    accion.value = 'MARCAR';
    accion.dispatchEvent(new Event('change'));
    expect(pista.textContent).toMatch(/nota interna: el autor no recibe aviso/);
  });

  test('el motivo tiene de 3 a 500 caracteres, como el contrato', () => {
    const panel = panelDeDetalle(detalle(), () => {});
    const motivo = panel.querySelector('#motivo');
    const boton = panel.querySelector('[data-accion="decidir"]');

    expect(motivo.getAttribute('maxlength')).toBe('500');
    motivo.value = 'no';
    motivo.dispatchEvent(new Event('input'));
    expect(boton.disabled).toBe(true);
    motivo.value = 'spam';
    motivo.dispatchEvent(new Event('input'));
    expect(boton.disabled).toBe(false);
  });

  test('las imágenes del comentario se piden con la sesión y se pintan desde un blob', async () => {
    const blob = new Blob(['png'], { type: 'image/png' });
    const cargarImagen = jest.fn().mockResolvedValueOnce(blob).mockResolvedValueOnce(null);
    const panel = panelDeDetalle(
      detalle({
        comentario: {
          ...detalle().comentario,
          imagenes: [IMAGEN, '3f1c2b4a-2222-4222-8333-944455566677', 'antigua.png'],
        },
      }),
      () => {},
      { cargarImagen, crearUrl: () => 'blob:prueba' },
    );
    await asentar();

    expect(cargarImagen).toHaveBeenCalledWith(IMAGEN);
    const zona = panel.querySelector('[data-zona="imagenes"]');
    const imagen = zona.querySelector('img');
    expect(imagen.getAttribute('src')).toBe('blob:prueba');
    expect(imagen.getAttribute('alt')).toBe('Imagen 1 de 3 que adjuntó LyraRoja');
    // La que ya no está lo dice; el nombre antiguo se enseña como texto.
    expect(zona.textContent).toContain('Imagen no disponible');
    expect(zona.textContent).toContain('antigua.png');
    expect(cargarImagen).toHaveBeenCalledTimes(2);
  });

  test('un comentario ELIMINADO no ofrece formulario: ya no admite decisiones', () => {
    const panel = panelDeDetalle(
      detalle({ comentario: { ...detalle().comentario, estado: 'ELIMINADO' } }),
      () => {},
    );
    expect(panel.querySelector('[data-zona="decision"]')).toBeNull();
    expect(panel.querySelector('[data-zona="sin-acciones"]')).not.toBeNull();
    expect(accionesDesde('ELIMINADO')).toEqual([]);
  });

  test('el boton esta apagado hasta que hay motivo, y manda accion y motivo', () => {
    const alDecidir = jest.fn();
    const panel = panelDeDetalle(detalle(), alDecidir);
    document.body.append(panel);

    const boton = panel.querySelector('[data-accion="decidir"]');
    const motivo = panel.querySelector('#motivo');
    expect(boton.disabled).toBe(true);

    // Espacios no son un motivo.
    motivo.value = '   ';
    motivo.dispatchEvent(new Event('input'));
    expect(boton.disabled).toBe(true);

    motivo.value = 'Incumple la norma';
    motivo.dispatchEvent(new Event('input'));
    expect(boton.disabled).toBe(false);

    panel.querySelector('#accion').value = 'OCULTAR';
    panel
      .querySelector('[data-zona="decision"]')
      .dispatchEvent(new Event('submit', { cancelable: true }));
    expect(alDecidir).toHaveBeenCalledWith({ accion: 'OCULTAR', motivo: 'Incumple la norma' });
  });

  test('el historial vacio se dice; con asientos se listan en orden', () => {
    expect(panelDeDetalle(detalle(), () => {}).textContent).toContain(
      'Todavía no se ha decidido nada',
    );

    const conHistorial = panelDeDetalle(
      detalle({
        historial: [
          {
            accion: 'OCULTAR',
            apodoModerador: 'AdaLaJusta',
            estadoAnterior: 'EN_REVISION',
            estadoNuevo: 'OCULTO',
            motivo: 'Acoso',
            fecha: AHORA,
          },
        ],
      }),
      () => {},
    );
    const entradas = conHistorial.querySelectorAll('[data-zona="historial"] li');
    expect(entradas).toHaveLength(1);
    expect(entradas[0].textContent).toContain('OCULTAR por AdaLaJusta');
    expect(entradas[0].textContent).toContain('EN_REVISION → OCULTO');
  });

  test('una edición en el historial dice qué texto había y cuál quedó', () => {
    const panel = panelDeDetalle(
      detalle({
        historial: [
          {
            accion: 'EDITAR',
            apodoModerador: 'AdaLaJusta',
            estadoAnterior: 'EN_REVISION',
            estadoNuevo: 'EN_REVISION',
            motivo: 'Insulto',
            fecha: AHORA,
            textoAnterior: 'Eres un inútil',
            textoNuevo: 'No me gustó',
          },
        ],
      }),
      () => {},
    );

    const linea = panel.querySelector('[data-zona="historial"] li').textContent;
    expect(linea).toContain('Antes: «Eres un inútil»');
    expect(linea).toContain('Ahora: «No me gustó»');
  });
});

describe('resolver desde la vista', () => {
  function apiQueResuelve(resuelto) {
    return {
      consultarCola: jest.fn().mockResolvedValue({ entradas: [entrada()], total: 1 }),
      consultarDetalle: jest.fn().mockResolvedValue(detalle()),
      resolverComentario: jest.fn().mockResolvedValue(resuelto),
    };
  }

  async function decidir(api) {
    const raiz = vista();
    montarModeracion(raiz, { api });
    await asentar();
    raiz.querySelector('[data-accion="revisar"]').click();
    await asentar();
    const motivo = raiz.querySelector('#motivo');
    motivo.value = 'Acoso a otro jugador';
    motivo.dispatchEvent(new Event('input'));
    raiz
      .querySelector('[data-zona="decision"]')
      .dispatchEvent(new Event('submit', { cancelable: true }));
    await asentar();
    return raiz;
  }

  test('dice que el autor se entero cuando el aviso salio', async () => {
    const api = apiQueResuelve({
      comentario: { ...entrada().comentario, estado: 'PUBLICADO' },
      asiento: { id: 'a-1' },
      autorNotificado: true,
    });
    const raiz = await decidir(api);

    expect(api.resolverComentario).toHaveBeenCalledWith('com-1', {
      accion: 'APROBAR',
      motivo: 'Acoso a otro jugador',
    });
    const aviso = raiz.querySelector('[data-zona="aviso"]');
    expect(aviso.textContent).toContain('Comentario aprobado');
    expect(aviso.textContent).toContain('Se avisó al autor');
  });

  test('marcar: se dice que es interno y el detalle vuelve a abrirse para seguir', async () => {
    const api = apiQueResuelve({
      comentario: { ...entrada().comentario, marcado: true },
      asiento: { id: 'a-2' },
      autorNotificado: false,
    });
    const raiz = vista();
    montarModeracion(raiz, { api });
    await asentar();
    raiz.querySelector('[data-accion="revisar"]').click();
    await asentar();

    const accion = raiz.querySelector('#accion');
    accion.value = 'MARCAR';
    accion.dispatchEvent(new Event('change'));
    const motivo = raiz.querySelector('#motivo');
    motivo.value = 'Seguimiento de este autor';
    motivo.dispatchEvent(new Event('input'));
    raiz
      .querySelector('[data-zona="decision"]')
      .dispatchEvent(new Event('submit', { cancelable: true }));
    await asentar();
    await asentar();

    expect(api.resolverComentario).toHaveBeenCalledWith('com-1', {
      accion: 'MARCAR',
      motivo: 'Seguimiento de este autor',
    });
    const aviso = raiz.querySelector('[data-zona="aviso"]');
    expect(aviso.textContent).toContain('Comentario marcado para seguimiento');
    expect(aviso.textContent).toContain('nota interna');
    // No dice «el aviso no salió»: no tenía que salir.
    expect(aviso.textContent).not.toMatch(/no salió/);
    // El mismo comentario sigue delante, con la decisión ya en su detalle.
    expect(api.consultarDetalle).toHaveBeenCalledTimes(2);
    expect(raiz.querySelector('[data-zona="detalle"]').dataset.comentarioId).toBe('com-1');
  });

  test('y dice que NO se entero cuando el aviso no salio: el aviso es fail-open', async () => {
    // Si esto se callara, el moderador se iria creyendo que el autor sabe
    // por que desaparecio su comentario. La decision vale igual (HU-DIS-003:
    // un servicio de avisos caido no puede impedir retirar contenido
    // ofensivo), pero eso es justo lo que hay que decir.
    const raiz = await decidir(
      apiQueResuelve({
        comentario: { ...entrada().comentario, estado: 'OCULTO' },
        asiento: { id: 'a-1' },
        autorNotificado: false,
      }),
    );
    expect(raiz.querySelector('[data-zona="aviso"]').textContent).toContain(
      'el aviso al autor no salió',
    );
  });

  test('HU-COM-005: el detalle hereda la prioridad de la entrada de la cola (el detalle no la trae)', async () => {
    const abrirEntrada = async (prioridadElevada) => {
      const raiz = vista();
      montarModeracion(raiz, {
        api: {
          consultarCola: jest
            .fn()
            .mockResolvedValue({ entradas: [entrada({ prioridadElevada })], total: 1 }),
          consultarDetalle: jest.fn().mockResolvedValue(detalle()),
        },
      });
      await asentar();
      raiz.querySelector('[data-accion="revisar"]').click();
      await asentar();
      return raiz.querySelector('[data-zona="detalle"]');
    };

    const elevada = await abrirEntrada(true);
    expect(elevada.querySelector('[data-campo="prioridad"]').textContent).toBe('Prioridad elevada');
    expect((await abrirEntrada(false)).querySelector('[data-campo="prioridad"]')).toBeNull();
  });

  test('HU-COM-005: tras marcar, el detalle que se reabre conserva la prioridad elevada', async () => {
    const api = apiQueResuelve({
      comentario: { ...entrada().comentario, marcado: true },
      asiento: { id: 'a-3' },
      autorNotificado: false,
    });
    api.consultarCola = jest
      .fn()
      .mockResolvedValue({ entradas: [entrada({ prioridadElevada: true })], total: 1 });
    const raiz = vista();
    montarModeracion(raiz, { api });
    await asentar();
    raiz.querySelector('[data-accion="revisar"]').click();
    await asentar();

    const accion = raiz.querySelector('#accion');
    accion.value = 'MARCAR';
    accion.dispatchEvent(new Event('change'));
    const motivo = raiz.querySelector('#motivo');
    motivo.value = 'Seguimiento de este autor';
    motivo.dispatchEvent(new Event('input'));
    raiz
      .querySelector('[data-zona="decision"]')
      .dispatchEvent(new Event('submit', { cancelable: true }));
    await asentar();
    await asentar();

    expect(api.consultarDetalle).toHaveBeenCalledTimes(2);
    expect(raiz.querySelector('[data-zona="detalle"] [data-campo="prioridad"]').textContent).toBe(
      'Prioridad elevada',
    );
  });

  test('si otro moderador se adelanto (409), se explica y se recarga la cola', async () => {
    const api = apiQueResuelve(null);
    api.resolverComentario = jest.fn().mockRejectedValue(
      Object.assign(new Error('conflicto'), {
        estado: 409,
        motivo: 'TRANSICION_INVALIDA',
      }),
    );
    const raiz = await decidir(api);

    expect(raiz.querySelector('[data-zona="aviso"]').textContent).toContain(
      'Otro moderador se adelantó',
    );
    // Dos veces: la del montaje y la de despues del conflicto. Dejar la
    // pantalla vieja seria invitar al segundo intento contra el mismo estado.
    expect(api.consultarCola).toHaveBeenCalledTimes(2);
  });
});

describe('HU-COM-005: el historial de comentarios del autor en el detalle', () => {
  const ZONA = '[data-zona="historial-autor"]';
  const BOTON = '[data-accion="ver-historial-autor"]';
  const VER_MAS = '[data-accion="ver-mas"]';

  const conAutor = () => detalle({ comentario: { ...detalle().comentario, autorId: 'aut-1' } });

  function item(n, sobrescribir = {}) {
    return {
      id: `h-${n}`,
      productoId: 'prod-7',
      texto: `Texto ${n}`,
      fechaPublicacion: AHORA,
      estado: 'PUBLICADO',
      editado: false,
      ...sobrescribir,
    };
  }

  function respuestaDe(comentarios, { total = comentarios.length, pagina = 0 } = {}) {
    return { autorId: 'aut-1', apodoAutor: 'LyraRoja', comentarios, total, pagina, tamano: 20 };
  }

  function abrirPanel(cargar, d = conAutor()) {
    const panel = panelDeDetalle(d, () => {}, { cargarHistorialDelAutor: cargar });
    document.body.replaceChildren(panel);
    return panel;
  }

  const pulsar = async (panel, selector) => {
    panel.querySelector(selector).click();
    await asentar();
  };

  test('es bajo demanda: abrir el detalle no pide nada y ofrece el boton', () => {
    const cargar = jest.fn();
    const panel = abrirPanel(cargar);

    expect(cargar).not.toHaveBeenCalled();
    expect(panel.querySelector(BOTON)).not.toBeNull();
    expect(panel.querySelector(`${ZONA} li`)).toBeNull();
  });

  test('sin autorId no hay seccion: no hay nada que pedir', () => {
    const cargar = jest.fn();
    const panel = abrirPanel(cargar, detalle());

    expect(panel.querySelector(BOTON)).toBeNull();
    expect(cargar).not.toHaveBeenCalled();
  });

  test('pulsar pide la pagina 0 del autor del comentario abierto, y una sola vez', async () => {
    const cargar = jest.fn().mockResolvedValue(respuestaDe([item(1)]));
    const panel = abrirPanel(cargar);

    panel.querySelector(BOTON).click();
    panel.querySelector(BOTON)?.click();
    await asentar();

    expect(cargar).toHaveBeenCalledTimes(1);
    expect(cargar).toHaveBeenCalledWith('aut-1', { pagina: 0 });
  });

  test('cada elemento dice texto, fecha, producto y estado con palabras', async () => {
    const cargar = jest
      .fn()
      .mockResolvedValue(
        respuestaDe([
          item(1, { estado: 'OCULTO' }),
          item(2, { estado: 'ELIMINADO' }),
          item(3, { estado: 'EN_REVISION' }),
          item(4, { estado: 'PUBLICADO', editado: true }),
        ]),
      );
    const panel = abrirPanel(cargar);
    await pulsar(panel, BOTON);

    const filas = panel.querySelectorAll(`${ZONA} li`);
    expect(filas).toHaveLength(4);
    expect(filas[0].querySelector('[data-campo="texto"]').textContent).toBe('Texto 1');
    expect(filas[0].querySelector('[data-campo="fecha"]').textContent).toBe(fechaHora(AHORA));
    expect(filas[0].querySelector('[data-campo="producto"]').textContent).toBe('Producto: prod-7');
    // Con texto, no solo con color: los cuatro estados, incluido EN_REVISION.
    const estados = [...filas].map((f) => f.querySelector('[data-campo="estado"]').textContent);
    expect(estados).toEqual(['Oculto', 'Eliminado', 'En revisión', 'Publicado']);
    expect(filas[3].querySelector('[data-campo="editado"]').textContent).toBe(
      'Editado por moderación',
    );
    expect(filas[0].querySelector('[data-campo="editado"]')).toBeNull();
  });

  test('el texto de un comentario se pinta como texto, nunca como marcado', async () => {
    const cargar = jest
      .fn()
      .mockResolvedValue(respuestaDe([item(1, { texto: '<img src=x onerror="robar()">' })]));
    const panel = abrirPanel(cargar);
    await pulsar(panel, BOTON);

    const texto = panel.querySelector(`${ZONA} [data-campo="texto"]`);
    expect(texto.textContent).toBe('<img src=x onerror="robar()">');
    expect(panel.querySelector(`${ZONA} img`)).toBeNull();
  });

  test('el comentario que se tiene abierto se reconoce con palabras', async () => {
    const cargar = jest.fn().mockResolvedValue(respuestaDe([item(1, { id: 'com-1' }), item(2)]));
    const panel = abrirPanel(cargar);
    await pulsar(panel, BOTON);

    const filas = panel.querySelectorAll(`${ZONA} li`);
    expect(filas[0].textContent).toContain('Este es el comentario abierto');
    expect(filas[1].textContent).not.toContain('Este es el comentario abierto');
  });

  test('«Ver más» añade la pagina siguiente sin reemplazar y se va al llegar al total', async () => {
    const cargar = jest
      .fn()
      .mockResolvedValueOnce(respuestaDe([item(1), item(2)], { total: 3, pagina: 0 }))
      .mockResolvedValueOnce(respuestaDe([item(3)], { total: 3, pagina: 1 }));
    const panel = abrirPanel(cargar);
    await pulsar(panel, BOTON);

    expect(panel.querySelectorAll(`${ZONA} li`)).toHaveLength(2);
    expect(panel.querySelector(VER_MAS)).not.toBeNull();

    await pulsar(panel, VER_MAS);

    expect(cargar).toHaveBeenLastCalledWith('aut-1', { pagina: 1 });
    const textos = [...panel.querySelectorAll(`${ZONA} [data-campo="texto"]`)].map(
      (t) => t.textContent,
    );
    expect(textos).toEqual(['Texto 1', 'Texto 2', 'Texto 3']);
    expect(panel.querySelector(VER_MAS)).toBeNull();
  });

  test('con todo en la primera pagina no se ofrece «Ver más»', async () => {
    const cargar = jest.fn().mockResolvedValue(respuestaDe([item(1)], { total: 1 }));
    const panel = abrirPanel(cargar);
    await pulsar(panel, BOTON);

    expect(panel.querySelector(VER_MAS)).toBeNull();
  });

  test('un doble clic en «Ver más» no pide dos veces la misma pagina', async () => {
    let resolver;
    const cargar = jest
      .fn()
      .mockResolvedValueOnce(respuestaDe([item(1)], { total: 2 }))
      .mockReturnValueOnce(new Promise((ok) => (resolver = ok)));
    const panel = abrirPanel(cargar);
    await pulsar(panel, BOTON);

    const verMas = panel.querySelector(VER_MAS);
    verMas.click();
    verMas.click();
    expect(cargar).toHaveBeenCalledTimes(2);
    resolver(respuestaDe([item(2)], { total: 2, pagina: 1 }));
    await asentar();

    expect(panel.querySelectorAll(`${ZONA} li`)).toHaveLength(2);
  });

  test('autor sin comentarios: estado vacio, no error', async () => {
    const cargar = jest.fn().mockResolvedValue({
      autorId: 'aut-1',
      comentarios: [],
      total: 0,
      pagina: 0,
      tamano: 20,
    });
    const panel = abrirPanel(cargar);
    await pulsar(panel, BOTON);

    const zona = panel.querySelector(ZONA);
    expect(zona.querySelector('[data-estado="vacio"]')).not.toBeNull();
    expect(zona.querySelector('[data-estado="error"]')).toBeNull();
    expect(zona.querySelector('li')).toBeNull();
    expect(zona.textContent).toContain('Este autor no tiene comentarios');
  });

  test.each([
    ['401', new ErrorDeApi(null, 401), 'advertencia', null],
    ['403', new ErrorDeApi(null, 403), 'advertencia', null],
    ['503', new ErrorDeApi(null, 503), 'error', TEXTO_SIN_SERVICIO],
  ])(
    '%s: un aviso del tono que dice MAPEO-ERRORES, en lenguaje de persona y sin cifras',
    async (_nombre, error, tono, textoEsperado) => {
      const cargar = jest.fn().mockRejectedValue(error);
      const panel = abrirPanel(cargar);
      await pulsar(panel, BOTON);

      const aviso = panel.querySelector(`${ZONA} .aviso`);
      expect(aviso.classList.contains(`aviso--${tono}`)).toBe(true);
      // La vista decide por estado, y lo que se lee es el texto del kit
      // (`respaldoPorEstado`), no uno inventado aqui.
      expect(aviso.textContent).toContain(error.detalle);
      if (textoEsperado) {
        expect(aviso.textContent).toContain(textoEsperado);
      }
      // Ni el numero del estado ni ningun umbral o limite llegan a la pantalla.
      expect(panel.querySelector(ZONA).textContent).not.toMatch(/\d/);
      expect(panel.querySelector(ZONA).textContent).not.toMatch(/\b(límite|umbral)\b/i);
    },
  );

  test('un fallo transitorio ofrece reintentar y el reintento lo arregla', async () => {
    const cargar = jest
      .fn()
      .mockRejectedValueOnce(new ErrorDeApi(null, 503))
      .mockResolvedValueOnce(respuestaDe([item(1)]));
    const panel = abrirPanel(cargar);
    await pulsar(panel, BOTON);

    await pulsar(panel, '[data-accion="reintentar"]');

    expect(panel.querySelectorAll(`${ZONA} li`)).toHaveLength(1);
    expect(panel.querySelector(`${ZONA} .aviso`)).toBeNull();
  });

  test('un 403 no ofrece reintentar: el permiso no va a cambiar entre dos clics', async () => {
    const cargar = jest.fn().mockRejectedValue(new ErrorDeApi(null, 403));
    const panel = abrirPanel(cargar);
    await pulsar(panel, BOTON);

    expect(panel.querySelector('[data-accion="reintentar"]')).toBeNull();
  });

  test('si falla «Ver más» se conserva lo ya pintado y se puede reintentar', async () => {
    const cargar = jest
      .fn()
      .mockResolvedValueOnce(respuestaDe([item(1)], { total: 2 }))
      .mockRejectedValueOnce(new ErrorDeApi(null, 503))
      .mockResolvedValueOnce(respuestaDe([item(2)], { total: 2, pagina: 1 }));
    const panel = abrirPanel(cargar);
    await pulsar(panel, BOTON);
    await pulsar(panel, VER_MAS);

    expect(panel.querySelectorAll(`${ZONA} li`)).toHaveLength(1);
    expect(panel.querySelector(`${ZONA} .aviso--error`)).not.toBeNull();

    await pulsar(panel, '[data-accion="reintentar"]');

    expect(cargar).toHaveBeenLastCalledWith('aut-1', { pagina: 1 });
    expect(panel.querySelectorAll(`${ZONA} li`)).toHaveLength(2);
  });

  test('si el historial falla, el resto del detalle sigue operativo', async () => {
    const cargar = jest.fn().mockRejectedValue(new ErrorDeApi(null, 503));
    const panel = abrirPanel(cargar);
    await pulsar(panel, BOTON);

    expect(panel.querySelector('[data-zona="decision"]')).not.toBeNull();
    expect(panel.querySelector('[data-zona="reportes"]')).not.toBeNull();
  });

  test('no pinta datos de perfil del autor', async () => {
    const cargar = jest.fn().mockResolvedValue({
      ...respuestaDe([item(1)]),
      correo: 'lyra@example.com',
      rol: 'JUGADOR',
      avatar: 'https://x/y.png',
    });
    const panel = abrirPanel(cargar);
    await pulsar(panel, BOTON);

    const zona = panel.querySelector(ZONA);
    expect(zona.textContent).not.toContain('lyra@example.com');
    expect(zona.textContent).not.toContain('JUGADOR');
    expect(zona.querySelector('img')).toBeNull();
  });

  test('montarModeracion conecta el boton con el cliente y no pide nada al abrir', async () => {
    const historialDelAutor = jest.fn().mockResolvedValue(respuestaDe([item(1)]));
    const api = {
      consultarCola: jest.fn().mockResolvedValue({ entradas: [entrada()], total: 1 }),
      consultarDetalle: jest.fn().mockResolvedValue(conAutor()),
      historialDelAutor,
    };
    const raiz = vista();
    montarModeracion(raiz, { api });
    await asentar();
    raiz.querySelector('[data-accion="revisar"]').click();
    await asentar();

    expect(historialDelAutor).not.toHaveBeenCalled();
    raiz.querySelector(BOTON).click();
    await asentar();

    expect(historialDelAutor).toHaveBeenCalledWith('aut-1', { pagina: 0 });
    expect(raiz.querySelectorAll(`${ZONA} li`)).toHaveLength(1);
  });
});

describe('HU-COM-005 (#519): filtros de categoría y de prioridad en la cola', () => {
  const SIN_FILTROS = {
    productoId: null,
    marcado: null,
    categoria: null,
    prioridadElevada: null,
    tamano: 20,
  };

  function conCola(...entradas) {
    return jest.fn().mockResolvedValue({ entradas, total: entradas.length });
  }

  function elegir(raiz, zona, valor) {
    const control = raiz.querySelector(`[data-zona="${zona}"]`);
    control.value = valor;
    control.dispatchEvent(new Event('change'));
  }

  const textoDeLaCola = (raiz) => raiz.querySelector('[data-zona="cola"]').textContent;

  test('las seis categorías del contrato salen de CATEGORIAS, más «Todas»', async () => {
    const raiz = vista();
    montarModeracion(raiz, { api: { consultarCola: conCola() } });
    await asentar();

    const opciones = [...raiz.querySelectorAll('[data-zona="filtro-categoria"] option')];
    expect(opciones.map((o) => o.value)).toEqual(['', ...CATEGORIAS.map((c) => c.valor)]);
    expect(opciones.map((o) => o.textContent)).toEqual([
      'Todas',
      ...CATEGORIAS.map((c) => c.etiqueta),
    ]);
    expect(CATEGORIAS).toHaveLength(6);
  });

  test('la prioridad ofrece «Todas», «Solo prioridad elevada» y «Sin prioridad elevada»', async () => {
    const raiz = vista();
    montarModeracion(raiz, { api: { consultarCola: conCola() } });
    await asentar();

    const opciones = [...raiz.querySelectorAll('[data-zona="filtro-prioridad"] option')];
    expect(opciones.map((o) => o.textContent)).toEqual([
      'Todas',
      'Solo prioridad elevada',
      'Sin prioridad elevada',
    ]);
    expect(opciones.map((o) => o.value)).toEqual(['todas', 'elevada', 'sin-elevada']);
  });

  test('al abrir la pantalla la cola se pide sin los filtros nuevos', async () => {
    const consultarCola = conCola();
    montarModeracion(vista(), { api: { consultarCola } });
    await asentar();

    expect(consultarCola).toHaveBeenCalledTimes(1);
    expect(consultarCola).toHaveBeenLastCalledWith(SIN_FILTROS);
  });

  test('elegir una categoría recarga de inmediato con ella; «Todas» la quita', async () => {
    const consultarCola = conCola();
    const raiz = vista();
    montarModeracion(raiz, { api: { consultarCola } });
    await asentar();

    elegir(raiz, 'filtro-categoria', 'ACOSO');
    await asentar();
    expect(consultarCola).toHaveBeenCalledTimes(2);
    // Sin `pagina`: la consulta siempre es la primera página.
    expect(consultarCola).toHaveBeenLastCalledWith({ ...SIN_FILTROS, categoria: 'ACOSO' });

    elegir(raiz, 'filtro-categoria', '');
    await asentar();
    expect(consultarCola).toHaveBeenLastCalledWith(SIN_FILTROS);
  });

  test('la prioridad viaja como true, false o nada según lo elegido', async () => {
    const consultarCola = conCola();
    const raiz = vista();
    montarModeracion(raiz, { api: { consultarCola } });
    await asentar();

    elegir(raiz, 'filtro-prioridad', 'elevada');
    await asentar();
    expect(consultarCola).toHaveBeenLastCalledWith({ ...SIN_FILTROS, prioridadElevada: true });

    elegir(raiz, 'filtro-prioridad', 'sin-elevada');
    await asentar();
    expect(consultarCola).toHaveBeenLastCalledWith({ ...SIN_FILTROS, prioridadElevada: false });

    elegir(raiz, 'filtro-prioridad', 'todas');
    await asentar();
    expect(consultarCola).toHaveBeenLastCalledWith(SIN_FILTROS);
  });

  test('los tres filtros se combinan en una sola consulta', async () => {
    const consultarCola = conCola();
    const raiz = vista();
    montarModeracion(raiz, { api: { consultarCola }, productoId: 'espada-del-alba' });
    await asentar();

    elegir(raiz, 'filtro', 'marcados');
    elegir(raiz, 'filtro-categoria', 'SPAM');
    elegir(raiz, 'filtro-prioridad', 'elevada');
    await asentar();

    expect(consultarCola).toHaveBeenLastCalledWith({
      productoId: 'espada-del-alba',
      marcado: true,
      categoria: 'SPAM',
      prioridadElevada: true,
      tamano: 20,
    });
  });

  test('cambiar un filtro cierra el detalle abierto: ese comentario puede ya no cumplirlo', async () => {
    const api = {
      consultarCola: conCola(entrada()),
      consultarDetalle: jest.fn().mockResolvedValue(detalle()),
    };
    const raiz = vista();
    montarModeracion(raiz, { api });
    await asentar();
    raiz.querySelector('[data-accion="revisar"]').click();
    await asentar();
    expect(raiz.querySelector('[data-zona="detalle"]')).not.toBeNull();

    elegir(raiz, 'filtro-categoria', 'SPAM');
    await asentar();

    expect(raiz.querySelector('[data-zona="detalle"]')).toBeNull();
  });

  describe('cuando los filtros no dejan nada', () => {
    test('no es una cola sin trabajo ni un error: lo dice y ofrece «Quitar filtros»', async () => {
      const raiz = vista();
      montarModeracion(raiz, { api: { consultarCola: conCola() } });
      await asentar();

      elegir(raiz, 'filtro-categoria', 'SPAM');
      await asentar();

      const zona = raiz.querySelector('[data-zona="cola"]');
      expect(zona.querySelector('[data-estado="vacio"]')).not.toBeNull();
      expect(zona.querySelector('[data-estado="error"]')).toBeNull();
      expect(zona.textContent).toContain('Ningún comentario coincide con los filtros');
      expect(zona.textContent).not.toContain('No hay comentarios esperando revisión');
      const quitar = zona.querySelector('[data-accion="quitar-filtros"]');
      expect(quitar).not.toBeNull();
      expect(quitar.textContent).toBe('Quitar filtros');
    });

    test('«Solo prioridad elevada» sin resultados no parece un error ni dice por qué', async () => {
      const raiz = vista();
      montarModeracion(raiz, { api: { consultarCola: conCola() } });
      await asentar();

      elegir(raiz, 'filtro-prioridad', 'elevada');
      await asentar();

      const zona = raiz.querySelector('[data-zona="cola"]');
      const texto = textoDeLaCola(raiz);
      expect(zona.querySelector('[data-estado="vacio"]')).not.toBeNull();
      expect(zona.querySelector('[data-estado="error"]')).toBeNull();
      expect(texto).toContain('Ningún comentario coincide con los filtros');
      expect(texto).toContain(
        'En este momento ningún comentario de la cola tiene prioridad elevada.',
      );
      expect(texto).not.toContain('esperando revisión');
      // Ni una cifra, ni una explicación de umbral o de configuración.
      expect(texto).not.toMatch(/\d/);
      expect(texto).not.toMatch(/umbral|configur/i);
      expect(zona.querySelector('[data-accion="quitar-filtros"]')).not.toBeNull();
    });

    test('con solo una categoría no se afirma nada sobre la prioridad', async () => {
      const raiz = vista();
      montarModeracion(raiz, { api: { consultarCola: conCola() } });
      await asentar();

      elegir(raiz, 'filtro-categoria', 'ACOSO');
      await asentar();

      expect(textoDeLaCola(raiz)).not.toContain('prioridad elevada');
    });

    test('con «Marcados» y un filtro, manda el mensaje de los filtros', async () => {
      const raiz = vista();
      montarModeracion(raiz, { api: { consultarCola: conCola() } });
      await asentar();

      elegir(raiz, 'filtro', 'marcados');
      await asentar();
      expect(textoDeLaCola(raiz)).toContain('No hay comentarios marcados para seguimiento');

      elegir(raiz, 'filtro-categoria', 'ACOSO');
      await asentar();
      expect(textoDeLaCola(raiz)).toContain('Ningún comentario coincide con los filtros');
      expect(textoDeLaCola(raiz)).not.toContain('No hay comentarios marcados');
    });

    test('sin filtros, la cola vacía sigue diciendo que no hay trabajo y no ofrece quitar nada', async () => {
      const raiz = vista();
      montarModeracion(raiz, { api: { consultarCola: conCola() } });
      await asentar();

      expect(textoDeLaCola(raiz)).toContain('No hay comentarios esperando revisión');
      expect(raiz.querySelector('[data-accion="quitar-filtros"]')).toBeNull();
    });

    test('«Quitar filtros» limpia categoría y prioridad, deja «Mostrar», recarga y devuelve el foco', async () => {
      const vacia = { entradas: [], total: 0 };
      const consultarCola = jest
        .fn()
        .mockResolvedValueOnce(vacia)
        .mockResolvedValueOnce(vacia)
        .mockResolvedValueOnce(vacia)
        .mockResolvedValueOnce(vacia)
        .mockResolvedValue({ entradas: [entrada()], total: 1 });
      const raiz = vista();
      montarModeracion(raiz, { api: { consultarCola } });
      await asentar();
      elegir(raiz, 'filtro', 'marcados');
      elegir(raiz, 'filtro-categoria', 'SPAM');
      elegir(raiz, 'filtro-prioridad', 'elevada');
      await asentar();

      raiz.querySelector('[data-accion="quitar-filtros"]').click();
      await asentar();

      expect(raiz.querySelector('[data-zona="filtro-categoria"]').value).toBe('');
      expect(raiz.querySelector('[data-zona="filtro-prioridad"]').value).toBe('todas');
      expect(raiz.querySelector('[data-zona="filtro"]').value).toBe('marcados');
      expect(consultarCola).toHaveBeenLastCalledWith({ ...SIN_FILTROS, marcado: true });
      expect(raiz.querySelector('[data-comentario-id="com-1"]')).not.toBeNull();
      expect(document.activeElement).toBe(raiz.querySelector('[data-zona="filtro-categoria"]'));
    });
  });

  test('una respuesta antigua que llega tarde no pisa la cola del filtro vigente', async () => {
    let soltarLaPrimera;
    const lenta = new Promise((resolver) => {
      soltarLaPrimera = resolver;
    });
    const deOtro = (id) => entrada({ comentario: { ...entrada().comentario, id } });
    const consultarCola = jest
      .fn()
      .mockResolvedValueOnce({ entradas: [], total: 0 })
      .mockReturnValueOnce(lenta)
      .mockResolvedValueOnce({ entradas: [deOtro('com-nuevo')], total: 1 });
    const raiz = vista();
    montarModeracion(raiz, { api: { consultarCola } });
    await asentar();

    elegir(raiz, 'filtro-categoria', 'ACOSO');
    elegir(raiz, 'filtro-categoria', 'SPAM');
    await asentar();
    soltarLaPrimera({ entradas: [deOtro('com-viejo')], total: 1 });
    await asentar();

    expect(raiz.querySelector('[data-comentario-id="com-nuevo"]')).not.toBeNull();
    expect(raiz.querySelector('[data-comentario-id="com-viejo"]')).toBeNull();
  });

  describe('la página real', () => {
    const AQUI = dirname(fileURLToPath(import.meta.url));
    const HTML = readFileSync(join(AQUI, 'moderar-comentarios.html'), 'utf8');

    beforeEach(() => {
      document.documentElement.innerHTML = HTML.replace(/<script[\s\S]*?<\/script>/g, '');
    });

    test.each([
      ['filtro-categoria', 'Categoría del reporte'],
      ['filtro-prioridad', 'Prioridad'],
    ])(
      'el control %s tiene una etiqueta visible asociada, no solo un placeholder',
      (zona, texto) => {
        const control = document.querySelector(`select[data-zona="${zona}"]`);
        expect(control).not.toBeNull();
        expect(control.id).not.toBe('');
        const etiqueta = document.querySelector(`label[for="${control.id}"]`);
        expect(etiqueta).not.toBeNull();
        expect(etiqueta.textContent.trim()).toBe(texto);
        expect(control.options[0].textContent.trim()).toBe('Todas');
      },
    );
  });
});

/**
 * La decision en lote — HU-COM-005 (#519), comentarios.yaml 1.10.1.
 *
 * El servicio es atomico: o se resuelven todos los comentarios del lote o
 * ninguno. Lo que esta pantalla tiene que hacer bien es lo que el servicio no
 * puede: que el moderador sepa QUE esta a punto de decidir (seleccion visible,
 * contador, confirmacion escrita para eliminar), que nunca se decida sobre
 * comentarios que ya no ve, y que un rechazo se explique sin ids ni texto
 * tecnico y diciendo con verdad si algo cambio.
 */
describe('HU-COM-005 (#519): decisión en lote', () => {
  const UUID = '3f1c2b4a-1111-4222-8333-944455566677';

  function entradaDe(id, apodo, texto) {
    return entrada({ comentario: { id, apodoAutor: apodo, texto, estado: 'EN_REVISION' } });
  }

  const COLA = [
    entradaDe('com-1', 'LyraRoja', 'Primer texto reportado'),
    entradaDe('com-2', 'NyxAzul', 'Segundo texto reportado'),
    entradaDe('com-3', 'KaelVerde', 'Tercer texto reportado'),
  ];
  const SIN_TERCERO = [COLA[0], COLA[1]];
  const SIN_SEGUNDO = [COLA[0], COLA[2]];

  /** La vista de siempre con la zona del lote entre los filtros y la cola. */
  function vistaConLote() {
    const raiz = vista();
    const zona = document.createElement('div');
    zona.dataset.zona = 'lote';
    raiz.querySelector('[data-zona="cola"]').before(zona);
    return raiz;
  }

  function resultadoDeLote(
    accion = 'OCULTAR',
    ids = ['com-1', 'com-2'],
    notificados = ids.map(() => true),
  ) {
    return {
      accion,
      total: ids.length,
      resultados: ids.map((id, i) => ({
        comentarioId: id,
        asiento: { id: `a-${id}`, accion },
        autorNotificado: notificados[i],
      })),
    };
  }

  function apiDeLote(sobrescribir = {}) {
    return {
      consultarCola: jest.fn().mockResolvedValue({ entradas: COLA, total: 3 }),
      consultarDetalle: jest.fn().mockResolvedValue(detalle()),
      resolverEnLote: jest.fn().mockResolvedValue(resultadoDeLote()),
      ...sobrescribir,
    };
  }

  /** Dos respuestas de la cola: la de antes de decidir y la de despues. */
  function colaQueCambia(antes, despues) {
    return jest
      .fn()
      .mockResolvedValueOnce({ entradas: antes, total: antes.length })
      .mockResolvedValue({ entradas: despues, total: despues.length });
  }

  async function montar(api = apiDeLote(), opciones = {}) {
    const raiz = vistaConLote();
    montarModeracion(raiz, { api, ...opciones });
    await asentar();
    return raiz;
  }

  /** El elemento, o una aserción que dice cuál selector faltaba (no un TypeError). */
  function elemento(raiz, selector) {
    const encontrado = raiz.querySelector(selector);
    expect({ selector, encontrado }).toEqual({ selector, encontrado: expect.anything() });
    return encontrado;
  }

  const casilla = (raiz, id) =>
    elemento(raiz, `[data-comentario-id="${id}"] input[data-accion="seleccionar"]`);
  const maestra = (raiz) => elemento(raiz, 'input[data-accion="seleccionar-pagina"]');
  const contador = (raiz) => elemento(raiz, '[data-zona="contador-lote"]').textContent;
  const botonAplicar = (raiz) => elemento(raiz, '[data-accion="aplicar-lote"]');
  const campoMotivo = (raiz) => elemento(raiz, '#motivo-lote');
  const textoDelAviso = (raiz) => raiz.querySelector('[data-zona="aviso"]').textContent;
  const dialogo = () => document.querySelector('[role="dialog"]');

  function marcar(raiz, ...ids) {
    for (const id of ids) {
      casilla(raiz, id).click();
    }
  }

  function escribirMotivo(raiz, texto) {
    const motivo = campoMotivo(raiz);
    motivo.value = texto;
    motivo.dispatchEvent(new Event('input'));
  }

  function elegirAccion(raiz, accion) {
    const seleccion = elemento(raiz, '#accion-lote');
    seleccion.value = accion;
    seleccion.dispatchEvent(new Event('change'));
  }

  /** Marca, elige la acción y escribe el motivo: todo menos pulsar «Aplicar». */
  function prepararLote(
    raiz,
    { ids = ['com-1', 'com-2'], accion = 'OCULTAR', motivo = 'Spam coordinado' } = {},
  ) {
    marcar(raiz, ...ids);
    elegirAccion(raiz, accion);
    escribirMotivo(raiz, motivo);
  }

  /** Pulsa «Aplicar»: con el foco en el botón, como lo haría quien lo pulsa. */
  async function aplicar(raiz) {
    botonAplicar(raiz).focus();
    elemento(raiz, '[data-zona="decision-lote"]').dispatchEvent(
      new Event('submit', { cancelable: true }),
    );
    await asentar();
  }

  function escribirConfirmacion(texto) {
    const campo = dialogo().querySelector('input');
    campo.value = texto;
    campo.dispatchEvent(new Event('input'));
  }

  /** Un rechazo del lote, como lo lanza el cliente. */
  function rechazo(estado, problema = {}) {
    return new ErrorDeLote({ status: estado, ...problema }, estado);
  }

  // ------------------------------------------------------------- la seleccion

  describe('seleccionar', () => {
    test('P1: sin la zona del lote la pantalla se monta igual que antes', async () => {
      const raiz = vista();
      montarModeracion(raiz, { api: apiDeLote() });
      await asentar();

      expect(raiz.querySelectorAll('article[data-comentario-id]')).toHaveLength(3);
      expect(raiz.querySelector('input[data-accion="seleccionar"]')).toBeNull();
    });

    test('P2: cada tarjeta trae su casilla con un nombre que dice de quién es', async () => {
      const raiz = await montar();

      const primera = casilla(raiz, 'com-1');
      expect(primera.type).toBe('checkbox');
      expect(primera.getAttribute('aria-label')).toBe('Seleccionar el comentario de LyraRoja');
      expect(primera.closest('label').textContent).toContain('Seleccionar');
      expect(casilla(raiz, 'com-3').getAttribute('aria-label')).toBe(
        'Seleccionar el comentario de KaelVerde',
      );
    });

    test('P2b: la tarjeta con opciones avisa de cada cambio; sin ellas no trae casilla', () => {
      expect(
        tarjetaDeEntrada(COLA[0], () => {}).querySelector('input[data-accion="seleccionar"]'),
      ).toBeNull();

      const alCambiar = jest.fn();
      const tarjeta = tarjetaDeEntrada(COLA[0], () => {}, {
        seleccionable: true,
        seleccionada: true,
        alCambiarSeleccion: alCambiar,
      });
      const caja = elemento(tarjeta, 'input[data-accion="seleccionar"]');

      expect(caja.checked).toBe(true);
      caja.click();
      expect(alCambiar).toHaveBeenLastCalledWith(false);
      caja.click();
      expect(alCambiar).toHaveBeenLastCalledWith(true);
    });

    test('P3: el contador dice cuántos hay y se anuncia sin interrumpir', async () => {
      const raiz = await montar();

      const zona = elemento(raiz, '[data-zona="contador-lote"]');
      expect(zona.getAttribute('role')).toBe('status');
      expect(zona.getAttribute('aria-live')).toBe('polite');
      expect(contador(raiz)).toBe('Ninguno seleccionado');
      marcar(raiz, 'com-1');
      expect(contador(raiz)).toBe('1 seleccionado');
      marcar(raiz, 'com-2');
      expect(contador(raiz)).toBe('2 seleccionados');
    });

    test('P4: «Seleccionar los de esta página» marca todas y, al repetirlo, las desmarca', async () => {
      const raiz = await montar();

      expect(maestra(raiz).closest('label').textContent).toContain(
        'Seleccionar los de esta página',
      );
      maestra(raiz).click();
      for (const id of ['com-1', 'com-2', 'com-3']) {
        expect(casilla(raiz, id).checked).toBe(true);
      }
      expect(contador(raiz)).toBe('3 seleccionados');
      expect(maestra(raiz).checked).toBe(true);

      maestra(raiz).click();
      for (const id of ['com-1', 'com-2', 'com-3']) {
        expect(casilla(raiz, id).checked).toBe(false);
      }
      expect(contador(raiz)).toBe('Ninguno seleccionado');
    });

    test('P4b: con solo una parte marcada, la casilla maestra queda indeterminada', async () => {
      const raiz = await montar();

      marcar(raiz, 'com-1');
      expect(maestra(raiz).indeterminate).toBe(true);
      expect(maestra(raiz).checked).toBe(false);

      marcar(raiz, 'com-2', 'com-3');
      expect(maestra(raiz).indeterminate).toBe(false);
      expect(maestra(raiz).checked).toBe(true);
    });

    test('P5: «Limpiar selección» deja cero, se apaga y la maestra vuelve a vacía', async () => {
      const raiz = await montar();
      const limpiar = () => elemento(raiz, 'button[data-accion="limpiar-seleccion"]');

      expect(limpiar().disabled).toBe(true);
      marcar(raiz, 'com-1', 'com-2');
      expect(limpiar().disabled).toBe(false);
      limpiar().click();

      expect(contador(raiz)).toBe('Ninguno seleccionado');
      expect(casilla(raiz, 'com-1').checked).toBe(false);
      expect(maestra(raiz).checked).toBe(false);
      expect(maestra(raiz).indeterminate).toBe(false);
      expect(limpiar().disabled).toBe(true);
    });
  });

  // ------------------------------------------------------------ la barra

  describe('la barra de acciones', () => {
    test('P6: «Aplicar» necesita una selección y un motivo de verdad', async () => {
      const raiz = await montar();

      expect(botonAplicar(raiz).disabled).toBe(true);
      escribirMotivo(raiz, 'Spam coordinado');
      expect(botonAplicar(raiz).disabled).toBe(true); // motivo sin selección

      marcar(raiz, 'com-1');
      expect(botonAplicar(raiz).disabled).toBe(false);

      escribirMotivo(raiz, 'ab');
      expect(botonAplicar(raiz).disabled).toBe(true); // motivo demasiado corto
      escribirMotivo(raiz, '     ');
      expect(botonAplicar(raiz).disabled).toBe(true); // solo espacios no cuentan
      escribirMotivo(raiz, 'abc');
      expect(botonAplicar(raiz).disabled).toBe(false);
    });

    test('P7: ofrece las seis acciones del lote y no «Editar el texto»', async () => {
      const raiz = await montar();

      const opciones = [...elemento(raiz, '#accion-lote').options];
      expect(opciones.map((o) => o.value)).toEqual([
        'APROBAR',
        'OCULTAR',
        'ELIMINAR',
        'RESTAURAR',
        'MARCAR',
        'DESMARCAR',
      ]);
      expect(opciones.map((o) => o.textContent)).toEqual(ACCIONES_EN_LOTE.map((a) => a.etiqueta));
      expect(opciones.map((o) => o.value)).not.toContain('EDITAR');
    });

    test('P8: la pista del motivo avisa de que MARCAR y DESMARCAR son una nota interna', async () => {
      const raiz = await montar();
      const pista = () => elemento(raiz, '#motivo-lote-pista').textContent;

      elegirAccion(raiz, 'OCULTAR');
      expect(pista()).not.toContain('nota interna');
      elegirAccion(raiz, 'MARCAR');
      expect(pista()).toContain('nota interna');
      elegirAccion(raiz, 'DESMARCAR');
      expect(pista()).toContain('nota interna');
    });
  });

  // ------------------------------------------------------------ el exito

  describe('aplicar el lote', () => {
    test('P9: manda los ids en el orden de la cola, con el motivo limpio y sin confirmación', async () => {
      const api = apiDeLote();
      const raiz = await montar(api);

      marcar(raiz, 'com-2', 'com-1'); // al revés: manda el orden de la cola
      elegirAccion(raiz, 'OCULTAR');
      escribirMotivo(raiz, '  Spam coordinado  ');
      await aplicar(raiz);

      expect(api.resolverEnLote).toHaveBeenCalledTimes(1);
      const [enviado] = api.resolverEnLote.mock.calls[0];
      expect(enviado).toEqual({
        comentarioIds: ['com-1', 'com-2'],
        accion: 'OCULTAR',
        motivo: 'Spam coordinado',
      });
      expect(Object.keys(enviado)).not.toContain('confirmacion');
      expect(dialogo()).toBeNull();

      expect(textoDelAviso(raiz)).toContain('Comentarios ocultados');
      expect(textoDelAviso(raiz)).toContain('Se avisó a los autores');
      expect(contador(raiz)).toBe('Ninguno seleccionado');
      expect(campoMotivo(raiz).value).toBe('');
      // Se recargó la cola y el aviso sobrevivió a la recarga.
      expect(api.consultarCola).toHaveBeenCalledTimes(2);
    });

    test('P10: si el aviso a algún autor no salió, se dice, sin ids', async () => {
      const api = apiDeLote({
        resolverEnLote: jest
          .fn()
          .mockResolvedValue(resultadoDeLote('OCULTAR', ['com-1', 'com-2'], [true, false])),
      });
      const raiz = await montar(api);

      prepararLote(raiz);
      await aplicar(raiz);

      expect(textoDelAviso(raiz)).toContain('La decisión quedó registrada');
      expect(textoDelAviso(raiz)).toContain('no salió');
      expect(textoDelAviso(raiz)).not.toContain('com-');
    });

    test('P11: MARCAR es una nota interna: los autores no reciben aviso y se dice', async () => {
      const api = apiDeLote({
        resolverEnLote: jest
          .fn()
          .mockResolvedValue(resultadoDeLote('MARCAR', ['com-1', 'com-2'], [false, false])),
      });
      const raiz = await montar(api);

      prepararLote(raiz, { accion: 'MARCAR' });
      await aplicar(raiz);

      expect(textoDelAviso(raiz)).toContain('Comentarios marcados para seguimiento');
      expect(textoDelAviso(raiz)).toContain('nota interna');
      expect(textoDelAviso(raiz)).not.toContain('no salió');
    });

    test('P12: mientras vuela, el botón espera y un segundo envío no duplica la llamada', async () => {
      let soltar;
      const enVuelo = new Promise((resolver) => {
        soltar = resolver;
      });
      const api = apiDeLote({ resolverEnLote: jest.fn().mockReturnValue(enVuelo) });
      const raiz = await montar(api);

      prepararLote(raiz);
      await aplicar(raiz);
      expect(botonAplicar(raiz).disabled).toBe(true);
      expect(botonAplicar(raiz).getAttribute('aria-busy')).toBe('true');

      elemento(raiz, '[data-zona="decision-lote"]').dispatchEvent(
        new Event('submit', { cancelable: true }),
      );
      await asentar();
      expect(api.resolverEnLote).toHaveBeenCalledTimes(1);

      soltar(resultadoDeLote());
      await asentar();
      expect(botonAplicar(raiz).getAttribute('aria-busy')).not.toBe('true');
    });

    test('P13: tras el éxito el foco queda en la casilla maestra, donde sigue el trabajo', async () => {
      const raiz = await montar();

      prepararLote(raiz);
      await aplicar(raiz);

      expect(document.activeElement).toBe(maestra(raiz));
    });
  });

  // ------------------------------------------------------------ ELIMINAR

  describe('eliminar en lote', () => {
    async function conLoteDeEliminar(api = apiDeLote()) {
      api.resolverEnLote.mockResolvedValue(resultadoDeLote('ELIMINAR'));
      const raiz = await montar(api);
      prepararLote(raiz, { accion: 'ELIMINAR', motivo: 'Limpieza de spam' });
      await aplicar(raiz);
      return { api, raiz };
    }

    test('P14: pide escribir ELIMINAR tal cual y no llama al servicio antes', async () => {
      const { api } = await conLoteDeEliminar();

      const caja = dialogo();
      expect(caja).not.toBeNull();
      expect(caja.getAttribute('aria-modal')).toBe('true');
      expect(caja.textContent).toContain('Eliminar los comentarios seleccionados');
      expect(caja.textContent).toContain('2 comentarios');
      expect(caja.textContent).toContain('con el motivo');
      const confirmar = caja.querySelector('[data-accion="confirmar"]');
      expect(confirmar.disabled).toBe(true);
      for (const intento of ['eliminar', ' eliminar', 'ELIMINA', 'ELIMINAR!']) {
        escribirConfirmacion(intento);
        expect(confirmar.disabled).toBe(true);
      }
      expect(api.resolverEnLote).not.toHaveBeenCalled();
    });

    test('P15: al confirmar manda confirmacion ELIMINAR y cierra el diálogo', async () => {
      const { api, raiz } = await conLoteDeEliminar();

      escribirConfirmacion('ELIMINAR');
      dialogo().querySelector('[data-accion="confirmar"]').click();
      await asentar();

      expect(api.resolverEnLote).toHaveBeenCalledTimes(1);
      expect(api.resolverEnLote.mock.calls[0][0]).toEqual({
        comentarioIds: ['com-1', 'com-2'],
        accion: 'ELIMINAR',
        motivo: 'Limpieza de spam',
        confirmacion: 'ELIMINAR',
      });
      expect(dialogo()).toBeNull();
      expect(textoDelAviso(raiz)).toContain('Comentarios eliminados');
    });

    test.each([
      ['el botón Cancelar', () => dialogo().querySelector('[data-accion="cancelar"]').click()],
      [
        'Escape',
        () =>
          document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })),
      ],
    ])(
      'P16: cerrar con %s no llama, no pierde nada y devuelve el foco a «Aplicar»',
      async (_como, cerrar) => {
        const { api, raiz } = await conLoteDeEliminar();

        cerrar();
        await asentar();

        expect(dialogo()).toBeNull();
        expect(api.resolverEnLote).not.toHaveBeenCalled();
        expect(contador(raiz)).toBe('2 seleccionados');
        expect(campoMotivo(raiz).value).toBe('Limpieza de spam');
        expect(document.activeElement).toBe(botonAplicar(raiz));
      },
    );

    test.each(['APROBAR', 'OCULTAR', 'RESTAURAR', 'MARCAR', 'DESMARCAR'])(
      'P17: %s no abre ninguna confirmación',
      async (accion) => {
        const api = apiDeLote({
          resolverEnLote: jest.fn().mockResolvedValue(resultadoDeLote(accion)),
        });
        const raiz = await montar(api);

        prepararLote(raiz, { accion });
        await aplicar(raiz);

        expect(dialogo()).toBeNull();
        expect(api.resolverEnLote).toHaveBeenCalledTimes(1);
      },
    );
  });

  // ------------------------------------------------------------ los rechazos

  describe('los rechazos del servicio', () => {
    /** Tres marcados, un motivo escrito, y un servicio que rechaza. */
    async function rechazado(error, { cola = apiDeLote().consultarCola, ids } = {}) {
      const api = apiDeLote({
        consultarCola: cola,
        resolverEnLote: jest.fn().mockRejectedValue(error),
      });
      const raiz = await montar(api);
      prepararLote(raiz, { ids: ids ?? ['com-1', 'com-2', 'com-3'], motivo: 'Spam coordinado' });
      await aplicar(raiz);
      return { api, raiz };
    }

    test('P18: 409 explica que otro moderador se adelantó, lista a quién y no pierde el trabajo', async () => {
      const { api, raiz } = await rechazado(
        rechazo(409, {
          motivo: 'TRANSICION_INVALIDA',
          comentarioIds: ['com-2'],
          detail: 'No se puede aplicar OCULTAR a los comentarios [com-2]',
        }),
        { cola: colaQueCambia(COLA, SIN_SEGUNDO) },
      );

      expect(textoDelAviso(raiz)).toContain('Otro moderador se adelantó');
      expect(textoDelAviso(raiz)).toContain('no se cambió ninguno');
      const afectados = raiz.querySelectorAll('[data-zona="afectados"] li');
      expect(afectados).toHaveLength(1);
      expect(afectados[0].textContent).toContain('NyxAzul');
      expect(textoDelAviso(raiz)).not.toContain('com-2');
      // Se recargó la cola, el aviso sobrevivió y el resto del trabajo sigue.
      expect(api.consultarCola).toHaveBeenCalledTimes(2);
      expect(contador(raiz)).toBe('2 seleccionados');
      expect(casilla(raiz, 'com-1').checked).toBe(true);
      expect(casilla(raiz, 'com-3').checked).toBe(true);
      expect(campoMotivo(raiz).value).toBe('Spam coordinado');
    });

    test('P19: un comentario que ya no está en pantalla se dice sin enseñar su identificador', async () => {
      const { raiz } = await rechazado(
        rechazo(409, { motivo: 'TRANSICION_INVALIDA', comentarioIds: ['com-fantasma'] }),
      );

      expect(textoDelAviso(raiz)).toContain('un comentario que ya no está en la cola');
      expect(document.body.textContent).not.toContain('com-fantasma');
      expect(raiz.querySelectorAll('[data-zona="afectados"] li')).toHaveLength(1);
    });

    test('P20: 404 dice que ya no están, los nombra, recarga y los saca de la selección', async () => {
      const { api, raiz } = await rechazado(rechazo(404, { comentarioIds: ['com-3'] }), {
        cola: colaQueCambia(COLA, SIN_TERCERO),
      });

      expect(textoDelAviso(raiz)).toContain('Algunos comentarios ya no están');
      expect(textoDelAviso(raiz)).toContain('No se cambió ningún comentario');
      expect(raiz.querySelector('[data-zona="afectados"]').textContent).toContain('KaelVerde');
      expect(api.consultarCola).toHaveBeenCalledTimes(2);
      expect(contador(raiz)).toBe('2 seleccionados');
    });

    test('P21: 400 CONFIRMACION_REQUERIDA se dice con texto propio, no con el del servidor', async () => {
      const { raiz } = await rechazado(
        rechazo(400, {
          motivo: 'CONFIRMACION_REQUERIDA',
          detail: 'Eliminar en lote exige confirmacion con el valor ELIMINAR',
        }),
        { ids: ['com-1', 'com-2'] },
      );

      expect(textoDelAviso(raiz)).toContain('Falta la confirmación');
      expect(textoDelAviso(raiz)).not.toContain('exige confirmacion');
      expect(contador(raiz)).toBe('2 seleccionados');
    });

    test('P22: cualquier otro 400 pide revisar, sin repetir lo que diga el servidor', async () => {
      const { raiz } = await rechazado(
        rechazo(400, { detail: 'La decision en lote admite hasta 100 comentarios' }),
      );

      expect(textoDelAviso(raiz)).toContain('Revisa la selección y el motivo');
      expect(textoDelAviso(raiz)).not.toContain('100');
      expect(contador(raiz)).toBe('3 seleccionados');
    });

    test('P23: 401 ofrece volver a entrar', async () => {
      const { raiz } = await rechazado(rechazo(401));

      expect(textoDelAviso(raiz)).toContain('Tu sesión ya no es válida');
      expect(elemento(raiz, '[data-accion="iniciar-sesion"]')).not.toBeNull();
    });

    test('P24: 403 lo dice sin ofrecer reintentar y apaga la barra', async () => {
      const { raiz } = await rechazado(rechazo(403));

      expect(textoDelAviso(raiz)).toContain('Tu cuenta no puede resolver comentarios');
      expect(raiz.querySelector('[data-zona="aviso"] button')).toBeNull();
      expect(botonAplicar(raiz).disabled).toBe(true);
      expect(elemento(raiz, '#accion-lote').disabled).toBe(true);
    });

    test.each([
      ['una caída de red', new TypeError('Failed to fetch')],
      ['un 502', rechazo(502)],
      ['un 500', rechazo(500, { detail: 'NullPointerException' })],
    ])(
      'P25: %s no promete que no cambió nada y ofrece actualizar la cola',
      async (_cual, error) => {
        const { api, raiz } = await rechazado(error, { ids: ['com-1', 'com-2'] });

        expect(textoDelAviso(raiz)).toContain('No se pudo aplicar la decisión');
        expect(textoDelAviso(raiz)).not.toMatch(/no se cambi/i);
        expect(elemento(raiz, '[data-accion="actualizar-cola"]')).not.toBeNull();
        expect(contador(raiz)).toBe('2 seleccionados');
        expect(campoMotivo(raiz).value).toBe('Spam coordinado');
        expect(api.consultarCola).toHaveBeenCalledTimes(1);
      },
    );

    test('P25b: «Actualizar la cola» recarga y deja en la selección solo lo que sigue en la cola', async () => {
      const { api, raiz } = await rechazado(rechazo(502), {
        cola: colaQueCambia(COLA, SIN_TERCERO),
        ids: ['com-1', 'com-3'],
      });

      elemento(raiz, '[data-accion="actualizar-cola"]').click();
      await asentar();

      expect(api.consultarCola).toHaveBeenCalledTimes(2);
      expect(contador(raiz)).toBe('1 seleccionado');
      expect(casilla(raiz, 'com-1').checked).toBe(true);
    });

    test('P26: ni el texto técnico del servidor ni los ids llegan a la pantalla, y los nombres no son marcado', async () => {
      const cola = [
        COLA[0],
        entradaDe('com-2', '<img src=x onerror="robar()">', 'Texto cualquiera'),
        COLA[2],
      ];
      const api = apiDeLote({
        consultarCola: jest.fn().mockResolvedValue({ entradas: cola, total: 3 }),
        resolverEnLote: jest.fn().mockRejectedValue(
          rechazo(409, {
            motivo: 'TRANSICION_INVALIDA',
            detail: `NullPointerException at com.nexusbattles.Servicio (X.java:1) ${UUID}`,
            comentarioIds: [UUID, 'com-2'],
          }),
        ),
      });
      const raiz = await montar(api);
      prepararLote(raiz, { ids: ['com-1', 'com-2'] });
      await aplicar(raiz);

      expect(document.body.textContent).not.toContain('NullPointerException');
      expect(document.body.textContent).not.toContain(UUID);
      const lista = elemento(raiz, '[data-zona="afectados"]');
      expect(lista.textContent).toContain('<img src=x');
      expect(lista.querySelector('img')).toBeNull();
    });
  });

  // ------------------------------------------- filtros, páginas y cola vacía

  describe('la selección frente a los filtros y la paginación', () => {
    test.each([
      ['filtro', 'marcados'],
      ['filtro-categoria', 'ACOSO'],
      ['filtro-prioridad', 'elevada'],
    ])('P27: cambiar el filtro «%s» limpia la selección', async (zona, valor) => {
      const raiz = await montar();

      marcar(raiz, 'com-1', 'com-2');
      expect(contador(raiz)).toBe('2 seleccionados');
      const filtro = raiz.querySelector(`[data-zona="${zona}"]`);
      filtro.value = valor;
      filtro.dispatchEvent(new Event('change'));
      await asentar();

      expect(contador(raiz)).toBe('Ninguno seleccionado');
      expect(casilla(raiz, 'com-1').checked).toBe(false);
    });

    test('P29: si la cola tiene más de lo que se ve, lo dice; «esta página» cuenta solo lo visible', async () => {
      const api = apiDeLote({
        consultarCola: jest.fn().mockResolvedValue({ entradas: COLA, total: 25 }),
      });
      const raiz = await montar(api);

      const nota = elemento(raiz, '[data-zona="nota-lote"]');
      expect(nota.hidden).toBe(false);
      expect(nota.textContent).toContain('solo alcanza a los que seleccionaste');
      maestra(raiz).click();
      expect(contador(raiz)).toBe('3 seleccionados');
    });

    test('P29b: si se ve toda la cola, no hay nota', async () => {
      const raiz = await montar();

      expect(casilla(raiz, 'com-1')).not.toBeNull();
      const nota = raiz.querySelector('[data-zona="nota-lote"]');
      expect(nota === null || nota.hidden).toBe(true);
    });

    test('P30: con la cola vacía no hay barra', async () => {
      const api = apiDeLote({
        consultarCola: jest.fn().mockResolvedValue({ entradas: [], total: 0 }),
      });
      const raiz = await montar(api);

      expect(elemento(raiz, '[data-zona="lote"]').hidden).toBe(true);
    });
  });

  // ------------------------------------------------------- rol y página real

  describe('el rol y la página', () => {
    test.each(['MODERADOR', 'ADMINISTRADOR', 'SUPER_ADMINISTRADOR'])(
      'P31: con el rol %s la barra está disponible',
      async (rol) => {
        const raiz = await montar(apiDeLote(), { rol });

        expect(elemento(raiz, '[data-zona="decision-lote"]')).not.toBeNull();
        expect(botonAplicar(raiz)).not.toBeNull();
      },
    );

    describe('la página real', () => {
      const AQUI = dirname(fileURLToPath(import.meta.url));
      const HTML = readFileSync(join(AQUI, 'moderar-comentarios.html'), 'utf8');

      beforeEach(() => {
        document.documentElement.innerHTML = HTML.replace(/<script[\s\S]*?<\/script>/g, '');
      });

      test('P32: trae la zona del lote, antes de la cola', () => {
        const lote = elemento(document, '[data-zona="lote"]');
        const cola = elemento(document, '[data-zona="cola"]');

        expect(lote.compareDocumentPosition(cola) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
      });
    });
  });
});
