/**
 * HU-SAL-005 · RF-JUE-009, RF-JUE-017 — jugar el turno y ver el combate.
 *
 * Aquí vive lo que la vista de batalla necesita por encima del panel de vidas:
 * mandar la acción por el canal, y saber qué hacer con cada mensaje que llega.
 *
 * NO pinta barras: eso es de `panel-vidas.js`, que ya sabe. NO habla STOMP: el
 * cliente se inyecta ya conectado, igual que en el resto del servicio.
 *
 * @module combate
 */

import {
  esSeccionDegradada,
  pintarSeccionDegradada,
  limpiarSeccionDegradada,
} from '../../comun/degradacion/aviso-degradacion.js';
import { h, vaciar } from '../../comun/ui/dom.js';
import {
  accionDeCombate,
  chipDeEfecto,
  impactoEnCampo,
  medidorDePoder,
  registroDeCombate,
} from '../../comun/ui/juego/combate.js';
import { panelDeResultado } from '../../comun/ui/juego/resultado.js';
import { puntosDePoder } from '../../comun/heroe-propio.js';
import { narrarAccion, narrarTurno, nombreDe } from './narracion.js';
import { textoDelServidor } from '../../comun/ui/texto-de-fallo.js';

/**
 * Por que las acciones especiales estan deshabilitadas — UXC-2, B7.
 *
 * §6.1.2 da a cada heroe tres acciones con coste en poder y un turno de carga.
 * Desde B7 el motor de combate las resuelve (y las epicas de la Tabla 20), y
 * lo que se puede jugar en cada turno lo CALCULA EL SERVIDOR: cada heroe trae
 * `acciones` con `disponible` y `motivo` (salas-partidas.yaml 1.7.0, canal
 * 1.5.0). La vista no reimplementa la regla. Mientras ese estado no llega
 * —una partida anterior a B7, o el motor no respondio al empezar— se ensenan
 * con este motivo, en vez de ofrecer un boton que el servidor rechazaria.
 *
 * Corto a proposito, como todos los motivos de la franja (LARGO_MAXIMO_MOTIVO):
 * el motivo va en la cabecera del grupo y ensancha la franja de mando. Con seis
 * participantes a 1360×768, uno de mas de ~50 caracteres empuja «Lo que ha
 * pasado» a una segunda fila y el campo baja del 80 % de la pantalla que pide
 * §7.6 (laboratorio visual, campo-de-combate.spec.js: 71,7 % con el texto
 * anterior, 73 caracteres).
 */
export const MOTIVO_ESPECIALES = 'Llegan con el estado de combate de tu héroe.';

/** Largo maximo de un motivo de la franja de especiales (ver MOTIVO_ESPECIALES). */
export const LARGO_MAXIMO_MOTIVO = 48;

/** Los motivos de la cabecera de especiales, por estado; todos caben en una fila. */
export const MOTIVOS_DE_ESPECIALES = Object.freeze({
  fueraDeTurno: 'Se habilitan en tu turno.',
  sinEspeciales: 'Tu héroe no tiene acciones especiales.',
  eligiendoRival: 'Elige el rival; púlsala otra vez para cancelar.',
  miTurno: 'Tu turno: las que no puedes usar dicen por qué.',
});

/** El ataque sin accion especial (motor-combate.yaml 1.2.0). */
export const ATAQUE_BASICO = 'ATAQUE_BASICO';

/** §6.1.1: un sanador no inflige daño; su acción básica es sanar. */
export const MOTIVO_SANADOR = 'Tu héroe es sanador: no ataca. Usa sus sanaciones.';

/**
 * El identificador del jugador de un participante, venga del canal
 * (`{jugador: {id}}`) o de `GET /partidas/{id}` (`{jugador: 'id'}`).
 *
 * @param {object} participante
 * @returns {string|null}
 */
function idDeJugador(participante) {
  const jugador = participante?.jugador;
  return typeof jugador === 'string' ? jugador : (jugador?.id ?? null);
}

/**
 * El estado de combate del heroe propio tal como lo manda el servidor (B7):
 * lo que puede jugar ahora (`acciones`) y su poder. Acepta los participantes
 * del canal y los de `GET /partidas/{id}`.
 *
 * @param {Array<object>} participantes
 * @param {string} yo
 * @returns {{acciones: Array<object>, poderActual: number|null, poderMaximo: number|null}}
 */
export function estadoPropioDe(participantes, yo) {
  const heroe = (participantes ?? []).find((p) => idDeJugador(p) === yo)?.heroe ?? null;
  return {
    acciones: Array.isArray(heroe?.acciones) ? heroe.acciones : [],
    poderActual: Number.isFinite(heroe?.poderActual) ? heroe.poderActual : null,
    poderMaximo: Number.isFinite(heroe?.poderMaximo) ? heroe.poderMaximo : null,
    // UXC-9 — acciones en carga y los turnos propios que les faltan (1.5.0).
    recargas: esMapaDeRecargas(heroe?.recargas) ? heroe.recargas : {},
  };
}

/** `recargas` del contrato: código de acción → turnos que faltan. */
function esMapaDeRecargas(valor) {
  return Boolean(valor) && typeof valor === 'object' && !Array.isArray(valor);
}

/** Nombres comparables: sin tildes, sin mayusculas, sin espacios sobrantes. */
function normalizar(texto) {
  return String(texto ?? '')
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLowerCase()
    .trim();
}

/**
 * Coste y carga de una accion del servidor, como insignias del kit.
 *
 * UXC-9 — la épica se ve (antes solo estaba en el nombre accesible) y la
 * carga que falta también: con `recargas` del servidor, la insignia del reloj
 * dice «faltan N» en vez de los turnos de carga de la acción, así que quien
 * no usa lector ni ratón sabe por qué está cerrada.
 *
 * @param {{codigo?: string, esEpica?: boolean, costoPoder?: number|null, todoElPoder?: boolean,
 *   turnosDeCarga?: number|null}} accion
 * @param {Record<string, number>} [recargas]
 * @returns {Array<{icono: string, texto: string, etiqueta: string, enCarga?: boolean}>}
 */
export function insigniasDe(accion, recargas = {}) {
  const insignias = [];
  if (accion.esEpica === true) {
    insignias.push({
      icono: 'estrella-llena',
      texto: 'Épica',
      // Sin la palabra «poder»: las etiquetas de coste no se repiten en el
      // nombre accesible, y esta sí tiene que decirse.
      etiqueta: 'Acción épica: sin coste y con dos turnos de recarga',
    });
  }
  if (accion.todoElPoder === true || accion.costoPoder === null) {
    insignias.push({ icono: 'rayo', texto: 'Todo', etiqueta: 'Todo tu poder' });
  } else if (Number.isFinite(accion.costoPoder) && accion.costoPoder > 0) {
    insignias.push({
      icono: 'rayo',
      texto: String(accion.costoPoder),
      etiqueta: `${accion.costoPoder} de poder`,
    });
  }
  const faltan = Number(recargas?.[accion.codigo]);
  if (Number.isInteger(faltan) && faltan > 0) {
    insignias.push({
      icono: 'reloj',
      texto: `faltan ${faltan}`,
      etiqueta: faltan === 1 ? 'En carga: falta un turno' : `En carga: faltan ${faltan} turnos`,
      enCarga: true,
    });
  } else if (Number.isFinite(accion.turnosDeCarga) && accion.turnosDeCarga > 0) {
    insignias.push({
      icono: 'reloj',
      texto: String(accion.turnosDeCarga),
      etiqueta:
        accion.turnosDeCarga === 1
          ? 'Un turno de carga'
          : `${accion.turnosDeCarga} turnos de carga`,
    });
  }
  return insignias;
}

/** Destino `accionDelJugador` del AsyncAPI. Prefijo de envío `/app`. */
export function destinoDeAccion(idPartida) {
  return `/app/partidas/${idPartida}/acciones`;
}

