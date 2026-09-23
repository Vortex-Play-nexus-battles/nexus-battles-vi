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
import { vaciar } from '../../comun/ui/dom.js';
import { accionDeCombate } from '../../comun/ui/juego/combate.js';
import { panelDeResultado } from '../../comun/ui/juego/resultado.js';

/** Destino `accionDelJugador` del AsyncAPI. Prefijo de envío `/app`. */
export function destinoDeAccion(idPartida) {
  return `/app/partidas/${idPartida}/acciones`;
}

/** Tipos que viajan por el canal de la partida, fijados por el contrato. */
export const ACCION_RESUELTA = 'partida.accion.resuelta';
export const TURNO_CAMBIADO = 'partida.turno.cambiado';
export const PARTIDA_FINALIZADA = 'partida.finalizada';

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
 * identidad se compone de lo que sí trae y no se repite: partida, ejecutor y la
 * vida resultante de cada afectado. Dos golpes distintos que dejaran a todos
 * exactamente igual serían indistinguibles, y aplicar el segundo tampoco
 * cambiaría nada: descartarlo es correcto.
 *
 * @returns {{yaVisto: (aviso: object) => boolean, olvidar: () => void}}
 */
export function registroDeAvisos() {
  const vistos = new Set();

  return {
    yaVisto(aviso) {
      const huella = huellaDe(aviso);
      if (huella === null) {
        return false;
      }
      if (vistos.has(huella)) {
        return true;
      }
      vistos.add(huella);
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
 * @returns {string|null}
 */
function huellaDe(aviso) {
  if (!aviso || typeof aviso.tipo !== 'string') {
    return null;
  }
  if (aviso.tipo === ACCION_RESUELTA) {
    const afectados = (aviso.afectados ?? [])
      .map((a) => `${a.idJugador}:${a.vidaActual}`)
      .join('|');
    return `${aviso.tipo}#${aviso.idPartida}#${aviso.idEjecutor}#${afectados}`;
  }
  if (aviso.tipo === TURNO_CAMBIADO) {
    // El número de turno sube siempre: identifica el aviso por sí solo.
    return `${aviso.tipo}#${aviso.idPartida}#${aviso.numeroTurno}`;
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
  return { texto: quien.esIA ? `Juega la máquina (${nombre})` : `Turno de ${nombre}`, mio: false };
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
 * @returns {{recibir: (aviso: object) => void, rechazar: (problema: object) => boolean}}
 */
export function montarControlesDeCombate(
  raiz,
  { idPartida, yo, participantes, turnoDe, alAtacar },
) {
  const zona = raiz.querySelector('[data-zona="acciones"]');
  const aviso = raiz.querySelector('[data-zona="resultado"]');
  // Indicador de turno y panel de vidas: el turno se dice con palabras y se
  // marca sobre la barra de quien juega.
  const zonaTurno = raiz.querySelector('[data-zona="turno"]');
  const zonaVidas = raiz.querySelector('[data-zona="vidas"]');
  // HU-DIS-003: hueco de «Seccion degradada» cuando el motor de combate no
  // responde; y la zona para los demas rechazos de la cola privada.
  const zonaDegradacion = raiz.querySelector('[data-zona="degradacion"]');
  const zonaRechazo = raiz.querySelector('[data-zona="rechazo"]');
  const registro = registroDeAvisos();
  /** La ultima accion enviada, para poder reintentarla tal cual. */
  let ultimaAccion = null;

  // Rivales: a uno mismo no se ataca, ni a un companero de equipo en el modo
  // cooperativo (HU-SAL-004): el servidor lo rechazaria, y ofrecer el boton
  // seria invitar al error.
  const miEquipo = (participantes ?? []).find((p) => p.jugador?.id === yo)?.equipo ?? null;
  const rivales = (participantes ?? []).filter(
    (p) => p.jugador?.id !== yo && (miEquipo === null || p.equipo !== miEquipo),
  );

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
        atacar({ idObjetivo: boton.dataset.atacar, codigoAccion: 'ATAQUE_BASICO' });
      }
    });
  }

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
   */
  function habilitar(esMiTurno) {
    for (const boton of zona?.querySelectorAll('[data-atacar]') ?? []) {
      boton.disabled = !esMiTurno;
      boton.classList.toggle('accion-combate--fuera-de-turno', !esMiTurno);
      const nombre = boton.dataset.accion ?? 'tu rival';
      if (esMiTurno) {
        boton.title = `Atacar a ${nombre}`;
        boton.setAttribute('aria-label', `Atacar a ${nombre}`);
      } else {
        boton.title = 'No es tu turno';
        boton.setAttribute('aria-label', `${nombre}. No es tu turno`);
      }
    }
  }

  /**
   * Deja dicho de quién es el turno: en el indicador (región viva, así que un
   * lector de pantalla lo anuncia solo) y sobre la barra de quien juega.
   *
   * @param {string|null} idJugador
   */
  function marcarTurno(idJugador) {
    const turno = textoDelTurno(idJugador, participantes, yo);
    if (zonaTurno) {
      zonaTurno.textContent = turno.texto;
      zonaTurno.hidden = turno.texto === '';
      zonaTurno.dataset.mio = String(turno.mio);
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

  // Con `turnoDe` conocido se decide ya; sin él, cerrados, que es lo prudente:
  // abrir un botón que el servidor va a rechazar es peor que hacer esperar.
  habilitar(Boolean(turnoDe) && turnoDe === yo);
  marcarTurno(turnoDe ?? null);

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
      zonaRechazo.textContent = problema?.detail ?? problema?.title ?? 'La acción fue rechazada.';
      zonaRechazo.hidden = false;
      return true;
    },

    recibir(mensaje) {
      if (registro.yaVisto(mensaje)) {
        return;
      }
      if (mensaje?.tipo === ACCION_RESUELTA && mensaje.idPartida === idPartida) {
        // El motor volvio a contestar: la degradacion, si la habia, ya paso.
        limpiarSeccionDegradada(zonaDegradacion);
        return;
      }
      if (mensaje?.tipo === TURNO_CAMBIADO && mensaje.idPartida === idPartida) {
        habilitar(mensaje.idJugador === yo);
        marcarTurno(mensaje.idJugador);
        return;
      }
      if (mensaje?.tipo === PARTIDA_FINALIZADA && mensaje.idPartida === idPartida) {
        habilitar(false);
        // Se acabo: ya no es el turno de nadie. Dejar la marca puesta haria
        // creer que la partida sigue.
        marcarTurno(null);
        if (zona) {
          zona.hidden = true;
        }
        if (aviso) {
          // UX-R2.3 (HU-JUE-017 CA-04: «vistas de alto impacto al inicio y al
          // final»). Antes el final de la partida era un parrafo del mismo
          // tamano que el resto de la pantalla. `--t-display-tam` estaba en
          // `tokens.css` reservada para esto desde el principio.
          const recompensa = recompensaDe(mensaje, yo);
          vaciar(aviso);
          aviso.append(
            panelDeResultado({
              victoria: gano(mensaje, yo, miEquipo),
              detalle: textoDelResultado(mensaje, yo, miEquipo),
              creditos: recompensa?.creditos ?? null,
            }),
          );
          aviso.hidden = false;
        }
      }
    },
  };
}
