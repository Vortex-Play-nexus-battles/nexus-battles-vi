/**
 * Dialogo modal accesible (`.velo` + `.dialogo` en el kit).
 *
 * Hoy solo dos vistas abren algo parecido a un modal y cada una lo hace a su
 * manera; ninguna devuelve el foco al elemento que lo abrio ni cierra con
 * Escape. Un modal mal hecho deja a quien navega con teclado atrapado detras
 * del velo.
 *
 * Lo que este componente garantiza:
 *
 *   - `role="dialog"` con `aria-modal` y titulo asociado;
 *   - el foco entra al dialogo y no se escapa mientras esta abierto;
 *   - Escape y el clic en el velo cierran (salvo que se pida lo contrario);
 *   - al cerrar, el foco vuelve a donde estaba.
 */

import { h } from './dom.js';

let contador = 0;

/**
 * @param {{titulo: string, cuerpo: Node|string, acciones?: Array<Node>,
 *          peligro?: boolean, cerrableFuera?: boolean, raiz?: HTMLElement,
 *          alCerrar?: (() => void)|null}} opciones
 *   `alCerrar` se llama una sola vez, cierre quien cierre: un botón, Escape,
 *   la equis o el velo.
 * @returns {{elemento: HTMLElement, cerrar: () => void}}
 */
export function abrirDialogo({
  titulo,
  cuerpo,
  acciones = [],
  peligro = false,
  cerrableFuera = true,
  raiz = document.body,
  alCerrar = null,
}) {
  contador += 1;
  const idTitulo = `dialogo-titulo-${contador}`;
  const devolverFocoA = document.activeElement;

  const velo = h('div', { clase: 'velo', datos: { zona: 'velo' } });
  const caja = h('div', {
    clase: `dialogo${peligro ? ' dialogo--peligro' : ''}`,
    atributos: { role: 'dialog', 'aria-modal': 'true', 'aria-labelledby': idTitulo },
  });

  const encabezado = h('h2', { clase: 'dialogo__titulo', texto: titulo });
  encabezado.id = idTitulo;
  caja.append(encabezado);
  caja.append(typeof cuerpo === 'string' ? h('p', { texto: cuerpo }) : cuerpo);

  let cerrado = false;
  const cerrar = () => {
    if (cerrado) {
      return;
    }
    cerrado = true;
    velo.remove();
    document.removeEventListener('keydown', alTeclado);
    if (devolverFocoA instanceof HTMLElement) {
      devolverFocoA.focus();
    }
    alCerrar?.();
  };

  const botonCerrar = h('button', {
    clase: 'dialogo__cerrar',
    texto: '×',
    atributos: { type: 'button', 'aria-label': 'Cerrar' },
    datos: { accion: 'cerrar' },
  });
  botonCerrar.addEventListener('click', cerrar);
  caja.append(botonCerrar);

  if (acciones.length > 0) {
    caja.append(h('div', { clase: 'dialogo__acciones', hijos: acciones }));
  }

  function enfocables() {
    return Array.from(
      caja.querySelectorAll(
        'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
      ),
    ).filter((e) => !e.hasAttribute('disabled'));
  }

  function alTeclado(evento) {
    if (evento.key === 'Escape') {
      evento.preventDefault();
      cerrar();
      return;
    }
    if (evento.key !== 'Tab') {
      return;
    }
    const lista = enfocables();
    if (lista.length === 0) {
      return;
    }
    const primero = lista[0];
    const ultimo = lista[lista.length - 1];
    if (evento.shiftKey && document.activeElement === primero) {
      evento.preventDefault();
      ultimo.focus();
    } else if (!evento.shiftKey && document.activeElement === ultimo) {
      evento.preventDefault();
      primero.focus();
    }
  }

  if (cerrableFuera) {
    velo.addEventListener('click', (evento) => {
      if (evento.target === velo) {
        cerrar();
      }
    });
  }
  document.addEventListener('keydown', alTeclado);

  velo.append(caja);
  raiz.append(velo);
  (enfocables()[0] ?? caja).focus?.();

  return { elemento: caja, cerrar };
}