/** Tipos que viajan por el canal de la partida, fijados por el contrato. */
export const ACCION_RESUELTA = 'partida.accion.resuelta';
export const TURNO_CAMBIADO = 'partida.turno.cambiado';
export const PARTIDA_FINALIZADA = 'partida.finalizada';
/** AsyncAPI 1.8.0 (salas-partidas 1.10.0): alguien salio del combate rindiendose. */
export const PARTICIPANTE_RENDIDO = 'partida.participante.rendido';

/**
 * Manda la acción del turno.
 *
 * El identificador del jugador NO viaja: lo pone el servidor desde el token del
 * CONNECT. Si lo mandara el cliente, cualquiera podría jugar el turno de otro.
 *
 * @param {{enviar: (destino: string, cuerpo: object) => void}} cliente STOMP conectado
 * @param {string} idPartida
 * @param {{codigoAccion?: string, idObjetivo?: string|null}} [accion]
 */
export function enviarAccion(cliente, idPartida, accion = {}) {
  cliente.enviar(destinoDeAccion(idPartida), {
    codigoAccion: accion.codigoAccion ?? 'ATAQUE_BASICO',
    idObjetivo: accion.idObjetivo ?? null,
  });
}

/**
 * Lleva la cuenta de lo que ya se aplicó, para que reconectar no duplique.
 *
 * El problema real: al reconectar, el servidor puede reenviar mensajes que el
 * cliente ya vio. Aplicar dos veces la misma acción resuelta no rompe la barra
 * —`vidaActual` es absoluta, no un delta— pero sí duplica las líneas del
 * historial y vuelve a disparar el final. Se descartan por identidad.
 *
 * Un aviso de acción no trae identificador propio en el contrato, así que la
 * identidad se compone de lo que sí trae: partida, el turno en que llegó
 * (cada jugador hace una acción por turno, HU-JUE-002), ejecutor, la acción y
 * la vida y el poder resultantes de cada afectado.
 *
 * Auditoría de DEV del 30-sep: antes no entraban ni el turno ni la acción, y
 * una especial que no movía ninguna vida —una defensa, un apoyo— con las
 * mismas vidas que una acción anterior del mismo jugador se descartaba como
 * repetida: no salía en el registro.
 *
 * @returns {{yaVisto: (aviso: object) => boolean, olvidar: () => void}}
 */
export function registroDeAvisos() {
  const vistos = new Set();
  /** El turno del último `partida.turno.cambiado` visto: ancla de las acciones. */
  let turno = null;

  return {
    yaVisto(aviso) {
      const huella = huellaDe(aviso, turno);
      if (huella !== null && vistos.has(huella)) {
        return true;
      }
      if (huella !== null) {
        vistos.add(huella);
      }
      // Solo hacia delante: un cambio de turno viejo que se reenvie no
      // devuelve el ancla a un turno ya pasado.
      if (
        aviso?.tipo === TURNO_CAMBIADO &&
        Number.isInteger(aviso.numeroTurno) &&
        (turno === null || aviso.numeroTurno > turno)
      ) {
        turno = aviso.numeroTurno;
      }
      return false;
    },
    olvidar() {
      vistos.clear();
    },
  };
}

/**
 * Huella de un aviso, o null si no es de los que se deduplican.
 *
 * @param {object} aviso
 * @param {number|null} [turno] turno vigente cuando llegó (ver `registroDeAvisos`)
 * @returns {string|null}
 */
function huellaDe(aviso, turno = null) {
  if (!aviso || typeof aviso.tipo !== 'string') {
    return null;
  }
  if (aviso.tipo === ACCION_RESUELTA) {
    const afectados = (aviso.afectados ?? [])
      .map((a) => `${a.idJugador}:${a.vidaActual}:${a.poderActual ?? ''}`)
      .join('|');
    const accion = aviso.accion?.codigo ?? '';
    return `${aviso.tipo}#${aviso.idPartida}#t${turno ?? '?'}#${aviso.idEjecutor}#${accion}#${afectados}`;
  }
  if (aviso.tipo === TURNO_CAMBIADO) {
    // El número de turno sube siempre: identifica el aviso por sí solo.
    return `${aviso.tipo}#${aviso.idPartida}#${aviso.numeroTurno}`;
  }
  if (aviso.tipo === PARTICIPANTE_RENDIDO) {
    // Cada participante se rinde una vez como mucho.
    return `${aviso.tipo}#${aviso.idPartida}#${aviso.idJugador}`;
  }
  if (aviso.tipo === PARTIDA_FINALIZADA) {
    // Una partida termina una sola vez... pero el servidor puede anunciar el
    // final dos veces a proposito: primero sin `reparto` (el libro de creditos
    // no respondio, HU-JUE-014 CA-06) y despues con el, cuando la liquidacion
    // se cierra. Ese segundo aviso no es un duplicado: trae lo que faltaba.
    // Lo mismo con `recompensa` (HU-JUE-012): sale cuando el libro acredita
    // los creditos por jugar, y puede llegar en un aviso posterior.
    const conReparto = Array.isArray(aviso.reparto) && aviso.reparto.length > 0;
    const conRecompensa = Array.isArray(aviso.recompensa) && aviso.recompensa.length > 0;
    return `${aviso.tipo}#${aviso.idPartida}#${conReparto ? 'con-reparto' : 'sin-reparto'}#${conRecompensa ? 'con-recompensa' : 'sin-recompensa'}`;
  }
  return null;
}

/**
 * Cuantos creditos netos gano o perdio quien mira, segun el `reparto` del
 * aviso de fin (HU-JUE-014, CA-04). `null` si el aviso no trae reparto: sin
 * apuesta, o con la liquidacion todavia pendiente.
 *
 * @param {{reparto?: Array<{idJugador: string, creditos: number}>}} aviso
 * @param {string} yo
 * @returns {number|null}
 */
export function creditosDe(aviso, yo) {
  const entrada = (aviso?.reparto ?? []).find((r) => r?.idJugador === yo);
  return entrada && Number.isFinite(entrada.creditos) ? entrada.creditos : null;
}

/**
 * Texto del resultado, desde el punto de vista de quien mira.
 *
 * Sin ganadores es empate: el servidor lo deja vacío cuando nadie quedó en pie,
 * y decirlo es más honesto que inventar un vencedor.
 *
 * En el modo cooperativo (HU-SAL-004) el aviso trae `equipoGanador` y se dice
 * el equipo: quien mira puede haber caido y aun asi haber ganado con los suyos.
 *
 * @param {{ganadores?: string[], equipoGanador?: number}} aviso
 * @param {string} yo identificador del jugador que mira
 * @param {number|null} [miEquipo] equipo de quien mira, si la partida es por equipos
 * @returns {string}
 */
export function textoDelResultado(aviso, yo, miEquipo = null) {
  const equipo = aviso?.equipoGanador;
  let base = 'Combate terminado en empate.';
  if (Number.isInteger(equipo) && equipo > 0) {
    base = gano(aviso, yo, miEquipo)
      ? `Tu equipo (${equipo}) ha ganado el combate.`
      : `Gana el equipo ${equipo}. Tu equipo ha perdido.`;
  } else if ((aviso?.ganadores ?? []).length > 0) {
    base = gano(aviso, yo, miEquipo) ? 'Has ganado el combate.' : 'Has perdido el combate.';
  }
  return `${base}${textoDelReparto(aviso, yo)}${textoDeLaRecompensa(aviso, yo)}`;
}

/**
 * Si la partida acabo sin vencedor.
 *
 * El servidor deja `ganadores` vacio cuando nadie quedo en pie, y el contrato
 * lo dice: «Vacio si nadie quedo en pie (empate; el desempate es del PO)».
 *
 * @param {{ganadores?: string[], equipoGanador?: number}} aviso
 * @returns {boolean}
 */
/**
 * Une dos listas de `{idJugador, ...}` quedandose con la entrada mas reciente
 * de cada jugador.
 *
 * Hace falta porque el final de partida se anuncia por partes: el aviso que
 * trae la liquidacion tardia de la apuesta lleva `recompensa` vacia, y quedarse
 * solo con el ultimo aviso borraria la recompensa ya anunciada.
 *
 * @param {Array<{idJugador: string}>|undefined} previas
 * @param {Array<{idJugador: string}>|undefined} nuevas
 * @returns {Array<{idJugador: string}>}
 */
