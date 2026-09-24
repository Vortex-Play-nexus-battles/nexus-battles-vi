/**
 * HU-SAL-006 — Sala de espera: salir o cancelar antes de que empiece.
 *
 * Lo que se prueba: que se ofrezca el boton que corresponde a quien mira y
 * solo ese; que cancelar pregunte y salir no (CA-05); que un rechazo del
 * servidor se muestre sin mover a nadie de pantalla; que al empezar la
 * partida los botones desaparezcan (CA-03); y que el aviso que se deja al
 * listado se lea una sola vez.
 */

import { jest } from '@jest/globals';

import {
  montarSalaDeEspera,
  textoDeConfirmacion,
  textoDeOcupacion,
  dejarAvisoParaElListado,
  recogerAvisoDelListado,
  salidaAlListado,
  invitacionDe,
  montarInvitacion,
  CLAVE_AVISO_DEL_LISTADO,
} from './sala-de-espera.js';

const ANFITRION = 'aaaaaaaa-0000-0000-0000-000000000001';
const VISITANTE = 'bbbbbbbb-0000-0000-0000-000000000002';

const VISTA = `
  <div data-zona="espera" hidden>
    <span data-zona="ocupacion"></span>
    <button type="button" data-accion="salir-de-sala" hidden>Salir de la sala</button>
    <button type="button" data-accion="cancelar-sala" hidden>Cancelar sala</button>
    <span data-zona="aviso-espera"></span>
  </div>
`;

function sala(cambios = {}) {
  return {
    id: 's1',
    idAnfitrion: ANFITRION,
    ocupacion: 2,
    maximoParticipantes: 4,
    estado: 'ABIERTA',
    ...cambios,
  };
}

/** Un `Storage` de mentira con la misma forma que sessionStorage. */
function almacen() {
  const datos = new Map();
  return {
    getItem: (k) => (datos.has(k) ? datos.get(k) : null),
    setItem: (k, v) => datos.set(k, String(v)),
    removeItem: (k) => datos.delete(k),
  };
}

const tick = () => new Promise((resolve) => setTimeout(resolve, 0));

beforeEach(() => {
  document.body.innerHTML = VISTA;
});

describe('textoDeConfirmacion (CA-05)', () => {
  test('cuenta a los demas, no al anfitrion', () => {
    expect(textoDeConfirmacion({ ocupacion: 3 })).toBe(
      '¿Cancelar la sala? Se expulsará a 2 participantes.',
    );
    expect(textoDeConfirmacion({ ocupacion: 2 })).toBe(
      '¿Cancelar la sala? Se expulsará a 1 participante.',
    );
  });

  test('solo el anfitrion dentro: no se expulsa a nadie', () => {
    expect(textoDeConfirmacion({ ocupacion: 1 })).toMatch(/no ha entrado nadie/i);
  });
});

describe('textoDeOcupacion', () => {
  test('dice cuantos hay de cuantos caben', () => {
    expect(textoDeOcupacion({ actual: 2, maximo: 4 })).toBe('2 de 4 jugadores en la sala');
  });
});

