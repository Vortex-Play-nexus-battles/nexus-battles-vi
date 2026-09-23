/**
 * Vista de sala en batalla — HU-SAL-005 (RF-JUE-009).
 *
 * Su unico trabajo es montar el panel de vidas sobre el marcado de la pagina y
 * decir la verdad sobre el estado del canal en tiempo real.
 *
 * De donde salen los participantes: de `GET /partidas/{id}` (HU-SAL-004, ya
 * publicado), de `montarSalaBatalla`, o del bloque JSON que el servidor deje
 * incrustado en la pagina. Lo que no se hace es inventar datos de ejemplo: sin
 * partida cargada la vista muestra su estado vacio, que es lo honesto.
 *
 * El indicador de conexion refleja lo que hay de verdad. Mientras no se le
 * pase un `suscribir`, la vista dice «sin conexion» en vez de aparentar que
 * el canal esta vivo.
 *
 * @module sala-batalla
 */

import { montarPanelVidas } from './panel-vidas.js';
import { pintarCampo } from './campo.js';
import { mostrarPresentacion } from '../../comun/ui/juego/presentacion.js';

/**
 * Destino del canal `partidaEstado` del AsyncAPI
 * (`contracts/websocket/salas-partidas.yaml`): por aqui llega
 * `partida.accion.resuelta`.
 *
 * @param {string} idPartida
 * @returns {string}
 */
export function destinoDePartida(idPartida) {
  return `/tema/partidas/${idPartida}`;
}

/**
 * Construye el `suscribir` que espera la vista a partir de un cliente STOMP ya
 * conectado (el transporte del servicio, `cliente-chat.js`: CONNECT con el JWT,
 * SUBSCRIBE, MESSAGE). La vista sigue sin saber que existe STOMP: solo recibe
 * una funcion que le entrega mensajes del canal de su partida.
 *
 * @param {{suscribir: (destino: string, alRecibir: Function) => unknown}} cliente
 * @param {string} idPartida
 * @returns {(alRecibir: (evento: object) => void) => void}
 */
export function suscripcionDePartida(cliente, idPartida) {
  return (alRecibir) => cliente.suscribir(destinoDePartida(idPartida), alRecibir);
}

/**
 * Lee el estado que el servidor haya incrustado en la pagina.
 *
 * Este es el punto por el que entrara la partida cuando exista el endpoint:
 * un `<script type="application/json" data-estado-inicial>` con la forma del
 * esquema del contrato. Si no hay bloque, no hay partida.
 *
 * @param {ParentNode} [raiz=document]
 * @returns {{idPartida: string, participantes: Array<object>} | null}
 */
export function leerEstadoInicial(raiz = document) {
  const bloque = raiz.querySelector('script[data-estado-inicial]');
  if (!bloque || !bloque.textContent.trim()) {
    return null;
  }

  return JSON.parse(bloque.textContent);
}

/**
 * Traduce la partida de `GET /partidas/{idPartida}` a lo que espera el panel de
 * vidas, que habla el esquema `Participante` del AsyncAPI.
 *
 * Son dos formas distintas del mismo concepto y la diferencia no es cosmetica:
 * en REST `jugador` es un identificador y `heroe` puede venir nulo; el panel
 * necesita `jugador.id` y un heroe con vida actual y maxima para poder dibujar
 * una barra.
 *
 * **Los participantes sin heroe se descartan.** Este servicio no conoce el
 * heroe equipado —lo tiene el inventario, y la verificacion de HU-SAL-003
 * todavia no es obligatoria en el ingreso (SCRUM-1074)—, asi que no hay ninguna
 * vida que pintar para ellos. Inventar un 100/100 llenaria la pantalla de
 * barras que no corresponden a nada.
 *
 * @param {{participantes?: Array<object>}} partida esquema `Partida` del OpenAPI
 * @returns {Array<object>} participantes con heroe conocido, en orden de turno
 */
export function participantesParaElPanel(partida) {
  return (partida?.participantes ?? [])
    .filter((participante) => participante?.heroe)
    .map((participante) => ({
      jugador: { id: participante.jugador },
      heroe: participante.heroe,
      esIA: Boolean(participante.esIA),
      equipo: participante.equipo ?? null,
    }));
}

/**
 * Pinta el indicador de estado del canal.
 *
 * @param {HTMLElement | null} zona
 * @param {boolean} hayCanal
 */
function pintarConexion(zona, hayCanal) {
  if (!zona) {
    return;
  }

  zona.className = hayCanal ? 'conexion conexion--estable' : 'conexion conexion--sin-conexion';
  zona.textContent = hayCanal
    ? 'Canal en tiempo real conectado'
    : 'Canal en tiempo real no conectado';
}

/**
 * Cambia el texto del estado vacio sin tocar su titulo.
 *
 * @param {HTMLElement | null} zona
 * @param {string} texto
 */
