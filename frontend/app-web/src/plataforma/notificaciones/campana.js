/**
 * HU-NOT-006 — Campana de notificaciones: contador, panel, emergentes y
 * estado del canal.
 *
 * Solo pinta lo que `bandeja.js` publica; no habla con el servicio. Usa las
 * clases del ui-kit (`cabecera__campana`, `cabecera__contador`, `conexion`,
 * `aviso`, `tarjeta`, `distintivo`, `estado-vista`) y ninguna propia.
 *
 * Marcado que espera dentro de `raiz`:
 *
 *   [data-zona="boton"]        boton que abre/cierra el panel
 *   [data-zona="contador"]     `.cabecera__contador`, oculto cuando no hay no leidos
 *   [data-zona="panel"]        contenedor del panel (hidden por omision)
 *   [data-zona="conexion"]     `.conexion`, estado del canal (mapeo §5.4)
 *   [data-zona="lista"]        lista de avisos
 *   [data-zona="lista-vacia"]  estado vacio de la lista
 *   [data-zona="marcar-todas"] boton «Marcar todas como leídas» (HU-NOT-001 CA-02),
 *                              deshabilitado sin no leidos y mientras responde
 *   [data-zona="emergentes"]   region viva donde aparecen los avisos nuevos
 *
 * La emergente no bloquea: es un `Aviso` de informacion en una region
 * `aria-live`, con cierre propio y cierre automatico. No hay velo ni dialogo.
 */

import { crearBandeja, ESTADO_CANAL } from './bandeja.js';
import { vaciar } from '../../comun/ui/dom.js';
import { RUTAS, resolver } from '../../comun/sesion.js';

/**
 * Invitación a una sala — salas-partidas 1.10.0 (revisión del modo jugador del
 * 6-oct, punto 13). El aviso lo emite salas-partidas con un `id` que lleva la
 * sala y, si es privada, el código: `sala:{idSala}:invitacion:{uid}` y
 * `…:codigo:{CODIGO}`. Este módulo no sabe de salas: solo convierte ese id en
 * el enlace de invitación que el listado ya entiende (`?sala=…&codigo=…`).
 */
const ID_DE_INVITACION =
  /^sala:([0-9a-f-]{36}):invitacion:[0-9a-f-]{36}(?::codigo:([A-Za-z0-9-]{1,20}))?$/i;

/**
 * El enlace «Unirme» de un aviso de invitación, o null si no lo es.
 *
 * @param {{id?: string}} aviso
 * @param {string} [destino] el listado de salas
 * @returns {string|null}
 */
export function enlaceDeInvitacion(aviso, destino = resolver(RUTAS.batallas, import.meta.url)) {
  const partes = ID_DE_INVITACION.exec(String(aviso?.id ?? ''));
  if (!partes) {
    return null;
  }
  const url = new URL(destino, globalThis.location?.href ?? 'http://localhost/');
  url.searchParams.set('sala', partes[1]);
  if (partes[2]) {
    url.searchParams.set('codigo', partes[2]);
  }
  return url.href;
}

/**
 * UX-R3.8 — el texto sin canal decia «Sin canal en tiempo real: consultando
 * periodicamente», en rojo. Dos cosas mal a la vez:
 *
 *  1. Nombraba el mecanismo («canal en tiempo real», «consultando») en vez de
 *     la consecuencia. Lo que le pasa a quien lo lee es que sus avisos llegan
 *     con retraso, no que un canal este caido.
 *  2. Iba en el tono de error, y esto no es un error: la vista sigue
 *     funcionando, sigue trayendo notificaciones y se pone al dia sola. El
 *     rojo es para lo que esta roto.
 */
const TEXTO_CONEXION = Object.freeze({
  // Auditoría de DEV del 30-sep: «Notificaciones al instante» se leía como un
  // interruptor. Es un estado, y lo dice.
  [ESTADO_CANAL.ESTABLE]: 'Conectado: los avisos llegan al instante',
  [ESTADO_CANAL.RECONECTANDO]: 'Reconectando…',
  [ESTADO_CANAL.SIN_CONEXION]: 'Los avisos pueden tardar un poco en llegar',
});

const formatoDeFecha = new Intl.DateTimeFormat('es-CO', {
  dateStyle: 'medium',
  timeStyle: 'short',
});

