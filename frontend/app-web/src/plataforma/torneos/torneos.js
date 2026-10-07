/**
 * Torneos — HU-TOR-008 (menú «Torneo»), sobre contracts/openapi/torneos.yaml 1.2.0.
 *
 * Una sola vista: el listado de torneos y, al elegir uno, su detalle con
 * los equipos, el árbol de ocho (RF-TOR-004) y las acciones que corresponden
 * a quien mira: el espectador consulta; el jugador registra su equipo y lo
 * inscribe; el administrador crea, inicia y cancela (D-21). Al crear, el
 * costo de inscripción se propone desde admin-parametros (HU-TOR-002 CA-03, D-23).
 *
 * Todo error llega como problem details y se decide por `motivo`, nunca
 * comparando textos (`shared/ui-kit/MAPEO-ERRORES.md`).
 */

import { fetchWithHttpErrorInterceptor } from '../../comun/interceptors/http-error.interceptor.js';
import { h, nodo } from '../../comun/ui/dom.js';
import { limpiarAviso, pintarAviso } from '../../comun/ui/aviso.js';
import { campo } from '../../comun/ui/campo.js';
import { estadoDeCarga, estadoDeError, estadoVacio } from '../../comun/ui/estado-vista.js';
import { pedirTexto } from '../../comun/ui/dialogo.js';
import { distintivo } from '../../comun/ui/distintivo.js';
import { icono } from '../../comun/ui/icono.js';
import { fechaHora } from '../../comun/ui/formato.js';
import { textoDelServidor } from '../../comun/ui/texto-de-fallo.js';
import {
  emblemaDe,
  imagenDeEmblema,
  selectorDeCompanero,
  selectorDeEmblema,
} from './equipo-formulario.js';
import {
  cuposDelTorneo,
  descripcionDelTorneo,
  dueloDelEncuentro,
  rutaDelTorneo,
  textoDeInscripcion,
} from './torneo-ruta.js';

export const ROLES_DE_ADMINISTRACION = Object.freeze(['ADMINISTRADOR', 'SUPER_ADMINISTRADOR']);

/**
 * Nombres de las llaves del árbol, en el orden en que se pintan.
 *
 * UXC-8 — decían «Llave de ganadores (1-6 y 11)» y «Llave de secundarios
 * (7-10, 12 y 13)»: la numeración interna de RF-TOR-004 escrita para quien
 * juega. La de secundarios es la segunda oportunidad: quien pierde una vez
 * baja ahí, y a la segunda derrota queda fuera.
 */
export const LLAVES = Object.freeze([
  ['GANADORES', 'Llave de ganadores'],
  ['SECUNDARIOS', 'Llave de segunda oportunidad'],
  ['FINAL', 'Gran final'],
]);

/** El nombre de una llave suelta, para frases («Llave de ganadores»). */
export function nombreDeLlave(llave) {
  return LLAVES.find(([clave]) => clave === llave)?.[1] ?? 'Encuentro del torneo';
}

/**
 * El nombre de una ronda del árbol, según cuántos encuentros tiene y si es
 * la última de su llave. Con ocho equipos la de ganadores son cuartos,
 * semifinales y su final; la de segunda oportunidad se numera.
 *
 * @param {string} llave
 * @param {number} ronda
 * @param {{cantidad: number, ultima: boolean}} forma
 * @returns {string}
 */
export function nombreDeRonda(llave, ronda, { cantidad, ultima }) {
  if (llave === 'FINAL') {
    return 'Gran final';
  }
  if (llave === 'GANADORES') {
    if (cantidad >= 4) {
      return 'Cuartos de final';
    }
    if (cantidad === 2) {
      return 'Semifinales';
    }
    if (ultima) {
      return 'Final de ganadores';
    }
  }
  if (llave === 'SECUNDARIOS' && ultima && cantidad === 1) {
    return 'Final de segunda oportunidad';
  }
  return ronda > 0 ? `Ronda ${ronda}` : 'Ronda';
}

function baseDeApi() {
  const meta = globalThis.document?.querySelector?.('meta[name="nexus-api-base"]');
  return String(meta?.content ?? '').replace(/\/+$/, '');
}

/**
 * UXC-9 — lo que se le dice a quien juega por cada `motivo` estable del
 * contrato: qué pasó y qué hacer. El texto del servidor solo se usa si el
 * motivo no está aquí y si se puede leer (`textoDelServidor`).
 */
export const MOTIVOS = Object.freeze({
  VENTANA_DE_91_DIAS: {
    titulo: 'Todavía no se puede crear otro torneo',
    detalle: 'Se abre un torneo cada 91 días y ya hay uno dentro de esa ventana.',
  },
  PERMISO_INSUFICIENTE: {
    titulo: 'Tu cuenta no puede hacer esto',
    detalle:
      'Solo la administración crea, inicia o cancela torneos; solo el capitán cambia a su compañero.',
  },
  ESTADO_NO_PERMITE: {
    titulo: 'El torneo ya cambió de fase',
    detalle:
      'Las inscripciones se cerraron o el torneo ya empezó. Vuelve a abrirlo para ver cómo está.',
  },
  JUGADOR_YA_EN_EQUIPO: {
    titulo: 'Ya estás en un equipo de este torneo',
    detalle: 'Cada jugador va en un solo equipo por torneo. Revisa tu equipo en el detalle.',
  },
  NOMBRE_RECHAZADO: {
    titulo: 'Ese nombre o ese avatar no se admiten',
    detalle: 'Llevan palabras que el juego no permite en nombres públicos. Elige otros.',
  },
  LISTA_NEGRA_NO_DISPONIBLE: {
    titulo: 'No pudimos revisar el nombre ahora',
    detalle: 'Sin esa revisión no se registra ningún equipo. Inténtalo de nuevo en un momento.',
  },
  CUPO_AGOTADO: {
    titulo: 'El torneo está completo',
    detalle: 'Ya hay ocho equipos inscritos. Espera al siguiente torneo.',
  },
  YA_INSCRITO: {
    titulo: 'Tu equipo ya está inscrito',
    detalle: 'No hace falta volver a inscribirlo: su posición está en el árbol.',
  },
  CREDITOS_INSUFICIENTES: {
    titulo: 'No te alcanzan los créditos',
    detalle:
      'La inscripción se paga en créditos al inscribir al equipo. Consigue más y vuelve a intentarlo.',
  },
  INTEGRANTE_SANCIONADO: {
    titulo: 'Un integrante tiene una sanción activa',
    detalle: 'Con una sanción activa no se puede inscribir el equipo. Revisa «Mis sanciones».',
  },
  LIBRO_NO_DISPONIBLE: {
    titulo: 'Los créditos no responden ahora',
    detalle: 'No se cobró ni se reservó nada. Inténtalo de nuevo en un momento.',
  },
  SANCIONES_NO_DISPONIBLES: {
    titulo: 'No pudimos revisar las sanciones',
    detalle: 'Sin esa revisión no se inscribe ningún equipo. Inténtalo de nuevo en un momento.',
  },
  SIN_EQUIPOS: {
    titulo: 'No hay equipos inscritos',
    detalle: 'El torneo no arranca sin al menos un equipo de jugadores inscrito.',
  },
  NO_ENCONTRADO: {
    titulo: 'Ese torneo ya no está',
    detalle: 'Puede que lo hayan cancelado. Vuelve al listado de torneos.',
  },
});

export class ErrorDeTorneos extends Error {
  constructor(problema, estado) {
    const motivo = problema?.motivo ?? null;
    const conocido = motivo ? MOTIVOS[motivo] : null;
    const legible = textoDelServidor(problema, estado, '');
    const respaldo =
      estado >= 500
        ? 'Los torneos no responden ahora mismo. Vuelve a intentarlo en un momento.'
        : 'No se pudo completar. Vuelve a intentarlo.';
    const detalle = conocido?.detalle ?? (legible || respaldo);
    super(detalle);
    this.name = 'ErrorDeTorneos';
    this.estado = estado;
    this.titulo = conocido?.titulo ?? 'No se pudo completar';
    this.detalle = detalle;
    this.motivo = motivo;
    this.proximaFechaPosible = problema?.proximaFechaPosible ?? null;
  }
}

async function pedir(ruta, opciones = {}, fetchImpl = fetchWithHttpErrorInterceptor) {
  const respuesta = await fetchImpl(`${baseDeApi()}${ruta}`, {
    ...opciones,
    headers: { Accept: 'application/json', ...(opciones.headers ?? {}) },
  });
  if (respuesta.ok) {
    return respuesta.status === 204 ? null : respuesta.json();
  }
  let problema = null;
  try {
    problema = await respuesta.json();
  } catch {
    problema = null;
  }
  throw new ErrorDeTorneos(problema, respuesta.status);
}

const json = (cuerpo, method = 'POST') => ({
  method,
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(cuerpo),
});

export const api = {
  listar: (f) => pedir('/api/v1/torneos', {}, f),
  obtener: (id, f) => pedir(`/api/v1/torneos/${encodeURIComponent(id)}`, {}, f),
  crear: (cuerpo, f) => pedir('/api/v1/torneos', json(cuerpo), f),
  cancelar: (id, motivo, f) =>
    pedir(`/api/v1/torneos/${encodeURIComponent(id)}/cancelacion`, json({ motivo }), f),
  crearEquipo: (id, cuerpo, f) =>
    pedir(`/api/v1/torneos/${encodeURIComponent(id)}/equipos`, json(cuerpo), f),
  inscribir: (id, equipoId, f) =>
    pedir(
      `/api/v1/torneos/${encodeURIComponent(id)}/equipos/${encodeURIComponent(equipoId)}/inscripcion`,
      { method: 'POST' },
      f,
    ),
  iniciar: (id, f) =>
    pedir(`/api/v1/torneos/${encodeURIComponent(id)}/inicio`, { method: 'POST' }, f),
  // 1.2.0 — vuelve a poner en cola los cobros, devoluciones y premios que
  // fallaron (administrador, RF-ADM-005).
  reintentar: (id, f) =>
    pedir(`/api/v1/torneos/${encodeURIComponent(id)}/operaciones/reintento`, { method: 'POST' }, f),
  // HU-TOR-002 CA-03 — el costo de inscripción que fija el PO (D-23), de
  // admin-parametros (valorVigente): {clave, valor: string|null, tipo, version}.
  costoPorDefecto: (f) =>
    pedir('/api/v1/parametros/torneos.costo-inscripcion-por-defecto/valor', {}, f),
};

