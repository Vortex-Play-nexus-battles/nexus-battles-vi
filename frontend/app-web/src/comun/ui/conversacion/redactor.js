/**
 * El redactor de mensajes — UXC-6.
 *
 * El mismo campo en el chat general, en el de la sala y en los mensajes
 * privados. Lo que añade a un `<form>` con un `textarea[name="texto"]`:
 *
 *   - cuenta de caracteres contra el máximo del contrato (500 en el chat,
 *     `EnviarMensaje.maxLength`), a la vista y leída junto al campo;
 *   - Enter envía y Mayús + Enter salta de línea, como en cualquier chat, y
 *     sin robarle el Enter a quien está componiendo con un método de
 *     entrada (`isComposing`);
 *   - **esperar conexión**: sin canal no se puede enviar, pero se puede seguir
 *     escribiendo; el botón dice por qué no envía y el borrador no se pierde;
 *   - **bloqueo con motivo**: silencio por sanción, conversación bloqueada o
 *     sin sesión. El campo se sustituye por la explicación y lo que se puede
 *     hacer, con su cuenta atrás si la sanción vence.
 *
 * @module comun/ui/conversacion/redactor
 */

import { h } from '../dom.js';
import { icono } from '../icono.js';
import { cuentaAtrasRotulada, vigilarCuentasAtras } from '../cuenta-atras.js';

/** Máximo del contrato del chat (`EnviarMensaje.texto.maxLength`). */
export const MAXIMO_POR_OMISION = 500;

let secuencia = 0;

/**
 * El marcado de un redactor nuevo (los mensajes privados). El chat lo trae
 * escrito en su HTML con la misma forma.
 *
 * @param {{id?: string, etiqueta?: string, marcador?: string, maximo?: number,
 *   textoDelBoton?: string}} [opciones]
 * @returns {HTMLFormElement}
 */
export function redactorDeMensaje({
  id = null,
  etiqueta = 'Mensaje',
  marcador = 'Escribe un mensaje…',
  maximo = MAXIMO_POR_OMISION,
  textoDelBoton = 'Enviar',
} = {}) {
  secuencia += 1;
  const idCampo = id ?? `redactor-${secuencia}`;
  return h('form', {
    clase: 'redactor-mensaje',
    atributos: { novalidate: true },
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
                texto: etiqueta,
                atributos: { for: idCampo },
              }),
              h('textarea', {
                clase: 'campo__control redactor-mensaje__texto',
                atributos: {
                  id: idCampo,
                  name: 'texto',
                  rows: 2,
                  maxlength: maximo,
                  placeholder: marcador,
                  'aria-describedby': `${idCampo}-pista`,
                },
              }),
            ],
          }),
          h('button', {
            clase: 'boton boton--primario redactor-mensaje__enviar',
            texto: textoDelBoton,
            atributos: { type: 'submit' },
          }),
        ],
      }),
      h('p', {
        clase: 'campo__pista redactor-mensaje__pista',
        atributos: { id: `${idCampo}-pista` },
      }),
    ],
  });
}

/**
 * Da comportamiento a un formulario de mensaje.
 *
 * @param {HTMLFormElement} formulario con `textarea[name="texto"]` y un botón `submit`
 * @param {{maximo?: number,
 *   alEnviar: (texto: string) => (boolean|void|Promise<boolean|void>)}} opciones
 *   `alEnviar` devuelve `false` si el mensaje no salió: entonces el texto se
 *   queda en el campo.
 */
