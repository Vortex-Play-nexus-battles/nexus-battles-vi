/**
 * Campos de formulario (`.campo` en el kit) con etiqueta, pista, error
 * debajo del control y, para las contraseñas, el interruptor de ver/ocultar.
 *
 * Lo que este módulo garantiza y antes no pasaba en ninguna vista de forma
 * consistente:
 *
 *   - la etiqueta está asociada al control por `for`/`id`, no por proximidad;
 *   - el error vive en un elemento propio referenciado con `aria-describedby`
 *     y `aria-invalid`, para que un lector de pantalla lo lea al entrar al
 *     campo y no solo cuando alguien mira la pantalla;
 *   - el criterio de aceptación de HU-SAL-001 —«indicar el motivo del
 *     rechazo», no solo que falló— se cumple por construcción.
 */

import { h } from './dom.js';

let contador = 0;

/**
 * @param {{nombre: string, etiqueta: string, tipo?: string, valor?: string,
 *          pista?: string|null, requerido?: boolean, autocompletar?: string|null,
 *          atributos?: Record<string, string|number|boolean|null>,
 *          multilinea?: boolean, opciones?: Array<{valor: string, texto: string}>|null}} opciones
 * @returns {{elemento: HTMLElement, control: HTMLElement, marcarError: (motivo: string|null) => void}}
 */
export function campo({
  nombre,
  etiqueta,
  tipo = 'text',
  valor = '',
  pista = null,
  requerido = false,
  autocompletar = null,
  atributos = {},
  multilinea = false,
  opciones = null,
}) {
  contador += 1;
  const id = `campo-${nombre}-${contador}`;
  const idError = `${id}-error`;
  const idPista = `${id}-pista`;

  const caja = h('div', { clase: 'campo', datos: { campo: nombre } });
  caja.append(h('label', { clase: 'campo__etiqueta', texto: etiqueta, atributos: { for: id } }));

  let control;
  if (opciones) {
    // UXC-7 — sin `desplegable`: esa clase del kit es la del contenedor
    // (flex en columna), y puesta en el propio `<select>` dejaba el texto
    // pegado arriba del control.
    control = h('select', { clase: 'campo__control', atributos: { id, name: nombre } });
    for (const opcion of opciones) {
      control.append(h('option', { texto: opcion.texto, atributos: { value: opcion.valor } }));
    }
    control.value = valor;
  } else if (multilinea) {
    control = h('textarea', {
      clase: 'campo__control campo--area',
      atributos: { id, name: nombre, rows: 4 },
    });
    control.value = valor;
  } else {
    control = h('input', {
      clase: 'campo__control',
      atributos: { id, name: nombre, type: tipo, value: valor },
    });
  }
  if (requerido) {
    control.setAttribute('required', '');
  }
  if (autocompletar) {
    control.setAttribute('autocomplete', autocompletar);
  }
  for (const [clave, dato] of Object.entries(atributos)) {
    if (dato !== null && dato !== false) {
      control.setAttribute(clave, dato === true ? '' : String(dato));
    }
  }

  // Una contraseña que no se puede mirar se escribe mal, y quien la escribe
  // mal culpa al formulario. El interruptor es un `button`, no un icono
  // decorativo: se alcanza con el teclado y dice en qué estado está.
  if (tipo === 'password') {
    const envoltorio = h('div', { clase: 'campo__con-accion' });
    const interruptor = h('button', {
      clase: 'campo__accion',
      texto: 'Ver',
      atributos: { type: 'button', 'aria-controls': id, 'aria-pressed': 'false' },
      datos: { accion: 'ver-password' },
    });
    interruptor.addEventListener('click', () => {
      const visible = control.getAttribute('type') === 'text';
      control.setAttribute('type', visible ? 'password' : 'text');
      interruptor.setAttribute('aria-pressed', String(!visible));
      interruptor.textContent = visible ? 'Ver' : 'Ocultar';
    });
    envoltorio.append(control, interruptor);
    caja.append(envoltorio);
  } else {
    caja.append(control);
  }

  if (pista) {
    const nodoPista = h('p', { clase: 'campo__pista', texto: pista });
    nodoPista.id = idPista;
    caja.append(nodoPista);
    control.setAttribute('aria-describedby', idPista);
  }

  const error = h('p', { clase: 'campo__error', atributos: { hidden: true } });
  error.id = idError;
  caja.append(error);

  /**
   * @param {string|null} motivo texto del rechazo; `null` lo limpia
   */
  function marcarError(motivo) {
    const hayError = Boolean(motivo);
    caja.classList.toggle('campo--invalido', hayError);
    error.textContent = motivo ?? '';
    error.hidden = !hayError;
    control.setAttribute('aria-invalid', String(hayError));
    const descripciones = [pista ? idPista : null, hayError ? idError : null].filter(Boolean);
    if (descripciones.length > 0) {
      control.setAttribute('aria-describedby', descripciones.join(' '));
    } else {
      control.removeAttribute('aria-describedby');
    }
  }

  return { elemento: caja, control, marcarError };
}