/* ---- Presentación (puro, probado) ---- */

export const ESTADOS = Object.freeze({
  INSCRIPCIONES_ABIERTAS: 'Inscripciones abiertas',
  EN_CURSO: 'En curso',
  FINALIZADO: 'Finalizado',
  CANCELADO: 'Cancelado',
});

/** @returns {string} texto del estado y de la ocupación de un torneo */
export function resumenDe(torneo) {
  const estado = ESTADOS[torneo.estado] ?? torneo.estado;
  const cupos = `${torneo.equiposInscritos} de ${torneo.cupos} equipos`;
  const costo = torneo.costoInscripcion > 0 ? `${torneo.costoInscripcion} créditos` : 'gratuito';
  return `${estado} · ${cupos} · ${costo}`;
}

/** El equipo del jugador en ese torneo, si lo tiene. */
export function miEquipo(torneo, uid) {
  return torneo.equipos.find((e) => !e.ia && e.integrantes.includes(uid)) ?? null;
}

/**
 * Qué puede hacer quien mira (CA-03 de HU-TOR-008: los botones lo dicen).
 *
 * @returns {{crearEquipo: boolean, inscribir: boolean, motivo: string}}
 */
export function accionesDe(torneo, uid) {
  if (!uid) {
    return { crearEquipo: false, inscribir: false, motivo: 'Inicia sesión para inscribirte.' };
  }
  if (torneo.estado !== 'INSCRIPCIONES_ABIERTAS') {
    return { crearEquipo: false, inscribir: false, motivo: 'Las inscripciones están cerradas.' };
  }
  const equipo = miEquipo(torneo, uid);
  if (!equipo) {
    if (torneo.equiposInscritos >= torneo.cupos) {
      return { crearEquipo: false, inscribir: false, motivo: 'Cupo agotado.' };
    }
    return { crearEquipo: true, inscribir: false, motivo: 'Registra tu equipo de dos.' };
  }
  if (equipo.inscrito) {
    return {
      crearEquipo: false,
      inscribir: false,
      motivo: `Ya estás inscrito con «${equipo.nombre}» (posición ${equipo.posicion}).`,
    };
  }
  // CA-03 — un equipo registrado que no se inscribió antes de que se llenara
  // el cupo ya no tiene sitio: ofrecerle «Inscribir» solo llevaba al rechazo.
  if (torneo.equiposInscritos >= torneo.cupos) {
    return {
      crearEquipo: false,
      inscribir: false,
      motivo: `Cupo agotado: «${equipo.nombre}» ya no puede inscribirse.`,
    };
  }
  return {
    crearEquipo: false,
    inscribir: true,
    motivo: `Tu equipo «${equipo.nombre}» falta por inscribirse.`,
  };
}

/** Nombre corto de un equipo por id (o «por definir»). */
export function nombreDe(torneo, equipoId) {
  if (!equipoId) {
    return 'por definir';
  }
  const equipo = torneo.equipos.find((e) => e.id === equipoId);
  // UXC-9 — antes, los ocho primeros caracteres del identificador.
  return equipo?.nombre || 'Equipo sin nombre';
}

/** Los encuentros de una llave, en orden. */
export function encuentrosDe(torneo, llave) {
  return torneo.encuentros.filter((e) => e.llave === llave).sort((a, b) => a.numero - b.numero);
}

/* ---- DOM ---- */

function avisarError(zona, error) {
  const deNegocio = error instanceof ErrorDeTorneos;
  let detalle = deNegocio ? error.detalle : 'Revisa tu conexión e inténtalo de nuevo.';
  if (deNegocio && error.proximaFechaPosible) {
    detalle += ` Próxima fecha posible: ${new Date(error.proximaFechaPosible).toLocaleDateString('es-CO')}.`;
  }
  pintarAviso(zona, {
    tono: deNegocio && error.estado < 500 ? 'advertencia' : 'error',
    titulo: deNegocio ? error.titulo : 'No pudimos conectar con los torneos',
    detalle,
  });
}

/** Un clic que el navegador debe resolver solo: otra pestaña, otra ventana. */
function clicConModificador(evento) {
  return (
    evento.defaultPrevented ||
    evento.button !== 0 ||
    evento.metaKey ||
    evento.ctrlKey ||
    evento.shiftKey ||
    evento.altKey
  );
}

/** Un dato de la tarjeta o de la portada: etiqueta arriba, valor con su icono. */
function datoDeTorneo(etiqueta, valor, iconoDelDato) {
  return h('div', {
    hijos: [
      h('dt', { texto: etiqueta }),
      h('dd', {
        hijos: [icono(iconoDelDato, { etiqueta: null }), h('span', { texto: valor })],
      }),
    ],
  });
}

/**
 * La tarjeta de un torneo en el tablón — punto 24 de la revisión del 6-oct:
 * «muy básico su cuadro inicial, quiero algo parecido a misiones».
 *
 * La misma anatomía que la tarjeta de una misión: arriba el arte —el trofeo
 * sobre el cromo, con el color de su fase— y el distintivo; debajo el nombre,
 * lo que toca a cada fase, los cupos como barra con su cifra mientras hay
 * inscripciones, el formato y lo que cuesta; y la acción. Todo sale de
 * `TorneoResumen`: el contrato no trae imagen ni el nombre del campeón, y no
 * se inventan.
 *
 * «Ver torneo» es un enlace a la ruta del torneo (`?torneo=<id>`): se puede
 * abrir en otra pestaña; con un clic normal se abre en la misma vista, sin
 * recargar. (`.tarjeta--pulsable` sigue fuera: pone la mano en toda la
 * superficie y aquí solo el enlace hace algo.)
 *
 * @param {object} torneo `TorneoResumen`
 * @param {{alAbrir?: (torneo: object) => void, href?: string|null}} [opciones]
 * @returns {HTMLElement}
 */
export function tarjetaDeTorneo(torneo, { alAbrir, href = null } = {}) {
  const idNombre = `torneo-${torneo.id}-nombre`;
  const abiertas = torneo.estado === 'INSCRIPCIONES_ABIERTAS';
  const ver = h('a', {
    clase: 'boton boton--primario boton--pequeno torneo-card__accion',
    texto: 'Ver torneo',
    datos: { accion: 'abrir' },
    atributos: {
      href: href ?? `?torneo=${encodeURIComponent(torneo.id)}`,
      'aria-label': `Ver torneo: ${torneo.nombre}`,
    },
  });
  ver.addEventListener('click', (evento) => {
    if (!alAbrir || clicConModificador(evento)) {
      return;
    }
    evento.preventDefault();
    alAbrir(torneo);
  });
  const datos = [datoDeTorneo('Formato', 'Doble eliminación', 'bandera')];
  if (!abiertas) {
    datos.push(
      datoDeTorneo(
        'Equipos',
        `${Math.min(torneo.equiposInscritos, torneo.cupos)} de ${torneo.cupos}`,
        'usuarios',
      ),
    );
  }
  datos.push(datoDeTorneo('Inscripción', textoDeInscripcion(torneo), 'moneda'));

  return h('article', {
    clase: 'tarjeta torneo-card',
    datos: { torneoId: torneo.id, estado: torneo.estado },
    atributos: { 'aria-labelledby': idNombre },
    hijos: [
      h('div', {
        clase: 'torneo-card__arte',
        hijos: [
          icono('trofeo', { etiqueta: null, clase: 'icono torneo-card__emblema' }),
          h('div', { clase: 'torneo-card__chips', hijos: [distintivoDeTorneo(torneo)] }),
        ],
      }),
      h('div', {
        clase: 'torneo-card__cuerpo',
        hijos: [
          h('h2', {
            clase: 'torneo-card__nombre',
            texto: torneo.nombre,
            atributos: { id: idNombre },
          }),
          // UXC-8 — lo que toca decir a cada fase, no «Inscripciones hasta…»
          // de un torneo terminado.
          h('p', { clase: 'torneo-card__fase', texto: faseDe(torneo) }),
          abiertas ? cuposDelTorneo(torneo) : null,
          h('dl', { clase: 'torneo-card__datos', hijos: datos }),
        ],
      }),
      h('div', { clase: 'torneo-card__acciones', hijos: [ver] }),
    ],
  });
}

/** El distintivo de la fase, con variante del kit: el color no va solo. */
export function distintivoDeTorneo(torneo) {
  const variante = {
    INSCRIPCIONES_ABIERTAS: 'abierta',
    EN_CURSO: 'en-juego',
    FINALIZADO: 'completada',
    CANCELADO: 'bloqueada',
  }[torneo.estado];
  return distintivo(ESTADOS[torneo.estado] ?? 'Torneo', variante ?? null);
}

/**
 * Lo que toca decir de un torneo según su fase.
 *
 * @param {object} torneo `TorneoResumen`
 * @returns {string}
 */
export function faseDe(torneo) {
  switch (torneo.estado) {
    case 'INSCRIPCIONES_ABIERTAS':
      return `Inscripciones hasta ${fechaHora(torneo.inscripcionesCierranEn)}`;
    case 'EN_CURSO':
      return 'Se está jugando: abre el torneo para ver el árbol y los resultados.';
    case 'FINALIZADO':
      return torneo.campeonEquipoId
        ? 'Terminado y con campeón: abre el torneo para ver quién ganó.'
        : 'Terminado.';
    case 'CANCELADO':
      // 1.2.0 — la devolución va después de cancelar, y puede tardar.
      return 'Cancelado: se devuelven las inscripciones que se pagaron.';
    default:
      return '';
  }
}

