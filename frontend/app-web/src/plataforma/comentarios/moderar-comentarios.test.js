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

import { jest } from '@jest/globals';

import { montarModeracion, panelDeDetalle, tarjetaDeEntrada } from './moderar-comentarios.js';
import { accionesDesde, ErrorDeApi } from './cliente-moderacion.js';
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