/**
 * Confirmacion de una accion que no se puede deshacer (cancelar una sala,
 * banear una cuenta, borrar un comentario).
 *
 * UXC-5 — antes, cerrar con Escape, con la equis o con el velo dejaba la
 * promesa sin resolver: quien esperaba la respuesta se quedaba esperando para
 * siempre. Cerrar sin decidir es decir que no.
 *
 * @param {{titulo: string, mensaje?: string, cuerpo?: Node|null, textoConfirmar?: string,
 *          textoCancelar?: string, peligro?: boolean}} opciones
 *   `cuerpo` sustituye a `mensaje` cuando hace falta mas que una frase (un
 *   resumen, una lista de consecuencias).
 * @returns {Promise<boolean>} true si se confirmo
 */
export function confirmar({
  titulo,
  mensaje = '',
  cuerpo = null,
  textoConfirmar = 'Confirmar',
  textoCancelar = 'Cancelar',
  peligro = true,
}) {
  return new Promise((resolverPromesa) => {
    let decidido = false;
    const resolver = (valor) => {
      if (!decidido) {
        decidido = true;
        resolverPromesa(valor);
      }
    };
    const cancelar = h('button', {
      clase: 'boton boton--secundario',
      texto: textoCancelar,
      atributos: { type: 'button' },
      datos: { accion: 'cancelar' },
    });
    const aceptar = h('button', {
      clase: `boton ${peligro ? 'boton--peligro' : 'boton--primario'}`,
      texto: textoConfirmar,
      atributos: { type: 'button' },
      datos: { accion: 'confirmar' },
    });

    const { cerrar } = abrirDialogo({
      titulo,
      cuerpo: cuerpo ?? mensaje,
      acciones: [cancelar, aceptar],
      peligro,
      alCerrar: () => resolver(false),
    });

    cancelar.addEventListener('click', () => {
      resolver(false);
      cerrar();
    });
    aceptar.addEventListener('click', () => {
      resolver(true);
      cerrar();
    });
  });
}

/**
 * ConfirmCritical — la confirmación de una acción crítica — UXC-7.
 *
 * §7.3.9 del documento: «Confirmación adicional para acciones críticas
 * (baneo, eliminación masiva)». Pulsar no basta: hay que escribir una
 * palabra que nombre lo que se va a tocar (el apodo de la cuenta, el término
 * que se borra), y el botón no se activa hasta que coincide. Lo que va a
 * pasar se lista antes, en frases, para que nadie confirme a ciegas.
 *
 * Sustituye a `window.confirm()`, que no se puede leer bien con lector de
 * pantalla, no se puede estilar y en algunos navegadores se desactiva solo.
 *
 * @param {{titulo: string, mensaje?: string, consecuencias?: string[], palabra: string,
 *   textoConfirmar?: string, textoCancelar?: string}} opciones
 * @returns {Promise<boolean>} true si se confirmó escribiendo la palabra
 */
