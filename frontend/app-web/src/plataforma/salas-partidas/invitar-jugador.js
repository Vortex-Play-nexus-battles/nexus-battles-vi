/**
 * Invitar a un jugador a la sala, buscándolo por su apodo — revisión del modo
 * jugador del 6-oct, punto 13 («para invitar a alguien debe haber una opción
 * que diga invitar amigos o buscar por nombre, algo bien diseñado»).
 *
 * El anfitrión escribe parte del apodo (mínimo 3 letras), elige de la lista y
 * pulsa «Invitar». Por debajo:
 *
 *   - la búsqueda es la pública de ms-identidad (`GET /perfiles/publicos`),
 *     la misma de los mensajes privados y del compañero de torneo;
 *   - la invitación es `POST /salas/{id}/invitaciones` (salas-partidas
 *     1.10.0): al invitado le llega un aviso a su bandeja con la sala y, si es
 *     privada, el código.
 *
 * El identificador del jugador viaja, pero no se enseña nunca: en pantalla
 * solo hay apodos. No hay sistema de amigos: no se guarda nada.
 *
 * Accesible: el panel se abre con un botón con `aria-expanded`, Escape lo
 * cierra y devuelve el foco al botón, los resultados son botones de verdad y
 * lo que pasa se anuncia en una región viva (sin repetirlo en cada tecla).
 *
 * @module invitar-jugador
 */

import { h } from '../../comun/ui/dom.js';
import { textoDeError } from '../../comun/ui/texto-de-fallo.js';
import { buscarJugadores, invitarASala, MINIMO_DE_BUSQUEDA_DE_JUGADOR } from './cliente-salas.js';

/** Espera tras la última tecla antes de buscar: no se pregunta por cada letra. */
export const DEMORA_DE_BUSQUEDA_MS = 300;

/**
 * Lo que se dice tras invitar.
 *
 * @param {{apodo: string, enviada: boolean}} resultado
 * @returns {string}
 */
export function textoDeInvitacion(resultado) {
  return resultado.enviada
    ? `Invitación enviada a ${resultado.apodo}.`
    : `${resultado.apodo} ya tenía tu invitación.`;
}

/**
 * Monta el panel de invitar sobre `[data-zona="panel-invitar"]` y su botón
 * `[data-accion="abrir-invitar"]`.
 *
 * Solo el anfitrión invita (el servidor lo comprueba igual); a los demás no
 * se les ofrece. Tampoco cuando la sala no tiene plazas libres.
 *
 * @param {ParentNode} raiz
 * @param {object} opciones
 * @param {{id: string, participantes?: string[]}} opciones.sala
 * @param {boolean} opciones.esAnfitrion
 * @param {string|null} [opciones.yo]
 * @param {(apodo: string, opciones?: object) => Promise<Array<{uid: string, apodo: string}>>}
 *   [opciones.buscar]
 * @param {(idSala: string, idJugador: string) => Promise<{idJugador: string, apodo: string,
 *   enviada: boolean}>} [opciones.invitar]
 * @param {number} [opciones.demora]
 * @returns {{actualizar: (estado: {ocupacion?: {actual: number, maximo: number},
 *   participantes?: string[]}) => void, cerrar: () => void} | null}
 */