export function mezclarPorJugador(previas, nuevas) {
  const porJugador = new Map();
  for (const entrada of [...(previas ?? []), ...(nuevas ?? [])]) {
    if (entrada?.idJugador) {
      porJugador.set(entrada.idJugador, entrada);
    }
  }
  return [...porJugador.values()];
}

export function empate(aviso) {
  const equipo = aviso?.equipoGanador;
  if (Number.isInteger(equipo) && equipo > 0) {
    return false;
  }
  return (aviso?.ganadores ?? []).length === 0;
}

/**
 * El desenlace desde el punto de vista de quien mira, para el panel.
 *
 * Tres estados y no dos: el servidor deja `ganadores` vacio cuando nadie quedo
 * en pie, y hasta R10 eso se pintaba como DERROTA mientras el texto de al lado
 * decia «empate». Decirle a alguien que perdio algo que no perdio es mentirle,
 * aunque la cifra sea correcta.
 *
 * @param {{ganadores?: string[], equipoGanador?: number}} aviso
 * @param {string} yo
 * @param {number|null} [miEquipo]
 * @returns {'victoria'|'derrota'|'empate'}
 */
export function desenlaceDe(aviso, yo, miEquipo = null) {
  if (empate(aviso)) {
    return 'empate';
  }
  return gano(aviso, yo, miEquipo) ? 'victoria' : 'derrota';
}

/**
 * Cuantos creditos se movieron EN TOTAL para quien mira: la apuesta mas la
 * recompensa por jugar. `null` si el aviso no trae ninguna de las dos.
 *
 * ## Por que la suma y no solo una
 *
 * Hasta R10 el numero grande del panel salia de `recompensa`, que es la
 * recompensa por jugar y **nunca resta** (`CreditoPorPartida` rechaza
 * negativos, y el contrato la declara `minimum: 0`). Asi que quien perdia una
 * apuesta de 350 creditos veia un `+2` enorme y la perdida solo en la frase de
 * abajo. La rama `data-signo="negativo"` del panel era inalcanzable.
 *
 * Las dos cifras son del servidor; sumarlas no inventa nada, y el resultado es
 * lo que de verdad le paso al saldo del jugador en esta partida.
 *
 * @param {object} aviso
 * @param {string} yo
 * @returns {number|null}
 */
export function netoDeCreditos(aviso, yo) {
  const apuesta = creditosDe(aviso, yo);
  const recompensa = recompensaDe(aviso, yo);
  if (apuesta === null && recompensa === null) {
    return null;
  }
  return (apuesta ?? 0) + (recompensa?.creditos ?? 0);
}

/**
 * Si quien mira gano.
 *
 * Se extrajo de `textoDelResultado` para que el panel de desenlace (UX-R2.3)
 * decida VICTORIA o DERROTA con la MISMA regla y no adivinandola del texto: un
 * `/ganado/.test(...)` sobre una frase traducible es un defecto esperando a
 * que alguien reescriba la frase.
 *
 * En el modo cooperativo se gana con el equipo aunque uno haya caido.
 *
 * @param {{ganadores?: string[], equipoGanador?: number}} aviso
 * @param {string} yo
 * @param {number|null} [miEquipo]
 * @returns {boolean}
 */
export function gano(aviso, yo, miEquipo = null) {
  const ganadores = aviso?.ganadores ?? [];
  const equipo = aviso?.equipoGanador;
  if (Number.isInteger(equipo) && equipo > 0) {
    return ganadores.includes(yo) || (miEquipo !== null && miEquipo === equipo);
  }
  return ganadores.includes(yo);
}

/**
 * Lo que el libro acredito por jugar a quien mira (HU-JUE-012): `{creditos,
 * ganador, cofre}` o `null` si el aviso no trae su recompensa (pendiente, ya
 * anunciada, o sancionado).
 *
 * @param {{recompensa?: Array<{idJugador: string, creditos: number, ganador: boolean, cofre?: string}>}} aviso
 * @param {string} yo
 * @returns {{creditos: number, ganador: boolean, cofre: string|null}|null}
 */
export function recompensaDe(aviso, yo) {
  const entrada = (aviso?.recompensa ?? []).find((r) => r?.idJugador === yo);
  if (!entrada || !Number.isFinite(entrada.creditos)) {
    return null;
  }
  return {
    creditos: entrada.creditos,
    ganador: entrada.ganador === true,
    cofre: entrada.cofre ?? null,
  };
}

/**
 * Coletilla de la recompensa por jugar (HU-JUE-012): cuantos creditos se
 * ganaron por ganar o por participar, y el cofre si toco uno. Vacia si el
 * aviso no la trae.
 *
 * @param {object} aviso
 * @param {string} yo
 * @returns {string}
 */
function textoDeLaRecompensa(aviso, yo) {
  const recompensa = recompensaDe(aviso, yo);
  if (recompensa === null) {
    return '';
  }
  const plural = recompensa.creditos === 1 ? 'crédito' : 'créditos';
  const motivo = recompensa.ganador ? 'por ganar' : 'por participar';
  const cofre = recompensa.cofre ? ' Además te llevas un cofre.' : '';
  return ` Ganas ${recompensa.creditos} ${plural} ${motivo}.${cofre}`;
}

/**
 * Coletilla economica del resultado (HU-JUE-014, CA-04): que paso con la
 * apuesta de quien mira. Vacia si el aviso no trae reparto.
 *
 * @param {object} aviso
 * @param {string} yo
 * @returns {string}
 */
function textoDelReparto(aviso, yo) {
  const creditos = creditosDe(aviso, yo);
  if (creditos === null) {
    return '';
  }
  if (creditos > 0) {
    return ` Te llevas ${creditos} créditos de la apuesta.`;
  }
  if (creditos < 0) {
    return ` Pierdes los ${-creditos} créditos que apostaste.`;
  }
  return ' Se te devuelven los créditos apostados.';
}

/**
 * De quién es el turno, dicho para quien mira.
 *
 * Hasta ahora el turno **solo** se notaba en que los botones de atacar
 * estaban grises o no. Eso deja fuera a tres personas: a quien usa lector de
 * pantalla (que solo se entera tabulando hasta un botón deshabilitado), a
 * quien espera su turno (que no sabe a quién está esperando) y a quien juega
 * contra la máquina (que no ve nada mientras la IA piensa). El dato ya venía
 * en `turnoActual.idJugador` y en cada `partida.turno.cambiado`: solo no se
 * enseñaba.
 *
 * @param {string|null} idJugador de quién es el turno
 * @param {Array<object>} participantes esquema del panel
 * @param {string} yo quién mira
 * @returns {{texto: string, mio: boolean}} vacío si aún no se sabe
 */
export function textoDelTurno(idJugador, participantes, yo) {
  if (!idJugador) {
    return { texto: '', mio: false };
  }
  if (idJugador === yo) {
    return { texto: 'Es tu turno', mio: true };
  }
  const quien = (participantes ?? []).find((p) => p.jugador?.id === idJugador);
  if (!quien) {
    // Un identificador que no está en pantalla: se dice que no es el turno
    // propio, que es lo único que se sabe con certeza, en vez de callar.
    return { texto: 'Turno de otro participante', mio: false };
  }
  const nombre = quien.heroe?.nombre ?? 'tu rival';
  // Revisión del 6-oct: mientras la IA «piensa» (la pausa del ritmo) el HUD lo
  // dice con las palabras del juego, y el mando está cerrado.
  return { texto: quien.esIA ? `Turno de la IA · ${nombre}` : `Turno de ${nombre}`, mio: false };
}

/**
 * Los enlaces con los que se sale del panel del desenlace (R17.4).
 *
 * Enlaces y no botones: llevan a otra pantalla, y así se pueden abrir en otra
 * pestaña o copiar. El primero es el principal. Uno sin texto o sin destino se
 * descarta: un botón que no lleva a ninguna parte es peor que no tenerlo.
 *
 * @param {Array<{texto: string, href: string, id: string}>} salidas
 * @returns {HTMLAnchorElement[]}
 */
