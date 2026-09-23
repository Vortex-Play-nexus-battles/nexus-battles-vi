/**
 * Botón flotante del asistente — HU-CHA-001 (#505, grupo-4, Sprint 3).
 *
 * ## Por qué existe este módulo
 *
 * Nueve vistas copiaban a mano este botón:
 *
 * ```html
 * <button class="chatbot-flotante" type="button" aria-label="Abrir el asistente">IA</button>
 * ```
 *
 * y **ningún JavaScript lo escuchaba**. Pulsarlo no hacía nada: ni abría una
 * ventana, ni avisaba, ni fallaba. Un control muerto en nueve pantallas.
 *
 * ## Qué hace ahora, y por qué no más
 *
 * El chatbot es de otro equipo y de otro sprint: HU-CHA-001 a HU-CHA-012
 * (#505–#516) están abiertas con etiqueta `grupo-4`, y el servicio no existe
 * todavía. **No se implementa aquí** — sería inventarse un módulo ajeno.
 *
 * Pero el propio criterio CA-03 de HU-CHA-001 ya dice qué hacer mientras
 * tanto: «Ante servicio de chatbot no disponible, que debe informarse
 * ofreciendo la vía alternativa de contacto». Eso es exactamente lo que falta
 * y es lo que este módulo cubre: el botón responde, dice la verdad sobre el
 * estado del asistente, y lleva a lo que sí funciona hoy (el chat general,
 * HU-JUE-015).
 *
 * Y RF-CHA-001 exige el icono flotante persistente en **todas** las vistas,
 * así que el botón no se retira: se monta desde aquí, una sola vez y con el
 * mismo comportamiento, en lugar de estar copiado en nueve archivos HTML.
 *
 * Cuando el servicio exista, este módulo es el único sitio que cambia.
 */

import { h } from './dom.js';
import { abrirDialogo } from './dialogo.js';

/** Destino del chat general de jugadores, relativo a `src/comun/ui/`. */
const CHAT_GENERAL = '../../plataforma/salas-partidas/chat.html';

/**
 * Lo que se le dice a quien pulsa. Se exporta para poder afirmarlo en pruebas
 * sin abrir el diálogo, y para que quien implemente HU-CHA-001 vea de un
 * vistazo qué texto hay que sustituir.
 */
export const AVISO_SIN_ASISTENTE = Object.freeze({
  titulo: 'El asistente todavía no está disponible',
  mensaje:
    'El asistente automático llega en una entrega posterior. Mientras tanto puedes ' +
    'preguntar en el chat general: alli hay gente jugando a la misma hora que tu.',
});

/**
 * Monta el botón flotante del asistente.
 *
 * @param {Document|HTMLElement} [raiz] dónde colgarlo; por omisión el `body`
 * @param {{base?: string, disponible?: boolean, alAbrir?: () => void}} [opciones]
 *   `disponible` y `alAbrir` son el enganche para cuando exista el servicio:
 *   con `disponible: true` el botón llama a `alAbrir` en vez de avisar.
 * @returns {HTMLButtonElement}
 */
export function montarAsistente(raiz = document, { base, disponible = false, alAbrir } = {}) {
  const destino = raiz.body ?? raiz;

  // Si la vista todavía trae el botón copiado en su HTML, se reutiliza ese en
  // vez de añadir un segundo: dos botones flotantes superpuestos serían peor
  // que el que no hacía nada.
  const existente = destino.querySelector?.('.chatbot-flotante');
  const boton =
    existente ??
    h('button', {
      clase: 'chatbot-flotante',
      texto: 'IA',
      atributos: { type: 'button', 'aria-label': 'Abrir el asistente' },
    });

  boton.addEventListener('click', () => {
    if (disponible && typeof alAbrir === 'function') {
      alAbrir();
      return;
    }
    // `abrirDialogo` ya pone su propio botón de cerrar, atrapa el foco y
    // escucha Escape: aquí solo se añade la vía alternativa que pide CA-03.
    const { cerrar } = abrirDialogo({
      titulo: AVISO_SIN_ASISTENTE.titulo,
      cuerpo: AVISO_SIN_ASISTENTE.mensaje,
      acciones: [
        h('a', {
          clase: 'boton boton--primario',
          texto: 'Ir al chat general',
          atributos: { href: new URL(base ?? CHAT_GENERAL, import.meta.url).href },
          datos: { accion: 'ir-al-chat' },
        }),
      ],
      raiz: destino,
    });
    boton.dataset.dialogoAbierto = 'si';
    destino.querySelector('[data-accion="ir-al-chat"]')?.addEventListener('click', () => cerrar());
  });

  if (!existente && destino.append) {
    destino.append(boton);
  }
  return boton;
}
