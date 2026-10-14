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
 *
 * Auditoría de DEV del 30-sep: «Eliminar tu comentario», abierto desde la
 * ficha del producto, quedaba DETRÁS de la ficha. No era un z-index que
 * faltara en un sitio: la ficha tenía capa propia (z-index 10) y el velo
 * ninguna, así que el orden del DOM —que es lo que el kit usa para apilar—
 * no servía. Ahora los modales (diálogos y fichas) comparten una capa,
 * `--capa-modal`, y entre ellos manda el orden: lo que se abre después queda
 * encima. Con varios abiertos, Escape cierra solo el de más arriba
 * ({@link esElModalDeArriba}) y la página de atrás no se desplaza mientras
 * haya alguno ({@link bloquearDesplazamiento}).
 */

import { h } from './dom.js';

let contador = 0;

/** Los modales que hay en el documento: diálogos (velo) y fichas de producto. */
const MODALES = '.velo, .ficha-capa';

/**
 * Mientras haya un modal abierto, la página de atrás no se desplaza. Se suelta
 * cuando ya no queda ninguno EN EL DOCUMENTO —no con un contador—: con una
 * ficha y su confirmación abiertas, cerrar la confirmación no suelta la
 * página, y un modal que alguien quitó a mano no la deja bloqueada para
 * siempre. Hay que llamarlo después de quitar el modal del documento.
 *
 * @returns {() => void} con qué soltarlo (una sola vez)
 */
export function bloquearDesplazamiento() {
  document.documentElement.classList.add('con-modal');
  let suelto = false;
  return () => {
    if (suelto) {
      return;
    }
    suelto = true;
    if (!document.querySelector(MODALES)) {
      document.documentElement.classList.remove('con-modal');
    }
  };
}

/**
 * Si `capa` es el modal de más arriba: el último `.velo` o `.ficha-capa` del
 * documento (comparten capa, así que el último es el que se ve encima).
 *
 * @param {Element} capa el velo de un diálogo o la capa de una ficha
 * @returns {boolean}
 */
export function esElModalDeArriba(capa) {
  const modales = document.querySelectorAll(MODALES);
  return modales.length > 0 && modales[modales.length - 1] === capa;
}

/**
 * @param {{titulo: string, cuerpo: Node|string, acciones?: Array<Node>,
 *          peligro?: boolean, cerrableFuera?: boolean, raiz?: HTMLElement,
 *          alCerrar?: (() => void)|null, variante?: 'lateral'|null}} opciones
 *   `alCerrar` se llama una sola vez, cierre quien cierre: un botón, Escape,
 *   la equis o el velo. `variante: 'lateral'` lo abre como panel pegado al
 *   borde derecho (la ficha rápida de la consola), con las mismas garantías.
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
  variante = null,
}) {
  contador += 1;
  const idTitulo = `dialogo-titulo-${contador}`;
  const devolverFocoA = document.activeElement;

  const lateral = variante === 'lateral';
  const velo = h('div', {
    clase: lateral ? 'velo velo--lateral' : 'velo',
    datos: { zona: 'velo' },
  });
  const caja = h('div', {
    clase: `dialogo${peligro ? ' dialogo--peligro' : ''}${lateral ? ' dialogo--lateral' : ''}`,
    atributos: { role: 'dialog', 'aria-modal': 'true', 'aria-labelledby': idTitulo },
  });

  const encabezado = h('h2', { clase: 'dialogo__titulo', texto: titulo });
  encabezado.id = idTitulo;
  caja.append(encabezado);
  caja.append(typeof cuerpo === 'string' ? h('p', { texto: cuerpo }) : cuerpo);

  let cerrado = false;
  const soltarDesplazamiento = bloquearDesplazamiento();
  const cerrar = () => {
    if (cerrado) {
      return;
    }
    cerrado = true;
    velo.remove();
    soltarDesplazamiento();
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
    // Con otro modal encima (una confirmación abierta desde este diálogo), las
    // teclas son suyas: Escape lo cierra a él, no a los dos.
    if (!esElModalDeArriba(velo)) {
      return;
    }
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

/**
 * Confirmación de una acción administrativa con su motivo — 7.3.5 y 7.3.9.
 *
 * Una acción que cambia la cuenta de otra persona (suspenderla, banearla,
 * cambiarle el rol, quitarle el segundo factor) queda en la auditoría con
 * quién, cuándo y **por qué**. Un «¿Está seguro?» no deja nada de eso: aquí
 * el motivo es un campo obligatorio del propio diálogo, con su mínimo de
 * caracteres, y el botón no se activa hasta que lo cumple. Para las más
 * graves se puede pedir además que se escriba una palabra (el apodo de la
 * cuenta), como en {@link confirmarCritico}.
 *
 * Lo que va a pasar se lista antes (`consecuencias`), en frases: nadie
 * confirma a ciegas.
 *
 * @param {{titulo: string, mensaje?: string, consecuencias?: string[],
 *   etiquetaMotivo?: string, pistaMotivo?: string, motivoInicial?: string,
 *   minimo?: number, maximo?: number, palabra?: string|null,
 *   textoConfirmar?: string, textoCancelar?: string, peligro?: boolean}} opciones
 * @returns {Promise<string|null>} el motivo (sin espacios de los bordes) o null si se canceló
 */