/**
 * Añade el interruptor ver/ocultar a un `<input type="password">` que ya está
 * escrito en el HTML.
 *
 * Existe porque los formularios de entrada al juego son marcado estático —no
 * hay nada que decidir en tiempo de ejecución— y aun así necesitan el mismo
 * interruptor que construye `campo()`. Duplicarlo en cada vista era
 * exactamente lo que este kit viene a terminar.
 *
 * @param {HTMLInputElement|null} control
 * @returns {HTMLButtonElement|null} el interruptor, o null si no había campo
 */
export function mejorarContrasena(control) {
  if (!control || control.type !== 'password' || control.dataset.conInterruptor === 'si') {
    return null;
  }
  const envoltorio = h('div', { clase: 'campo__con-accion' });
  control.replaceWith(envoltorio);
  const interruptor = h('button', {
    clase: 'campo__accion',
    texto: 'Ver',
    atributos: { type: 'button', 'aria-controls': control.id, 'aria-pressed': 'false' },
    datos: { accion: 'ver-password' },
  });
  interruptor.addEventListener('click', () => {
    const visible = control.getAttribute('type') === 'text';
    control.setAttribute('type', visible ? 'password' : 'text');
    interruptor.setAttribute('aria-pressed', String(!visible));
    interruptor.textContent = visible ? 'Ver' : 'Ocultar';
  });
  envoltorio.append(control, interruptor);
  control.dataset.conInterruptor = 'si';
  return interruptor;
}

/**
 * Marca o limpia el error de un campo escrito en el HTML (mismo contrato que
 * `marcarError` de `campo()`, para marcado estático).
 *
 * @param {HTMLElement|null} control
 * @param {string|null} motivo
 */
export function marcarErrorDe(control, motivo) {
  if (!control) {
    return;
  }
  const caja = control.closest('.campo');
  const hayError = Boolean(motivo);
  if (caja) {
    caja.classList.toggle('campo--invalido', hayError);
    let error = caja.querySelector('.campo__error');
    if (!error) {
      error = h('p', { clase: 'campo__error' });
      error.id = `${control.id}-error`;
      caja.append(error);
    }
    error.textContent = motivo ?? '';
    error.hidden = !hayError;
    if (hayError) {
      control.setAttribute('aria-describedby', error.id);
    } else {
      control.removeAttribute('aria-describedby');
    }
  }
  control.setAttribute('aria-invalid', String(hayError));
}

/**
 * Reparte los `errores[]` de un problem detail entre los campos de un
 * formulario construido con `campo()`, y devuelve el primero para enfocarlo.
 *
 * @param {Map<string, {marcarError: Function, control: HTMLElement}>} campos por nombre
 * @param {Array<{campo: string, mensaje: string}>} errores
 * @returns {HTMLElement|null}
 */
export function marcarErroresDeCampo(campos, errores) {
  for (const uno of campos.values()) {
    uno.marcarError(null);
  }
  let primero = null;
  for (const error of errores ?? []) {
    const destino = campos.get(error.campo);
    if (destino) {
      destino.marcarError(error.mensaje);
      primero = primero ?? destino.control;
    }
  }
  return primero;
}
