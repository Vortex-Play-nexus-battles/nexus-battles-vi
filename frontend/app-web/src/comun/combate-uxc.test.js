/**
 * UXC-2 — piezas del combate: poder, efectos, registro, impacto, acciones
 * especiales, canal que se reconecta, estado del canal y heroe propio.
 */
import { jest } from '@jest/globals';

import {
  accionDeCombate,
  chipDeEfecto,
  impactoEnCampo,
  medidorDePoder,
  registroDeCombate,
} from './ui/juego/combate.js';
import { canalReconectable, ESTADOS_DE_CANAL } from './canal-reconectable.js';
import { pintarEstadoDelCanal, RECUPERADA_VISIBLE_MS } from './ui/reconexion.js';
import { buscarElementoPropio, cargarHeroePropio, puntosDePoder } from './heroe-propio.js';

describe('medidorDePoder (PowerMeter)', () => {
  test('sin valor actual dice la capacidad y que no hay seguimiento', () => {
    const medidor = medidorDePoder({ maximo: 10 });
    expect(medidor.textContent).toContain('máx. 10');
    expect(medidor.textContent).toContain('Sin seguimiento');
    expect(medidor.getAttribute('role')).toBeNull();
    expect(medidor.querySelectorAll('.medidor-poder__segmento--lleno')).toHaveLength(0);
  });

  test('con valor actual es un medidor accesible', () => {
    const medidor = medidorDePoder({ maximo: 10, actual: 6 });
    expect(medidor.getAttribute('role')).toBe('meter');
    expect(medidor.getAttribute('aria-valuenow')).toBe('6');
    expect(medidor.querySelectorAll('.medidor-poder__segmento--lleno')).toHaveLength(6);
  });

  test('sin maximo no se pinta', () => {
    expect(medidorDePoder({ maximo: null })).toBeNull();
  });

  test('revisión 6-oct: enseña el movimiento que mandó el servidor (−2 al gastar, +2 al recuperar)', () => {
    const gasto = medidorDePoder({ maximo: 10, actual: 8, cambio: -2 });
    const cambio = gasto.querySelector('.medidor-poder__cambio');
    expect(gasto.querySelector('.medidor-poder__valor').textContent).toBe('8/10');
    expect(cambio.textContent).toBe('−2');
    expect(cambio.dataset.signo).toBe('negativo');
    // Lo dice el registro con palabras: para el lector, el medidor ya trae su valor.
    expect(cambio.getAttribute('aria-hidden')).toBe('true');

    const recupera = medidorDePoder({ maximo: 10, actual: 10, cambio: 2 });
    expect(recupera.querySelector('.medidor-poder__cambio').textContent).toBe('+2');
    expect(recupera.querySelector('.medidor-poder__cambio').dataset.signo).toBe('positivo');
  });

  test('sin movimiento (o sin valor actual) no hay cifra de cambio', () => {
    expect(
      medidorDePoder({ maximo: 10, actual: 10, cambio: 0 }).querySelector('.medidor-poder__cambio'),
    ).toBeNull();
    expect(
      medidorDePoder({ maximo: 10, cambio: -2 }).querySelector('.medidor-poder__cambio'),
    ).toBeNull();
  });
});

