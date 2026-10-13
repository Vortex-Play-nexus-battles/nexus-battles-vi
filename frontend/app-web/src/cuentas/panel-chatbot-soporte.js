/**
 * Pestaña «Soporte» del panel del asistente.
 *
 * Las solicitudes que abren los jugadores cuando el asistente no les resuelve
 * algo. El administrador las filtra por estado, abre una para leer el mensaje
 * y la conversación que la originó (ya sin contraseñas ni tarjetas), y la
 * atiende: la pone en revisión, la responde o la cierra. La respuesta es lo
 * que el jugador lee en la ventana del asistente.
 *
 * @module cuentas/panel-chatbot-soporte
 */

import { conCarga } from '../comun/ui/boton.js';
import { campo } from '../comun/ui/campo.js';
import { abrirDialogo } from '../comun/ui/dialogo.js';
import { distintivo } from '../comun/ui/distintivo.js';
import { h, vaciar } from '../comun/ui/dom.js';
import {
  estadoDeCarga,
  estadoDeError,
  estadoVacio,
  pintarEstado,
} from '../comun/ui/estado-vista.js';
import { fechaHora, numero } from '../comun/ui/formato.js';
import { encabezadoDeSeccion } from '../comun/ui/pagina.js';
import { CATEGORIAS, ESTADOS } from '../comun/ui/soporte-chatbot.js';
import { textoDeErrorDelPanel } from './panel-chatbot-analiticas.js';

/** Cuántas solicitudes por página. */
export const POR_PAGINA = 20;

/** Transiciones del contrato: a qué estados puede pasar cada uno. */
export const SIGUIENTES = Object.freeze({
  ABIERTO: ['EN_PROCESO', 'RESUELTO', 'CERRADO'],
  EN_PROCESO: ['RESUELTO', 'CERRADO'],
  RESUELTO: ['CERRADO', 'EN_PROCESO'],
  CERRADO: [],
});

const NOMBRE_DE_CATEGORIA = new Map(CATEGORIAS.map((c) => [c.valor, c.texto]));

/** @param {string} categoria @returns {string} */
export function nombreDeCategoria(categoria) {
  return NOMBRE_DE_CATEGORIA.get(categoria) ?? categoria;
}

function distintivoDeEstado(estado) {
  const conocido = ESTADOS[estado] ?? { texto: estado, variante: null };
  return distintivo(conocido.texto, conocido.variante);
}

/**
 * Texto de un fallo al atender. Por el `motivo`, luego por el estado.
 *
 * @param {{estado?: number, problema?: {motivo?: string}|null}} error
 * @returns {string}
 */
export function textoDeFalloAlAtender(error) {
  // ms-chatbot.yaml 1.3.9: el jugador solo puede tener una solicitud abierta.
  if (error?.problema?.motivo === 'OTRO_TICKET_ABIERTO') {
    return 'No se puede reabrir: este jugador ya tiene otra solicitud abierta. Atiende esa primero.';
  }
  if (error?.problema?.motivo === 'TRANSICION_NO_PERMITIDA' || error?.estado === 409) {
    return 'Ese cambio no está permitido para el estado actual de la solicitud. Para marcarla como respondida hace falta una respuesta.';
  }
  if (error?.estado === 404) {
    return 'La solicitud ya no existe.';
  }
  if (error?.estado === 400) {
    return 'Revisa la respuesta: admite hasta 2000 caracteres.';
  }
  return textoDeErrorDelPanel(error).titulo;
}

/**
 * Diálogo con el detalle de una solicitud y el formulario para atenderla.
 *
 * @param {{cliente: object, ticketId: string}} opciones
 * @returns {Promise<boolean>} true si se guardó un cambio
 */
export function atenderEnDialogo({ cliente, ticketId }) {
  return new Promise((resolver) => {
    const cuerpo = h('div', { clase: 'pila panel-chatbot__ticket' });
    pintarEstado(cuerpo, estadoDeCarga({ filas: 3, etiqueta: 'Cargando la solicitud…' }));

    const cerrarBoton = h('button', {
      clase: 'boton boton--secundario',
      texto: 'Cerrar',
      atributos: { type: 'button' },
      datos: { accion: 'cerrar-detalle' },
    });
    const guardar = h('button', {
      clase: 'boton boton--primario',
      texto: 'Guardar',
      atributos: { type: 'button', disabled: true },
      datos: { accion: 'guardar-ticket' },
    });
    const { cerrar } = abrirDialogo({
      titulo: 'Solicitud de soporte',
      cuerpo,
      acciones: [cerrarBoton, guardar],
      cerrableFuera: false,
    });
    cerrarBoton.addEventListener('click', () => {
      cerrar();
      resolver(false);
    });

    let formulario = null;

    cliente
      .obtenerTicket(ticketId)
      .then((ticket) => {
        formulario = pintarDetalle(cuerpo, ticket);
        guardar.disabled = formulario === null;
      })
      .catch((error) => {
        pintarEstado(cuerpo, estadoDeError(textoDeErrorDelPanel(error)));
      });

    guardar.addEventListener('click', async () => {
      if (!formulario) {
        return;
      }
      const datos = formulario.datos();
      if (!datos) {
        return;
      }
      formulario.avisar(null);
      conCarga(guardar, true, 'Guardando…');
      try {
        await cliente.atenderTicket(ticketId, datos);
        cerrar();
        resolver(true);
      } catch (error) {
        conCarga(guardar, false);
        formulario.avisar(textoDeFalloAlAtender(error));
      }
    });
  });
}