export function montarInvitarJugador(
  raiz,
  {
    sala,
    esAnfitrion,
    yo = null,
    buscar = buscarJugadores,
    invitar = invitarASala,
    demora = DEMORA_DE_BUSQUEDA_MS,
  } = {},
) {
  const panel = raiz.querySelector('[data-zona="panel-invitar"]');
  const abrir = raiz.querySelector('[data-accion="abrir-invitar"]');
  if (!panel || !abrir || !sala?.id) {
    return null;
  }
  if (!esAnfitrion) {
    abrir.hidden = true;
    panel.hidden = true;
    return null;
  }

  /** Quien ya está dentro no sale en la lista: invitarle sería un 409. */
  let dentro = new Set(Array.isArray(sala.participantes) ? sala.participantes : []);
  let hayPlazas = Number(sala.ocupacion ?? 0) < Number(sala.maximoParticipantes ?? 0);

  const campo = h('input', {
    clase: 'campo__control',
    atributos: {
      id: 'buscar-invitado',
      type: 'search',
      name: 'apodo',
      autocomplete: 'off',
      spellcheck: 'false',
      maxlength: 50,
      placeholder: 'Apodo del jugador',
      'aria-describedby': 'buscar-invitado-pista',
    },
  });
  const resultados = h('div', {
    clase: 'buscador-jugador__resultados',
    datos: { zona: 'invitados' },
  });
  const estado = h('p', {
    clase: 'sala-espera__acuse t-meta',
    datos: { zona: 'acuse-invitacion' },
    atributos: { role: 'status', 'aria-live': 'polite' },
  });
  const cerrarBoton = h('button', {
    clase: 'boton boton--secundario boton--pequeno',
    texto: 'Cerrar',
    atributos: { type: 'button' },
    datos: { accion: 'cerrar-invitar' },
  });
  const formulario = h('form', {
    clase: 'buscador-jugador',
    atributos: { role: 'search', 'aria-label': 'Buscar un jugador para invitarlo' },
    hijos: [
      h('div', {
        clase: 'campo',
        hijos: [
          h('label', {
            clase: 'campo__etiqueta',
            texto: 'Invitar por apodo',
            atributos: { for: 'buscar-invitado' },
          }),
          campo,
          h('p', {
            clase: 'campo__pista',
            texto: `Escribe al menos ${MINIMO_DE_BUSQUEDA_DE_JUGADOR} letras de su apodo.`,
            atributos: { id: 'buscar-invitado-pista' },
          }),
        ],
      }),
      resultados,
    ],
  });
  panel.replaceChildren(
    h('div', {
      clase: 'sala-espera__invitar-cabecera',
      hijos: [h('p', { clase: 'tarjeta__titulo', texto: 'Invitar jugador' }), cerrarBoton],
    }),
    formulario,
    estado,
  );

  let temporizador = null;
  let consulta = 0;

  function decir(texto, error = false) {
    estado.textContent = texto;
    estado.dataset.tono = error ? 'error' : 'info';
  }

  function pintarAyuda(texto, error = false) {
    resultados.replaceChildren(
      h('p', {
        clase: `buscador-jugador__estado${error ? ' buscador-jugador__estado--error' : ' t-meta'}`,
        texto,
      }),
    );
  }

  async function invitarA(jugador, boton) {
    boton.disabled = true;
    decir(`Invitando a ${jugador.apodo}…`);
    try {
      const resultado = await invitar(sala.id, jugador.uid);
      const texto = textoDeInvitacion({
        apodo: resultado?.apodo ?? jugador.apodo,
        enviada: Boolean(resultado?.enviada),
      });
      decir(texto);
      const accion = boton.querySelector('.buscador-jugador__accion');
      if (accion) {
        accion.textContent = resultado?.enviada ? 'Invitado' : 'Ya invitado';
      }
      boton.dataset.invitado = 'si';
    } catch (error) {
      // Rechazos con su motivo (no eres el anfitrión, la sala está completa,
      // no encontramos a ese jugador): lo redacta el servicio.
      decir(textoDeError(error, 'No pudimos enviar la invitación ahora.'), true);
      boton.disabled = false;
    }
  }

  async function ejecutarBusqueda(texto) {
    const esta = ++consulta;
    pintarAyuda('Buscando…');
    let jugadores;
    try {
      jugadores = await buscar(texto, { yo });
    } catch {
      if (esta === consulta) {
        pintarAyuda('No pudimos buscar ahora. Vuelve a intentarlo en un momento.', true);
      }
      return;
    }
    if (esta !== consulta) {
      return;
    }
    const otros = (jugadores ?? []).filter((jugador) => !dentro.has(jugador.uid));
    if (otros.length === 0) {
      pintarAyuda(`Ningún jugador con un apodo como «${texto}» puede entrar ahora.`);
      return;
    }
    const lista = h('ul', {
      clase: 'buscador-jugador__lista',
      atributos: { 'aria-label': 'Jugadores encontrados' },
    });
    for (const jugador of otros) {
      const boton = h('button', {
        clase: 'buscador-jugador__resultado',
        atributos: { type: 'button', 'aria-label': `Invitar a ${jugador.apodo}` },
        datos: { invitar: 'jugador' },
        hijos: [
          h('span', { clase: 'buscador-jugador__apodo', texto: jugador.apodo }),
          h('span', {
            clase: 'buscador-jugador__accion',
            texto: 'Invitar',
            atributos: { 'aria-hidden': 'true' },
          }),
        ],
      });
      boton.addEventListener('click', () => invitarA(jugador, boton));
      lista.append(h('li', { hijos: [boton] }));
    }
    resultados.replaceChildren(lista);
  }

  function programarBusqueda() {
    clearTimeout(temporizador);
    const texto = String(campo.value ?? '').trim();
    if (texto.length < MINIMO_DE_BUSQUEDA_DE_JUGADOR) {
      consulta += 1;
      resultados.replaceChildren();
      return;
    }
    temporizador = setTimeout(() => ejecutarBusqueda(texto), demora);
  }

  campo.addEventListener('input', programarBusqueda);
  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    clearTimeout(temporizador);
    const texto = String(campo.value ?? '').trim();
    if (texto.length >= MINIMO_DE_BUSQUEDA_DE_JUGADOR) {
      ejecutarBusqueda(texto);
    } else {
      pintarAyuda(`Escribe al menos ${MINIMO_DE_BUSQUEDA_DE_JUGADOR} letras del apodo.`);
    }
  });

  function abrirPanel() {
    panel.hidden = false;
    abrir.setAttribute('aria-expanded', 'true');
    campo.focus();
  }

  function cerrar() {
    clearTimeout(temporizador);
    panel.hidden = true;
    abrir.setAttribute('aria-expanded', 'false');
  }

  abrir.addEventListener('click', () => {
    if (panel.hidden) {
      abrirPanel();
    } else {
      cerrar();
      abrir.focus();
    }
  });
  cerrarBoton.addEventListener('click', () => {
    cerrar();
    abrir.focus();
  });
  panel.addEventListener('keydown', (evento) => {
    if (evento.key === 'Escape') {
      evento.preventDefault();
      cerrar();
      abrir.focus();
    }
  });

  function pintarDisponibilidad() {
    abrir.hidden = !hayPlazas;
    if (!hayPlazas && !panel.hidden) {
      cerrar();
    }
  }

  panel.hidden = true;
  abrir.setAttribute('aria-expanded', 'false');
  pintarDisponibilidad();

  return {
    actualizar(estadoDeLaSala) {
      if (Array.isArray(estadoDeLaSala?.participantes)) {
        dentro = new Set(estadoDeLaSala.participantes);
      }
      if (estadoDeLaSala?.ocupacion) {
        hayPlazas =
          Number(estadoDeLaSala.ocupacion.actual) < Number(estadoDeLaSala.ocupacion.maximo);
        pintarDisponibilidad();
      }
    },
    cerrar,
  };
}