describe('montarSalaDeEspera · quien ve que', () => {
  test('el anfitrion ve «Cancelar sala» y no «Salir»', () => {
    const espera = montarSalaDeEspera(document, {
      sala: sala(),
      yo: ANFITRION,
      abandonar: jest.fn(),
      cancelar: jest.fn(),
    });

    expect(espera.esAnfitrion).toBe(true);
    expect(document.querySelector('[data-zona="espera"]').hidden).toBe(false);
    expect(document.querySelector('[data-accion="cancelar-sala"]').hidden).toBe(false);
    expect(document.querySelector('[data-accion="salir-de-sala"]').hidden).toBe(true);
    expect(document.querySelector('[data-zona="ocupacion"]').textContent).toBe(
      '2 de 4 jugadores en la sala',
    );
  });

  test('un participante ve «Salir de la sala» y no «Cancelar»', () => {
    const espera = montarSalaDeEspera(document, {
      sala: sala(),
      yo: VISITANTE,
      abandonar: jest.fn(),
      cancelar: jest.fn(),
    });

    expect(espera.esAnfitrion).toBe(false);
    expect(document.querySelector('[data-accion="salir-de-sala"]').hidden).toBe(false);
    expect(document.querySelector('[data-accion="cancelar-sala"]').hidden).toBe(true);
  });

  test('sin identidad no se es anfitrion de nada', () => {
    const espera = montarSalaDeEspera(document, {
      sala: sala(),
      yo: null,
      abandonar: jest.fn(),
      cancelar: jest.fn(),
    });

    expect(espera.esAnfitrion).toBe(false);
  });

  test('CA-03: con la partida en curso no se ofrece ninguno de los dos', () => {
    montarSalaDeEspera(document, {
      sala: sala({ estado: 'EN_JUEGO' }),
      yo: ANFITRION,
      abandonar: jest.fn(),
      cancelar: jest.fn(),
    });

    expect(document.querySelector('[data-zona="espera"]').hidden).toBe(true);
    expect(document.querySelector('[data-accion="cancelar-sala"]').hidden).toBe(true);
    expect(document.querySelector('[data-accion="salir-de-sala"]').hidden).toBe(true);
  });

  test('ocultar() retira la zona cuando el combate arranca desde otro sitio', () => {
    const espera = montarSalaDeEspera(document, {
      sala: sala(),
      yo: VISITANTE,
      abandonar: jest.fn(),
      cancelar: jest.fn(),
    });

    espera.ocultar();

    expect(document.querySelector('[data-zona="espera"]').hidden).toBe(true);
  });

  test('actualizar() repinta la ocupacion con lo que diga el canal', () => {
    const espera = montarSalaDeEspera(document, {
      sala: sala(),
      yo: VISITANTE,
      abandonar: jest.fn(),
      cancelar: jest.fn(),
    });

    espera.actualizar({ ocupacion: { actual: 3, maximo: 4 } });

    expect(document.querySelector('[data-zona="ocupacion"]').textContent).toBe(
      '3 de 4 jugadores en la sala',
    );
  });
});

describe('montarSalaDeEspera · salir (CA-01)', () => {
  test('pulsar «Salir» llama al servicio con la sala y avisa que se salio, sin preguntar', async () => {
    const abandonar = jest.fn().mockResolvedValue(undefined);
    const confirmar = jest.fn(() => false);
    const alSalir = jest.fn();
    montarSalaDeEspera(document, {
      sala: sala(),
      yo: VISITANTE,
      abandonar,
      cancelar: jest.fn(),
      confirmar,
      alSalir,
    });

    document.querySelector('[data-accion="salir-de-sala"]').click();
    await tick();

    expect(abandonar).toHaveBeenCalledWith('s1');
    expect(confirmar).not.toHaveBeenCalled();
    expect(alSalir).toHaveBeenCalledWith({ motivo: 'abandono' });
  });

  test('si el servidor rechaza (409: ya empezo), se muestra el motivo y no se sale', async () => {
    const abandonar = jest.fn().mockRejectedValue({
      estado: 409,
      detalle: 'La partida ya comenzo: no puedes abandonar la sala.',
    });
    const alSalir = jest.fn();
    montarSalaDeEspera(document, {
      sala: sala(),
      yo: VISITANTE,
      abandonar,
      cancelar: jest.fn(),
      alSalir,
    });

    const boton = document.querySelector('[data-accion="salir-de-sala"]');
    boton.click();
    await tick();

    expect(alSalir).not.toHaveBeenCalled();
    expect(document.querySelector('[data-zona="aviso-espera"]').textContent).toContain(
      'ya comenzo',
    );
    expect(boton.disabled).toBe(false);
  });
});

describe('montarSalaDeEspera · cancelar (CA-02, CA-05)', () => {
  test('pulsar «Cancelar» pregunta con el número de expulsados y, si se acepta, cancela', async () => {
    const cancelar = jest.fn().mockResolvedValue(undefined);
    const confirmar = jest.fn(() => true);
    const alSalir = jest.fn();
    montarSalaDeEspera(document, {
      sala: sala({ ocupacion: 3 }),
      yo: ANFITRION,
      abandonar: jest.fn(),
      cancelar,
      confirmar,
      alSalir,
    });

    document.querySelector('[data-accion="cancelar-sala"]').click();
    await tick();

    expect(confirmar).toHaveBeenCalledWith('¿Cancelar la sala? Se expulsará a 2 participantes.');
    expect(cancelar).toHaveBeenCalledWith('s1');
    expect(alSalir).toHaveBeenCalledWith({ motivo: 'cancelada' });
  });

  test('la confirmación usa la ocupacion VIVA, no la de cuando se monto', async () => {
    const confirmar = jest.fn(() => false);
    const espera = montarSalaDeEspera(document, {
      sala: sala({ ocupacion: 1 }),
      yo: ANFITRION,
      abandonar: jest.fn(),
      cancelar: jest.fn(),
      confirmar,
    });
    espera.actualizar({ ocupacion: { actual: 4, maximo: 4 } });

    document.querySelector('[data-accion="cancelar-sala"]').click();
    await tick();

    expect(confirmar).toHaveBeenCalledWith('¿Cancelar la sala? Se expulsará a 3 participantes.');
  });

  test('si no se confirma, no se llama al servicio', async () => {
    const cancelar = jest.fn();
    montarSalaDeEspera(document, {
      sala: sala(),
      yo: ANFITRION,
      abandonar: jest.fn(),
      cancelar,
      confirmar: () => false,
    });

    document.querySelector('[data-accion="cancelar-sala"]').click();
    await tick();

    expect(cancelar).not.toHaveBeenCalled();
    expect(document.querySelector('[data-accion="cancelar-sala"]').disabled).toBe(false);
  });
});

