/**
 * Presentacion de los heroes al empezar la partida — HU-JUE-017 CA-04.
 *
 * ## Por que existe, y por que no existia
 *
 * La ficha de RF-JUE-017 pide «vistas de alto impacto al inicio y al final de
 * la partida». El final estaba hecho desde UX-R2.3 (`panelDeResultado`, con
 * el desenlace y el reparto de creditos). **El inicio no.**
 *
 * Durante UX-R2.3 se dejo pendiente con este motivo: hacia falta una senal
 * del servidor que dijera «ya, ahora, empieza», y ponerla con un temporizador
 * del cliente habria sido inventarse el momento.
 *
 * Al revisarlo en UX-R2.10 resulta que **la senal ya existe y esta probada**:
 * `sala.partida.iniciada` del contrato `salas-partidas.yaml` llega justo
 * cuando arranca el combate y trae **todo** lo que hace falta:
 *
 *   - `participantes[]` con `jugador`, `heroe`, `esIA` y `equipo`;
 *   - `ordenDeTurnos` y `turnoActual`, o sea quien abre.
 *
 * El E2E juega una partida de verdad y ese mensaje llega de verdad. Asi que
 * el criterio se puede cumplir sin inventar nada: la presentacion se dispara
 * con ese evento y se cierra con el primero que indique que el combate ya
 * esta corriendo, o cuando la persona lo cierra.
 *
 * ## Lo que NO hace
 *
 * - **No espera un tiempo fijo.** No hay `setTimeout` que decida cuanto dura:
 *   se cierra con una accion (pulsar, Escape) o con el evento siguiente. Si
 *   alguien tarda en leer, la presentacion espera.
 * - **No bloquea el combate.** El campo y las barras se montan igual detras;
 *   esto es una capa encima. Si el jugador la cierra en medio segundo, no se
 *   ha perdido ningun turno.
 * - **No inventa datos.** Nombre del heroe, bando y quien abre salen del
 *   mensaje. Si el mensaje no trae heroes —la verificacion de heroe no es
 *   obligatoria al entrar a la sala—, no se presenta nada.
 */

import { h, vaciar } from '../dom.js';
import { retratoDeHeroe } from './heroe.js';

/** Segundos de la cuenta atrás del comienzo (revisión del modo jugador del 6-oct, punto 17). */
export const SEGUNDOS_DE_CUENTA_ATRAS = 5;

/** Cuánto se ve «¡COMBATE!» al final de la cuenta, antes de entrar al campo. */
export const PAUSA_FINAL_MS = 800;

/** Lo que se lee al llegar a cero. */
export const GRITO_DE_COMBATE = '¡COMBATE!';

/**
 * Cuántos segundos de cuenta atrás quedan para una partida que empezó en
 * `iniciadaEn`, contados con el reloj de quien mira. Sincroniza a dos
 * jugadores que reciben el comienzo con un poco de diferencia; un reloj
 * desfasado no alarga la cuenta más allá de su duración, ni la vuelve
 * negativa. Sin hora de inicio, la cuenta entera.
 *
 * @param {string|null|undefined} iniciadaEn ISO-8601 del servidor
 * @param {number} [ahora] milisegundos
 * @param {number} [duracion] segundos
 * @returns {number} segundos enteros, de 0 a `duracion`
 */
export function segundosDeCuentaAtras(
  iniciadaEn,
  ahora = Date.now(),
  duracion = SEGUNDOS_DE_CUENTA_ATRAS,
) {
  const inicio = Date.parse(iniciadaEn ?? '');
  if (!Number.isFinite(inicio)) {
    return duracion;
  }
  const quedan = duracion - (ahora - inicio) / 1000;
  return Math.max(0, Math.min(duracion, Math.ceil(quedan)));
}

/**
 * Quien abrio el combate.
 *
 * `turnoActual` es el turno EN CURSO, no el primero: quien pulsa «Iniciar
 * combate» recibe la partida ya avanzada si abrio la maquina, que juega en el
 * mismo instante. Auditoria de DEV del 30-sep: la presentacion decia «Abres
 * tu» y el registro empezaba en «Turno 2» con la vida ya mermada. Pasado el
 * turno 1, quien abrio es el primero de `participantes`, que van en el orden
 * de los turnos (salas-partidas.yaml 1.7.0).
 *
 * @param {Array<object>} participantes
 * @param {{idJugador: string, numeroTurno?: number}|null} turnoActual
 * @returns {string|null}
 */
