/**
 * Respuestas enriquecidas del asistente y la vista donde está el jugador.
 *
 * El servidor manda, junto al texto de una respuesta, pasos numerados,
 * enlaces a secciones del sitio, tarjetas de texto, botones de respuesta
 * rápida y, cuando no entendió, la oferta de hablar con soporte. Aquí se
 * pintan. Sin imágenes (decisión del cliente).
 *
 * Los enlaces llegan como el id de una vista de la matriz de acceso, nunca
 * como una URL: se convierten con `urlDeVista`, y uno que no existe no se
 * pinta. Así el asistente no puede mandar a nadie fuera del sitio.
 *
 * #506 — tampoco se pinta un enlace (suelto o de tarjeta) a una vista que la
 * sesión actual no puede ver (`destinoVisible`, la misma pregunta que hace
 * la barra de navegación). Es defensa en profundidad: el servidor no debería
 * mandarlo, y si lo manda, la vista lo negaría igual; así no se ofrece una
 * puerta cerrada.
 *
 * ms-chatbot.yaml 1.12.0 (RF-CHA-007) — cuando no pudo responder, el
 * asistente abre solo la solicitud de soporte (o suma la pregunta a la que ya
 * estaba abierta) y la manda en `solicitud`. Se pinta como un aviso con
 * «Ver solicitud», en lugar de «Hablar con soporte»: ya no hay nada que pedir.
 *
 * @module comun/ui/enriquecido-chatbot
 */

import { destinoVisible, urlDeVista } from '../acceso.js';
import { vistaDeRuta } from '../matriz-acceso.js';
import { leerSesion } from '../sesion.js';
import { h } from './dom.js';

export const TEXTOS_ENRIQUECIDO = Object.freeze({
  pasos: 'Pasos',
  soporte: 'Hablar con soporte',
  otrasPreguntas: 'También puedes preguntar',
  // ms-chatbot.yaml 1.12.0 — la solicitud que se abrió sola.
  solicitudAbierta: 'Abrí una solicitud de soporte',
  solicitudSumada: 'Sumé tu pregunta a tu solicitud abierta',
  verSolicitud: 'Ver solicitud',
});

/**
 * La sección del sitio (vista de la matriz) → la `vista` del contrato. Las
 * que no están aquí no se mandan: el asistente responde igual sin ella.
 *
 * #506 — toda vista de la matriz que pertenece a una de las seis secciones
 * del contrato. Quedan fuera, a propósito: las de jugar en línea (batallas,
 * salas, chat), que no tienen sección en el contrato; la tienda, las
 * notificaciones y los comentarios, que no son de ninguna de las seis; y la
 * consola de operación entera (administración y moderación).
 */
export const VISTA_DEL_CHAT = Object.freeze({
  // El inicio del jugador y la portada pública.
  home: 'INICIO',
  portada: 'INICIO',
  // Inventario y lo que se guarda en él.
  inventario: 'INVENTARIO',
  'mis-cofres': 'INVENTARIO',
  misiones: 'MISIONES',
  torneos: 'TORNEOS',
  // El listado, el detalle con sus pujas y publicar una subasta.
  subastas: 'SUBASTAS',
  pujas: 'SUBASTAS',
  'publicar-subasta': 'SUBASTAS',
  // La cuenta: perfil, historial y sanciones, y entrar, crearla, recuperarla
  // o confirmarla (RF-INV-008: a quien no ha entrado, «Mi Cuenta» le ofrece
  // el registro).
  perfil: 'CUENTA',
  'historial-transacciones': 'CUENTA',
  'mis-sanciones': 'CUENTA',
  login: 'CUENTA',
  registro: 'CUENTA',
  'restablecer-solicitar': 'CUENTA',
  'restablecer-confirmar': 'CUENTA',
  'verificar-cuenta': 'CUENTA',
  preparando: 'CUENTA',
});

/**
 * La `vista` del contrato para la página donde está el jugador, o `null`.
 *
 * @param {string|null} [idVista] id de la matriz; por omisión, el de la URL actual
 * @returns {string|null}
 */
export function vistaDelChat(idVista = vistaDeRuta()) {
  return (idVista && VISTA_DEL_CHAT[idVista]) || null;
}

/**
 * El texto que va antes del "1)" de una respuesta paso a paso: los pasos se
 * pintan aparte como lista, así que no se repiten dentro del texto.
 *
 * @param {string} contenido
 * @returns {string}
 */
export function introAntesDePasos(contenido) {
  return String(contenido ?? '')
    .split(/\s*\b1\)\s+/)[0]
    .trim();
}

/**
 * Pinta la parte enriquecida de una respuesta del bot.
 *
 * @param {{pasos?: string[], enlaces?: Array<{texto: string, destino: string}>,
 *          tarjetas?: Array<{titulo: string, texto: string, enlace?: {texto: string, destino: string}|null}>,
 *          respuestasRapidas?: string[], ofrecerSoporteHumano?: boolean,
 *          solicitud?: {id: string, asunto?: string, estado?: string, nueva?: boolean}|null}|null} enriquecido
 * @param {{alPreguntar: (pregunta: string) => void, alPedirSoporte: () => void,
 *          alVerSolicitud?: (solicitud: object) => void,
 *          url?: (destino: string) => string|null,
 *          visible?: (destino: string) => boolean}} acciones `visible`: si la
 *          sesión actual puede ver ese destino (inyectable en pruebas);
 *          `alVerSolicitud`: abrir «Tus solicitudes» (por omisión, `alPedirSoporte`)
 * @returns {HTMLElement|null} null si no hay nada que pintar
 */
