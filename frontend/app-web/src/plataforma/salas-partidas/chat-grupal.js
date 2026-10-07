/**
 * Chat grupal dentro de la sala — revisión del modo jugador del 6-oct, punto
 * 15: «el botón “Chat de la sala” ponerle mejor “Chat grupal”, pero me hace
 * irme a una pestaña nueva… en plena batalla no será dinámico estar entre
 * pestañas escribiendo y jugando».
 *
 * Es el MISMO chat de sala de HU-JUE-015, no otro: `montarChat` de `chat.js`
 * con sus destinos STOMP, su historial, el filtro de la lista negra, el
 * silencio por sanción y el límite de frecuencia del servidor. Lo único nuevo
 * es dónde vive: un panel lateral en escritorio y una hoja inferior en móvil,
 * en la misma página del combate. Nunca una pestaña nueva.
 *
 *   - Se abre con «Chat grupal» (aria-expanded/aria-controls) y se cierra con
 *     «Cerrar» o Escape; el foco vuelve al botón.
 *   - No es modal en escritorio: se sigue jugando con él abierto. En móvil
 *     ocupa la pantalla y el foco no se escapa de él mientras está abierto.
 *   - El canal se abre la primera vez que se abre el panel: quien no chatea
 *     no paga una conexión.
 *   - Cerrado, cuenta los mensajes nuevos de otros sobre el botón, sin
 *     anunciarlos uno a uno al lector de pantalla.
 *
 * @module chat-grupal
 */

import { h } from '../../comun/ui/dom.js';
import { montarChat, destinosDe } from './chat.js';

/** Por debajo de esta anchura el panel es una hoja a pantalla (y atrapa el foco). */
export const ANCHO_DE_HOJA = 768;

const ENFOCABLES =
  'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * El texto del contador de nuevos sobre el botón.
 *
 * @param {number} nuevos
 * @returns {{visible: string, accesible: string}}
 */
export function textoDeNuevos(nuevos) {
  if (!nuevos) {
    return { visible: '', accesible: 'Chat grupal' };
  }
  const cifra = nuevos > 99 ? '99+' : String(nuevos);
  const mensajes = nuevos === 1 ? '1 mensaje nuevo' : `${nuevos} mensajes nuevos`;
  return { visible: cifra, accesible: `Chat grupal, ${mensajes}` };
}

/**
 * El panel con la estructura que espera `montarChat`.
 *
 * @returns {{panel: HTMLElement, cerrar: HTMLButtonElement, cuerpo: HTMLElement}}
 */
function construirPanel() {
  const cerrar = h('button', {
    clase: 'boton boton--secundario boton--pequeno',
    texto: 'Cerrar',
    atributos: { type: 'button', 'aria-label': 'Cerrar el chat grupal' },
    datos: { accion: 'cerrar-chat-grupal' },
  });
  const texto = h('textarea', {
    clase: 'campo__control redactor-mensaje__texto',
    atributos: {
      id: 'texto-chat-grupal',
      name: 'texto',
      rows: 2,
      maxlength: 500,
      placeholder: 'Escribe a la sala…',
      required: true,
    },
  });
  const formulario = h('form', {
    clase: 'redactor-mensaje',
    atributos: { id: 'formulario-chat-grupal', novalidate: true },
    hijos: [
      h('div', { datos: { zona: 'aviso' }, atributos: { hidden: true } }),
      h('div', {
        clase: 'redactor-mensaje__fila',
        hijos: [
          h('div', {
            clase: 'campo redactor-mensaje__campo',
            hijos: [
              h('label', {
                clase: 'campo__etiqueta solo-lectores',
                texto: 'Mensaje para la sala',
                atributos: { for: 'texto-chat-grupal' },
              }),
              texto,
            ],
          }),
          h('button', {
            clase: 'boton boton--primario redactor-mensaje__enviar',
            texto: 'Enviar',
            atributos: { type: 'submit' },
          }),
        ],
      }),
      h('p', {
        clase: 'campo__pista redactor-mensaje__pista',
        texto: 'Hasta 500 caracteres.',
        atributos: { id: 'pista-chat-grupal' },
      }),
    ],
  });
  const cuerpo = h('section', {
    clase: 'conversacion chat-grupal__conversacion',
    atributos: { 'aria-labelledby': 'titulo-chat-grupal' },
    hijos: [
      h('div', {
        clase: 'conversacion__cuerpo chat-grupal__mensajes',
        hijos: [
          h('ol', { datos: { zona: 'mensajes' } }),
          h('div', { clase: 'conversacion__estado', datos: { zona: 'sin-mensajes' } }),
        ],
      }),
      formulario,
    ],
  });
  const panel = h('aside', {
    clase: 'chat-grupal',
    datos: { zona: 'chat-grupal' },
    atributos: {
      id: 'chat-grupal',
      role: 'dialog',
      'aria-modal': 'false',
      'aria-labelledby': 'titulo-chat-grupal',
      hidden: true,
    },
    hijos: [
      h('header', {
        clase: 'chat-grupal__cabecera',
        hijos: [
          h('h2', {
            clase: 'chat-grupal__titulo',
            texto: 'Chat grupal',
            atributos: { id: 'titulo-chat-grupal' },
          }),
          // `montarChat` pinta aquí el estado del canal; discreto en el
          // combate, como la píldora del HUD.
          h('span', {
            clase: 'conexion',
            datos: { zona: 'conexion' },
            atributos: { role: 'status' },
          }),
          cerrar,
        ],
      }),
      cuerpo,
    ],
  });
  return { panel, cerrar, cuerpo };
}

