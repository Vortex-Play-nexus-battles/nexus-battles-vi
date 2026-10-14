/**
 * Diccionario FX — Battle Motion Language (HU-UX-003, MOTION-2).
 *
 * Traduce un `partida.accion.resuelta` (contracts/websocket/salas-partidas.yaml)
 * a SEMÁNTICA visual: qué clase de golpe fue, qué le pasó a cada afectado y
 * qué debe ver el jugador. No sabe de píxeles ni de milisegundos —eso es de
 * `coreografia-de-combate.js`— y no calcula nada: la categoría del golpe, el
 * tipo de acción, si fue épica y los efectos aplicados vienen en el aviso;
 * el prototipo del ejecutor, en los participantes (1.7.0).
 *
 * ## Las nueve claves
 *
 * PHYSICAL_HIT · MAGIC_FIRE · MAGIC_ICE · POISON · HEAL · SHIELD · BUFF ·
 * DEBUFF · EPIC. El FX base lo decide el TIPO de acción y, para un ataque,
 * la familia del héroe (Tabla 6, `prototipos.js`); EPIC se suma al base.
 * Regla: 1 acción importante → 1 foco visual dominante.
 *
 * ## Lo que le pasa al afectado
 *
 * `resultado` sale de la categoría que sorteó el motor (`CATEGORIAS` de
 * `narracion.js`): golpe, critico, esquiva, resistencia, escape, fallo; o de
 * la diferencia de vida: curacion, sinCambio. Y `caido` si quedó a cero.
 */

import { identidadDePrototipo } from './prototipos.js';

export const FX = Object.freeze({
  PHYSICAL_HIT: 'PHYSICAL_HIT',
  MAGIC_FIRE: 'MAGIC_FIRE',
  MAGIC_ICE: 'MAGIC_ICE',
  POISON: 'POISON',
  HEAL: 'HEAL',
  SHIELD: 'SHIELD',
  BUFF: 'BUFF',
  DEBUFF: 'DEBUFF',
  EPIC: 'EPIC',
});

/** FX de ATAQUE por prototipo (clave normalizada de `prototipos.js`). */
const ATAQUE_POR_PROTOTIPO = Object.freeze({
  'guerrero tanque': FX.PHYSICAL_HIT,
  'guerrero armas': FX.PHYSICAL_HIT,
  'mago fuego': FX.MAGIC_FIRE,
  'mago hielo': FX.MAGIC_ICE,
  'picaro veneno': FX.POISON,
  'picaro machete': FX.PHYSICAL_HIT,
  chaman: FX.HEAL,
  medico: FX.HEAL,
});

/** Categorías del motor → resultado sobre el afectado. */
const RESULTADO_POR_CATEGORIA = Object.freeze({
  CAUSAR_DANO: 'golpe',
  CAUSAR_DANO_CRITICO: 'critico',
  EVADIR_EL_GOLPE: 'esquiva',
  RESISTIR_EL_GOLPE: 'resistencia',
  ESCAPAR_AL_GOLPE: 'escape',
  SIN_EFECTO: 'fallo',
});

/** Tipos de efecto (motor-combate.yaml `TipoDeEfecto`) que mejoran al portador. */
const EFECTOS_QUE_MEJORAN = new Set([
  'BONO_ATAQUE',
  'BONO_DANO',
  'BONO_SANACION',
  'BONO_CRITICO',
  'BONO_DEFENSA',
  'INMUNE_FISICO',
  'REDUCE_MAGICO',
  'INMUNE_TOTAL',
  'REFLEJA_MITAD',
  'SANACION_POR_TURNO',
  'VINCULO_REANIMACION',
]);

/** Tipos de efecto que perjudican al portador. */
const EFECTOS_QUE_PERJUDICAN = new Set(['PENALIZA_ATAQUE', 'PENALIZA_DANO', 'DANO_POR_TURNO']);

/** Efectos de protección: lo que se ve es un escudo, no una mejora cualquiera. */
const EFECTOS_DE_ESCUDO = new Set([
  'BONO_DEFENSA',
  'INMUNE_FISICO',
  'REDUCE_MAGICO',
  'INMUNE_TOTAL',
  'REFLEJA_MITAD',
]);

const EFECTO_POR_TURNO = 'EFECTO_POR_TURNO';

/**
 * El prototipo del ejecutor, por su participante.
 *
 * @param {string|null|undefined} idEjecutor
 * @param {Array<object>} participantes
 * @returns {string} clave normalizada, o '' si no se sabe
 */
function prototipoDe(idEjecutor, participantes) {
  const participante = (participantes ?? []).find((p) => p?.jugador?.id === idEjecutor);
  const identidad = identidadDePrototipo(participante?.heroe?.prototipo ?? null);
  return identidad.conocido ? identidad.clave : '';
}

/**
 * El FX de un efecto aplicado, por su tipo.
 *
 * @param {{tipo?: string|null}|null|undefined} efecto
 * @returns {'SHIELD'|'BUFF'|'DEBUFF'|null}
 */
export function fxDeEfecto(efecto) {
  const tipo = String(efecto?.tipo ?? '').toUpperCase();
  if (EFECTOS_DE_ESCUDO.has(tipo)) {
    return FX.SHIELD;
  }
  if (EFECTOS_QUE_MEJORAN.has(tipo)) {
    return FX.BUFF;
  }
  if (EFECTOS_QUE_PERJUDICAN.has(tipo)) {
    return FX.DEBUFF;
  }
  return null;
}

