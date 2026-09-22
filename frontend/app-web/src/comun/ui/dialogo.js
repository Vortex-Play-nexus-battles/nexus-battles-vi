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
 *          peligro?: boolean, cerrableFuera?: boolean, raiz?: HTMLElement}} opciones
 * @returns {{elemento: HTMLElement, cerrar: () => void}}
 */
export function abrirDialogo({
  titulo,
  cuerpo,
  acciones = [],
  peligro = false,
  cerrableFuera = true,
  raiz = document.body,
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

  const cerrar = () => {
    velo.remove();
    document.removeEventListener('keydown', alTeclado);
    if (devolverFocoA instanceof HTMLElement) {
      devolverFocoA.focus();
    }
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
 * @param {{titulo: string, mensaje: string, textoConfirmar?: string,
 *          textoCancelar?: string, peligro?: boolean}} opciones
 * @returns {Promise<boolean>} true si se confirmo
 */
export function confirmar({
  titulo,
  mensaje,
  textoConfirmar = 'Confirmar',
  textoCancelar = 'Cancelar',
  peligro = true,
}) {
  return new Promise((resolver) => {
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
      cuerpo: mensaje,
      acciones: [cancelar, aceptar],
      peligro,
    });

    cancelar.addEventListener('click', () => {
      cerrar();
      resolver(false);
    });
    aceptar.addEventListener('click', () => {
      cerrar();
      resolver(true);
    });
  });
}