/**
 * Pinta el detalle y, si la solicitud se puede cambiar, el formulario.
 *
 * @returns {{datos: () => object|null, avisar: (texto: string|null) => void}|null}
 */
function pintarDetalle(cuerpo, ticket) {
  const datosDelTicket = h('dl', {
    clase: 'panel-chatbot__datos',
    hijos: [
      ['Asunto', ticket.asunto],
      ['Tema', nombreDeCategoria(ticket.categoria)],
      ['Jugador', ticket.uid],
      ['Abierta', fechaHora(ticket.creadoEn)],
    ].flatMap(([termino, valor]) => [h('dt', { texto: termino }), h('dd', { texto: valor ?? '' })]),
  });
  const estadoActual = h('p', {
    clase: 'panel-chatbot__estado-ticket',
    hijos: ['Estado: ', distintivoDeEstado(ticket.estado)],
  });

  const conversacion = (ticket.contexto ?? []).map((m) =>
    h('li', {
      clase: `panel-chatbot__contexto panel-chatbot__contexto--${m.remitente === 'BOT' ? 'bot' : 'jugador'}`,
      hijos: [
        h('p', {
          clase: 't-meta',
          texto: `${m.remitente === 'BOT' ? 'Asistente' : 'Jugador'} · ${fechaHora(m.fechaEnvio)}`,
        }),
        h('p', { texto: m.contenido }),
      ],
    }),
  );

  vaciar(cuerpo).append(
    datosDelTicket,
    estadoActual,
    h('p', { clase: 't-etiqueta', texto: 'Mensaje del jugador' }),
    h('p', { clase: 'panel-chatbot__mensaje-ticket', texto: ticket.mensaje }),
    h('p', { clase: 't-etiqueta', texto: 'Conversación con el asistente' }),
    conversacion.length > 0
      ? h('ol', { clase: 'panel-chatbot__conversacion', hijos: conversacion })
      : h('p', { clase: 't-meta', texto: 'No había conversación al abrir la solicitud.' }),
  );

  const siguientes = SIGUIENTES[ticket.estado] ?? [];
  if (siguientes.length === 0) {
    cuerpo.append(
      h('p', { clase: 't-meta', texto: 'La solicitud está cerrada y ya no se puede cambiar.' }),
    );
    if (ticket.respuesta) {
      cuerpo.append(h('p', { texto: `Respuesta enviada: ${ticket.respuesta}` }));
    }
    return null;
  }

  const estado = campo({
    nombre: 'estado',
    etiqueta: 'Nuevo estado',
    opciones: [ticket.estado, ...siguientes].map((valor) => ({
      valor,
      texto:
        valor === ticket.estado ? `${ESTADOS[valor].texto} (sin cambios)` : ESTADOS[valor].texto,
    })),
    valor: ticket.estado,
  });
  const respuesta = campo({
    nombre: 'respuesta',
    etiqueta: 'Respuesta para el jugador',
    pista: 'Es lo que el jugador leerá en la ventana del asistente.',
    multilinea: true,
    valor: ticket.respuesta ?? '',
    atributos: { maxlength: 2000 },
  });
  const aviso = h('p', { clase: 'campo__error', atributos: { role: 'alert', hidden: true } });
  cuerpo.append(estado.elemento, respuesta.elemento, aviso);

  return {
    datos() {
      const nuevoEstado = estado.control.value;
      const texto = respuesta.control.value.trim();
      const falta = nuevoEstado === 'RESUELTO' && !texto;
      respuesta.marcarError(falta ? 'Escribe la respuesta para el jugador.' : null);
      if (falta) {
        return null;
      }
      const cambios = {};
      if (nuevoEstado !== ticket.estado) {
        cambios.estado = nuevoEstado;
      }
      if (texto !== (ticket.respuesta ?? '')) {
        cambios.respuesta = texto || null;
      }
      if (Object.keys(cambios).length === 0) {
        this.avisar('No hay cambios que guardar.');
        return null;
      }
      return cambios;
    },
    avisar(texto) {
      aviso.textContent = texto ?? '';
      aviso.hidden = !texto;
    },
  };
}

/**
 * Monta la pestaña.
 *
 * @param {HTMLElement} raiz
 * @param {{cliente: object, atender?: typeof atenderEnDialogo}} dependencias
 * @returns {{cargar: () => Promise<void>}}
 */