/**
 * Las fechas del torneo que publica el contrato (CA-01 de HU-TOR-008), en el
 * orden en que pasan. Solo las que ya existen: el inicio y el fin llegan
 * nulos hasta que ocurren, y cancelar también fija el fin.
 *
 * @param {object} torneo esquema `Torneo`
 * @returns {string[]}
 */
export function fechasDe(torneo) {
  const fechas = [`Publicado el ${fechaHora(torneo.creadoEn)}`];
  if (torneo.estado === 'INSCRIPCIONES_ABIERTAS') {
    fechas.push(`Inscripciones hasta ${fechaHora(torneo.inscripcionesCierranEn)}`);
  }
  if (torneo.iniciadoEn) {
    fechas.push(`Empezó el ${fechaHora(torneo.iniciadoEn)}`);
  }
  if (torneo.finalizadoEn) {
    const verbo = torneo.estado === 'CANCELADO' ? 'Cancelado' : 'Terminó';
    fechas.push(`${verbo} el ${fechaHora(torneo.finalizadoEn)}`);
  }
  return fechas;
}

export function tarjetaDeEquipo(torneo, equipo, uid) {
  const tarjeta = nodo('article', 'tarjeta pila pila--ajustada');
  tarjeta.dataset.equipoId = equipo.id;
  if (equipo.ia) {
    tarjeta.dataset.ia = 'true';
  }
  // RFINAL-04 — el emblema elegido al registrar el equipo, con su imagen. Un
  // avatar que no es uno de los emblemas (equipos de antes, la máquina) no
  // se pinta: es texto que nadie eligió como imagen.
  const emblema = emblemaDe(equipo.avatar);
  if (emblema) {
    tarjeta.appendChild(
      h('img', {
        clase: 'comentario__avatar',
        atributos: {
          src: imagenDeEmblema(emblema),
          alt: `Emblema ${emblema.nombre}`,
          width: 40,
          height: 40,
          loading: 'lazy',
        },
      }),
    );
  }
  const titulo = nodo('strong', 'tarjeta__titulo', equipo.nombre);
  if (uid && equipo.integrantes.includes(uid)) {
    titulo.textContent += ' (tu equipo)';
  }
  tarjeta.appendChild(titulo);
  let estado = 'Registrado, sin inscribir';
  if (equipo.ia) {
    estado = 'Equipo de la máquina';
  } else if (equipo.inscrito) {
    estado = `Inscrito · posición ${equipo.posicion}`;
  }
  tarjeta.appendChild(nodo('p', 't-meta', estado));
  if (torneo.estado !== 'INSCRIPCIONES_ABIERTAS') {
    tarjeta.appendChild(
      nodo('p', 't-meta', equipo.eliminado ? 'Eliminado' : `Derrotas: ${equipo.derrotas}`),
    );
  }
  return tarjeta;
}

/**
 * HU-TOR-004 CA-04: un encuentro LISTO en el que juega mi equipo se juega en
 * una sala de batalla vinculada; al terminar, salas-partidas informa el
 * ganador a torneos. Aqui solo se decide si mostrar el acceso.
 *
 * @returns {boolean}
 */
export function puedoJugar(torneo, encuentro, uid) {
  if (encuentro.estado !== 'LISTO' || torneo.estado !== 'EN_CURSO') {
    return false;
  }
  const equipo = miEquipo(torneo, uid);
  return Boolean(equipo) && (equipo.id === encuentro.equipoA || equipo.id === encuentro.equipoB);
}

/** Ruta relativa de crear sala con el encuentro prefijado (misma carpeta `plataforma/`). */
export function rutaDeSalaDelEncuentro(torneo, encuentro) {
  const parametros = new URLSearchParams({
    torneo: torneo.id,
    encuentro: String(encuentro.numero),
  });
  return `../salas-partidas/crear-sala.html?${parametros}`;
}

/** Estados del encuentro, en palabras y con la variante del componente. */
const ESTADO_DEL_ENCUENTRO = Object.freeze({
  PENDIENTE: { texto: 'Pendiente', clase: '' },
  LISTO: { texto: 'Listo para jugarse', clase: ' encuentro--en-curso' },
  JUGADO: { texto: 'Jugado', clase: ' encuentro--finalizado' },
});

/**
 * Un encuentro del arbol, con el componente `Encuentro` del sistema de diseno.
 *
 * **Por que cambio.** El kit trae ese componente completo desde el Figma —
 * cabecera con el estado, una fila por equipo, el ganador distinguido por
 * **peso** de letra y no solo por color (`.encuentro__equipo--ganador`)— y
 * esta vista lo ignoraba: ponia la clase `.encuentro` sobre un `<li>` y le
 * metia una frase corrida, «Encuentro 5: Los Dragones vs Los Lobos → gana Los
 * Dragones», dentro de una caja de 220 px pensada para dos filas. El resultado
 * era un arbol de doble eliminacion imposible de leer de un vistazo.
 *
 * El marcador (`.encuentro__marcador`) se queda fuera a proposito: el contrato
 * no trae puntuacion por encuentro, y un hueco vacio es mas honesto que un
 * cero inventado.
 *
 * @param {object} torneo
 * @param {object} encuentro esquema `Encuentro` del contrato
 * @param {string|null} [uid] quien mira, para ofrecerle jugar el suyo
 * @returns {HTMLElement}
 */
export function tarjetaDeEncuentro(torneo, encuentro, uid = null) {
  const estado = ESTADO_DEL_ENCUENTRO[encuentro.estado] ?? { texto: encuentro.estado, clase: '' };
  const mio = uid ? miEquipo(torneo, uid) : null;
  const juegaMiEquipo = Boolean(mio) && [encuentro.equipoA, encuentro.equipoB].includes(mio.id);
  const tarjeta = nodo(
    'article',
    `encuentro${estado.clase}${juegaMiEquipo ? ' encuentro--mio' : ''}`,
  );
  tarjeta.dataset.numero = String(encuentro.numero);
  tarjeta.dataset.estado = encuentro.estado;

  const cabecera = nodo('header', 'encuentro__cabecera');
  cabecera.appendChild(
    nodo('span', undefined, encuentro.numero === 14 ? 'Final' : `Encuentro ${encuentro.numero}`),
  );
  cabecera.appendChild(nodo('span', undefined, estado.texto));
  tarjeta.appendChild(cabecera);

  for (const lado of ['equipoA', 'equipoB']) {
    const id = encuentro[lado];
    const gana = Boolean(encuentro.ganador) && id === encuentro.ganador;
    const fila = nodo('div', `encuentro__equipo${gana ? ' encuentro__equipo--ganador' : ''}`);
    fila.dataset.lado = lado === 'equipoA' ? 'a' : 'b';
    fila.appendChild(nodo('span', undefined, nombreDe(torneo, id)));
    if (mio && id === mio.id) {
      // UXC-8 — «Mi camino» en el árbol: tu equipo se dice con texto, no solo
      // con el borde del encuentro.
      fila.appendChild(nodo('span', 'encuentro__tuyo', 'Tu equipo'));
    }
    if (gana) {
      // El peso de la letra solo lo ve quien mira la pantalla: para un lector
      // de pantalla hay que decirlo.
      fila.appendChild(nodo('span', 'solo-lectores', ' (gana este encuentro)'));
    }
    tarjeta.appendChild(fila);
  }

  if (puedoJugar(torneo, encuentro, uid)) {
    const pie = nodo('div', 'encuentro__equipo');
    const enlace = nodo('a', 'boton boton--primario boton--pequeno', 'Crear sala del encuentro');
    enlace.href = rutaDeSalaDelEncuentro(torneo, encuentro);
    enlace.dataset.accion = 'jugar-encuentro';
    pie.appendChild(enlace);
    tarjeta.appendChild(pie);
  }
  return tarjeta;
}

/**
 * Los encuentros en los que juega un equipo, en orden de número.
 *
 * @param {object} torneo
 * @param {string} equipoId
 */
export function encuentrosDeEquipo(torneo, equipoId) {
  return torneo.encuentros
    .filter((e) => e.equipoA === equipoId || e.equipoB === equipoId)
    .sort((a, b) => a.numero - b.numero);
}

/**
 * El próximo encuentro de un equipo: el primero que no se ha jugado.
 *
 * @returns {object|null}
 */
export function proximoEncuentro(torneo, equipoId) {
  return encuentrosDeEquipo(torneo, equipoId).find((e) => e.estado !== 'JUGADO') ?? null;
}

/**
 * Dónde está tu equipo, en una frase.
 *
 * @param {object} torneo
 * @param {object} equipo
 * @returns {string}
 */
export function situacionDeEquipo(torneo, equipo) {
  if (torneo.campeonEquipoId && torneo.campeonEquipoId === equipo.id) {
    return 'Campeón del torneo';
  }
  if (equipo.eliminado) {
    return 'Eliminado tras dos derrotas';
  }
  if (torneo.estado === 'INSCRIPCIONES_ABIERTAS') {
    return equipo.inscrito
      ? `Inscrito · posición ${equipo.posicion} del árbol`
      : 'Registrado, falta inscribirlo';
  }
  if (torneo.estado === 'EN_CURSO') {
    // Un equipo que no llegó a inscribirse no está en el árbol: no «sigue en juego».
    if (!equipo.inscrito) {
      return 'No llegó a inscribirse';
    }
    return equipo.derrotas > 0
      ? 'Sigue en juego, en la llave de segunda oportunidad'
      : 'Sigue en juego, sin derrotas';
  }
  return equipo.inscrito ? 'Participó en el torneo' : 'No llegó a inscribirse';
}

/**
 * UXC-8 — en qué va el pago de la inscripción de un equipo (`estadoPago`,
 * torneos.yaml 1.2.0). Cobrar y devolver ya no pasan en el acto: van después
 * de iniciar o cancelar, con reintentos, y esto es lo que se ve de ellos.
 */
export const ESTADOS_DE_PAGO = Object.freeze({
  SIN_COSTO: 'Inscripción gratuita',
  RESERVADO: 'Inscripción reservada: se cobra al empezar el torneo',
  COBRO_PENDIENTE: 'Cobrando la inscripción…',
  COBRADO: 'Inscripción pagada',
  DEVOLUCION_PENDIENTE: 'Devolviendo la inscripción…',
  DEVUELTO: 'Inscripción devuelta',
  REQUIERE_REVISION: 'El pago de la inscripción está en revisión: lo resolverá un administrador',
});

