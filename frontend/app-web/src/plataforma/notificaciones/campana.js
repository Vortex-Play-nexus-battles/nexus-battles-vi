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
 *   [data-zona="emergentes"]   region viva donde aparecen los avisos nuevos
 *
 * La emergente no bloquea: es un `Aviso` de informacion en una region
 * `aria-live`, con cierre propio y cierre automatico. No hay velo ni dialogo.
 */

import { crearBandeja, ESTADO_CANAL } from './bandeja.js';

const TEXTO_CONEXION = Object.freeze({
  [ESTADO_CANAL.ESTABLE]: 'Notificaciones en tiempo real',
  [ESTADO_CANAL.RECONECTANDO]: 'Reconectando con el canal…',
  [ESTADO_CANAL.SIN_CONEXION]: 'Sin canal en tiempo real: consultando periodicamente',
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
  return noLeidas === 1 ? '1 notificacion sin leer' : `${noLeidas} notificaciones sin leer`;
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

  if (aviso.leida) {
    const leida = document.createElement('span');
    leida.className = 'tarjeta__meta';
    leida.textContent = 'Leida';
    pie.appendChild(leida);
  } else {
    const marcar = document.createElement('button');
    marcar.type = 'button';
    marcar.className = 'boton boton--secundario boton--pequeno';
    marcar.dataset.accion = 'marcar-leida';
    marcar.textContent = 'Marcar como leida';
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
  lista.innerHTML = '';
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

  const bandeja = fabrica({
    alCambiar(estado) {
      pintarContador(raiz, estado.noLeidas);
      pintarConexion(raiz, estado.canal);
      pintarLista(raiz, estado.avisos, marcar);
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

  pintarContador(raiz, 0);
  pintarConexion(raiz, ESTADO_CANAL.SIN_CONEXION);
  bandeja.iniciar();

  return { bandeja, abrir, cerrar };
}
