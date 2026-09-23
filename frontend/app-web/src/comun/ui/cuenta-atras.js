/**
 * Cuenta atras viva — UX-R2.8b.
 *
 * ## Por que
 *
 * La vitrina de subastas pinta «42m» junto a cada lote. Ese numero se calcula
 * una vez, cuando se renderiza la tarjeta, y **no se vuelve a tocar**. El
 * propio `subastas-vitrina.js` lo dice en un comentario: mantenerlo al dia es
 * responsabilidad de quien llama. Nadie lo estaba haciendo.
 *
 * El resultado en pantalla es una subasta que dice «2m» durante media hora.
 * En un mercado con puja, el tiempo restante no es decoracion: es el dato con
 * el que alguien decide si puja ahora o espera. Un contador congelado no es un
 * detalle estetico, es informacion falsa.
 *
 * ## Como
 *
 * Un solo `setInterval` para toda la pagina, no uno por tarjeta: con
 * dieciseis lotes eso serian dieciseis temporizadores haciendo el mismo
 * trabajo. Los elementos se marcan con `data-termina-en` (ISO 8601) y el
 * latido los recorre.
 *
 * La cadencia cambia con lo que queda, porque la precision que importa
 * tambien cambia: a dos dias vista nadie necesita ver los segundos, y a dos
 * minutos del cierre son lo unico que importa.
 *
 *   - mas de una hora → «2d», «18h», se refresca cada 30 s
 *   - menos de una hora → «04:31», se refresca cada segundo
 *   - menos de diez minutos → ademas `--urgente`
 *   - vencida → «Finalizada», y deja de contar
 *
 * `--urgente` es color y peso, **no parpadeo**: la regla de movimiento de
 * UX-R2.10 (`prefers-reduced-motion` no puede perder informacion) se cumple
 * sola si la urgencia nunca se codifica solo en una animacion.
 */

import { h } from './dom.js';

/** Umbral por debajo del cual se muestran minutos y segundos. */
const UNA_HORA_MS = 3_600_000;

/** Umbral de urgencia. Diez minutos es lo que dura una guerra de pujas. */
const URGENTE_MS = 600_000;

/**
 * Elemento de cuenta atras para una fecha de cierre.
 *
 * Es un `<time>` con `datetime`: un lector de pantalla y un buscador leen la
 * fecha real, no el texto abreviado.
 *
 * @param {string} fechaFinIso fecha de cierre en ISO 8601.
 * @param {{clase?: string, prefijo?: string}} [opciones]
 * @returns {HTMLTimeElement}
 */
export function cuentaAtras(fechaFinIso, { clase = '', prefijo = 'Termina en' } = {}) {
  const elemento = h('time', {
    clase: `cuenta-atras ${clase}`.trim(),
    datos: { terminaEn: fechaFinIso },
    atributos: { datetime: fechaFinIso },
  });
  refrescarUno(elemento, Date.now(), prefijo);
  return elemento;
}

/**
 * Marca un elemento que ya existe para que el latido lo mantenga al dia.
 *
 * Existe para las vitrinas que construye un modulo que no se puede tocar
 * (`subastas-vitrina.js` es un modulo protegido): la tarjeta se pinta como
 * siempre y luego se adopta su contador.
 *
 * @param {HTMLElement} elemento
 * @param {string} fechaFinIso
 * @param {{prefijo?: string}} [opciones]
 * @returns {HTMLElement} el mismo elemento
 */
export function adoptarCuentaAtras(elemento, fechaFinIso, { prefijo = 'Termina en' } = {}) {
  elemento.classList.add('cuenta-atras');
  elemento.dataset.terminaEn = fechaFinIso;
  refrescarUno(elemento, Date.now(), prefijo);
  return elemento;
}

/**
 * Pone al dia todas las cuentas atras que haya dentro de una raiz.
 *
 * Separada del temporizador a proposito: asi una prueba puede adelantar el
 * reloj y comprobar el texto sin esperar un segundo real.
 *
 * @param {ParentNode} raiz
 * @param {{ahora?: number, prefijo?: string}} [opciones]
 * @returns {number} cuantas siguen contando (0 = ya no hace falta latir)
 */
export function latir(raiz, { ahora = Date.now(), prefijo = 'Termina en' } = {}) {
  let vivas = 0;
  for (const elemento of raiz.querySelectorAll('[data-termina-en]')) {
    if (refrescarUno(elemento, ahora, prefijo)) {
      vivas += 1;
    }
  }
  return vivas;
}

/**
 * Arranca el latido sobre una raiz y devuelve como pararlo.
 *
 * La cadencia se recalcula en cada vuelta: mientras todo esta lejos late cada
 * 30 s, y baja a 1 s en cuanto algo entra en la ultima hora. Cuando no queda
 * ninguna viva, se detiene solo.
 *
 * @param {ParentNode} raiz
 * @param {{prefijo?: string, temporizador?: {fijar: Function, quitar: Function}}} [opciones]
 * @returns {() => void} funcion para detenerlo
 */