export function prepararRedactor(formulario, { maximo = MAXIMO_POR_OMISION, alEnviar }) {
  formulario.classList.add('redactor-mensaje');
  const campo = formulario.elements.namedItem('texto');
  const boton = formulario.querySelector('[type="submit"]');
  const textoOriginalDelBoton = boton.textContent;
  let zonaAviso = formulario.querySelector('[data-zona="aviso"]');
  if (!zonaAviso) {
    zonaAviso = h('div', { datos: { zona: 'aviso' }, atributos: { hidden: true } });
    formulario.prepend(zonaAviso);
  }

  // La pista: cómo se envía y cuánto queda. Se reutiliza la del HTML si la hay.
  let pista = formulario.querySelector('.redactor-mensaje__pista');
  if (!pista) {
    pista = h('p', { clase: 'campo__pista redactor-mensaje__pista' });
    const contenedorDelCampo = campo.closest('.campo');
    if (contenedorDelCampo) {
      contenedorDelCampo.after(pista);
    } else {
      formulario.append(pista);
    }
  }
  if (!pista.id) {
    secuencia += 1;
    pista.id = `pista-redactor-${secuencia}`;
  }
  const cuenta = h('span', { clase: 'redactor-mensaje__cuenta', datos: { zona: 'cuenta' } });
  const motivoDeEspera = h('span', {
    clase: 'redactor-mensaje__espera',
    datos: { zona: 'espera' },
    atributos: { hidden: true },
  });
  pista.replaceChildren(
    h('span', { texto: 'Enter envía; Mayús + Enter salta de línea.' }),
    ' ',
    cuenta,
    motivoDeEspera,
  );
  const describe = new Set((campo.getAttribute('aria-describedby') ?? '').split(/\s+/));
  describe.add(pista.id);
  campo.setAttribute('aria-describedby', [...describe].filter(Boolean).join(' '));
  campo.setAttribute('maxlength', String(maximo));

  // El bloqueo va dentro del formulario, en el sitio del campo.
  const bloqueo = h('div', {
    clase: 'redactor-mensaje__bloqueo',
    datos: { zona: 'bloqueo' },
    atributos: { hidden: true, role: 'status' },
  });
  const fila = formulario.querySelector('.redactor-mensaje__fila');
  if (fila) {
    fila.before(bloqueo);
  } else {
    formulario.prepend(bloqueo);
  }
  let pararCuenta = () => {};

  let esperando = false;
  let bloqueado = false;
  let ocupado = false;

  function repasarCuenta() {
    const largo = campo.value.length;
    cuenta.textContent = `${largo}/${maximo}`;
    cuenta.classList.toggle('redactor-mensaje__cuenta--cerca', largo >= maximo * 0.9);
  }

  /**
   * Se cambia desde una función: el envío no se puede solapar (el botón está
   * deshabilitado mientras dura), y así `require-atomic-updates` no toma el
   * `await` de en medio por una carrera.
   */
  function marcarOcupado(activo) {
    ocupado = activo;
    repasarBoton();
  }

  function repasarBoton() {
    boton.disabled = esperando || bloqueado || ocupado;
    boton.textContent = ocupado ? 'Enviando…' : textoOriginalDelBoton;
  }

  campo.addEventListener('input', repasarCuenta);
  campo.addEventListener('keydown', (evento) => {
    if (
      evento.key !== 'Enter' ||
      evento.shiftKey ||
      evento.altKey ||
      evento.ctrlKey ||
      evento.metaKey ||
      evento.isComposing
    ) {
      return;
    }
    evento.preventDefault();
    if (typeof formulario.requestSubmit === 'function') {
      formulario.requestSubmit();
    } else {
      formulario.dispatchEvent(new Event('submit', { cancelable: true }));
    }
  });

  formulario.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    const texto = campo.value.trim();
    if (!texto || esperando || bloqueado || ocupado) {
      return;
    }
    marcarOcupado(true);
    let salio;
    try {
      salio = await alEnviar(texto);
    } finally {
      marcarOcupado(false);
    }
    if (salio !== false) {
      // Solo el texto: el resto del formulario (el logro que se comparte)
      // también se limpia, como antes.
      formulario.reset();
      repasarCuenta();
    }
    if (!bloqueado) {
      campo.focus();
    }
  });
  formulario.addEventListener('reset', () => queueMicrotask(repasarCuenta));

  repasarCuenta();
  repasarBoton();

  return {
    formulario,
    campo,
    zonaAviso,

    /**
     * Sin canal no se envía, pero se puede seguir escribiendo.
     *
     * @param {boolean} activo
     * @param {string} [motivo]
     */
    esperarConexion(activo, motivo = 'Esperando conexión: tu texto no se pierde.') {
      esperando = Boolean(activo);
      motivoDeEspera.hidden = !esperando;
      motivoDeEspera.textContent = esperando ? ` ${motivo}` : '';
      repasarBoton();
    },

    /**
     * El campo se sustituye por la explicación.
     *
     * @param {{titulo: string, detalle?: string|null, enlace?: {texto: string, href: string}|null,
     *   accion?: {texto: string, nombre?: string, alPulsar: () => void}|null,
     *   hasta?: string|null}} motivo
     */
    bloquear({ titulo, detalle = null, enlace = null, accion = null, hasta = null }) {
      bloqueado = true;
      pararCuenta();
      const cuerpo = h('div', {
        clase: 'redactor-mensaje__bloqueo-cuerpo',
        hijos: [
          h('p', { clase: 'redactor-mensaje__bloqueo-titulo', texto: titulo }),
          detalle ? h('p', { clase: 'redactor-mensaje__bloqueo-detalle', texto: detalle }) : null,
          hasta ? cuentaAtrasRotulada(hasta, { clase: 't-meta' }) : null,
        ],
      });
      const acciones = h('div', { clase: 'redactor-mensaje__bloqueo-acciones' });
      if (enlace) {
        acciones.append(
          h('a', {
            clase: 'boton boton--secundario boton--pequeno',
            texto: enlace.texto,
            atributos: { href: enlace.href },
          }),
        );
      }
      if (accion) {
        const botonDeAccion = h('button', {
          clase: 'boton boton--secundario boton--pequeno',
          texto: accion.texto,
          atributos: { type: 'button' },
          datos: accion.nombre ? { accion: accion.nombre } : {},
        });
        botonDeAccion.addEventListener('click', () => accion.alPulsar());
        acciones.append(botonDeAccion);
      }
      bloqueo.replaceChildren(
        ...[
          icono('candado', { etiqueta: null, clase: 'icono redactor-mensaje__bloqueo-icono' }),
          cuerpo,
          acciones.childElementCount ? acciones : null,
        ].filter(Boolean),
      );
      if (hasta) {
        pararCuenta = vigilarCuentasAtras(bloqueo, { prefijo: 'Termina en' });
      }
      bloqueo.hidden = false;
      formulario.classList.add('redactor-mensaje--bloqueado');
      campo.disabled = true;
      repasarBoton();
    },

    desbloquear() {
      bloqueado = false;
      pararCuenta();
      pararCuenta = () => {};
      bloqueo.hidden = true;
      bloqueo.replaceChildren();
      formulario.classList.remove('redactor-mensaje--bloqueado');
      campo.disabled = false;
      repasarBoton();
    },

    /** Devuelve al campo un texto que no salió (si no se escribió otro). */
    restaurar(texto) {
      if (!campo.value.trim() && texto) {
        campo.value = texto;
        repasarCuenta();
      }
    },

    enfocar() {
      campo.focus();
    },

    get bloqueado() {
      return bloqueado;
    },
  };
}