describe('aviso para el listado', () => {
  test('lo que se deja se recoge una sola vez', () => {
    const storage = almacen();

    dejarAvisoParaElListado(storage, { tono: 'info', titulo: 'Saliste de la sala.' });

    expect(recogerAvisoDelListado(storage)).toEqual({
      tono: 'info',
      titulo: 'Saliste de la sala.',
    });
    expect(recogerAvisoDelListado(storage)).toBeNull();
    expect(storage.getItem(CLAVE_AVISO_DEL_LISTADO)).toBeNull();
  });

  test('basura en el almacen no rompe el listado', () => {
    const storage = almacen();
    storage.setItem(CLAVE_AVISO_DEL_LISTADO, '{no es json');

    expect(recogerAvisoDelListado(storage)).toBeNull();
  });

  test('un almacen que falla no impide volver', () => {
    const roto = {
      setItem: () => {
        throw new Error('QuotaExceeded');
      },
      getItem: () => {
        throw new Error('no');
      },
      removeItem: () => {},
    };

    expect(() => dejarAvisoParaElListado(roto, { tono: 'info', titulo: 'x' })).not.toThrow();
    expect(recogerAvisoDelListado(roto)).toBeNull();
  });
});

describe('salidaAlListado · una sola navegacion', () => {
  test('al anfitrion que cancela le llega el hecho por dos caminos y solo navega una vez', () => {
    const storage = almacen();
    const navegar = jest.fn();
    const volver = salidaAlListado(storage, navegar);

    // Primero la respuesta del boton, despues su propio aviso por el canal.
    expect(volver({ tono: 'info', titulo: 'Cancelaste la sala.' })).toBe(true);
    expect(volver({ tono: 'info', titulo: 'Cancelaste la sala.' })).toBe(false);

    expect(navegar).toHaveBeenCalledTimes(1);
    expect(navegar).toHaveBeenCalledWith('./batallas.html');
    // El aviso que queda es el de la primera llamada, no se pisa.
    expect(recogerAvisoDelListado(storage)).toEqual({
      tono: 'info',
      titulo: 'Cancelaste la sala.',
    });
  });

  test('el destino se puede cambiar y el aviso llega antes de navegar', () => {
    const storage = almacen();
    const orden = [];
    const navegar = jest.fn((destino) =>
      orden.push(['navegar', destino, storage.getItem(CLAVE_AVISO_DEL_LISTADO) !== null]),
    );
    const volver = salidaAlListado(storage, navegar, '../otro.html');

    volver({ tono: 'advertencia', titulo: 'La sala se cerró' });

    expect(orden).toEqual([['navegar', '../otro.html', true]]);
  });
});

/**
 * FI-R4 — el anfitrion puede repartir la invitacion.
 *
 * `GET /salas/{id}` devuelve `codigoInvitacion` solo al anfitrion
 * (`SalaResponse.segunQuienPregunta`), y el frontend no lo miraba en ningun
 * sitio: el codigo se generaba, se guardaba en `codigo_invitacion` y moria
 * ahi.
 */
