/**
 * HU-JUE-015 — Vista del chat de sala y de vista general.
 *
 * Es la misma vista para los dos lugares: cambia el canal, no las reglas.
 * Con `?sala={id}` en la URL es el chat de esa sala; sin el, es el general.
 *
 * Flujo: historial al suscribirse (una respuesta), mensajes en vivo por el
 * tema del canal, y errores por la cola privada del jugador. Nunca se decide
 * por el texto del error: por su `type` (y, si no se conoce, por su estado,
 * tabla 4 del mapeo de errores).
 *
 * El token de acceso se lee de sessionStorage con la misma clave que escribe
 * el login de cuentas (`nexus.token`, ver `cuentas/login.js`) y que ya usan
 * `batallas.html` y `pujas-api.js`. Antes se leia `nexus.tokenAcceso`, una
 * clave que ningun login escribe: el chat conectaba siempre sin token y el
 * servidor lo rechazaba en el CONNECT.
 *
 * UXC-6 — lo que cambió:
 *
 *   - los mensajes son los globos compartidos (`comun/ui/conversacion/`): lo
 *     tuyo dice «Tú» y va a la derecha, lo de otro jugador lleva su apodo e
 *     inicial, y lo que cuenta la propia vista dice «Sistema». No solo color;
 *   - el canal se reconecta solo (`canal-reconectable.js`) y la píldora dice
 *     en qué punto va (`comun/ui/reconexion.js`, la misma del combate). Al
 *     volver, el historial se vuelve a pedir y lo perdido se intercala;
 *   - el silencio por sanción ya no es un aviso que se va: el campo se
 *     sustituye por el motivo y el enlace a tus sanciones;
 *   - un mensaje que el filtro rechaza vuelve al campo, para corregirlo.
 *
 * La pestaña «Mensajes privados» es `mensajes-privados.js`.
 */

import { conectarChat, ErrorDeCanal } from './cliente-chat.js';
import { urlDelCanal } from './canal-sala.js';
import { baseDeApi } from '../../comun/base-api.js';
import { canalReconectable } from '../../comun/canal-reconectable.js';
import { usuarioIdDeSesion } from '../../comun/identidad.js';
import { RUTAS, resolver } from '../../comun/sesion.js';
import { limpiarAviso, pintarAviso } from '../../comun/ui/aviso.js';
import { montarPestanas } from '../../comun/ui/pestanas.js';
import { pintarEstadoDelCanal } from '../../comun/ui/reconexion.js';
import {
  estadoDeCarga,
  estadoDeError,
  estadoVacio,
  pintarEstado,
} from '../../comun/ui/estado-vista.js';
import { burbujaDeMensaje } from '../../comun/ui/conversacion/mensaje.js';
import { hiloDeConversacion } from '../../comun/ui/conversacion/hilo.js';
import { prepararRedactor } from '../../comun/ui/conversacion/redactor.js';
import { montarMensajesPrivados } from './mensajes-privados.js';
import { fuenteDeMensajes } from './fuente-mensajes.js';

export const CLAVE_TOKEN = 'nexus.token';
export const COLA_DE_ERRORES = '/usuario/cola/salas';

/** `type` de los errores del chat (`services/.../salaspartidas/chat/*.java`). */
const ERRORES_DEL_CHAT = Object.freeze({
  SILENCIADO: 'https://nexusbattles.local/errores/jugador-silenciado',
  BLOQUEADO: 'https://nexusbattles.local/errores/contenido-bloqueado',
  SIN_FILTRO: 'https://nexusbattles.local/errores/filtro-no-disponible',
  INVALIDO: 'https://nexusbattles.local/errores/mensaje-invalido',
});

/** Destinos del contrato AsyncAPI para el canal elegido. */
export function destinosDe(canal) {
  if (canal?.idSala) {
    return {
      vivo: `/tema/salas/${canal.idSala}/chat`,
      historial: `/app/salas/${canal.idSala}/chat/historial`,
      envio: `/app/salas/${canal.idSala}/chat`,
    };
  }
  return {
    vivo: '/tema/chat/general',
    historial: '/app/chat/general/historial',
    envio: '/app/chat/general',
  };
}

/** `?sala=<uuid>` es el chat de esa sala; sin parametro, el general. */
export function canalDesdeUrl(busqueda) {
  const idSala = new URLSearchParams(busqueda).get('sala');
  return idSala ? { idSala } : {};
}