/**
 * El pago de la inscripción de tu equipo, con quién lo hizo.
 *
 * @param {object} equipo `Equipo`
 * @param {string} uid quien mira
 * @param {string} otro cómo se llama al otro integrante («tu compañero», «tu capitán»)
 * @returns {string|null} null si no hay nada que decir (sin inscribir, de la máquina)
 */
export function textoDelPago(equipo, uid, otro = 'tu compañero') {
  const estado = ESTADOS_DE_PAGO[equipo.estadoPago];
  if (!estado) {
    return null;
  }
  if (!equipo.pagadoPor || equipo.estadoPago === 'SIN_COSTO') {
    return estado;
  }
  return `${estado} · ${equipo.pagadoPor === uid ? 'la pagaste tú' : `la pagó ${otro}`}`;
}

/** Lo que reparte el premio, en palabras: «500 créditos por integrante y una épica». */
function loteDelPremio(premio) {
  const partes = [];
  if (premio.creditosPorIntegrante > 0) {
    partes.push(`${premio.creditosPorIntegrante} créditos por integrante`);
  }
  if (premio.epicaProductoId) {
    partes.push('una épica para cada uno');
  }
  return partes.join(' y ');
}

/**
 * El premio del torneo (RF-TOR-007, torneos.yaml 1.2.0) en una frase para
 * todos. El monto y la épica son los que se anunciaron al crear el torneo.
 *
 * @param {object} torneo `Torneo`
 * @returns {string|null}
 */
export function textoDelPremio(torneo) {
  const premio = torneo.premio;
  if (!premio) {
    return null;
  }
  const lote = loteDelPremio(premio);
  switch (premio.estado) {
    case 'SIN_CAMPEON':
      return lote ? `El equipo campeón gana ${lote}.` : 'Este torneo no tiene premio.';
    case 'PENDIENTE':
      return `Entregando el premio al equipo campeón: ${lote}.`;
    case 'ENTREGADO':
      return `Premio entregado al equipo campeón: ${lote}.`;
    case 'REQUIERE_REVISION':
      return 'El premio está en revisión: un administrador lo está resolviendo.';
    case 'NO_APLICA':
      return lote
        ? 'Ganó un equipo de la máquina: este torneo no reparte premio.'
        : 'Este torneo no tiene premio.';
    default:
      return null;
  }
}

/**
 * Tu parte del premio, si tu equipo lo ganó: la entrega de `premio.entregas`
 * con tu `uid`.
 *
 * @param {object} torneo
 * @param {string} uid
 * @returns {{tono: string, texto: string}|null}
 */
export function miPremio(torneo, uid) {
  const premio = torneo.premio;
  const entrega = premio?.entregas?.find((e) => e.uid === uid);
  if (!entrega) {
    return null;
  }
  switch (entrega.estado) {
    case 'ENTREGADO': {
      const partes = [];
      if (entrega.creditosEntregados && premio.creditosPorIntegrante > 0) {
        partes.push(`${premio.creditosPorIntegrante} créditos`);
      }
      if (entrega.epicaEntregada) {
        partes.push('la épica, ya en tu inventario');
      }
      return {
        tono: 'exito',
        texto: partes.length
          ? `Recibiste tu premio: ${partes.join(' y ')}.`
          : 'Recibiste tu premio.',
      };
    }
    case 'PENDIENTE':
      return {
        tono: 'info',
        texto: 'Tu premio está en camino: te avisaremos en la campana cuando llegue.',
      };
    case 'EXCLUIDO_POR_SANCION':
      return {
        tono: 'advertencia',
        texto: 'No recibes premio: tenías una sanción activa cuando terminó el torneo.',
      };
    case 'REQUIERE_REVISION':
      return {
        tono: 'advertencia',
        texto: 'Tu premio está en revisión: un administrador lo está resolviendo.',
      };
    default:
      return null;
  }
}

/** Si algún cobro, devolución o premio del torneo necesita que un administrador lo reintente. */
export function necesitaRevision(torneo) {
  return (
    torneo.equipos.some((e) => e.estadoPago === 'REQUIERE_REVISION') ||
    torneo.premio?.estado === 'REQUIERE_REVISION' ||
    Boolean(torneo.premio?.entregas?.some((e) => e.estado === 'REQUIERE_REVISION'))
  );
}

/**
 * La marca de un equipo: el emblema que eligió al registrarse (RFINAL-04) o,
 * si no es uno de los emblemas, su inicial. Adorno: el nombre va al lado.
 *
 * @param {{nombre?: string, avatar?: string|null}} equipo
 * @returns {HTMLElement}
 */
function marcaDeEquipo(equipo) {
  const emblema = emblemaDe(equipo.avatar);
  if (emblema) {
    return h('img', {
      clase: 'torneo-mio__emblema',
      atributos: {
        src: imagenDeEmblema(emblema),
        alt: '',
        width: 40,
        height: 40,
        loading: 'lazy',
      },
    });
  }
  return h('span', {
    clase: 'torneo-mio__inicial',
    texto: (equipo.nombre || '?').trim().charAt(0).toUpperCase(),
    atributos: { 'aria-hidden': 'true' },
  });
}

/**
 * Lo tuyo en un torneo — UXC-8: tu equipo, tu próximo encuentro, tu camino y
 * tu parte del premio, cada uno en su bloque.
 *
 * Solo con lo que trae el contrato: el compañero es un identificador sin
 * apodo, así que se dice «tu compañero»; el marcador no existe, así que el
 * camino dice victoria o derrota y contra quién.
 *
 * La ruta del torneo (punto 24) pone cada bloque en su hito, sin título propio
 * (el hito ya lo lleva) y con el duelo del próximo encuentro; mientras hay
 * inscripciones, la situación del equipo la dice la zona de acciones de al
 * lado y aquí no se repite. `panelDeMiTorneo` los junta en un panel.
 *
 * @param {object} torneo
 * @param {object} equipo el de quien mira
 * @param {string} uid
 * @param {{enRuta?: boolean}} [opciones]
 * @returns {{equipo: HTMLElement, proximo: HTMLElement, camino: HTMLElement,
 *   premio: HTMLElement|null}}
 */
export function bloquesDeMiTorneo(torneo, equipo, uid, { enRuta = false } = {}) {
  const soyCapitan = equipo.capitanUid === uid;
  const conCompanero = equipo.integrantes.length > 1;
  const titulo = (texto) => (enRuta ? null : h('h4', { clase: 't-etiqueta', texto }));
  const conSituacion = !enRuta || torneo.estado !== 'INSCRIPCIONES_ABIERTAS';

  const bloqueEquipo = h('article', {
    clase: 'torneo-mio__bloque pila pila--ajustada',
    datos: { zona: 'mi-equipo' },
    hijos: [
      titulo('Mi equipo'),
      h('p', {
        clase: 'torneo-mio__equipo',
        hijos: [marcaDeEquipo(equipo), h('strong', { texto: equipo.nombre })],
      }),
      h('p', {
        clase: 't-meta',
        texto: `${soyCapitan ? 'Tú (capitán)' : 'Tú'}${conCompanero ? ` y ${soyCapitan ? 'tu compañero' : 'tu capitán'}` : ', sin compañero todavía'}`,
      }),
      conSituacion
        ? h('p', {
            clase: 't-cuerpo',
            datos: { zona: 'situacion' },
            texto: situacionDeEquipo(torneo, equipo),
          })
        : null,
    ],
  });
  // UXC-8 — 1.2.0: en qué va el pago de la inscripción.
  const pago = textoDelPago(equipo, uid, soyCapitan ? 'tu compañero' : 'tu capitán');
  if (pago) {
    bloqueEquipo.append(h('p', { clase: 't-meta', datos: { zona: 'pago' }, texto: pago }));
  }
  // Y, si ganasteis, tu parte del premio.
  const premio = miPremio(torneo, uid);
  const bloquePremio = premio
    ? h('p', {
        clase: `torneo-mio__premio torneo-mio__premio--${premio.tono}`,
        datos: { zona: 'mi-premio' },
        hijos: [
          icono('trofeo', { clase: 'icono icono--menudo' }),
          h('span', { texto: premio.texto }),
        ],
      })
    : null;

  const siguiente = proximoEncuentro(torneo, equipo.id);
  const bloqueProximo = h('article', {
    clase: 'torneo-mio__bloque pila pila--ajustada',
    datos: { zona: 'proximo-encuentro' },
    hijos: [titulo('Próximo encuentro')],
  });
  if (siguiente && torneo.estado === 'EN_CURSO') {
    const rival = siguiente.equipoA === equipo.id ? siguiente.equipoB : siguiente.equipoA;
    if (enRuta) {
      bloqueProximo.append(
        dueloDelEncuentro(equipo.nombre, rival ? nombreDe(torneo, rival) : 'Por decidir'),
      );
    }
    bloqueProximo.append(
      h('p', {
        clase: 't-cuerpo',
        texto: rival ? `Contra ${nombreDe(torneo, rival)}` : 'Rival por decidir',
      }),
      h('p', {
        clase: 't-meta',
        texto: `${siguiente.numero === 14 ? 'Gran final' : `Encuentro ${siguiente.numero}`} · ${nombreDeLlave(siguiente.llave)}`,
      }),
    );
    if (puedoJugar(torneo, siguiente, uid)) {
      const enlace = nodo('a', 'boton boton--primario boton--pequeno', 'Crear sala del encuentro');
      enlace.href = rutaDeSalaDelEncuentro(torneo, siguiente);
      enlace.dataset.accion = 'jugar-mi-encuentro';
      bloqueProximo.append(enlace);
    } else {
      bloqueProximo.append(
        h('p', {
          clase: 't-meta',
          texto: 'Se podrá jugar cuando el otro encuentro decida a tu rival.',
        }),
      );
    }
  } else {
    let texto = 'El árbol se genera al cerrar las inscripciones: aquí verás tu primer rival.';
    if (torneo.estado === 'EN_CURSO' && equipo.eliminado) {
      texto = 'Tu equipo ya no juega más encuentros en este torneo.';
    } else if (torneo.estado === 'FINALIZADO') {
      texto = 'El torneo terminó: no quedan encuentros por jugar.';
    } else if (torneo.estado === 'CANCELADO') {
      texto = 'El torneo se canceló.';
    } else if (!equipo.inscrito) {
      // Solo se ofrece inscribirse mientras se puede: con el torneo en curso,
      // o con el cupo lleno, ya no hay forma de entrar en el árbol.
      if (torneo.estado === 'EN_CURSO') {
        texto = 'Tu equipo no llegó a inscribirse, así que no juega en este torneo.';
      } else if (torneo.equiposInscritos >= torneo.cupos) {
        texto = 'El cupo se llenó antes de que tu equipo se inscribiera.';
      } else {
        texto = 'Inscribe a tu equipo para entrar en el árbol.';
      }
    }
    bloqueProximo.append(h('p', { clase: 't-meta', texto }));
  }

  const jugados = encuentrosDeEquipo(torneo, equipo.id).filter((e) => e.estado === 'JUGADO');
  const bloqueCamino = h('article', {
    clase: 'torneo-mio__bloque pila pila--ajustada',
    datos: { zona: 'mi-camino' },
    hijos: [titulo('Mi camino')],
  });
  if (jugados.length === 0) {
    bloqueCamino.append(
      h('p', { clase: 't-meta', texto: 'Todavía no has jugado ningún encuentro de este torneo.' }),
    );
  } else {
    const lista = h('ol', { clase: 'torneo-mio__camino' });
    for (const encuentro of jugados) {
      const rival = encuentro.equipoA === equipo.id ? encuentro.equipoB : encuentro.equipoA;
      const gano = encuentro.ganador === equipo.id;
      lista.append(
        h('li', {
          clase: `torneo-mio__paso torneo-mio__paso--${gano ? 'victoria' : 'derrota'}`,
          datos: { numero: String(encuentro.numero) },
          hijos: [
            icono(gano ? 'check' : 'cerrar', { clase: 'icono icono--menudo' }),
            h('span', {
              texto: `${gano ? 'Victoria' : 'Derrota'} contra ${nombreDe(torneo, rival)}`,
            }),
            h('span', {
              clase: 't-meta',
              texto: ` · ${encuentro.numero === 14 ? 'Gran final' : nombreDeLlave(encuentro.llave)}`,
            }),
          ],
        }),
      );
    }
    bloqueCamino.append(lista);
  }

  return {
    equipo: bloqueEquipo,
    proximo: bloqueProximo,
    camino: bloqueCamino,
    premio: bloquePremio,
  };
}