describe('FI-R4 - la invitacion de una sala privada', () => {
  const HTML_INVITACION = `
    <main id="raiz">
      <div data-zona="invitacion" hidden>
        <code data-zona="codigo-invitacion"></code>
        <button type="button" data-accion="copiar-codigo">Copiar código</button>
        <button type="button" data-accion="copiar-enlace">Copiar enlace</button>
        <span data-zona="acuse-copia"></span>
      </div>
    </main>
  `;

  const ID = '44444444-4444-4444-4444-444444444444';
  const ORIGEN = 'https://nexus.example/plataforma/salas-partidas/sala-batalla.html';

  let raiz;
  beforeEach(() => {
    document.body.innerHTML = HTML_INVITACION;
    raiz = document.getElementById('raiz');
  });

  const zona = () => raiz.querySelector('[data-zona="invitacion"]');
  const acuse = () => raiz.querySelector('[data-zona="acuse-copia"]').textContent;

  describe('invitacionDe', () => {
    test('arma el codigo y un enlace que lleva a la sala con el codigo puesto', () => {
      const invitacion = invitacionDe({ id: ID, codigoInvitacion: 'WXYZ-2345' }, ORIGEN);

      expect(invitacion.codigo).toBe('WXYZ-2345');
      expect(invitacion.enlace).toBe(
        `https://nexus.example/plataforma/salas-partidas/batallas.html?sala=${ID}&codigo=WXYZ-2345`,
      );
    });

    test('sin codigo no hay invitacion: la sala es publica o quien mira no es el anfitrion', () => {
      expect(invitacionDe({ id: ID }, ORIGEN)).toBeNull();
      expect(invitacionDe({ id: ID, codigoInvitacion: null }, ORIGEN)).toBeNull();
      expect(invitacionDe({ id: ID, codigoInvitacion: '  ' }, ORIGEN)).toBeNull();
    });

    test('sin sala tampoco, aunque venga el codigo', () => {
      expect(invitacionDe({ codigoInvitacion: 'WXYZ-2345' }, ORIGEN)).toBeNull();
    });
  });

  test('al anfitrion de una sala privada se le ensena el codigo', () => {
    const pintada = montarInvitacion(raiz, {
      sala: { id: ID, codigoInvitacion: 'WXYZ-2345' },
      origen: ORIGEN,
    });

    expect(pintada).toBe(true);
    expect(zona().hidden).toBe(false);
    expect(raiz.querySelector('[data-zona="codigo-invitacion"]').textContent).toBe('WXYZ-2345');
  });

  test('a quien no es anfitrion no le aparece la zona', () => {
    // No es que se le oculte un dato: es que el servidor no se lo manda.
    const pintada = montarInvitacion(raiz, { sala: { id: ID }, origen: ORIGEN });

    expect(pintada).toBe(false);
    expect(zona().hidden).toBe(true);
  });

  test('copiar el codigo lo manda al portapapeles y lo dice', async () => {
    const copiar = jest.fn().mockResolvedValue(undefined);
    montarInvitacion(raiz, {
      sala: { id: ID, codigoInvitacion: 'WXYZ-2345' },
      origen: ORIGEN,
      copiar,
    });

    raiz.querySelector('[data-accion="copiar-codigo"]').click();
    await Promise.resolve();
    await Promise.resolve();

    expect(copiar).toHaveBeenCalledWith('WXYZ-2345');
    expect(acuse()).toMatch(/copiado/i);
  });

  test('copiar el enlace manda el enlace entero, no solo el codigo', async () => {
    const copiar = jest.fn().mockResolvedValue(undefined);
    montarInvitacion(raiz, {
      sala: { id: ID, codigoInvitacion: 'WXYZ-2345' },
      origen: ORIGEN,
      copiar,
    });

    raiz.querySelector('[data-accion="copiar-enlace"]').click();
    await Promise.resolve();
    await Promise.resolve();

    expect(copiar.mock.calls[0][0]).toContain('batallas.html?sala=');
    expect(copiar.mock.calls[0][0]).toContain('codigo=WXYZ-2345');
  });

  test('sin portapapeles no se dice «copiado»: se dice que lo copie a mano', () => {
    montarInvitacion(raiz, {
      sala: { id: ID, codigoInvitacion: 'WXYZ-2345' },
      origen: ORIGEN,
      copiar: undefined,
    });

    raiz.querySelector('[data-accion="copiar-codigo"]').click();

    expect(acuse()).not.toMatch(/copiado/i);
    expect(acuse()).toMatch(/a mano/i);
    // Y el codigo sigue en pantalla para poder seleccionarlo.
    expect(raiz.querySelector('[data-zona="codigo-invitacion"]').textContent).toBe('WXYZ-2345');
  });

  test('un portapapeles que falla tampoco dice «copiado»', async () => {
    const copiar = jest.fn().mockRejectedValue(new Error('permiso denegado'));
    montarInvitacion(raiz, {
      sala: { id: ID, codigoInvitacion: 'WXYZ-2345' },
      origen: ORIGEN,
      copiar,
    });

    raiz.querySelector('[data-accion="copiar-codigo"]').click();
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();

    expect(acuse()).not.toMatch(/copiado/i);
    expect(acuse()).toMatch(/no se pudo copiar/i);
  });
});
