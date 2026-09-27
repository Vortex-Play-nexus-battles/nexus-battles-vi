/**
 * El hilo de una conversación — UXC-6.
 *
 * Una lista `<ol>` de globos (`mensaje.js`) con lo que toda conversación
 * necesita y ninguna vista tenía:
 *
 *   - **separadores de día** («Hoy», «Ayer», «Lunes 21 de septiembre»), para
 *     que la hora de cada globo baste;
 *   - **orden por momento**: al reconectar, el historial trae lo que se
 *     perdió mientras tanto y se intercala donde pasó, no al final;
 *   - **sin repetidos**: un mensaje que ya está (por `id`) se sustituye, no se
 *     duplica — es lo que pasa cuando el historial vuelve a llegar;
 *   - **desplazamiento honesto**: si estás abajo, lo nuevo se ve; si subiste a
 *     leer, no se te arrastra, y aparece «Hay mensajes nuevos» para bajar
 *     cuando quieras.
 *
 * La lista es una región desplazable con nombre y enfocable con el teclado
 * (WCAG 2.1.1): sin foco, quien no usa ratón no puede leer lo de arriba.
 *
 * @module comun/ui/conversacion/hilo
 */

import { h } from '../dom.js';
import { burbujaDeMensaje, etiquetaDeDia, mensajeDelSistema } from './mensaje.js';

/** Distancia al final, en píxeles, que todavía cuenta como «estar abajo». */
const MARGEN_DE_FINAL = 48;

/** @param {unknown} valor */
function momentoDe(valor) {
  const fecha = valor instanceof Date ? valor : new Date(valor ?? Number.NaN);
  const ms = fecha.getTime();
  return Number.isNaN(ms) ? null : ms;
}

/** @param {number} ms */
function claveDeDia(ms) {
  const fecha = new Date(ms);
  return `${fecha.getFullYear()}-${fecha.getMonth()}-${fecha.getDate()}`;
}

/**
 * @param {HTMLOListElement} lista la `<ol>` de la conversación
 * @param {{miId?: string|null, nombre?: string, ahora?: () => Date,
 *   alCambiar?: (cantidad: number) => void}} [opciones]
 *   `alCambiar` avisa cuántos mensajes hay (para pintar o quitar el vacío).
 */