/**
 * «Tu torneo» — UXC-8: Mi equipo (con su pago y, si ganasteis, tu premio),
 * Próximo encuentro y Mi camino, juntos en un panel.
 *
 * @param {object} torneo
 * @param {object} equipo el de quien mira
 * @param {string} uid
 * @returns {HTMLElement}
 */
export function panelDeMiTorneo(torneo, equipo, uid) {
  const bloques = bloquesDeMiTorneo(torneo, equipo, uid);
  if (bloques.premio) {
    bloques.equipo.append(bloques.premio);
  }
  return h('section', {
    clase: 'tarjeta torneo-mio pila pila--compacta',
    datos: { zona: 'mi-torneo' },
    atributos: { 'aria-label': 'Tu torneo' },
    hijos: [
      h('h3', { texto: 'Tu torneo' }),
      h('div', {
        clase: 'torneo-mio__rejilla',
        hijos: [bloques.equipo, bloques.proximo, bloques.camino],
      }),
    ],
  });
}

/**
 * Los equipos del torneo (CA-01/02 de HU-TOR-008), en rejilla.
 *
 * @param {object} torneo
 * @param {string|null} uid quien mira, para marcar el suyo
 * @returns {HTMLElement}
 */
export function equiposDelTorneo(torneo, uid) {
  const equipos = nodo('section', 'pila pila--compacta torneo__participantes');
  equipos.dataset.zona = 'equipos';
  equipos.appendChild(nodo('h3', 'torneo__subtitulo', `Equipos (${torneo.equipos.length})`));
  if (torneo.equipos.length === 0) {
    equipos.appendChild(
      nodo(
        'p',
        't-meta',
        torneo.estado === 'INSCRIPCIONES_ABIERTAS'
          ? 'Todavía no hay equipos registrados. Registra el tuyo con un compañero para ser el primero.'
          : 'Este torneo no tuvo equipos registrados.',
      ),
    );
  }
  // UX-GAME-5 — ocho tarjetas a lo ancho ocupaban una pantalla entera antes
  // del arbol; en rejilla caben en dos filas.
  const rejilla = nodo('div', 'torneo__equipos');
  torneo.equipos.forEach((e) => rejilla.appendChild(tarjetaDeEquipo(torneo, e, uid)));
  equipos.appendChild(rejilla);
  return equipos;
}

/**
 * El árbol de doble eliminación (HU-TOR-004 CA-03): una columna por ronda y
 * una llave tras otra. Cada llave lleva su título (`h3`): el hito de la ruta
 * ya se llama «El árbol».
 *
 * @param {object} torneo
 * @param {string|null} uid quien mira, para ofrecerle jugar el suyo
 * @returns {HTMLElement}
 */
export function arbolDelTorneo(torneo, uid) {
  const arbol = nodo('section', 'pila pila--compacta torneo__arbol');
  arbol.dataset.zona = 'arbol';
  arbol.setAttribute('aria-label', 'Árbol del torneo');
  if (torneo.encuentros.length === 0) {
    arbol.appendChild(
      nodo(
        'p',
        't-meta',
        torneo.estado === 'CANCELADO'
          ? 'El torneo se canceló antes de empezar: no llegó a tener árbol.'
          : 'El árbol se genera al cerrar las inscripciones: entonces verás contra quién juega cada equipo.',
      ),
    );
  }
  LLAVES.forEach(([llave, titulo]) => {
    const lista = encuentrosDe(torneo, llave);
    if (lista.length === 0) {
      return;
    }
    const tituloLlave = nodo('h3', 't-etiqueta torneo__llave', titulo);
    tituloLlave.id = `torneo-llave-${llave.toLowerCase()}`;
    arbol.appendChild(tituloLlave);
    const cuadro = nodo('div', 'arbol-torneo');
    cuadro.dataset.llave = llave;
    // Barrido final de la revisión del 6-oct (1024 y 768): dentro de la ruta
    // el cuadro es más estrecho que sus rondas y se desplaza de lado (el
    // `overflow-x: auto` del kit). Quien usa el teclado tiene que poder llegar
    // a él para desplazarlo (axe: scrollable-region-focusable): es una región
    // con el nombre de su llave y entra en el orden de tabulación.
    cuadro.tabIndex = 0;
    cuadro.setAttribute('role', 'region');
    cuadro.setAttribute('aria-labelledby', tituloLlave.id);
    const rondas = porRonda(lista);
    rondas.forEach(({ ronda, encuentros }, indice) => {
      const columna = nodo('div', 'arbol-torneo__ronda');
      columna.dataset.ronda = String(ronda);
      // La gran final ya la nombra su llave: repetirlo encima del único
      // encuentro no dice nada nuevo.
      if (llave !== 'FINAL') {
        columna.appendChild(
          nodo(
            'p',
            't-meta',
            nombreDeRonda(llave, ronda, {
              cantidad: encuentros.length,
              ultima: indice === rondas.length - 1,
            }),
          ),
        );
      }
      encuentros.forEach((e) => columna.appendChild(tarjetaDeEncuentro(torneo, e, uid)));
      cuadro.appendChild(columna);
    });
    arbol.appendChild(cuadro);
  });
  return arbol;
}

/**
 * La transmisión — UXC-8, dicha como es: el contrato no la tiene todavía
 * (RF-TOR-006). Se usa el marco `.transmision` del kit en su variante sin
 * señal, con qué pasa, por qué y cómo seguir el torneo mientras tanto.
 *
 * @param {object} torneo
 * @param {{alActualizar?: Function}} [opciones]
 * @returns {HTMLElement}
 */
export function panelDeTransmision(torneo, { alActualizar } = {}) {
  const enCurso = torneo.estado === 'EN_CURSO';
  const actualizar =
    enCurso && alActualizar
      ? nodo('button', 'boton boton--secundario boton--pequeno', 'Actualizar resultados')
      : null;
  if (actualizar) {
    actualizar.type = 'button';
    actualizar.dataset.accion = 'actualizar-torneo';
    actualizar.addEventListener('click', () => alActualizar());
  }
  return h('section', {
    clase: 'transmision transmision--sin-senal',
    datos: { zona: 'transmision' },
    atributos: { 'aria-label': 'Transmisión del torneo' },
    hijos: [
      h('div', {
        clase: 'transmision__lienzo',
        hijos: [
          h('span', { clase: 'transmision__etiqueta', texto: 'Sin transmisión' }),
          icono('transmision', { clase: 'icono transmision__simbolo' }),
        ],
      }),
      h('div', {
        clase: 'transmision__comentarios pila pila--ajustada',
        hijos: [
          h('strong', {
            texto: enCurso
              ? 'Este torneo no se transmite en vivo'
              : 'Este torneo no tiene grabación',
          }),
          h('p', {
            clase: 't-meta',
            texto: enCurso
              ? 'Los encuentros todavía no se pueden ver desde el juego. Sigue el torneo en el árbol: cada resultado aparece al actualizar.'
              : 'Los encuentros no se grabaron. El árbol de abajo cuenta cómo terminó cada uno.',
          }),
          actualizar,
        ],
      }),
    ],
  });
}

/**
 * Los encuentros de una llave agrupados por ronda, que es lo que da forma al
 * arbol. Una lista plana de catorce encuentros no se parece a un cuadro de
 * doble eliminacion; el contrato ya trae `ronda` y no se estaba usando.
 *
 * @param {Array<object>} encuentros ya filtrados por llave y ordenados
 * @returns {Array<{ronda: number, encuentros: Array<object>}>}
 */
