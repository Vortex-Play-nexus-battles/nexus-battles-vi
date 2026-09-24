/**
 * «Preparando tu cuenta» — R17.
 *
 * Se monta sobre el marcado real de `preparando.html` y con respuestas con la
 * forma exacta de `GET /api/v1/auth/onboarding` (contrato 1.1.0). El reloj es
 * manual: el tiempo solo pasa cuando la prueba lo dice.
 */

import { readFileSync } from 'node:fs';

import { jest } from '@jest/globals';

import { FalloDelAlta } from '../comun/alta.js';
import {
  PAUSA_LARGA_MS,
  TARDA_DEMASIADO_MS,
  cuandoReintenta,
  estadoDelPaso,
  faseDelAlta,
  montarPreparacion,
  pausaTras,
} from './preparando.js';

const MARCADO = readFileSync(new URL('./preparando.html', import.meta.url), 'utf8');
const DESTINO = 'http://localhost/frontend/app-web/src/cuentas/index.html';

function crearReloj(inicio = 1_700_000_000_000) {
  let ahora = inicio;
  let siguienteId = 1;
  const pendientes = new Map();
  return {
    ahora: () => ahora,
    setTimeout(funcion, ms) {
      const id = siguienteId++;
      pendientes.set(id, { funcion, cuando: ahora + ms });
      return id;
    },
    clearTimeout(id) {
      pendientes.delete(id);
    },
    /** Avanza y ejecuta lo que venza, esperando a que termine cada consulta. */
    async avanzar(ms) {
      ahora += ms;
      for (const [id, { funcion, cuando }] of [...pendientes]) {
        if (cuando <= ahora) {
          pendientes.delete(id);
          await funcion();
        }
      }
    },
    pendientes,
  };
}

const paso = (codigo, titulo, estado, motivo = null) => ({ paso: codigo, titulo, estado, motivo });

function alta(estado, estados, extra = {}) {
  const [perfil, creditos, heroe, equipo] = estados;
  return {
    estado,
    listo: estado === 'COMPLETO',
    version: 1,
    intentos: 1,
    siguienteIntento: null,
    creditosIniciales: null,
    heroeInicial: null,
    pasos: [
      paso('PERFIL', 'Perfil de jugador', perfil),
      paso('CREDITOS', 'Créditos de bienvenida', creditos),
      paso('HEROE', 'Héroe inicial', heroe),
      paso('EQUIPO', 'Equipo del héroe', equipo),
    ],
    ...extra,
  };
}

const zona = (nombre) => document.querySelector(`[data-zona="${nombre}"]`);
const pasosPintados = () =>
  [...document.querySelectorAll('[data-zona="pasos"] li')].map((li) => ({
    paso: li.dataset.paso,
    estado: li.dataset.estado,
    texto: li.querySelector('.alta-paso__estado').textContent,
  }));

/** Monta con una cola de respuestas; cada consulta toma la siguiente. */
function montar(respuestas, extra = {}) {
  const reloj = crearReloj();
  const cola = [...respuestas];
  const consultar = jest.fn(() => {
    const siguiente = cola.length > 1 ? cola.shift() : cola[0];
    return siguiente instanceof Error ? Promise.reject(siguiente) : Promise.resolve(siguiente);
  });
  const reemplazar = jest.fn();
  const alSinSesion = jest.fn();
  const pantalla = montarPreparacion(document, {
    destino: DESTINO,
    consultar,
    reintentar: extra.reintentar ?? jest.fn(() => Promise.resolve(cola[0])),
    reloj,
    ahora: reloj.ahora,
    reemplazar,
    alSinSesion,
    documento: document,
  });
  return { reloj, consultar, reemplazar, alSinSesion, pantalla };
}

beforeEach(() => {
  document.documentElement.innerHTML = MARCADO.replace(/<script[\s\S]*?<\/script>/g, '');
});