export function confirmarConMotivo(opciones) {
  return confirmarConMotivoYSeleccion({ ...opciones, seleccion: null }).then((resultado) =>
    resultado === null ? null : resultado.motivo,
  );
}

/**
 * Como {@link confirmarConMotivo}, con una elección obligatoria antes del
 * motivo — SA-4: la causal de una sanción (§7.3.2). El botón no se activa
 * hasta que hay una opción elegida y el motivo cumple su mínimo.
 *
 * @param {Parameters<typeof confirmarConMotivo>[0] & {seleccion: {etiqueta: string,
 *   opciones: Array<[string, string]>, vacio?: string, pista?: string|null,
 *   inicial?: string|null}|null}} opciones
 * @returns {Promise<{seleccion: string|null, motivo: string}|null>} null si se canceló
 */
export function confirmarConMotivoYSeleccion({
  titulo,
  mensaje = '',
  consecuencias = [],
  etiquetaMotivo = 'Motivo',
  pistaMotivo = 'Queda en el registro de auditoría con tu nombre y la fecha.',
  motivoInicial = '',
  minimo = 5,
  maximo = 500,
  palabra = null,
  textoConfirmar = 'Confirmar',
  textoCancelar = 'Cancelar',
  peligro = true,
  seleccion = null,
}) {
  contador += 1;
  const idMotivo = `motivo-${contador}`;
  const idPalabra = `palabra-${contador}`;
  const idSeleccion = `seleccion-${contador}`;
  const esperada = palabra === null ? null : String(palabra).trim();

  const desplegable =
    seleccion === null
      ? null
      : h('select', {
          clase: 'desplegable__control',
          atributos: {
            id: idSeleccion,
            required: true,
            'aria-describedby': seleccion.pista ? `${idSeleccion}-pista` : null,
          },
          hijos: [
            h('option', { texto: seleccion.vacio ?? 'Elige una opción', atributos: { value: '' } }),
            ...seleccion.opciones.map(([valor, texto]) =>
              h('option', { texto, atributos: { value: valor } }),
            ),
          ],
        });
  if (desplegable && seleccion.inicial) {
    desplegable.value = seleccion.inicial;
  }

  const motivo = h('textarea', {
    clase: 'campo__control',
    atributos: {
      id: idMotivo,
      rows: 3,
      maxlength: maximo,
      required: true,
      'aria-describedby': `${idMotivo}-pista`,
    },
  });
  motivo.value = String(motivoInicial ?? '').slice(0, maximo);
  const pista = h('p', {
    clase: 'campo__pista',
    texto: `${pistaMotivo} Mínimo ${minimo} caracteres.`,
    atributos: { id: `${idMotivo}-pista` },
  });
  const campoPalabra =
    esperada === null
      ? null
      : h('input', {
          clase: 'campo__control',
          atributos: {
            id: idPalabra,
            type: 'text',
            autocomplete: 'off',
            spellcheck: 'false',
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
      desplegable
        ? h('div', {
            clase: 'desplegable',
            hijos: [
              h('label', {
                clase: 'campo__etiqueta',
                texto: seleccion.etiqueta,
                atributos: { for: idSeleccion },
              }),
              desplegable,
              seleccion.pista
                ? h('p', {
                    clase: 'campo__pista',
                    texto: seleccion.pista,
                    atributos: { id: `${idSeleccion}-pista` },
                  })
                : null,
            ],
          })
        : null,
      h('div', {
        clase: 'campo',
        hijos: [
          h('label', {
            clase: 'campo__etiqueta',
            texto: etiquetaMotivo,
            atributos: { for: idMotivo },
          }),
          motivo,
          pista,
        ],
      }),
      campoPalabra
        ? h('div', {
            clase: 'campo',
            hijos: [
              h('label', {
                clase: 'campo__etiqueta',
                texto: `Escribe «${esperada}» para confirmar`,
                atributos: { for: idPalabra },
              }),
              campoPalabra,
            ],
          })
        : null,
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
      clase: `boton ${peligro ? 'boton--peligro' : 'boton--primario'}`,
      texto: textoConfirmar,
      atributos: { type: 'button', disabled: true },
      datos: { accion: 'confirmar' },
    });
    const listo = () =>
      (desplegable === null || desplegable.value !== '') &&
      motivo.value.trim().length >= minimo &&
      (esperada === null || (campoPalabra.value.trim() === esperada && esperada !== ''));
    const actualizar = () => {
      aceptar.disabled = !listo();
    };
    motivo.addEventListener('input', actualizar);
    campoPalabra?.addEventListener('input', actualizar);
    desplegable?.addEventListener('change', actualizar);
    actualizar();

    const { cerrar } = abrirDialogo({
      titulo,
      cuerpo,
      acciones: [cancelar, aceptar],
      peligro,
      alCerrar: () => resolver(null),
    });
    (desplegable ?? motivo).focus();

    cancelar.addEventListener('click', () => {
      resolver(null);
      cerrar();
    });
    aceptar.addEventListener('click', () => {
      if (!listo()) {
        return;
      }
      resolver({ seleccion: desplegable ? desplegable.value : null, motivo: motivo.value.trim() });
      cerrar();
    });
  });
}
