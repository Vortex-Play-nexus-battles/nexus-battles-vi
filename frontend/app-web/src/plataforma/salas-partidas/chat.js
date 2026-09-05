/**
 * HU-JUE-015 — Vista del chat de sala y de vista general.
 *
 * Es la misma vista para los dos lugares: cambia el canal, no las reglas.
 * Con `?sala={id}` en la URL es el chat de esa sala; sin el, es el general.
 *
 * Flujo: historial al suscribirse (una respuesta), mensajes en vivo por el
 * tema del canal, y errores por la cola privada del jugador, que se pintan
 * con el componente Aviso segun la tabla 4 del mapeo de errores. Nunca se
 * decide por el texto del error.
 *
 * El token de acceso se lee de sessionStorage con la misma convencion de
 * nombres del login (`nexus.rolActual`, `nexus.apodoActual`). Hoy el login no
 * lo guarda: queda declarado como pendiente con el equipo de cuentas.
 */

import { conectarChat, ErrorDeCanal } from './cliente-chat.js';

export const CLAVE_TOKEN = 'nexus.tokenAcceso';
export const COLA_DE_ERRORES = '/usuario/cola/salas';

/** Destinos del contrato AsyncAPI para el canal elegido. */
export function destinosDe(canal) {
  if (canal?.idSala) {
    return {
      vivo: `/tema/salas/${canal.idSala}/chat`,
      historial: `/app/salas/${canal.idSala}/chat/historial`,
      envio: `/app/salas/${canal.idSala}/chat`,
    };
  }
  return {
    vivo: '/tema/chat/general',
    historial: '/app/chat/general/historial',
    envio: '/app/chat/general',
  };
}

/** `?sala=<uuid>` es el chat de esa sala; sin parametro, el general. */
export function canalDesdeUrl(busqueda) {
  const idSala = new URLSearchParams(busqueda).get('sala');
  return idSala ? { idSala } : {};
}

/** Codigo HTTP -> variante del componente Aviso (tabla 4 del mapeo). */
export function tonoPara(estado) {
  if (estado >= 500) return 'error';
  if (estado === 404) return 'info';
  return 'advertencia';
}

/** Un mensaje del contrato (mensajeDeChat) como elemento de la lista. */
export function pintarMensaje(mensaje) {
  const item = document.createElement('li');
  item.className = 'chat__mensaje';
  item.dataset.tipo = mensaje.tipo;

  const autor = document.createElement('strong');
  autor.className = 't-etiqueta';
  autor.textContent = mensaje.autor?.apodo ?? 'Jugador';
  item.appendChild(autor);

  const texto = document.createElement('p');
  texto.className = 't-cuerpo';
  texto.textContent = mensaje.texto;
  item.appendChild(texto);

  if (mensaje.tipo === 'chat.logro' && mensaje.logro) {
    const logro = document.createElement('p');
    logro.className = 'logro';
    logro.textContent = `Logro: ${mensaje.logro.titulo} (${mensaje.logro.mision})`;
    item.appendChild(logro);
  }

  const hora = document.createElement('time');
  hora.dateTime = mensaje.enviadoEn;
  hora.textContent = new Date(mensaje.enviadoEn).toLocaleTimeString('es-CO', {
    hour: '2-digit',
    minute: '2-digit',
  });
  item.appendChild(hora);
  return item;
}

function pintarAviso(zona, { tono, titulo, detalle }) {
  zona.innerHTML = '';
  const aviso = document.createElement('div');
  aviso.className = `aviso aviso--${tono}`;
  aviso.setAttribute('role', tono === 'error' || tono === 'advertencia' ? 'alert' : 'status');
  const encabezado = document.createElement('p');
  encabezado.className = 'aviso__titulo';
  encabezado.textContent = titulo;
  aviso.appendChild(encabezado);
  if (detalle) {
    const cuerpo = document.createElement('p');
    cuerpo.textContent = detalle;
    aviso.appendChild(cuerpo);
  }
  zona.appendChild(aviso);
  zona.hidden = false;
}

const TEXTO_CONEXION = {
  estable: 'Conectado',
  reconectando: 'Conectando',
  'sin-conexion': 'Sin conexion',
};

function marcarConexion(indicador, estado) {
  indicador.className = `conexion conexion--${estado}`;
  indicador.textContent = TEXTO_CONEXION[estado];
}

function leerLogro(formulario) {
  const mision = formulario.elements.logroMision?.value.trim();
  const titulo = formulario.elements.logroTitulo?.value.trim();
  return mision && titulo ? { mision, titulo } : null;
}

/** Ruta relativa, como `/api/v1/salas` en cliente-salas.js: el mismo origen o su proxy. */
function urlDelCanal() {
  const protocolo = globalThis.location?.protocol === 'https:' ? 'wss' : 'ws';
  return `${protocolo}://${globalThis.location?.host ?? 'localhost:8084'}/ws`;
}

/**
 * Monta la vista sobre su raiz. Devuelve el cliente conectado, o null si no
 * hubo conexion (sin token, o el servidor la rechazo).
 *
 * @param {HTMLElement} raiz contenedor con [data-zona=mensajes|aviso|conexion] y el form
 * @param {{canal: {idSala?: string}, token: string|null, conectar?: Function, url?: string}} opciones
 */
export async function montarChat(raiz, { canal, token, conectar = conectarChat, url = urlDelCanal() }) {
  const lista = raiz.querySelector('[data-zona="mensajes"]');
  const zonaAviso = raiz.querySelector('[data-zona="aviso"]');
  const indicador = raiz.querySelector('[data-zona="conexion"]');
  const formulario = raiz.querySelector('form');
  const boton = formulario.querySelector('[type="submit"]');
  const destinos = destinosDe(canal);

  if (!token) {
    marcarConexion(indicador, 'sin-conexion');
    boton.disabled = true;
    pintarAviso(zonaAviso, {
      tono: 'advertencia',
      titulo: 'Inicia sesion para chatear',
      detalle: 'El chat necesita tu sesion iniciada para saber quien escribe.',
    });
    return null;
  }

  marcarConexion(indicador, 'reconectando');
  let cliente;
  try {
    cliente = await conectar({ url, token });
  } catch (error) {
    marcarConexion(indicador, 'sin-conexion');
    boton.disabled = true;
    pintarAviso(zonaAviso, { tono: 'error', titulo: 'No hay conexion con el chat', detalle: error.message });
    return null;
  }
  marcarConexion(indicador, 'estable');

  const agregar = (mensaje) => {
    lista.appendChild(pintarMensaje(mensaje));
    lista.scrollTop = lista.scrollHeight;
  };

  cliente.suscribir(destinos.historial, (mensajes) => {
    lista.innerHTML = '';
    (mensajes ?? []).forEach(agregar);
  });
  cliente.suscribir(destinos.vivo, agregar);
  cliente.suscribir(COLA_DE_ERRORES, (problema) => {
    const error = new ErrorDeCanal(problema);
    pintarAviso(zonaAviso, { tono: tonoPara(error.estado), titulo: error.titulo, detalle: error.detalle });
  });
  cliente.alCerrar = () => {
    marcarConexion(indicador, 'sin-conexion');
    boton.disabled = true;
  };

  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    const texto = formulario.elements.texto.value.trim();
    if (!texto) return;
    zonaAviso.hidden = true;
    zonaAviso.innerHTML = '';
    cliente.enviar(destinos.envio, { texto, logro: leerLogro(formulario) });
    formulario.reset();
  });

  return cliente;
}