describe('pasos del alta', () => {
  test('pinta cada paso con lo que dice el servidor, y el progreso real', async () => {
    const { pantalla } = montar([alta('EN_PROCESO', ['HECHO', 'HECHO', 'PENDIENTE', 'PENDIENTE'])]);
    await pantalla.primeraConsulta;

    expect(pasosPintados()).toEqual([
      { paso: 'PERFIL', estado: 'HECHO', texto: 'Listo' },
      { paso: 'CREDITOS', estado: 'HECHO', texto: 'Listo' },
      { paso: 'HEROE', estado: 'PENDIENTE', texto: 'En curso…' },
      { paso: 'EQUIPO', estado: 'PENDIENTE', texto: 'Pendiente' },
    ]);
    expect(zona('valor-progreso').textContent).toBe('2 de 4');
    expect(zona('riel-progreso').getAttribute('aria-valuenow')).toBe('2');
    expect(zona('relleno-progreso').style.width).toBe('50%');
    expect(zona('resumen').textContent).toBe('Preparando tu cuenta: 2 de 4 pasos listos.');
    // Solo un paso «en curso» a la vez, y marcado como tal para el kit.
    expect(document.querySelectorAll('.paso--actual')).toHaveLength(1);
    expect(document.querySelectorAll('.paso--completado')).toHaveLength(2);
  });

  test('sigue consultando hasta que termina, y entonces enseña lo que recibió', async () => {
    const { reloj, consultar, pantalla } = montar([
      alta('EN_PROCESO', ['HECHO', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE']),
      alta('EN_PROCESO', ['HECHO', 'HECHO', 'HECHO', 'PENDIENTE']),
      alta('COMPLETO', ['HECHO', 'HECHO', 'HECHO', 'HECHO'], {
        creditosIniciales: 500,
        heroeInicial: 'elemento-heroe-1',
      }),
    ]);
    await pantalla.primeraConsulta;
    expect(zona('listo').hidden).toBe(true);

    await reloj.avanzar(pausaTras(1));
    await reloj.avanzar(pausaTras(2));

    expect(consultar).toHaveBeenCalledTimes(3);
    expect(zona('listo').hidden).toBe(false);
    expect(zona('creditos-iniciales').textContent).toBe('500 créditos');
    expect(zona('heroe-inicial').textContent).toBe('Equipado y listo para combatir');
    expect(zona('empezar').hidden).toBe(false);
    expect(zona('empezar').getAttribute('href')).toBe(DESTINO);
    expect(zona('resumen').textContent).toBe('Todo listo: ya puedes jugar.');
    expect(document.activeElement.id).toBe('tituloListo');
    // Terminado: ya no se consulta más.
    expect(reloj.pendientes.size).toBe(0);
  });

  test('los créditos del resumen son los del servidor: si no los dijo, no se inventan', async () => {
    const { pantalla } = montar([alta('COMPLETO', ['HECHO', 'HECHO', 'HECHO', 'HECHO'])]);
    await pantalla.primeraConsulta;
    expect(zona('listo').hidden).toBe(false);
    expect(zona('creditos-iniciales')).toBeNull();
    expect(zona('heroe-inicial')).toBeNull();
  });

  test('una cuenta sin alta (anterior, o de administración) sigue de largo', async () => {
    const { reemplazar, pantalla } = montar([
      { estado: 'NO_APLICA', listo: true, pasos: [], intentos: 0 },
    ]);
    await pantalla.primeraConsulta;
    expect(reemplazar).toHaveBeenCalledWith(DESTINO);
  });
});

describe('cuando algo falla', () => {
  test('un paso con error dice el motivo, cuándo se reintenta, y deja reintentar o entrar', async () => {
    const reintentar = jest.fn(() =>
      Promise.resolve(alta('EN_PROCESO', ['HECHO', 'HECHO', 'PENDIENTE', 'PENDIENTE'])),
    );
    const conError = alta('ERROR_REINTENTABLE', ['HECHO', 'HECHO', 'ERROR', 'ERROR'], {
      siguienteIntento: new Date(1_700_000_000_000 + 30_000).toISOString(),
    });
    conError.pasos[2].motivo =
      'El servicio no respondió a tiempo. Lo reintentamos automáticamente.';
    conError.pasos[3].motivo = 'Espera a que el héroe inicial esté listo.';
    const { pantalla, reloj } = montar([conError], { reintentar });
    await pantalla.primeraConsulta;

    const fallidos = document.querySelectorAll('.alta-paso--error');
    expect(fallidos).toHaveLength(2);
    expect(fallidos[0].querySelector('.alta-paso__motivo').textContent).toContain(
      'no respondió a tiempo',
    );
    expect(zona('aviso').hidden).toBe(false);
    expect(zona('aviso-detalle').textContent).toContain('en 30 segundos');
    expect(zona('reintentar').hidden).toBe(false);
    expect(zona('continuar').hidden).toBe(false);
    expect(zona('continuar').getAttribute('href')).toBe(DESTINO);
    // Mientras espera su reintento, se consulta sin prisa.
    expect([...reloj.pendientes.values()][0].cuando - reloj.ahora()).toBe(PAUSA_LARGA_MS);

    zona('reintentar').click();
    await new Promise((resolver) => setTimeout(resolver, 0));

    expect(reintentar).toHaveBeenCalledTimes(1);
    expect(zona('reintentar').hidden).toBe(true);
    expect(pasosPintados()[2]).toEqual({ paso: 'HEROE', estado: 'PENDIENTE', texto: 'En curso…' });
  });

  test('si la sesión ya no vale, sale sin quedarse consultando', async () => {
    const { alSinSesion, reloj, pantalla } = montar([new FalloDelAlta('sin-sesion', 403)]);
    await pantalla.primeraConsulta;
    expect(alSinSesion).toHaveBeenCalledTimes(1);
    expect(reloj.pendientes.size).toBe(0);
  });

  test('si el servicio no contesta, lo dice, lo reintenta y a la segunda deja entrar', async () => {
    const caido = new FalloDelAlta('no-disponible', 502);
    const { reloj, consultar, pantalla, alSinSesion } = montar([
      caido,
      caido,
      alta('COMPLETO', ['HECHO', 'HECHO', 'HECHO', 'HECHO']),
    ]);
    await pantalla.primeraConsulta;
    expect(zona('aviso').hidden).toBe(false);
    expect(zona('aviso-titulo').textContent).toBe('No pudimos consultar tu cuenta');
    expect(zona('continuar').hidden).toBe(true);

    await reloj.avanzar(PAUSA_LARGA_MS);
    expect(zona('continuar').hidden).toBe(false);

    await reloj.avanzar(PAUSA_LARGA_MS);
    expect(consultar).toHaveBeenCalledTimes(3);
    expect(zona('listo').hidden).toBe(false);
    expect(zona('aviso').hidden).toBe(true);
    // Una caída no es una sesión caducada.
    expect(alSinSesion).not.toHaveBeenCalled();
  });

  test('si tarda más de lo normal, lo dice y ofrece entrar sin esperar', async () => {
    const { reloj, pantalla } = montar([
      alta('EN_PROCESO', ['HECHO', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE']),
    ]);
    await pantalla.primeraConsulta;
    expect(zona('continuar').hidden).toBe(true);

    let transcurrido = 0;
    while (transcurrido < TARDA_DEMASIADO_MS) {
      await reloj.avanzar(5000);
      transcurrido += 5000;
    }

    expect(zona('aviso-titulo').textContent).toBe('Está tardando más de lo normal');
    expect(zona('continuar').hidden).toBe(false);
  });
});

describe('piezas', () => {
  test('pausaTras: rápido al principio, luego espaciado', () => {
    expect(pausaTras(0)).toBe(1000);
    expect(pausaTras(1)).toBe(1500);
    expect(pausaTras(99)).toBe(5000);
    expect(pausaTras(-3)).toBe(1000);
  });

  test('cuandoReintenta lo dice con palabras', () => {
    const ahora = Date.parse('2026-09-24T12:00:00Z');
    expect(cuandoReintenta('2026-09-24T12:00:03Z', ahora)).toBe('en unos segundos');
    expect(cuandoReintenta('2026-09-24T12:00:40Z', ahora)).toBe('en 40 segundos');
    expect(cuandoReintenta('2026-09-24T12:01:10Z', ahora)).toBe('en un minuto');
    expect(cuandoReintenta('2026-09-24T12:10:00Z', ahora)).toBe('en 10 minutos');
    expect(cuandoReintenta(null, ahora)).toBe('en breve');
  });

  test('estadoDelPaso no depende del color', () => {
    expect(estadoDelPaso({ estado: 'HECHO' }, false)).toBe('Listo');
    expect(estadoDelPaso({ estado: 'ERROR' }, false)).toBe('Todavía no se pudo completar');
    expect(estadoDelPaso({ estado: 'PENDIENTE' }, true)).toBe('En curso…');
    expect(estadoDelPaso({ estado: 'PENDIENTE' }, false)).toBe('Pendiente');
  });

  test('faseDelAlta', () => {
    expect(faseDelAlta(alta('COMPLETO', ['HECHO', 'HECHO', 'HECHO', 'HECHO']))).toEqual({
      fase: 'listo',
      hechos: 4,
      total: 4,
    });
    expect(faseDelAlta({ estado: 'NO_APLICA', pasos: [] }).fase).toBe('sin-alta');
    expect(
      faseDelAlta(alta('ERROR_REINTENTABLE', ['HECHO', 'ERROR', 'PENDIENTE', 'PENDIENTE'])).fase,
    ).toBe('error');
    expect(faseDelAlta(null)).toEqual({ fase: 'preparando', hechos: 0, total: 0 });
  });
});