/** @param {string} iso */
export function fechaLegible(iso) {
  const fecha = new Date(iso);
  return Number.isNaN(fecha.getTime()) ? String(iso ?? '') : formatoDeFecha.format(fecha);
}

/** Texto del contador para lectores de pantalla. */
export function textoDeContador(noLeidas) {
  if (noLeidas === 0) {
    return 'Sin notificaciones sin leer';
  }
  return noLeidas === 1 ? '1 notificación sin leer' : `${noLeidas} notificaciones sin leer`;
}

function pintarContador(raiz, noLeidas) {
  const contador = raiz.querySelector('[data-zona="contador"]');
  const boton = raiz.querySelector('[data-zona="boton"]');
  if (contador) {
    contador.textContent = String(noLeidas);
    contador.hidden = noLeidas === 0;
  }
  if (boton) {
    boton.setAttribute('aria-label', textoDeContador(noLeidas));
  }
}

/** HU-NOT-001 CA-02: sin no leidos no hay nada que marcar; mientras responde, tampoco. */
function pintarMarcarTodas(raiz, noLeidas, enCurso) {
  const boton = raiz.querySelector('[data-zona="marcar-todas"]');
  if (boton) {
    boton.disabled = enCurso || noLeidas === 0;
  }
}

function pintarConexion(raiz, estadoCanal) {
  const zona = raiz.querySelector('[data-zona="conexion"]');
  if (!zona) {
    return;
  }
  zona.className = `conexion conexion--${estadoCanal}`;
  zona.textContent = TEXTO_CONEXION[estadoCanal] ?? estadoCanal;
}

function nodoDeAviso(aviso, alMarcar) {
  const item = document.createElement('li');
  item.className = 'tarjeta pila pila--compacta';
  item.dataset.avisoId = aviso.id;
  item.dataset.leida = String(Boolean(aviso.leida));

  const cabecera = document.createElement('div');
  cabecera.className = 'fila';

  const titulo = document.createElement('span');
  titulo.className = 'tarjeta__titulo';
  titulo.textContent = aviso.titulo;
  cabecera.appendChild(titulo);

  const tipo = document.createElement('span');
  tipo.className = 'distintivo';
  tipo.textContent = aviso.tipo ?? '';
  cabecera.appendChild(tipo);

  item.appendChild(cabecera);

  const cuerpo = document.createElement('p');
  cuerpo.className = 't-cuerpo';
  cuerpo.textContent = aviso.cuerpo ?? '';
  item.appendChild(cuerpo);

  const pie = document.createElement('div');
  pie.className = 'tarjeta__pie fila';

  const fecha = document.createElement('time');
  fecha.className = 'tarjeta__meta';
  fecha.dateTime = aviso.creadaEn ?? '';
  fecha.textContent = fechaLegible(aviso.creadaEn);
  pie.appendChild(fecha);

  // Punto 13: una invitación a una sala se acepta desde aquí mismo.
  const unirme = enlaceDeInvitacion(aviso);
  if (unirme) {
    const enlace = document.createElement('a');
    enlace.className = 'boton boton--primario boton--pequeno';
    enlace.dataset.accion = 'unirme-a-la-sala';
    enlace.href = unirme;
    enlace.textContent = 'Unirme';
    pie.appendChild(enlace);
  }

  if (aviso.leida) {
    const leida = document.createElement('span');
    leida.className = 'tarjeta__meta';
    leida.textContent = 'Leída';
    pie.appendChild(leida);
  } else {
    const marcar = document.createElement('button');
    marcar.type = 'button';
    marcar.className = 'boton boton--secundario boton--pequeno';
    marcar.dataset.accion = 'marcar-leida';
    marcar.textContent = 'Marcar como leída';
    marcar.addEventListener('click', () => alMarcar(aviso.id));
    pie.appendChild(marcar);
  }

  item.appendChild(pie);
  return item;
}

function pintarLista(raiz, avisos, alMarcar) {
  const lista = raiz.querySelector('[data-zona="lista"]');
  const vacia = raiz.querySelector('[data-zona="lista-vacia"]');
  if (!lista) {
    return;
  }
  vaciar(lista);
  avisos.forEach((aviso) => lista.appendChild(nodoDeAviso(aviso, alMarcar)));
  if (vacia) {
    vacia.hidden = avisos.length > 0;
  }
}

/**
 * Aviso emergente, no bloqueante, con cierre propio y automatico.
 *
 * @returns {HTMLElement} el nodo pintado
 */
