/**
 * Mensajes privados entre jugadores — la vista (UXC-6).
 *
 * Vive en la pestaña «Mensajes privados» del chat. Lista de conversaciones,
 * buscador de jugadores y la conversación abierta, con los mismos globos y el
 * mismo redactor que el chat general (`comun/ui/conversacion/`): quien lee un
 * chat ya sabe leer el otro.
 *
 * Todo sale de la fuente (`fuente-mensajes.js`). Hoy la fuente dice
 * `disponible: false` —no hay servicio ni contrato de mensajes privados— y la
 * vista lo cuenta: qué pasa, por qué y qué se puede hacer ya (el chat general
 * y las salas privadas con invitación). No finge ni una conversación.
 *
 * Estados que cubre, por la retroalimentación del docente: lista, vacío,
 * cargando, error con reintento, buscar jugador (sin resultados y con fallo),
 * conversación nueva, enviando / enviado / leído / no se envió, reconexión,
 * conversación bloqueada, la otra cuenta sancionada y tu propio silencio con
 * su cuenta atrás.
 *
 * @module plataforma/salas-partidas/mensajes-privados
 */

import { h } from '../../comun/ui/dom.js';
import { icono } from '../../comun/ui/icono.js';
import { distintivo } from '../../comun/ui/distintivo.js';
import { confirmar } from '../../comun/ui/dialogo.js';
import { limpiarAviso, pintarAviso } from '../../comun/ui/aviso.js';
import { cuentaAtrasRotulada, vigilarCuentasAtras } from '../../comun/ui/cuenta-atras.js';
import { pintarEstadoDelCanal } from '../../comun/ui/reconexion.js';
import {
  estadoDeCarga,
  estadoDeError,
  estadoVacio,
  pintarEstado,
} from '../../comun/ui/estado-vista.js';
import { cuandoFue, inicialDeJugador } from '../../comun/ui/conversacion/mensaje.js';
import { hiloDeConversacion } from '../../comun/ui/conversacion/hilo.js';
import { prepararRedactor, redactorDeMensaje } from '../../comun/ui/conversacion/redactor.js';
import { fuenteDeMensajes } from './fuente-mensajes.js';

/** Letras mínimas para buscar un jugador. */
export const MINIMO_DE_BUSQUEDA = 2;

/** Lo que dice una conversación que no admite mensajes, por su estado. */
export function motivoDeBloqueo(estado, apodo) {
  switch (estado) {
    case 'BLOQUEADA':
      return {
        titulo: `Bloqueaste a ${apodo}`,
        detalle:
          'No recibes sus mensajes y no puedes escribirle. Desbloquéalo si quieres retomar la conversación.',
        distintivo: 'Bloqueado',
      };
    case 'NO_ADMITE':
      return {
        titulo: `${apodo} no recibe mensajes tuyos`,
        detalle: 'No puedes escribir en esta conversación.',
        distintivo: 'No admite mensajes',
      };
    case 'CUENTA_SANCIONADA':
      return {
        titulo: `La cuenta de ${apodo} está sancionada`,
        detalle:
          'Mientras dure la sanción no puede recibir mensajes. Lo que ya os escribisteis sigue aquí.',
        distintivo: 'Cuenta sancionada',
      };
    default:
      return null;
  }
}

/**
 * El estado honesto de hoy: no hay servicio de mensajes privados.
 *
 * @param {{alIrAlChatGeneral?: (() => void)|null, hrefCrearSala: string}} opciones
 * @returns {HTMLElement}
 */
