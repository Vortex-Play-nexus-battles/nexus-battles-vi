/**
 * Campo de combate — HU-JUE-017 (RF-JUE-017), CA-01 y CA-03.
 *
 * La ficha pide «ubicacion aleatoria del personaje en el campo». Lo que NO
 * pide, y seria un defecto, es que el heroe salte de sitio cada vez que se
 * repinta la pantalla: llega un `partida.accion.resuelta`, se vuelve a dibujar
 * y el jugador ve a todo el mundo teletransportarse. Tampoco puede depender de
 * `Math.random()`, porque entonces no hay forma de probarlo.
 *
 * Asi que la posicion es **aleatoria en apariencia y estable de verdad**: se
 * deriva del identificador del jugador. Dos participantes distintos caen en
 * sitios distintos, el mismo participante cae siempre en el suyo, y en una
 * prueba se puede afirmar exactamente donde.
 *
 * ## Donde NO se colocan
 *
 * En dos franjas: la de arriba la ocupa la barra de vida y turno (CA-02) y la
 * de abajo los controles (CA-01 dice que los menus van abajo o al lado, nunca
 * sobre el campo). Quedan entre el 18 % y el 78 % del alto.
 *
 * ## Lados
 *
 * En una partida por equipos (HU-SAL-004) cada bando ocupa su mitad: si los
 * companeros y los rivales estuvieran mezclados no se sabria a quien se ataca.
 * Sin equipos, quien mira va a la izquierda y los demas a la derecha.
 */

import { h, vaciar, clases } from '../../comun/ui/dom.js';
import { retratoDeHeroe } from '../../comun/ui/juego/heroe.js';

/** Margen vertical reservado arriba (HUD) y abajo (controles), en %. */
const FRANJA_SUPERIOR = 18;
const FRANJA_INFERIOR = 22;

/**
 * Numero estable entre 0 y 1 derivado de un texto.
 *
 * Es un FNV-1a de 32 bits: barato, bien repartido para cadenas cortas y, sobre
 * todo, el mismo resultado en cualquier navegador y en cualquier corrida. No
 * es ni pretende ser aleatoriedad criptografica — es colocar munecos.
 *
 * @param {string} texto
 * @param {number} [semilla] para pedir dos numeros distintos del mismo id
 * @returns {number} en [0, 1)
 */
export function dispersion(texto, semilla = 0) {
  let h32 = 0x811c9dc5 ^ semilla;
  const cadena = String(texto ?? '');
  for (let i = 0; i < cadena.length; i += 1) {
    h32 ^= cadena.charCodeAt(i);
    // El multiplicador primo de FNV, en aritmetica de 32 bits sin desbordar.
    h32 = Math.imul(h32, 0x01000193) >>> 0;
  }
  return (h32 >>> 8) / 0x01000000;
}

/**
 * Donde va cada participante, en porcentaje del campo.
 *
 * @param {Array<object>} participantes esquema `Participante` del contrato
 * @param {string} yo identificador del jugador de esta sesion
 * @returns {Array<{participante: object, izquierda: number, arriba: number, lado: 'propio'|'rival'}>}
 */
export function repartirEnElCampo(participantes, yo) {
  const lista = Array.isArray(participantes) ? participantes : [];
  const miEquipo = lista.find((p) => p.jugador?.id === yo)?.equipo ?? null;

  const esDelMioLado = (p) => (miEquipo === null ? p.jugador?.id === yo : p.equipo === miEquipo);

  // Por lado, para repartirlos sin amontonarlos: cada uno recibe su carril.
  const porLado = { propio: [], rival: [] };
  for (const p of lista) {
    porLado[esDelMioLado(p) ? 'propio' : 'rival'].push(p);
  }

  const puestos = [];
  for (const [lado, miembros] of Object.entries(porLado)) {
    miembros.forEach((participante, indice) => {
      const id = participante.jugador?.id ?? `sin-id-${indice}`;
      // Carril vertical propio, y dentro de el un desplazamiento derivado del
      // id: ni superpuestos ni alineados como en una formacion militar.
      const alto = 100 - FRANJA_SUPERIOR - FRANJA_INFERIOR;
      const carril = alto / miembros.length;
      const arriba = FRANJA_SUPERIOR + carril * indice + carril * (0.2 + dispersion(id) * 0.5);

      // Cada bando en su mitad, con su propio margen interior para que nadie
      // quede pegado al borde ni invada el centro.
      const base = lado === 'propio' ? 8 : 58;
      const izquierda = base + dispersion(id, 7) * 26;

      puestos.push({
        participante,
        lado: /** @type {'propio'|'rival'} */ (lado),
        izquierda: Math.round(izquierda * 10) / 10,
        arriba: Math.round(arriba * 10) / 10,
      });
    });
  }
  return puestos;
}

/**
 * Pinta el campo con sus participantes.
 *
 * El campo es decorativo para quien usa lector de pantalla: la informacion que
 * importa —vida, turno, a quien se ataca— vive en la barra superior y en los
 * controles, que si se leen. Duplicarla aqui obligaria a oir a cada heroe dos
 * veces.
 *
 * @param {HTMLElement} contenedor
 * @param {Array<object>} participantes
 * @param {string} yo
 * @param {{caidos?: Set<string>}} [estado]
 */
export function pintarCampo(contenedor, participantes, yo, { caidos } = {}) {
  if (!(contenedor instanceof HTMLElement)) {
    throw new TypeError('campo: se esperaba un HTMLElement.');
  }
  vaciar(contenedor);
  contenedor.setAttribute('aria-hidden', 'true');

  for (const puesto of repartirEnElCampo(participantes, yo)) {
    const { participante, izquierda, arriba, lado } = puesto;
    const id = participante.jugador?.id;
    const caido = Boolean(caidos?.has(id));

    contenedor.append(
      h('div', {
        clase: clases('campo__puesto', `campo__puesto--${lado}`, caido && 'campo__puesto--caido'),
        datos: { puesto: id ?? '', lado },
        atributos: { style: `left:${izquierda}%; top:${arriba}%` },
        hijos: [
          retratoDeHeroe(
            {
              nombre: participante.heroe?.nombre,
              nivel: participante.heroe?.nivel ?? undefined,
              imagen: participante.heroe?.retratoUrl ?? undefined,
            },
            { conNombre: false },
          ),
          h('span', { clase: 'campo__nombre', texto: participante.heroe?.nombre ?? '' }),
        ],
      }),
    );
  }
}

/**
 * Marca de quien es el turno sobre el campo, sin repintarlo.
 *
 * @param {HTMLElement} contenedor
 * @param {string|null} idJugador
 */
export function marcarTurnoEnElCampo(contenedor, idJugador) {
  for (const puesto of contenedor?.querySelectorAll('[data-puesto]') ?? []) {
    if (idJugador && puesto.dataset.puesto === idJugador) {
      puesto.dataset.turno = 'si';
    } else {
      delete puesto.dataset.turno;
    }
  }
}