export function montarSoporte(raiz, { cliente, atender = atenderEnDialogo }) {
  let estadoFiltro = '';
  let pagina = 0;

  const filtro = campo({
    nombre: 'estado',
    etiqueta: 'Mostrar',
    opciones: [
      { valor: '', texto: 'Todas' },
      ...Object.entries(ESTADOS).map(([valor, { texto }]) => ({ valor, texto })),
    ],
    valor: '',
  });
  filtro.control.addEventListener('change', () => {
    estadoFiltro = filtro.control.value;
    pagina = 0;
    cargar();
  });

  const nota = h('p', { clase: 'panel-chatbot__nota t-meta', atributos: { role: 'status' } });
  const zona = h('section', { clase: 'pila', datos: { zona: 'tickets' } });
  raiz.append(
    encabezadoDeSeccion({
      titulo: 'Solicitudes de soporte',
      detalle:
        'Lo que los jugadores preguntaron a soporte cuando el asistente no les resolvió. Ábrelas para leer la conversación y responder.',
    }),
    h('div', { clase: 'panel-chatbot__filtros', hijos: [filtro.elemento] }),
    nota,
    zona,
  );

  function avisar(texto) {
    nota.textContent = texto ?? '';
  }

  async function cargar() {
    pintarEstado(zona, estadoDeCarga({ filas: 3, etiqueta: 'Cargando solicitudes…' }));
    try {
      const resultado = await cliente.listarTickets({
        estado: estadoFiltro || null,
        pagina,
        tamano: POR_PAGINA,
      });
      pintarPagina(resultado);
    } catch (error) {
      pintarEstado(
        zona,
        estadoDeError({ ...textoDeErrorDelPanel(error), alReintentar: () => cargar() }),
      );
    }
  }

  function pintarPagina({ contenido = [], totalElementos = 0 }) {
    if (contenido.length === 0) {
      vaciar(zona).append(
        estadoVacio({
          titulo: estadoFiltro
            ? 'No hay solicitudes con ese estado'
            : 'No hay solicitudes de soporte',
          detalle: 'Cuando un jugador pida ayuda desde el asistente, su solicitud aparecerá aquí.',
        }),
      );
      return;
    }
    vaciar(zona).append(tabla(contenido), paginacion(totalElementos));
  }

  function tabla(tickets) {
    const filas = tickets.map((ticket) => {
      const ver = h('button', {
        clase: 'boton boton--contorno boton--pequeno',
        texto: 'Abrir',
        atributos: { type: 'button', 'aria-label': `Abrir la solicitud ${ticket.asunto}` },
        datos: { accion: 'abrir-ticket', ticket: ticket.ticketId },
      });
      ver.addEventListener('click', async () => {
        const guardado = await atender({ cliente, ticketId: ticket.ticketId });
        if (guardado) {
          await cargar();
          avisar('Solicitud actualizada.');
        }
      });
      return h('tr', {
        datos: { ticket: ticket.ticketId },
        hijos: [
          h('td', { texto: ticket.asunto }),
          h('td', { texto: nombreDeCategoria(ticket.categoria) }),
          h('td', { hijos: [distintivoDeEstado(ticket.estado)] }),
          h('td', { texto: fechaHora(ticket.creadoEn) }),
          h('td', { hijos: [ver] }),
        ],
      });
    });
    return h('table', {
      clase: 'tabla-panel',
      datos: { zona: 'tabla-tickets' },
      hijos: [
        h('thead', {
          hijos: [
            h('tr', {
              hijos: ['Asunto', 'Tema', 'Estado', 'Abierta', ''].map((texto) =>
                h('th', { texto, atributos: { scope: 'col' } }),
              ),
            }),
          ],
        }),
        h('tbody', { hijos: filas }),
      ],
    });
  }

  function paginacion(total) {
    const paginas = Math.max(1, Math.ceil(total / POR_PAGINA));
    const anterior = h('button', {
      clase: 'boton boton--secundario boton--pequeno',
      texto: 'Anterior',
      atributos: { type: 'button', disabled: pagina === 0 },
      datos: { accion: 'pagina-anterior' },
    });
    const siguiente = h('button', {
      clase: 'boton boton--secundario boton--pequeno',
      texto: 'Siguiente',
      atributos: { type: 'button', disabled: pagina >= paginas - 1 },
      datos: { accion: 'pagina-siguiente' },
    });
    anterior.addEventListener('click', () => {
      pagina -= 1;
      cargar();
    });
    siguiente.addEventListener('click', () => {
      pagina += 1;
      cargar();
    });
    return h('div', {
      clase: 'acciones',
      datos: { zona: 'paginacion' },
      hijos: [
        anterior,
        h('span', {
          clase: 't-meta',
          texto: `Página ${numero(pagina + 1)} de ${numero(paginas)} · ${numero(total)} solicitudes`,
        }),
        siguiente,
      ],
    });
  }

  return { cargar };
}