export function porRonda(encuentros) {
  const rondas = new Map();
  for (const encuentro of encuentros) {
    const clave = Number.isInteger(encuentro.ronda) ? encuentro.ronda : 0;
    if (!rondas.has(clave)) {
      rondas.set(clave, []);
    }
    rondas.get(clave).push(encuentro);
  }
  return [...rondas.entries()]
    .sort((a, b) => a[0] - b[0])
    .map(([ronda, lista]) => ({ ronda, encuentros: lista }));
}

/** El `valor` de un parámetro como entero >= 0, o null si no lo es. */
function enteroNoNegativo(valor) {
  if (typeof valor !== 'string' || !/^\d+$/.test(valor)) {
    return null;
  }
  const numero = Number(valor);
  return Number.isSafeInteger(numero) ? numero : null;
}

/**
 * HU-TOR-002 CA-03 — propone en el formulario de crear el costo de inscripción
 * que fija el PO en admin-parametros (D-23). Solo un entero >= 0 cambia algo:
 * si el parámetro no existe (404), vale null o admin-parametros no responde,
 * el campo se queda con su respaldo del HTML y no se avisa de nada. Una caída
 * de admin-parametros nunca tumba la vista.
 *
 * Se fija `defaultValue` y no `value`: así el `reset()` que sigue a crear un
 * torneo vuelve al propuesto y no al 0 del HTML.
 */
async function proponerCosto(form, fetchImpl) {
  const control = form.querySelector('[name="costoInscripcion"]');
  if (!control) {
    return;
  }
  let parametro;
  try {
    parametro = await api.costoPorDefecto(fetchImpl);
  } catch {
    return;
  }
  const costo = enteroNoNegativo(parametro?.valor);
  if (costo === null) {
    return;
  }
  control.defaultValue = String(costo);
  const ayuda = form.querySelector('[data-zona="costo-propuesto"]');
  if (ayuda) {
    ayuda.textContent = 'Propuesto por el catálogo de parámetros (D-23); puedes cambiarlo';
    ayuda.hidden = false;
  }
}

/**
 * Monta la vista completa.
 *
 * Dos modos (punto 24 de la revisión del 6-oct): el **tablón** con las
 * tarjetas y la **ruta** de un torneo (`?torneo=<id>`), que ocupa la vista
 * entera en vez de desplegarse debajo. Se pasa de uno a otro sin recargar y
 * la dirección los sigue: atrás y adelante del navegador funcionan, y la
 * dirección de un torneo se puede compartir.
 *
 * @param {ParentNode} raiz zonas `[data-zona=...]`: aviso, listado, detalle,
 *   crear-torneo y, en la vista real, encabezado y tablon
 * @param {{uid?: string|null, rol?: string|null, fetchImpl?: Function,
 *   torneoInicial?: string|null, historial?: History|null, ubicacion?: Location|null,
 *   ventana?: EventTarget|null}} [opciones] las tres últimas, para las pruebas
 */