/**
 * Monta el chat grupal de una sala sobre el botón que lo abre.
 *
 * @param {Document} documento
 * @param {object} opciones
 * @param {string} opciones.idSala
 * @param {HTMLButtonElement} opciones.abridor el botón «Chat grupal» del HUD
 * @param {string|null} opciones.token
 * @param {string|null} [opciones.miId]
 * @param {typeof montarChat} [opciones.montar] inyectable para las pruebas
 * @param {Function} [opciones.conectar] transporte, inyectable para las pruebas
 * @param {(consulta: string) => {matches: boolean}} [opciones.medios] `matchMedia`
 * @returns {{abrir: () => void, cerrar: () => void, panel: HTMLElement, nuevos: () => number} | null}
 */
export function montarChatGrupal(
  documento,
  {
    idSala,
    abridor,
    token,
    miId = null,
    montar = montarChat,
    conectar,
    medios = (consulta) => globalThis.matchMedia?.(consulta) ?? { matches: false },
  },
) {
  if (!idSala || !abridor) {
    return null;
  }
  const { panel, cerrar: botonCerrar } = construirPanel();
  documento.body.append(panel);
  abridor.hidden = false;
  abridor.setAttribute('aria-controls', 'chat-grupal');
  abridor.setAttribute('aria-expanded', 'false');
  const marca = abridor.querySelector('[data-zona="chat-nuevos"]');

  let montado = null;
  let nuevos = 0;

  function pintarNuevos() {
    const { visible, accesible } = textoDeNuevos(nuevos);
    if (marca) {
      marca.textContent = visible;
      marca.hidden = !visible;
    }
    abridor.setAttribute('aria-label', accesible);
  }

  function esHoja() {
    return Boolean(medios(`(max-width: ${ANCHO_DE_HOJA}px)`).matches);
  }

  async function montarUnaVez() {
    if (montado) {
      return montado;
    }
    const opciones = { canal: { idSala }, token, miId };
    if (conectar) {
      opciones.conectar = conectar;
    }
    montado = Promise.resolve(montar(panel, opciones)).then((canal) => {
      // Cerrado, los mensajes de otros se cuentan sobre el botón.
      canal?.suscribir?.(destinosDe({ idSala }).vivo, (mensaje) => {
        const deOtro = !miId || String(mensaje?.autor?.id ?? '') !== String(miId);
        if (panel.hidden && deOtro) {
          nuevos += 1;
          pintarNuevos();
        }
      });
      return canal;
    });
    return montado;
  }

  function abrir() {
    const hoja = esHoja();
    panel.hidden = false;
    panel.dataset.hoja = hoja ? 'si' : 'no';
    // La hoja de móvil tapa la vista y retiene el foco: ahí sí es modal.
    panel.setAttribute('aria-modal', hoja ? 'true' : 'false');
    // En escritorio ancho el combate le deja sitio en vez de quedar debajo.
    documento.body.dataset.chatGrupal = 'abierto';
    abridor.setAttribute('aria-expanded', 'true');
    nuevos = 0;
    pintarNuevos();
    montarUnaVez();
    panel.querySelector('textarea')?.focus();
  }

  function cerrar({ devolverFoco = true } = {}) {
    if (panel.hidden) {
      return;
    }
    panel.hidden = true;
    delete documento.body.dataset.chatGrupal;
    abridor.setAttribute('aria-expanded', 'false');
    if (devolverFoco) {
      abridor.focus();
    }
  }

  abridor.addEventListener('click', () => {
    if (panel.hidden) {
      abrir();
    } else {
      cerrar();
    }
  });
  botonCerrar.addEventListener('click', () => cerrar());
  panel.addEventListener('keydown', (evento) => {
    if (evento.key === 'Escape') {
      evento.preventDefault();
      cerrar();
      return;
    }
    // En móvil es una hoja a pantalla: el foco no se escapa por detrás.
    if (evento.key === 'Tab' && panel.dataset.hoja === 'si') {
      const enfocables = [...panel.querySelectorAll(ENFOCABLES)].filter((e) => !e.hidden);
      if (enfocables.length === 0) {
        return;
      }
      const primero = enfocables[0];
      const ultimo = enfocables[enfocables.length - 1];
      if (evento.shiftKey && documento.activeElement === primero) {
        evento.preventDefault();
        ultimo.focus();
      } else if (!evento.shiftKey && documento.activeElement === ultimo) {
        evento.preventDefault();
        primero.focus();
      }
    }
  });
  pintarNuevos();

  return { abrir, cerrar, panel, nuevos: () => nuevos };
}