export function enlacesDeSalida(salidas) {
  return (salidas ?? [])
    .filter((salida) => salida?.texto && salida?.href)
    .map((salida, i) =>
      h('a', {
        clase: i === 0 ? 'boton boton--primario' : 'boton boton--secundario',
        texto: salida.texto,
        atributos: { href: salida.href },
        datos: salida.id ? { accion: salida.id } : {},
      }),
    );
}

/**
 * Monta los controles de combate sobre el marcado de la vista.
 *
 * @param {ParentNode} raiz
 * @param {object} opciones
 * @param {string} opciones.idPartida
 * @param {string} opciones.yo identificador del jugador de esta sesión
 * @param {Array<object>} opciones.participantes esquema del panel: `{jugador:{id}}`
 * @param {string} [opciones.turnoDe]
 *   De quién es el turno AHORA MISMO, si ya se sabe: `turnoActual.idJugador`
 *   de `GET /partidas/{id}`. Sin esto los botones nacen cerrados y solo los
 *   abre un `partida.turno.cambiado`, que el servidor únicamente emite
 *   DESPUÉS de que alguien juegue (`AvanzarTurno`). En el turno 1 nadie ha
 *   jugado todavía, así que el combate no podía arrancar desde el navegador,
 *   y recargar a mitad de partida dejaba al jugador sin poder actuar hasta
 *   que lo hiciera el rival.
 * @param {(accion: object) => void} opciones.alAtacar
 * @param {Array<{texto: string, href: string, id: string}>} [opciones.salidas]
 *   Adónde ir cuando termina (R17.4). El panel del desenlace ocupa toda la
 *   pantalla, barra del juego incluida: sin salidas propias, quien acababa de
 *   jugar se quedaba mirando «VICTORIA» sin más camino que el botón «Atrás».
 *   La primera es la principal. Las rutas las pone la vista, que sabe si el
 *   borde sirve direcciones limpias; este módulo no conoce ninguna.
 * @param {object|null} [opciones.partidaFinalizada]
 *   La partida tal como la devuelve `GET /partidas/{id}` cuando ya está
 *   FINALIZADA (auditoría de DEV del 30-sep). Recargar una partida terminada
 *   decía «Combate en curso», reabría los botones y las especiales decían
 *   «No es tu turno». Con esto los controles nacen cerrados y se muestra el
 *   resultado (`ganadores`, `equipoGanador`).
 * @param {boolean} [opciones.recienEmpezada]
 *   La partida acaba de empezar en esta pantalla (la presentacion de los
 *   heroes, CA-04), no es una recarga. Si cuando llega ya no es el turno 1
 *   —contra la maquina, la IA juega en el mismo instante en que empieza—, el
 *   registro cuenta quien abrio y lo que dejo ese primer golpe: antes empezaba
 *   en «Turno 2» con la vida ya mermada y sin explicar por que (auditoria de
 *   DEV del 30-sep).
 * @returns {{recibir: (aviso: object) => void, rechazar: (problema: object) => boolean}}
 */