export function avisoSinMensajesPrivados({ alIrAlChatGeneral = null, hrefCrearSala }) {
  const acciones = h('div', { clase: 'fila fila--acciones' });
  if (alIrAlChatGeneral) {
    const boton = h('button', {
      clase: 'boton boton--primario',
      texto: 'Ir al chat general',
      atributos: { type: 'button' },
      datos: { accion: 'ir-al-chat-general' },
    });
    boton.addEventListener('click', () => alIrAlChatGeneral());
    acciones.append(boton);
  }
  acciones.append(
    h('a', {
      clase: 'boton boton--secundario',
      texto: 'Crear una sala privada',
      atributos: { href: hrefCrearSala },
    }),
  );
  return h('section', {
    clase: 'tarjeta mensajes-privados__sin-abrir',
    datos: { estado: 'sin-abrir' },
    atributos: { 'aria-labelledby': 'mensajes-privados-sin-abrir' },
    hijos: [
      icono('sobre', { etiqueta: null, clase: 'icono mensajes-privados__sin-abrir-icono' }),
      h('h2', {
        texto: 'Los mensajes privados todavía no están abiertos',
        atributos: { id: 'mensajes-privados-sin-abrir' },
      }),
      h('p', {
        texto:
          'Aún no hay un servicio que guarde y entregue mensajes entre dos jugadores. Nadie recibiría lo que escribieras aquí, así que no te dejamos escribir.',
      }),
      h('p', {
        texto:
          'Mientras tanto, habla en el chat general o invita a alguien a una sala privada: al crearla tienes un código y un enlace de invitación para compartir.',
      }),
      acciones,
    ],
  });
}

/**
 * La vista previa de lo último de una conversación.
 *
 * @param {{texto: string, deMi: boolean}|null} ultimo
 * @returns {string}
 */
export function vistaPrevia(ultimo) {
  if (!ultimo?.texto) {
    return 'Sin mensajes todavía';
  }
  return ultimo.deMi ? `Tú: ${ultimo.texto}` : ultimo.texto;
}

/**
 * Un elemento de la lista de conversaciones.
 *
 * @param {import('./fuente-mensajes.js').ResumenDeConversacion} resumen
 * @param {{activa?: boolean, ahora?: Date}} [opciones]
 * @returns {HTMLLIElement}
 */
export function elementoDeConversacion(resumen, { activa = false, ahora = new Date() } = {}) {
  const motivo = motivoDeBloqueo(resumen.estado, resumen.con.apodo);
  const noLeidos = Number(resumen.noLeidos) || 0;
  const lado = h('span', {
    clase: 'conversaciones__lado',
    hijos: [
      resumen.ultimo?.enviadoEn
        ? h('time', {
            clase: 'conversaciones__cuando',
            texto: cuandoFue(resumen.ultimo.enviadoEn, ahora),
            atributos: { datetime: resumen.ultimo.enviadoEn },
          })
        : null,
      noLeidos > 0
        ? h('span', {
            clase: 'conversaciones__no-leidos',
            hijos: [
              h('span', { texto: String(noLeidos), atributos: { 'aria-hidden': 'true' } }),
              h('span', {
                clase: 'solo-lectores',
                texto: noLeidos === 1 ? '1 mensaje sin leer' : `${noLeidos} mensajes sin leer`,
              }),
            ],
          })
        : null,
    ],
  });
  const boton = h('button', {
    clase: `conversaciones__item${noLeidos > 0 ? ' conversaciones__item--sin-leer' : ''}`,
    atributos: { type: 'button', 'aria-current': activa ? 'true' : null },
    datos: { conversacion: resumen.id },
    hijos: [
      inicialDeJugador(resumen.con.apodo, 'conversaciones__inicial'),
      h('span', {
        clase: 'conversaciones__cuerpo',
        hijos: [
          h('span', { clase: 'conversaciones__apodo', texto: resumen.con.apodo }),
          h('span', { clase: 'conversaciones__vista', texto: vistaPrevia(resumen.ultimo) }),
          motivo ? distintivo(motivo.distintivo, 'no-disponible') : null,
        ],
      }),
      lado,
    ],
  });
  return h('li', { hijos: [boton] });
}

/**
 * @param {unknown} error
 * @returns {string}
 */
function detalleDeFallo(error) {
  return error?.detalle || 'Revisa tu conexión y vuelve a intentarlo.';
}

/**
 * Monta la pestaña de mensajes privados.
 *
 * @param {HTMLElement} zona el panel de la pestaña
 * @param {{fuente?: import('./fuente-mensajes.js').FuenteDeMensajes, miId?: string|null,
 *   alIrAlChatGeneral?: (() => void)|null, hrefCrearSala?: string, hrefSanciones?: string,
 *   confirmarBloqueo?: typeof confirmar, demoraDeBusqueda?: number,
 *   ahora?: () => Date}} [opciones]
 * @returns {Promise<{estado: 'sin-abrir'|'bandeja'|'error', detener: () => void}>}
 */