export function quienAbrio(participantes, turnoActual) {
  if (!turnoActual?.idJugador) {
    return null;
  }
  const numero = turnoActual.numeroTurno;
  if (Number.isInteger(numero) && numero > 1) {
    return participantes[0]?.jugador?.id ?? null;
  }
  return turnoActual.idJugador;
}

/**
 * La frase de quien abre, o de quien abrio y a quien le toca ahora.
 *
 * @returns {string}
 */
function textoDeApertura(conHeroe, turnoActual, yo) {
  const nombre = (id) => conHeroe.find((p) => p.jugador?.id === id)?.heroe?.nombre ?? null;
  const abrio = quienAbrio(conHeroe, turnoActual);
  const ahora = turnoActual.idJugador;
  if (!abrio || abrio === ahora) {
    return ahora === yo ? 'Abres tú' : `Abre ${nombre(ahora) ?? 'el otro bando'}`;
  }
  const quienAbrioTexto = abrio === yo ? 'Abriste tú' : `Abrió ${nombre(abrio) ?? 'el otro bando'}`;
  const quienSigue =
    ahora === yo ? 'ahora te toca a ti' : `ahora juega ${nombre(ahora) ?? 'el otro bando'}`;
  return `${quienAbrioTexto}; ${quienSigue}`;
}

/**
 * Construye la presentacion de los heroes de una partida.
 *
 * @param {{participantes: Array<object>, turnoActual?: {idJugador: string, numeroTurno?: number}|null,
 *          yo?: string|null, alCerrar?: Function}} opciones
 * @returns {HTMLElement|null} null si no hay nada que presentar.
 */
export function presentacionDeHeroes({
  participantes = [],
  turnoActual = null,
  yo = null,
  alCerrar = () => {},
  cuentaAtras = 0,
  programar = (fn, ms) => setTimeout(fn, ms),
} = {}) {
  const conHeroe = participantes.filter((p) => p?.heroe?.nombre);
  if (conHeroe.length === 0) {
    // Sin heroes no hay presentacion. Mejor entrar directo al campo que
    // ensenar una pantalla de impacto con huecos.
    return null;
  }

  const capa = h('div', {
    clase: 'presentacion',
    datos: { zona: 'presentacion-heroes' },
    atributos: {
      role: 'dialog',
      'aria-modal': 'false',
      'aria-label': 'Los héroes de esta partida',
    },
  });

  capa.append(h('p', { clase: 'presentacion__rotulo', texto: 'La batalla empieza' }));

  // Por bando. Si la modalidad no usa equipos, `equipo` viene vacío y todos
  // caen en el mismo grupo: la presentación sigue siendo una fila de héroes.
  const bandos = new Map();
  for (const p of conHeroe) {
    const clave = Number.isInteger(p.equipo) ? p.equipo : 0;
    if (!bandos.has(clave)) {
      bandos.set(clave, []);
    }
    bandos.get(clave).push(p);
  }

  const campo = h('div', { clase: 'presentacion__bandos' });
  const abridor = quienAbrio(conHeroe, turnoActual);
  const claves = [...bandos.keys()].sort((a, b) => a - b);
  claves.forEach((clave, indice) => {
    if (indice > 0) {
      capa.classList.add('presentacion--con-versus');
      campo.append(h('span', { clase: 'presentacion__versus', texto: 'VS' }));
    }
    const bando = h('div', { clase: 'presentacion__bando' });
    for (const p of bandos.get(clave)) {
      bando.append(fichaDeHeroe(p, { yo, abre: abridor !== null && abridor === p.jugador?.id }));
    }
    campo.append(bando);
  });
  capa.append(campo);

  if (turnoActual?.idJugador) {
    capa.append(
      h('p', {
        clase: 'presentacion__turno',
        // `aria-live`: quien no ve la pantalla igual tiene que enterarse de
        // quien abre, porque decide si le toca actuar ya.
        atributos: { role: 'status', 'aria-live': 'polite' },
        texto: textoDeApertura(conHeroe, turnoActual, yo),
      }),
    );
  }

  const segundos = Number.isInteger(cuentaAtras) && cuentaAtras > 0 ? cuentaAtras : 0;
  if (segundos > 0) {
    // Revisión del modo jugador del 6-oct, punto 17: 5, 4, 3, 2, 1, ¡COMBATE!
    // Es una pausa de la vista, no del servidor (no hay esperas en el
    // backend): lo que llegue mientras tanto se retiene y se ve después.
    capa.append(...cuentaAtrasDe(capa, segundos, { programar, alCerrar }));
  } else {
    const entrar = h('button', {
      clase: 'boton boton--primario presentacion__entrar',
      texto: 'Entrar al combate',
      atributos: { type: 'button' },
      datos: { accion: 'entrar-al-combate' },
    });
    entrar.addEventListener('click', () => cerrar(capa, alCerrar));
    capa.append(entrar);
  }

  // Escape también cierra: si el foco está aquí, tiene que haber una salida
  // de teclado (WCAG 2.1.2).
  capa.addEventListener('keydown', (evento) => {
    if (evento.key === 'Escape') {
      cerrar(capa, alCerrar);
    }
  });

  return capa;
}