export function confirmarCritico({
  titulo,
  mensaje = '',
  consecuencias = [],
  palabra,
  textoConfirmar = 'Confirmar',
  textoCancelar = 'Cancelar',
}) {
  contador += 1;
  const idCampo = `confirmacion-critica-${contador}`;
  const esperada = String(palabra ?? '').trim();
  const campo = h('input', {
    clase: 'campo__control',
    atributos: {
      id: idCampo,
      type: 'text',
      autocomplete: 'off',
      spellcheck: 'false',
      'aria-describedby': `${idCampo}-pista`,
    },
  });
  const cuerpo = h('div', {
    clase: 'dialogo__cuerpo confirmacion-critica',
    hijos: [
      mensaje ? h('p', { texto: mensaje }) : null,
      consecuencias.length
        ? h('ul', {
            clase: 'confirmacion-critica__consecuencias',
            hijos: consecuencias.map((texto) => h('li', { texto })),
          })
        : null,
      h('div', {
        clase: 'campo',
        hijos: [
          h('label', {
            clase: 'campo__etiqueta',
            texto: `Escribe «${esperada}» para confirmar`,
            atributos: { for: idCampo },
          }),
          campo,
          h('p', {
            clase: 'campo__pista',
            texto: 'Tal cual, con sus mayúsculas: así nadie lo confirma sin leerlo.',
            atributos: { id: `${idCampo}-pista` },
          }),
        ],
      }),
    ],
  });

  return new Promise((resolverPromesa) => {
    let decidido = false;
    const resolver = (valor) => {
      if (!decidido) {
        decidido = true;
        resolverPromesa(valor);
      }
    };
    const cancelar = h('button', {
      clase: 'boton boton--secundario',
      texto: textoCancelar,
      atributos: { type: 'button' },
      datos: { accion: 'cancelar' },
    });
    const aceptar = h('button', {
      clase: 'boton boton--peligro',
      texto: textoConfirmar,
      atributos: { type: 'button', disabled: true },
      datos: { accion: 'confirmar' },
    });
    const coincide = () => campo.value.trim() === esperada && esperada !== '';
    campo.addEventListener('input', () => {
      aceptar.disabled = !coincide();
    });

    const { cerrar } = abrirDialogo({
      titulo,
      cuerpo,
      acciones: [cancelar, aceptar],
      peligro: true,
      alCerrar: () => resolver(false),
    });

    const confirmarSiCoincide = () => {
      if (!coincide()) {
        return;
      }
      resolver(true);
      cerrar();
    };
    campo.addEventListener('keydown', (evento) => {
      if (evento.key === 'Enter') {
        evento.preventDefault();
        confirmarSiCoincide();
      }
    });
    cancelar.addEventListener('click', () => {
      resolver(false);
      cerrar();
    });
    aceptar.addEventListener('click', confirmarSiCoincide);
  });
}

/**
 * Pedir un texto en un diálogo — UXC-7, en lugar de `window.prompt()`.
 *
 * Con su etiqueta asociada, el valor actual ya escrito y seleccionado, Enter
 * para aceptar y Escape para cancelar.
 *
 * @param {{titulo: string, etiqueta: string, valor?: string, pista?: string|null,
 *   textoConfirmar?: string, textoCancelar?: string, maximo?: number|null}} opciones
 * @returns {Promise<string|null>} el texto (sin espacios de los bordes) o null si se canceló
 */
export function pedirTexto({
  titulo,
  etiqueta,
  valor = '',
  pista = null,
  textoConfirmar = 'Guardar',
  textoCancelar = 'Cancelar',
  maximo = null,
}) {
  contador += 1;
  const idCampo = `texto-pedido-${contador}`;
  const campo = h('input', {
    clase: 'campo__control',
    atributos: {
      id: idCampo,
      type: 'text',
      autocomplete: 'off',
      maxlength: maximo,
      'aria-describedby': pista ? `${idCampo}-pista` : null,
    },
  });
  campo.value = valor;
  const cuerpo = h('div', {
    clase: 'campo',
    hijos: [
      h('label', { clase: 'campo__etiqueta', texto: etiqueta, atributos: { for: idCampo } }),
      campo,
      pista
        ? h('p', { clase: 'campo__pista', texto: pista, atributos: { id: `${idCampo}-pista` } })
        : null,
    ],
  });

  return new Promise((resolverPromesa) => {
    let decidido = false;
    const resolver = (resultado) => {
      if (!decidido) {
        decidido = true;
        resolverPromesa(resultado);
      }
    };
    const cancelar = h('button', {
      clase: 'boton boton--secundario',
      texto: textoCancelar,
      atributos: { type: 'button' },
      datos: { accion: 'cancelar' },
    });
    const aceptar = h('button', {
      clase: 'boton boton--primario',
      texto: textoConfirmar,
      atributos: { type: 'button' },
      datos: { accion: 'confirmar' },
    });
    const { cerrar } = abrirDialogo({
      titulo,
      cuerpo,
      acciones: [cancelar, aceptar],
      alCerrar: () => resolver(null),
    });
    campo.select();
    const aceptarTexto = () => {
      resolver(campo.value.trim());
      cerrar();
    };
    campo.addEventListener('keydown', (evento) => {
      if (evento.key === 'Enter') {
        evento.preventDefault();
        aceptarTexto();
      }
    });
    cancelar.addEventListener('click', () => {
      resolver(null);
      cerrar();
    });
    aceptar.addEventListener('click', aceptarTexto);
  });
}