export function vigilarCuentasAtras(raiz, { prefijo = 'Termina en', temporizador } = {}) {
  const reloj = temporizador ?? {
    fijar: (fn, ms) => setTimeout(fn, ms),
    quitar: (id) => clearTimeout(id),
  };
  let pendiente = null;
  let detenido = false;

  const vuelta = () => {
    if (detenido) {
      return;
    }
    const vivas = latir(raiz, { prefijo });
    if (vivas === 0) {
      return; // Todas finalizadas: no hay nada que contar.
    }
    pendiente = reloj.fijar(vuelta, cadencia(raiz));
  };

  vuelta();

  return () => {
    detenido = true;
    if (pendiente !== null) {
      reloj.quitar(pendiente);
    }
  };
}

/**
 * Cuanto esperar hasta el siguiente refresco: 1 s si algo esta en su ultima
 * hora, 30 s si no. Se mira la mas urgente de todas.
 *
 * @param {ParentNode} raiz
 * @returns {number} milisegundos
 */
function cadencia(raiz) {
  const ahora = Date.now();
  for (const elemento of raiz.querySelectorAll('[data-termina-en]')) {
    const restante = new Date(elemento.dataset.terminaEn).getTime() - ahora;
    if (restante > 0 && restante < UNA_HORA_MS) {
      return 1000;
    }
  }
  return 30_000;
}

/**
 * Pone al dia un solo elemento.
 *
 * @param {HTMLElement} elemento
 * @param {number} ahora
 * @param {string} prefijo
 * @returns {boolean} true si sigue contando.
 */
function refrescarUno(elemento, ahora, prefijo) {
  const fin = new Date(elemento.dataset.terminaEn).getTime();
  if (Number.isNaN(fin)) {
    // Una fecha ilegible no se convierte en «NaN» en la pantalla.
    elemento.textContent = '—';
    elemento.removeAttribute('aria-label');
    return false;
  }

  const restante = fin - ahora;
  if (restante <= 0) {
    elemento.textContent = 'Finalizada';
    elemento.classList.remove('cuenta-atras--urgente');
    elemento.classList.add('cuenta-atras--finalizada');
    elemento.setAttribute('aria-label', 'Finalizada');
    return false;
  }

  elemento.textContent = textoDe(restante);
  elemento.classList.remove('cuenta-atras--finalizada');
  elemento.classList.toggle('cuenta-atras--urgente', restante < URGENTE_MS);
  // El texto abreviado («04:31») no se lee bien en voz alta; la etiqueta si.
  elemento.setAttribute('aria-label', `${prefijo} ${enPalabras(restante)}`);
  return true;
}

/**
 * «2d», «18h», «04:31».
 *
 * @param {number} restanteMs
 * @returns {string}
 */
export function textoDe(restanteMs) {
  if (restanteMs <= 0) {
    return 'Finalizada';
  }
  if (restanteMs >= 86_400_000) {
    return `${Math.floor(restanteMs / 86_400_000)}d`;
  }
  if (restanteMs >= UNA_HORA_MS) {
    return `${Math.floor(restanteMs / UNA_HORA_MS)}h`;
  }
  const totalSegundos = Math.floor(restanteMs / 1000);
  const minutos = Math.floor(totalSegundos / 60);
  const segundos = totalSegundos % 60;
  return `${String(minutos).padStart(2, '0')}:${String(segundos).padStart(2, '0')}`;
}

/**
 * Lo mismo, pero dicho para que se pueda oir: «4 minutos y 31 segundos».
 *
 * @param {number} restanteMs
 * @returns {string}
 */
export function enPalabras(restanteMs) {
  if (restanteMs <= 0) {
    return 'nada, ya finalizo';
  }
  const dias = Math.floor(restanteMs / 86_400_000);
  if (dias >= 1) {
    return dias === 1 ? '1 día' : `${dias} días`;
  }
  const horas = Math.floor(restanteMs / UNA_HORA_MS);
  if (horas >= 1) {
    return horas === 1 ? '1 hora' : `${horas} horas`;
  }
  const minutos = Math.floor(restanteMs / 60_000);
  const segundos = Math.floor((restanteMs % 60_000) / 1000);
  if (minutos === 0) {
    return segundos === 1 ? '1 segundo' : `${segundos} segundos`;
  }
  const parteMinutos = minutos === 1 ? '1 minuto' : `${minutos} minutos`;
  if (segundos === 0) {
    return parteMinutos;
  }
  return `${parteMinutos} y ${segundos === 1 ? '1 segundo' : `${segundos} segundos`}`;
}