export function hiloDeConversacion(
  lista,
  { miId = null, nombre = 'Mensajes', ahora = () => new Date(), alCambiar = () => {} } = {},
) {
  lista.classList.add('conversacion__mensajes');
  lista.setAttribute('tabindex', '0');
  if (!lista.hasAttribute('aria-label') && !lista.hasAttribute('aria-labelledby')) {
    lista.setAttribute('aria-label', nombre);
  }
  if (!lista.hasAttribute('aria-live')) {
    lista.setAttribute('aria-live', 'polite');
  }

  /** @type {Array<{momento: number, nodo: HTMLLIElement, id: string|null, orden: number}>} */
  let entradas = [];
  let secuencia = 0;
  let nuevosSinVer = 0;

  const botonNuevos = h('button', {
    clase: 'boton boton--primario boton--pequeno conversacion__nuevos',
    atributos: { type: 'button', hidden: true },
    datos: { accion: 'ver-mensajes-nuevos' },
  });
  botonNuevos.addEventListener('click', () => irAlFinal({ enfocar: true }));
  lista.addEventListener('scroll', () => {
    if (estaAbajo()) {
      ocultarNuevos();
    }
  });

  function colocarBoton() {
    if (!botonNuevos.isConnected && lista.parentElement) {
      lista.after(botonNuevos);
    }
  }

  function estaAbajo() {
    return lista.scrollHeight - lista.scrollTop - lista.clientHeight <= MARGEN_DE_FINAL;
  }

  function ocultarNuevos() {
    nuevosSinVer = 0;
    botonNuevos.hidden = true;
  }

  function irAlFinal({ enfocar = false } = {}) {
    lista.scrollTop = lista.scrollHeight;
    ocultarNuevos();
    if (enfocar) {
      lista.focus({ preventScroll: true });
    }
  }

  /** Rehace la lista en orden, con sus separadores de día. */
  function pintar() {
    entradas.sort((a, b) => a.momento - b.momento || a.orden - b.orden);
    const hijos = [];
    let diaAnterior = null;
    for (const entrada of entradas) {
      const dia = claveDeDia(entrada.momento);
      if (dia !== diaAnterior) {
        hijos.push(
          h('li', {
            clase: 'conversacion__dia',
            datos: { dia },
            hijos: [h('span', { texto: etiquetaDeDia(new Date(entrada.momento), ahora()) })],
          }),
        );
        diaAnterior = dia;
      }
      hijos.push(entrada.nodo);
    }
    lista.replaceChildren(...hijos);
    alCambiar(entradas.length);
  }

  /**
   * @param {HTMLLIElement} nodo
   * @param {{momento: number|null, id?: string|null}} datos
   */
  function colocar(nodo, { momento, id = null }) {
    const cuando = momento ?? ahora().getTime();
    nodo.dataset.momento = String(cuando);
    const previa = id ? entradas.findIndex((e) => e.id === id) : -1;
    if (previa >= 0) {
      entradas[previa] = { ...entradas[previa], momento: cuando, nodo };
    } else {
      entradas.push({ momento: cuando, nodo, id, orden: (secuencia += 1) });
    }
  }

  /**
   * Tras pintar: si estabas abajo (o el mensaje es tuyo), se baja; si no, se
   * avisa de que hay algo nuevo.
   */
  function seguir(estabaAbajo, { propio = false, cuantos = 1 } = {}) {
    if (estabaAbajo || propio) {
      irAlFinal();
      return;
    }
    colocarBoton();
    nuevosSinVer += cuantos;
    botonNuevos.textContent =
      nuevosSinVer === 1 ? 'Hay 1 mensaje nuevo' : `Hay ${nuevosSinVer} mensajes nuevos`;
    botonNuevos.hidden = false;
  }

  /**
   * Un mensaje tuyo puede traer su entrega del servidor (`ENVIADO`, `LEIDO`);
   * la que se pase a mano manda.
   */
  function burbuja(mensaje, opciones = {}) {
    return burbujaDeMensaje(mensaje, {
      miId,
      ...opciones,
      entrega: opciones.entrega ?? mensaje.entrega ?? null,
    });
  }

  return {
    /**
     * Sustituye todo por el historial (la primera vez que llega).
     *
     * @param {object[]} mensajes
     */
    reemplazar(mensajes = []) {
      lista.setAttribute('aria-busy', 'true');
      entradas = [];
      for (const mensaje of mensajes ?? []) {
        colocar(burbuja(mensaje), {
          momento: momentoDe(mensaje.enviadoEn),
          id: mensaje.id ?? null,
        });
      }
      pintar();
      lista.removeAttribute('aria-busy');
      irAlFinal();
    },

    /**
     * Mezcla un historial con lo que ya hay: lo que falta se intercala en su
     * momento y lo que ya estaba se queda. Es lo que pasa al reconectar.
     *
     * @param {object[]} mensajes
     * @returns {number} cuántos mensajes nuevos entraron
     */
    sincronizar(mensajes = []) {
      const estabaAbajo = estaAbajo();
      const conocidos = new Set(entradas.map((e) => e.id).filter(Boolean));
      let nuevos = 0;
      for (const mensaje of mensajes ?? []) {
        if (mensaje.id && conocidos.has(mensaje.id)) {
          continue;
        }
        colocar(burbuja(mensaje), {
          momento: momentoDe(mensaje.enviadoEn),
          id: mensaje.id ?? null,
        });
        nuevos += 1;
      }
      if (nuevos > 0) {
        pintar();
        seguir(estabaAbajo, { cuantos: nuevos });
      }
      return nuevos;
    },

    /**
     * Un mensaje en vivo (o uno tuyo, con su estado de entrega).
     *
     * @param {object} mensaje
     * @param {{entrega?: string|null, alReintentar?: () => void, clave?: string}} [opciones]
     *   `clave` identifica un mensaje tuyo que todavía no tiene `id` del
     *   servidor, para sustituirlo cuando llegue la confirmación.
     */
    agregar(mensaje, { entrega = null, alReintentar, clave } = {}) {
      const estabaAbajo = estaAbajo();
      const nodo = burbuja(mensaje, { entrega, alReintentar });
      colocar(nodo, { momento: momentoDe(mensaje.enviadoEn), id: clave ?? mensaje.id ?? null });
      pintar();
      seguir(estabaAbajo, { propio: nodo.dataset.quien === 'yo' });
      return nodo;
    },

    /**
     * Cambia la entrada de un mensaje ya pintado (el `id` o la `clave` con
     * que se agregó) por su versión nueva: la confirmación del servidor, un
     * fallo, un «Leído».
     *
     * @param {string} idOClave
     * @param {object} mensaje
     * @param {{entrega?: string|null, alReintentar?: () => void, id?: string|null}} [opciones]
     */
    actualizar(idOClave, mensaje, { entrega = null, alReintentar, id = null } = {}) {
      const indice = entradas.findIndex((e) => e.id === idOClave);
      if (indice < 0) {
        return null;
      }
      const nodo = burbuja(mensaje, { entrega, alReintentar });
      nodo.dataset.momento = String(entradas[indice].momento);
      entradas[indice] = { ...entradas[indice], nodo, id: id ?? idOClave };
      pintar();
      return nodo;
    },

    /**
     * Una línea del sistema: la vista contando algo que acaba de pasar.
     *
     * @param {string} texto
     * @param {{tono?: 'info'|'advertencia'|'exito'}} [opciones]
     */
    sistema(texto, { tono = 'info' } = {}) {
      const estabaAbajo = estaAbajo();
      const cuando = ahora();
      const nodo = mensajeDelSistema(texto, { tono, cuando });
      colocar(nodo, { momento: cuando.getTime() });
      pintar();
      seguir(estabaAbajo);
      return nodo;
    },

    /** Mensajes y líneas del sistema pintados (sin contar los separadores). */
    cantidad() {
      return entradas.length;
    },

    /** ¿Hay ya un mensaje con este `id` (o clave)? */
    tiene(id) {
      return entradas.some((e) => e.id === id);
    },

    irAlFinal,
  };
}