describe('chipDeEfecto (EffectChip)', () => {
  test('icono por codigo, nombre escrito y turnos', () => {
    const chip = chipDeEfecto({ codigo: 'VENENO', nombre: 'Veneno', turnosRestantes: 2 });
    expect(chip.querySelector('use').getAttribute('href')).toMatch(/#gota$/);
    expect(chip.textContent).toContain('Veneno');
    expect(chip.title).toBe('Veneno, 2 turnos restantes');
  });

  test('UXC-9 — sin icono del servidor, el icono sale del tipo del motor', () => {
    const chip = chipDeEfecto({
      codigo: 'CONO_DE_HIELO',
      nombre: 'Cono de hielo',
      icono: null,
      tipo: 'DANO_POR_TURNO',
      turnosRestantes: 1,
    });
    expect(chip.querySelector('use').getAttribute('href')).toMatch(/#gota$/);
    expect(
      chipDeEfecto({ codigo: 'DEFENSA_FEROZ', nombre: 'Defensa feroz', tipo: 'INMUNE_FISICO' })
        .querySelector('use')
        .getAttribute('href'),
    ).toMatch(/#escudo-check$/);
  });

  test('compacto: el nombre queda para el lector de pantalla', () => {
    const chip = chipDeEfecto({ codigo: 'X', nombre: 'Aturdido' }, { compacto: true });
    expect(chip.querySelector('.solo-lectores').textContent).toBe('Aturdido');
    expect(chip.querySelector('use').getAttribute('href')).toMatch(/#estrella$/);
  });
});

describe('registroDeCombate (CombatLog)', () => {
  test('es un log accesible que se puede alcanzar con el teclado', () => {
    const { elemento, anotar, lineas } = registroDeCombate({ maximo: 2 });
    expect(elemento.getAttribute('tabindex')).toBe('0');
    expect(elemento.getAttribute('role')).toBe('log');
    expect(elemento.querySelector('ol')).not.toBeNull();
    anotar({ texto: 'uno' });
    anotar({ texto: 'dos', tono: 'critico', icono: 'rayo' });
    anotar({ texto: 'tres' });
    anotar({ texto: '' });
    expect(lineas()).toBe(2);
    expect(elemento.textContent).toBe('dostres');
    expect(elemento.querySelector('[data-tono="critico"]')).not.toBeNull();
  });
});

describe('impactoEnCampo (DamageCallout)', () => {
  test('decorativo: el registro ya lo dice con palabras', () => {
    const impacto = impactoEnCampo({ cifra: '−9', etiqueta: 'Crítico', tono: 'critico' });
    expect(impacto.getAttribute('aria-hidden')).toBe('true');
    expect(impacto.className).toContain('impacto--critico');
    expect(impacto.textContent).toBe('−9Crítico');
  });
});

describe('accionDeCombate especial', () => {
  test('deshabilitada con su motivo, coste y carga en el nombre accesible', () => {
    const boton = accionDeCombate({
      nombre: 'Golpe con escudo',
      icono: 'rayo',
      coste: 2,
      insignias: [
        { icono: 'rayo', texto: '2', etiqueta: '2 de poder' },
        { icono: 'reloj', texto: '1', etiqueta: 'Un turno de carga' },
      ],
      efecto: 'Efecto: +2 al ataque',
      impedimento: 'Aún no',
      especial: true,
      describidaPor: 'motivo',
    });
    expect(boton.disabled).toBe(true);
    expect(boton.className).toContain('accion-combate--especial');
    expect(boton.getAttribute('aria-describedby')).toBe('motivo');
    expect(boton.getAttribute('aria-label')).toBe(
      'Golpe con escudo. cuesta 2 de poder. Un turno de carga. Efecto: +2 al ataque. Aún no',
    );
    expect(boton.querySelectorAll('.accion-combate__insignia')).toHaveLength(2);
    // No es un ataque: el E2E cuenta `[data-atacar]` para saber cuantos rivales hay.
    expect(boton.hasAttribute('data-atacar')).toBe(false);
  });
});

describe('puntosDePoder', () => {
  test('lee la cifra del texto del catalogo, o nada', () => {
    expect(puntosDePoder('2 puntos de poder')).toBe(2);
    expect(puntosDePoder('Todos los puntos de poder')).toBeNull();
    expect(puntosDePoder(null)).toBeNull();
  });
});

describe('cargarHeroePropio', () => {
  function respuestas(mapa) {
    return jest.fn(async (url) => {
      const clave = Object.keys(mapa).find((k) => url.includes(k));
      if (!clave) {
        return { ok: false, status: 404, json: async () => ({}) };
      }
      const cuerpo = mapa[clave];
      return cuerpo instanceof Error
        ? { ok: false, status: 503, json: async () => ({}) }
        : { ok: true, status: 200, json: async () => cuerpo };
    });
  }

  test('junta prototipo, acciones y poder desde los servicios', async () => {
    const fetchImpl = respuestas({
      'inventario/elementos?pagina=0': {
        elementos: [{ id: 'h1', productoId: 'p1', tipo: 'HEROE' }],
        totalPaginas: 1,
        ultima: true,
      },
      'productos/p1': { prototipo: 'Guerrero Tanque' },
      'heroes/Guerrero%20Tanque': {
        acciones: [{ nombre: 'Golpe con escudo', costo: '2 puntos de poder' }],
        estadisticasNivel1: { poder: 10 },
      },
      'heroes/h1/estadisticas': { poder: 12 },
    });
    const heroe = await cargarHeroePropio({ heroeId: 'h1', fetchImpl });
    expect(heroe).toEqual({
      prototipo: 'Guerrero Tanque',
      acciones: [{ nombre: 'Golpe con escudo', costo: '2 puntos de poder' }],
      poderMaximo: 12,
      motivo: null,
    });
  });

  test('si el catalogo de heroes no responde, lo dice', async () => {
    const fetchImpl = respuestas({
      'inventario/elementos?pagina=0': {
        elementos: [{ id: 'h1', productoId: 'p1' }],
        totalPaginas: 1,
      },
      'productos/p1': { prototipo: 'Mago Fuego' },
      'heroes/Mago%20Fuego': new Error('caido'),
      'heroes/h1/estadisticas': { poder: 8 },
    });
    const heroe = await cargarHeroePropio({ heroeId: 'h1', fetchImpl });
    expect(heroe.acciones).toEqual([]);
    expect(heroe.poderMaximo).toBe(8);
    expect(heroe.motivo).toMatch(/no respondió/);
  });

  test('sin heroe conocido no se piden servicios', async () => {
    const fetchImpl = jest.fn();
    const heroe = await cargarHeroePropio({ heroeId: null, fetchImpl });
    expect(heroe.motivo).toMatch(/No se sabe/);
    expect(fetchImpl).not.toHaveBeenCalled();
  });

  test('busca el elemento pagina a pagina y para al encontrarlo', async () => {
    const fetchImpl = respuestas({
      'pagina=0': { elementos: [{ id: 'x' }], totalPaginas: 3 },
      'pagina=1': { elementos: [{ id: 'h1', productoId: 'p' }], totalPaginas: 3 },
    });
    const elemento = await buscarElementoPropio('h1', { fetchImpl });
    expect(elemento.id).toBe('h1');
    expect(fetchImpl).toHaveBeenCalledTimes(2);
  });
});

describe('canalReconectable', () => {
  function clienteFalso() {
    return {
      alCerrar: null,
      alError: null,
      suscritos: [],
      enviados: [],
      suscribir(destino) {
        this.suscritos.push(destino);
        return 'sub';
      },
      enviar(destino, cuerpo) {
        this.enviados.push([destino, cuerpo]);
      },
      cerrar: jest.fn(),
    };
  }

  function relojManual() {
    const pendientes = [];
    return {
      setTimeout: (fn, ms) => {
        pendientes.push({ fn, ms });
        return pendientes.length;
      },
      clearTimeout: () => {},
      async avanzar() {
        const siguiente = pendientes.shift();
        await siguiente?.fn();
        return siguiente?.ms;
      },
    };
  }

  test('al caerse reintenta, rehace las suscripciones y avisa para reconciliar', async () => {
    const clientes = [clienteFalso(), clienteFalso()];
    const conectar = jest.fn(async () => clientes[conectar.mock.calls.length - 1]);
    const estados = [];
    const alReconectar = jest.fn();
    const reloj = relojManual();
    const canal = await canalReconectable({
      conectar,
      alEstado: (e) => estados.push(e.estado),
      alReconectar,
      reloj,
    });
    canal.suscribir('/tema/partidas/1', () => {});
    expect(clientes[0].suscritos).toEqual(['/tema/partidas/1']);

    clientes[0].alCerrar();
    expect(canal.conectado).toBe(false);
    expect(() => canal.enviar('/app/x', {})).toThrow(/Sin conexión/);
    expect(await reloj.avanzar()).toBe(1000);

    expect(clientes[1].suscritos).toEqual(['/tema/partidas/1']);
    expect(alReconectar).toHaveBeenCalledTimes(1);
    expect(estados).toEqual([
      ESTADOS_DE_CANAL.CONECTADO,
      ESTADOS_DE_CANAL.RECONECTANDO,
      ESTADOS_DE_CANAL.RECONECTADO,
    ]);
    canal.enviar('/app/x', { a: 1 });
    expect(clientes[1].enviados).toEqual([['/app/x', { a: 1 }]]);
  });

  test('agotados los intentos queda sin conexion, y reintentar vuelve a empezar', async () => {
    const primero = clienteFalso();
    let intentos = 0;
    const conectar = jest.fn(async () => {
      intentos += 1;
      if (intentos === 1) {
        return primero;
      }
      throw new Error('no abre');
    });
    const estados = [];
    const reloj = relojManual();
    const canal = await canalReconectable({
      conectar,
      alEstado: (e) => estados.push(e.estado),
      esperas: [10, 20],
      reloj,
    });
    primero.alCerrar();
    await reloj.avanzar();
    await reloj.avanzar();
    expect(estados.at(-1)).toBe(ESTADOS_DE_CANAL.SIN_CONEXION);

    canal.reintentar();
    expect(estados.at(-1)).toBe(ESTADOS_DE_CANAL.RECONECTANDO);
  });

  test('UXC-9 — con insistirAlAbrir, un primer intento fallido no deja la vista muda', async () => {
    const bueno = clienteFalso();
    let intentos = 0;
    const conectar = jest.fn(async () => {
      intentos += 1;
      if (intentos === 1) {
        throw new Error('no abre');
      }
      return bueno;
    });
    const estados = [];
    const alReconectar = jest.fn();
    const reloj = relojManual();
    const canal = await canalReconectable({
      conectar,
      alEstado: (e) => estados.push(e.estado),
      alReconectar,
      reloj,
      insistirAlAbrir: true,
      ventana: null,
    });
    // Sin canal todavía: se dice que se está reconectando y lo que se
    // suscriba espera a que abra.
    expect(canal.conectado).toBe(false);
    expect(estados).toEqual([ESTADOS_DE_CANAL.RECONECTANDO]);
    canal.suscribir('/tema/partidas/1', () => {});

    await reloj.avanzar();
    expect(canal.conectado).toBe(true);
    expect(bueno.suscritos).toEqual(['/tema/partidas/1']);
    // Al abrir, la vista relee lo que pudo perderse.
    expect(alReconectar).toHaveBeenCalledTimes(1);
    expect(estados.at(-1)).toBe(ESTADOS_DE_CANAL.RECONECTADO);
  });

  test('sin insistirAlAbrir, un primer intento fallido rechaza como antes', async () => {
    await expect(
      canalReconectable({
        conectar: async () => {
          throw new Error('no abre');
        },
        reloj: relojManual(),
        ventana: null,
      }),
    ).rejects.toThrow('no abre');
  });

  test('UXC-9 — al volver la red, sin conexión, vuelve a intentarlo solo', async () => {
    const primero = clienteFalso();
    const segundo = clienteFalso();
    let intentos = 0;
    const conectar = jest.fn(async () => {
      intentos += 1;
      if (intentos === 1) {
        return primero;
      }
      if (intentos <= 3) {
        throw new Error('no abre');
      }
      return segundo;
    });
    const estados = [];
    const reloj = relojManual();
    const ventana = new EventTarget();
    const canal = await canalReconectable({
      conectar,
      alEstado: (e) => estados.push(e.estado),
      esperas: [10, 20],
      reloj,
      ventana,
    });
    primero.alCerrar();
    await reloj.avanzar();
    await reloj.avanzar();
    expect(estados.at(-1)).toBe(ESTADOS_DE_CANAL.SIN_CONEXION);

    ventana.dispatchEvent(new Event('online'));
    expect(estados.at(-1)).toBe(ESTADOS_DE_CANAL.RECONECTANDO);
    await reloj.avanzar();
    expect(canal.conectado).toBe(true);

    // Cerrado a propósito, `online` ya no reabre nada.
    canal.cerrar();
    const antes = conectar.mock.calls.length;
    ventana.dispatchEvent(new Event('online'));
    await reloj.avanzar();
    expect(conectar.mock.calls.length).toBe(antes);
  });

  test('cerrado a proposito no se reconecta', async () => {
    const cliente = clienteFalso();
    const reloj = relojManual();
    const setTimeoutEspia = jest.spyOn(reloj, 'setTimeout');
    const canal = await canalReconectable({ conectar: async () => cliente, reloj });
    canal.cerrar();
    cliente.alCerrar?.();
    expect(setTimeoutEspia).not.toHaveBeenCalled();
    expect(cliente.cerrar).toHaveBeenCalled();
  });
});

describe('pintarEstadoDelCanal (ReconnectBanner)', () => {
  test('dice el estado con su variante del kit y ofrece reintentar al final', () => {
    const contenedor = document.createElement('div');
    const pildora = document.createElement('span');
    contenedor.append(pildora);
    const alReintentar = jest.fn();

    pintarEstadoDelCanal(pildora, { estado: 'reconectando', intento: 2, de: 5 });
    expect(pildora.className).toBe('conexion conexion--reconectando');
    expect(pildora.textContent).toBe('Canal en tiempo real: Reconectando… (intento 2 de 5)');
    expect(pildora.getAttribute('role')).toBe('status');

    pintarEstadoDelCanal(pildora, { estado: 'sin-conexion', alReintentar });
    const boton = contenedor.querySelector('[data-accion="reintentar-canal"]');
    expect(boton).not.toBeNull();
    boton.click();
    expect(alReintentar).toHaveBeenCalled();

    pintarEstadoDelCanal(pildora, { estado: 'reconectado' });
    expect(contenedor.querySelector('[data-accion="reintentar-canal"]')).toBeNull();
    expect(pildora.textContent).toMatch(/Conexión recuperada$/);
  });

  /*
   * Revisión del modo jugador del 6-oct (puntos 9, 13 y 18): «Canal en tiempo
   * real: Conectado» a la vista todo el rato era texto técnico. En discreto
   * solo se ve cuando hay algo que contar; el estado se sigue pintando.
   */
  test('discreto: conectado no se ve; reconectando y sin conexión sí; recuperada solo un momento', () => {
    jest.useFakeTimers();
    try {
      const pildora = document.createElement('span');
      document.body.append(pildora);

      pintarEstadoDelCanal(pildora, { estado: 'conectando', discreto: true });
      expect(pildora.hidden).toBe(true);

      pintarEstadoDelCanal(pildora, { estado: 'conectado', discreto: true });
      expect(pildora.hidden).toBe(true);
      expect(pildora.dataset.estadoCanal).toBe('conectado');
      expect(pildora.textContent).toMatch(/Conectado$/);

      pintarEstadoDelCanal(pildora, { estado: 'reconectando', intento: 1, de: 5, discreto: true });
      expect(pildora.hidden).toBe(false);

      pintarEstadoDelCanal(pildora, { estado: 'sin-conexion', discreto: true });
      expect(pildora.hidden).toBe(false);

      pintarEstadoDelCanal(pildora, { estado: 'reconectado', discreto: true });
      expect(pildora.hidden).toBe(false);
      jest.advanceTimersByTime(RECUPERADA_VISIBLE_MS);
      expect(pildora.hidden).toBe(true);
    } finally {
      jest.useRealTimers();
    }
  });

  test('sin discreto, la píldora no toca su visibilidad (chat y mensajes privados siguen igual)', () => {
    const pildora = document.createElement('span');
    pintarEstadoDelCanal(pildora, { estado: 'conectado' });
    expect(pildora.hidden).toBe(false);
  });
});
