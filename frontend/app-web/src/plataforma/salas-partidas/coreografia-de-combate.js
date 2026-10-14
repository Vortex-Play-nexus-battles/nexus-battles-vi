/**
 * Coreografía del combate — HU-UX-003, MOTION-2 (Battle Game Feel).
 *
 * Lo que se VE cuando llega un aviso del canal, construido con el Nexus
 * Motion System (`comun/ui/motion.js`) y la semántica de `comun/ui/juego/fx.js`:
 *
 *   anticipa (120 ms) → lunge (150 ms) → impacto (flash + recoil + cifra)
 *   → hit-stop (60 ms) → regreso (220 ms)
 *
 * Cabe entera (≈ 550 ms; ≈ 650 en un crítico) dentro de la pausa perceptual
 * que `ritmo-de-combate.js` ya deja entre acciones (2 200 ms), así que no se
 * pisa con el golpe siguiente y no toca el ritmo ni la lógica: el aviso que
 * ya llegaba es el que ahora se dibuja.
 *
 * ## Lo que este módulo promete
 *
 * - **Estado antes que movimiento.** Cada fase deja un atributo en el DOM
 *   (`data-accion` en quien actúa, `data-golpe` en quien recibe,
 *   `data-pensando` en la IA, `data-desenlace` en el campo). Las pruebas
 *   afirman estados y textos, nunca milisegundos; y con movimiento reducido
 *   los estados siguen ahí (el CSS los pinta quietos).
 * - **La cifra es texto con icono** (`impactoEnCampo`), nace EN el impacto y
 *   la retira `motion.float`. El texto lo pone `narracion.js` (la misma
 *   fuente que el registro); el icono, el diccionario FX.
 * - **Nada se sacude salvo lo golpeado**, y dos píxeles. El campo, el HUD y
 *   la barra de mando no se mueven.
 * - **Solo transform, opacity y filter**, sobre la figura del héroe
 *   (`.marco-heroe`); el puesto (`.campo__puesto`) es el ancla y no se mueve,
 *   así que la cifra y los efectos no viajan con el lunge.
 *
 * @module coreografia-de-combate
 */

import { animar, detener, motion, permitido, secuencia } from '../../comun/ui/motion.js';
import { FX, iconoDe, semanticaDe } from '../../comun/ui/juego/fx.js';
import { impactoEnCampo } from '../../comun/ui/juego/combate.js';

/** Tiempos de la coreografía (ms). Los nombres, los del sistema; los números, los del POC. */
export const TIEMPOS = Object.freeze({
  anticipa: 'fast', // 120
  lunge: 150,
  lungeCritico: 170,
  hitStop: 60,
  hitStopCritico: 90,
  regreso: 'normal', // 200
  cifra: 1600,
  lecturaDeLaCifra: 2400,
  estadoDelGolpe: 320,
  celebracion: 1600,
});

/** Píxeles del lunge: pocos, se sienten sin leerse como teletransporte. */
export const LUNGE_PX = 44;
export const LUNGE_CRITICO_PX = 56;

/** Partículas de la celebración: contenidas, una sola vez, solo en VICTORIA. */
export const PARTICULAS = 60;

/**
 * @param {{campo: HTMLElement|null, participantes: Array<object>, yo: string|null}} opciones
 */