/**
 * Pinta la presentacion dentro de una raiz y devuelve como cerrarla.
 *
 * Devolver el cierre es lo que permite que **el evento siguiente** la quite:
 * quien escucha el canal llama a esto cuando llega el primer turno o la
 * primera accion, sin que este modulo sepa nada del protocolo.
 *
 * @param {HTMLElement} raiz
 * @param {object} opciones las de `presentacionDeHeroes`.
 * @returns {() => void} cierra la presentacion; es idempotente.
 */
export function mostrarPresentacion(raiz, opciones = {}) {
  const capa = presentacionDeHeroes(opciones);
  if (!capa || !raiz) {
    return () => {};
  }
  vaciar(raiz).append(capa);
  raiz.hidden = false;
  // El foco va al boton: es la salida, y asi el Escape de la capa funciona.
  // Con cuenta atrás no hay botón: se cierra sola.
  capa.querySelector('[data-accion="entrar-al-combate"]')?.focus();
  return () => cerrar(capa, opciones.alCerrar ?? (() => {}));
}

/**
 * La cuenta atrás: la cifra grande (oculta al lector de pantalla, que no
 * tiene por qué oír cinco números) y un único aviso al empezar y al acabar.
 *
 * @param {HTMLElement} capa
 * @param {number} segundos
 * @param {{programar: Function, alCerrar: Function}} opciones
 * @returns {HTMLElement[]}
 */
function cuentaAtrasDe(capa, segundos, { programar, alCerrar }) {
  const cifra = h('p', {
    clase: 'presentacion__cuenta',
    texto: String(segundos),
    datos: { zona: 'cuenta-atras', tic: 'a' },
    atributos: { 'aria-hidden': 'true' },
  });
  const aviso = h('p', {
    clase: 'solo-lectores',
    texto: `El combate empieza en ${segundos} segundos.`,
    atributos: { role: 'status' },
  });
  capa.dataset.cuentaAtras = 'si';
  let quedan = segundos;
  const tic = () => {
    if (!capa.isConnected) {
      return;
    }
    quedan -= 1;
    // El atributo alterna para que la animación del número vuelva a empezar.
    cifra.dataset.tic = cifra.dataset.tic === 'a' ? 'b' : 'a';
    if (quedan > 0) {
      cifra.textContent = String(quedan);
      programar(tic, 1000);
      return;
    }
    cifra.textContent = GRITO_DE_COMBATE;
    cifra.dataset.final = 'si';
    aviso.textContent = '¡Combate!';
    programar(() => cerrar(capa, alCerrar), PAUSA_FINAL_MS);
  };
  programar(tic, 1000);
  return [cifra, aviso];
}

/** Una ficha: retrato, nombre, quien lo lleva y si abre. */
function fichaDeHeroe(participante, { yo, abre }) {
  const { heroe, jugador, esIA } = participante;
  const ficha = h('div', {
    clase: `presentacion__heroe${abre ? ' presentacion__heroe--abre' : ''}`,
    datos: { jugador: jugador?.id ?? '' },
  });
  ficha.append(retratoDeHeroe({ nombre: heroe.nombre }, { conNombre: false }));
  ficha.append(h('p', { clase: 'presentacion__nombre', texto: heroe.nombre }));

  let quien;
  if (esIA) {
    // Revisión del 6-oct, punto 17: «IA», sin más.
    quien = 'IA';
  } else if (jugador?.id === yo) {
    quien = 'Tu héroe';
  } else {
    quien = jugador?.apodo ?? 'Otro jugador';
  }
  ficha.append(h('p', { clase: 'presentacion__jugador', texto: quien }));
  return ficha;
}

/** Quita la capa una sola vez y avisa. */
function cerrar(capa, alCerrar) {
  if (!capa.isConnected) {
    return;
  }
  const raiz = capa.parentElement;
  capa.remove();
  if (raiz) {
    raiz.hidden = true;
  }
  alCerrar();
}
