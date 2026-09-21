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
    // Una partida termina una sola vez.
    return `${aviso.tipo}#${aviso.idPartida}`;
  }
  return null;
}

/**
 * Texto del resultado, desde el punto de vista de quien mira.
 *
 * Sin ganadores es empate: el servidor lo deja vacío cuando nadie quedó en pie,
 * y decirlo es más honesto que inventar un vencedor.
 *
 * @param {{ganadores?: string[]}} aviso
 * @param {string} yo identificador del jugador que mira
 * @returns {string}
 */
export function textoDelResultado(aviso, yo) {
  const ganadores = aviso?.ganadores ?? [];
  if (ganadores.length === 0) {
    return 'Combate terminado en empate.';
  }
  return ganadores.includes(yo) ? 'Has ganado el combate.' : 'Has perdido el combate.';
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
 * @returns {{recibir: (aviso: object) => void}}
 */
export function montarControlesDeCombate(
  raiz,
  { idPartida, yo, participantes, turnoDe, alAtacar },
) {
  const zona = raiz.querySelector('[data-zona="acciones"]');
  const aviso = raiz.querySelector('[data-zona="resultado"]');
  const registro = registroDeAvisos();
  const doc = raiz.ownerDocument ?? document;

  /** Rivales: a uno mismo no se ataca. */
  const rivales = (participantes ?? []).filter((p) => p.jugador?.id !== yo);

  if (zona) {
    zona.innerHTML = '';
    for (const rival of rivales) {
      const boton = doc.createElement('button');
      boton.type = 'button';
      boton.className = 'boton boton--primario';
      boton.dataset.atacar = rival.jugador.id;
      boton.textContent = `Atacar a ${rival.heroe?.nombre ?? 'tu rival'}`;
      zona.append(boton);
    }
    zona.addEventListener('click', (evento) => {
      const boton = evento.target.closest('[data-atacar]');
      if (boton && !boton.disabled) {
        alAtacar({ idObjetivo: boton.dataset.atacar, codigoAccion: 'ATAQUE_BASICO' });
      }
    });
  }

  /** Solo se puede atacar en el turno propio. */
  function habilitar(esMiTurno) {
    for (const boton of zona?.querySelectorAll('[data-atacar]') ?? []) {
      boton.disabled = !esMiTurno;
    }
  }

  // Con `turnoDe` conocido se decide ya; sin él, cerrados, que es lo prudente:
  // abrir un botón que el servidor va a rechazar es peor que hacer esperar.
  habilitar(Boolean(turnoDe) && turnoDe === yo);

  return {
    recibir(mensaje) {
      if (registro.yaVisto(mensaje)) {
        return;
      }
      if (mensaje?.tipo === TURNO_CAMBIADO && mensaje.idPartida === idPartida) {
        habilitar(mensaje.idJugador === yo);
        return;
      }
      if (mensaje?.tipo === PARTIDA_FINALIZADA && mensaje.idPartida === idPartida) {
        habilitar(false);
        if (zona) {
          zona.hidden = true;
        }
        if (aviso) {
          aviso.textContent = textoDelResultado(mensaje, yo);
          aviso.hidden = false;
        }
      }
    },
  };
}