export function coreografiaDeCombate({ campo, participantes = [], yo = null }) {
  const puestoDe = (id) => (id && campo ? campo.querySelector(`[data-puesto="${id}"]`) : null);
  const figuraDe = (puesto) => puesto?.querySelector('.marco-heroe') ?? puesto;
  const retratoDe = (puesto) => puesto?.querySelector('.marco-heroe__retrato') ?? figuraDe(puesto);
  /** Hacia dónde mira: el bando propio ataca hacia la derecha, el rival hacia la izquierda. */
  const direccionDe = (puesto) => (puesto?.dataset.lado === 'rival' ? -1 : 1);
  const esIA = (id) => Boolean(participantes.find((p) => p?.jugador?.id === id)?.esIA);

  /**
   * La cifra que flota sobre un héroe. Sustituye a la anterior del mismo
   * puesto (dos cifras encima de la misma figura son ruido).
   *
   * @param {HTMLElement} puesto
   * @param {{cifra: string, unidad?: string|null, etiqueta?: string|null, tono?: string, icono?: string|null}} rotulo
   * @returns {HTMLElement}
   */
  function cifra(puesto, rotulo) {
    puesto.querySelector('.impacto')?.remove();
    const nodo = impactoEnCampo(rotulo);
    puesto.append(nodo);
    motion.float(nodo, { duracion: TIEMPOS.cifra, lectura: TIEMPOS.lecturaDeLaCifra });
    return nodo;
  }

  /** Códigos de los efectos que ya se ven sobre un puesto, para saber cuáles son nuevos. */
  function efectosVisibles(puesto) {
    return new Set(
      [...(puesto?.querySelectorAll('[data-efecto]') ?? [])].map((chip) => chip.dataset.efecto),
    );
  }

  /**
   * Lo que recibe un afectado: estado, cifra y movimiento según el resultado.
   *
   * @param {{puesto: HTMLElement, resultado: string, efectos: Array<{codigo: string, fx: string|null}>}} objetivo
   * @param {{fx: string}} semantica
   * @param {Array<object>} impactos los de `narrarAccion` para este afectado
   * @param {Set<string>} yaVisibles efectos que ya tenía
   * @returns {Promise<void>}
   */
  function recibir(objetivo, semantica, impactos, yaVisibles) {
    const { puesto, resultado, efectos } = objetivo;
    const figura = figuraDe(puesto);
    const retroceso = -direccionDe(puesto);
    puesto.dataset.golpe = resultado;
    for (const impacto of impactos) {
      cifra(puesto, { ...impacto, icono: iconoDe(semantica.fx, resultado) });
    }

    const animaciones = [];
    switch (resultado) {
      case 'critico':
        animaciones.push(motion.critical(figura, { direccion: retroceso }));
        break;
      case 'golpe':
        animaciones.push(
          motion.damage(figura, {
            direccion: retroceso,
            fuerza: semantica.fx === FX.POISON ? 0.6 : 1,
          }),
        );
        break;
      case 'resistencia':
      case 'escape':
        // Lo mitigó: encaja (escudo) y apenas retrocede.
        animaciones.push(motion.shield(figura));
        animaciones.push(motion.damage(figura, { direccion: retroceso, fuerza: 0.4 }));
        break;
      case 'esquiva':
        // Un paso de lado y vuelve: el golpe pasó por donde estaba.
        animaciones.push(
          animar(
            figura,
            [
              { transform: 'translate3d(0, 0, 0)' },
              { transform: `translate3d(${retroceso * 10}px, -4px, 0)`, offset: 0.4 },
              { transform: 'translate3d(0, 0, 0)' },
            ],
            { duracion: 'slow', curva: 'standard', composicion: 'add' },
          ),
        );
        break;
      case 'curacion':
        animaciones.push(motion.heal(figura));
        break;
      default:
        // fallo o sin cambio: la cifra lo dice; la figura no reacciona.
        break;
    }
    // Un efecto NUEVO se ve llegar: mejora, escudo o perjuicio, uno solo.
    const nuevo = efectos.find((e) => e.fx && !yaVisibles.has(e.codigo));
    if (nuevo?.fx === FX.DEBUFF) {
      animaciones.push(motion.debuff(figura));
    } else if (nuevo?.fx === FX.SHIELD) {
      animaciones.push(motion.shield(figura));
    } else if (nuevo?.fx === FX.BUFF) {
      animaciones.push(motion.buff(figura));
    }

    const quitarEstado = () => {
      delete puesto.dataset.golpe;
    };
    if (!permitido() || animaciones.length === 0) {
      // Sin movimiento el estado se ve (y se prueba) un instante y se va.
      setTimeout(quitarEstado, TIEMPOS.estadoDelGolpe);
      return Promise.resolve();
    }
    return Promise.allSettled(animaciones.map((a) => a.finished)).then(quitarEstado);
  }

  /**
   * Un aviso `partida.accion.resuelta`, coreografiado.
   *
   * @param {object} aviso
   * @param {Array<{idJugador: string, cifra: string, unidad?: string|null, etiqueta?: string|null, tono?: string}>} [impactos]
   *   los de `narrarAccion`: el texto de cada cifra
   * @returns {Promise<void>} cuando el actor volvió a su sitio
   */
  async function accion(aviso, impactos = []) {
    const semantica = semanticaDe(aviso, participantes);
    const actor = puestoDe(semantica.ejecutor);
    const figura = figuraDe(actor);
    const objetivos = semantica.afectados
      .map((a) => ({ ...a, puesto: puestoDe(a.id) }))
      .filter((o) => o.puesto);
    const visibles = new Map(objetivos.map((o) => [o.id, efectosVisibles(o.puesto)]));
    const impactosDe = (id) => impactos.filter((i) => i.idJugador === id);
    const critico = objetivos.some((o) => o.resultado === 'critico');
    const seLanza = Boolean(actor) && semantica.ataca && !semantica.porTurno;
    const d = direccionDe(actor);
    const avance = critico ? LUNGE_CRITICO_PX : LUNGE_PX;

    // La IA ya decidió: deja de «pensar».
    if (actor) {
      delete actor.dataset.pensando;
      actor.dataset.fx = semantica.fx;
      if (semantica.epica) {
        actor.dataset.epica = 'si';
      }
      if (!semantica.porTurno) {
        let verbo = 'apoya';
        if (semantica.ataca) {
          verbo = 'ataca';
        } else if (semantica.fx === FX.HEAL) {
          verbo = 'cura';
        } else if (semantica.fx === FX.SHIELD) {
          verbo = 'defiende';
        }
        actor.dataset.accion = verbo;
      }
    }

    const reposo = 'translate3d(0, 0, 0) scale(1)';
    const atras = `translate3d(${-d * 8}px, 0, 0) scale(0.97)`;
    const delante = `translate3d(${d * avance}px, 0, 0) scale(1.02)`;
    /** El impacto: todos los afectados a la vez (una sanación de grupo, un veneno que corre). */
    const impactar = () =>
      objetivos.map((objetivo) =>
        recibir(objetivo, semantica, impactosDe(objetivo.id), visibles.get(objetivo.id)),
      );
    const limpiar = () => {
      if (figura) {
        // Sin `fill` colgando: la figura vuelve al CSS (el idle).
        detener(figura);
      }
      if (actor) {
        delete actor.dataset.accion;
        delete actor.dataset.fx;
        delete actor.dataset.epica;
      }
    };

    if (!seLanza || !permitido() || typeof figura?.animate !== 'function') {
      // Sin lunge (cura, defensa, apoyo, efecto por turno), sin movimiento o
      // sin Web Animations API (jsdom):
      // el impacto es AHORA, en la misma vuelta del bucle. Los estados y las
      // cifras quedan puestos antes de que nadie espere nada.
      const promesas = impactar();
      if (actor && !semantica.porTurno && !seLanza) {
        // Cura, defiende o apoya sin moverse del sitio: brilla o encaja.
        promesas.push(
          (semantica.fx === FX.SHIELD ? motion.shield(figura) : motion.heal(figura)).finished,
        );
      }
      try {
        await Promise.allSettled(promesas);
      } finally {
        limpiar();
      }
      return;
    }

    const pasos = [];
    if (semantica.epica) {
      // El halo de la épica, antes del golpe: el único momento con dos focos.
      pasos.push(() => motion.reward(retratoDe(actor)));
    }
    pasos.push(() =>
      animar(figura, [{ transform: reposo }, { transform: atras }], {
        duracion: TIEMPOS.anticipa,
        curva: 'exit',
        relleno: 'forwards',
      }),
    );
    pasos.push(() =>
      animar(figura, [{ transform: atras }, { transform: delante }], {
        duracion: critico ? TIEMPOS.lungeCritico : TIEMPOS.lunge,
        curva: 'impact',
        relleno: 'forwards',
      }),
    );
    pasos.push(() => Promise.allSettled(impactar()));
    pasos.push(critico ? TIEMPOS.hitStopCritico : TIEMPOS.hitStop);
    pasos.push(() =>
      animar(figura, [{ transform: delante }, { transform: reposo }], {
        duracion: TIEMPOS.regreso,
        curva: 'spring',
        relleno: 'forwards',
      }),
    );
    try {
      await secuencia(pasos);
    } finally {
      limpiar();
    }
  }

  /**
   * Cambió el turno: un pulso (uno) en quien juega y, si es la máquina, su
   * puesto «piensa» hasta que llega su acción.
   *
   * @param {string|null} idJugador
   */
  function turno(idJugador) {
    for (const puesto of campo?.querySelectorAll('[data-pensando]') ?? []) {
      delete puesto.dataset.pensando;
    }
    const puesto = puestoDe(idJugador);
    if (!puesto) {
      return;
    }
    motion.pulse(retratoDe(puesto), { escala: 1.08 });
    if (esIA(idJugador) && idJugador !== yo) {
      puesto.dataset.pensando = 'si';
    }
  }

  /**
   * Celebración contenida: partículas DOM animadas por WAAPI dentro del
   * campo, una vez, y fuera. Con movimiento reducido no hay partículas (el
   * panel ya dice VICTORIA con su icono y su texto).
   *
   * @param {HTMLElement} anfitrion
   * @returns {Promise<void>}
   */
  function celebrar(anfitrion) {
    if (!anfitrion || !permitido()) {
      return Promise.resolve();
    }
    const capa = document.createElement('div');
    capa.className = 'celebracion';
    capa.setAttribute('aria-hidden', 'true');
    anfitrion.append(capa);
    const promesas = [];
    for (let i = 0; i < PARTICULAS; i += 1) {
      const particula = document.createElement('span');
      particula.className = `celebracion__particula celebracion__particula--${i % 5}`;
      capa.append(particula);
      const angulo = -Math.PI / 2 + (Math.random() - 0.5) * 1.7;
      const fuerza = 200 + Math.random() * 280;
      const dx = Math.cos(angulo) * fuerza;
      const dy = Math.sin(angulo) * fuerza;
      const giro = (Math.random() - 0.5) * 900;
      const animacion = animar(
        particula,
        [
          { transform: 'translate3d(0, 0, 0) rotate(0deg)', opacity: 1 },
          {
            transform: `translate3d(${dx * 0.6}px, ${dy * 0.6}px, 0) rotate(${giro * 0.5}deg)`,
            opacity: 1,
            offset: 0.4,
          },
          { transform: `translate3d(${dx}px, ${dy + 360}px, 0) rotate(${giro}deg)`, opacity: 0 },
        ],
        {
          duracion: TIEMPOS.celebracion + Math.round(Math.random() * 400),
          curva: 'enter',
          relleno: 'forwards',
        },
      );
      promesas.push(animacion.finished);
    }
    return Promise.allSettled(promesas).then(() => capa.remove());
  }

  /**
   * El desenlace: VICTORIA se celebra (la palabra encaja y llueven
   * partículas, contenidas), DERROTA es sobria (el campo pierde color) y el
   * EMPATE, neutro. El estado queda en el campo para el CSS y las pruebas.
   *
   * @param {'victoria'|'derrota'|'empate'} tipo
   * @param {HTMLElement|null} [panel] el `.panel-resultado` ya pintado
   * @returns {Promise<void>}
   */
  function desenlace(tipo, panel = null) {
    if (campo) {
      campo.dataset.desenlace = tipo;
    }
    if (tipo !== 'victoria') {
      return Promise.resolve();
    }
    const palabra = panel?.querySelector('.panel-resultado__palabra');
    if (palabra) {
      motion.reward(palabra);
    }
    return celebrar(campo);
  }

  return { accion, turno, desenlace, cifra, celebrar };
}
