/**
 * B6 — Vista de mensajes privados entre jugadores.
 *
 * FEEDBACK DEL PROFESOR, no requisito del documento: la sección 7.6 pide chat
 * en las salas y en la vista general; los mensajes privados se piden en la
 * demostración y van sobre el mismo chat (mismo servicio y mismo canal STOMP).
 *
 * Qué hace:
 *   - lista las conversaciones del jugador con sus no leídos;
 *   - abre una (la de `?con=<uid>`, o la más reciente) y la carga hacia atrás;
 *   - envía por STOMP (`/app/mensajes-directos/{uid}`) con un `idCliente` para
 *     reconocer el eco; si el canal no está, o el eco no llega, por el POST de
 *     respaldo, que aplica las mismas reglas y no duplica (mismo `idCliente`);
 *   - recibe en tiempo real por `/usuario/cola/mensajes-directos`: mensajes
 *     nuevos, el eco de los propios (también de otras pestañas) y los rechazos,
 *     que se explican por motivo;
 *   - sin canal, consulta periódica por REST y se pone al día al volver
 *     (degradación controlada, riesgo #7 del Project Charter): nunca falla en
 *     silencio.
 *
 * Reutiliza el cliente STOMP de salas y chat (`cliente-chat.js`, con el JWT en
 * el CONNECT) y el canal que se reconecta solo (`comun/canal-reconectable.js`).
 * Nada de `innerHTML`: todo texto va por `textContent`.
 *
 * @module plataforma/salas-partidas/mensajes
 */

import {
  COLA_MENSAJES,
  LARGO_MAXIMO,
  TAMANO_DE_PAGINA,
  destinoDeEnvio,
  enviarPorRest,
  esUid,
  historial,
  listarConversaciones,
  marcarLeida,
  nuevoIdCliente,
  textoDeRechazo,
} from './cliente-mensajes.js';
import { h, vaciar } from '../../comun/ui/dom.js';
import { limpiarAviso, pintarAviso } from '../../comun/ui/aviso.js';
import {
  estadoDeCarga,
  estadoDeError,
  estadoVacio,
  pintarEstado,
} from '../../comun/ui/estado-vista.js';
import { fechaHora } from '../../comun/ui/formato.js';
import { pintarEstadoDelCanal } from '../../comun/ui/reconexion.js';

/** Cada cuánto se consulta por REST mientras no hay canal en tiempo real. */
export const INTERVALO_DE_SONDEO_MS = 10000;

/** Cuánto se espera el eco de un envío por STOMP antes de mandarlo por REST. */
export const ESPERA_DEL_ECO_MS = 8000;

/** Largo de la vista previa del último mensaje en la lista. */
const VISTA_PREVIA = 60;

/**
 * El uid de `?con=`, o null si no hay o no es un uid.
 *
 * @param {string} busqueda `location.search`
 * @returns {string|null}
 */
export function conversacionDesdeUrl(busqueda) {
  const con = new URLSearchParams(busqueda ?? '').get('con');
  return esUid(con) ? con.toLowerCase() : null;
}

/**
 * Texto del contador del redactor.
 *
 * @param {string} texto
 * @returns {string}
 */
export function textoDelContador(texto) {
  return `${String(texto ?? '').length} de ${LARGO_MAXIMO} caracteres`;
}

function vistaPrevia(texto) {
  const plano = String(texto ?? '')
    .replace(/\s+/g, ' ')
    .trim();
  return plano.length > VISTA_PREVIA ? `${plano.slice(0, VISTA_PREVIA - 1)}…` : plano;
}

/**
 * Una fila de la lista de conversaciones.
 *
 * @param {{uidOtro: string, apodoOtro?: string|null, ultimoMensaje?: object, noLeidos?: number}} resumen
 * @param {{activa?: boolean, yo?: string|null, alElegir?: (uid: string) => void}} [opciones]
 * @returns {HTMLLIElement}
 */