export function montarControlesDeCombate(
  raiz,
  {
    idPartida,
    yo,
    participantes,
    turnoDe,
    numeroTurno = null,
    alAtacar,
    salidas = [],
    partidaFinalizada = null,
    recienEmpezada = false,
  },
) {
  const zona = raiz.querySelector('[data-zona="acciones"]');
  // UXC-2 — las zonas nuevas de la barra de mando. Todas opcionales: una
  // prueba o una vista que no las tenga sigue funcionando como antes.
  const zonaEspeciales = raiz.querySelector('[data-zona="especiales"]');
  const zonaMotivoEspeciales = raiz.querySelector('[data-zona="motivo-especiales"]');
  const zonaPoder = raiz.querySelector('[data-zona="poder"]');
  const zonaRegistro = raiz.querySelector('[data-zona="registro"]');
  const zonaCampo = raiz.querySelector('[data-zona="campo"]');
  const aviso = raiz.querySelector('[data-zona="resultado"]');
  // Indicador de turno y panel de vidas: el turno se dice con palabras y se
  // marca sobre la barra de quien juega.
  const zonaTurno = raiz.querySelector('[data-zona="turno"]');
  const zonaVidas = raiz.querySelector('[data-zona="vidas"]');
  // HU-DIS-003: hueco de «Seccion degradada» cuando el motor de combate no
  // responde; y la zona para los demas rechazos de la cola privada.
  const zonaDegradacion = raiz.querySelector('[data-zona="degradacion"]');
  const zonaRechazo = raiz.querySelector('[data-zona="rechazo"]');
  // La barra de mando entera y la salida del HUD: al terminar se retira la
  // una y la otra deja de decir que la partida sigue en curso.
  const zonaMando = raiz.querySelector('[data-zona="mando"]');
  const zonaSalir = raiz.querySelector('[data-zona="salir"]');
  const registro = registroDeAvisos();
  /** La ultima accion enviada, para poder reintentarla tal cual. */
  let ultimaAccion = null;
  /** De quien es el turno ahora, y cual es: lo necesita `sincronizar`. */
  let turnoActual = turnoDe ?? null;
  let numeroActual = Number.isInteger(numeroTurno) ? numeroTurno : null;
  /** Sin canal no se juega: lo marca `bloquear` y lo quita `sincronizar`. */
  let sinCanal = false;
  // Una partida que ya llega terminada no abre nada ni un instante.
  let terminado = Boolean(partidaFinalizada);

  // UXC-2 · CombatLog: lo que ha pasado, con palabras.
  const bitacora = zonaRegistro ? registroDeCombate() : null;
  if (bitacora) {
    vaciar(zonaRegistro);
    zonaRegistro.append(bitacora.elemento);
  }
  function anotar(linea) {
    bitacora?.anotar(linea);
  }
  /**
   * Lo que se sabe del final, acumulado entre los avisos que lo anuncian.
   *
   * El servidor manda «partida.finalizada» mas de una vez a proposito cuando la
   * liquidacion de la apuesta llega tarde (HU-JUE-014 CA-06). Cada aviso trae
   * una parte, asi que hay que quedarse con la union y no con el ultimo.
   */
  let desenlaceConocido = null;

  // Rivales: a uno mismo no se ataca, ni a un companero de equipo en el modo
  // cooperativo (HU-SAL-004): el servidor lo rechazaria, y ofrecer el boton
  // seria invitar al error.
  const miEquipo = (participantes ?? []).find((p) => p.jugador?.id === yo)?.equipo ?? null;
  const rivales = (participantes ?? []).filter(
    (p) => p.jugador?.id !== yo && (miEquipo === null || p.equipo !== miEquipo),
  );

  // B7 — el estado de combate del heroe propio que calcula el servidor: que
  // acciones puede jugar ahora y su poder. Llega con la partida y se renueva
  // con cada turno propio (`partida.turno.cambiado`).
  const estadoPropio = estadoPropioDe(participantes, yo);
  /**
   * Lo que el catalogo cuenta del heroe propio (`mostrarHeroe`): el efecto de
   * cada accion y su poder maximo. Sin estado del servidor es lo unico que hay.
   *
   * @type {{acciones: Array<{nombre: string, costo?: string, efecto?: string}>,
   *   poderMaximo: number|null, motivo: string|null}|null}
   */
  let catalogo = null;
  /** Una especial de ataque elegida que espera a que se elija el rival. */
  let accionPendiente = null;
  /** Si el turno es de quien mira, tal como se pidio a `habilitar`. */
  let turnoPropio = false;
  /** Quien ya cayo no es objetivo de un ataque. */
  const caidos = new Set(
    (participantes ?? []).filter((p) => p.heroe?.vidaActual === 0).map((p) => idDeJugador(p)),
  );

  /** Si el servidor ya dijo que el heroe propio no tiene ataque (un sanador). */
  function noAtaca() {
    return (
      estadoPropio.acciones.length > 0 &&
      !estadoPropio.acciones.some((accion) => accion?.codigo === ATAQUE_BASICO)
    );
  }

  function rivalesEnPie() {
    return rivales.filter((rival) => !caidos.has(idDeJugador(rival)));
  }

  if (zona) {
    vaciar(zona);
    for (const rival of rivales) {
      // UX-R2.3 (HU-JUE-017 CA-03: «todos los efectos y controles mediante
      // iconos»). Antes esto era `<button class="boton boton--primario">Atacar
      // a X</button>`: el mismo boton azul que «Guardar cambios» en un
      // formulario de perfil. `.accion-combate` estaba en el kit desde el
      // Figma, con icono, etiqueta y sus dos variantes de «no se puede», y
      // ninguna vista lo usaba.
      const boton = accionDeCombate({
        nombre: rival.heroe?.nombre ?? 'Tu rival',
        icono: 'espada',
        // El motivo se rellena en `habilitar()`: aqui todavia no se sabe de
        // quien es el turno.
        impedimento: 'No es tu turno',
      });
      boton.dataset.atacar = rival.jugador.id;
      zona.append(boton);
    }
    zona.addEventListener('click', (evento) => {
      const boton = evento.target.closest('[data-atacar]');
      if (boton && !boton.disabled) {
        // Con una especial de ataque elegida, este rival es su objetivo (B7);
        // si no, es el ataque basico de siempre.
        const codigoAccion = accionPendiente?.codigo ?? ATAQUE_BASICO;
        accionPendiente = null;
        atacar({ idObjetivo: boton.dataset.atacar, codigoAccion });
        habilitar(turnoPropio);
      }
    });
  }

  // B7 — las acciones especiales se juegan. Las de ataque necesitan rival: con
  // uno solo en pie va a el; con varios, la accion queda elegida y el rival se
  // elige con su boton (pulsarla otra vez la suelta). Las demas no llevan
  // objetivo: el servidor aplica la regla de cada una (a uno mismo, al grupo).
  zonaEspeciales?.addEventListener('click', (evento) => {
    const boton = evento.target.closest('[data-especial]');
    if (!boton || boton.disabled) {
      return;
    }
    const accion = estadoPropio.acciones.find((a) => a?.codigo === boton.dataset.especial);
    if (!accion) {
      return;
    }
    if (accion.tipo === 'ATAQUE') {
      const enPie = rivalesEnPie();
      if (enPie.length > 1) {
        accionPendiente =
          accionPendiente?.codigo === accion.codigo
            ? null
            : { codigo: accion.codigo, nombre: accion.nombre ?? accion.codigo };
        habilitar(turnoPropio);
        return;
      }
      accionPendiente = null;
      atacar({ codigoAccion: accion.codigo, idObjetivo: idDeJugador(enPie[0]) });
    } else {
      accionPendiente = null;
      atacar({ codigoAccion: accion.codigo, idObjetivo: null });
    }
    habilitar(turnoPropio);
  });

  function atacar(accion) {
    ultimaAccion = accion;
    limpiarSeccionDegradada(zonaDegradacion);
    if (zonaRechazo) {
      zonaRechazo.hidden = true;
      zonaRechazo.textContent = '';
    }
    alAtacar(accion);
  }

  /**
   * Solo se puede atacar en el turno propio.
   *
   * No basta con apagar el boton: el kit distingue «fuera de turno» de «sin
   * poder» y el motivo tiene que leerse. Un boton gris sin explicacion es el
   * defecto clasico del juego por turnos — el jugador pulsa, no pasa nada, y
   * no sabe si le falta algo o si la pantalla esta rota.
   *
   * B7: tampoco se ofrece atacar a quien ya cayo, ni el ataque basico a un
   * sanador (el servidor no se lo da en sus `acciones`); con una especial de
   * ataque elegida, el boton del rival dice que accion va contra el.
   */
  function habilitar(esMiTurnoPedido) {
    turnoPropio = Boolean(esMiTurnoPedido);
    const esMiTurno = turnoPropio && !sinCanal && !terminado;
    if (!esMiTurno) {
      accionPendiente = null;
    }
    const sinAtaque = noAtaca() && !accionPendiente;
    for (const boton of zona?.querySelectorAll('[data-atacar]') ?? []) {
      const nombre = boton.dataset.accion ?? 'tu rival';
      let impedimento = null;
      if (!esMiTurno) {
        impedimento = 'No es tu turno';
      } else if (caidos.has(boton.dataset.atacar)) {
        impedimento = 'Ya cayó';
      } else if (sinAtaque) {
        impedimento = MOTIVO_SANADOR;
      }
      boton.disabled = impedimento !== null;
      boton.classList.toggle('accion-combate--fuera-de-turno', impedimento !== null);
      if (impedimento === null) {
        const texto = accionPendiente
          ? `${accionPendiente.nombre} contra ${nombre}`
          : `Atacar a ${nombre}`;
        boton.title = texto;
        boton.setAttribute('aria-label', texto);
      } else {
        boton.title = impedimento;
        boton.setAttribute('aria-label', `${nombre}. ${impedimento}`);
      }
    }
    pintarEspeciales(esMiTurno);
  }

  /**
   * El poder del heroe propio: el que lleva el servidor (B7) y, si todavia no
   * lo lleva, el maximo del catalogo. Sin ninguno de los dos no se toca.
   *
   * @param {number|null} [cambio] cuánto se acaba de mover (servidor: después − antes),
   *   para que el medidor lo enseñe («−2», «+2»); nunca se calcula aquí
   */
  function pintarPoder(cambio = null) {
    // Terminado no hay poder que gastar: el medidor se retira con el mando.
    if (!zonaPoder || terminado) {
      return;
    }
    const maximo = estadoPropio.poderMaximo ?? catalogo?.poderMaximo ?? null;
    if (maximo === null && catalogo === null) {
      return;
    }
    vaciar(zonaPoder);
    const medidor = medidorDePoder({ maximo, actual: estadoPropio.poderActual, cambio });
    if (medidor) {
      zonaPoder.append(medidor);
    }
    zonaPoder.hidden = !medidor;
  }

  /** Lo que el catalogo dice que hace una accion, para su descripcion. */
  function efectoDe(accion) {
    const delCatalogo = (catalogo?.acciones ?? []).find(
      (c) => normalizar(c?.nombre) === normalizar(accion.nombre ?? accion.codigo),
    );
    // La épica ya se dice en su insignia (y en el nombre accesible).
    return delCatalogo?.efecto ? `Efecto: ${delCatalogo.efecto}` : null;
  }

  /**
   * Las acciones especiales. Con el estado del servidor (B7) se pintan las
   * que calcula el motor, jugables en el turno propio si `disponible`, y si
   * no con su `motivo`. Sin el, las del catalogo, informativas y cerradas
   * (como antes de B7). El ataque basico no va aqui: son los botones de rival.
   *
   * Se rehacen enteras en cada cambio; el foco vuelve a la misma accion para
   * no perder a quien juega con teclado.
   *
   * @param {boolean} esMiTurno
   */
  function pintarEspeciales(esMiTurno) {
    // Terminado no se pinta nada: antes quedaban las especiales diciendo «No
    // es tu turno» con la partida ya cerrada (auditoria de DEV del 30-sep).
    if (!zonaEspeciales || terminado) {
      return;
    }
    if (estadoPropio.acciones.length === 0) {
      if (catalogo) {
        pintarEspecialesDelCatalogo();
      }
      return;
    }
    const enfocada = zonaEspeciales.contains(document.activeElement)
      ? document.activeElement?.dataset?.especial
      : null;
    vaciar(zonaEspeciales);
    const idMotivo = zonaMotivoEspeciales?.id || null;
    const especiales = estadoPropio.acciones.filter((a) => a?.codigo && a.codigo !== ATAQUE_BASICO);
    for (const accion of especiales) {
      let impedimento = null;
      if (!esMiTurno) {
        impedimento = 'No es tu turno';
      } else if (accion.disponible !== true) {
        impedimento = accion.motivo || 'No se puede usar ahora.';
      }
      const insignias = insigniasDe(accion, estadoPropio.recargas);
      const boton = accionDeCombate({
        nombre: accion.nombre ?? accion.codigo,
        icono: 'rayo',
        coste:
          Number.isFinite(accion.costoPoder) && accion.costoPoder > 0
            ? accion.costoPoder
            : undefined,
        insignias,
        detalle: insignias.some((i) => i.texto === 'Todo') ? 'Cuesta todo tu poder' : null,
        efecto: efectoDe(accion),
        impedimento,
        causa: impedimento && /poder/i.test(impedimento) ? 'poder' : 'turno',
        especial: true,
        describidaPor: idMotivo,
      });
      boton.dataset.especial = accion.codigo;
      if (accion.tipo) {
        boton.dataset.tipo = accion.tipo;
      }
      if (accion.tipo === 'ATAQUE') {
        boton.setAttribute('aria-pressed', String(accionPendiente?.codigo === accion.codigo));
      }
      zonaEspeciales.append(boton);
    }
    if (enfocada) {
      [...zonaEspeciales.querySelectorAll('[data-especial]')]
        .find((b) => b.dataset.especial === enfocada)
        ?.focus();
    }
    if (zonaMotivoEspeciales) {
      // El nombre de la especial elegida no va aqui: ya lo dice su boton,
      // pulsado (aria-pressed), y con el la franja no cabria en una fila.
      let motivo = MOTIVOS_DE_ESPECIALES.fueraDeTurno;
      if (especiales.length === 0) {
        motivo = MOTIVOS_DE_ESPECIALES.sinEspeciales;
      } else if (accionPendiente) {
        motivo = MOTIVOS_DE_ESPECIALES.eligiendoRival;
      } else if (esMiTurno) {
        motivo = MOTIVOS_DE_ESPECIALES.miTurno;
      }
      zonaMotivoEspeciales.textContent = motivo;
    }
  }

  /**
   * Deja dicho de quién es el turno: en el indicador (región viva, así que un
   * lector de pantalla lo anuncia solo) y sobre la barra de quien juega.
   *
   * @param {string|null} idJugador
   */
  function marcarTurno(idJugador, numero = null) {
    const turno = textoDelTurno(idJugador, participantes, yo);
    if (zonaTurno) {
      // UXC-2 — con el numero de turno conocido se dice tambien cual es
      // («Turno 7 · Es tu turno»); el punto medio lo pone el kit.
      if (Number.isInteger(numero) && turno.texto) {
        zonaTurno.replaceChildren(
          h('span', { clase: 'turno-actual__ronda', texto: `Turno ${numero}` }),
          h('span', { clase: 'turno-actual__texto', texto: turno.texto }),
        );
      } else {
        zonaTurno.textContent = turno.texto;
      }
      zonaTurno.hidden = turno.texto === '';
      zonaTurno.dataset.mio = String(turno.mio);
    }
    for (const puesto of zonaCampo?.querySelectorAll('[data-puesto]') ?? []) {
      if (idJugador && puesto.dataset.puesto === idJugador) {
        puesto.dataset.turno = 'si';
      } else {
        delete puesto.dataset.turno;
      }
    }
    for (const barra of zonaVidas?.querySelectorAll('[data-jugador]') ?? []) {
      // `delete` y no `= 'no'`: el selector del kit mira si el atributo está.
      if (idJugador && barra.dataset.jugador === idJugador) {
        barra.dataset.turno = 'si';
      } else {
        delete barra.dataset.turno;
      }
    }
  }

  /**
   * El combate termino: nadie tiene turno, no queda nada que jugar y el HUD lo
   * dice. Lo comparten el aviso de fin, la partida releida al volver el canal
   * y una partida que ya estaba FINALIZADA al montar.
   *
   * Auditoria de DEV del 30-sep: tras terminar quedaban el poder y las
   * especiales con «No es tu turno», y la salida del HUD seguia diciendo
   * «la partida sigue en curso». Se retira la barra de mando entera; el
   * registro se queda, que es la historia de la partida.
   */
  function cerrarControles() {
    terminado = true;
    accionPendiente = null;
    habilitar(false);
    // Se acabo: ya no es el turno de nadie. Dejar la marca puesta haria
    // creer que la partida sigue.
    marcarTurno(null);
    // UX-GAME-4 — el indicador no se vacia: dice que el combate termino.
    // Es el tercer estado del turno (tuyo / del rival / finalizado), y el
    // panel de resultado puede quedar tapado o fuera de la pantalla.
    if (zonaTurno) {
      zonaTurno.textContent = 'Combate finalizado';
      zonaTurno.hidden = false;
      zonaTurno.dataset.mio = 'false';
      zonaTurno.dataset.fin = 'si';
    }
    for (const parte of [zonaMando, zona, zonaEspeciales, zonaMotivoEspeciales, zonaPoder]) {
      if (parte) {
        parte.hidden = true;
      }
    }
    if (zonaEspeciales) {
      vaciar(zonaEspeciales);
    }
    if (zonaSalir) {
      zonaSalir.title = 'Volver a Jugar online (la partida terminó)';
    }
  }

  /**
   * El panel del desenlace con lo que se sepa del final.
   *
   * El servidor puede anunciar el final dos veces: primero sin `reparto` (el
   * libro no respondio, CA-06) y despues con el. Cada aviso trae una parte,
   * asi que se acumulan antes de pintar. Sin esto el segundo render perdia la
   * recompensa que ya se habia anunciado en el primero, y el numero grande
   * cambiaba de significado a mitad.
   *
   * @param {object} fuente `partida.finalizada`, o la partida FINALIZADA
   *   releida (trae `ganadores` y `equipoGanador` desde 1.7.0, sin creditos)
   * @param {{nota?: string}} [opciones] frase para cuando no llegan creditos
   */
  function pintarDesenlace(fuente, { nota = '' } = {}) {
    if (!aviso) {
      return;
    }
    // UX-R2.3 (HU-JUE-017 CA-04: «vistas de alto impacto al inicio y al
    // final»). `--t-display-tam` estaba en `tokens.css` reservada para esto.
    const primerAviso = desenlaceConocido === null;
    desenlaceConocido = {
      ...fuente,
      reparto: mezclarPorJugador(desenlaceConocido?.reparto, fuente?.reparto),
      recompensa: mezclarPorJugador(desenlaceConocido?.recompensa, fuente?.recompensa),
    };
    const desenlace = desenlaceDe(desenlaceConocido, yo, miEquipo);
    // El final puede anunciarse dos veces (reparto tardio): se anota una.
    if (primerAviso) {
      anotar({
        texto: `Combate finalizado: ${desenlace}.`,
        tono: 'fin',
        icono: desenlace === 'victoria' ? 'trofeo' : 'alerta',
      });
    }
    // La suma de apuesta y recompensa, no solo la recompensa: ver
    // `netoDeCreditos`. Antes quien perdia 350 creditos apostados veia un «+2».
    const creditos = netoDeCreditos(desenlaceConocido, yo);
    const detalle = textoDelResultado(desenlaceConocido, yo, miEquipo);
    vaciar(aviso);
    aviso.append(
      panelDeResultado({
        desenlace,
        detalle: creditos === null && nota ? `${detalle} ${nota}` : detalle,
        creditos,
        acciones: enlacesDeSalida(salidas),
      }),
    );
    aviso.hidden = false;
  }

  /**
   * Lo que se movio el poder propio, dicho en el registro.
   *
   * Auditoria de DEV del 30-sep: «Poder 10/10 +2 por turno» parecia no
   * moverse al usar una accion de coste 2. Se gastaban 2 y, contra la
   * maquina, el +2 del turno propio siguiente llegaba en el mismo instante:
   * el medidor volvia a 10/10 sin que nadie lo viera bajar. Ahora el gasto y
   * la recuperacion quedan escritos.
   *
   * @param {number|null} antes poder antes del aviso
   * @param {'gasto'|'turno'|'ajeno'} motivo por que cambio
   */
  function anotarPoder(antes, motivo) {
    const despues = estadoPropio.poderActual;
    if (!Number.isFinite(antes) || !Number.isFinite(despues) || antes === despues) {
      return;
    }
    // Revisión del 6-oct: además de escrito, el medidor enseña el movimiento.
    pintarPoder(despues - antes);
    const maximo = estadoPropio.poderMaximo ?? catalogo?.poderMaximo ?? null;
    const cifra = maximo === null ? `${despues}` : `${despues}/${maximo}`;
    const cuanto = Math.abs(despues - antes);
    let texto = `Pierdes ${cuanto} de poder (${cifra}).`;
    if (despues > antes) {
      texto = `Recuperas ${cuanto} de poder (${cifra}).`;
    } else if (motivo === 'gasto') {
      texto = `Gastas ${cuanto} de poder (${cifra}).`;
    }
    anotar({ texto, tono: 'sistema', icono: 'rayo' });
  }

  // Con `turnoDe` conocido se decide ya; sin él, cerrados, que es lo prudente:
  // abrir un botón que el servidor va a rechazar es peor que hacer esperar.
  habilitar(Boolean(turnoDe) && turnoDe === yo);
  marcarTurno(turnoDe ?? null, numeroActual);
  pintarPoder();

  /**
   * B7 — lo que el servidor cuenta del heroe propio: sus acciones al empezar
   * su turno (`partida.turno.cambiado`) o al releer la partida, y su poder
   * tras cada accion. Lo que no trae se queda como estaba.
   *
   * @param {{acciones?: Array<object>, poderActual?: number|null, poderMaximo?: number|null}} fuente
   */
  function actualizarEstadoPropio(fuente) {
    if (Array.isArray(fuente?.acciones)) {
      estadoPropio.acciones = fuente.acciones;
    }
    if (Number.isFinite(fuente?.poderActual)) {
      estadoPropio.poderActual = fuente.poderActual;
    }
    if (Number.isFinite(fuente?.poderMaximo)) {
      estadoPropio.poderMaximo = fuente.poderMaximo;
    }
    if (esMapaDeRecargas(fuente?.recargas)) {
      estadoPropio.recargas = fuente.recargas;
    }
    pintarPoder();
  }

  /** Quien cae deja de ser objetivo; una reanimacion lo devuelve (B7). */
  function anotarVida(idJugador, vidaActual) {
    if (!idJugador || !Number.isFinite(vidaActual)) {
      return;
    }
    const puesto = zonaCampo?.querySelector(`[data-puesto="${idJugador}"]`);
    if (vidaActual === 0) {
      caidos.add(idJugador);
      puesto?.classList.add('campo__puesto--caido');
    } else {
      caidos.delete(idJugador);
      puesto?.classList.remove('campo__puesto--caido');
    }
  }

  // UXC-2 · efectos activos que ya trae la partida (Participante.heroe.efectosActivos).
  for (const participante of participantes ?? []) {
    pintarEfectos(participante.jugador?.id, participante.heroe?.efectosActivos);
  }
  if (turnoDe && !partidaFinalizada) {
    const quien =
      turnoDe === yo ? 'te toca a ti.' : `${textoDelTurno(turnoDe, participantes, yo).texto}.`;
    anotar({
      texto: numeroActual
        ? `Combate en curso · Turno ${numeroActual}: ${quien}`
        : 'Combate en curso.',
      tono: 'sistema',
      icono: 'espada',
    });
  }
  if (recienEmpezada && !partidaFinalizada && Number.isInteger(numeroActual) && numeroActual > 1) {
    contarLoQueYaPaso();
  }
  if (partidaFinalizada) {
    cerrarControles();
    pintarDesenlace(partidaFinalizada, {
      nota: 'Los créditos de la partida quedan en el historial de Mi cuenta.',
    });
  }

  /**
   * Lo que paso entre que empezo la partida y que esta pantalla la monto: el
   * orden de turnos es el de `participantes` (1.7.0), asi que el primero es
   * quien abrio; y todos empiezan a vida completa, asi que lo que falte de
   * vida es lo que dejaron esos turnos. Nada se deduce de otra cosa.
   */
  function contarLoQueYaPaso() {
    const primero = idDeJugador((participantes ?? [])[0]);
    if (primero && primero !== yo) {
      anotar({
        texto: `Turno 1: abrió ${nombreDe(primero, participantes, yo)}.`,
        tono: 'turno',
        icono: 'reloj',
      });
    }
    const cuando = numeroActual === 2 ? 'en el turno 1' : `en los turnos 1 a ${numeroActual - 1}`;
    for (const participante of participantes ?? []) {
      const vida = participante?.heroe?.vidaActual;
      const maxima = participante?.heroe?.vidaMaxima;
      if (Number.isFinite(vida) && Number.isFinite(maxima) && vida < maxima) {
        anotar({
          texto: `${nombreDe(idDeJugador(participante), participantes, yo)} recibió ${maxima - vida} de daño ${cuando} (${vida}/${maxima}).`,
          tono: 'dano',
          icono: 'espada',
        });
      }
    }
  }

  /**
   * Efectos activos de un participante, sobre su barra (compactos) y bajo su
   * heroe en el campo. Sin efectos, se quitan los que hubiera.
   */
  function pintarEfectos(idJugador, efectos) {
    if (!idJugador) {
      return;
    }
    const lista = Array.isArray(efectos) ? efectos.filter((e) => e?.codigo || e?.nombre) : [];
    const barra = zonaVidas?.querySelector(`[data-jugador="${idJugador}"]`);
    const puesto = zonaCampo?.querySelector(`[data-puesto="${idJugador}"]`);
    for (const [anfitrion, compacto] of [
      [barra, true],
      [puesto, false],
    ]) {
      if (!anfitrion) {
        continue;
      }
      anfitrion.querySelector('.efectos')?.remove();
      if (lista.length > 0) {
        anfitrion.append(
          h('span', {
            clase: 'efectos',
            datos: { efectos: '' },
            hijos: lista.map((efecto) => chipDeEfecto(efecto, { compacto })),
          }),
        );
      }
    }
  }

  /** La cifra del golpe sobre el heroe en el campo; se va sola. */
  function mostrarImpacto({ idJugador, cifra, etiqueta, tono }) {
    const puesto = zonaCampo?.querySelector(`[data-puesto="${idJugador}"]`);
    if (!puesto) {
      return;
    }
    puesto.querySelector('.impacto')?.remove();
    const impacto = impactoEnCampo({ cifra, etiqueta, tono });
    puesto.append(impacto);
    setTimeout(() => impacto.remove(), 2400);
  }

  /**
   * UXC-2 · las acciones del heroe propio (§6.1.2) y su poder (§6.1.1), tal
   * como las cuenta el catalogo. Llegan despues de montar: salen de tres
   * servicios (ver `comun/heroe-propio.js`) y el ataque basico no las espera.
   *
   * Desde B7 lo que se puede jugar lo dice el servidor (`acciones` del heroe
   * en la partida); el catalogo pone la descripcion de cada una y, si la
   * partida aun no trae poder, su maximo.
   *
   * @param {{acciones?: Array<{nombre: string, costo?: string, efecto?: string}>,
   *   poderMaximo?: number|null, motivo?: string|null}} heroe
   */
  function mostrarHeroe({ acciones = [], poderMaximo = null, motivo = null } = {}) {
    catalogo = {
      acciones: Array.isArray(acciones) ? acciones : [],
      poderMaximo: Number.isFinite(poderMaximo) ? poderMaximo : null,
      motivo,
    };
    pintarPoder();
    pintarEspeciales(turnoPropio && !sinCanal && !terminado);
  }

  /** Las especiales del catalogo, cerradas con su motivo: sin estado del servidor. */
  function pintarEspecialesDelCatalogo() {
    const { acciones, motivo } = catalogo;
    vaciar(zonaEspeciales);
    const idMotivo = zonaMotivoEspeciales?.id || null;
    for (const accion of acciones) {
      const coste = puntosDePoder(accion.costo);
      zonaEspeciales.append(
        accionDeCombate({
          nombre: accion.nombre,
          icono: 'rayo',
          coste: coste ?? undefined,
          // El coste con su numero si el catalogo lo trae («2 puntos de
          // poder»); si no («Todos los puntos de poder»), el texto tal cual.
          insignias:
            coste !== null
              ? [
                  { icono: 'rayo', texto: String(coste), etiqueta: `${coste} de poder` },
                  { icono: 'reloj', texto: '1', etiqueta: 'Un turno de carga' },
                ]
              : [],
          detalle: coste !== null ? null : (accion.costo ?? null),
          efecto: [accion.efecto ? `Efecto: ${accion.efecto}` : null, 'Un turno de carga']
            .filter(Boolean)
            .join('. '),
          impedimento: MOTIVO_ESPECIALES,
          causa: 'turno',
          especial: true,
          describidaPor: idMotivo,
        }),
      );
    }
    if (zonaMotivoEspeciales) {
      zonaMotivoEspeciales.textContent =
        acciones.length > 0
          ? MOTIVO_ESPECIALES
          : (motivo ?? 'No se conocen las acciones de tu héroe.');
    }
  }

  return {
    /**
     * Un rechazo llegado por la cola privada del jugador (`errorDeCanal`).
     *
     * Si es una seccion degradada (HU-DIS-003: el motor no responde), se
     * pinta el componente comun sobre los controles, que siguen vivos porque
     * la accion no se aplico y el turno sigue siendo del jugador; Reintentar
     * vuelve a mandar la misma accion. Cualquier otro rechazo va a la zona
     * de rechazo con el texto del servicio. Devuelve si lo gestiono.
     *
     * @param {object} problema problem detail del contrato
     * @returns {boolean}
     */
    rechazar(problema) {
      if (esSeccionDegradada(problema)) {
        if (!zonaDegradacion) {
          return false;
        }
        pintarSeccionDegradada(zonaDegradacion, problema, {
          alReintentar: () => {
            if (ultimaAccion) {
              atacar(ultimaAccion);
            } else {
              limpiarSeccionDegradada(zonaDegradacion);
            }
          },
        });
        return true;
      }
      if (!zonaRechazo) {
        return false;
      }
      zonaRechazo.textContent = textoDelServidor(
        problema,
        problema?.status,
        'La acción fue rechazada.',
      );
      zonaRechazo.hidden = false;
      return true;
    },

    mostrarHeroe,
    anotar,

    /**
     * Sin canal no se juega el turno (riesgo #7: nunca fallar en silencio).
     * `sincronizar` los vuelve a abrir cuando el canal vuelve.
     *
     * @param {string} [motivo]
     */
    bloquear(motivo = 'Sin conexión en tiempo real: se reintentará solo.') {
      if (!sinCanal && !terminado) {
        anotar({
          texto: 'Se perdió la conexión: reintentando. Los controles vuelven al recuperarla.',
          tono: 'sistema',
          icono: 'alerta',
        });
      }
      sinCanal = true;
      habilitar(false);
      for (const boton of zona?.querySelectorAll('[data-atacar]') ?? []) {
        boton.title = motivo;
        boton.setAttribute('aria-label', `${boton.dataset.accion ?? 'Atacar'}. ${motivo}`);
      }
    },

    /**
     * Reconciliacion al volver el canal (riesgo #7): la partida releida manda.
     *
     * @param {{turnoActual?: {idJugador: string, numeroTurno?: number}, estado?: string}} partida
     */
    sincronizar(partida) {
      sinCanal = false;
      if (partida?.estado === 'FINALIZADA') {
        // Termino mientras no habia canal: el aviso de fin se perdio. Se dice,
        // se cierran los controles y, si el final no se conocia, se pinta con
        // lo que trae la partida (ganadores desde 1.7.0; los creditos no).
        if (!terminado) {
          cerrarControles();
          anotar({
            texto:
              'La partida terminó mientras se recuperaba la conexión. El resultado y los créditos quedan en el historial de Mi cuenta.',
            tono: 'fin',
            icono: 'alerta',
          });
          if (desenlaceConocido === null && Array.isArray(partida.ganadores)) {
            pintarDesenlace(partida, {
              nota: 'Los créditos de la partida quedan en el historial de Mi cuenta.',
            });
          }
        }
        return;
      }
      // Ya se sabe que termino (llego el aviso): una lectura atrasada que aun
      // diga EN_CURSO no reabre nada.
      if (terminado) {
        return;
      }
      turnoActual = partida?.turnoActual?.idJugador ?? turnoActual;
      numeroActual = partida?.turnoActual?.numeroTurno ?? numeroActual;
      // B7 — la partida releida trae el estado de combate de cada heroe: las
      // acciones y el poder propios, y quien cayo mientras no habia canal.
      const heroePropio = (partida?.participantes ?? []).find((p) => idDeJugador(p) === yo)?.heroe;
      if (heroePropio) {
        actualizarEstadoPropio(heroePropio);
      }
      for (const participante of partida?.participantes ?? []) {
        anotarVida(idDeJugador(participante), participante?.heroe?.vidaActual);
      }
      habilitar(turnoActual === yo);
      marcarTurno(turnoActual, numeroActual);
      anotar({
        texto: 'Conexión recuperada: la partida está al día.',
        tono: 'sistema',
        icono: 'check',
      });
    },

    recibir(mensaje) {
      if (registro.yaVisto(mensaje)) {
        return;
      }
      if (mensaje?.tipo === ACCION_RESUELTA && mensaje.idPartida === idPartida) {
        // El motor volvio a contestar: la degradacion, si la habia, ya paso.
        limpiarSeccionDegradada(zonaDegradacion);
        // UXC-2 — que paso, con palabras (registro) y sobre el campo (cifra).
        const { lineas, impactos } = narrarAccion(mensaje, participantes, yo);
        for (const linea of lineas) {
          anotar(linea);
        }
        for (const impacto of impactos) {
          mostrarImpacto(impacto);
        }
        for (const afectado of mensaje.afectados ?? []) {
          if (Array.isArray(afectado.efectosActivos)) {
            pintarEfectos(afectado.idJugador, afectado.efectosActivos);
          }
          anotarVida(afectado.idJugador, afectado.vidaActual);
          // B7 — el ejecutor gasta poder, y algunas acciones se lo quitan a otro.
          if (afectado.idJugador === yo) {
            const antes = estadoPropio.poderActual;
            actualizarEstadoPropio(afectado);
            anotarPoder(antes, mensaje.idEjecutor === yo ? 'gasto' : 'ajeno');
          }
        }
        habilitar(turnoPropio);
        return;
      }
      if (mensaje?.tipo === TURNO_CAMBIADO && mensaje.idPartida === idPartida) {
        turnoActual = mensaje.idJugador;
        numeroActual = Number.isInteger(mensaje.numeroTurno) ? mensaje.numeroTurno : numeroActual;
        // B7 — el aviso trae el estado de quien juega ahora, ya con el +2 de
        // poder y los efectos del inicio de su turno: si es el propio, sus
        // acciones jugables. Una especial a medio elegir no pasa de turno.
        const poderAntes = estadoPropio.poderActual;
        if (mensaje.idJugador === yo) {
          actualizarEstadoPropio(mensaje);
        }
        if (Array.isArray(mensaje.efectosActivos)) {
          pintarEfectos(mensaje.idJugador, mensaje.efectosActivos);
        }
        accionPendiente = null;
        habilitar(mensaje.idJugador === yo);
        marcarTurno(mensaje.idJugador, numeroActual);
        anotar(narrarTurno(mensaje, participantes, yo));
        if (mensaje.idJugador === yo) {
          anotarPoder(poderAntes, 'turno');
        }
        return;
      }
      if (mensaje?.tipo === PARTICIPANTE_RENDIDO && mensaje.idPartida === idPartida) {
        // 1.10.0 — alguien salio del combate: su heroe queda fuera, sin golpe
        // ni ejecutor. Quien gana, si se acaba, lo dice `partida.finalizada`,
        // que llega despues: aqui no se decide nada.
        anotarVida(mensaje.idJugador, 0);
        anotar({
          texto:
            mensaje.idJugador === yo
              ? 'Abandonaste la batalla: cuenta como derrota.'
              : `${nombreDe(mensaje.idJugador, participantes, yo)} abandonó la batalla.`,
          tono: 'sistema',
          icono: 'salir',
        });
        if (mensaje.idJugador === yo && !terminado) {
          cerrarControles();
        }
        return;
      }
      if (mensaje?.tipo === PARTIDA_FINALIZADA && mensaje.idPartida === idPartida) {
        if (!terminado) {
          cerrarControles();
        }
        pintarDesenlace(mensaje);
      }
    },
  };
}