export function pintarEmergente(zona, aviso, { duracion = 8000, programar = setTimeout } = {}) {
  const emergente = document.createElement('div');
  emergente.className = 'aviso aviso--info';
  emergente.setAttribute('role', 'status');
  emergente.dataset.avisoId = aviso.id;

  const cuerpo = document.createElement('div');
  const titulo = document.createElement('p');
  titulo.className = 'aviso__titulo';
  titulo.textContent = aviso.titulo;
  cuerpo.appendChild(titulo);
  const texto = document.createElement('p');
  texto.textContent = aviso.cuerpo ?? '';
  cuerpo.appendChild(texto);
  emergente.appendChild(cuerpo);

  const cerrar = document.createElement('button');
  cerrar.type = 'button';
  cerrar.className = 'boton boton--secundario boton--pequeno';
  cerrar.dataset.accion = 'cerrar-emergente';
  cerrar.setAttribute('aria-label', 'Cerrar aviso');
  cerrar.textContent = '×';
  cerrar.addEventListener('click', () => emergente.remove());
  emergente.appendChild(cerrar);

  zona.appendChild(emergente);
  if (duracion > 0) {
    programar(() => emergente.remove(), duracion);
  }
  return emergente;
}

/**
 * Monta la campana y arranca la bandeja.
 *
 * @param {ParentNode} raiz
 * @param {object} opciones
 * @param {string} [opciones.usuarioId]
 * @param {string} [opciones.sesionId]
 * @param {Function} [opciones.fabrica] `(callbacks) => bandeja`; inyeccion para las pruebas
 * @param {number} [opciones.duracionEmergente]
 * @param {Function} [opciones.programar]
 * @param {Function} [opciones.alError]
 * @returns {{bandeja: object, abrir: Function, cerrar: Function}}
 */
export function montarCampana(
  raiz,
  {
    usuarioId,
    sesionId,
    fabrica = (callbacks) => crearBandeja({ usuarioId, sesionId, ...callbacks }),
    duracionEmergente = 8000,
    programar = setTimeout,
    alError = (error) => console.warn('Notificaciones:', error?.message ?? error),
  } = {},
) {
  const panel = raiz.querySelector('[data-zona="panel"]');
  const boton = raiz.querySelector('[data-zona="boton"]');
  const emergentes = raiz.querySelector('[data-zona="emergentes"]');

  const marcar = (id) => bandeja.marcarLeida(id).catch(alError);

  let noLeidas = 0;
  let marcandoTodas = false;
  const marcarTodas = () => {
    marcandoTodas = true;
    pintarMarcarTodas(raiz, noLeidas, marcandoTodas);
    return bandeja
      .marcarTodasLeidas()
      .catch(alError)
      .finally(() => {
        marcandoTodas = false;
        pintarMarcarTodas(raiz, noLeidas, marcandoTodas);
      });
  };

  const bandeja = fabrica({
    alCambiar(estado) {
      noLeidas = estado.noLeidas;
      pintarContador(raiz, estado.noLeidas);
      pintarConexion(raiz, estado.canal);
      pintarLista(raiz, estado.avisos, marcar);
      pintarMarcarTodas(raiz, estado.noLeidas, marcandoTodas);
    },
    alAviso(aviso) {
      if (emergentes) {
        pintarEmergente(emergentes, aviso, { duracion: duracionEmergente, programar });
      }
    },
    alError,
  });

  const abrir = () => {
    if (panel) {
      panel.hidden = false;
    }
    boton?.setAttribute('aria-expanded', 'true');
  };
  const cerrar = () => {
    if (panel) {
      panel.hidden = true;
    }
    boton?.setAttribute('aria-expanded', 'false');
  };

  if (boton) {
    boton.setAttribute('aria-expanded', 'false');
    boton.addEventListener('click', () => {
      if (panel?.hidden === false) {
        cerrar();
      } else {
        abrir();
      }
    });
  }

  raiz.querySelector('[data-zona="marcar-todas"]')?.addEventListener('click', marcarTodas);

  pintarContador(raiz, 0);
  pintarConexion(raiz, ESTADO_CANAL.SIN_CONEXION);
  pintarMarcarTodas(raiz, 0, marcandoTodas);
  bandeja.iniciar();

  return { bandeja, abrir, cerrar };
}