export function pintarConversacion(resumen, { activa = false, yo = null, alElegir } = {}) {
  const nombre = resumen.apodoOtro || 'Jugador';
  const ultimo = resumen.ultimoMensaje;
  const propio = ultimo && ultimo.remitente === yo;
  const boton = h('button', {
    clase: 'conversacion',
    atributos: { type: 'button', 'aria-current': activa ? 'true' : null },
    datos: { uid: resumen.uidOtro },
  });
  const cabeza = h('span', { clase: 'conversacion__cabeza' });
  cabeza.append(h('span', { clase: 'conversacion__nombre', texto: nombre }));
  const sinLeer = Number(resumen.noLeidos) || 0;
  if (sinLeer > 0) {
    const distintivo = h('span', { clase: 'distintivo', datos: { zona: 'sin-leer' } });
    distintivo.append(String(sinLeer), h('span', { clase: 'solo-lectores', texto: ' sin leer' }));
    cabeza.append(distintivo);
  }
  boton.append(cabeza);
  if (ultimo) {
    boton.append(
      h('span', {
        clase: 'conversacion__ultimo t-meta',
        texto: `${propio ? 'Tú: ' : ''}${vistaPrevia(ultimo.texto)}`,
      }),
    );
  }
  if (typeof alElegir === 'function') {
    boton.addEventListener('click', () => alElegir(resumen.uidOtro));
  }
  const fila = h('li');
  fila.append(boton);
  return fila;
}

/**
 * Una línea del hilo.
 *
 * @param {object} mensaje esquema `MensajeDirecto` (REST) o `MensajeEntregado` (STOMP)
 * @param {string|null} yo uid de quien mira
 * @param {{estado?: 'entregado'|'enviando'|'fallido'}} [opciones]
 * @returns {HTMLLIElement}
 */
export function pintarMensaje(mensaje, yo, { estado = 'entregado' } = {}) {
  const propio = mensaje.remitente === yo;
  const fila = h('li', {
    clase: `mensaje-directo${propio ? ' mensaje-directo--propio' : ''}`,
    datos: { id: mensaje.id ?? '', estado },
  });
  if (mensaje.idCliente) {
    fila.dataset.idCliente = mensaje.idCliente;
  }
  fila.append(
    h('p', {
      clase: 'mensaje-directo__autor t-etiqueta',
      texto: propio ? 'Tú' : mensaje.apodoRemitente || 'Jugador',
    }),
    h('p', { clase: 'mensaje-directo__texto t-cuerpo', texto: mensaje.texto }),
  );
  const meta = h('p', { clase: 'mensaje-directo__meta t-meta' });
  const hora = h('time', { texto: fechaHora(mensaje.fecha) });
  hora.dateTime = mensaje.fecha ?? '';
  meta.append(hora);
  const textoDeEstado = { enviando: ' · Enviando…', fallido: ' · No se envió' }[estado];
  if (textoDeEstado) {
    meta.append(h('span', { clase: 'mensaje-directo__estado', texto: textoDeEstado }));
  }
  fila.append(meta);
  return fila;
}

/**
 * Monta la vista sobre su raíz.
 *
 * @param {ParentNode} raiz contenedor con las `data-zona` de `mensajes.html`
 * @param {object} opciones
 * @param {string|null} opciones.yo uid de la sesión (claim `uid` del token)
 * @param {() => Promise<object>} [opciones.abrirCanal] abre el canal que se
 *   reconecta solo; recibe `{alEstado, alReconectar}` y devuelve la fachada de
 *   `canal-reconectable.js`. Sin él, la vista funciona solo por REST.
 * @param {object} [opciones.api] cliente REST (inyectable en pruebas)
 * @param {string} [opciones.busqueda] `location.search`
 * @param {object} [opciones.reloj] `setTimeout/clearTimeout/setInterval/clearInterval`
 * @param {number} [opciones.intervaloSondeo]
 * @param {number} [opciones.esperaDelEco]
 * @param {(sinLeer: number) => void} [opciones.alContador] el contador de la cabecera
 * @param {(uid: string|null) => void} [opciones.alSeleccionar] refleja `?con=` en la dirección
 * @param {() => boolean} [opciones.visible] si la pestaña se está mirando
 * @returns {Promise<{seleccionar: Function, recibir: Function, sondear: Function, cerrar: Function}>}
 */
