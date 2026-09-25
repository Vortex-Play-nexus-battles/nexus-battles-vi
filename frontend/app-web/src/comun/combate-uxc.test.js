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
import { pintarEstadoDelCanal } from './ui/reconexion.js';
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
});

describe('chipDeEfecto (EffectChip)', () => {
  test('icono por codigo, nombre escrito y turnos', () => {
    const chip = chipDeEfecto({ codigo: 'VENENO', nombre: 'Veneno', turnosRestantes: 2 });
    expect(chip.querySelector('use').getAttribute('href')).toMatch(/#gota$/);
    expect(chip.textContent).toContain('Veneno');
    expect(chip.title).toBe('Veneno, 2 turnos restantes');
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
});
