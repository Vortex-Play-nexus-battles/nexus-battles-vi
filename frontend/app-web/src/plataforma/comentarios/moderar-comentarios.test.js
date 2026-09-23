/**
 * La cola de moderacion — RF-COM-005 y RF-COM-008 (R10.1).
 *
 * Lo que se prueba aqui es lo que distingue esta pantalla de una lista:
 * que una cola vacia NO es un error, que el motivo es obligatorio antes de
 * pulsar y no despues, que las acciones ofrecidas dependen del estado, y que
 * el resultado dice la verdad sobre si el autor se entero.
 */

import { jest } from '@jest/globals';

import { montarModeracion, panelDeDetalle, tarjetaDeEntrada } from './moderar-comentarios.js';
import { accionesDesde } from './cliente-moderacion.js';

const AHORA = '2026-09-23T10:00:00Z';

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
});

describe('el detalle y la decision', () => {
  test('solo ofrece las acciones que valen desde el estado actual', () => {
    const enRevision = panelDeDetalle(detalle(), () => {});
    const ofrecidas = [...enRevision.querySelectorAll('option')].map((o) => o.value);
    expect(ofrecidas).toEqual(['APROBAR', 'OCULTAR', 'ELIMINAR']);
    // No se pinta «Restaurar» sobre algo que no esta oculto: seria un boton
    // que el servicio contesta con 409.
    expect(ofrecidas).not.toContain('RESTAURAR');

    const oculto = panelDeDetalle(
      detalle({ comentario: { ...detalle().comentario, estado: 'OCULTO' } }),
      () => {},
    );
    expect([...oculto.querySelectorAll('option')].map((o) => o.value)).toEqual([
      'ELIMINAR',
      'RESTAURAR',
    ]);
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
      comentario: { ...entrada().comentario, estado: 'OCULTO' },
      asiento: { id: 'a-1' },
      autorNotificado: true,
    });
    const raiz = await decidir(api);

    expect(api.resolverComentario).toHaveBeenCalledWith('com-1', {
      accion: 'APROBAR',
      motivo: 'Acoso a otro jugador',
    });
    expect(raiz.querySelector('[data-zona="aviso"]').textContent).toContain('Se aviso al autor');
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
      'Otro moderador se adelanto',
    );
    // Dos veces: la del montaje y la de despues del conflicto. Dejar la
    // pantalla vieja seria invitar al segundo intento contra el mismo estado.
    expect(api.consultarCola).toHaveBeenCalledTimes(2);
  });
});