/**
 * La semántica de un aviso de acción resuelta.
 *
 * @param {object} aviso `partida.accion.resuelta`
 * @param {Array<object>} participantes esquema `Participante` del contrato
 * @returns {{
 *   fx: string, epica: boolean, ataca: boolean, porTurno: boolean,
 *   ejecutor: string|null, objetivo: string|null,
 *   afectados: Array<{id: string, diferencia: number|null, resultado: string,
 *     caido: boolean, efectos: Array<{codigo: string, fx: string|null}>}>
 * }}
 */
export function semanticaDe(aviso, participantes = []) {
  const accion = aviso?.accion ?? {};
  const tipo = String(accion.tipo ?? '').toUpperCase();
  const porTurno = accion.codigo === EFECTO_POR_TURNO || tipo === 'EFECTO';
  const epica = accion.esEpica === true;
  const categoria = RESULTADO_POR_CATEGORIA[String(accion.nombre ?? '').toUpperCase()] ?? null;
  const ejecutor = aviso?.idEjecutor ?? null;

  const todos = Array.isArray(aviso?.afectados) ? aviso.afectados : [];
  // El ejecutor viaja en sus propios avisos para llevar su poder y sus cargas
  // (1.5.0); no es un afectado salvo que su vida cambie.
  const afectados = todos
    .filter((a) => a?.idJugador && (a.idJugador !== ejecutor || a.diferencia))
    .map((a) => {
      const diferencia = Number.isFinite(a.diferencia) ? a.diferencia : null;
      let resultado;
      if (diferencia !== null && diferencia > 0) {
        resultado = 'curacion';
      } else if (categoria && (diferencia === null || diferencia <= 0)) {
        resultado = diferencia === 0 && categoria !== 'fallo' ? 'sinCambio' : categoria;
      } else if (diferencia !== null && diferencia < 0) {
        resultado = 'golpe';
      } else {
        resultado = 'sinCambio';
      }
      return {
        id: a.idJugador,
        diferencia,
        resultado,
        caido: a.vidaActual === 0,
        efectos: (Array.isArray(a.efectosActivos) ? a.efectosActivos : [])
          .filter((e) => e?.codigo)
          .map((e) => ({ codigo: e.codigo, fx: fxDeEfecto(e) })),
      };
    });

  // El FX base: por el tipo de acción; un ataque, además, por la familia.
  let fx;
  let ataca = false;
  if (porTurno) {
    const quita = afectados.some((a) => a.diferencia !== null && a.diferencia < 0);
    fx = quita ? FX.POISON : FX.HEAL;
  } else if (tipo === 'DEFENSA') {
    fx = FX.SHIELD;
  } else if (tipo === 'SANACION' || tipo === 'SANACION_GRUPAL' || tipo === 'REANIMACION') {
    fx = FX.HEAL;
  } else if (tipo === 'APOYO') {
    const sobreRival =
      aviso?.idObjetivo && aviso.idObjetivo !== ejecutor && !mismoEquipo(aviso, participantes);
    fx = sobreRival ? FX.DEBUFF : FX.BUFF;
  } else if (afectados.length > 0 && afectados.every((a) => a.resultado === 'curacion')) {
    // Sin `tipo` (una partida anterior a 1.7.0): la vida subió, fue sanación.
    fx = FX.HEAL;
  } else {
    ataca = true;
    fx = ATAQUE_POR_PROTOTIPO[prototipoDe(ejecutor, participantes)] ?? FX.PHYSICAL_HIT;
    if (fx === FX.HEAL) {
      // Un sanador no ataca (§6.1.1); si el motor dijera lo contrario, se
      // pinta como golpe físico y no como una curación que no fue.
      fx = FX.PHYSICAL_HIT;
    }
  }

  return {
    fx,
    epica,
    ataca,
    porTurno,
    ejecutor,
    objetivo: aviso?.idObjetivo ?? afectados[0]?.id ?? null,
    afectados,
  };
}

/** Si ejecutor y objetivo están en el mismo equipo (HU-SAL-004). */
function mismoEquipo(aviso, participantes) {
  const equipoDe = (id) => (participantes ?? []).find((p) => p?.jugador?.id === id)?.equipo ?? null;
  const propio = equipoDe(aviso?.idEjecutor);
  return propio !== null && propio === equipoDe(aviso?.idObjetivo);
}

/**
 * El icono que acompaña a la cifra flotante: nunca solo color (RNF-USA-002,
 * XAG 102). El TEXTO de la cifra lo pone `narracion.js` (`impactos`), que es
 * la misma fuente que el registro: aquí no se inventa otro rótulo.
 *
 * @param {string} fx una clave de `FX`
 * @param {string} resultado el `resultado` de un afectado
 * @returns {string} nombre de icono del sprite (`icono.js`)
 */
export function iconoDe(fx, resultado) {
  switch (resultado) {
    case 'curacion':
      return 'cruz';
    case 'critico':
      return 'rayo';
    case 'esquiva':
    case 'resistencia':
    case 'escape':
      return 'escudo';
    case 'fallo':
      return 'cerrar';
    default:
      return (
        { MAGIC_FIRE: 'fuego', MAGIC_ICE: 'copo', POISON: 'gota', HEAL: 'cruz', SHIELD: 'escudo' }[
          fx
        ] ?? 'espada'
      );
  }
}