export async function montarMensajes(
  raiz,
  {
    yo,
    abrirCanal = null,
    api = { listarConversaciones, historial, enviarPorRest, marcarLeida },
    busqueda = globalThis.location?.search ?? '',
    reloj = globalThis,
    intervaloSondeo = INTERVALO_DE_SONDEO_MS,
    esperaDelEco = ESPERA_DEL_ECO_MS,
    alContador = () => {},
    alSeleccionar = () => {},
    visible = () => globalThis.document?.visibilityState !== 'hidden',
  } = {},
) {
  const zonas = {
    conexion: raiz.querySelector('[data-zona="conexion"]'),
    avisoConexion: raiz.querySelector('[data-zona="aviso-conexion"]'),
    avisoGeneral: raiz.querySelector('[data-zona="aviso-general"]'),
    estadoLista: raiz.querySelector('[data-zona="estado-conversaciones"]'),
    lista: raiz.querySelector('[data-zona="conversaciones"]'),
    titulo: raiz.querySelector('[data-zona="titulo-hilo"]'),
    estadoHilo: raiz.querySelector('[data-zona="estado-hilo"]'),
    lineas: raiz.querySelector('[data-zona="lineas"]'),
    anteriores: raiz.querySelector('[data-accion="cargar-anteriores"]'),
    anuncio: raiz.querySelector('[data-zona="anuncio"]'),
    redactor: raiz.querySelector('[data-zona="redactor"]'),
    avisoRedactor: raiz.querySelector('[data-zona="aviso-redactor"]'),
    contador: raiz.querySelector('[data-zona="contador"]'),
  };
  const campoTexto = zonas.redactor?.querySelector('[name="texto"]');

  /** @type {Map<string, object>} uidOtro → ResumenDeConversacion */
  const conversaciones = new Map();
  /** La conversación abierta: con quién, qué hay pintado, y si queda más atrás. */
  let abierta = null;
  /** Envíos sin confirmar, por idCliente. */
  const pendientes = new Map();
  /** Mensajes ya incorporados, para no contar ni anunciar dos veces el mismo. */
  const vistos = new Set();
  let canal = null;
  let sondeo = null;
  let marcaDiferida = null;
  let cerrada = false;

  // ------------------------------------------------------------ utilidades

  const anunciar = (texto) => {
    if (zonas.anuncio) {
      zonas.anuncio.textContent = texto;
    }
  };

  const totalSinLeer = () =>
    [...conversaciones.values()].reduce((suma, c) => suma + (Number(c.noLeidos) || 0), 0);

  const ordenadas = () =>
    [...conversaciones.values()].sort((a, b) =>
      String(b.ultimoMensaje?.fecha ?? '').localeCompare(String(a.ultimoMensaje?.fecha ?? '')),
    );

  function pintarLista() {
    vaciar(zonas.lista);
    const lista = ordenadas();
    for (const resumen of lista) {
      zonas.lista.append(
        pintarConversacion(resumen, {
          activa: resumen.uidOtro === abierta?.uid,
          yo,
          alElegir: (uid) => seleccionar(uid, { enfocar: true }),
        }),
      );
    }
    if (lista.length === 0) {
      pintarEstado(
        zonas.estadoLista,
        estadoVacio({
          titulo: 'Todavía no tienes conversaciones',
          detalle:
            'Para escribirle a alguien, pulsa «Mensaje privado» junto a su apodo en el chat general o en la lista de una sala.',
          accion: { texto: 'Ir al chat general', href: './chat.html', nombre: 'ir-al-chat' },
        }),
      );
    } else {
      vaciar(zonas.estadoLista);
      zonas.estadoLista.hidden = true;
    }
    alContador(totalSinLeer());
  }

  /** El resumen de la conversación con `uid`, creándolo si hace falta. */
  function resumenDe(uid, apodo = null) {
    if (!conversaciones.has(uid)) {
      conversaciones.set(uid, { uidOtro: uid, apodoOtro: apodo, ultimoMensaje: null, noLeidos: 0 });
    }
    const resumen = conversaciones.get(uid);
    if (!resumen.apodoOtro && apodo) {
      resumen.apodoOtro = apodo;
    }
    return resumen;
  }

  const otroDe = (mensaje) => (mensaje.remitente === yo ? mensaje.destinatario : mensaje.remitente);

  function llevarAlFinal() {
    zonas.lineas.scrollTop = zonas.lineas.scrollHeight;
  }

  function lineaDe(selector) {
    return zonas.lineas.querySelector(selector);
  }

  /** Pinta un mensaje del hilo abierto si no estaba ya (por id). */
  function agregarAlHilo(mensaje, { alPrincipio = false, estado = 'entregado' } = {}) {
    if (!abierta || abierta.ids.has(mensaje.id)) {
      return false;
    }
    abierta.ids.add(mensaje.id);
    vistos.add(mensaje.id);
    const linea = pintarMensaje(mensaje, yo, { estado });
    if (alPrincipio) {
      zonas.lineas.prepend(linea);
    } else {
      zonas.lineas.append(linea);
    }
    if (!abierta.masAntigua || String(mensaje.fecha) < String(abierta.masAntigua)) {
      abierta.masAntigua = mensaje.fecha;
    }
    vaciar(zonas.estadoHilo);
    zonas.estadoHilo.hidden = true;
    return true;
  }

  function hiloVacio() {
    pintarEstado(
      zonas.estadoHilo,
      estadoVacio({
        titulo: 'Todavía no hay mensajes aquí',
        detalle: 'Escribe el primero. Pasa por el mismo filtro que el chat antes de llegar.',
      }),
    );
  }

  // ----------------------------------------------------------- leer / abrir

  async function cargarConversaciones() {
    pintarEstado(
      zonas.estadoLista,
      estadoDeCarga({ filas: 4, etiqueta: 'Cargando conversaciones…' }),
    );
    try {
      const lista = await api.listarConversaciones();
      conversaciones.clear();
      for (const resumen of lista ?? []) {
        conversaciones.set(resumen.uidOtro, { ...resumen });
      }
      if (abierta && !conversaciones.has(abierta.uid)) {
        resumenDe(abierta.uid, abierta.apodo);
      }
      pintarLista();
      return true;
    } catch {
      pintarEstado(
        zonas.estadoLista,
        estadoDeError({
          titulo: 'No pudimos cargar tus conversaciones',
          detalle: 'Revisa tu conexión e inténtalo de nuevo.',
          alReintentar: () => cargarConversaciones(),
        }),
      );
      return false;
    }
  }

  /** Relee la lista sin pintar el estado de carga: para el sondeo y la reconciliación. */
  async function refrescarConversaciones() {
    try {
      const lista = await api.listarConversaciones();
      for (const resumen of lista ?? []) {
        const previo = conversaciones.get(resumen.uidOtro);
        const sinLeer = resumen.uidOtro === abierta?.uid ? 0 : resumen.noLeidos;
        conversaciones.set(resumen.uidOtro, { ...previo, ...resumen, noLeidos: sinLeer });
      }
      pintarLista();
    } catch {
      // El sondeo vuelve a intentarlo; el estado del canal ya dice lo que pasa.
    }
  }

  function tituloDelHilo(uid) {
    const apodo = conversaciones.get(uid)?.apodoOtro;
    return apodo ? `Conversación con ${apodo}` : 'Nueva conversación';
  }

  function sinConversacionAbierta() {
    zonas.titulo.textContent = 'Elige una conversación';
  }

  /**
   * Abre la conversación con ese jugador.
   *
   * @param {string} uid
   * @param {{enfocar?: boolean}} [opciones] mover el foco al redactor (al elegir con el ratón o el teclado)
   */
  async function seleccionar(uid, { enfocar = false } = {}) {
    if (!esUid(uid)) {
      return;
    }
    if (uid === yo) {
      pintarAviso(zonas.avisoGeneral, textoDeRechazo('DESTINATARIO_PROPIO'));
      return;
    }
    abierta = {
      uid,
      apodo: conversaciones.get(uid)?.apodoOtro ?? null,
      ids: new Set(),
      masAntigua: null,
    };
    alSeleccionar(uid);
    resumenDe(uid);
    zonas.titulo.textContent = tituloDelHilo(uid);
    vaciar(zonas.lineas);
    limpiarAviso(zonas.avisoRedactor);
    zonas.anteriores.hidden = true;
    zonas.redactor.hidden = false;
    pintarLista();
    await cargarHistorial(uid);
    if (enfocar && campoTexto) {
      campoTexto.focus();
    }
  }

  /** Quedan más mensajes atrás si la página vino llena. */
  function ofrecerAnteriores(cuantos) {
    zonas.anteriores.hidden = cuantos < TAMANO_DE_PAGINA;
  }

  function hiloCargando() {
    pintarEstado(zonas.estadoHilo, estadoDeCarga({ filas: 3, etiqueta: 'Cargando mensajes…' }));
  }

  function hiloConError(uid) {
    pintarEstado(
      zonas.estadoHilo,
      estadoDeError({
        titulo: 'No pudimos cargar esta conversación',
        detalle: 'Revisa tu conexión e inténtalo de nuevo.',
        alReintentar: () => cargarHistorial(uid),
      }),
    );
  }

  async function cargarHistorial(uid) {
    hiloCargando();
    try {
      const mensajes = await api.historial(uid, { limite: TAMANO_DE_PAGINA });
      if (abierta?.uid !== uid) {
        return;
      }
      if (!mensajes?.length) {
        hiloVacio();
      }
      for (const mensaje of mensajes ?? []) {
        agregarAlHilo(mensaje);
      }
      ofrecerAnteriores(mensajes?.length ?? 0);
      llevarAlFinal();
      await marcarComoLeida(uid);
    } catch {
      if (abierta?.uid === uid) {
        hiloConError(uid);
      }
    }
  }

  async function cargarAnteriores() {
    if (!abierta?.masAntigua) {
      return;
    }
    const uid = abierta.uid;
    zonas.anteriores.disabled = true;
    try {
      const mensajes = await api.historial(uid, {
        antesDe: abierta.masAntigua,
        limite: TAMANO_DE_PAGINA,
      });
      if (abierta?.uid !== uid) {
        return;
      }
      const alto = zonas.lineas.scrollHeight;
      // Vienen del más antiguo al más reciente: se anteponen al revés.
      for (const mensaje of [...(mensajes ?? [])].reverse()) {
        agregarAlHilo(mensaje, { alPrincipio: true });
      }
      zonas.lineas.scrollTop += zonas.lineas.scrollHeight - alto;
      ofrecerAnteriores(mensajes?.length ?? 0);
      anunciar(
        mensajes?.length
          ? `Se cargaron ${mensajes.length} mensajes anteriores.`
          : 'No hay mensajes anteriores.',
      );
      if (zonas.anteriores.hidden) {
        zonas.titulo.focus?.();
      }
    } catch {
      pintarAviso(zonas.avisoRedactor, {
        tono: 'error',
        titulo: 'No pudimos cargar los mensajes anteriores',
        detalle: 'Inténtalo de nuevo en un momento.',
      });
    } finally {
      zonas.anteriores.disabled = false;
    }
  }

  /**
   * Marca leída la conversación en el servidor, si quien mira la está viendo.
   *
   * @param {string} uid
   * @param {{forzar?: boolean}} [opciones] `forzar`: aunque la lista ya diga
   *   cero, porque acaba de llegar un mensaje a la conversación abierta
   */
  async function marcarComoLeida(uid, { forzar = false } = {}) {
    const resumen = conversaciones.get(uid);
    if (!resumen || !visible() || (!forzar && !(Number(resumen.noLeidos) > 0))) {
      return;
    }
    try {
      await api.marcarLeida(uid);
      resumen.noLeidos = 0;
      pintarLista();
    } catch {
      // Se reintenta con el siguiente mensaje o al volver a abrirla.
    }
  }

  /** Varios mensajes seguidos, una sola llamada. */
  function marcarMasTarde(uid) {
    reloj.clearTimeout?.(marcaDiferida);
    marcaDiferida = reloj.setTimeout(() => marcarComoLeida(uid, { forzar: true }), 1000);
  }

  // Lo que llegó con la pestaña en segundo plano se marca al volver a ella.
  const alVolverALaPestana = () => {
    if (abierta && visible()) {
      marcarComoLeida(abierta.uid, { forzar: true });
    }
  };
  globalThis.document?.addEventListener?.('visibilitychange', alVolverALaPestana);

  // ---------------------------------------------------------------- recibir

  /**
   * Un mensaje (propio o ajeno) ya guardado en el servidor: al hilo si es de
   * la conversación abierta, y al resumen de su conversación en la lista. Un
   * mismo mensaje puede llegar dos veces (por el canal y por el sondeo): se
   * cuenta y se anuncia una sola.
   */
  function incorporar(mensaje, { anunciarSiEsAjeno = true } = {}) {
    const uid = otroDe(mensaje);
    const propio = mensaje.remitente === yo;
    const resumen = resumenDe(uid, propio ? null : mensaje.apodoRemitente);
    if (String(mensaje.fecha ?? '') >= String(resumen.ultimoMensaje?.fecha ?? '')) {
      resumen.ultimoMensaje = mensaje;
    }
    const primeraVez = !vistos.has(mensaje.id);
    vistos.add(mensaje.id);
    const enElHilo = abierta?.uid === uid;
    const pintado = enElHilo ? agregarAlHilo(mensaje) : false;
    if (!propio && primeraVez) {
      if (enElHilo) {
        resumen.noLeidos = 0;
        marcarMasTarde(uid);
      } else if (!mensaje.leido) {
        resumen.noLeidos = (Number(resumen.noLeidos) || 0) + 1;
      }
      if (anunciarSiEsAjeno) {
        anunciar(`Mensaje nuevo de ${mensaje.apodoRemitente || 'un jugador'}: ${mensaje.texto}`);
      }
    }
    if (pintado) {
      llevarAlFinal();
    }
    if (enElHilo && !abierta.apodo && !propio) {
      abierta.apodo = mensaje.apodoRemitente;
      zonas.titulo.textContent = tituloDelHilo(uid);
    }
    pintarLista();
  }

  /** Lo que llega por la cola de usuario: `MensajeEntregado` o `EnvioRechazado`. */
  function recibir(payload) {
    if (!payload || typeof payload !== 'object') {
      return;
    }
    if (payload.tipo === 'RECHAZO') {
      fallar(payload.idCliente, payload.motivo);
      return;
    }
    if (payload.tipo !== 'MENSAJE') {
      return;
    }
    if (payload.idCliente && pendientes.has(payload.idCliente)) {
      confirmar(payload);
      return;
    }
    incorporar(payload);
  }

  // ----------------------------------------------------------------- enviar

  function confirmar(mensaje) {
    const pendiente = pendientes.get(mensaje.idCliente);
    if (pendiente) {
      reloj.clearTimeout?.(pendiente.temporizador);
      pendientes.delete(mensaje.idCliente);
      pendiente.linea?.remove();
      abierta?.ids?.delete(pendiente.idProvisional);
    }
    incorporar(mensaje, { anunciarSiEsAjeno: false });
    // Una conversación nueva no tenía apodo: el servidor sí lo sabe.
    if (!conversaciones.get(otroDe(mensaje))?.apodoOtro) {
      refrescarConversaciones().then(() => {
        if (abierta?.uid === otroDe(mensaje)) {
          abierta.apodo = conversaciones.get(abierta.uid)?.apodoOtro ?? null;
          zonas.titulo.textContent = tituloDelHilo(abierta.uid);
        }
      });
    }
  }

  function fallar(idCliente, motivo) {
    const pendiente = idCliente ? pendientes.get(idCliente) : null;
    if (pendiente) {
      reloj.clearTimeout?.(pendiente.temporizador);
      pendientes.delete(idCliente);
      if (pendiente.linea) {
        pendiente.linea.dataset.estado = 'fallido';
        const estado = pendiente.linea.querySelector('.mensaje-directo__estado');
        if (estado) {
          estado.textContent = ' · No se envió';
        }
      }
    }
    pintarAviso(zonas.avisoRedactor, textoDeRechazo(motivo));
  }

  async function porRest(idCliente) {
    const pendiente = pendientes.get(idCliente);
    if (!pendiente) {
      return;
    }
    try {
      const guardado = await api.enviarPorRest(pendiente.uid, {
        texto: pendiente.texto,
        idCliente,
      });
      if (pendientes.has(idCliente)) {
        confirmar(guardado);
      }
    } catch (error) {
      if (pendientes.has(idCliente)) {
        fallar(idCliente, error?.motivo ?? null);
      }
    }
  }

  function enviar(texto) {
    const uid = abierta?.uid;
    if (!uid) {
      return;
    }
    const idCliente = nuevoIdCliente();
    const provisional = {
      id: `pendiente-${idCliente}`,
      remitente: yo,
      destinatario: uid,
      texto,
      fecha: new Date().toISOString(),
      idCliente,
    };
    agregarAlHilo(provisional, { estado: 'enviando' });
    const linea = lineaDe(`[data-id="${provisional.id}"]`);
    pendientes.set(idCliente, {
      uid,
      texto,
      linea,
      idProvisional: provisional.id,
      temporizador: null,
    });
    llevarAlFinal();

    if (canal?.conectado) {
      try {
        canal.enviar(destinoDeEnvio(uid), { texto, idCliente });
        // Si el eco no llega, se manda por REST con el mismo idCliente: el
        // servidor lo reconoce y no duplica.
        pendientes.get(idCliente).temporizador = reloj.setTimeout(
          () => porRest(idCliente),
          esperaDelEco,
        );
        return;
      } catch {
        // El canal se cayó justo ahora: sigue por REST.
      }
    }
    porRest(idCliente);
  }

  zonas.redactor?.addEventListener('submit', (evento) => {
    evento.preventDefault();
    const texto = campoTexto.value.trim();
    if (!texto || texto.length > LARGO_MAXIMO) {
      pintarAviso(zonas.avisoRedactor, textoDeRechazo('TEXTO_INVALIDO'));
      campoTexto.focus();
      return;
    }
    limpiarAviso(zonas.avisoRedactor);
    enviar(texto);
    campoTexto.value = '';
    if (zonas.contador) {
      zonas.contador.textContent = textoDelContador('');
    }
    campoTexto.focus();
  });

  campoTexto?.addEventListener('input', () => {
    if (zonas.contador) {
      zonas.contador.textContent = textoDelContador(campoTexto.value);
    }
  });

  zonas.anteriores?.addEventListener('click', () => cargarAnteriores());

  // ---------------------------------------------- tiempo real y su respaldo

  async function sondear() {
    await refrescarConversaciones();
    if (!abierta) {
      return;
    }
    const uid = abierta.uid;
    try {
      const recientes = await api.historial(uid, { limite: TAMANO_DE_PAGINA });
      if (abierta?.uid !== uid) {
        return;
      }
      for (const mensaje of recientes ?? []) {
        if (mensaje.idCliente && pendientes.has(mensaje.idCliente)) {
          confirmar(mensaje);
        } else if (!abierta.ids.has(mensaje.id)) {
          incorporar(mensaje);
        }
      }
    } catch {
      // Se reintenta en el siguiente sondeo.
    }
  }

  function empezarSondeo() {
    if (sondeo || cerrada) {
      return;
    }
    sondeo = reloj.setInterval(() => sondear(), intervaloSondeo);
    if (zonas.avisoConexion) {
      zonas.avisoConexion.hidden = false;
    }
  }

  function pararSondeo() {
    if (sondeo) {
      reloj.clearInterval?.(sondeo);
      sondeo = null;
    }
    if (zonas.avisoConexion) {
      zonas.avisoConexion.hidden = true;
    }
  }

  function alEstado(estado) {
    pintarEstadoDelCanal(zonas.conexion, {
      ...estado,
      alReintentar: () => (canal ? canal.reintentar() : conectarCanal()),
    });
    if (estado.estado === 'conectado' || estado.estado === 'reconectado') {
      pararSondeo();
    } else {
      empezarSondeo();
    }
  }

  async function conectarCanal() {
    if (typeof abrirCanal !== 'function') {
      alEstado({ estado: 'sin-conexion' });
      return;
    }
    try {
      canal = await abrirCanal({
        alEstado,
        alReconectar: () => sondear(),
      });
      canal.suscribir(COLA_MENSAJES, recibir);
      alEstado({ estado: 'conectado' });
    } catch {
      canal = null;
      alEstado({ estado: 'sin-conexion' });
    }
  }

  // ------------------------------------------------------------- arranque

  if (zonas.contador) {
    zonas.contador.textContent = textoDelContador('');
  }
  zonas.redactor.hidden = true;
  zonas.anteriores.hidden = true;

  const [cargadas] = await Promise.all([cargarConversaciones(), conectarCanal()]);
  const pedida = conversacionDesdeUrl(busqueda);
  if (new URLSearchParams(busqueda ?? '').get('con') && !pedida) {
    pintarAviso(zonas.avisoGeneral, {
      tono: 'advertencia',
      titulo: 'Ese enlace no lleva a ningún jugador',
      detalle: 'Elige una conversación de la lista o escribe desde el chat.',
    });
  }
  const primera = pedida ?? (cargadas ? ordenadas()[0]?.uidOtro : null);
  if (primera) {
    await seleccionar(primera);
  } else {
    sinConversacionAbierta();
  }

  return {
    seleccionar,
    recibir,
    sondear,
    cerrar() {
      cerrada = true;
      pararSondeo();
      reloj.clearTimeout?.(marcaDiferida);
      globalThis.document?.removeEventListener?.('visibilitychange', alVolverALaPestana);
      canal?.cerrar?.();
    },
  };
}