/** Codigo HTTP -> variante del componente Aviso (tabla 4 del mapeo). */
export function tonoPara(estado) {
  if (estado >= 500) {
    return 'error';
  }
  if (estado === 404) {
    return 'info';
  }
  return 'advertencia';
}

/**
 * Qué hace la vista con un error de la cola privada.
 *
 * Por `type`, con palabras del producto: el texto del servidor se usa solo
 * para un error que la vista no conoce.
 *
 * @param {object} problema problem details (RFC 7807)
 * @returns {{bloquear?: {titulo: string, detalle: string, enlace: {texto: string, href: string}},
 *   aviso?: {tono: string, titulo: string, detalle: string}, devolverTexto?: boolean}}
 */
export function reaccionAlError(problema) {
  const error = new ErrorDeCanal(problema);
  switch (error.tipo) {
    case ERRORES_DEL_CHAT.SILENCIADO:
      return {
        bloquear: {
          titulo: 'Tienes un silencio activo',
          detalle: 'Mientras dure la sanción, tus mensajes no salen al chat.',
          enlace: { texto: 'Ver mis sanciones', href: resolver(RUTAS.misSanciones) },
        },
        devolverTexto: true,
      };
    case ERRORES_DEL_CHAT.BLOQUEADO:
      return {
        aviso: {
          tono: 'advertencia',
          titulo: 'Tu mensaje no salió',
          detalle:
            'Tiene palabras que no están permitidas en el chat. Te lo devolvemos al campo para que lo cambies.',
        },
        devolverTexto: true,
      };
    case ERRORES_DEL_CHAT.SIN_FILTRO:
      return {
        aviso: {
          tono: 'error',
          titulo: 'No pudimos revisar tu mensaje',
          detalle:
            'El filtro de contenido no respondió y el mensaje no salió. Sigue en el campo: envíalo otra vez en un momento.',
        },
        devolverTexto: true,
      };
    case ERRORES_DEL_CHAT.INVALIDO:
      return {
        aviso: {
          tono: 'advertencia',
          titulo: 'Revisa el mensaje',
          detalle: 'Un mensaje lleva entre 1 y 500 caracteres.',
        },
        devolverTexto: true,
      };
    default:
      return {
        aviso: { tono: tonoPara(error.estado), titulo: error.titulo, detalle: error.detalle },
      };
  }
}

/**
 * Un mensaje del contrato (mensajeDeChat) como elemento de la lista.
 *
 * @param {object} mensaje
 * @param {{miId?: string|null}} [opciones]
 * @returns {HTMLLIElement}
 */
export function pintarMensaje(mensaje, { miId = null } = {}) {
  return burbujaDeMensaje(mensaje, { miId });
}

function leerLogro(formulario) {
  const mision = formulario.elements.logroMision?.value.trim();
  const titulo = formulario.elements.logroTitulo?.value.trim();
  return mision && titulo ? { mision, titulo } : null;
}

/** El canal del chat vive donde la API (`<meta name="nexus-api-base">`) o en este origen. */
function urlDelCanalDelChat() {
  return urlDelCanal({ base: baseDeApi() });
}

/**
 * Monta la conversación sobre su raiz. Devuelve el canal conectado, o null si
 * no hubo conexion (sin token, o el servidor la rechazo).
 *
 * @param {HTMLElement} raiz contenedor con [data-zona=mensajes|sin-mensajes|conexion], el form y su [data-zona=aviso]
 * @param {{canal: {idSala?: string}, token: string|null, conectar?: Function, url?: string,
 *   miId?: string|null, esperas?: readonly number[], reloj?: object}} opciones
 */