export function montarTorneos(
  raiz,
  {
    uid = null,
    rol = null,
    fetchImpl,
    torneoInicial = null,
    historial = globalThis.history ?? null,
    ubicacion = globalThis.location ?? null,
    ventana = globalThis.window ?? null,
  } = {},
) {
  const zonaAviso = raiz.querySelector('[data-zona="aviso"]');
  const zonaListado = raiz.querySelector('[data-zona="listado"]');
  const zonaDetalle = raiz.querySelector('[data-zona="detalle"]');
  const formCrear = raiz.querySelector('[data-zona="crear-torneo"]');
  // Lo que se oculta en la ruta: el encabezado de la página y el tablón.
  const zonaEncabezado = raiz.querySelector('[data-zona="encabezado"]');
  const zonaTablon = raiz.querySelector('[data-zona="tablon"]') ?? zonaListado;
  const documento = zonaDetalle.ownerDocument;
  const tituloBase = documento.title;
  const administra = ROLES_DE_ADMINISTRACION.includes(rol);

  if (formCrear) {
    formCrear.hidden = !administra;
    // Solo para quien administra: a un jugador o a un espectador nunca se le
    // pide el parámetro.
    if (administra) {
      proponerCosto(formCrear, fetchImpl);
    }
  }

  /* ---- Tablón y ruta: la dirección sigue al modo ---- */

  // La última petición de detalle: si llegan dos respuestas, se pinta solo
  // la del torneo que se pidió el último.
  let peticionDeDetalle = 0;

  /**
   * La dirección del tablón o de un torneo, sobre la página en la que se
   * está y no sobre el `<base>` del borde: tras las rutas limpias (R17) la
   * página es `/torneos` y su `<base>` apunta a la carpeta del fichero.
   *
   * @param {string|null} torneoId
   * @returns {string}
   */
  function direccion(torneoId) {
    if (!ubicacion?.href) {
      return torneoId ? `?torneo=${encodeURIComponent(torneoId)}` : '?';
    }
    const url = new URL(ubicacion.href);
    url.search = '';
    url.hash = '';
    if (torneoId) {
      url.searchParams.set('torneo', torneoId);
    }
    return `${url.pathname}${url.search}`;
  }

  function anotarDireccion(torneoId) {
    const destino = direccion(torneoId);
    if (!historial?.pushState || `${ubicacion?.pathname}${ubicacion?.search}` === destino) {
      return;
    }
    historial.pushState({ torneo: torneoId }, '', destino);
  }

  /** @param {'tablon'|'ruta'} modo */
  function mostrarModo(modo) {
    const enRuta = modo === 'ruta';
    zonaDetalle.hidden = !enRuta;
    zonaTablon.hidden = enRuta;
    if (zonaEncabezado) {
      zonaEncabezado.hidden = enRuta;
    }
    if (formCrear) {
      formCrear.hidden = !administra || enRuta;
    }
    const vista = zonaDetalle.closest('[data-vista]');
    if (vista) {
      vista.dataset.modo = modo;
    }
  }

  /** Volver al tablón; con `foco`, a la tarjeta del torneo del que se viene. */
  function mostrarListado({ anotar = false, foco = false } = {}) {
    const venia = zonaDetalle.dataset.torneoId ?? null;
    peticionDeDetalle += 1;
    mostrarModo('tablon');
    zonaDetalle.replaceChildren();
    delete zonaDetalle.dataset.torneoId;
    delete zonaDetalle.dataset.estado;
    documento.title = tituloBase;
    if (anotar) {
      anotarDireccion(null);
    }
    if (zonaListado.childElementCount === 0) {
      cargarListado();
    }
    if (foco) {
      const tarjeta = [...zonaListado.querySelectorAll('[data-torneo-id]')].find(
        (t) => t.dataset.torneoId === venia,
      );
      const destino =
        tarjeta?.querySelector('[data-accion="abrir"]') ?? zonaEncabezado?.querySelector('h1');
      destino?.focus();
    }
  }

  /** De una tarjeta a la ruta de su torneo. */
  function navegarA(torneoId) {
    limpiarAviso(zonaAviso);
    return abrir(torneoId, { anotar: true, foco: true, enSitio: true });
  }

  /** «Todos los torneos», arriba de la ruta. */
  function migas() {
    const volver = h('a', {
      clase: 'torneo-detalle__volver',
      datos: { accion: 'volver-a-torneos' },
      atributos: { href: direccion(null) },
      hijos: [icono('chevron', { etiqueta: null }), h('span', { texto: 'Todos los torneos' })],
    });
    volver.addEventListener('click', (evento) => {
      if (clicConModificador(evento)) {
        return;
      }
      evento.preventDefault();
      limpiarAviso(zonaAviso);
      mostrarListado({ anotar: true, foco: true });
    });
    return h('nav', {
      clase: 'torneo-detalle__migas',
      atributos: { 'aria-label': 'Volver' },
      hijos: [volver],
    });
  }

  /** Al llegar a la ruta, el foco va a su título: es una página nueva. */
  function enfocarTitulo() {
    zonaDetalle.scrollIntoView?.({ block: 'start' });
    zonaDetalle.querySelector('#torneo-detalle-titulo')?.focus({ preventScroll: true });
  }

  ventana?.addEventListener?.('popstate', () => {
    const torneoId = new URLSearchParams(ubicacion?.search ?? '').get('torneo');
    limpiarAviso(zonaAviso);
    if (torneoId) {
      abrir(torneoId, { enSitio: true });
    } else {
      mostrarListado();
    }
  });

  async function cargarListado() {
    try {
      zonaListado.replaceChildren(estadoDeCarga({ filas: 2, etiqueta: 'Buscando torneos…' }));
      const torneos = await api.listar(fetchImpl);
      zonaListado.replaceChildren();
      // Con un solo torneo, su tarjeta se pone a lo ancho (torneos.css).
      zonaListado.dataset.cuantos = String(torneos.length);

      // UX-R2.7 — el vacio era un parrafo gris de una linea: «Todavia no hay
      // torneos publicados». Cierto y completamente inutil — el jugador se
      // queda mirando una pantalla con un titulo y nada mas, sin saber que
      // hacer ni cuando volver. Ahora se dice que NO hay torneo, que el
      // formato es por temporadas, y se ofrece lo unico que si puede hacer
      // ahora mismo: jugar una sala.
      //
      // No se inventa una fecha: `torneos.yaml` no publica cuando empieza el
      // siguiente, y poner «vuelve en X dias» seria adivinarlo.
      if (torneos.length === 0) {
        zonaListado.appendChild(
          estadoVacio({
            titulo: 'No hay ningún torneo abierto',
            detalle:
              'Los torneos se abren por temporadas. Cuando haya uno, aparecerá aquí con sus ' +
              'equipos, sus cupos y el árbol de encuentros.',
            accion: {
              texto: 'Jugar una batalla',
              href: '../salas-partidas/batallas.html',
            },
          }),
        );
        return;
      }
      torneos.forEach((t) =>
        zonaListado.appendChild(
          tarjetaDeTorneo(t, { alAbrir: (x) => navegarA(x.id), href: direccion(t.id) }),
        ),
      );
    } catch (error) {
      // El fallo se pinta DONDE iban los torneos, no solo en el aviso de
      // arriba: hasta ahora quedaba un encabezado «Torneos» huerfano con
      // setecientos pixeles de vacio debajo, y el unico rastro del problema
      // era una caja amarilla que decia «No se pudo completar» sin mas.
      zonaListado.replaceChildren(
        estadoDeError({
          titulo: 'Los torneos no están disponibles',
          // El detalle del servidor solo si trae uno util; si no, el motivo en
          // el idioma del producto. Sin detalle, la tarjeta quedaba con un
          // titulo y un boton y ninguna razon (§17).
          detalle:
            (error instanceof ErrorDeTorneos && error.estado < 500 && error.detalle) ||
            'El servicio de torneos no responde ahora mismo. Vuelve a intentarlo en un momento.',
          alReintentar: () => cargarListado(),
        }),
      );
      // UX-R3.6 — y NO se avisa tambien arriba. El aviso flotante es para los
      // fallos de una accion (inscribir un equipo, abrir un torneo), donde el
      // contenido sigue siendo valido y hay que decir que fallo lo que se
      // acaba de pulsar. Cuando lo que falla es la carga del listado, el
      // propio listado ya lo dice, con su motivo y su boton de reintentar; el
      // aviso solo anadia una caja amarilla con «No se pudo completar» y nada
      // mas, encima del mensaje bueno. Dos avisos del mismo fallo, y el peor
      // primero.
      console.warn('[torneos] no se pudo cargar el listado:', error);
    }
  }

  /**
   * Pide un torneo y pinta su ruta.
   *
   * @param {string} torneoId
   * @param {{anotar?: boolean, foco?: boolean, enSitio?: boolean}} [opciones]
   *   `anotar` pone su dirección en el historial; `foco` lleva el foco a su
   *   título; `enSitio` es una navegación: la ruta se enseña al momento,
   *   cargando, y un fallo se dice en ella. Sin `enSitio` es un refresco tras
   *   una acción, y el fallo va al aviso de arriba.
   */
  async function abrir(torneoId, { anotar = false, foco = false, enSitio = false } = {}) {
    peticionDeDetalle += 1;
    const esta = peticionDeDetalle;
    if (enSitio) {
      mostrarModo('ruta');
      delete zonaDetalle.dataset.estado;
      zonaDetalle.dataset.torneoId = torneoId;
      zonaDetalle.replaceChildren(
        migas(),
        estadoDeCarga({ filas: 3, etiqueta: 'Abriendo el torneo…' }),
      );
    }
    try {
      const torneo = await api.obtener(torneoId, fetchImpl);
      if (esta !== peticionDeDetalle) {
        return;
      }
      pintarDetalle(torneo);
      if (anotar) {
        anotarDireccion(torneo.id);
      }
      if (foco) {
        enfocarTitulo();
      }
    } catch (error) {
      if (esta !== peticionDeDetalle) {
        return;
      }
      if (!enSitio) {
        avisarError(zonaAviso, error);
        return;
      }
      // UX-R3.6 — el fallo de abrir la ruta se dice donde iba el torneo, una
      // sola vez. Reintentar solo si puede servir: un torneo que no existe
      // no aparece por insistir.
      const deNegocio = error instanceof ErrorDeTorneos;
      if (anotar) {
        anotarDireccion(torneoId);
      }
      zonaDetalle.replaceChildren(
        migas(),
        estadoDeError({
          titulo: deNegocio ? error.titulo : 'No pudimos abrir el torneo',
          detalle: deNegocio ? error.detalle : 'Revisa tu conexión e inténtalo de nuevo.',
          alReintentar:
            deNegocio && error.estado < 500 ? null : () => abrir(torneoId, { enSitio: true }),
        }),
      );
    }
  }

  /* ---- La ruta del torneo ---- */

  /** La portada: el trofeo, la fase, el nombre, sus fechas, los cupos y el premio. */
  function portadaDelTorneo(torneo, mio) {
    // UXC-8 — el premio (RF-TOR-007, 1.2.0), para todos: qué gana el
    // campeón y en qué va la entrega. De un torneo cancelado, no se anuncia.
    const premio = torneo.estado === 'CANCELADO' ? null : textoDelPremio(torneo);
    return h('header', {
      clase: 'tarjeta torneo-portada',
      datos: { estado: torneo.estado },
      hijos: [
        h('div', {
          clase: 'torneo-portada__arte',
          atributos: { 'aria-hidden': 'true' },
          hijos: [icono('trofeo', { etiqueta: null, clase: 'icono torneo-portada__emblema' })],
        }),
        h('div', {
          clase: 'torneo-portada__identidad',
          hijos: [
            h('div', {
              clase: 'torneo-portada__chips',
              hijos: [
                distintivoDeTorneo(torneo),
                mio
                  ? h('span', {
                      clase: 'torneo-portada__tuyo',
                      hijos: [
                        icono('usuarios', { etiqueta: null }),
                        h('span', { texto: `Tu equipo: ${mio.nombre}` }),
                      ],
                    })
                  : null,
              ],
            }),
            h('h1', {
              clase: 'torneo-portada__nombre',
              texto: torneo.nombre,
              atributos: { id: 'torneo-detalle-titulo', tabindex: '-1' },
            }),
            // CA-01 de HU-TOR-008: el detalle dice sus fechas, no solo la tarjeta.
            h('p', {
              clase: 'torneo-portada__fechas',
              datos: { zona: 'fechas' },
              texto: fechasDe(torneo).join(' · '),
            }),
            torneo.estado === 'INSCRIPCIONES_ABIERTAS' ? cuposDelTorneo(torneo) : null,
            premio
              ? h('p', {
                  clase: 'torneo__premio torneo-portada__premio',
                  datos: { zona: 'premio' },
                  hijos: [
                    icono('trofeo', { clase: 'icono icono--menudo' }),
                    h('span', { texto: premio }),
                  ],
                })
              : null,
            administra && torneo.estado === 'INSCRIPCIONES_ABIERTAS'
              ? zonaDeAdministracion(torneo)
              : null,
          ],
        }),
      ],
    });
  }

  /** «Próximo encuentro» de quien no tiene equipo en este torneo. */
  function proximoSinEquipo(torneo) {
    const textos = {
      INSCRIPCIONES_ABIERTAS:
        'Cuando se cierren las inscripciones, aquí verás contra quién juega tu equipo y podrás crear la sala del encuentro.',
      EN_CURSO: 'No tienes equipo en este torneo: sigue sus encuentros en el árbol.',
      FINALIZADO: 'El torneo terminó: no quedan encuentros por jugar.',
      CANCELADO: 'El torneo se canceló.',
    };
    return h('p', { clase: 't-meta', texto: textos[torneo.estado] ?? textos.EN_CURSO });
  }

  /** «Mi camino» de quien no tiene equipo en este torneo. */
  function caminoSinEquipo(torneo) {
    return h('p', {
      clase: 't-meta',
      texto:
        torneo.estado === 'FINALIZADO' || torneo.estado === 'CANCELADO'
          ? 'No tuviste equipo en este torneo.'
          : 'Aquí queda cada encuentro que juegue tu equipo: victoria o derrota, y contra quién.',
    });
  }

  /** «Resultado y premio»: el campeón, o en qué queda el torneo, y tu premio. */
  function resultadoDelTorneo(torneo, premioMio) {
    const partes = [];
    if (torneo.campeonEquipoId) {
      partes.push(
        h('p', {
          clase: 'torneo-campeon',
          datos: { zona: 'campeon' },
          hijos: [
            icono('trofeo', { etiqueta: null, clase: 'icono torneo-campeon__icono' }),
            h('span', { texto: `Campeón: ${nombreDe(torneo, torneo.campeonEquipoId)}` }),
          ],
        }),
      );
    } else if (torneo.estado === 'CANCELADO') {
      partes.push(
        h('p', {
          clase: 't-cuerpo',
          texto: 'El torneo se canceló: se devuelven las inscripciones que se pagaron.',
        }),
      );
    } else if (torneo.estado === 'FINALIZADO') {
      partes.push(h('p', { clase: 't-cuerpo', texto: 'El torneo terminó.' }));
    } else {
      partes.push(
        h('p', {
          clase: 't-meta',
          texto: 'El campeón sale de la gran final: aquí verás quién gana y lo que se lleva.',
        }),
      );
    }
    if (torneo.motivoCancelacion) {
      partes.push(
        h('p', {
          clase: 't-meta',
          datos: { zona: 'cancelacion' },
          texto: `Cancelado: ${torneo.motivoCancelacion}`,
        }),
      );
    }
    if (premioMio) {
      partes.push(premioMio);
    }
    return partes;
  }

  function pintarDetalle(torneo) {
    const mio = uid ? miEquipo(torneo, uid) : null;
    const bloques = mio ? bloquesDeMiTorneo(torneo, mio, uid, { enRuta: true }) : null;
    mostrarModo('ruta');
    zonaDetalle.replaceChildren();
    zonaDetalle.dataset.torneoId = torneo.id;
    zonaDetalle.dataset.estado = torneo.estado;
    documento.title = `${torneo.nombre} · ${tituloBase}`;

    // La zona de acciones va en «Mi equipo» mientras hay algo que hacer en
    // ella: sin equipo (registrarlo, o por qué no se puede) o con las
    // inscripciones abiertas (inscribirlo). Con el torneo ya jugándose, a
    // quien tiene equipo solo le diría «Las inscripciones están cerradas».
    const conAcciones = !mio || torneo.estado === 'INSCRIPCIONES_ABIERTAS';
    const enJuegoOJugado = torneo.estado === 'EN_CURSO' || torneo.estado === 'FINALIZADO';

    const ruta = rutaDelTorneo(
      torneo,
      mio,
      {
        descripcion: [descripcionDelTorneo(torneo)],
        equipo: [bloques?.equipo, conAcciones ? zonaDeAcciones(torneo) : null],
        proximo: [bloques ? bloques.proximo : proximoSinEquipo(torneo)],
        arbol: [
          arbolDelTorneo(torneo, uid),
          // UXC-8 — la transmisión, dicha como es: todavía no hay (RF-TOR-006).
          enJuegoOJugado
            ? panelDeTransmision(torneo, { alActualizar: () => abrir(torneo.id) })
            : null,
          equiposDelTorneo(torneo, uid),
        ],
        camino: [bloques ? bloques.camino : caminoSinEquipo(torneo)],
        resultado: resultadoDelTorneo(torneo, bloques?.premio ?? null),
      },
      // UXC-8 — con equipo, la ruta es «tu torneo».
      { zona: mio ? 'mi-torneo' : null },
    );

    zonaDetalle.append(migas(), portadaDelTorneo(torneo, mio));
    // UXC-8 — 1.2.0: un cobro, una devolución o un premio que el proveedor
    // rechazó o que agotó sus reintentos se vuelve a poner en cola desde aquí
    // (RF-ADM-005), después de corregir la causa.
    if (administra && necesitaRevision(torneo)) {
      zonaDetalle.append(bloqueDeRevision(torneo));
    }
    zonaDetalle.append(ruta);
  }

  /** Acciones del jugador (HU-TOR-003 / HU-TOR-002): registrar e inscribir. */
  function zonaDeAcciones(torneo) {
    const acciones = accionesDe(torneo, uid);
    const zonaAcciones = nodo('div', 'pila pila--compacta torneo__acciones');
    zonaAcciones.dataset.zona = 'acciones';
    zonaAcciones.appendChild(nodo('p', 't-meta', acciones.motivo));
    if (acciones.crearEquipo) {
      zonaAcciones.appendChild(formularioDeEquipo(torneo));
    }
    if (acciones.inscribir) {
      const equipo = miEquipo(torneo, uid);
      const boton = nodo(
        'button',
        'boton boton--primario',
        torneo.costoInscripcion > 0
          ? `Inscribir «${equipo.nombre}» por ${torneo.costoInscripcion} créditos`
          : `Inscribir «${equipo.nombre}»`,
      );
      boton.type = 'button';
      boton.dataset.accion = 'inscribir';
      boton.addEventListener('click', async () => {
        boton.disabled = true;
        try {
          const inscrito = await api.inscribir(torneo.id, equipo.id, fetchImpl);
          pintarAviso(zonaAviso, {
            tono: 'exito',
            titulo: 'Inscripción confirmada',
            detalle: `Tu equipo ocupa la posición ${inscrito.posicion} del árbol.`,
          });
          await abrir(torneo.id);
          await cargarListado();
        } catch (error) {
          avisarError(zonaAviso, error);
          boton.disabled = false;
        }
      });
      zonaAcciones.appendChild(boton);
    }
    return zonaAcciones;
  }

  /** Acciones del administrador (HU-TOR-001 / HU-ADM-005), en la portada. */
  function zonaDeAdministracion(torneo) {
    const zonaAdmin = nodo('div', 'fila torneo-portada__administracion');
    zonaAdmin.dataset.zona = 'administracion';
    const iniciar = nodo('button', 'boton boton--acento', 'Cerrar inscripciones e iniciar');
    iniciar.type = 'button';
    iniciar.dataset.accion = 'iniciar';
    iniciar.addEventListener('click', async () => {
      iniciar.disabled = true;
      try {
        await api.iniciar(torneo.id, fetchImpl);
        pintarAviso(zonaAviso, {
          tono: 'exito',
          titulo: 'Torneo iniciado',
          detalle: 'Las posiciones vacías se completaron con la máquina y el árbol está listo.',
        });
        await abrir(torneo.id);
        await cargarListado();
      } catch (error) {
        avisarError(zonaAviso, error);
        iniciar.disabled = false;
      }
    });
    const cancelar = nodo('button', 'boton boton--peligro', 'Cancelar torneo');
    cancelar.type = 'button';
    cancelar.dataset.accion = 'cancelar';
    cancelar.addEventListener('click', async () => {
      // UXC-7/9 — era `globalThis.prompt?.()`: el diálogo del navegador, sin
      // estilo ni foco gestionado, y el guardián no lo veía por el `?.`.
      const motivo =
        (await pedirTexto({
          titulo: `¿Cancelar «${torneo.nombre}»?`,
          etiqueta: 'Motivo de la cancelación',
          pista: 'Lo verán los equipos. Se devuelven todas las inscripciones.',
          textoConfirmar: 'Cancelar el torneo',
          textoCancelar: 'Volver',
          maximo: 500,
        })) ?? '';
      if (!motivo.trim()) {
        return;
      }
      try {
        await api.cancelar(torneo.id, motivo.trim(), fetchImpl);
        pintarAviso(zonaAviso, {
          tono: 'info',
          titulo: 'Torneo cancelado',
          detalle:
            'Las inscripciones pagadas se devuelven ahora; cada equipo ve en el torneo en qué va la suya.',
        });
        await abrir(torneo.id);
        await cargarListado();
      } catch (error) {
        avisarError(zonaAviso, error);
      }
    });
    zonaAdmin.append(iniciar, cancelar);
    return zonaAdmin;
  }

  function bloqueDeRevision(torneo) {
    const reintentar = nodo('button', 'boton boton--primario boton--pequeno', 'Reintentar ahora');
    reintentar.type = 'button';
    reintentar.dataset.accion = 'reintentar-operaciones';
    reintentar.addEventListener('click', async () => {
      reintentar.disabled = true;
      try {
        const actualizado = await api.reintentar(torneo.id, fetchImpl);
        pintarAviso(zonaAviso, {
          tono: 'exito',
          titulo: 'Operaciones en cola otra vez',
          detalle:
            'Los cobros, devoluciones y premios pendientes se vuelven a intentar; el estado de cada uno se ve en el torneo.',
        });
        pintarDetalle(actualizado);
      } catch (error) {
        avisarError(zonaAviso, error);
        reintentar.disabled = false;
      }
    });
    return h('div', {
      clase: 'aviso aviso--advertencia pila pila--ajustada',
      datos: { zona: 'revision' },
      hijos: [
        h('strong', { texto: 'Hay pagos o premios en revisión' }),
        h('p', {
          texto:
            'Algún cobro, devolución o premio de este torneo no se pudo completar tras varios intentos. Corrige la causa y vuelve a intentarlo.',
        }),
        reintentar,
      ],
    });
  }

  function formularioDeEquipo(torneo) {
    const form = nodo('form', 'tarjeta pila pila--compacta');
    form.dataset.zona = 'crear-equipo';
    form.appendChild(nodo('h3', undefined, 'Registrar mi equipo'));
    // Con `campo()` en vez de innerHTML: etiqueta asociada por for/id y pista
    // bajo el control.
    form.appendChild(
      campo({
        nombre: 'nombre',
        etiqueta: 'Nombre del equipo',
        requerido: true,
        atributos: { minlength: 3, maxlength: 40 },
        pista: 'Entre 3 y 40 caracteres. Pasa por la lista negra de terminos prohibidos.',
      }).elemento,
    );
    // RFINAL-04 (revisión de AWS DEV del 4-oct) — el emblema se elige de una
    // lista con su imagen y el compañero se busca por apodo; antes se pedía
    // escribir una dirección de imagen y pegar el UUID del compañero.
    const emblema = selectorDeEmblema();
    form.appendChild(emblema.elemento);
    const companero = selectorDeCompanero({ fetchImpl, yo: uid });
    form.appendChild(companero.elemento);
    const enviar = nodo('button', 'boton boton--primario', 'Registrar equipo');
    enviar.type = 'submit';
    form.appendChild(enviar);
    form.addEventListener('submit', async (evento) => {
      evento.preventDefault();
      const datos = new FormData(form);
      const elegido = companero.elegido();
      if (!elegido) {
        companero.marcarError('Busca a tu compañero por su apodo y elígelo de la lista.');
        return;
      }
      const boton = form.querySelector('[type="submit"]');
      boton.disabled = true;
      try {
        await api.crearEquipo(
          torneo.id,
          {
            nombre: String(datos.get('nombre') ?? '').trim(),
            avatar: emblema.elegido(),
            companeroUid: elegido.uid,
          },
          fetchImpl,
        );
        pintarAviso(zonaAviso, {
          tono: 'exito',
          titulo: 'Equipo registrado',
          detalle: 'Ahora inscríbelo para ocupar su posición en el árbol.',
        });
        await abrir(torneo.id);
      } catch (error) {
        avisarError(zonaAviso, error);
      } finally {
        boton.disabled = false;
      }
    });
    return form;
  }

  formCrear?.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    const form = evento.currentTarget;
    const datos = new FormData(form);
    const boton = form.querySelector('[type="submit"]');
    boton.disabled = true;
    zonaAviso.hidden = true;
    try {
      const cierre = String(datos.get('inscripcionesCierranEn') ?? '');
      const torneo = await api.crear(
        {
          nombre: String(datos.get('nombre') ?? '').trim(),
          inscripcionesCierranEn: cierre ? new Date(cierre).toISOString() : null,
          costoInscripcion: Number(datos.get('costoInscripcion') ?? 0) || 0,
        },
        fetchImpl,
      );
      pintarAviso(zonaAviso, {
        tono: 'exito',
        titulo: 'Torneo creado',
        detalle: `«${torneo.nombre}» tiene las inscripciones abiertas.`,
      });
      form.reset();
      await cargarListado();
      // El torneo recién creado se abre en su ruta, con su dirección.
      pintarDetalle(torneo);
      anotarDireccion(torneo.id);
      enfocarTitulo();
    } catch (error) {
      avisarError(zonaAviso, error);
    } finally {
      boton.disabled = false;
    }
  });

  // Con `?torneo=` se entra directo a su ruta; el tablón se carga igual, para
  // volver a él sin esperar.
  if (torneoInicial) {
    abrir(torneoInicial, { enSitio: true });
  } else {
    mostrarModo('tablon');
  }
  cargarListado();
  return { cargarListado, abrir, mostrarListado };
}