export async function montarMensajesPrivados(
  zona,
  {
    fuente = fuenteDeMensajes(),
    miId = null,
    alIrAlChatGeneral = null,
    hrefCrearSala = './crear-sala.html',
    hrefSanciones = '../moderacion-sanciones/mis-sanciones.html',
    confirmarBloqueo = confirmar,
    demoraDeBusqueda = 300,
    ahora = () => new Date(),
  } = {},
) {
  if (!fuente?.disponible) {
    zona.replaceChildren(avisoSinMensajesPrivados({ alIrAlChatGeneral, hrefCrearSala }));
    return { estado: 'sin-abrir', detener: () => {} };
  }

  /* --- Armazón -------------------------------------------------------- */
  const anuncios = h('p', {
    clase: 'solo-lectores',
    atributos: { role: 'status', 'aria-live': 'polite' },
  });
  const zonaRestriccion = h('div', {
    clase: 'mensajes-privados__restriccion',
    datos: { zona: 'restriccion' },
    atributos: { hidden: true },
  });
  const campoBusqueda = h('input', {
    clase: 'campo__control',
    atributos: {
      type: 'search',
      id: 'buscar-jugador',
      name: 'apodo',
      autocomplete: 'off',
      placeholder: 'Apodo del jugador',
      'aria-describedby': 'buscar-jugador-pista',
    },
  });
  const pistaBusqueda = h('p', {
    clase: 'campo__pista',
    texto: `Escribe al menos ${MINIMO_DE_BUSQUEDA} letras del apodo.`,
    atributos: { id: 'buscar-jugador-pista' },
  });
  const resultados = h('div', {
    clase: 'buscador-jugador__resultados',
    datos: { zona: 'resultados' },
    atributos: { 'aria-live': 'polite', hidden: true },
  });
  const buscador = h('form', {
    clase: 'buscador-jugador',
    atributos: { role: 'search', 'aria-label': 'Buscar un jugador para escribirle' },
    hijos: [
      h('div', {
        clase: 'campo',
        hijos: [
          h('label', {
            clase: 'campo__etiqueta',
            texto: 'Buscar jugador',
            atributos: { for: 'buscar-jugador' },
          }),
          campoBusqueda,
          pistaBusqueda,
        ],
      }),
      resultados,
    ],
  });
  const zonaLista = h('div', {
    clase: 'mensajes-privados__conversaciones',
    datos: { zona: 'conversaciones' },
  });
  const lateral = h('aside', {
    clase: 'tarjeta mensajes-privados__lateral',
    atributos: { 'aria-labelledby': 'mensajes-privados-titulo' },
    hijos: [
      h('h2', {
        clase: 'mensajes-privados__titulo',
        texto: 'Conversaciones',
        atributos: { id: 'mensajes-privados-titulo' },
      }),
      zonaRestriccion,
      buscador,
      zonaLista,
    ],
  });
  const zonaHilo = h('section', {
    clase: 'tarjeta mensajes-privados__hilo',
    datos: { zona: 'hilo' },
    atributos: { 'aria-label': 'Conversación' },
  });
  const raiz = h('div', {
    clase: 'mensajes-privados',
    datos: { vistaMovil: 'lista' },
    hijos: [lateral, zonaHilo, anuncios],
  });
  zona.replaceChildren(raiz);

  /* --- Estado --------------------------------------------------------- */
  /** @type {import('./fuente-mensajes.js').ResumenDeConversacion[]} */
  let conversaciones = [];
  /** @type {import('./fuente-mensajes.js').RestriccionDeMensajes|null} */
  let restriccion = null;
  /** @type {{id: string, resumen: object, hilo: any, redactor: any, pildora: HTMLElement,
   *   propios: Map<string, object>, cargado: boolean}|null} */
  let abierta = null;
  let canal = { estado: 'conectado' };
  let caido = false;
  let pararRestriccion = () => {};
  let temporizadorBusqueda = null;
  let pendientes = 0;
  let busquedaEnCurso = 0;

  function anunciar(texto) {
    anuncios.textContent = '';
    // Un cambio de texto en el siguiente ciclo: si se anuncia dos veces lo
    // mismo seguido, el lector de pantalla lo vuelve a leer.
    setTimeout(() => {
      anuncios.textContent = texto;
    }, 30);
  }

  /* --- Lista de conversaciones --------------------------------------- */
  function pintarLista() {
    if (conversaciones.length === 0) {
      pintarEstado(
        zonaLista,
        estadoVacio({
          titulo: 'Todavía no tienes conversaciones',
          detalle: 'Busca a un jugador por su apodo para escribirle.',
          accion: {
            texto: 'Buscar jugador',
            nombre: 'enfocar-busqueda',
            alPulsar: () => campoBusqueda.focus(),
          },
        }),
      );
      return;
    }
    const lista = h('ul', {
      clase: 'conversaciones',
      atributos: { 'aria-label': 'Tus conversaciones' },
    });
    for (const resumen of conversaciones) {
      const item = elementoDeConversacion(resumen, {
        activa: abierta?.id === resumen.id,
        ahora: ahora(),
      });
      item
        .querySelector('button')
        .addEventListener('click', () => abrirConversacion(resumen, { enfocar: true }));
      lista.append(item);
    }
    zonaLista.replaceChildren(lista);
    zonaLista.hidden = false;
  }

  /** Pone una conversación arriba (la más reciente) con sus cambios. */
  function actualizarResumen(id, cambios) {
    const indice = conversaciones.findIndex((c) => c.id === id);
    if (indice < 0) {
      return null;
    }
    const nuevo = { ...conversaciones[indice], ...cambios };
    conversaciones.splice(indice, 1);
    conversaciones.unshift(nuevo);
    if (abierta?.id === id) {
      abierta.resumen = nuevo;
    }
    pintarLista();
    return nuevo;
  }

  /* --- Tu silencio ---------------------------------------------------- */
  function pintarRestriccion() {
    pararRestriccion();
    if (!restriccion) {
      zonaRestriccion.hidden = true;
      zonaRestriccion.replaceChildren();
      return;
    }
    zonaRestriccion.replaceChildren(
      h('div', {
        clase: 'aviso aviso--advertencia',
        atributos: { role: 'status' },
        hijos: [
          h('div', {
            clase: 'aviso__cuerpo',
            hijos: [
              h('p', { clase: 'aviso__titulo', texto: 'Tienes un silencio activo' }),
              h('p', {
                clase: 'aviso__detalle',
                texto: 'Puedes leer tus conversaciones, pero no escribir mientras dure la sanción.',
              }),
              restriccion.hasta
                ? cuentaAtrasRotulada(restriccion.hasta, { clase: 't-meta' })
                : null,
            ],
          }),
          h('a', {
            clase: 'boton boton--secundario boton--pequeno',
            texto: 'Ver mis sanciones',
            atributos: { href: hrefSanciones },
          }),
        ],
      }),
    );
    zonaRestriccion.hidden = false;
    if (restriccion.hasta) {
      pararRestriccion = vigilarCuentasAtras(zonaRestriccion, { prefijo: 'Termina en' });
    }
  }

  /* --- Buscar jugador ------------------------------------------------- */
  function limpiarResultados() {
    resultados.replaceChildren();
    resultados.hidden = true;
  }

  async function buscar(texto) {
    busquedaEnCurso += 1;
    const esta = busquedaEnCurso;
    resultados.hidden = false;
    resultados.replaceChildren(
      h('p', { clase: 'buscador-jugador__estado t-meta', texto: 'Buscando…' }),
    );
    let jugadores;
    try {
      jugadores = await fuente.buscarJugadores(texto);
    } catch (error) {
      if (esta !== busquedaEnCurso) {
        return;
      }
      resultados.replaceChildren(
        h('p', {
          clase: 'buscador-jugador__estado buscador-jugador__estado--error',
          texto: `No pudimos buscar ahora. ${detalleDeFallo(error)}`,
        }),
      );
      return;
    }
    if (esta !== busquedaEnCurso) {
      return;
    }
    const otros = (jugadores ?? []).filter((j) => !miId || j.id !== miId);
    if (otros.length === 0) {
      resultados.replaceChildren(
        h('p', {
          clase: 'buscador-jugador__estado t-meta',
          texto: `Ningún jugador tiene un apodo con «${texto}».`,
        }),
      );
      return;
    }
    const lista = h('ul', {
      clase: 'buscador-jugador__lista',
      atributos: { 'aria-label': 'Jugadores encontrados' },
    });
    for (const jugador of otros) {
      const boton = h('button', {
        clase: 'buscador-jugador__resultado',
        atributos: { type: 'button', 'aria-label': `Escribir a ${jugador.apodo}` },
        datos: { jugador: jugador.id },
        hijos: [
          inicialDeJugador(jugador.apodo, 'conversaciones__inicial'),
          h('span', { clase: 'buscador-jugador__apodo', texto: jugador.apodo }),
          h('span', {
            clase: 'buscador-jugador__accion',
            texto: 'Escribir',
            atributos: { 'aria-hidden': 'true' },
          }),
        ],
      });
      boton.addEventListener('click', () => empezarCon(jugador));
      lista.append(h('li', { hijos: [boton] }));
    }
    resultados.replaceChildren(lista);
  }

  campoBusqueda.addEventListener('input', () => {
    clearTimeout(temporizadorBusqueda);
    const texto = campoBusqueda.value.trim();
    if (texto.length < MINIMO_DE_BUSQUEDA) {
      busquedaEnCurso += 1;
      limpiarResultados();
      return;
    }
    temporizadorBusqueda = setTimeout(() => buscar(texto), demoraDeBusqueda);
  });
  campoBusqueda.addEventListener('keydown', (evento) => {
    if (evento.key === 'Escape' && campoBusqueda.value) {
      evento.preventDefault();
      campoBusqueda.value = '';
      busquedaEnCurso += 1;
      limpiarResultados();
    }
  });
  buscador.addEventListener('submit', (evento) => {
    evento.preventDefault();
    clearTimeout(temporizadorBusqueda);
    const texto = campoBusqueda.value.trim();
    if (texto.length >= MINIMO_DE_BUSQUEDA) {
      buscar(texto);
    }
  });

  async function empezarCon(jugador) {
    let resumen;
    try {
      resumen = await fuente.conversacionCon(jugador.id);
    } catch (error) {
      resultados.hidden = false;
      resultados.replaceChildren(
        h('p', {
          clase: 'buscador-jugador__estado buscador-jugador__estado--error',
          texto: `No pudimos abrir la conversación con ${jugador.apodo}. ${detalleDeFallo(error)}`,
        }),
      );
      return;
    }
    campoBusqueda.value = '';
    limpiarResultados();
    if (!conversaciones.some((c) => c.id === resumen.id)) {
      conversaciones.unshift(resumen);
    }
    pintarLista();
    abrirConversacion(resumen, { enfocar: true });
  }

  /* --- La conversación abierta --------------------------------------- */
  function pintarSinConversacion() {
    abierta = null;
    zonaHilo.replaceChildren(
      estadoVacio({
        titulo: 'Elige una conversación',
        detalle: 'O busca a un jugador por su apodo para escribirle.',
      }),
    );
  }

  function volverALaLista() {
    raiz.dataset.vistaMovil = 'lista';
    const id = abierta?.id;
    const boton = id ? zonaLista.querySelector(`[data-conversacion="${id}"]`) : null;
    (boton ?? campoBusqueda).focus();
  }

  function aplicarBloqueo() {
    if (!abierta) {
      return;
    }
    const { resumen, redactor } = abierta;
    if (restriccion) {
      redactor.bloquear({
        titulo: 'Tienes un silencio activo',
        detalle: 'Mientras dure la sanción tus mensajes no salen.',
        enlace: { texto: 'Ver mis sanciones', href: hrefSanciones },
        hasta: restriccion.hasta,
      });
      return;
    }
    const motivo = motivoDeBloqueo(resumen.estado, resumen.con.apodo);
    if (!motivo) {
      redactor.desbloquear();
      return;
    }
    redactor.bloquear({
      titulo: motivo.titulo,
      detalle: motivo.detalle,
      accion:
        resumen.estado === 'BLOQUEADA'
          ? { texto: 'Desbloquear', nombre: 'desbloquear', alPulsar: () => cambiarBloqueo(false) }
          : null,
    });
  }

  async function cambiarBloqueo(bloquear) {
    if (!abierta) {
      return;
    }
    const { con } = abierta.resumen;
    if (bloquear) {
      const seguro = await confirmarBloqueo({
        titulo: `¿Bloquear a ${con.apodo}?`,
        mensaje:
          'Dejarás de recibir sus mensajes y no podrá escribirte. Puedes desbloquearlo cuando quieras.',
        textoConfirmar: 'Bloquear',
      });
      if (!seguro) {
        return;
      }
    }
    const zonaAviso = abierta.redactor.zonaAviso;
    limpiarAviso(zonaAviso);
    try {
      const resumen = await fuente.bloquear(con.id, bloquear);
      actualizarResumen(resumen.id, resumen);
      pintarCabecera();
      aplicarBloqueo();
      anunciar(bloquear ? `Bloqueaste a ${con.apodo}.` : `Desbloqueaste a ${con.apodo}.`);
    } catch (error) {
      pintarAviso(zonaAviso, {
        tono: 'error',
        titulo: bloquear ? 'No pudimos bloquear' : 'No pudimos desbloquear',
        detalle: detalleDeFallo(error),
      });
    }
  }

  function pintarCabecera() {
    if (!abierta) {
      return;
    }
    const { resumen } = abierta;
    const volver = h('button', {
      clase: 'boton boton--secundario boton--pequeno mensajes-privados__volver',
      atributos: { type: 'button' },
      datos: { accion: 'volver-a-conversaciones' },
      hijos: [
        icono('chevron', { etiqueta: null, clase: 'icono mensajes-privados__volver-icono' }),
        h('span', { texto: 'Conversaciones' }),
      ],
    });
    volver.addEventListener('click', volverALaLista);
    // «Bloquear» solo en una conversación activa. Bloqueada, el desbloqueo va
    // junto a la explicación, en el sitio del campo: un solo botón, no dos.
    const botonBloqueo =
      resumen.estado === 'ACTIVA'
        ? h('button', {
            clase: 'boton boton--contorno boton--pequeno',
            texto: 'Bloquear',
            atributos: { type: 'button' },
            datos: { accion: 'bloquear' },
          })
        : null;
    botonBloqueo?.addEventListener('click', () => cambiarBloqueo(true));
    const cabecera = h('header', {
      clase: 'mensajes-privados__cabecera',
      hijos: [
        volver,
        inicialDeJugador(resumen.con.apodo, 'conversaciones__inicial'),
        h('h2', {
          clase: 'mensajes-privados__con',
          texto: resumen.con.apodo,
          atributos: { id: 'mensajes-privados-con', tabindex: '-1' },
        }),
        abierta.pildora,
        botonBloqueo,
      ],
    });
    const previa = zonaHilo.querySelector('.mensajes-privados__cabecera');
    if (previa) {
      previa.replaceWith(cabecera);
    } else {
      zonaHilo.prepend(cabecera);
    }
  }

  function pintarCanal() {
    if (!abierta) {
      return;
    }
    pintarEstadoDelCanal(abierta.pildora, {
      ...canal,
      alReintentar: typeof fuente.reintentar === 'function' ? () => fuente.reintentar() : undefined,
    });
    abierta.redactor.esperarConexion(
      canal.estado === 'reconectando' || canal.estado === 'sin-conexion',
    );
  }

  async function enviar(texto, { claveAnterior = null } = {}) {
    if (!abierta) {
      return false;
    }
    const { id, hilo } = abierta;
    pendientes += 1;
    const clave = claveAnterior ?? `pendiente-${pendientes}`;
    const borrador = {
      tipo: 'mensaje',
      autor: { id: miId, apodo: 'Tú' },
      texto,
      enviadoEn: ahora().toISOString(),
    };
    if (claveAnterior) {
      hilo.actualizar(claveAnterior, borrador, { entrega: 'ENVIANDO' });
    } else {
      hilo.agregar(borrador, { entrega: 'ENVIANDO', clave });
    }
    let confirmado;
    try {
      confirmado = await fuente.enviar(id, texto);
    } catch {
      if (abierta?.id === id) {
        hilo.actualizar(clave, borrador, {
          entrega: 'FALLIDO',
          alReintentar: () => enviar(texto, { claveAnterior: clave }),
        });
        anunciar('Tu mensaje no se envió. Puedes reintentarlo.');
      }
      return true;
    }
    if (abierta?.id === id) {
      abierta.propios.set(confirmado.id, confirmado);
      hilo.actualizar(clave, confirmado, {
        entrega: confirmado.entrega ?? 'ENVIADO',
        id: confirmado.id,
      });
    }
    actualizarResumen(id, {
      ultimo: { texto: confirmado.texto, enviadoEn: confirmado.enviadoEn, deMi: true },
    });
    return true;
  }

  async function cargarHilo() {
    if (!abierta) {
      return;
    }
    const { id, hilo, zonaEstado } = abierta;
    pintarEstado(zonaEstado, estadoDeCarga({ filas: 4, etiqueta: 'Cargando la conversación…' }));
    let respuesta;
    try {
      respuesta = await fuente.hilo(id);
    } catch (error) {
      if (abierta?.id === id) {
        pintarEstado(
          zonaEstado,
          estadoDeError({
            titulo: 'No pudimos cargar la conversación',
            detalle: detalleDeFallo(error),
            alReintentar: () => cargarHilo(),
          }),
        );
      }
      return;
    }
    if (abierta?.id !== id) {
      return;
    }
    const mensajes = respuesta?.mensajes ?? [];
    for (const mensaje of mensajes) {
      if (miId && mensaje.autor?.id === miId) {
        abierta.propios.set(mensaje.id, mensaje);
      }
    }
    abierta.cargado = true;
    hilo.reemplazar(mensajes);
    const resumen = conversaciones.find((c) => c.id === id);
    if (resumen?.noLeidos > 0) {
      actualizarResumenSinMover(id, { noLeidos: 0 });
      fuente.marcarLeida(id).catch(() => {});
    }
  }

  function actualizarResumenSinMover(id, cambios) {
    const indice = conversaciones.findIndex((c) => c.id === id);
    if (indice >= 0) {
      conversaciones[indice] = { ...conversaciones[indice], ...cambios };
      pintarLista();
    }
  }

  function abrirConversacion(resumen, { enfocar = false } = {}) {
    const pildora = h('span', { clase: 'conexion', datos: { zona: 'conexion-privados' } });
    const lista = h('ol', { atributos: { 'aria-label': `Mensajes con ${resumen.con.apodo}` } });
    const zonaEstado = h('div', { clase: 'conversacion__estado', datos: { zona: 'sin-mensajes' } });
    const formulario = redactorDeMensaje({
      id: 'mensaje-privado',
      etiqueta: `Mensaje para ${resumen.con.apodo}`,
      marcador: `Escribe a ${resumen.con.apodo}…`,
    });
    const cuerpo = h('div', {
      clase: 'conversacion mensajes-privados__conversacion',
      hijos: [h('div', { clase: 'conversacion__cuerpo', hijos: [lista, zonaEstado] }), formulario],
    });
    zonaHilo.replaceChildren(cuerpo);
    const hilo = hiloDeConversacion(lista, {
      miId,
      ahora,
      alCambiar: (cantidad) => {
        if (!abierta?.cargado) {
          return;
        }
        if (cantidad > 0) {
          zonaEstado.replaceChildren();
          zonaEstado.hidden = true;
          return;
        }
        pintarEstado(
          zonaEstado,
          estadoVacio({
            titulo: 'Todavía no os habéis escrito',
            detalle: `Escribe el primer mensaje a ${resumen.con.apodo}.`,
          }),
        );
      },
    });
    const redactor = prepararRedactor(formulario, { alEnviar: (texto) => enviar(texto) });
    abierta = {
      id: resumen.id,
      resumen,
      hilo,
      redactor,
      pildora,
      zonaEstado,
      propios: new Map(),
      cargado: false,
    };
    pintarCabecera();
    pintarCanal();
    aplicarBloqueo();
    pintarLista();
    raiz.dataset.vistaMovil = 'hilo';
    if (enfocar) {
      zonaHilo.querySelector('#mensajes-privados-con')?.focus();
    }
    cargarHilo();
  }

  /* --- Lo que llega en vivo ------------------------------------------- */
  function alEvento(evento) {
    if (evento?.tipo === 'canal') {
      canal = { estado: evento.estado, intento: evento.intento, de: evento.de };
      const sinCanal = evento.estado === 'reconectando' || evento.estado === 'sin-conexion';
      if (sinCanal && !caido) {
        caido = true;
        abierta?.hilo.sistema(
          'Se cortó la conexión. Reintentando: lo que te escriban llegará al volver.',
          {
            tono: 'advertencia',
          },
        );
      } else if (evento.estado === 'reconectado' && caido) {
        caido = false;
        abierta?.hilo.sistema('Conexión recuperada. La conversación está al día.', {
          tono: 'exito',
        });
      }
      pintarCanal();
      return;
    }
    if (evento?.tipo === 'mensaje') {
      const { conversacionId, mensaje } = evento;
      const deMi = Boolean(miId && mensaje.autor?.id === miId);
      if (abierta?.id === conversacionId && abierta.cargado) {
        if (!abierta.hilo.tiene(mensaje.id)) {
          abierta.hilo.agregar(mensaje);
        }
        if (!deMi) {
          fuente.marcarLeida(conversacionId).catch(() => {});
        }
        actualizarResumen(conversacionId, {
          ultimo: { texto: mensaje.texto, enviadoEn: mensaje.enviadoEn, deMi },
        });
        return;
      }
      const previa = conversaciones.find((c) => c.id === conversacionId);
      if (!previa) {
        return;
      }
      actualizarResumen(conversacionId, {
        ultimo: { texto: mensaje.texto, enviadoEn: mensaje.enviadoEn, deMi },
        noLeidos: deMi ? previa.noLeidos : (Number(previa.noLeidos) || 0) + 1,
      });
      if (!deMi) {
        anunciar(`Nuevo mensaje de ${previa.con.apodo}.`);
      }
      return;
    }
    if (evento?.tipo === 'leido' && abierta?.id === evento.conversacionId) {
      const limite = new Date(evento.hasta).getTime();
      for (const [id, mensaje] of abierta.propios) {
        if (mensaje.entrega !== 'LEIDO' && new Date(mensaje.enviadoEn).getTime() <= limite) {
          const leido = { ...mensaje, entrega: 'LEIDO' };
          abierta.propios.set(id, leido);
          abierta.hilo.actualizar(id, leido, { entrega: 'LEIDO' });
        }
      }
    }
  }

  /* --- Arranque ------------------------------------------------------- */
  async function cargarBandeja() {
    pintarEstado(zonaLista, estadoDeCarga({ filas: 4, etiqueta: 'Cargando tus conversaciones…' }));
    let bandeja;
    try {
      bandeja = await fuente.bandeja();
    } catch (error) {
      pintarEstado(
        zonaLista,
        estadoDeError({
          titulo: 'No pudimos cargar tus conversaciones',
          detalle: detalleDeFallo(error),
          alReintentar: () => cargarBandeja(),
        }),
      );
      return 'error';
    }
    conversaciones = [...(bandeja?.conversaciones ?? [])];
    restriccion = bandeja?.restriccion ?? null;
    pintarRestriccion();
    pintarLista();
    if (abierta) {
      aplicarBloqueo();
    }
    return 'bandeja';
  }

  pintarSinConversacion();
  const dejarDeEscuchar = fuente.escuchar(alEvento);
  const estado = await cargarBandeja();

  return {
    estado,
    detener() {
      dejarDeEscuchar?.();
      pararRestriccion();
      clearTimeout(temporizadorBusqueda);
    },
  };
}