export function pintarEnriquecido(
  enriquecido,
  {
    alPreguntar,
    alPedirSoporte,
    alVerSolicitud = alPedirSoporte,
    url = urlDeVista,
    visible = (destino) => destinoVisible(destino, leerSesion()),
  },
) {
  if (!enriquecido) {
    return null;
  }
  const enlaceA = (enlace) => enlaceInterno(enlace, url, visible);
  const caja = h('div', { clase: 'chatbot-enriquecido', datos: { enriquecido: '' } });

  const pasos = enriquecido.pasos ?? [];
  if (pasos.length > 0) {
    caja.append(
      h('ol', {
        clase: 'chatbot-enriquecido__pasos',
        atributos: { 'aria-label': TEXTOS_ENRIQUECIDO.pasos },
        hijos: pasos.map((paso) => h('li', { texto: paso })),
      }),
    );
  }

  for (const tarjeta of enriquecido.tarjetas ?? []) {
    const cuerpo = [
      h('p', { clase: 'chatbot-enriquecido__tarjeta-titulo', texto: tarjeta.titulo }),
      h('p', { clase: 'chatbot-enriquecido__tarjeta-texto', texto: tarjeta.texto }),
    ];
    const enlace = enlaceA(tarjeta.enlace);
    if (enlace) {
      cuerpo.push(enlace);
    }
    caja.append(h('div', { clase: 'chatbot-enriquecido__tarjeta', hijos: cuerpo }));
  }

  const enlaces = (enriquecido.enlaces ?? []).map(enlaceA).filter(Boolean);
  if (enlaces.length > 0) {
    caja.append(h('div', { clase: 'chatbot-enriquecido__enlaces', hijos: enlaces }));
  }

  const botones = (enriquecido.respuestasRapidas ?? []).map((pregunta) => {
    const boton = h('button', {
      clase: 'chatbot-rapidas__opcion',
      texto: pregunta,
      atributos: { type: 'button' },
      datos: { accion: 'respuesta-rapida' },
    });
    boton.addEventListener('click', () => alPreguntar(pregunta));
    return boton;
  });
  const solicitud = enriquecido.solicitud?.id ? enriquecido.solicitud : null;
  if (solicitud) {
    caja.append(avisoDeSolicitud(solicitud, alVerSolicitud));
  }
  if (enriquecido.ofrecerSoporteHumano && !solicitud) {
    const soporte = h('button', {
      clase: 'chatbot-rapidas__opcion chatbot-enriquecido__soporte',
      texto: TEXTOS_ENRIQUECIDO.soporte,
      atributos: { type: 'button' },
      datos: { accion: 'ofrecer-soporte' },
    });
    soporte.addEventListener('click', () => alPedirSoporte());
    botones.push(soporte);
  }
  if (botones.length > 0) {
    caja.append(
      h('div', {
        clase: 'chatbot-rapidas__lista',
        atributos: { role: 'group', 'aria-label': TEXTOS_ENRIQUECIDO.otrasPreguntas },
        hijos: botones,
      }),
    );
  }

  return caja.childElementCount > 0 ? caja : null;
}

// ms-chatbot.yaml 1.12.0 — «Abrí una solicitud…» o «Sumé tu pregunta…», su
// asunto (texto del servidor, con textContent) y «Ver solicitud».
function avisoDeSolicitud(solicitud, alVerSolicitud) {
  const ver = h('button', {
    clase: 'chatbot-rapidas__opcion chatbot-enriquecido__soporte',
    texto: TEXTOS_ENRIQUECIDO.verSolicitud,
    atributos: { type: 'button' },
    datos: { accion: 'ver-solicitud' },
  });
  ver.addEventListener('click', () => alVerSolicitud(solicitud));
  return h('div', {
    clase: 'chatbot-enriquecido__solicitud',
    datos: { solicitudId: solicitud.id },
    hijos: [
      h('p', {
        clase: 'chatbot-enriquecido__solicitud-titulo',
        texto:
          solicitud.nueva === false
            ? TEXTOS_ENRIQUECIDO.solicitudSumada
            : TEXTOS_ENRIQUECIDO.solicitudAbierta,
      }),
      solicitud.asunto
        ? h('p', { clase: 'chatbot-enriquecido__solicitud-asunto', texto: solicitud.asunto })
        : null,
      ver,
    ],
  });
}

function enlaceInterno(enlace, url, visible) {
  if (!enlace?.destino || !visible(enlace.destino)) {
    return null;
  }
  const destino = url(enlace.destino);
  if (!destino) {
    return null;
  }
  return h('a', {
    clase: 'chatbot-enriquecido__enlace',
    texto: enlace.texto || enlace.destino,
    atributos: { href: conAncla(destino, enlace.ancla) },
    datos: { destino: enlace.destino },
  });
}

/**
 * ms-chatbot.yaml 1.8.0 — el destino con su ancla (`#categoria-cuenta-y-registro`
 * en el centro de ayuda), si la trae con la forma del contrato. Si no, el
 * destino tal cual: un ancla rara no cambia a dónde lleva el enlace.
 *
 * @param {string} url
 * @param {string|null|undefined} ancla
 * @returns {string}
 */
export function conAncla(url, ancla) {
  return typeof ancla === 'string' && /^[a-z0-9-]{1,60}$/.test(ancla) ? `${url}#${ancla}` : url;
}