export async function montarChat(
  raiz,
  {
    canal,
    token,
    conectar = conectarChat,
    url = urlDelCanalDelChat(),
    miId = null,
    esperas,
    reloj,
  },
) {
  const lista = raiz.querySelector('[data-zona="mensajes"]');
  const zonaSinMensajes = raiz.querySelector('[data-zona="sin-mensajes"]');
  const indicador = raiz.querySelector('[data-zona="conexion"]');
  const formulario = raiz.querySelector('form');
  const destinos = destinosDe(canal);

  let conectado = false;
  let historialRecibido = false;

  /** Lo que se ve cuando no hay mensajes depende de si hay canal. */
  function repasarSilencio(cantidad) {
    if (!zonaSinMensajes) {
      return;
    }
    if (cantidad > 0) {
      zonaSinMensajes.replaceChildren();
      zonaSinMensajes.hidden = true;
      return;
    }
    pintarEstado(
      zonaSinMensajes,
      conectado
        ? estadoVacio({
            titulo: 'El canal está en silencio',
            detalle: 'Todavía no hay mensajes aquí. El primero puede ser el tuyo.',
          })
        : estadoVacio({
            titulo: 'Todavía no hay conversación que mostrar',
            detalle: 'Cuando vuelva la conexión verás aquí los últimos mensajes.',
          }),
    );
  }

  const hilo = hiloDeConversacion(lista, {
    miId,
    nombre: canal?.idSala ? 'Mensajes de la sala' : 'Mensajes del chat general',
    alCambiar: (cantidad) => {
      if (historialRecibido || cantidad > 0) {
        repasarSilencio(cantidad);
      }
    },
  });

  let canalVivo = null;
  let ultimoTexto = '';
  const zonaAviso = formulario.querySelector('[data-zona="aviso"]');
  const redactor = prepararRedactor(formulario, {
    alEnviar: (texto) => {
      limpiarAviso(zonaAviso);
      try {
        canalVivo.enviar(destinos.envio, { texto, logro: leerLogro(formulario) });
      } catch {
        pintarAviso(zonaAviso, {
          tono: 'advertencia',
          titulo: 'Tu mensaje no salió',
          detalle: 'Se cortó la conexión. Sigue en el campo: envíalo cuando vuelva.',
        });
        return false;
      }
      ultimoTexto = texto;
      return true;
    },
  });

  if (!token) {
    pintarEstadoDelCanal(indicador, { estado: 'sin-conexion' });
    redactor.bloquear({
      titulo: 'Inicia sesión para chatear',
      detalle: 'El chat necesita tu sesión iniciada para saber quién escribe.',
      enlace: { texto: 'Iniciar sesión', href: resolver(RUTAS.login) },
    });
    historialRecibido = true;
    repasarSilencio(0);
    return null;
  }

  function atenderError(problema) {
    const reaccion = reaccionAlError(problema);
    if (reaccion.devolverTexto) {
      redactor.restaurar(ultimoTexto);
    }
    if (reaccion.bloquear) {
      redactor.bloquear(reaccion.bloquear);
      return;
    }
    pintarAviso(zonaAviso, reaccion.aviso);
  }

  let caido = false;
  function alEstado(estado) {
    pintarEstadoDelCanal(indicador, { ...estado, alReintentar: () => canalVivo?.reintentar() });
    const perdido = estado.estado === 'reconectando' || estado.estado === 'sin-conexion';
    conectado = !perdido;
    redactor.esperarConexion(perdido);
    if (perdido && !caido) {
      caido = true;
      hilo.sistema(
        'Se cortó la conexión. Reintentando: lo que se escriba mientras tanto aparecerá al volver.',
        { tono: 'advertencia' },
      );
    } else if (estado.estado === 'reconectado' && caido) {
      caido = false;
      hilo.sistema('Conexión recuperada. La conversación está al día.', { tono: 'exito' });
    }
    if (estado.estado === 'sin-conexion') {
      hilo.sistema('No pudimos reconectar. Pulsa «Reintentar» junto al estado del canal.', {
        tono: 'advertencia',
      });
    }
  }

  async function abrir() {
    pintarEstadoDelCanal(indicador, { estado: 'conectando' });
    redactor.esperarConexion(true, 'Conectando con el chat…');
    if (zonaSinMensajes && !historialRecibido) {
      pintarEstado(
        zonaSinMensajes,
        estadoDeCarga({ filas: 3, etiqueta: 'Cargando la conversación…' }),
      );
    }
    try {
      return await canalReconectable({
        conectar: () => conectar({ url, token }),
        alEstado,
        esperas,
        reloj,
      });
    } catch {
      return null;
    }
  }

  canalVivo = await abrir();
  if (!canalVivo) {
    return sinCanal();
  }
  return enlazar(canalVivo);

  /** El primer intento falló: se dice donde iría la conversación, con reintento. */
  function sinCanal() {
    pintarEstadoDelCanal(indicador, { estado: 'sin-conexion' });
    redactor.esperarConexion(true, 'Sin conexión: tu texto no se pierde.');
    const titulo = 'No pudimos abrir el chat';
    const detalle = 'El canal no respondió. Los mensajes no llegan hasta que vuelva.';
    if (zonaSinMensajes) {
      pintarEstado(
        zonaSinMensajes,
        estadoDeError({ titulo, detalle, alReintentar: () => reintentarDesdeCero() }),
      );
    } else {
      pintarAviso(zonaAviso, {
        tono: 'error',
        titulo,
        detalle,
        accion: {
          texto: 'Reintentar',
          nombre: 'reintentar',
          alPulsar: () => reintentarDesdeCero(),
        },
      });
    }
    return null;
  }

  /** «Reintentar» tras un primer intento fallido: otra conexión, sin recargar. */
  async function reintentarDesdeCero() {
    limpiarAviso(zonaAviso);
    const nuevo = await abrir();
    if (!nuevo) {
      sinCanal();
      return;
    }
    adoptar(nuevo);
  }

  /** Se cambia desde una función: así `require-atomic-updates` no ve carrera. */
  function adoptar(nuevo) {
    canalVivo = nuevo;
    enlazar(nuevo);
  }

  function enlazar(vivo) {
    conectado = true;
    redactor.esperarConexion(false);
    vivo.suscribir(destinos.historial, (mensajes) => {
      if (!historialRecibido) {
        historialRecibido = true;
        hilo.reemplazar(mensajes ?? []);
        repasarSilencio(hilo.cantidad());
        return;
      }
      hilo.sincronizar(mensajes ?? []);
    });
    vivo.suscribir(destinos.vivo, (mensaje) => {
      if (!hilo.tiene(mensaje?.id)) {
        hilo.agregar(mensaje);
      }
    });
    vivo.suscribir(COLA_DE_ERRORES, atenderError);
    return vivo;
  }
}