function explicarVacio(zona, texto) {
  const detalle = zona?.querySelector('.t-meta');
  if (detalle) {
    detalle.textContent = texto;
  }
}

/**
 * Monta la vista.
 *
 * Acepta la partida en las dos formas que puede llegar: `partida` tal como la
 * devuelve `GET /partidas/{idPartida}`, o `participantes` ya en el esquema del
 * panel. La primera gana cuando vienen las dos.
 *
 * @param {ParentNode} raiz
 * @param {object} [opciones]
 * @param {object} [opciones.partida] respuesta de `GET /partidas/{idPartida}`
 * @param {string} [opciones.idPartida]
 * @param {Array<object>} [opciones.participantes]
 * @param {(alRecibir: (evento: object) => void) => void} [opciones.suscribir]
 *   Transporte del canal de la partida. Se inyecta desde fuera para que el dia
 *   que exista STOMP no haya que rehacer nada de aqui.
 */
export function montarSalaBatalla(
  raiz,
  { partida, idPartida, participantes, suscribir, yo, turnoActual = null, presentar = false } = {},
) {
  const zonaConexion = raiz.querySelector('[data-zona="conexion"]');
  const zonaSinPartida = raiz.querySelector('[data-zona="sin-partida"]');
  const panel = raiz.querySelector('[data-zona="panel"]');
  const vidas = raiz.querySelector('[data-zona="vidas"]');
  const campo = raiz.querySelector('[data-zona="campo"]');
  const zonaPresentacion = raiz.querySelector('[data-zona="presentacion"]');

  pintarConexion(zonaConexion, typeof suscribir === 'function');

  const id = partida?.id ?? idPartida;
  const enPantalla = partida ? participantesParaElPanel(partida) : participantes;
  const hayPartida = Array.isArray(enPantalla) && enPantalla.length > 0;

  // La partida existe pero de ninguno de sus participantes se conoce el heroe.
  // Se dice, en vez de dejar un panel vacio que parece un fallo de carga.
  if (!hayPartida && partida?.participantes?.length) {
    explicarVacio(
      zonaSinPartida,
      `La partida ${partida.id} está en curso con ${partida.participantes.length} participantes, ` +
        'pero todavía no se conoce el héroe de ninguno: la verificación de héroe no es ' +
        'obligatoria al entrar a la sala. Sin héroe no hay vida que pintar.',
    );
  }

  if (zonaSinPartida) {
    zonaSinPartida.hidden = hayPartida;
  }
  if (panel) {
    panel.hidden = !hayPartida;
  }

  // UX-R3.4 — el campo de combate solo existe cuando hay combate.
  //
  // `.combate__campo` ocupa la franja `1fr` de la reja, que es la mayor parte
  // de la ventana (CA-01 pide mas del 80 % del alto util para el area de
  // juego). Sin partida cargada eso dejaba media pantalla de degradado vacio
  // con una tarjeta blanca huerfana debajo, cerca del borde inferior: la
  // pantalla mas importante del producto parecia rota.
  //
  // La marca la lleva la raiz y el resto lo decide el CSS, que es quien sabe
  // de tamaños. `aria-hidden` ya estaba en el campo: no cambia nada de lo que
  // oye un lector de pantalla.
  const marco = raiz.querySelector?.('[data-zona="combate"]') ?? raiz.closest?.('.combate');
  if (marco?.dataset) {
    marco.dataset.sinPartida = hayPartida ? 'no' : 'si';
  }

  if (!hayPartida) {
    return;
  }

  // HU-JUE-017 CA-01 y CA-03: el campo con los heroes colocados. Es lo que
  // ocupa mas del 80 % de la pantalla; la barra de vida y los controles van
  // alrededor, nunca encima. Si la vista no trae campo (o una prueba monta
  // solo el panel), no pasa nada: el resto sigue funcionando igual.
  if (campo) {
    pintarCampo(campo, enPantalla, yo ?? null);
  }

  if (!vidas) {
    return;
  }

  montarPanelVidas(vidas, { idPartida: id, participantes: enPantalla, suscribir });

  // HU-JUE-017 CA-04 · la presentacion de los heroes.
  //
  // `presentar` solo es true cuando se llega AQUI desde el aviso
  // `sala.partida.iniciada`, no al recargar una partida que ya estaba en
  // curso: entrar a mitad de combate y que te presenten a los heroes como si
  // empezara ahora seria mentir sobre el momento.
  //
  // Se cierra con una accion o con el primer aviso del canal, nunca con un
  // tiempo fijo. Si alguien tarda en leer, la presentacion espera.
  if (presentar && zonaPresentacion) {
    const cerrar = mostrarPresentacion(zonaPresentacion, {
      participantes: enPantalla,
      turnoActual,
      yo: yo ?? null,
    });
    if (typeof suscribir === 'function') {
      suscribir((aviso) => {
        // Cualquier aviso de la partida significa que el combate ya corre.
        if (aviso?.idPartida === id) {
          cerrar();
        }
      });
    }
  }
}