/**
 * Monta la página entera: título, pestañas (general y privados) o, con
 * `?sala=`, el chat de esa sala con su vuelta.
 *
 * @param {Document} documento
 * @param {{busqueda?: string, token?: string|null, miId?: string|null,
 *   fuenteMensajes?: import('./fuente-mensajes.js').FuenteDeMensajes,
 *   conectar?: Function}} [opciones]
 */
export function montarVistaDeChat(
  documento,
  {
    busqueda = globalThis.location?.search ?? '',
    token = globalThis.sessionStorage?.getItem(CLAVE_TOKEN) ?? null,
    miId = usuarioIdDeSesion(),
    fuenteMensajes = fuenteDeMensajes(),
    conectar = conectarChat,
  } = {},
) {
  const canal = canalDesdeUrl(busqueda);
  const titulo = documento.querySelector('[data-zona="titulo"]');
  const intro = documento.querySelector('[data-zona="intro"]');
  const panelGeneral = documento.querySelector('[data-panel-chat="general"]');
  const zonaPestanas = documento.querySelector('[data-zona="pestanas-chat"]');
  const tituloConversacion = documento.querySelector('[data-zona="titulo-conversacion"]');

  if (canal.idSala) {
    titulo.textContent = 'Chat de la sala';
    tituloConversacion.textContent = 'Conversación de la sala';
    intro.textContent =
      'Solo lo leen quienes están en esta sala. Lo que escribes pasa por el filtro de contenido antes de salir.';
    const vuelta = documento.querySelector('[data-zona="volver-a-la-sala"]');
    vuelta.href = `./sala-batalla.html?sala=${encodeURIComponent(canal.idSala)}`;
    vuelta.hidden = false;
    zonaPestanas.remove();
    return {
      chat: montarChat(panelGeneral, { canal, token, miId, conectar }),
      privados: null,
    };
  }

  titulo.textContent = 'Chat';
  const panelPrivados = documento.createElement('section');
  panelPrivados.className = 'chat__privados';
  let privados = null;
  const pestanas = montarPestanas(
    zonaPestanas,
    [
      { id: 'general', etiqueta: 'Chat general', panel: panelGeneral },
      { id: 'privados', etiqueta: 'Mensajes privados', panel: panelPrivados },
    ],
    {
      alCambiar: (id) => {
        if (id === 'privados' && !privados) {
          privados = montarMensajesPrivados(panelPrivados, {
            fuente: fuenteMensajes,
            miId,
            alIrAlChatGeneral: () => {
              pestanas.mostrar('general');
              documento.getElementById('pestana-general')?.focus();
            },
          });
        }
      },
    },
  );
  return {
    chat: montarChat(panelGeneral, { canal, token, miId, conectar }),
    privados: () => privados,
  };
}
